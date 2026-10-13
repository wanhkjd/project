package com.wsy.wanjoymall.item.service.impl;

import cn.hutool.core.util.StrUtil;
import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.RangeQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import co.elastic.clients.json.JsonData;
import com.wsy.wanjoymall.common.domain.PageDTO;
import com.wsy.wanjoymall.common.utils.BeanUtils;
import com.wsy.wanjoymall.item.domain.dto.ItemDTO;
import com.wsy.wanjoymall.item.domain.po.ItemDoc;
import com.wsy.wanjoymall.item.domain.query.ItemPageQuery;
import com.wsy.wanjoymall.item.service.ISearchService;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Elasticsearch 搜索实现：wm.elasticsearch.enabled 为 true 时启用
 */
@ConditionalOnProperty(prefix = "wm.elasticsearch", name = "enabled", havingValue = "true")
@Service
@RequiredArgsConstructor
@Slf4j
public class EsSearchServiceImpl implements ISearchService {

    private static final String INDEX_NAME = "items";

    private final ElasticsearchClient client;

    @SneakyThrows
    @Override
    public PageDTO<ItemDTO> search(ItemPageQuery query) {
        // 1.发送检索请求，指定索引库 items，并用 ItemDoc 接收 _source 反序列化结果
        SearchResponse<ItemDoc> response = client.search(builder -> {
            // 1.1.索引库
            builder.index(INDEX_NAME);
            // 1.2.bool 查询：关键字全文检索 + 品牌/分类/价格过滤
            builder.query(q -> q.bool(buildQuery(query)));
            // 1.3.排序：前端未指定则默认按 updateTime 倒序
            String sortBy = StrUtil.isNotBlank(query.getSortBy()) ? query.getSortBy() : "updateTime";
            SortOrder order = Boolean.TRUE.equals(query.getIsAsc()) ? SortOrder.Asc : SortOrder.Desc;
            builder.sort(o -> o.field(f -> f.field(sortBy).order(order)));
            // 1.4.分页
            builder.from(query.from()).size(query.getPageSize());
            return builder;
        }, ItemDoc.class);

        // 2.解析命中总数与总页数
        long total = response.hits().total() != null ? response.hits().total().value() : 0L;
        int pageSize = query.getPageSize();
        long pages = pageSize == 0 ? 0 : (total + pageSize - 1) / pageSize;

        // 3.遍历命中文档，ItemDoc 转 ItemDTO
        List<ItemDTO> list = new ArrayList<>();
        for (Hit<ItemDoc> hit : response.hits().hits()) {
            ItemDoc doc = hit.source();
            if (doc != null) {
                list.add(BeanUtils.copyBean(doc, ItemDTO.class));
            }
        }

        // 4.封装为与数据库实现一致的分页结果
        return new PageDTO<>(total, pages, list);
    }

    // 构建 bool 查询：关键字放 must 参与算分，品牌/分类/价格放 filter 精确过滤（不算分、可被缓存）
    private BoolQuery buildQuery(ItemPageQuery query) {
        return BoolQuery.of(b -> {
            // 1.关键字：对 name 做全文检索（ik 分词）；无关键字时匹配全部
            if (StrUtil.isNotBlank(query.getKey())) {
                b.must(m -> m.match(t -> t.field("name").query(query.getKey())));
            } else {
                b.must(m -> m.matchAll(t -> t));
            }
            // 2.品牌精确匹配
            if (StrUtil.isNotBlank(query.getBrand())) {
                b.filter(TermQuery.of(t -> t.field("brand").value(query.getBrand()))._toQuery());
            }
            // 3.分类精确匹配
            if (StrUtil.isNotBlank(query.getCategory())) {
                b.filter(TermQuery.of(t -> t.field("category").value(query.getCategory()))._toQuery());
            }
            // 4.价格区间过滤
            if (query.getMinPrice() != null) {
                b.filter(RangeQuery.of(r -> r.field("price").gte(JsonData.of(query.getMinPrice())))._toQuery());
            }
            if (query.getMaxPrice() != null) {
                b.filter(RangeQuery.of(r -> r.field("price").lte(JsonData.of(query.getMaxPrice())))._toQuery());
            }
            return b;
        });
    }
}
