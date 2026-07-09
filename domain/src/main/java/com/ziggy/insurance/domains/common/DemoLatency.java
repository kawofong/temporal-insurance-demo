// Shared helper for the artificial processing delay that mock activities use to mimic downstream
// system latency, making activity execution visible in the demo timeline.
package com.ziggy.insurance.domains.common;

import java.util.concurrent.ThreadLocalRandom;

public final class DemoLatency {

    // System property that gates the artificial delay. Defaults to enabled so real demo runs keep
    // the latency; tests set it to false to avoid waiting in real time.
    private static final String ENABLED_PROPERTY = "insurance.simulateDelays";

    private DemoLatency() {
    }

    // Sleeps a random duration in [minMillis, maxMillis) to mimic downstream system latency,
    // unless the demo delay is disabled via the insurance.simulateDelays system property.
    public static void simulate(int minMillis, int maxMillis) {
        if (!Boolean.parseBoolean(System.getProperty(ENABLED_PROPERTY, "true"))) {
            return;
        }
        try {
            Thread.sleep(ThreadLocalRandom.current().nextInt(minMillis, maxMillis));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
