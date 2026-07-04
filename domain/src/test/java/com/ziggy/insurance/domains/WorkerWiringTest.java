// Guards the Temporal spring-boot worker wiring: every activity and Nexus service impl the
// worker auto-discovers must be a Spring bean, or its handler is silently never registered.
package com.ziggy.insurance.domains;

import static org.assertj.core.api.Assertions.assertThat;

import com.ziggy.insurance.domains.notifications.NotificationServiceImpl;
import io.temporal.spring.boot.ActivityImpl;
import io.temporal.spring.boot.NexusServiceImpl;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

class WorkerWiringTest {

    private static final String BASE_PACKAGE = "com.ziggy.insurance.domains";

    // temporal-spring-boot-starter registers @ActivityImpl and @NexusServiceImpl handlers only
    // when they are Spring beans (@Component). Miss it and the worker polls the task queue but
    // serves no handler, so callers hang / time out (UPSTREAM_TIMEOUT for Nexus). @WorkflowImpl
    // types are exempt — Temporal, not Spring, instantiates them.
    @Test
    void activityAndNexusImplsAreSpringBeans() throws ClassNotFoundException {
        List<Class<?>> handlers = findWorkerHandlerImpls();

        // Guard against a vacuous pass: the scan must actually find the handlers it checks,
        // including the notifications Nexus service that regressed.
        assertThat(handlers)
            .as("scan should discover worker handler impls")
            .contains(NotificationServiceImpl.class);

        List<Class<?>> missingComponent = new ArrayList<>();
        for (Class<?> impl : handlers) {
            if (!impl.isAnnotationPresent(Component.class)) {
                missingComponent.add(impl);
            }
        }
        assertThat(missingComponent)
            .as("@ActivityImpl/@NexusServiceImpl classes must be @Component for worker auto-discovery")
            .isEmpty();
    }

    // Scans the domain package for every class carrying @ActivityImpl or @NexusServiceImpl,
    // regardless of whether it is a Spring bean, so a handler that forgot @Component is caught.
    private List<Class<?>> findWorkerHandlerImpls() throws ClassNotFoundException {
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ActivityImpl.class));
        scanner.addIncludeFilter(new AnnotationTypeFilter(NexusServiceImpl.class));

        List<Class<?>> classes = new ArrayList<>();
        for (BeanDefinition beanDef : scanner.findCandidateComponents(BASE_PACKAGE)) {
            classes.add(Class.forName(beanDef.getBeanClassName()));
        }
        return classes;
    }
}
