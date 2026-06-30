// Entry point for the worker application.
// Runs headless and registers Temporal workers for the policy workflows.
package com.ziggy.insurance.workers;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {
    "com.ziggy.insurance.workers",
    "com.ziggy.insurance.domains"
})
public class WorkersApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkersApplication.class, args);
    }
}
