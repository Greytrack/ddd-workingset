package com.airedgesoft.sharedkernel.businessframework.application.query;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 工作集比较查询（计算型只读自定义方法，幂等；以 body 承载比较范围参数，AIP-136）。
 *
 * @param targetWorkingsetId
 *            目标工作集 id（与路径 id 构成比较双方，必填）
 * @param spaceId
 *            限定比较的产品空间 id（可选；缺省比较全部）
 * @param subclassId
 *            限定的分类 id（可选；缺省比较全部分类）
 */
@Schema(description = "工作集比较查询（POST :compare 请求体；计算型只读语义）")
public record CompareWorkingsetsQuery(@Schema(description = "目标工作集 id（与路径 id 构成比较双方，必填）") String targetWorkingsetId,
        @Schema(description = "限定比较的产品空间 id（可选）") String spaceId,
        @Schema(description = "限定的分类 id（可选）") String subclassId) {
}
