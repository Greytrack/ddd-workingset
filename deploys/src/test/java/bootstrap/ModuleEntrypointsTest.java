package bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.event.ApplicationListenerMethodAdapter;
import org.springframework.modulith.events.ApplicationModuleListener;

class ModuleEntrypointsTest {

    @Test
    void deploysDiscoversControllersAndKeepsPersistedListenerIds() throws ClassNotFoundException {
        var scanner = new ClassPathScanningCandidateComponentProvider(true);
        var packages = WorkingsetApplication.class.getAnnotation(SpringBootApplication.class).scanBasePackages();
        var components = Arrays.stream(packages).flatMap(pkg -> scanner.findCandidateComponents(pkg).stream())
                .map(BeanDefinition::getBeanClassName).collect(Collectors.toSet());

        assertTrue(components.contains("alm.requirement.adapters.RequirementController"));
        assertTrue(components.contains("sk.workingset.adapters.WorkingSetController"));
        assertTrue(components.contains("bootstrap.web.ApiExceptionHandler"));

        var existingListeners = Set.of("MergeRoundSubmittedListener", "RequirementChangedListener",
                "RequirementMergeAppliedListener", "RequirementPurgedListener", "TrackerItemsIndexedListener",
                "TrackerItemCreatedListener", "WorkingSetCreationSubmittedListener");
        for (var name : existingListeners) {
            var className = "sk.workingset.application.eventhandler." + name;
            assertTrue(components.contains(className));
            var listener = Class.forName(className);
            var method = Arrays.stream(listener.getDeclaredMethods())
                    .filter(candidate -> candidate.isAnnotationPresent(ApplicationModuleListener.class))
                    .findFirst().orElseThrow();
            var adapter = new ApplicationListenerMethodAdapter(name, listener, method);
            assertEquals("bootstrap." + name + ".on(" + method.getParameterTypes()[0].getName() + ")",
                    adapter.getListenerId());
        }
    }
}
