package com.airedgesoft.lib.restful.openapi;

import com.airedgesoft.lib.restful.readmask.ReadMaskAspect;
import com.airedgesoft.lib.restful.readmask.ReadMaskWhitelists;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.util.Set;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.method.HandlerMethod;

/**
 * 为 GET 端点<b>自动注入</b> {@code readMask} 投影参数文档的 springdoc 定制器（遵循矩阵「部分读取与字段投影」行，Tier A）。
 *
 * <p>
 * <b>为何存在</b>：readMask 由框架全局拦截（{@code ReadMaskAspect}），Controller 方法参数不声明（Tier A 零注解）——若无本定制器，该参数在 OpenAPI
 * 中不可见，前端生成客户端将缺失投影契约。可选字段清单从方法返回类型自动派生（{@link ReadMaskWhitelists}），与运行时白名单校验同源。
 *
 * <p>
 * <b>为何是 {@link GlobalOperationCustomizer}</b>：springdoc 分组宿主（{@code springdoc.group-configs}）只把 context 级
 * {@code GlobalOperationCustomizer} 合并进每个分组，普通 {@code OperationCustomizer} 在分组模式下被静默丢弃——投影文档必须全分组生效。
 *
 * <p>
 * <b>装配</b>：经 {@code OpenApiAutoConfiguration}（{@code @ConditionalOnClass(springdoc)}）注册；springdoc 缺席时静默跳过。仅覆盖方法级
 * {@code @GetMapping} 端点（集合/详情读路径惯例）——运行时投影由 {@code ReadMaskAspect} 对全部 {@code @RestController} 方法生效（任意携带
 * {@code ?readMask} 的响应均按掩码剪枝），参数文档只在标准读端点注入；返回类型派生不出 record 白名单的端点不注入。
 */
public class ReadMaskParameterOperationCustomizer implements GlobalOperationCustomizer {

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        if (!AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), GetMapping.class)) {
            return operation;
        }
        Set<String> whitelist = ReadMaskWhitelists.of(handlerMethod.getMethod());
        if (whitelist.isEmpty()) {
            return operation;
        }
        boolean exists = operation.getParameters() != null
                && operation.getParameters().stream().anyMatch(p -> ReadMaskAspect.READ_MASK_PARAM.equals(p.getName()));
        if (!exists) {
            // 白名单排序后拼接：Set 无序，排序保证文档字段顺序稳定（跨重启确定）
            String fields = whitelist.stream().sorted().collect(Collectors.joining("/"));
            operation.addParametersItem(new Parameter().name(ReadMaskAspect.READ_MASK_PARAM).in("query").required(false)
                    .description("字段投影（逗号分隔；可选字段: " + fields + "；未知字段 400；name 恒保留；掩码外字段置 null 不输出）"));
        }
        return operation;
    }
}
