// Integration tests for the AI-adjuster path of PropertyClaimWorkflow (spec §6.2, §9).
// The Python agents are exercised end-to-end by the mise demo tasks; here we substitute
// deterministic Temporal child workflows registered under the SAME workflow type names
// ("FieldAdjusterWorkflow" / "ClaimAdjusterWorkflow") on ai-agents-task-queue, returning canned
// reports. This proves the Java routing, the cross-language request shape, and the state
// mutations without depending on an LLM.
package com.ziggy.insurance.domains.claim.property;

import static org.assertj.core.api.Assertions.assertThat;

import com.ziggy.insurance.domains.claim.models.ClaimStatus;
import com.ziggy.insurance.domains.claim.models.DamageAssessmentResult;
import com.ziggy.insurance.domains.claim.property.agents.AgentClaimDecisionRequest;
import com.ziggy.insurance.domains.claim.property.agents.AgentDamageAssessment;
import com.ziggy.insurance.domains.claim.property.agents.AgentApprovalRecommendation;
import com.ziggy.insurance.domains.claim.property.agents.AgentFieldAdjusterRequest;
import com.ziggy.insurance.domains.claim.property.agents.ClaimDecisionReport;
import com.ziggy.insurance.domains.claim.property.agents.FieldAdjusterReport;
import com.ziggy.insurance.domains.claim.search.ClaimSearchAttributes;
import com.ziggy.insurance.domains.notifications.NotificationActivitiesImpl;
import com.ziggy.insurance.domains.notifications.NotificationServiceImpl;
import com.ziggy.insurance.domains.notifications.NotificationWorkflowImpl;
import com.ziggy.insurance.domains.notifications.NotificationsNexus;
import com.ziggy.insurance.domains.payment.PaymentActivities;
import com.ziggy.insurance.domains.payment.PaymentNexus;
import com.ziggy.insurance.domains.payment.PaymentServiceImpl;
import com.ziggy.insurance.domains.payment.PaymentWorkflowImpl;
import com.ziggy.insurance.domains.policy.TaskQueues;
import io.temporal.api.enums.v1.IndexedValueType;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;
import org.junit.jupiter.api.Test;

class PropertyClaimAiAdjusterWorkflowTest {

    // ── Deterministic agent stand-ins (registered under the agents' workflow type names) ────

    @WorkflowInterface
    public interface FieldAdjusterWorkflow {
        @WorkflowMethod
        FieldAdjusterReport run(AgentFieldAdjusterRequest request);
    }

    // Field adjuster stub: assesses a fixed $20,000 of damage and recommends a payout. The
    // recommendation is informational; the claim adjuster makes the binding decision.
    public static class FieldAdjusterWorkflowImpl implements FieldAdjusterWorkflow {
        @Override
        public FieldAdjusterReport run(AgentFieldAdjusterRequest request) {
            return new FieldAdjusterReport(
                new AgentDamageAssessment("AI assessment for " + request.claim().claimId(), 20_000),
                new AgentApprovalRecommendation("adj-ai-agent", 19_000, "recommended"));
        }
    }

    @WorkflowInterface
    public interface ClaimAdjusterWorkflow {
        @WorkflowMethod
        ClaimDecisionReport run(AgentClaimDecisionRequest request);
    }

    // Claim adjuster stub (approve): pays the assessed cost minus the deductible, clamped at 0.
    public static class ApprovingClaimAdjusterWorkflowImpl implements ClaimAdjusterWorkflow {
        @Override
        public ClaimDecisionReport run(AgentClaimDecisionRequest request) {
            int payout = Math.max(0,
                request.assessment().estimatedCost() - request.coverage().deductible());
            return new ClaimDecisionReport(
                true, payout, "adj-ai-agent", "Approved by AI adjuster", null,
                "Coverage verified; repair cost exceeds the deductible.");
        }
    }

    // Claim adjuster stub (deny): rejects the claim with a reason and a zero payout.
    public static class DenyingClaimAdjusterWorkflowImpl implements ClaimAdjusterWorkflow {
        @Override
        public ClaimDecisionReport run(AgentClaimDecisionRequest request) {
            return new ClaimDecisionReport(
                false, 0, "adj-ai-agent", "", "AI: loss is not a covered peril.",
                "Excluded peril under the verified coverage.");
        }
    }

    // Field adjuster stub that fails if ever invoked — used to prove the field-adjuster agent is
    // NOT run when a human already submitted the assessment (mixed mode).
    public static class UnusedFieldAdjusterWorkflowImpl implements FieldAdjusterWorkflow {
        @Override
        public FieldAdjusterReport run(AgentFieldAdjusterRequest request) {
            throw new IllegalStateException("field adjuster agent must not run after a human assessment");
        }
    }

    // Delay-free, non-flaky payment stand-in so the payout settles instantly.
    static class FastPaymentActivities implements PaymentActivities {
        @Override
        public String disburse(String claimId, String policyHolderId, int amount) {
            return "pay-" + claimId;
        }
    }

