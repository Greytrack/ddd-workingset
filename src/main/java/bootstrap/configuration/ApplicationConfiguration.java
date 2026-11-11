package bootstrap.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import alm.requirement.application.RequirementApplicationService;
import alm.requirement.infrastructure.InMemoryRequirementRepository;
import alm.requirement.infrastructure.RequirementAdapter;
import sk.workingset.application.WorkingSetApplicationService;
import sk.workingset.infrastructure.InMemoryLightTrackerItemRepository;

/**
 * 组合根（Composition Root）：唯一允许同时引用 SK 与 ALM 实现的位置（A1）。
 *
 * <p>S1 仅装配内存实现与 Adapter 工厂；不启动 Web 容器、不接入数据库。
 */
@Configuration
public class ApplicationConfiguration {

    @Bean
    public InMemoryLightTrackerItemRepository lightTrackerItemRepository() {
        return new InMemoryLightTrackerItemRepository();
    }

    @Bean
    public InMemoryRequirementRepository requirementRepository() {
        return new InMemoryRequirementRepository();
    }

    @Bean
    public RequirementApplicationService requirementApplicationService(InMemoryRequirementRepository requirements) {
        return new RequirementApplicationService(requirements);
    }

    @Bean
    public WorkingSetApplicationService workingSetApplicationService(
            InMemoryLightTrackerItemRepository items,
            InMemoryRequirementRepository requirements) {
        // SK 只依赖 TrackerItemReadPort 接口；Adapter 由组合根按变体身份构造
        return new WorkingSetApplicationService(items, localId -> new RequirementAdapter(requirements, localId));
    }
}
