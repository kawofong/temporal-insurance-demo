// Activity interface for demo environment setup operations.
// Provides methods to create policy workflows idempotently on the Temporal server.
package com.ziggy.insurance.domains.demo;

import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

@ActivityInterface
public interface DemoSetupActivities {

    @ActivityMethod
    String createAutoPolicyIfAbsent(DemoAutoPolicy request);

    @ActivityMethod
    String createPropertyPolicyIfAbsent(DemoPropertyPolicy request);

    @ActivityMethod
    String createCommercialPolicyIfAbsent(DemoCommercialPolicy request);
}
