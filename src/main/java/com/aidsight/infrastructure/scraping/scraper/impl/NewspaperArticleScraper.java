package com.aidsight.infrastructure.scraping.scraper.impl;

import com.aidsight.infrastructure.config.ScraperConfig;
import com.aidsight.domain.model.instance.NewspaperArticleInstance;
import com.aidsight.infrastructure.scraping.scraper.Scraper;
import com.aidsight.infrastructure.scraping.scraper.Scraper.ProgressHelper.BranchType;
import com.aidsight.infrastructure.scraping.exception.ScraperException;
import com.aidsight.infrastructure.scraping.factory.WebDriverFactory;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Scraper implementation for newspaper articles.
 * Scrapes disaster reports from Vietnamese government websites
 * based on storm keywords and date ranges.
 */
public class NewspaperArticleScraper implements Scraper<NewspaperArticleInstance> {
    private static final Logger logger = LoggerFactory.getLogger(NewspaperArticleScraper.class);

    private static final String BASE_URL = "http://phongchongthientai.mard.gov.vn/Pages/bao-cao-nhanh.aspx";
    private static final String URL_PREFIX = "http://phongchongthientai.mard.gov.vn/";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String DEFAULT_AUTHOR = "Cục Quản lý đê điều và PCTT";

    @Override
    public List<NewspaperArticleInstance> scrape(String keywords, int maxResults, Scraper.ProgressCallback progressCallback) throws ScraperException {
        logger.info("Starting Newspaper scraping for keywords: '{}', max results: {}", keywords, maxResults);

        List<NewspaperArticleInstance> articles = new ArrayList<>();
        WebDriver driver = null;

        try {
            // Create WebDriver
            ProgressHelper.report(progressCallback, 0.0, 0, BranchType.ROOT, "Initializing browser for newspaper scraping...");
            driver = WebDriverFactory.createChromeDriver();

            // Step 1: Fetch storm date information
            logger.debug("Fetching storm information for: {}", keywords);
            ProgressHelper.report(progressCallback, 0.05, 0, BranchType.BRANCH, "Fetching storm date information...");
            String stormDate = fetchStormDate(driver, keywords);

            if (stormDate == null) {
                logger.warn("Could not determine storm date for keywords: {}", keywords);
                return articles;
            }

            logger.info("Storm date: {}", stormDate);
            ProgressHelper.report(progressCallback, 0.1, 1, BranchType.END, "Storm date found: " + stormDate);

            // Step 2: Calculate date range
            ProgressHelper.report(progressCallback, 0.12, 0, BranchType.BRANCH, "Calculating date range...");
            String[] dateRange = StormInfoFetcher.getDateRange(stormDate);
            if (dateRange == null || dateRange.length != 2) {
                logger.error("Failed to calculate date range");
                return articles;
            }

            LocalDate startDate = LocalDate.parse(dateRange[0], DATE_FORMATTER);
            LocalDate endDate = LocalDate.parse(dateRange[1], DATE_FORMATTER);
            logger.info("Searching articles from {} to {}", dateRange[0], dateRange[1]);
            ProgressHelper.report(progressCallback, 0.15, 1, BranchType.END, "Date range: " + dateRange[0] + " to " + dateRange[1]);

            // Step 3: Find article URLs
            ProgressHelper.report(progressCallback, 0.2, 0, BranchType.BRANCH, "Searching for article URLs...");
            List<String> articleUrls = findArticleUrls(startDate, endDate, maxResults);
            int articlesToScrape = Math.min(articleUrls.size(), maxResults);
            logger.info("Found {} article URLs, will scrape up to {}", articleUrls.size(), articlesToScrape);

            if (articleUrls.isEmpty()) {
                logger.warn("No article URLs found in date range");
                return articles;
            }

            ProgressHelper.report(progressCallback, 0.3, 1, BranchType.END, "Found " + articleUrls.size() + " articles. Starting scraping (limit: " + maxResults + ")...");

            // Step 4: Scrape each article
            int scrapedCount = 0;
            for (int i = 0; i < articleUrls.size() && scrapedCount < maxResults; i++) {
                // Check if thread was interrupted
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("Newspaper scraping interrupted by user");
                    throw new InterruptedException("Scraping cancelled");
                }

                String articleUrl = articleUrls.get(i);

                try {
                    logger.debug("Scraping article {}/{}: {}", scrapedCount + 1, maxResults, articleUrl);

                    // Base progress: 30% for setup, 70% for scraping articles
                    double baseProgress = 0.3 + ((double) scrapedCount / maxResults) * 0.7;

                    if (progressCallback != null) {
                        progressCallback.onProgress(baseProgress, "├─ Scraping article " + (scrapedCount + 1) + " of " + maxResults + "...");
                    }

                    NewspaperArticleInstance article = scrapeArticle(articleUrl, scrapedCount + 1, maxResults, progressCallback, baseProgress);

                    if (article != null && article.getContent() != null && !article.getContent().isEmpty()) {
                        articles.add(article);
                        scrapedCount++;

                        // Report progress
                        double progress = 0.3 + ((double) scrapedCount / maxResults) * 0.7;
                        String message = String.format("Scraped newspaper article %d of %d", scrapedCount, maxResults);
                        ProgressHelper.report(progressCallback, progress, 1, BranchType.END, message);

                        logger.info("Successfully scraped article {}/{}", scrapedCount, maxResults);
                    }

                    // Delay between articles (Thread.sleep will throw InterruptedException if interrupted)
                    if (i < articleUrls.size() - 1) {
                        //noinspection BusyWait - Intentional rate limiting delay between article requests
                        Thread.sleep(ScraperConfig.getScrapeDelayMs());
                    }

                } catch (InterruptedException e) {
                    logger.info("Newspaper scraping interrupted during delay");
                    throw e; // Re-throw to exit the scraping
                } catch (Exception e) {
                    logger.error("Failed to scrape article {}: {}", articleUrl, e.getMessage());
                    ProgressHelper.report(progressCallback, 0.3 + ((double) scrapedCount / maxResults) * 0.7,
                        1, BranchType.END, "Failed to scrape article " + (i + 1) + " - continuing...");
                    // Continue with next article
                }
            }

            logger.info("Successfully scraped {} newspaper articles", articles.size());
            return articles;

        } catch (InterruptedException e) {
            logger.info("Newspaper scraping was interrupted/cancelled");
            // Re-throw as ScraperException to signal cancellation
            throw new ScraperException("Newspaper scraping cancelled by user", e);
        } catch (Exception e) {
            logger.error("Newspaper scraping failed: {}", e.getMessage(), e);
            throw new ScraperException("Failed to scrape newspaper articles: " + e.getMessage(), e);
        } finally {
            WebDriverFactory.closeDriver(driver);
        }
    }

    @Override
    public String getPlatformName() {
        return "Newspaper";
    }

    @Override
    public Class<NewspaperArticleInstance> getInstanceType() {
        return NewspaperArticleInstance.class;
    }

    /**
     * Fetches the storm date with retries.
     */
    private String fetchStormDate(WebDriver driver, String stormName) throws InterruptedException {
        int maxRetries = ScraperConfig.getMaxRetries();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            // Check if thread was interrupted
            if (Thread.currentThread().isInterrupted()) {
                logger.info("Storm date fetching interrupted");
                throw new InterruptedException("Scraping cancelled");
            }

            try {
                logger.debug("Fetching storm date, attempt {}/{}", attempt, maxRetries);
                String date = StormInfoFetcher.fetchStormStartTime(driver, stormName);

                if (date != null && !date.equals("Không tìm thấy thông tin")) {
                    return date;
                }

                if (attempt < maxRetries) {
                    Thread.sleep(3000);
                }
            } catch (InterruptedException e) {
                logger.info("Storm date fetching interrupted during sleep");
                throw e; // Re-throw to propagate cancellation
            } catch (Exception e) {
                logger.warn("Attempt {} failed: {}", attempt, e.getMessage());
            }
        }

        return null;
    }

    /**
     * Finds article URLs within the given date range.
     */
    private List<String> findArticleUrls(LocalDate startDate, LocalDate endDate, int maxArticles) {
        List<String> urls = new ArrayList<>();

        // Calculate starting page based on end date
        long daysDiff = java.time.temporal.ChronoUnit.DAYS.between(endDate, LocalDate.now());
        int startPage = Math.max(1, (int) Math.ceil(daysDiff / 10.0) - 3);

        logger.debug("Starting search from page {}", startPage);

        // Find the page containing the end date
        int currentPage = findPageContainingDate(endDate, startPage);
        if (currentPage == -1) {
            logger.warn("Could not find page containing date: {}", endDate);
            return urls;
        }

        logger.info("Found starting page: {}", currentPage);

        // Collect links from pages
        boolean reachedEnd = false;
        while (!reachedEnd && urls.size() < maxArticles) {
            try {
                List<String> pageLinks = collectLinksFromPage(currentPage, startDate, endDate);

                if (pageLinks.isEmpty()) {
                    reachedEnd = true;
                } else {
                    urls.addAll(pageLinks);

                    // Check if we've gone past the start date
                    String lastLink = pageLinks.getLast();
                    LocalDate lastDate = extractDateFromUrl(lastLink);

                    if (lastDate != null && lastDate.isBefore(startDate)) {
                        reachedEnd = true;
                    }
                }

                if (!reachedEnd) {
                    currentPage++;
                    //noinspection BusyWait - Intentional rate limiting delay between page requests
                    Thread.sleep(800);
                }

            } catch (Exception e) {
                logger.error("Error collecting links from page {}: {}", currentPage, e.getMessage());
                reachedEnd = true;
            }
        }

        return urls;
    }

    /**
     * Finds the page number containing the given date.
     */
    private int findPageContainingDate(LocalDate targetDate, int startPage) {
        int currentPage = startPage;
        int maxAttempts = 5;

        for (int i = 0; i < maxAttempts; i++) {
            try {
                DateRange range = getPageDateRange(currentPage);

                if (range != null) {
                    if (!targetDate.isAfter(range.newest) && !targetDate.isBefore(range.oldest)) {
                        logger.debug("Found target date on page {}", currentPage);
                        return currentPage;
                    }

                    // Adjust page number
                    if (targetDate.isBefore(range.oldest)) {
                        currentPage++;
                    } else {
                        currentPage = Math.max(1, currentPage - 1);
                    }
                }

                Thread.sleep(1000);

            } catch (Exception e) {
                logger.error("Error finding page: {}", e.getMessage());
                return -1;
            }
        }

        return -1;
    }

    /**
     * Gets the date range (newest to oldest) for a given page.
     */
    private DateRange getPageDateRange(int pageNumber) throws Exception {
        String url = BASE_URL;
        if (pageNumber > 1) {
            url += "?p=" + pageNumber;
        }

        logger.debug("Fetching page date range from: {}", url);

        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(20000)
                .get();

        Elements items = findReportItems(doc);
        if (items.isEmpty()) {
            logger.warn("No report items found on page {}", pageNumber);
            return null;
        }

        List<LocalDate> dates = new ArrayList<>();
        for (Element item : items) {
            String dateStr = extractDateFromElement(item);
            if (dateStr != null) {
                LocalDate date = LocalDate.parse(dateStr, DATE_FORMATTER);
                dates.add(date);
            }
        }

        if (dates.isEmpty()) {
            return null;
        }

        Collections.sort(dates);
        return new DateRange(dates.getLast(), dates.getFirst());
    }

    /**
     * Collects article links from a specific page within the date range.
     */
    private List<String> collectLinksFromPage(int pageNumber, LocalDate startDate, LocalDate endDate) throws Exception {
        List<String> links = new ArrayList<>();
        String url = BASE_URL;

        if (pageNumber > 1) {
            url += "?p=" + pageNumber;
        }

        Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(20000)
                .get();

        Elements items = findReportItems(doc);
        if (items.isEmpty()) {
            return links;
        }

        for (Element item : items) {
            try {
                String dateStr = extractDateFromElement(item);
                if (dateStr == null) {
                    continue;
                }

                LocalDate reportDate = LocalDate.parse(dateStr, DATE_FORMATTER);

                // Skip if after end date
                if (reportDate.isAfter(endDate)) {
                    continue;
                }

                // Stop if before start date
                if (reportDate.isBefore(startDate)) {
                    break;
                }

                // Extract and add link
                String link = extractLinkFromElement(item);
                if (link != null) {
                    links.add(link);
                }

            } catch (Exception e) {
                logger.debug("Error processing item: {}", e.getMessage());
            }
        }

        return links;
    }

    /**
     * Scrapes a single newspaper article.
     */
    private NewspaperArticleInstance scrapeArticle(String articleUrl, int articleIndex, int totalArticles,
                                                   ProgressCallback progressCallback, double baseProgress) {
        try {
            logger.debug("Fetching article content from: {}", articleUrl);

            ProgressHelper.report(progressCallback, baseProgress, 1, BranchType.BRANCH, "Article " + articleIndex + "/" + totalArticles + ": Fetching article page...");

            Document doc = Jsoup.connect(articleUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .timeout(ScraperConfig.getRequestTimeoutSeconds() * 1000)
                    .get();

            ProgressHelper.report(progressCallback, baseProgress, 2, BranchType.END, "Article " + articleIndex + "/" + totalArticles + ": Page fetched");

            // Extract specific section (e.g., damage report section)
            ProgressHelper.report(progressCallback, baseProgress, 1, BranchType.BRANCH, "Article " + articleIndex + "/" + totalArticles + ": Extracting damage report section...");
            String content = extractSpecificSection(doc, "TÌNH HÌNH THIỆT HẠI");

            if (content == null || content.isEmpty()) {
                logger.debug("No content found in article: {}", articleUrl);
                ProgressHelper.report(progressCallback, baseProgress, 2, BranchType.END, "Article " + articleIndex + "/" + totalArticles + ": No damage report section found");
                return null;
            }

            ProgressHelper.report(progressCallback, baseProgress, 2, BranchType.END, "Article " + articleIndex + "/" + totalArticles + ": Content extracted (" + content.length() + " chars)");

            // Extract date from URL
            ProgressHelper.report(progressCallback, baseProgress, 1, BranchType.BRANCH, "Article " + articleIndex + "/" + totalArticles + ": Extracting date...");
            LocalDate postedDate = extractDateFromUrl(articleUrl);
            if (postedDate == null) {
                postedDate = LocalDate.now();
            }
            ProgressHelper.report(progressCallback, baseProgress, 2, BranchType.END, "Article " + articleIndex + "/" + totalArticles + ": Date extracted: " + postedDate);

            // Create instance
            NewspaperArticleInstance article = new NewspaperArticleInstance();
            article.setUrl(articleUrl);
            article.setAuthor(DEFAULT_AUTHOR);
            article.setContent(content);
            article.setPostedDate(postedDate);

            return article;

        } catch (Exception e) {
            logger.error("Failed to scrape article {}: {}", articleUrl, e.getMessage());
            ProgressHelper.report(progressCallback, baseProgress, 2, BranchType.END, "Article " + articleIndex + "/" + totalArticles + ": Error: " + e.getMessage());
            return null;
        }
    }

    /**
     * Extracts a specific section from the article.
     */
    private String extractSpecificSection(Document doc, String sectionTitle) {
        Element contentArea = doc.selectFirst(".htmlcontent, .article-content, .post-content");
        if (contentArea == null) {
            contentArea = doc.body();
        }

        Elements paragraphs = contentArea.select("p");
        StringBuilder sectionContent = new StringBuilder();
        boolean inTargetSection = false;
        boolean foundSection = false;

        for (Element p : paragraphs) {
            String pText = p.text().trim();
            Element strong = p.selectFirst("strong");

            if (strong != null) {
                String strongText = strong.text().trim();

                // Found target section
                if (strongText.contains(sectionTitle)) {
                    inTargetSection = true;
                    foundSection = true;
                    sectionContent.append(pText).append("\n\n");
                    continue;
                }

                // Reached next section, stop
                if (inTargetSection && strongText.matches("^[IVX]+\\..*")) {
                    break;
                }
            }

            // Add content if in target section
            if (inTargetSection) {
                sectionContent.append(pText).append("\n");

                // Check for tables
                if (p.nextElementSibling() != null && Objects.requireNonNull(p.nextElementSibling()).tagName().equals("table")) {
                    Element table = p.nextElementSibling();
                    sectionContent.append("\n[BẢNG DỮ LIỆU]\n");
                    assert table != null;
                    sectionContent.append(extractTableData(table));
                    sectionContent.append("\n");
                }
            }
        }

        // If section not found using structured approach, try text-based extraction
        if (!foundSection) {
            logger.debug("Section not found using structured approach, trying text-based extraction");
            return extractSectionByText(contentArea, sectionTitle);
        }

        return sectionContent.toString().trim();
    }

    /**
     * Fallback method: Extracts section by finding text directly in the full content.
     */
    private String extractSectionByText(Element contentArea, String sectionTitle) {
        String fullText = contentArea.text();

        // Find start position
        int startIndex = fullText.indexOf(sectionTitle);
        if (startIndex == -1) {
            // Try normalized version (no punctuation)
            String normalized = sectionTitle.replaceAll("[^\\w\\s]", "");
            startIndex = fullText.indexOf(normalized);
        }

        if (startIndex == -1) {
            return "";
        }

        // Find end position (next section)
        int endIndex = fullText.length();
        String[] nextSections = {"V.", "VI.", "VII.", "VIII."};

        for (String nextSection : nextSections) {
            int nextIndex = fullText.indexOf(nextSection, startIndex + sectionTitle.length());
            if (nextIndex != -1 && nextIndex < endIndex) {
                endIndex = nextIndex;
            }
        }

        return fullText.substring(startIndex, endIndex).trim();
    }

    /**
     * Extracts infrastructure from HTML table.
     */
    private String extractTableData(Element table) {
        StringBuilder tableData = new StringBuilder();
        Elements rows = table.select("tr");

        for (Element row : rows) {
            Elements cells = row.select("td, th");
            List<String> cellTexts = new ArrayList<>();

            for (Element cell : cells) {
                cellTexts.add(cell.text().trim());
            }

            if (!cellTexts.isEmpty()) {
                tableData.append(String.join(" | ", cellTexts)).append("\n");
            }
        }

        return tableData.toString();
    }

    /**
     * Finds report items in the document.
     */
    private Elements findReportItems(Document doc) {
        Elements items = doc.select("div.firstitemknhn h3 > a");
        if (!items.isEmpty()) {
            return items;
        }

        // Fallback
        return doc.select("*:containsOwn(Báo cáo nhanh)");
    }

    /**
     * Extracts date string from an element.
     */
    private String extractDateFromElement(Element item) {
        String text = item.text();

        // Try pattern with parentheses: (dd/mm/yyyy)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\((\\d{2}/\\d{2}/\\d{4})\\)");
        java.util.regex.Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // Try various dd/mm/yyyy patterns
        String[] patterns = {
            "(\\d{2}/\\d{2}/\\d{4})",
            "(\\d{2}/\\d{1}/\\d{4})",
            "(\\d{1}/\\d{2}/\\d{4})",
            "(\\d{1}/\\d{1}/\\d{4})"
        };

        for (String patternStr : patterns) {
            pattern = java.util.regex.Pattern.compile(patternStr);
            matcher = pattern.matcher(text);
            if (matcher.find()) {
                String dateStr = matcher.group(1);
                // Normalize format
                String[] parts = dateStr.split("/");
                if (parts.length == 3) {
                    String day = parts[0].length() == 1 ? "0" + parts[0] : parts[0];
                    String month = parts[1].length() == 1 ? "0" + parts[1] : parts[1];
                    return day + "/" + month + "/" + parts[2];
                }
            }
        }

        return null;
    }

    /**
     * Extracts date from URL.
     */
    private LocalDate extractDateFromUrl(String link) {
        int idx = link.indexOf("ngay-");
        if (idx == -1) {
            return null;
        }

        String sub = link.substring(idx + 5);
        int dot = sub.indexOf(".");
        if (dot != -1) {
            sub = sub.substring(0, dot);
        }

        String[] parts = sub.split("-");
        String day;
        String month;
        String year;

        if (parts.length == 3) {
            day = parts[0];
            month = parts[1];
            year = parts[2];
        } else if (parts.length == 2) {
            String dm = parts[0];
            year = parts[1];

            if (dm.length() == 3) {
                day = dm.substring(0, 2);
                month = dm.substring(2);
            } else if (dm.length() == 4) {
                day = dm.substring(0, 2);
                month = dm.substring(2);
            } else {
                return null;
            }
        } else {
            return null;
        }

        // Normalize
        if (day.length() == 1) day = "0" + day;
        if (month.length() == 1) month = "0" + month;

        try {
            String dateStr = day + "/" + month + "/" + year;
            return LocalDate.parse(dateStr, DATE_FORMATTER);
        } catch (Exception e) {
            logger.warn("Failed to parse date from URL: {}", link);
            return null;
        }
    }

    /**
     * Extracts link from an element.
     */
    private String extractLinkFromElement(Element item) {
        Element link = item.selectFirst("a[href]");
        if (link != null) {
            String href = link.attr("href");
            if (href.startsWith("http")) {
                return href;
            } else {
                return URL_PREFIX + href;
            }
        }
        return null;
    }

    /**
         * Helper class to store date ranges.
         */
        private record DateRange(LocalDate newest, LocalDate oldest) {
    }

    /**
     * Utility class for fetching storm information from Google search.
     * Extracts storm landfall dates for use in newspaper article searches.
     */
    public static final class StormInfoFetcher {
        private static final Logger logger = LoggerFactory.getLogger(StormInfoFetcher.class);

        /**
         * Private constructor to prevent instantiation.
         */
        private StormInfoFetcher() {}

        /**
         * Fetches the storm start time by searching Google for storm information.
         * Uses AI Overview results to extract the landfall date.
         *
         * @param driver the WebDriver instance to use
         * @param stormName the name of the storm (e.g., "Yagi")
         * @return date string in format "DD/MM/YYYY" or null if not found
         */
        public static String fetchStormStartTime(WebDriver driver, String stormName) {
            logger.info("Fetching storm start time for: {}", stormName);
            String result = null;

            try {
                // Hide webdriver detection
                JavascriptExecutor js = (JavascriptExecutor) driver;
                js.executeScript("Object.defineProperty(navigator, 'webdriver', {get: () => undefined})");

                // Build search query
                String query = "bão " + stormName + " vào đất liền việt nam vào thời gian nào";
                String searchUrl = "https://www.google.com/search?q=" +
                    query.replace(" ", "+") + "&hl=vi";

                logger.debug("Searching Google with query: {}", query);
                driver.get(searchUrl);

                WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5)); // Reduced timeout
                wait.until(ExpectedConditions.presenceOfElementLocated(By.cssSelector("strong.Yjhzub")));

                // Extract bold text from AI Overview
                List<WebElement> boldTexts = driver.findElements(By.cssSelector("strong.Yjhzub"));
                if (!boldTexts.isEmpty()) {
                    StringBuilder boldContent = new StringBuilder();
                    for (WebElement elem : boldTexts) {
                        String text = elem.getText();
                        if (text != null && !text.trim().isEmpty()) {
                            boldContent.append(text).append(" ");
                        }
                    }

                    if (!boldContent.isEmpty()) {
                        String extractedText = boldContent.toString().trim();
                        logger.debug("Extracted text: {}", extractedText);

                        // Parse the date
                        LocalDate parsedDate = FacebookPostScraper.DateParser.parseVietnameseDate(extractedText);
                        if (parsedDate != null) {
                            result = FacebookPostScraper.DateParser.formatDate(parsedDate);
                            logger.info("Successfully parsed storm date: {}", result);
                            return result;
                        }
                    }
                }

                // Try backup selectors if main one fails
                List<WebElement> boldTextsBackup = driver.findElements(By.cssSelector(
                    ".hgKElc strong, .LGOjhe strong, .Z0LcW strong, .V3FYCf strong"
                ));

                if (!boldTextsBackup.isEmpty()) {
                    StringBuilder boldContent = new StringBuilder();
                    for (WebElement elem : boldTextsBackup) {
                        String text = elem.getText();
                        if (text != null && !text.trim().isEmpty()) {
                            boldContent.append(text).append(" ");
                        }
                    }

                    if (!boldContent.isEmpty()) {
                        String extractedText = boldContent.toString().trim();
                        logger.debug("Extracted text (backup): {}", extractedText);

                        LocalDate parsedDate = FacebookPostScraper.DateParser.parseVietnameseDate(extractedText);
                        if (parsedDate != null) {
                            result = FacebookPostScraper.DateParser.formatDate(parsedDate);
                            logger.info("Successfully parsed storm date: {}", result);
                            return result;
                        }
                    }
                }

                logger.warn("Could not find storm information for: {}", stormName);

            } catch (Exception e) {
                logger.error("Error fetching storm information: {}", e.getMessage(), e);
            }

            return result;
        }

        /**
         * Gets the date range for searching articles around a storm event.
         * Returns [startDate, endDate] where startDate is 3 days before
         * and endDate is 5 days after the given date.
         *
         * @param dateString date in format "DD/MM/YYYY"
         * @return array with [startDate, endDate] in format "DD/MM/YYYY"
         */
        public static String[] getDateRange(String dateString) {
            if (dateString == null || dateString.isEmpty()) {
                logger.warn("Cannot get date range for null/empty date string");
                return null;
            }

            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                LocalDate date = LocalDate.parse(dateString, formatter);

                LocalDate before3Days = date.minusDays(3);
                LocalDate after5Days = date.plusDays(5);

                String[] range = new String[]{
                    before3Days.format(formatter),
                    after5Days.format(formatter)
                };

                logger.debug("Date range for {}: {} to {}", dateString, range[0], range[1]);
                return range;

            } catch (Exception e) {
                logger.error("Error calculating date range for '{}': {}", dateString, e.getMessage());
                return null;
            }
        }
    }
}

