package com.airedgesoft.lib.restful.etag;

import com.airedgesoft.lib.restful.softdelete.SoftDeleteContext;
import com.airedgesoft.lib.restful.persistence.AggregateSnapshots;
import org.springframework.data.mapping.PersistentPropertyAccessor;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.relational.core.mapping.RelationalPersistentEntity;
import org.springframework.data.relational.core.mapping.event.AfterConvertCallback;
import org.springframework.data.relational.core.mapping.event.AfterSaveCallback;

/**
 * Spring Data JDBC <b>实体生命周期自动观测</b>（规范 §6.7）：聚合从数据库加载（{@link AfterConvertCallback}）或保存（{@link AfterSaveCallback}）时，
 * 依持久化元数据提取 {@code @Id} / {@code @Version}，把存储版本登记进 {@link EtagContext}—— 仓储与映射器不再手工登记加载 / 保存版本， 业务侧只需建表含版本列（实体声明
 * {@code @Version}），并发代数全链路透明。
 *
 * <p>
 * <b>覆盖面</b>：注册为 Bean 后，由 Spring Data JDBC 的 {@code JdbcAggregateTemplate}（仓储接口与直接注入两种形态共用同一模板）经
 * {@code EntityCallbacks} 自动发现并触发；MyBatis-Plus / 裸 {@code JdbcTemplate} 读路径不经模板，仍由业务侧显式登记（无版本列读视图场景）。
 *
 * <p>
 * <b>加载期职责</b>：① 登记加载快照（{@link AggregateSnapshots}，供存库时取 CAS 基线）；② 登记当前版本（{@link EtagContext}，供 ETag 装配）； ③ 版本
 * 前置条件断言（不一致 → 409，在写入生效前拒绝）。
 */
public class EtagEntityLifecycleCallback implements AfterConvertCallback<Object>, AfterSaveCallback<Object> {

    private final MappingContext<? extends RelationalPersistentEntity<?>, ?> mappingContext;

    public EtagEntityLifecycleCallback(MappingContext<? extends RelationalPersistentEntity<?>, ?> mappingContext) {
        this.mappingContext = mappingContext;
    }

    @Override
    public Object onAfterConvert(Object aggregate) {
        observe(aggregate, false);
        return aggregate;
    }

    @Override
    public Object onAfterSave(Object aggregate) {
        observe(aggregate, true);
        return aggregate;
    }

    /**
     * 提取身份与存储版本并登记。<b>实体无 {@code @Version} 属性（表无版本列）时整体跳过登记</b>—— 「未配置版本来源」不是「版本为 NULL」：恒定假代数（如恒
     * {@code 1}）会让客户端回传后永远通过，并发保护形同虚设且无人察觉 （规范 §6.7 规则 8：身份未知 → 不注入 {@code lockedVersion}；ETag 仍为表示 hash）。
     */
    private void observe(Object aggregate, boolean saved) {
        if (aggregate == null) {
            return;
        }
        RelationalPersistentEntity<?> entity = mappingContext.getRequiredPersistentEntity(aggregate.getClass());
        if (!entity.hasVersionProperty()) {
            return;
        }
        Object identity = entity.getIdentifierAccessor(aggregate).getIdentifier();
        PersistentPropertyAccessor<Object> accessor = entity.getPropertyAccessor(aggregate);
        Object raw = accessor.getProperty(entity.getRequiredVersionProperty());
        Long storageVersion = raw == null ? null : Long.valueOf(((Number) raw).longValue());
        if (saved) {
            EtagContext.recordSaved(identity, aggregate, storageVersion);
            AggregateSnapshots.recordSaved(identity, null, storageVersion);
            return;
        }
        if (isSoftDeleted(entity, accessor)) {
            // 回收站行：登记但不做版本前置断言（软删资源对并发前置不可见——单体读照常返回）
            EtagContext.recordObserved(identity, aggregate, storageVersion);
        } else {
            EtagContext.recordLoaded(identity, aggregate, storageVersion);
        }
        AggregateSnapshots.recordLoaded(identity, aggregate, storageVersion);
    }

    /**
     * 软删行识别：实体中映射到删除标志列（{@link SoftDeleteContext#flagColumn()}）的属性值等于已删值即已删。 列名取自框架配置，属性按列名反查—— BC 无需为此做任何声明。
     */
    private static boolean isSoftDeleted(RelationalPersistentEntity<?> entity,
            PersistentPropertyAccessor<Object> accessor) {
        String column = SoftDeleteContext.flagColumn();
        if (column == null || column.isBlank()) {
            return false;
        }
        for (org.springframework.data.relational.core.mapping.RelationalPersistentProperty property : entity) {
            if (!column.equalsIgnoreCase(property.getColumnName().getReference())) {
                continue;
            }
            Object value = accessor.getProperty(property);
            if (value instanceof Number number) {
                return number.intValue() == SoftDeleteContext.deletedValue();
            }
            if (value instanceof Boolean flag) {
                return flag;
            }
            return false;
        }
        return false;
    }
}
