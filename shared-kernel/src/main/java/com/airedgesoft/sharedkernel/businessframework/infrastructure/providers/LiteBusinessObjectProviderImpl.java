package com.airedgesoft.sharedkernel.businessframework.infrastructure.providers;

import com.airedgesoft.lib.jdbc.common.AirInstCommonPo;
import com.airedgesoft.lib.jdbc.common.JdbcCommonInstanceStore;
import com.airedgesoft.sharedkernel.businessframework.application.query.LiteBusinessObjectProvider;
import com.airedgesoft.sharedkernel.businessframework.domain.litebusinessobject.LiteBusinessObject;
import com.airedgesoft.sharedkernel.businessframework.domain.litebusinessobject.LiteBusinessObjectRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 业务对象只读快照提供者：直接读取 {@code air_inst_common} 通用列。
 *
 * <p>
 * 同时兑现应用查询端口 {@link LiteBusinessObjectProvider} 与领域只读仓储
 * {@link LiteBusinessObjectRepository}；两者语义相同，均按身份返回快照，缺失返回空。
 */
@Component
@RequiredArgsConstructor
public class LiteBusinessObjectProviderImpl implements LiteBusinessObjectProvider, LiteBusinessObjectRepository {

    private final JdbcCommonInstanceStore commonInstances;

    @Override
    public Optional<LiteBusinessObject> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return commonInstances.findById(id).map(LiteBusinessObjectProviderImpl::toView);
    }

    @Override
    public List<LiteBusinessObject> findByIds(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return commonInstances.findByIds(ids).stream()
                .map(LiteBusinessObjectProviderImpl::toView)
                .toList();
    }

    private static LiteBusinessObject toView(AirInstCommonPo row) {
        return new LiteBusinessObject(row.id(), row.baseClass(), row.clazz(), row.type(), row.workSpace(),
                row.commonId(), row.workingSetId(), row.parentId(), row.tracker(), row.number(), row.name(),
                row.status(), row.version(), integer(row.revision()), row.maxRevision(), row.deleteFlag(),
                row.checkedOut(), row.owner(), row.createdBy(), row.createdTime(), row.updatedBy(),
                row.updatedTime());
    }

    private static Integer integer(Long value) {
        return value == null ? null : Math.toIntExact(value);
    }
}
