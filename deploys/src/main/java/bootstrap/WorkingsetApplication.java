package bootstrap;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Web 应用启动类：Spring Boot 承担内嵌容器启动、依赖装配与事件集成。
 *
 * <p>领域层（{@code sk.workingset.domain}、{@code alm.requirement.domain}）保持纯 Java，不依赖 Spring。
 */
@SpringBootApplication(scanBasePackages = {
        "bootstrap",
        "com.airedgesoft.lib.jdbc",
        "com.airedgesoft.lib.restful",
        // bridge-v3（v3 ↔ v4 勾连模块）：shared-kernel 端口的 v3 直读实现 Bean（V3* 实现）在此装配
        "com.airedgesoft.bridge.v3",
        "com.airedgesoft.alm.requirement",
        "com.airedgesoft.sharedkernel.workingset",
        "com.airedgesoft.sharedkernel.tracker",
        "com.airedgesoft.sharedkernel.productspace",
        "com.airedgesoft.sharedkernel.businessframework"
})
@MapperScan(basePackages = {
        "com.airedgesoft.sharedkernel.workingset.infrastructure",
        "com.airedgesoft.alm.requirement.infrastructure"
}, annotationClass = Mapper.class)
public class WorkingsetApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkingsetApplication.class, args);
    }
}