    // ── Fixtures ────────────────────────────────────────────────────────────────────────────

    private PropertyClaimInput portalInput(String claimId) {
        return new PropertyClaimInput(
            claimId, "demo-property-001", "PH-001",
            null, null,
            "Wind tore off roof shingles", 1_750_000_000L,
            "742 Evergreen Terrace", "SINGLE_FAMILY");
    }

    private PropertyClaimInput aiEnabledInput(String claimId) {
        return new PropertyClaimInput(
            claimId, "demo-property-001", "PH-001",
            null, null,
            "Wind tore off roof shingles", 1_750_000_000L,
            "742 Evergreen Terrace", "SINGLE_FAMILY", true);
    }

    private WorkflowOptions options(String workflowId) {
        return WorkflowOptions.newBuilder()
            .setTaskQueue(TaskQueues.CLAIM_TASK_QUEUE)
            .setWorkflowId(workflowId)
            .build();
    }

    private void registerSearchAttributes(TestWorkflowEnvironment env) {
        env.registerSearchAttribute(ClaimSearchAttributes.POLICY_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
        env.registerSearchAttribute(ClaimSearchAttributes.POLICY_HOLDER_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
        env.registerSearchAttribute(ClaimSearchAttributes.CLAIM_STATUS, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
        env.registerSearchAttribute(ClaimSearchAttributes.CAT_EVENT_ID, IndexedValueType.INDEXED_VALUE_TYPE_KEYWORD);
    }

    private void registerNotifications(TestWorkflowEnvironment env) {
        Worker w = env.newWorker(NotificationsNexus.TASK_QUEUE);
        w.registerNexusServiceImplementation(new NotificationServiceImpl());
        w.registerWorkflowImplementationTypes(NotificationWorkflowImpl.class);
        w.registerActivitiesImplementations(new NotificationActivitiesImpl());
        env.createNexusEndpoint(NotificationsNexus.ENDPOINT, NotificationsNexus.TASK_QUEUE);
    }

    private void registerPayment(TestWorkflowEnvironment env) {
        Worker w = env.newWorker(PaymentNexus.TASK_QUEUE);
        w.registerNexusServiceImplementation(new PaymentServiceImpl());
        w.registerWorkflowImplementationTypes(PaymentWorkflowImpl.class);
        w.registerActivitiesImplementations(new FastPaymentActivities());
        env.createNexusEndpoint(PaymentNexus.ENDPOINT, PaymentNexus.TASK_QUEUE);
    }

    // Stands up the claim worker plus the agent stand-ins on ai-agents-task-queue.
    private Worker standUpClaimAndAgents(
            TestWorkflowEnvironment env,
            Class<?> fieldAdjusterImpl,
            Class<?> claimAdjusterImpl) {
        Worker claimWorker = env.newWorker(TaskQueues.CLAIM_TASK_QUEUE);
        claimWorker.registerWorkflowImplementationTypes(PropertyClaimWorkflowImpl.class);
        claimWorker.registerActivitiesImplementations(new PropertyClaimActivitiesImpl());

        Worker agentsWorker = env.newWorker(TaskQueues.AI_AGENTS_TASK_QUEUE);
        agentsWorker.registerWorkflowImplementationTypes(fieldAdjusterImpl, claimAdjusterImpl);

        registerNotifications(env);
        registerPayment(env);
        return claimWorker;
    }

    private PropertyClaimState awaitStatus(PropertyClaimWorkflow wf, ClaimStatus expected) {
        long deadline = System.currentTimeMillis() + 10_000;
        PropertyClaimState state = wf.getClaim();
        while (state.getStatus() != expected && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
            state = wf.getClaim();
        }
        assertThat(state.getStatus()).isEqualTo(expected);
        return state;
    }

    // ── Tests ───────────────────────────────────────────────────────────────────────────────

    @Test
    void enableWhileParkedAtDamageAssessmentRunsBothAgentsAndCloses() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            registerSearchAttributes(env);
            standUpClaimAndAgents(env,
                FieldAdjusterWorkflowImpl.class, ApprovingClaimAdjusterWorkflowImpl.class);
            env.start();

            PropertyClaimWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                PropertyClaimWorkflow.class, options("claim/property/test-ai-takeover"));
            WorkflowClient.start(wf::run, portalInput("CLM-AI-TAKEOVER-001"));

            // Park on the human wait, then flip to AI mid-wait.
            awaitStatus(wf, ClaimStatus.PENDING_DAMAGE_ASSESSMENT);
            assertThat(wf.getClaim().isAiAdjusterEnabled()).isFalse();
            wf.enableAiAdjuster();

            PropertyClaimState closed = WorkflowStub.fromTyped(wf).getResult(PropertyClaimState.class);
            assertThat(closed.getStatus()).isEqualTo(ClaimStatus.CLOSED);
            assertThat(closed.isAiAdjusterEnabled()).isTrue();
            // Field-adjuster agent produced the assessment...
            assertThat(closed.getDamageAssessment()).isEqualTo("AI assessment for CLM-AI-TAKEOVER-001");
            assertThat(closed.getEstimatedRepairCost()).isEqualTo(20_000);
            // ...and the claim-adjuster agent made the binding approval (20000 - 1000 deductible).
            assertThat(closed.getApprovedByAdjusterId()).isEqualTo("adj-ai-agent");
            assertThat(closed.getApprovedPayoutAmount()).isEqualTo(19_000);
            assertThat(closed.getPaymentReference()).isEqualTo("pay-CLM-AI-TAKEOVER-001");
            assertThat(closed.getRejectionReason()).isNull();
        }
    }

