// Guards against activity type-name collisions on the shared claim-task-queue.
// The Spring worker auto-discovers every @ActivityImpl on a task queue and registers them on
// one worker; two impls exposing the same activity type name fail at startup. This reproduces
// that registration on a test worker so the collision is caught here, not only in bootRun.
package com.ziggy.insurance.domains;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.ziggy.insurance.domains.claim.auto.AutoClaimActivitiesImpl;
import com.ziggy.insurance.domains.claim.property.PropertyClaimActivitiesImpl;
import com.ziggy.insurance.domains.policy.TaskQueues;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.Test;

class ClaimActivityRegistrationTest {

    // Both the auto and property claim activity impls are @ActivityImpl(taskQueues =
    // "claim-task-queue"), so the real worker registers both together. Their interfaces share
    // method names (verifyCoverage, assignAdjuster, dispatchFieldAdjuster), so
    // without a distinguishing namePrefix the second registration throws
    // TypeAlreadyRegisteredException. Registering both on one worker asserts the names are
    // disambiguated.
    @Test
    void autoAndPropertyClaimActivitiesRegisterOnSameTaskQueue() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            Worker worker = env.newWorker(TaskQueues.CLAIM_TASK_QUEUE);
            assertThatCode(() -> worker.registerActivitiesImplementations(
                    new AutoClaimActivitiesImpl(), new PropertyClaimActivitiesImpl()))
                .doesNotThrowAnyException();
        }
    }
}
