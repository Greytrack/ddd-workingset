package com.airedgesoft.sharedkernel.businessframework.domain.businessobject.attributevaluevalidation;

import java.util.Objects;

/**
 * 属性值校验取数端口静态持有者（同 bridge {@code AuthorizationPorts} 约定）。
 *
 * <p>
 * 存在原因：校验族是静态工具类（策略单例 + 包级支撑），无法构造器注入；用极薄静态持有者承接， 装配期由 {@code v4-bridge-v3}
 * （{@code V3AttributeValueMetadataConfiguration}）安装实现，业务侧仅换调用入口。未安装时调用即失败，避免静默降级。
 */
public final class AttributeValueMetadataPorts {

    private static volatile AttributeValueMetadataPort port;

    private AttributeValueMetadataPorts() {
    }

    /** 取数端口；未安装时抛 {@link IllegalStateException} */
    public static AttributeValueMetadataPort port() {
        AttributeValueMetadataPort p = port;
        if (Objects.isNull(p)) {
            throw new IllegalStateException(
                    "AttributeValueMetadataPort 未安装：应由 v4-bridge-v3 在装配期调用 AttributeValueMetadataPorts.install(...)");
        }
        return p;
    }

    /** 装配期安装实现（v4-bridge-v3 专用；测试可整体替换） */
    public static void install(AttributeValueMetadataPort port) {
        AttributeValueMetadataPorts.port = Objects.requireNonNull(port, "port");
    }
}
