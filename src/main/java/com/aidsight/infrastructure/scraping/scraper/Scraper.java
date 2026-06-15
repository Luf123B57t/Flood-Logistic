package com.aidsight.infrastructure.scraping.scraper;

import com.aidsight.domain.model.core.Instance;
import com.aidsight.infrastructure.scraping.exception.ScraperException;

import java.util.List;

/**
 * Interface for scraping infrastructure from different platforms.
 * Implementations should scrape infrastructure based on keywords and return instances.
 *
 * @param <T> the type of Instance this scraper produces
 */
public interface Scraper<T extends Instance> {
    /**
     * Scrapes infrastructure from the platform based on the provided keywords.
     *
     * @param keywords the search keywords to use for scraping
     * @param maxResults the maximum number of results to scrape
     * @param progressCallback callback to report progress (0.0 to 1.0)
     * @return a list of scraped instances
     * @throws ScraperException if scraping fails
     */
    List<T> scrape(String keywords, int maxResults, ProgressCallback progressCallback) throws ScraperException;

    /**
     * Returns the platform name this scraper targets.
     *
     * @return the platform name (e.g., "Facebook", "Newspaper")
     */
    String getPlatformName();

    /**
     * Returns the instance type this scraper produces.
     *
     * @return the class type of instances produced
     */
    Class<T> getInstanceType();

    /**
     * Callback interface for reporting scraping progress.
     */
    @FunctionalInterface
    interface ProgressCallback {
        /**
         * Reports progress of the scraping operation.
         *
         * @param progress progress value between 0.0 and 1.0
         * @param message descriptive message about current progress
         */
        void onProgress(double progress, String message);
    }

    /**
     * Helper class for formatting progress messages with tree-like branching symbols.
     */
    class ProgressHelper {
        /**
         * Enum for different branch types in the progress tree.
         */
        public enum BranchType {
            /** Root/start of tree: ┌─ */
            ROOT("┌─"),
            /** Branch/node: ├─ */
            BRANCH("├─"),
            /** End/leaf node: └─ */
            END("└─"),
            /** Continuation (no branch symbol, just vertical line): │ */
            CONTINUE("│");

            private final String symbol;

            BranchType(String symbol) {
                this.symbol = symbol;
            }

            public String getSymbol() {
                return symbol;
            }
        }

        /**
         * Formats a progress message with tree-like branching symbols.
         *
         * @param level the indentation level (0 = root, 1 = first level, etc.)
         * @param branchType the type of branch symbol to use
         * @param message the actual message text
         * @return formatted message with branching symbols
         */
        public static String formatMessage(int level, BranchType branchType, String message) {
            if (level < 0) {
                level = 0;
            }

            StringBuilder sb = new StringBuilder();

            // Add vertical lines for indentation (│  for each level before the last)
            if (level > 0) {
                sb.append("│  ".repeat(level));
            }

            // Add the branch symbol
            sb.append(branchType.getSymbol());

            // Add space before message if there is a branch symbol
            if (!branchType.getSymbol().isEmpty()) {
                sb.append(" ");
            }

            // Add the message
            sb.append(message);

            return sb.toString();
        }

        /**
         * Reports progress with formatted tree-like message.
         *
         * @param callback the progress callback to use
         * @param progress progress value between 0.0 and 1.0
         * @param level the indentation level
         * @param branchType the type of branch symbol
         * @param message the message text
         */
        public static void report(ProgressCallback callback, double progress, int level, BranchType branchType, String message) {
            if (callback != null) {
                callback.onProgress(progress, formatMessage(level, branchType, message));
            }
        }
    }
}

