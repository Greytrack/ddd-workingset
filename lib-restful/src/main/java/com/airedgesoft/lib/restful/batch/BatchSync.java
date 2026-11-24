package com.airedgesoft.lib.restful.batch;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 批量写端点<b>同步原子通道</b>的设计期声明（v4-restful-api.md §4.1；AIP-233/234/235：同步批量 must 原子）。
 *
 * <p>
 * 批量写端点必须二选一标注通道：{@code @LroOperation}（缺省形态——异步 LRO 部分成功）或本注解（例外形态——同步原子 all-or-nothing：全成功 200 / 全失败 409 或
 * 422，「全失败」由应用层本地补偿兑现）。<b>一个端点只承载一个通道</b>，无运行时通道参数；OpenAPI / codegen 各自声明单一响应族。
 *
 * <p>
 * <b>运行期行为</b>：纯声明标记——同步原子的执行语义（整批判定、逐实例独立事务、失败本地补偿）由应用层命令服务实现，本注解不引入切面拦截；其价值是设计期契约钉死 （场景行登记「为何必须整批原子」理由）与 ArchUnit
 * 门禁判据（批量写端点必须标注 {@code @LroOperation} / {@code @BatchSync} 二者之一）。
 */
@Documented
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface BatchSync {

    /** 通道声明理由（为何必须整批原子；与场景行登记同源，供文档与门禁报告呈现）。 */
    String reason();
}
