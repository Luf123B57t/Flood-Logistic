package com.aidsight.infrastructure.scraping.factory;

import com.aidsight.infrastructure.config.ScraperConfig;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Factory class for creating configured WebDriver instances.
 * Handles ChromeDriver setup with anti-detection measures and
 * configuration based on ScraperConfig settings.
 */
public final class WebDriverFactory {
    private static final Logger logger = LoggerFactory.getLogger(WebDriverFactory.class);
    private static final int DRIVER_QUIT_TIMEOUT_SECONDS = 5;

    // Track WebDriver instances by thread for forceful termination
    private static final Map<Thread, WebDriver> activeDrivers = new ConcurrentHashMap<>();

    /**
     * Private constructor to prevent instantiation.
     */
    private WebDriverFactory() {}

    /**
     * Creates a new ChromeDriver instance with anti-detection configuration.
     * The driver is configured with:
     * - Anti-automation detection measures
     * - Custom user agent
     * - Vietnamese language preference
     * - Headless mode based on ScraperConfig
     * - Disabled notification, geolocation, and permission prompts
     * - Disabled infobars and popups
     *
     * @return configured WebDriver instance
     */
    public static WebDriver createChromeDriver() {
        logger.debug("Setting up ChromeDriver with WebDriverManager");

        ChromeOptions options = new ChromeOptions();

        // Set the Chrome binary path
        options.setBinary("/usr/sbin/chromium");

         // Setup ChromeDriver with explicit version matching the installed browser (142)
        WebDriverManager.chromedriver()
                .driverVersion("142.0.7444.134")
                .clearDriverCache()
                .clearResolutionCache()
                .setup();


        // Disable automation flags to avoid detection
        options.setExperimentalOption("excludeSwitches", new String[]{"enable-automation"});
        options.setExperimentalOption("useAutomationExtension", false);

        // Set realistic user agent
        options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

        // Anti-detection measures
        options.addArguments("--disable-blink-features=AutomationControlled");
        options.addArguments("--disable-gpu");
        options.addArguments("--no-sandbox");
        options.addArguments("--disable-dev-shm-usage");

        // Disable infobars and popups
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");

        // Disable extensions that might show popups
        options.addArguments("--disable-extensions");

        // Language and window settings
        options.addArguments("--lang=vi");
        options.addArguments("--window-size=1920,1080");

        // Headless mode configuration
        if (ScraperConfig.isHeadlessMode()) {
            logger.debug("Running Chrome in headless mode");
            options.addArguments("--headless=new");
        } else {
            logger.debug("Running Chrome in visible mode");
        }

        // Disable password manager and credentials service
        Map<String, Object> prefs = getPrefs();

        options.setExperimentalOption("prefs", prefs);

        logger.info("Creating ChromeDriver instance (headless={})", ScraperConfig.isHeadlessMode());

        WebDriver driver = new ChromeDriver(options);

        // Register the driver with the current thread for potential forceful termination
        activeDrivers.put(Thread.currentThread(), driver);
        logger.debug("Registered WebDriver for thread: {}", Thread.currentThread().getName());

        return driver;
    }

    private static Map<String, Object> getPrefs() {
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);

        // Disable notification prompts (prevents "Allow notifications?" popup)
        prefs.put("profile.default_content_setting_values.notifications", 2); // 1=allow, 2=block

        // Disable geolocation prompts
        prefs.put("profile.default_content_setting_values.geolocation", 2);

        // Disable media stream (camera/microphone) prompts
        prefs.put("profile.default_content_setting_values.media_stream", 2);

        // Disable other permission prompts
        prefs.put("profile.default_content_setting_values.media_stream_mic", 2);
        prefs.put("profile.default_content_setting_values.media_stream_camera", 2);
        prefs.put("profile.default_content_setting_values.popups", 2);
        prefs.put("profile.default_content_setting_values.automatic_downloads", 2);
        return prefs;
    }

    /**
     * Safely closes a WebDriver instance with timeout protection.
     * <p>
     * This method handles the case where the thread is interrupted by:
     * 1. Clearing the interrupt flag temporarily to allow clean shutdown
     * 2. Using a timeout to prevent indefinite hanging
     * 3. Restoring the interrupt flag after cleanup
     *
     * @param driver the WebDriver to close, can be null
     */
    public static void closeDriver(WebDriver driver) {
        if (driver != null) {
            // Unregister the driver
            Thread currentThread = Thread.currentThread();
            activeDrivers.remove(currentThread);
            logger.debug("Unregistered WebDriver for thread: {}", currentThread.getName());

            // Check if current thread is interrupted and clear the flag
            boolean wasInterrupted = Thread.interrupted();

            if (wasInterrupted) {
                logger.info("Thread interrupted - closing WebDriver with timeout protection");
            }

            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<?> future = executor.submit(() -> {
                try {
                    logger.debug("Closing WebDriver");
                    driver.quit();
                    logger.debug("WebDriver closed successfully");
                } catch (Exception e) {
                    logger.warn("Error during driver.quit(): {}", e.getMessage());
                }
            });

            try {
                // Wait for driver to quit with timeout
                future.get(DRIVER_QUIT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                logger.warn("WebDriver quit timed out after {} seconds, forcing shutdown", DRIVER_QUIT_TIMEOUT_SECONDS);
                future.cancel(true);
            } catch (InterruptedException e) {
                logger.warn("Interrupted while waiting for WebDriver to close");
                future.cancel(true);
            } catch (ExecutionException e) {
                logger.warn("Error while closing WebDriver: {}", e.getMessage());
            } finally {
                executor.shutdownNow();
                try {
                    if (!executor.awaitTermination(1, TimeUnit.SECONDS)) {
                        logger.warn("Executor did not terminate in time");
                    }
                } catch (InterruptedException e) {
                    logger.warn("Interrupted while shutting down executor");
                }

                // Restore interrupt flag if it was set
                if (wasInterrupted) {
                    Thread.currentThread().interrupt();
                    logger.debug("Restored interrupt flag after WebDriver cleanup");
                }
            }
        }
    }

    /**
     * Forcefully closes the WebDriver associated with a specific thread.
     * This is used when terminating a scraping thread to ensure the WebDriver
     * is closed even if the thread is stuck in a blocking Selenium operation.
     *
     * @param thread the thread whose WebDriver should be closed
     */
    public static void forceCloseDriverForThread(Thread thread) {
        WebDriver driver = activeDrivers.get(thread);
        if (driver != null) {
            logger.info("Forcefully closing WebDriver for thread: {}", thread.getName());

            // Remove from tracking first
            activeDrivers.remove(thread);

            // Attempt to close in a separate thread with very short timeout
            ExecutorService executor = Executors.newSingleThreadExecutor();
            Future<?> future = executor.submit(() -> {
                try {
                    driver.quit();
                    logger.info("Successfully closed WebDriver for thread: {}", thread.getName());
                } catch (Exception e) {
                    logger.warn("Error forcefully closing WebDriver: {}", e.getMessage());
                }
            });

            try {
                future.get(500, TimeUnit.MILLISECONDS); // Reduced from 3s to 500ms
            } catch (TimeoutException e) {
                logger.warn("Force close timed out for thread: {}", thread.getName());
                future.cancel(true);
            } catch (Exception e) {
                logger.warn("Error during force close: {}", e.getMessage());
            } finally {
                executor.shutdownNow();
            }
        } else {
            logger.debug("No WebDriver found for thread: {}", thread.getName());
        }
    }
}

