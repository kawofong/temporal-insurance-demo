// Mock activity implementation for the payment domain. No external system calls — it stands in
// for a downstream payment gateway so the domain can be demoed end-to-end without any real
// integration. The gateway is deliberately flaky so the demo shows Temporal retrying to success.
package com.ziggy.insurance.domains.payment;

import io.temporal.activity.Activity;
import io.temporal.spring.boot.ActivityImpl;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Component;

@Component
@ActivityImpl(taskQueues = PaymentNexus.TASK_QUEUE)
public class PaymentActivitiesImpl implements PaymentActivities {

    // disburse fails on earlier attempts so the demo shows Temporal retrying to success.
    private static final int PAYMENT_SUCCEEDS_ON_ATTEMPT = 6;

    @Override
    public String disburse(String claimId, String policyHolderId, int amount) {
        // Artificial 100-500 ms delay so the demo shows realistic downstream latency.
        simulateProcessingDelay();
        // Simulate a flaky payment gateway: fail early attempts so the default retry policy
        // drives the activity to eventual success.
        int attempt = Activity.getExecutionContext().getInfo().getAttempt();
        if (attempt < PAYMENT_SUCCEEDS_ON_ATTEMPT) {
            throw new RuntimeException("Payment gateway unavailable (attempt " + attempt + ")");
        }
        return "pay-" + claimId;
    }

    // Sleeps a random 100-500 ms to mimic downstream gateway latency. Demo only — this makes
    // activity execution visible in the timeline; a real activity would do actual work instead.
    private static void simulateProcessingDelay() {
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(100, 500));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
