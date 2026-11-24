package com.airedgesoft.lib.restful.openapi;

import com.airedgesoft.lib.restful.dryrun.DryRun;
import com.airedgesoft.lib.restful.filter.Filterable;
import com.airedgesoft.lib.restful.orderby.Sortable;
import com.airedgesoft.lib.restful.pagination.Page;
import com.airedgesoft.lib.restful.pagination.PaginationProperties;
import com.airedgesoft.lib.restful.softdelete.SoftDeleteProperties;
import com.airedgesoft.lib.restful.updatemask.ValidUpdateMask;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.parameters.Parameter;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.stream.Collectors;
import org.springdoc.core.customizers.GlobalOperationCustomizer;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.method.HandlerMethod;

/**
 * 契约参数文档<b>统一补齐</b>定制器：把框架全局拦截 / 注解驱动、Controller 方法签名不声明的协议参数补进 OpenAPI——
 * {@code updateMask}（{@link ValidUpdateMask}）、{@code filter}（{@link Filterable}）、{@code orderBy}（{@link Sortable}）、
 * {@code validateOnly}（{@link DryRun}）、{@code pageSize} / {@code pageToken}（返回 {@link Page} 的 GET）、
 * {@code showDeleted}（软删开启时的 GET）。
 *
 * <p>
 * <b>为何存在</b>：这些参数的运行时取值全部来自 {@code request.getParameter}（切面 / Filter），Controller 无需声明「死参数」；
 * 若文档不补齐，前端生成客户端将缺失契约。参数名与白名单<b>从注解 / 配置推导</b>，与运行时校验同源；已显式声明的参数不重复注入。
 *
 * <p>
 * <b>为何是 {@link GlobalOperationCustomizer}</b>：分组宿主（{@code springdoc.group-configs}）只合并 context 级 Global 定制器。
 */
public class ContractParameterOperationCustomizer implements GlobalOperationCustomizer {

    private final PaginationProperties pagination;
    private final SoftDeleteProperties softDelete;

    public ContractParameterOperationCustomizer() {
        this(null, null);
    }

    public ContractParameterOperationCustomizer(PaginationProperties pagination, SoftDeleteProperties softDelete) {
        this.pagination = pagination;
        this.softDelete = softDelete;
    }

    @Override
    public Operation customize(Operation operation, HandlerMethod handlerMethod) {
        Method method = handlerMethod.getMethod();
        boolean get = AnnotatedElementUtils.hasAnnotation(method, GetMapping.class);

        ValidUpdateMask updateMask = AnnotatedElementUtils.findMergedAnnotation(method, ValidUpdateMask.class);
        if (updateMask != null) {
            add(operation, updateMask.param(),
                    "增量更新掩码（逗号分隔；可选字段: " + fields(updateMask.whitelist()) + "；省略=全部已填充字段，*=全量替换，空串=no-op；未知字段 400）");
        }
        Filterable filterable = AnnotatedElementUtils.findMergedAnnotation(method, Filterable.class);
        if (filterable != null) {
            add(operation, filterable.param(),
                    "AIP-160 过滤表达式（可过滤字段: " + fields(filterable.value()) + "；非法字段 / 语法 400）");
        }
        Sortable sortable = AnnotatedElementUtils.findMergedAnnotation(method, Sortable.class);
        if (sortable != null) {
            add(operation, sortable.param(), "排序（逗号分隔，字段 asc/desc；可排序字段: " + fields(sortable.value()) + "；服务端追加主键兜底）");
        }
        DryRun dryRun = AnnotatedElementUtils.findMergedAnnotation(method, DryRun.class);
        if (dryRun != null) {
            add(operation, dryRun.param(), "预检（true = 完整执行校验后回滚，零落库；响应形态与真实执行一致）");
        }
        if (get && Page.class.isAssignableFrom(handlerMethod.getReturnType().getParameterType())) {
            int defaultSize = pagination == null
                    ? PaginationProperties.FALLBACK_DEFAULT_PAGE_SIZE
                    : pagination.getDefaultPageSize();
            int maxSize = pagination == null
                    ? PaginationProperties.FALLBACK_MAX_PAGE_SIZE
                    : pagination.getMaxPageSize();
            add(operation, "pageSize", "单页数量（缺省 / 0 取默认 " + defaultSize + "；负数 / 非数字 400；超上限钳到 " + maxSize + "）");
            add(operation, "pageToken", "下一页游标（AIP-158 不透明令牌；首页不传；非法 / 过期 400）");
        }
        if (get && softDelete != null && softDelete.isEnabled()) {
            add(operation, softDelete.getShowDeletedParam(), "回收站视图开关（true 放行已软删行；缺省隐藏）");
        }
        return operation;
    }

    private static void add(Operation operation, String name, String description) {
        boolean exists = operation.getParameters() != null
                && operation.getParameters().stream().anyMatch(p -> name.equals(p.getName()));
        if (!exists) {
            operation
                    .addParametersItem(new Parameter().name(name).in("query").required(false).description(description));
        }
    }

    private static String fields(String[] whitelist) {
        return whitelist == null || whitelist.length == 0
                ? "（运行时派生）"
                : Arrays.stream(whitelist).sorted().collect(Collectors.joining("/"));
    }
}
