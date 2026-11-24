package com.airedgesoft.sharedkernel.businessframework.infrastructure.providers;

import com.airedgesoft.sharedkernel.businessframework.application.query.ClassificationQueryProvider;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.metadata.MetadataCachePort;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.claasification.model.ClassSnapshot;
import com.airedgesoft.sharedkernel.workingset.domain.SharingMode;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * 分类定义信息查询实现类
 * 两种方式：1通过本地数据库查询 2通过外部服务端口查询
 */
@Component
@RequiredArgsConstructor
public class ClassificationQueryProviderImpl implements ClassificationQueryProvider {

    /**
     * 1通过本地数据库查询
     */
    private final JdbcTemplate jdbc;
    /**
     * 2通过外部服务端口查询
     */
    //private final MetadataCachePort metadataCachePort;




    private static final RowMapper<ClassSnapshot> CLASSIFICATION = (row, position) ->
            ClassSnapshot.forTracker(row.getLong(1), SharingMode.valueOf(row.getString(2)) == SharingMode.SHARED, row.getString(3),
                    row.getLong(4), row.getLong(5));
    private static final String SELECT = "SELECT classification_id, sharing_mode, name, base_class_id, class_id FROM classifications";


    @Override
    public Optional<ClassSnapshot> findClassification(String id) {
        return jdbc.query(SELECT + " WHERE classification_id = ?",
                CLASSIFICATION, Long.valueOf(id)).stream().findFirst();
    }

    @Override
    public List<ClassSnapshot> findClassifications() {
        return jdbc.query(SELECT + " ORDER BY classification_id",
                CLASSIFICATION);
    }
}
