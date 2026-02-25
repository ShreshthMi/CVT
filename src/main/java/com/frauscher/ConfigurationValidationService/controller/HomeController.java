package com.frauscher.ConfigurationValidationService.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Home controller to handle root endpoint and favicon requests
 */
@Controller
public class HomeController {

    /**
     * Root endpoint - redirects to API documentation
     */
    @GetMapping("/")
    @ResponseBody
    public String home() {
        return """
            <!DOCTYPE html>
            <html>
            <head>
                <title>Configuration Validation Engine</title>
                <meta http-equiv="refresh" content="0; url=/swagger-ui.html">
            </head>
            <body>
                <h1>Configuration Validation Engine</h1>
                <p>Redirecting to API documentation...</p>
                <p>If not redirected, <a href="/swagger-ui.html">click here</a> for API documentation.</p>
                <p>Or visit <a href="/actuator/health">/actuator/health</a> for health check.</p>
            </body>
            </html>
            """;
    }

    /**
     * Favicon endpoint - returns empty response
     */
    @GetMapping("/favicon.ico")
    @ResponseBody
    public void favicon() {
        // Return empty response for favicon requests
    }
}
