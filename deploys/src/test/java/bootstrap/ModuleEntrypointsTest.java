package bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.context.event.ApplicationListenerMethodAdapter;
import org.springframework.modulith.events.ApplicationModuleListener;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModuleEntrypointsTest {

    @Test
    void deploysDiscoversControllersAndKeepsPersistedListenerIds() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(true);
        String[] packages = WorkingsetApplication.class.getAnnotation(SpringBootApplication.class).scanBasePackages();
        Set<String> components = Arrays.stream(packages).flatMap(pkg -> scanner.findCandidateComponents(pkg).stream())
                .map(BeanDefinition::getBeanClassName).collect(Collectors.toSet());

        assertTrue(components.contains("alm.requirement.adapters.RequirementController"));
        assertTrue(components.contains("alm.requirement.application.eventhandler.RequirementDerivedItemSummaryListener"));
        assertTrue(components.contains("sk.workingset.adapters.WorkingSetController"));
        assertTrue(components.contains("bootstrap.web.ApiExceptionHandler"));

        Map<String, String> existingListeners = Map.of(
                "MergeRoundSubmittedListener", "MergeRoundSubmittedListener",
                "TrackerItemChangedListener", "RequirementChangedListener",
                "MergeAppliedListener", "RequirementMergeAppliedListener",
                "TrackerItemCompleteDeteleListener", "RequirementPurgedListener",
                "TrackerItemCreatedListener", "TrackerItemCreatedListener",
                "WorkingSetCreationSubmittedListener", "WorkingSetCreationSubmittedListener");
        for (Map.Entry<String, String> entry : existingListeners.entrySet()) {
            String className = "sk.workingset.application.eventhandler." + entry.getKey();
            assertTrue(components.contains(className));
            Class<?> listener = Class.forName(className);
            Method method = Arrays.stream(listener.getDeclaredMethods())
                    .filter(candidate -> candidate.isAnnotationPresent(ApplicationModuleListener.class))
                    .findFirst().orElseThrow();
            ApplicationListenerMethodAdapter adapter = new ApplicationListenerMethodAdapter(entry.getKey(), listener, method);
            assertEquals("bootstrap." + entry.getValue() + ".on(" + method.getParameterTypes()[0].getName() + ")",
                    adapter.getListenerId());
        }
    }
}
