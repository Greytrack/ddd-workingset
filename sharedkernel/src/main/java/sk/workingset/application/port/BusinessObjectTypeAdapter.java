package sk.workingset.application.port;

import sk.workingset.application.query.provider.TrackerItemCatalogQueryProvider;
import sk.workingset.application.query.provider.TrackerItemQueryProvider;
import sk.workingset.domain.LocalId;

import java.util.Set;

/**
 * 一个业务对象类型对 Shared Kernel 提供的适配入口。
 *
 * <p>业务对象类型就是 Tracker 的 {@code tracker_class}/{@code classificationId}；
 * 一个 Tracker 只承载一种类型。新增业务 BC 时只需提供本接口实现，SK 不修改组合根或路由逻辑。
 */
public interface BusinessObjectTypeAdapter {

    /**
     * 本业务对象类型对应的 Tracker {@code tracker_class}/{@code classificationId} 集合。
     *
     * <p>一个业务对象类型可以对应多个分类实例（例如共享/非共享、租户自定义分类），
     * 但一个具体分类只能属于一个 Adapter。
     */
    Set<BusinessObjectType> types();

    Capabilities capabilities();

    /**
     * 按变体读取内容与生命周期证据；只读，不提供保存、整体校验或删除。
     */
    TrackerItemQueryProvider query(LocalId localId);

    /**
     * 按 Tracker 读取目录、存在性与家族信息。
     */
    TrackerItemCatalogQueryProvider catalog();

    /**
     * 来源行锁租约；所有会在 Working Set 中登记 Item 的类型都必须提供。
     */
    TrackerItemSourceLock sourceLock();
}
