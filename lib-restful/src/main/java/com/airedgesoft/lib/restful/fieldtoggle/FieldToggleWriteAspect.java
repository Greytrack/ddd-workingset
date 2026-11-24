package com.airedgesoft.lib.restful.fieldtoggle;

import com.airedgesoft.lib.restful.DomainRestCode;
import com.airedgesoft.lib.restful.path.RequestCollections;
import com.airedgesoft.lib.restful.updatemask.UpdateMask;
import com.airedgesoft.lib.restful.updatemask.ValidUpdateMask;
import com.airedgesoft.lib.restful.updatemask.UpdateMaskAspect;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Set;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 预定义字段开关<b>写路径</b>切面（遵循矩阵「预定义字段开关」行第 3 条）：{@code @ValidUpdateMask} 端点的 updateMask （AIP-161：掩码即请求体字段路径的选择器——掩码外 body
 * 字段不参与变更，故掩码通道已覆盖「请求体引用」面）<b>点名</b>引用当前停用字段 → <b>400 + 逐字段
 * {@code fieldViolations}</b>（字段不存在语义，{@link DomainRestCode#PREDEFINED_FIELD_DISABLED}），方法体不执行。
 *
 * <p>
 * <b>省略 / {@code *} 不 400，静默剔除</b>：省略掩码（=全部已填充）与 {@code *}（=全量替换）的候选集都表示「全部<b>可写</b>字段」—— 停用字段不属于可写集（§20
 * 「字段不存在」语义），从候选集剔除而非 400（否则部署期一旦停用某字段，任何省略 / {@code *} PATCH 恒 400 且客户端无从规避——它从不点名该字段）； 与读侧「省略掩码=停用字段静默剪枝」同口径，两态方向一致。
 *
 * <p>
 * <b>与 {@link UpdateMaskAspect} 的协作（校验链第 2 步）</b>：本切面 {@code @Order(HIGHEST_PRECEDENCE + 20)} 内层于 {@code +16} 的
 * {@link UpdateMaskAspect}——其先完成掩码解析与静态白名单校验（未知字段 400）并把解析结果挂请求属性， 本切面复用 {@link UpdateMaskAspect#currentMask()}
 * 只补「停用字段」裁决：先 400 未知、后 400 停用 / 剔除，一次请求违规逐层暴露。
 *
 * <p>
 * <b>声明式接管（Tier B）</b>：停用集合由 BC 经 {@link FieldToggleProvider} Bean 声明（配置中心承载启停），框架收口执行； <b>无 Provider Bean 时本切面由
 * {@link FieldToggleAutoConfiguration} 整体不装配</b>——读写路径零改动、零开销（静默不激活）。
 */
@Aspect
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class FieldToggleWriteAspect {

    private final FieldToggleProvider provider;

    public FieldToggleWriteAspect(FieldToggleProvider provider) {
        this.provider = provider;
    }

    @Around("@annotation(com.airedgesoft.lib.restful.updatemask.ValidUpdateMask)")
    public Object around(ProceedingJoinPoint pjp) throws Throwable {
        HttpServletRequest request = currentRequest();
        UpdateMask mask = UpdateMaskAspect.currentMask();
        if (request == null || mask == null) {
            return pjp.proceed();
        }
        Set<String> disabled = provider.disabledFields(RequestCollections.of(request));
        if (disabled == null || disabled.isEmpty()) {
            return pjp.proceed();
        }
        UpdateMask.Mode requestedMode = requestedMode(pjp, request);
        if (requestedMode == UpdateMask.Mode.FULL_REPLACE || requestedMode == UpdateMask.Mode.ALL_POPULATED) {
            // 省略 / `*` = 「全部**可写**字段」：停用字段不属于可写集（§20「字段不存在」语义），从候选集剔除而非 400——
            // 否则部署期一旦停用某字段，任何省略 / `*` PATCH 恒 400 且客户端无从规避（它从不点名该字段）；读侧省略掩码同为静默剪枝。
            List<String> kept = mask.paths().stream().filter(path -> !disabled.contains(path)).toList();
            UpdateMaskAspect.override(request, new UpdateMask(kept));
            return pjp.proceed();
        }
        FieldToggleViolations.assertPathsEnabled(mask.paths(), disabled);
        return pjp.proceed();
    }

    /**
     * 请求的原始掩码模式（省略 → {@code ALL_POPULATED}、{@code *} → {@code FULL_REPLACE}；EXPLICIT / NO_OP 走点名 400 口径）。 原始串已经
     * {@code UpdateMaskAspect}（+16 外层）解析校验，此处重解析不触异常。
     */
    private static UpdateMask.Mode requestedMode(ProceedingJoinPoint pjp, HttpServletRequest request) {
        ValidUpdateMask annotation = ((MethodSignature) pjp.getSignature()).getMethod()
                .getAnnotation(ValidUpdateMask.class);
        String raw = request.getParameter(annotation == null ? "updateMask" : annotation.param());
        return UpdateMask.parse(raw).mode();
    }

    private static HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
