package com.aidsight.infrastructure.scraping.manager;

import com.aidsight.domain.model.core.Instance;
import com.aidsight.domain.enums.InstanceType;
import com.aidsight.domain.model.core.Subject;
import com.aidsight.domain.model.core.Task;
import com.aidsight.infrastructure.config.DatabaseConfig;
import com.aidsight.infrastructure.config.ScraperConfig;
import com.aidsight.infrastructure.persistence.connection.SQLiteConnectionManager;
import com.aidsight.infrastructure.persistence.dao.InstanceDAO;
import com.aidsight.infrastructure.persistence.dao.InstanceDAORegistry;
import com.aidsight.infrastructure.persistence.dao.SubjectDAO;
import com.aidsight.infrastructure.scraping.scraper.Scraper;
import com.aidsight.infrastructure.scraping.exception.ScraperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.SQLException;
import java.util.*;

/**
 * Manages scraping operations and infrastructure persistence.
 * Separates business logic from GUI concerns.
 */
public class ScraperManager {
    private static final Logger logger = LoggerFactory.getLogger(ScraperManager.class);

    /**
     * Callback interface for scraping progress updates.
     */
    public interface ScraperProgressCallback {
        void onProgress(double progress, String message);
        void onComplete(ScrapeResult result);
        void onError(String errorMessage);
    }

    /**
         * Configuration for a scraping operation.
         */
    public record ScrapeConfig(String keywords, int numPosts, Map<InstanceType, Integer> postCountsPerScraper,
                               Set<InstanceType> enabledScrapers, boolean headlessMode) {
        public ScrapeConfig(String keywords, int numPosts, Map<InstanceType, Integer> postCountsPerScraper,
                           Set<InstanceType> enabledScrapers, boolean headlessMode) {
            this.keywords = keywords;
            this.numPosts = numPosts;
            this.postCountsPerScraper = postCountsPerScraper != null ? new HashMap<>(postCountsPerScraper) : new HashMap<>();
            this.enabledScrapers = new HashSet<>(enabledScrapers);
            this.headlessMode = headlessMode;
        }

        @Override
        public Set<InstanceType> enabledScrapers() {
            return new HashSet<>(enabledScrapers);
    }

        /**
         * Gets the post count for a specific scraper.
         * Falls back to the default numPosts if not specified.
         */
        public int getPostCount(InstanceType instanceType) {
            return postCountsPerScraper.getOrDefault(instanceType, numPosts);
        }

    }

    /**
     * Result of a scraping operation.
     */
    public static class ScrapeResult {
        private final Map<Class<? extends Instance>, List<? extends Instance>> scrapedData;
        private final int totalCount;

        public ScrapeResult(Map<Class<? extends Instance>, List<? extends Instance>> scrapedData) {
            this.scrapedData = scrapedData;
            this.totalCount = scrapedData.values().stream().mapToInt(List::size).sum();
        }

        public Map<Class<? extends Instance>, List<? extends Instance>> getScrapedData() {
            return scrapedData;
        }

        public int getTotalCount() {
            return totalCount;
        }

        public boolean isEmpty() {
            return scrapedData.isEmpty() || totalCount == 0;
        }
    }

    /**
     * Starts scraping in a background thread.
     *
     * @param config The scraping configuration
     * @param callback Progress callback for updates
     * @param threadRegistrationCallback Optional callback to register the thread (e.g., with MainController)
     * @return The scraping thread that was started
     */
    public Thread startScrapingAsync(ScrapeConfig config, ScraperProgressCallback callback,
                                      ThreadRegistrationCallback threadRegistrationCallback) {
        Thread scrapingThread = new Thread(() -> {
            try {
                // Return value not used in async context - result is communicated via callback
                performScraping(config, callback);
            } catch (InterruptedException e) {
                logger.info("Scraping thread was interrupted");
                callback.onError("Scraping was cancelled");
            } catch (Exception e) {
                logger.error("Error during scraping", e);
                callback.onError(e.getMessage());
            } finally {
                // Unregister the thread when it completes
                if (threadRegistrationCallback != null) {
                    threadRegistrationCallback.onThreadComplete(Thread.currentThread());
                }
            }
        });

        scrapingThread.setDaemon(true);
        scrapingThread.setName("Scraping Thread");

        // Register the thread before starting
        if (threadRegistrationCallback != null) {
            threadRegistrationCallback.onThreadCreated(scrapingThread);
        }

        scrapingThread.start();
        return scrapingThread;
    }

    /**
     * Callback interface for thread lifecycle management.
     */
    public interface ThreadRegistrationCallback {
        void onThreadCreated(Thread thread);
        void onThreadComplete(Thread thread);
    }

