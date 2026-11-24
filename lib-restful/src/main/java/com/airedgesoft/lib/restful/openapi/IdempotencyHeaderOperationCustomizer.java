package com.airedgesoft.lib.restful.openapi;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * 为所有 {@code POST} 端点<b>自动注入</b> {@code Idempotency-Key} 头参数文档的 springdoc 定制器。
 *
 * <p>
 * <b>为何存在</b>：幂等防护是框架默认行为（{@code IdempotencyFilter}，v4-restful-api.md §6.5），幂等键从请求头读取、方法参数不声明（审计裁决：禁止死参数）—— 若无本定制器，该头在
 * OpenAPI 中不可见，前端生成客户端将缺失契约。头文档从"每个端点手写 {@code @Parameters}"收口为"宿主按 POST 自动注入" （规范 §6/§9 第 11 条：运行时文档由宿主 springdoc 提供）。
 *
 * <p>
 * <b>为何是 {@link GlobalOperationCustomizer}</b>：springdoc 分组宿主（{@code springdoc.group-configs}）只把 context 级
 * {@code GlobalOperationCustomizer} 合并进每个分组，普通 {@code OperationCustomizer} 在分组模式下被丢弃——本头文档必须全分组生效，故实现全局定制器。
 *
 * <p>
 * <b>装配</b>：经 {@code OpenApiAutoConfiguration}（{@code @ConditionalOnClass(springdoc)}）注册； springdoc 缺席时静默跳过。
 */
public class IdempotencyHeaderOperationCustomizer implements GlobalOperationCustomizer {

    private static final String KEY_HEADER = com.airedgesoft.lib.restful.idempotency.IdempotencyFilter.KEY_HEADER;

    @Override
    public Operation customize(Operation operation, org.springframework.web.method.HandlerMethod handlerMethod) {
        boolean postEndpoint = AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(), PostMapping.class);
        if (postEndpoint) {
            boolean exists = operation.getParameters() != null
                    && operation.getParameters().stream().anyMatch(p -> KEY_HEADER.equals(p.getName()));
            if (!exists) {
                // 长流程（202 + LRO）端点必须携带幂等键（§16）：文档标 required=true，否则生成客户端照 spec 省略即恒 400
                boolean lroEndpoint = AnnotatedElementUtils.hasAnnotation(handlerMethod.getMethod(),
                        com.airedgesoft.lib.restful.lro.LroOperation.class);
                operation.addParametersItem(new Parameter().name(KEY_HEADER).in("header").required(lroEndpoint)
                        .description(lroEndpoint
                                ? "幂等键（客户端 UUID；**长流程端点必填**，缺失 400 300010030；重试必须复用同一 key）"
                                : "幂等键（客户端 UUID；重试必须复用同一 key；POST 端点框架默认幂等防护，携带即处理）"));
            }
        }
        return operation;
    }
}
