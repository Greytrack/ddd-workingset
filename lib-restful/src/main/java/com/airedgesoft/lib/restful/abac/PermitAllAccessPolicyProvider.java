package com.airedgesoft.lib.restful.abac;

/**
 * {@link AccessPolicyProvider} 缺省实现：全允许、零脱敏、零约束（未接入裁决引擎时的向后兼容形态）。
 *
 * <p>
 * 注意「全允许」的表达：四个集合全部为空 = 读路径不脱敏、{@code _access} 节点各集合为空、写路径不启用可写白名单与值约束 （{@link AbacWriteGuard} 对空集合不生效）。
 *
 * <p>
 * <b>⚠ 生产警示</b>：本实现是<b>开发兼容缺省，不是生产缺省</b>。生产环境必须显式声明业务侧 {@link AccessPolicyProvider} Bean（由 IdentityAccess 裁决引擎
 * 供数）——因本缺省「全允许」，宿主漏配时 ABAC <b>静默失效</b>：无 403、无脱敏、无值约束，越权访问不产生任何报错或日志信号， 审计与测试均难以察觉。上线前检查项：
 * 容器内不得残留本缺省（{@code AbacAutoConfiguration#permitAllAccessPolicyProvider} 仅在无任何 Provider Bean 时兜底装配）。
 */
public class PermitAllAccessPolicyProvider implements AccessPolicyProvider {

    @Override
    public AccessMetadata resolve(AccessContext context) {
        return AccessMetadata.allowAll();
    }
}
