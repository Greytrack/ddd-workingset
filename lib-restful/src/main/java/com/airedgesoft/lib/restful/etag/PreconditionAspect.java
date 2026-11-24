package com.airedgesoft.lib.restful.etag;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 写请求并发版本横切处理（规范 §6；决策 C5 / C6 / C7 / D13）。
 *
 * <p>
 * <b>版本载体是 body，不是请求头</b>：{@code If-Match} 不再参与业务并发控制；框架从 body 参数读取 {@link LockedCommand#lockedVersion()}，校验后绑定到
 * {@link EtagContext}，由基础设施在加载 / 写入时 CAS 断言。
 *
 * <p>
 * <b>强制范围</b>：凡是 body 实现 {@link LockedCommand}（或 {@link BatchLockedCommand}）的端点强制携带版本—— 缺失 → 400
 * {@code LOCKED_VERSION_REQUIRED}，非正整数 → 400 {@code LOCKED_VERSION_INVALID}，不匹配 → 409 {@code VERSION_CONFLICT}。Create
 * 命令与删除 / 恢复族命令<b>不实现</b>该接口，因而天然豁免。
 *
 * <p>
 * <b>删除族一律不带版本</b>：{@code DELETE} 端点直接跳过（含 {@code delete} / {@code undelete} / {@code expunge} / {@code purge} /
 * {@code :batchDelete} 等自定义方法）。
 *
 * <p>
 * <b>批量</b>：{@link BatchLockedCommand} 的每个条目都必须携带版本；任一条缺失 → 400；任一条不匹配 → 409，整批原子不落库。
 *
 * <p>
 * <b>切面顺序</b>：{@code @Order(HIGHEST_PRECEDENCE + 15)}，先于响应装配——400 快速失败，绑定在仓储加载前完成。
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 15)
public class PreconditionAspect {

    private final PreconditionProperties properties;

    public PreconditionAspect(PreconditionProperties properties) {
        this.properties = properties;
    }

    @Around("@within(org.springframework.web.bind.annotation.RestController)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        if (!properties.isEnabled()) {
            return pjp.proceed();
        }
        Method method = ((MethodSignature) pjp.getSignature()).getMethod();
        // 删除族不带版本（DELETE 及其自定义方法）
        if (isDeleteMethod(method)) {
            return pjp.proceed();
        }
        Object[] args = pjp.getArgs();
        if (args != null) {
            for (Object arg : args) {
                if (arg instanceof BatchLockedCommand batch) {
                    bindBatch(batch);
                    return pjp.proceed();
                }
                if (arg instanceof LockedCommand locked) {
                    bindSingle(method, args, locked);
                    return pjp.proceed();
                }
            }
        }
        // 无版本命令（Create / 删除 / 恢复 / 通知类）→ 不做并发校验
        return pjp.proceed();
    }

    /** 单资源写：校验必填与合法性，并按路径变量身份绑定。 */
    private static void bindSingle(Method method, Object[] args, LockedCommand command) {
        long generation = requireGeneration(command.lockedVersion(), "body.lockedVersion");
        String target = bindingTarget(method, args);
        if (target == null) {
            // 无法确定资源身份 → 显式失败，避免静默丢弃并发保护（规范 §6）
            throw new DomainException(DomainRestCode.LOCKED_VERSION_INVALID, "无法确定资源身份");
        }
        EtagContext.bindLockedVersion(target, generation);
    }

    /** 批量写：逐条校验并绑定；任一缺失 → 400（整批拒绝，不进入业务事务）。 */
    private static void bindBatch(BatchLockedCommand batch) {
        for (IdentifiedLockedCommand item : batch.items()) {
            if (item == null) {
                throw new DomainException(DomainRestCode.LOCKED_VERSION_REQUIRED);
            }
            long generation = requireGeneration(item.lockedVersion(), "items[].lockedVersion");
            String id = item.id();
            if (id == null || id.isBlank()) {
                throw new DomainException(DomainRestCode.LOCKED_VERSION_INVALID, "批量条目缺少 id");
            }
            EtagContext.bindLockedVersion(id, generation);
        }
    }

    private static long requireGeneration(Long generation, String field) {
        if (generation == null) {
            throw new DomainException(DomainRestCode.LOCKED_VERSION_REQUIRED);
        }
        if (generation < 1) {
            throw new DomainException(DomainRestCode.LOCKED_VERSION_INVALID, field + "=" + generation);
        }
        return generation;
    }

    /**
     * 绑定目标资源名：{@link EtagResource} 声明的路径变量优先；未声明时取唯一路径变量；都没有 → null（无法确定身份）。
     *
     * <p>
     * 身份统一为 AIP-122 相对资源名（{@code collection/id}）——与响应装配侧（{@link EtagResponseAdvice}）同一口径， 保证「比较谁」与「下发谁的 ETag」永远是同一个资源。
     */
    private static String bindingTarget(Method method, Object[] args) {
        return EtagIdentities.bindingTarget(method, args, currentRequest());
    }

    /**
     * 删除 / 恢复族豁免：{@code @DeleteMapping}，或路由冒号动词 / 方法名属删除恢复族 （{@code delete} / {@code undelete} / {@code expunge} /
     * {@code purge} / {@code batchDelete} / {@code batchRemove}）—— 这些端点即使 body 实现了 {@link LockedCommand} 也不做版本校验（决策
     * C7）。
     */
    private static boolean isDeleteMethod(Method method) {
        if (method.getAnnotation(DeleteMapping.class) != null) {
            return true;
        }
        org.springframework.web.bind.annotation.RequestMapping mapping = org.springframework.core.annotation.AnnotatedElementUtils
                .findMergedAnnotation(method, org.springframework.web.bind.annotation.RequestMapping.class);
        if (mapping != null) {
            for (String path : mapping.path()) {
                int colon = path.lastIndexOf(':');
                if (colon >= 0
                        && DELETE_FAMILY_VERBS.contains(path.substring(colon + 1).toLowerCase(java.util.Locale.ROOT))) {
                    return true;
                }
            }
        }
        return DELETE_FAMILY_VERBS.contains(method.getName().toLowerCase(java.util.Locale.ROOT));
    }

    private static final java.util.Set<String> DELETE_FAMILY_VERBS = java.util.Set.of("delete", "undelete", "expunge",
            "purge", "batchdelete", "batchremove");

    /** 当前请求（无 HTTP 上下文——非 web 线程 / 单测直调 → null，检查跳过）。 */
    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