    /**
     * Performs scraping from selected platforms.
     *
     * @param config   The scraping configuration
     * @param callback Progress callback for updates
     * @throws InterruptedException if the operation is cancelled
     */
    public void performScraping(ScrapeConfig config, ScraperProgressCallback callback)
            throws InterruptedException {

        Map<Class<? extends Instance>, List<? extends Instance>> scrapedData = new HashMap<>();
        List<Scraper<? extends Instance>> scrapers = new ArrayList<>();

        // Configure headless mode
        ScraperConfig.setHeadlessMode(config.headlessMode());
        callback.onProgress(0, "Browser mode: " + (config.headlessMode() ? "Headless (background)" : "Visible (windowed)"));

        // Create scrapers based on configuration
        for (InstanceType instanceType : config.enabledScrapers()) {
            scrapers.add(instanceType.createScraper());
        }

        int totalScrapers = scrapers.size();
        int completedScrapers = 0;

        for (Scraper<? extends Instance> scraper : scrapers) {
            // Check if cancellation was requested
            if (Thread.currentThread().isInterrupted()) {
                logger.info("Scraping cancelled before starting {}", scraper.getPlatformName());
                throw new InterruptedException("Scraping cancelled by user");
            }

            try {
                // Get the post count for this specific scraper
                InstanceType instanceType = InstanceType.fromClass(scraper.getInstanceType());
                int postCount = instanceType != null ? config.getPostCount(instanceType) : config.numPosts();

                callback.onProgress(
                    (double) completedScrapers / totalScrapers,
                    "Starting " + scraper.getPlatformName() + " scraper (" + postCount + " posts)..."
                );

                final int currentScraperIndex = completedScrapers;
                List<? extends Instance> instances = scraper.scrape(
                    config.keywords(),
                    postCount,
                    (progress, message) -> {
                        // Calculate overall progress
                        double overallProgress = (currentScraperIndex + progress) / (double) totalScrapers;
                        callback.onProgress(overallProgress, message);
                    }
                );

                // Check if cancellation was requested after scraping
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("Scraping cancelled after completing {}", scraper.getPlatformName());
                    throw new InterruptedException("Scraping cancelled by user");
                }

                // Store scraped infrastructure
                scrapedData.put(scraper.getInstanceType(), instances);
                completedScrapers++;

                callback.onProgress(
                    (double) completedScrapers / totalScrapers,
                    "Completed " + scraper.getPlatformName() + " scraping: " + instances.size() + " items found"
                );

            } catch (ScraperException e) {
                // Check if cancellation was the cause
                if (Thread.currentThread().isInterrupted() || e.getCause() instanceof InterruptedException) {
                    logger.info("Scraping cancelled during {} scraper", scraper.getPlatformName());
                    throw new InterruptedException("Scraping cancelled by user");
                }
                logger.error("Error scraping from {}", scraper.getPlatformName(), e);
                callback.onError("Failed to scrape from " + scraper.getPlatformName() + ": " + e.getMessage());
            }
        }

        // Scraping complete
        ScrapeResult result = new ScrapeResult(scrapedData);
        callback.onProgress(1.0, "Scraping completed!");
        callback.onComplete(result);
    }

    /**
     * Saves a subject with scraped infrastructure to the database.
     *
     * @param name Subject name
     * @param description Subject description
     * @param tasks The list of tasks to perform on this subject
     * @param scrapedData The scraped instance infrastructure
     * @return The saved Subject with ID
     * @throws SQLException if database operation fails
     */
    public Subject saveSubject(String name, String description, List<Task> tasks,
                              Map<Class<? extends Instance>, List<? extends Instance>> scrapedData)
            throws SQLException {

        // Create subject with the provided tasks
        Subject subject = new Subject(name, description, tasks, new ArrayList<>());

        // Save to database
        SQLiteConnectionManager connectionManager = SQLiteConnectionManager.getInstance(DatabaseConfig.getDatabasePath());
        SubjectDAO subjectDAO = new SubjectDAO(connectionManager);
        Subject savedSubject = subjectDAO.save(subject);

        logger.info("Subject saved with ID: {}", savedSubject.getId());

        // Save instances
        InstanceDAORegistry registry = InstanceDAORegistry.getInstance();
        int totalSaved = 0;

        for (Map.Entry<Class<? extends Instance>, List<? extends Instance>> entry : scrapedData.entrySet()) {
            Class<? extends Instance> instanceType = entry.getKey();
            List<? extends Instance> instances = entry.getValue();

            @SuppressWarnings("unchecked")
            Optional<InstanceDAO<Instance, Integer>> daoOptional =
                registry.getDAO((Class<Instance>) instanceType);

            if (daoOptional.isPresent()) {
                InstanceDAO<Instance, Integer> dao = daoOptional.get();
                @SuppressWarnings("unchecked")
                List<Instance> typedInstances = (List<Instance>) instances;
                dao.saveAllWithSubject(typedInstances, savedSubject.getId());
                totalSaved += instances.size();
                logger.info("Saved {} {} instances", instances.size(), instanceType.getSimpleName());

                // Register instance type with subject
                savedSubject.addInstanceType(instanceType);
            }
        }

        logger.info("Successfully saved subject with {} total instances", totalSaved);
        return savedSubject;
    }

}

