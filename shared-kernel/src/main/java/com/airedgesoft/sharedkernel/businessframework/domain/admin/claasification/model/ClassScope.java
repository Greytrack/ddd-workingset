package com.airedgesoft.sharedkernel.businessframework.domain.admin.claasification.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/** 分类匹配所需的基类和适用分类。 */
public record ClassScope(Long baseClassId, List<Long> applicableClassIds) {
    public ClassScope {
        Objects.requireNonNull(baseClassId, "baseClassId");
        applicableClassIds = List.copyOf(applicableClassIds);
    }

    public static ClassScope fromHierarchy(Long baseClassId, Long parentClassId, Long classId) {
        Objects.requireNonNull(classId, "classId");
        var scope = new LinkedHashSet<Long>();
        if (baseClassId != null) {
            scope.add(baseClassId);
        }
        if (parentClassId != null) {
            scope.add(parentClassId);
        }
        scope.add(classId);
        return new ClassScope(baseClassId == null ? classId : baseClassId, List.copyOf(scope));
    }
}
