package com.airedgesoft.alm.requirement.adapters.workingset;

import com.airedgesoft.alm.requirement.application.RequirementTypes;
import com.airedgesoft.alm.requirement.application.query.provider.RequirementQueryProvider;
import com.airedgesoft.alm.requirement.infrastructure.providers.RequirementTrackerItemCatalogQueryProvider;
import com.airedgesoft.sharekernel.workingset.application.port.BusinessObjectTypeAdapter;
import com.airedgesoft.sharekernel.workingset.application.port.Capabilities;
import com.airedgesoft.sharekernel.workingset.application.query.provider.TrackerItemCatalogQueryProvider;
import com.airedgesoft.sharekernel.workingset.application.query.provider.TrackerItemQueryProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Requirement 业务对象类型适配入口。
 *
 * <p>ALM 的内部实现（查询、目录、锁）仍各自独立；本类只负责能力声明与组装，
 * 使 SK 不依赖任何 ALM 类型。公共查询视图的投影由 ALM 消费索引完成事件后自行处理。
 */
@Component
@RequiredArgsConstructor
public class RequirementTypeAdapter implements BusinessObjectTypeAdapter {

    private static final Capabilities CAPABILITIES = new Capabilities(true, true);

    private final RequirementQueryProvider query;
    private final RequirementTrackerItemCatalogQueryProvider catalog;

    @Override
    public Set<Long> types() {
        return RequirementTypes.SUPPORTED;
    }

    @Override
    public Capabilities capabilities() {
        return CAPABILITIES;
    }

    @Override
    public TrackerItemQueryProvider query(Long localId) {
        return new RequirementReadModelAdapter(query, localId);
    }

    @Override
    public TrackerItemCatalogQueryProvider catalog() {
        return catalog;
    }

}