    @Test
    void enableAtIntakeRunsBothAgentsWithoutAnyHumanSignal() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            registerSearchAttributes(env);
            standUpClaimAndAgents(env,
                FieldAdjusterWorkflowImpl.class, ApprovingClaimAdjusterWorkflowImpl.class);
            env.start();

            PropertyClaimWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                PropertyClaimWorkflow.class, options("claim/property/test-ai-intake"));
            WorkflowClient.start(wf::run, aiEnabledInput("CLM-AI-INTAKE-001"));

            PropertyClaimState closed = WorkflowStub.fromTyped(wf).getResult(PropertyClaimState.class);
            assertThat(closed.getStatus()).isEqualTo(ClaimStatus.CLOSED);
            assertThat(closed.getApprovedByAdjusterId()).isEqualTo("adj-ai-agent");
            assertThat(closed.getApprovedPayoutAmount()).isEqualTo(19_000);
            assertThat(closed.getDamageAssessment()).isEqualTo("AI assessment for CLM-AI-INTAKE-001");
        }
    }

    @Test
    void enableWhileParkedAtApprovalAfterHumanAssessmentRunsOnlyClaimAdjuster() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            registerSearchAttributes(env);
            // The field adjuster stub throws if invoked — proving only the claim adjuster runs.
            standUpClaimAndAgents(env,
                UnusedFieldAdjusterWorkflowImpl.class, ApprovingClaimAdjusterWorkflowImpl.class);
            env.start();

            PropertyClaimWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                PropertyClaimWorkflow.class, options("claim/property/test-ai-mixed"));
            WorkflowClient.start(wf::run, portalInput("CLM-AI-MIXED-001"));

            // A human submits the assessment; the claim parks at PENDING_APPROVAL.
            awaitStatus(wf, ClaimStatus.PENDING_DAMAGE_ASSESSMENT);
            wf.submitDamageAssessment(new DamageAssessmentResult("Human assessment: kitchen fire", 12_000));
            awaitStatus(wf, ClaimStatus.PENDING_APPROVAL);

            // Now flip to AI — only the claim adjuster should run, using the human assessment.
            wf.enableAiAdjuster();

            PropertyClaimState closed = WorkflowStub.fromTyped(wf).getResult(PropertyClaimState.class);
            assertThat(closed.getStatus()).isEqualTo(ClaimStatus.CLOSED);
            // Human assessment preserved (field-adjuster agent did not overwrite it).
            assertThat(closed.getDamageAssessment()).isEqualTo("Human assessment: kitchen fire");
            assertThat(closed.getEstimatedRepairCost()).isEqualTo(12_000);
            // Claim-adjuster agent approved 12000 - 1000 = 11000.
            assertThat(closed.getApprovedByAdjusterId()).isEqualTo("adj-ai-agent");
            assertThat(closed.getApprovedPayoutAmount()).isEqualTo(11_000);
        }
    }

    @Test
    void claimAdjusterDenyReportClosesRejected() {
        try (TestWorkflowEnvironment env = TestWorkflowEnvironment.newInstance()) {
            registerSearchAttributes(env);
            standUpClaimAndAgents(env,
                FieldAdjusterWorkflowImpl.class, DenyingClaimAdjusterWorkflowImpl.class);
            env.start();

            PropertyClaimWorkflow wf = env.getWorkflowClient().newWorkflowStub(
                PropertyClaimWorkflow.class, options("claim/property/test-ai-deny"));
            WorkflowClient.start(wf::run, aiEnabledInput("CLM-AI-DENY-001"));

            PropertyClaimState rejected = WorkflowStub.fromTyped(wf).getResult(PropertyClaimState.class);
            assertThat(rejected.getStatus()).isEqualTo(ClaimStatus.REJECTED);
            assertThat(rejected.getRejectionReason()).isEqualTo("AI: loss is not a covered peril.");
            assertThat(rejected.getPaymentReference()).isNull();
            assertThat(rejected.getClosedAt()).isPositive();
        }
    }
}
