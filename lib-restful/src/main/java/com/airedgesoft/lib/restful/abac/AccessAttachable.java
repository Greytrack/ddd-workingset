package com.airedgesoft.lib.restful.abac;

/**
 * 「可挂载 {@code _access} 节点」的载荷契约（Q* record 实现之；{@link IncludeAccessAspect} 消费）。
 *
 * <p>
 * record
 * 形态（法定）：{@code record QDemoTask(..., @JsonProperty("_access") AccessNode access) implements AccessAttachable<QDemoTask>}——
 * {@link #withAccess} 返回携带节点的新实例（record 不可变，返回副本）；{@code access} 组件为 null 时该键不序列化 （Jackson 对 null 组件默认忽略需配合
 * {@code @JsonInclude(NON_NULL)} 或全局策略；缺省 null 组件也会输出为 null——BC 按需标注）。
 *
 * @param <T>
 *            载荷自身类型（返回副本的静态类型）
 */
public interface AccessAttachable<T> {

    /** 返回携带 {@code _access} 节点的新实例（不修改原实例）。 */
    T withAccess(AccessNode access);
}
