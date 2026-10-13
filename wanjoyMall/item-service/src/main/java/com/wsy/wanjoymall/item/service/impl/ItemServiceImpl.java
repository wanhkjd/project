package com.wsy.wanjoymall.item.service.impl;

import cn.hutool.core.thread.ThreadUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.wsy.wanjoymall.common.constants.RedisConstants;
import com.wsy.wanjoymall.common.exception.BizIllegalException;
import com.wsy.wanjoymall.common.utils.BeanUtils;
import com.wsy.wanjoymall.common.utils.CollUtils;
import com.wsy.wanjoymall.item.domain.dto.ItemDTO;
import com.wsy.wanjoymall.item.domain.dto.OrderDetailDTO;
import com.wsy.wanjoymall.item.domain.po.Item;
import com.wsy.wanjoymall.item.mapper.ItemMapper;
import com.wsy.wanjoymall.item.service.IItemService;
import com.wsy.wanjoymall.item.service.ItemEsSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>
 * 商品表 服务实现类
 * </p>
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ItemServiceImpl extends ServiceImpl<ItemMapper, Item> implements IItemService {

    private final StringRedisTemplate redisTemplate;
    private final ItemEsSyncService itemEsSyncService;

    @Override
    @Transactional
    public void deductStock(List<OrderDetailDTO> items) {
        for (OrderDetailDTO item : items) {
            // 1.数量校验：0 或负数会让 stock - num 变成加库存
            if (item.getNum() == null || item.getNum() <= 0) {
                throw new BizIllegalException("购买数量必须大于0");
            }
            // 2.原子扣减，影响行数为0说明商品不存在、已下架或库存不足
            int rows = baseMapper.updateStock(item);
            if (rows != 1) {
                // 抛异常让本地事务回滚已扣的商品，再由 Seata 回滚上游订单
                throw new BizIllegalException("商品[" + item.getItemId() + "]库存不足或已下架！");
            }
        }
        // 3.库存变了，事务提交后删缓存让下次读取回源
        evictAfterCommit(items);
    }

    @Override
    @Transactional
    public void restoreStock(List<OrderDetailDTO> items) {
        for (OrderDetailDTO item : items) {
            // 1.数量校验：0 或负数会让 stock + num 变成减库存
            if (item.getNum() == null || item.getNum() <= 0) {
                throw new BizIllegalException("恢复数量必须大于0");
            }
            // 2.加回库存；商品已被删除时影响行数为 0，只记日志不阻断关单
            int rows = baseMapper.increaseStock(item);
            if (rows != 1) {
                log.warn("商品[{}]不存在，跳过库存回补", item.getItemId());
            }
        }
        // 3.事务提交后删缓存
        evictAfterCommit(items);
    }

    private void evictAfterCommit(List<OrderDetailDTO> items) {
        // 1.有事务时挂到提交后再删：提交前删除会被并发读回填成旧库存
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    // 事务已提交，删缓存失败只能记日志，不能让调用方误以为扣减失败
                    try {
                        items.forEach(item -> evictItemCache(item.getItemId()));
                    } catch (RuntimeException e) {
                        log.warn("提交后删除商品缓存失败，等待 TTL 过期", e);
                    }
                }
            });
        } else {
            // 2.无事务（如单元测试）直接删
            items.forEach(item -> evictItemCache(item.getItemId()));
        }
    }

    @Override
    public List<ItemDTO> queryItemByIds(Collection<Long> ids) {
        if (CollUtils.isEmpty(ids)) {
            return CollUtils.emptyList();
        }
        // 1.批量查缓存；Redis 不可用时把全部 id 视为未命中，整批退化查库
        List<Long> idList = new ArrayList<>(ids);
        List<String> keys = idList.stream()
                .map(id -> RedisConstants.ITEM_INFO_KEY + id)
                .collect(Collectors.toList());
        List<String> jsons;
        try {
            jsons = redisTemplate.opsForValue().multiGet(keys);
        } catch (DataAccessException e) {
            log.warn("批量读取缓存失败，退化查库：keys={}", keys, e);
            jsons = null;
        }
        // 2.分拣：命中的直接转 DTO，空串占位跳过，null 记为未命中
        List<ItemDTO> result = new ArrayList<>(idList.size());
        List<Long> missIds = new ArrayList<>();
        for (int i = 0; i < idList.size(); i++) {
            String json = jsons == null ? null : jsons.get(i);
            if (json == null) {
                missIds.add(idList.get(i));
            } else if (!json.isEmpty()) {
                result.add(JSONUtil.toBean(json, ItemDTO.class));
            }
        }
        if (missIds.isEmpty()) {
            return result;
        }
        // 3.未命中的批量回源并回填缓存
        Set<Long> foundIds = new HashSet<>();
        for (Item item : listByIds(missIds)) {
            ItemDTO dto = BeanUtils.copyBean(item, ItemDTO.class);
            safeRun("写入商品缓存", () -> redisTemplate.opsForValue().set(
                    RedisConstants.ITEM_INFO_KEY + item.getId(), JSONUtil.toJsonStr(dto), randomTtl()));
            result.add(dto);
            foundIds.add(item.getId());
        }
        // 4.库里也没有的写空串占位，防止批量接口被恶意 id 穿透
        for (Long missId : missIds) {
            if (!foundIds.contains(missId)) {
                safeRun("写入空值占位", () -> redisTemplate.opsForValue().set(
                        RedisConstants.ITEM_INFO_KEY + missId, "", RedisConstants.ITEM_NULL_TTL));
            }
        }
        return result;
    }

    @Override
    public ItemDTO queryItemById(Long id) {
        String key = RedisConstants.ITEM_INFO_KEY + id;
        // 1.查缓存：命中直接返回；空串是防穿透占位，代表商品不存在
        String json = safeGet(key);
        if (json != null) {
            return json.isEmpty() ? null : JSONUtil.toBean(json, ItemDTO.class);
        }
        // 2.未命中：抢互斥锁重建，防止热点 key 击穿；抢不到的线程短暂等待后重读缓存
        String lockKey = RedisConstants.ITEM_LOCK_KEY + id;
        for (int i = 0; i < 3; i++) {
            Boolean locked;
            try {
                locked = redisTemplate.opsForValue()
                        .setIfAbsent(lockKey, "1", RedisConstants.ITEM_LOCK_TTL);
            } catch (DataAccessException e) {
                // Redis 不可用，抢锁和等待重读都没有意义，直接退化查库
                log.warn("抢缓存锁失败，退化查库：key={}", lockKey, e);
                break;
            }
            if (Boolean.TRUE.equals(locked)) {
                try {
                    return rebuildItemCache(id, key);
                } finally {
                    safeRun("释放缓存锁", () -> redisTemplate.delete(lockKey));
                }
            }
            ThreadUtil.sleep(50);
            json = safeGet(key);
            if (json != null) {
                return json.isEmpty() ? null : JSONUtil.toBean(json, ItemDTO.class);
            }
        }
        // 3.等待超时或 Redis 不可用，退化为直接查库保证可用
        return BeanUtils.copyBean(getById(id), ItemDTO.class);
    }

    private ItemDTO rebuildItemCache(Long id, String key) {
        // 1.查库
        Item item = getById(id);
        if (item == null) {
            // 2.不存在则写空串占位，短 TTL，防止恶意 id 反复打库
            safeRun("写入空值占位", () -> redisTemplate.opsForValue().set(key, "", RedisConstants.ITEM_NULL_TTL));
            return null;
        }
        // 3.存在则写缓存，TTL 随机抖动
        ItemDTO dto = BeanUtils.copyBean(item, ItemDTO.class);
        safeRun("写入商品缓存", () -> redisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(dto), randomTtl()));
        return dto;
    }

    private Duration randomTtl() {
        // 30 ± 5 分钟，避免同批缓存同时过期造成雪崩
        int minutes = RedisConstants.ITEM_INFO_TTL_BASE_MINUTES
                + RandomUtil.randomInt(-RedisConstants.ITEM_INFO_TTL_RANDOM_MINUTES,
                RedisConstants.ITEM_INFO_TTL_RANDOM_MINUTES + 1);
        return Duration.ofMinutes(minutes);
    }

    @Override
    public void saveItem(ItemDTO item) {
        // 1.先入库，MyBatis-Plus 会把自增 id 回填到 po
        Item po = BeanUtils.copyBean(item, Item.class);
        save(po);
        // 2.删缓存：清掉创建前可能被查询留下的空值占位
        evictItemCache(po.getId());
        // 3.同步新增 ES 文档（ES 未启用时为空操作）
        itemEsSyncService.saveOrUpdate(po);
    }

    @Override
    public void updateItem(ItemDTO item) {
        // 1.先更库
        updateById(BeanUtils.copyBean(item, Item.class));
        // 2.删缓存
        evictItemCache(item.getId());
        // 3.回查最新数据：更新可能只传了部分字段，回查保证文档完整并拿到最新状态
        Item latest = getById(item.getId());
        // 4.只索引在售商品，编辑到下架商品时要移出索引，保证搜索不返回下架商品
        if (latest != null && Integer.valueOf(1).equals(latest.getStatus())) {
            itemEsSyncService.saveOrUpdate(latest);
        } else {
            itemEsSyncService.deleteById(item.getId());
        }
    }

    @Override
    public void updateItemStatus(Long id, Integer status) {
        Item item = new Item();
        item.setId(id);
        item.setStatus(status);
        updateById(item);
        evictItemCache(id);
        // 上架则同步文档，下架/其它状态则从 ES 移除，保证索引库只留在售商品
        if (Integer.valueOf(1).equals(status)) {
            itemEsSyncService.saveOrUpdate(getById(id));
        } else {
            itemEsSyncService.deleteById(id);
        }
    }

    @Override
    public void deleteItem(Long id) {
        removeById(id);
        evictItemCache(id);
        // 同步从 ES 删除文档
        itemEsSyncService.deleteById(id);
    }

    @Override
    public void evictItemCache(Long id) {
        safeRun("删除商品缓存", () -> redisTemplate.delete(RedisConstants.ITEM_INFO_KEY + id));
    }

    // 读缓存：Redis 不可用时按未命中处理，退化为查库，不能让商品查询整体不可用
    private String safeGet(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (DataAccessException e) {
            log.warn("读取缓存失败，退化查库：key={}", key, e);
            return null;
        }
    }

    // 写缓存 / 删缓存：失败只记日志，缓存靠 TTL 兜底，不影响主流程
    private void safeRun(String action, Runnable redisOp) {
        try {
            redisOp.run();
        } catch (DataAccessException e) {
            log.warn("{}失败，忽略：{}", action, e.getMessage());
        }
    }
}
