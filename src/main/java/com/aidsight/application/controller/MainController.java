package com.aidsight.application.controller;

import com.aidsight.domain.service.Analyzer;
import com.aidsight.domain.model.core.Subject;
import com.aidsight.presentation.navigation.NavigationManager;
import com.aidsight.infrastructure.scraping.factory.WebDriverFactory;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Main application controller that coordinates business logic and manages application state.
 * <p>
 * This controller serves as the central coordination point for the application,
 * managing analysis operations, navigation, and scraping thread lifecycle.
 * It is created at application startup and injected into view controllers.
 * </p>
 *
 * @see Analyzer
 * @see NavigationManager
 */
public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);
    private final Analyzer analyzer;
    private final List<Thread> activeScrapingThreads = new ArrayList<>();
    private final NavigationManager navigationManager;

    /**
     * Constructs a new MainController with initialized analyzer and navigation manager.
     * <p>
     * This constructor creates the core dependencies needed for application operation.
     * </p>
     */
    public MainController() {
        this.analyzer = new Analyzer();
        this.navigationManager = new NavigationManager(this);
    }

    /**
     * Performs asynchronous analysis on the given subject.
     * <p>
     * This method wraps the synchronous analyzer.analyze() method in a CompletableFuture
     * to enable non-blocking execution. The analysis processes all tasks against all
     * instances in the subject and returns the aggregated results.
     * </p>
     *
     * @param subject the subject containing tasks and instances to analyze
     * @return a CompletableFuture that will complete with the analysis results
     * @throws NullPointerException if subject is null
     */
    public CompletableFuture<Analyzer.AnalysisResult> analyze(Subject subject) {
        return CompletableFuture.supplyAsync(() -> {
            // Perform the actual analysis
            return analyzer.analyze(subject.getTasks(), subject.getInstances());
        });
    }

    /**
     * Navigates to the selection view.
     * <p>
     * Transitions the application to the subject selection screen where users
     * can choose a subject for analysis.
     * </p>
     *
     * @param stage the current stage to transition
     * @throws NullPointerException if stage is null
     */
    public void navigateToSelectionView(Stage stage) {
        navigationManager.navigateToSelection(stage);
    }

    /**
     * Retrieves the navigation manager for view transitions.
     * <p>
     * The navigation manager handles all view transitions and maintains
     * the navigation state of the application.
     * </p>
     *
     * @return the navigation manager instance
     */
    public NavigationManager getNavigationManager() {
        return navigationManager;
    }

    /**
     * Registers a scraping thread for lifecycle management.
     * <p>
     * This method tracks active scraping threads to ensure they can be properly
     * terminated during application shutdown or when needed.
     * </p>
     *
     * @param thread the scraping thread to register
     * @throws NullPointerException if thread is null
     */
    public void registerScrapingThread(Thread thread) {
        synchronized (activeScrapingThreads) {
            activeScrapingThreads.add(thread);
            logger.debug("Registered scraping thread. Total active: {}", activeScrapingThreads.size());
        }
    }

    /**
     * Unregisters a scraping thread after it completes execution.
     * <p>
     * This method removes a thread from the active thread list once it has
     * finished execution normally.
     * </p>
     *
     * @param thread the scraping thread to unregister
     * @throws NullPointerException if thread is null
     */
    public void unregisterScrapingThread(Thread thread) {
        synchronized (activeScrapingThreads) {
            activeScrapingThreads.remove(thread);
            logger.debug("Unregistered scraping thread. Total active: {}", activeScrapingThreads.size());
        }
    }

    /**
     * Terminates a specific scraping thread immediately.
     * <p>
     * This method forcefully terminates a scraping thread by closing its WebDriver
     * and interrupting the thread. It waits briefly for graceful termination but
     * does not block indefinitely. If the thread cannot be terminated after interruption,
     * it is abandoned and will be cleaned up during application exit.
     * </p>
     *
     * @param thread the thread to terminate, or null to skip termination
     */
    public void terminateScrapingThread(Thread thread) {
        if (thread == null) {
            return;
        }

        synchronized (activeScrapingThreads) {
            if (!thread.isAlive()) {
                // Thread already terminated, just remove it from the list
                activeScrapingThreads.remove(thread);
                return;
            }

            logger.info("Force killing scraping thread immediately: {}", thread.getName());

            // First, forcefully close any WebDriver associated with this thread
            WebDriverFactory.forceCloseDriverForThread(thread);

            // Interrupt the thread
            thread.interrupt();

            // Wait only 100ms for graceful termination
            try {
                thread.join(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // Log if still alive (Thread.stop() is unsupported in Java 21+)
            if (thread.isAlive()) {
                logger.warn("Thread still alive after interrupt+100ms: {}", thread.getName());
                logger.warn("Thread will be abandoned - application will force exit if needed");
            } else {
                logger.info("Thread terminated successfully: {}", thread.getName());
            }

            // Ensure the thread is removed from the list
            activeScrapingThreads.remove(thread);
        }
    }

    /**
     * Terminates all active scraping threads during application shutdown.
     * <p>
     * This method is invoked when the application is shutting down to ensure all
     * scraping threads are properly terminated. It uses aggressive termination
     * by closing WebDrivers, interrupting threads, and waiting briefly for termination.
     * If threads remain alive after interruption, the JVM is forcefully terminated
     * using System.exit() to prevent resource leaks.
     * </p>
     * <p>
     * Note: Thread.stop() is unsupported in Java 21+, so System.exit() is used
     * as a fallback for stubborn threads.
     * </p>
     */
    public void terminateAllScrapingThreads() {
        synchronized (activeScrapingThreads) {
            if (activeScrapingThreads.isEmpty()) {
                logger.info("No active scraping threads to terminate");
                return;
            }

            logger.info("Force killing {} active scraping thread(s)", activeScrapingThreads.size());

            // Create a copy to avoid concurrent modification
            List<Thread> threadsToTerminate = new ArrayList<>(activeScrapingThreads);

            // First, forcefully close all WebDrivers to unblock Selenium operations
            for (Thread thread : threadsToTerminate) {
                if (thread.isAlive()) {
                    logger.info("Force closing WebDriver for thread: {}", thread.getName());
                    WebDriverFactory.forceCloseDriverForThread(thread);
                }
            }

            // Interrupt all threads
            for (Thread thread : threadsToTerminate) {
                if (thread.isAlive()) {
                    logger.info("Interrupting scraping thread: {}", thread.getName());
                    thread.interrupt();
                }
            }

            // Wait only 100ms per thread for graceful termination
            for (Thread thread : threadsToTerminate) {
                if (thread.isAlive()) {
                    try {
                        thread.join(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
            }

            // Count remaining alive threads
            int aliveCount = 0;
            for (Thread thread : threadsToTerminate) {
                if (thread.isAlive()) {
                    logger.warn("Thread still alive after interrupt: {}", thread.getName());
                    aliveCount++;
                }
            }

            activeScrapingThreads.clear();
            logger.info("All scraping threads have been processed");

            // If threads are still alive, force exit the JVM
            // Note: Thread.stop() is unsupported in Java 21+, so we use System.exit
            if (aliveCount > 0) {
                logger.warn("Forcing application exit due to {} stubborn thread(s)", aliveCount);
                try {
                    Thread.sleep(200); // Brief moment for logging to flush
                } catch (InterruptedException e) {
                    // Ignore
                }
                System.exit(0);
            }
        }
    }
}
