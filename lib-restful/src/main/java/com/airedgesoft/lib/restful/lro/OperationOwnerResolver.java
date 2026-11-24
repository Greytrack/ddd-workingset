package com.airedgesoft.lib.restful.lro;

/**
 * LRO 发起人解析 SPI：宿主注册 Bean 后，触发装配与「我的长运行任务列表」按当前用户装配/过滤 {@link Operation#owner()}。
 *
 * <p>
 * 未注册（开发 / 本地形态）→ owner 为空、列表不按人过滤（全量可见）；鉴权能力接入后由宿主（如 IdentityAccess 上下文）注册实现。
 */
@FunctionalInterface
public interface OperationOwnerResolver {

    /** 当前登录用户 id（十进制字符串）；无法判定 → {@code null}（触发端点照常受理，owner 留空）。 */
    String currentOwner();
}
