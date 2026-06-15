package com.aidsight.infrastructure.config;

/**
 * Configuration class for web scraping parameters.
 * Provides centralized control over scraping behavior including
 * browser settings, delays, and timeouts.
 */
public final class ScraperConfig {
    /**
     * Whether to run Chrome in headless mode (no visible browser window).
     * Default: true
     */
    private static boolean headlessMode = true;

    /**
     * Delay in milliseconds between scraping operations to avoid rate limiting.
     * Default: 2000ms (2 seconds)
     */
    private static int scrapeDelayMs = 2000;

    /**
     * Maximum number of scroll attempts when loading dynamic content.
     * Default: 50
     */
    private static int maxScrolls = 50;

    /**
     * Timeout in seconds for HTTP requests and page loads.
     * Default: 20 seconds
     */
    private static int requestTimeoutSeconds = 20;

    /**
     * Maximum number of retries for failed operations.
     * Default: 3
     */
    private static int maxRetries = 3;

    /**
     * Private constructor to prevent instantiation.
     */
    private ScraperConfig() {}

    // Getters
    public static boolean isHeadlessMode() {
        return headlessMode;
    }

    public static int getScrapeDelayMs() {
        return scrapeDelayMs;
    }

    public static int getMaxScrolls() {
        return maxScrolls;
    }

    public static int getRequestTimeoutSeconds() {
        return requestTimeoutSeconds;
    }

    public static int getMaxRetries() {
        return maxRetries;
    }

    // Setters for runtime configuration
    public static void setHeadlessMode(boolean headless) {
        headlessMode = headless;
    }

    public static void setScrapeDelayMs(int delayMs) {
        if (delayMs < 0) {
            throw new IllegalArgumentException("Scrape delay must be non-negative");
        }
        scrapeDelayMs = delayMs;
    }

    public static void setMaxScrolls(int scrolls) {
        if (scrolls < 1) {
            throw new IllegalArgumentException("Max scrolls must be at least 1");
        }
        maxScrolls = scrolls;
    }

    public static void setRequestTimeoutSeconds(int timeoutSeconds) {
        if (timeoutSeconds < 1) {
            throw new IllegalArgumentException("Request timeout must be at least 1 second");
        }
        requestTimeoutSeconds = timeoutSeconds;
    }

    public static void setMaxRetries(int retries) {
        if (retries < 0) {
            throw new IllegalArgumentException("Max retries must be non-negative");
        }
        maxRetries = retries;
    }
}

