package com.airedgesoft.sharedkernel.businessframework.domain.admin.claasification;

import java.util.List;
import java.util.Set;

/** 读取分类层级关系（仅元数据，不含任何判定规则）。 */
public interface ClassHierarchyReader {

    /** 该分类是否为基类。 */
    boolean isBaseClass(Long classId);

    /**
     * 创建权限匹配所需的分类邻域。
     *
     * <p>
     * 基类包含其全部下级分类及下级的父类，中类包含父类与基类，子类包含父类与基类。
     * </p>
     *
     * @param classId
     *            分类、中类或子类 ID
     * @return 邻域内全部分类 ID（含自身）
     */
    Set<Long> createNeighborhood(Long classId);

    /**
     * 创建权限命中某分类后实际可创建的分类集合。
     *
     * <p>
     * 基类向全部下级展开，中类向子类展开，子类回溯父类与基类。
     * </p>
     *
     * @param classId
     *            权限配置所挂分类 ID
     * @return 可创建的分类 ID 集合（含自身）
     */
    List<Long> expandCreatableClasses(Long classId);
}
