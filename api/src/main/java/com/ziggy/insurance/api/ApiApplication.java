// Entry point for the REST API application.
// Boots a web server with a Temporal client; no workers are started here.
package com.ziggy.insurance.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;

@SpringBootApplication
public class ApiApplication {

    private static final Logger log = LoggerFactory.getLogger(ApiApplication.class);

    private final Environment environment;

    public ApiApplication(Environment environment) {
        this.environment = environment;
    }

    public static void main(String[] args) {
        SpringApplication.run(ApiApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logSwaggerUrl() {
        String port = environment.getProperty("server.port", "8080");
        log.info("Swagger UI available at http://localhost:{}/swagger-ui.html", port);
    }
}
