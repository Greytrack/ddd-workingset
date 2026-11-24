package com.airedgesoft.lib.restful.etag;

/**
 * 携带对外并发代数的响应契约：{@code lockedVersion} 的唯一声明源。
 *
 * <p>
 * 契约保留字规则（规范 §2）：
 * <ul>
 * <li>{@code lockedVersion}：并发代数（专指乐观锁并发控制，表达专门的非业务含义；仅由 {@link Etagged} 在 wire 上携带，{@code Long}）； 应用层 {@code Q*}
 * 与领域模型不声明该字段；</li>
 * <li>{@code revision} / {@code revisionNumber}：专指业务版次；API 主版本只出现在 URL（不设 {@code apiVersion} 字段）。</li>
 * </ul>
 *
 * <p>
 * 代数 N 从 1 起算 = 存储 {@code @Version} + 1（存储语义为"已执行更新次数"），与 {@code readMask} 投影无关（规范 §6）。
 *
 * <p>
 * <b>与 {@code ETag} 的关系</b>：{@code ETag} 恒为<b>响应表示的 hash</b>（由 {@link EtagResponseAdvice} 计算），与本接口的
 * {@code lockedVersion} <b>不再互相派生</b>——并发控制只依赖 body 中的 {@code lockedVersion}（决策 C4 / C5）。
 *
 * <p>
 * <b>两种实现形态（规范 §6 / §7.2）</b>：
 * <ol>
 * <li><b>推荐：控制器返回纯数据 {@code Q*}，由 {@link EtagResponseAdvice} 依 {@link EtagResource} 身份声明自动装配</b>—— 应用层 / 读模型保持纯数据
 * record（无版本字段、不实现本接口），版本来源为基础设施请求级上下文（{@link EtagContext}）；</li>
 * <li>兼容形态：DTO 实现本接口并自带 {@code lockedVersion()}（切面同样支持）。</li>
 * </ol>
 * 用接口而非基类：响应 DTO / 读模型约定为 {@code record}（不可继承），record 可实现接口。
 */
public interface Etag {

    /**
     * 对外并发代数（≥1，专指乐观锁并发控制，表达专门的非业务含义）。 约定读模型投影处归一化为非空；此处兜底防御未归一化的手工构造。
     */
    Long lockedVersion();
}
