package com.wsy.wanjoymall.item.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wsy.wanjoymall.item.domain.dto.OrderDetailDTO;
import com.wsy.wanjoymall.item.domain.po.Item;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 * 商品表 Mapper 接口
 * </p>
 *
 * @since 2023-05-05
 */
public interface ItemMapper extends BaseMapper<Item> {

    // 原子扣减库存：库存充足且商品上架才更新，判断与扣减在同一条 SQL 里由行锁保证，不会超卖
    @Update("UPDATE `item` SET stock = stock - #{num} WHERE id = #{itemId} AND status = 1 AND stock >= #{num}")
    int updateStock(OrderDetailDTO orderDetail);

    // 关单回补库存，返回影响行数，商品已被删除时为 0
    @Update("UPDATE `item` SET stock = stock + #{num} WHERE id = #{itemId}")
    int increaseStock(OrderDetailDTO orderDetail);
}
