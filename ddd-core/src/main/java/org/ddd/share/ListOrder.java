package org.ddd.share;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * @author qiaohe
 * @date 2023/8/13
 */
@Data
@Schema(description = "排序定义")
@ApiModel(description = "排序定义")
public class ListOrder {
    /**
     * 排序字段
     */
    @Schema(description="排序字段")
    @ApiModelProperty(value = "排序字段")
    String field;
    /**
     * 是否降序
     */
    @Schema(description="是否降序")
    @ApiModelProperty(value = "是否降序")
    Boolean desc;
}
