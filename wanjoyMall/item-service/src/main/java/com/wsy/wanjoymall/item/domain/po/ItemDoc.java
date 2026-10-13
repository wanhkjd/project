package com.wsy.wanjoymall.item.domain.po;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Data;

import java.time.LocalDateTime;

@Data
@Schema(description = "索引库实体")
public class ItemDoc{

    @Schema(description = "商品id")
    private String id;

    @Schema(description = "商品名称")
    private String name;

    @Schema(description = "价格（分）")
    private Integer price;

    @Schema(description = "商品图片")
    private String image;

    @Schema(description = "类目名称")
    private String category;

    @Schema(description = "品牌名称")
    private String brand;

    @Schema(description = "销量")
    private Integer sold;

    @Schema(description = "评论数")
    private Integer commentCount;

    @Schema(description = "是否是推广广告，true/false")
    private Boolean isAD;

    @Schema(description = "更新时间")
    private LocalDateTime updateTime;
}