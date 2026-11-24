package com.airedgesoft.lib.restful.batch;

import com.airedgesoft.lib.exception.DomainException;
import com.airedgesoft.lib.restful.DomainRestCode;
import java.lang.reflect.RecordComponent;
import java.util.Collection;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;

/**
 * {@link BatchLimit} 切面：方法执行前校验全部 Collection 型实参条数，任一超限即 400 快速失败（方法体不执行）—— v4-restful-api.md §4.1 /
 * §4.1.3「同步批量上限」：所有批量操作必须在适配层入口设定硬性条目数量上限（demo 基准 200）。
 *
 * <p>
 * <b>实参形态覆盖</b>：
 * <ul>
 * <li><b>顶层 Collection</b>（{@code List<String> ids}）直接取 {@code size()} 校验；</li>
 * <li><b>record 包装</b>（如 {@code record BatchUpdateDemoTaskRequest(List<Item> items, ...) implements ...}）——经
 * {@link Class#isRecord()} 探测后遍历 {@link RecordComponent} 反射取值，值为 Collection 的组件逐一取 {@code size()} 校验（声明序）。</li>
 * </ul>
 * record 内含多个 Collection 时<b>全部逐一校验</b>（取第一个即判会漏报第二个超限；全部校验语义正确且实现同样简单——防「一次批量拉全表」的容量攻击不允许从次要集合绕过）。只展开一层（批量 Command 的
 * Collection 字段即批量本体，更深嵌套非批量语义，交给字段级校验）。null 实参 / 非 record 非 Collection 实参（散参、String 等）跳过。
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 18)
public class BatchLimitAspect {

    private static final int DEFAULT_MAX = 200;

    private final int globalMax;

    public BatchLimitAspect() {
        this(DEFAULT_MAX);
    }

    public BatchLimitAspect(int globalMax) {
        this.globalMax = globalMax <= 0 ? DEFAULT_MAX : globalMax;
    }

    @Around("@annotation(com.airedgesoft.lib.restful.batch.BatchLimit)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        BatchLimit annotation = ((MethodSignature) pjp.getSignature()).getMethod().getAnnotation(BatchLimit.class);
        if (annotation != null) {
            int limit = annotation.max() > 0 ? annotation.max() : globalMax;
            for (Object argument : pjp.getArgs()) {
                check(argument, limit);
            }
        }
        return pjp.proceed();
    }

    /** 单实参校验：顶层 Collection / 数组 / Map 直接取条数；record 则展开一层组件，批量型值逐一取条数。 */
    private static void check(Object argument, int limit) {
        if (argument == null) {
            return;
        }
        if (isBatchContainer(argument)) {
            enforce(sizeOf(argument), limit);
            return;
        }
        if (argument.getClass().isRecord()) {
            for (RecordComponent component : argument.getClass().getRecordComponents()) {
                Object value = componentValue(argument, component);
                if (value != null && isBatchContainer(value)) {
                    enforce(sizeOf(value), limit);
                }
            }
        }
    }

    /** 批量容器形态：Collection / 数组 / Map（AIP-231/233/234/235 批量本体均可能以此承载）。 */
    private static boolean isBatchContainer(Object value) {
        return value instanceof Collection<?> || value instanceof java.util.Map<?, ?> || value.getClass().isArray();
    }

    private static int sizeOf(Object container) {
        if (container instanceof Collection<?> collection) {
            return collection.size();
        }
        if (container instanceof java.util.Map<?, ?> map) {
            return map.size();
        }
        return java.lang.reflect.Array.getLength(container);
    }

    private static void enforce(int size, int limit) {
        if (size > limit) {
            throw new DomainException(DomainRestCode.BATCH_LIMIT_EXCEEDED, size, limit);
        }
    }

    private static Object componentValue(Object record, RecordComponent component) {
        try {
            return component.getAccessor().invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                    "批量实参 record 组件取值失败: " + record.getClass().getSimpleName() + "." + component.getName(), e);
        }
    }
}
