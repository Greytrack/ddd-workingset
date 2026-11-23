package bootstrap;

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
        "com.airedgesoft.alm.requirement.adapters",
        "com.airedgesoft.alm.requirement.application",
        "com.airedgesoft.alm.requirement.infrastructure",
        "com.airedgesoft.sharedkernel.workingset.adapters",
        "com.airedgesoft.sharedkernel.workingset.application",
        "com.airedgesoft.sharedkernel.workingset.infrastructure",
        "com.airedgesoft.sharedkernel.businessframework.infrastructure.projection",
        "com.airedgesoft.sharedkernel.tracker.adapters",
        "com.airedgesoft.sharedkernel.tracker.application",
        "com.airedgesoft.sharedkernel.tracker.infrastructure",
        "com.airedgesoft.sharedkernel.productspace"
})
@MapperScan({
        "com.airedgesoft.sharedkernel.workingset.infrastructure.providers",
        "com.airedgesoft.alm.requirement.infrastructure.providers"
})
public class WorkingsetApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkingsetApplication.class, args);
    }
}
