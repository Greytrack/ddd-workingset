package com.airedgesoft.sharedkernel.businessframework.infrastructure.providers;

import com.airedgesoft.sharedkernel.businessframework.application.query.ClassificationQueryProvider;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.claasification.model.ClassSnapshot;
import com.airedgesoft.sharedkernel.businessframework.domain.admin.metadata.MetadataCachePort;
import lombok.RequiredArgsConstructor;
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
     * 1通过本地数据库查询 private final JdbcTemplate jdbc
     * 2通过外部服务端口查询 V3 bridge实现
     */
    private final MetadataCachePort metadataCachePort;



  @Override
  public Optional<ClassSnapshot> findClassification(Long id) {
    return Optional.of(metadataCachePort.classSnapshot(id));
  }

    @Override
    public List<ClassSnapshot> findClassifications() {
        return metadataCachePort.allSubClasses();
    }
}
