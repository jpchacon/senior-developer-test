package com.caseware.templateupdate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the read API that serves pending template updates. */
@SpringBootApplication
public class TemplateUpdateAdvisorApplication {

    /** Not instantiated; this class exists only to carry the entry point. */
    protected TemplateUpdateAdvisorApplication() {
        // Utility entry point.
    }

    /**
     * Starts the application.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
        SpringApplication.run(TemplateUpdateAdvisorApplication.class, args);
    }
}
