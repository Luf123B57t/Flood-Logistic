package com.aidsight.infrastructure.scraping.scraper.impl;

import com.aidsight.infrastructure.config.ScraperConfig;
import com.aidsight.domain.model.instance.FacebookPostInstance;
import com.aidsight.infrastructure.scraping.scraper.Scraper;
import com.aidsight.infrastructure.scraping.scraper.Scraper.ProgressHelper.BranchType;
import com.aidsight.infrastructure.scraping.exception.ScraperException;
import com.aidsight.infrastructure.scraping.factory.WebDriverFactory;
import org.openqa.selenium.*;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Scraper implementation for Facebook posts.
 * Searches Facebook for posts matching keywords and extracts post content,
 * reactions, comments, and metadata.
 * Implementation follows the detailed scraping process:
 * 1. Login to Facebook with credentials
 * 2. Search for posts using keywords
 * 3. For each post: extract time, reactions, comments, and content
 */
public class FacebookPostScraper implements Scraper<FacebookPostInstance> {
    private static final Logger logger = LoggerFactory.getLogger(FacebookPostScraper.class);

    private static final List<String> SKIP_TEXTS = List.of(
        "Thích", "Like", "Trả lời", "Reply", "Chia sẻ", "Share",
        "bình luận", "comment", "Xem thêm", "View more", "Viết bình luận"
    );
    private static final String FB_EMAIL = "0963816281";
    private static final String FB_PASSWORD = "Testing123";

    @Override
    public List<FacebookPostInstance> scrape(String keywords, int maxResults, ProgressCallback progressCallback) throws ScraperException {
        logger.info("Starting Facebook scraping for keywords: '{}', max results: {}", keywords, maxResults);

        List<FacebookPostInstance> posts = new ArrayList<>();
        WebDriver driver = null;

        try {
            // Create WebDriver
            ProgressHelper.report(progressCallback, 0.0, 0, BranchType.ROOT, "Initializing browser...");
            driver = WebDriverFactory.createChromeDriver();
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(ScraperConfig.getRequestTimeoutSeconds()));

            // Step 1: Login to Facebook
            logger.info("Step 1: Logging in to Facebook...");
            ProgressHelper.report(progressCallback, 0.05, 0, BranchType.BRANCH, "Logging in to Facebook...");
            loginToFacebook(driver, wait, progressCallback);
            logger.info("Login successful");
            ProgressHelper.report(progressCallback, 0.1, 1, BranchType.END, "Login successful");

            // Step 2: Find post URLs
            logger.info("Step 2: Finding Facebook post URLs");
            ProgressHelper.report(progressCallback, 0.15, 0, BranchType.BRANCH, "Searching for posts...");
            List<String> postUrls = findPostUrls(driver, keywords, maxResults, progressCallback);
            logger.info("Found {} post URLs", postUrls.size());

            if (postUrls.isEmpty()) {
                logger.info("No posts found");
                return posts;
            }

            ProgressHelper.report(progressCallback, 0.3, 1, BranchType.END, "Found " + postUrls.size() + " posts. Starting scraping...");

            // Step 3: Scrape each post
            logger.info("Step 3: Scraping posts");
            int scrapedCount = 0;
            for (int i = 0; i < postUrls.size() && scrapedCount < maxResults; i++) {
                // Check if thread was interrupted
                if (Thread.currentThread().isInterrupted()) {
                    logger.info("Facebook scraping interrupted by user");
                    throw new InterruptedException("Scraping cancelled");
                }

                String postUrl = postUrls.get(i);

                try {
                    logger.debug("Scraping post {}/{}: {}", i + 1, postUrls.size(), postUrl);

                    // Base progress: 30% for login/search, 70% for scraping posts
                    double baseProgress = 0.3 + ((double) scrapedCount / maxResults) * 0.7;

                    if (progressCallback != null) {
                        progressCallback.onProgress(baseProgress, "├─ Scraping post " + (i + 1) + " of " + postUrls.size() + "...");
                    }

                    FacebookPostInstance post = scrapePost(driver, postUrl, i + 1, postUrls.size(), progressCallback, baseProgress);

                    posts.add(post);
                    scrapedCount++;

                    double progress = 0.3 + ((double) scrapedCount / maxResults) * 0.7;
                    String message = String.format("Scraped Facebook post %d of %d", scrapedCount, maxResults);
                    ProgressHelper.report(progressCallback, progress, 1, BranchType.END, message);

                    logger.info("Successfully scraped post {}/{}", scrapedCount, maxResults);

                    // Delay between posts (Thread.sleep will throw InterruptedException if interrupted)
                    if (i < postUrls.size() - 1) {
                        //noinspection BusyWait - Intentional rate limiting delay between post requests
                        Thread.sleep(ScraperConfig.getScrapeDelayMs());
                    }

                } catch (InterruptedException e) {
                    logger.info("Facebook scraping interrupted during delay");
                    throw e; // Re-throw to exit the scraping
                } catch (Exception e) {
                    logger.error("Failed to scrape post {}: {}", postUrl, e.getMessage());
                    ProgressHelper.report(progressCallback, 0.3 + ((double) scrapedCount / maxResults) * 0.7,
                        1, BranchType.END, "Failed to scrape post " + (i + 1) + " - continuing...");
                    // Continue with next post
                }
            }

            logger.info("Successfully scraped {} Facebook posts", posts.size());
            return posts;

        } catch (InterruptedException e) {
            logger.info("Facebook scraping was interrupted/cancelled");
            // Re-throw as ScraperException to signal cancellation
            throw new ScraperException("Facebook scraping cancelled by user", e);
        } catch (Exception e) {
            logger.error("Facebook scraping failed: {}", e.getMessage(), e);
            throw new ScraperException("Failed to scrape Facebook posts: " + e.getMessage(), e);
        } finally {
            WebDriverFactory.closeDriver(driver);
        }
    }

    @Override
    public String getPlatformName() {
        return "Facebook";
    }

    @Override
    public Class<FacebookPostInstance> getInstanceType() {
        return FacebookPostInstance.class;
    }

    /**
     * Finds Facebook post URLs matching the given keywords.
     */
    private List<String> findPostUrls(WebDriver driver, String keyword, int maxPosts, ProgressCallback progressCallback) throws Exception {
        logger.debug("Starting search for keyword: {}", keyword);
        Set<String> postUrls = new HashSet<>();
        JavascriptExecutor js = (JavascriptExecutor) driver;

        // Create search URL
        String searchUrl = "https://www.facebook.com/search/posts?q=" +
                URLEncoder.encode(keyword, StandardCharsets.UTF_8);

        if (progressCallback != null) {
            progressCallback.onProgress(0.15, ProgressHelper.formatMessage(1, BranchType.BRANCH, "Loading search page..."));
        }
        driver.get(searchUrl);
        Thread.sleep(3000);

        // Scroll to load results
        int scrollCount = 0;
        while (postUrls.size() < maxPosts && scrollCount < 20) {
            // Check if thread was interrupted
            if (Thread.currentThread().isInterrupted()) {
                logger.info("Post URL search interrupted by user");
                throw new InterruptedException("Scraping cancelled during URL search");
            }

            scrollCount++;
            logger.debug("Scroll {}, found {} links...", scrollCount, postUrls.size());
            if (progressCallback != null) {
                double scrollProgress = 0.15 + (scrollCount / 20.0) * 0.15; // 0.15 to 0.30
                progressCallback.onProgress(scrollProgress, ProgressHelper.formatMessage(2, BranchType.BRANCH, "Scrolling to load posts... (scroll " + scrollCount + ", found " + postUrls.size() + " posts)"));
            }

            // Scroll main window
            js.executeScript("window.scrollTo(0, document.body.scrollHeight);");

            // Wait for page to load new content after scroll
            //noinspection BusyWait - Intentional delay for web page content loading
            Thread.sleep(3000);

            // Find all post links
            List<WebElement> linkElements = driver.findElements(
                By.xpath("//a[starts-with(@href, 'https://www.facebook.com/') and (contains(@href, '/posts/') or contains(@href, '/videos/') or contains(@href, '/p/'))]")
            );

            for (WebElement linkEl : linkElements) {
                String href = linkEl.getAttribute("href");

                if (isValidPostUrl(href)) {
                    postUrls.add(href);
                    if (postUrls.size() >= maxPosts) {
                        break;
                    }
                }
            }
        }

        logger.debug("Found {} valid post URLs", postUrls.size());
        return new ArrayList<>(postUrls);
    }

    /**
     * Logs in to Facebook using provided credentials.
     */
    private void loginToFacebook(WebDriver driver, WebDriverWait wait, ProgressCallback progressCallback) throws Exception {
        logger.debug("Navigating to Facebook login page");
        ProgressHelper.report(progressCallback, 0.05, 1, BranchType.BRANCH, "Opening Facebook login page...");
        driver.get("http://facebook.com");

        // Enter email
        ProgressHelper.report(progressCallback, 0.06, 2, BranchType.BRANCH, "Entering credentials...");
        WebElement txtUser = wait.until(
            ExpectedConditions.visibilityOfElementLocated(By.id("email"))
        );
        txtUser.sendKeys(FB_EMAIL);
        Thread.sleep(5000);

        // Enter password
        WebElement txtPass = wait.until(
            ExpectedConditions.visibilityOfElementLocated(By.id("pass"))
        );
        txtPass.sendKeys(FB_PASSWORD);
        Thread.sleep(5000);
        ProgressHelper.report(progressCallback, 0.07, 2, BranchType.BRANCH, "Submitting login...");
        txtPass.sendKeys(Keys.ENTER);
        Thread.sleep(2000);

        logger.debug("Waiting for login verification...");
        ProgressHelper.report(progressCallback, 0.08, 2, BranchType.BRANCH, "Waiting for login verification...");
        wait.until(ExpectedConditions.visibilityOfElementLocated(
            By.xpath("//a[@aria-label='Trang chủ']")
        ));

        logger.debug("Login verification complete");
    }

    /**
     * Validates if a URL is a valid Facebook post URL.
     */
    private boolean isValidPostUrl(String href) {
        if (href == null || href.isEmpty()) {
            return false;
        }

        if (href.contains("/help/") || href.contains("/comment/") || href.contains("/reply/")) {
            return false;
        }

        if (href.contains("/groups/")) {
            return href.contains("/posts/");
        }

        return href.contains("/posts/") || href.contains("/p/");
    }

    /**
     * Scrapes a single Facebook post.
     */
    private FacebookPostInstance scrapePost(WebDriver driver, String postUrl, int postIndex, int totalPosts,
                                           ProgressCallback progressCallback, double baseProgress) throws Exception {
        WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(ScraperConfig.getRequestTimeoutSeconds()));
        JavascriptExecutor js = (JavascriptExecutor) driver;
        Actions actions = new Actions(driver);

        // VISIT 1: Extract posted time
        logger.debug("Visit 1/3: Extracting posted time");
        ProgressHelper.report(progressCallback, baseProgress, 1, BranchType.BRANCH, "Post " + postIndex + "/" + totalPosts + ": Extracting posted time...");
        driver.get(postUrl);
        Thread.sleep(3000);

        // Prepare comment section for time extraction
        switchToAllCommentsView(wait);

        try {
            WebElement popup = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("(//div[@role='dialog'])[last()]")
            ));
            WebElement scrollableDiv = findScrollableDiv(js, popup);
            Thread.sleep(2000);
            js.executeScript("arguments[0].scrollTop = arguments[0].scrollTop + 500", scrollableDiv);
            Thread.sleep(3000);
        } catch (Exception e) {
            logger.debug("Could not prepare comment section for time extraction");
        }

        String postedTime = extractPostedTime(driver, js, actions);
        LocalDate postedDate = DateParser.parseVietnameseDate(postedTime);
        // Keep postedDate as null if it cannot be parsed, instead of defaulting to today's date
        ProgressHelper.report(progressCallback, baseProgress, 2, BranchType.END, "Post " + postIndex + "/" + totalPosts + ": Posted time extracted");

        // VISIT 2: Extract reactions
        logger.debug("Visit 2/3: Extracting reactions");
        ProgressHelper.report(progressCallback, baseProgress, 1, BranchType.BRANCH, "Post " + postIndex + "/" + totalPosts + ": Extracting reactions...");
        driver.get(postUrl);
        waitForPageLoad(wait);
        switchToAllCommentsView(wait);

        Thread.sleep(3000);
        int[] reactions = extractReactions(driver, wait, js);
        Thread.sleep(2000);
        if (progressCallback != null) {
            int totalReactions = reactions[0] + reactions[1] + reactions[2] + reactions[3] + reactions[4] + reactions[5];
            progressCallback.onProgress(baseProgress, ProgressHelper.formatMessage(2, BranchType.END, "Post " + postIndex + "/" + totalPosts + ": Reactions extracted (" + totalReactions + " total)"));
        }

        // VISIT 3: Extract comments and content
        logger.debug("Visit 3/3: Extracting comments and content");
        ProgressHelper.report(progressCallback, baseProgress, 1, BranchType.BRANCH, "Post " + postIndex + "/" + totalPosts + ": Extracting comments...");
        driver.get(postUrl);
        waitForPageLoad(wait);
        switchToAllCommentsView(wait);
        Thread.sleep(3000);

        List<String> comments = extractComments(wait, js, postIndex, totalPosts, progressCallback, baseProgress);

        ProgressHelper.report(progressCallback, baseProgress, 2, BranchType.END, "Post " + postIndex + "/" + totalPosts + ": Comments extracted (" + comments.size() + " comments)");

        ProgressHelper.report(progressCallback, baseProgress, 1, BranchType.BRANCH, "Post " + postIndex + "/" + totalPosts + ": Extracting post content...");
        String content = extractPostContent(driver);
        ProgressHelper.report(progressCallback, baseProgress, 2, BranchType.END, "Post " + postIndex + "/" + totalPosts + ": Post content extracted");

        // Create instance
        FacebookPostInstance post = new FacebookPostInstance();
        post.setUrl(postUrl);
        post.setContent(content);
        post.setComments(comments);
        post.setPostedDate(postedDate);
        post.setReactionsCount(reactions);

        return post;
    }

    /**
     * Waits for the Facebook page to load by checking for common elements.
     */
    private void waitForPageLoad(WebDriverWait wait) {
        try {
            logger.debug("Waiting for page load...");
            wait.until(ExpectedConditions.or(
                ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@aria-label='Viết bình luận']")),
                ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@aria-label='Thích']")),
                ExpectedConditions.presenceOfElementLocated(By.xpath("//span[contains(text(), 'Bình luận')]"))
            ));
            logger.debug("Page loaded successfully");
        } catch (TimeoutException e) {
            logger.warn("Page load timeout");
        }
    }

    /**
     * Switches the comment view from "Most relevant" to "All comments".
     */
    private void switchToAllCommentsView(WebDriverWait wait) throws InterruptedException {
        Thread.sleep(3000);
        try {
            WebElement sortButton1 = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//span[contains(text(), 'Phù hợp nhất')]")
            ));
            sortButton1.click();
            WebElement sortButton2 = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("//span[contains(text(), 'Tất cả bình luận')]")
            ));
            sortButton2.click();
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            throw e; // Re-throw InterruptedException
        } catch (Exception e) {
            logger.debug("Could not switch to all comments view");
        }
    }

    /**
     * Extracts the posted time by hovering over the time link.
     */
    private String extractPostedTime(WebDriver driver, JavascriptExecutor js, Actions actions) {
        String foundTime = "Unknown";

        try {
            List<WebElement> links = driver.findElements(By.tagName("a"));
            WebElement timeLink = null;

            for (WebElement link : links) {
                String href = link.getAttribute("href");
                String text = link.getText();

                if (href != null && href.contains("/posts/")) {
                    if (text != null && text.matches("^\\d+[smhdwy]$")) {
                        timeLink = link;
                        break;
                    }
                }
            }

            if (timeLink == null) {
                int minLength = Integer.MAX_VALUE;
                for (WebElement link : links) {
                    String href = link.getAttribute("href");
                    String text = link.getText();

                    if (href != null && href.contains("/posts/")) {
                        if (text != null && !text.isEmpty() && text.length() < minLength && text.length() < 20) {
                            timeLink = link;
                            minLength = text.length();
                        }
                    }
                }
            }

            if (timeLink != null) {
                js.executeScript("arguments[0].scrollIntoView({block: 'center', inline: 'center'});", timeLink);
                Thread.sleep(2000);
                actions.moveToElement(timeLink).perform();
                Thread.sleep(2500);

                List<WebElement> tooltips = driver.findElements(By.xpath("//div[@role='tooltip']"));
                Thread.sleep(1000);
                for (WebElement tooltip : tooltips) {
                    try {
                        WebElement spanElement = tooltip.findElement(By.tagName("span"));
                        Thread.sleep(1500);
                        String tooltipText = spanElement.getText();

                        if (tooltipText != null && !tooltipText.isEmpty()) {
                            if (tooltipText.matches(".*\\d{1,2}.*\\d{4}.*") ||
                                tooltipText.toLowerCase().contains("at") ||
                                tooltipText.toLowerCase().contains("lúc") ||
                                tooltipText.toLowerCase().contains("tháng")) {
                                foundTime = tooltipText;
                                break;
                            }
                        }
                    } catch (Exception e) {
                        String tooltipText = tooltip.getText();
                        if (tooltipText != null && !tooltipText.isEmpty()) {
                            foundTime = tooltipText;
                            break;
                        }
                    }
                }

                if ("Unknown".equals(foundTime)) {
                    String ariaLabel = timeLink.getAttribute("aria-label");
                    if (ariaLabel != null && !ariaLabel.isEmpty()) {
                        foundTime = ariaLabel;
                    } else {
                        foundTime = timeLink.getText();
                    }
                }
            }

            logger.debug("Extracted time: {}", foundTime);
        } catch (Exception e) {
            logger.warn("Failed to extract time: {}", e.getMessage());
        }

        return foundTime;
    }

    /**
     * Extracts reaction counts from the post.
     */
    private int[] extractReactions(WebDriver driver, WebDriverWait wait, JavascriptExecutor js) {
        int[] reactionCounts = new int[FacebookPostInstance.Reaction.values().length];
        Map<String, String> reactionPatterns = setupReactionPatterns();
        Map<String, Integer> reactions = new LinkedHashMap<>();

        try {
            Thread.sleep(3000);

            WebElement reactionsButton = wait.until(ExpectedConditions.elementToBeClickable(
                By.xpath("(//div[@role='button' and .//div[contains(text(), 'Tất cả cảm xúc:')]])[last()]")
            ));
            reactionsButton.click();

            wait.until(ExpectedConditions.presenceOfElementLocated(By.xpath("//div[@role='tablist']")));

            // STEP 1: Get reactions displayed directly in tablist
            for (Map.Entry<String, String> entry : reactionPatterns.entrySet()) {
                String reactionName = entry.getKey();
                String pattern = entry.getValue();

                try {
                    WebElement countSpan = driver.findElement(
                        By.xpath("//div[@role='tablist']//div[@role='tab' and .//img[contains(@src, '" + pattern + "')]]//span[contains(@class, 'xi81zsa')]")
                    );
                    String count = countSpan.getText();
                    if (!count.isEmpty() && !count.equals("Xem thêm")) {
                        reactions.put(reactionName, parseNumber(count));
                        logger.debug("{}: {}", reactionName, count);
                    }
                } catch (org.openqa.selenium.NoSuchElementException e) {
                    // Reaction not found in tablist, skip for now
                }
            }

            Thread.sleep(500);

            // STEP 2: Click "Xem thêm" to open dropdown menu for additional reactions
            try {
                WebElement viewMoreTab = driver.findElement(
                    By.xpath("//div[@role='tablist']//div[@role='tab']//span[text()='Xem thêm']")
                );

                WebElement parentTab = viewMoreTab.findElement(By.xpath("./ancestor::div[@role='tab']"));
                js.executeScript("arguments[0].click();", parentTab);
                Thread.sleep(1000);

                // STEP 3: Wait for dropdown menu to appear
                wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.xpath("//div[@role='menu']")
                ));

                // STEP 4: Get reactions from dropdown menu
                for (Map.Entry<String, String> entry : reactionPatterns.entrySet()) {
                    String reactionName = entry.getKey();
                    String pattern = entry.getValue();

                    // Skip if already extracted in step 1
                    if (reactions.containsKey(reactionName)) {
                        continue;
                    }

                    try {
                        // Find in dropdown menu (role="menuitemradio")
                        WebElement menuItem = driver.findElement(
                            By.xpath("//div[@role='menu']//div[@role='menuitemradio' and .//img[contains(@src, '" + pattern + "')]]")
                        );

                        // Get count from span in menu item
                        WebElement countSpan = menuItem.findElement(
                            By.xpath(".//span[contains(@class, 'x193iq5w') and string-length(text()) > 0 and string-length(text()) < 10]")
                        );

                        String count = countSpan.getText();
                        reactions.put(reactionName, parseNumber(count));
                        logger.debug("{}: {}", reactionName, count);

                    } catch (org.openqa.selenium.NoSuchElementException e) {
                        // Reaction not found in dropdown either
                    }
                }

            } catch (org.openqa.selenium.NoSuchElementException e) {
                logger.debug("No 'Xem thêm' button - all reactions visible");
            }

            // Fill in zeros for missing reactions
            for (String reactionName : reactionPatterns.keySet()) {
                if (!reactions.containsKey(reactionName)) {
                    reactions.put(reactionName, 0);
                }
            }

            // Close reactions popup
            Thread.sleep(1000);
            try {
                WebElement closeButton = wait.until(ExpectedConditions.elementToBeClickable(
                    By.xpath("(//div[@aria-label='Đóng' and @role='button'])[last()]")
                ));
                closeButton.click();
            } catch (Exception e) {
                logger.debug("Could not close reactions popup");
            }

        } catch (Exception e) {
            logger.warn("Failed to extract reactions: {}", e.getMessage());
        }

        // Map to array
        reactionCounts[FacebookPostInstance.Reaction.LIKE.ordinal()] = reactions.getOrDefault("Like", 0);
        reactionCounts[FacebookPostInstance.Reaction.LOVE.ordinal()] = reactions.getOrDefault("Love", 0);
        reactionCounts[FacebookPostInstance.Reaction.CARE.ordinal()] = reactions.getOrDefault("Care", 0);
        reactionCounts[FacebookPostInstance.Reaction.HAHA.ordinal()] = reactions.getOrDefault("Haha", 0);
        reactionCounts[FacebookPostInstance.Reaction.WOW.ordinal()] = reactions.getOrDefault("Wow", 0);
        reactionCounts[FacebookPostInstance.Reaction.SAD.ordinal()] = reactions.getOrDefault("Sad", 0);
        reactionCounts[FacebookPostInstance.Reaction.ANGRY.ordinal()] = reactions.getOrDefault("Angry", 0);

        return reactionCounts;
    }

    /**
     * Extracts comments from the post.
     * Note: Assumes the page is already in "all comments" view.
     */
    private List<String> extractComments(WebDriverWait wait, JavascriptExecutor js,
                                         int postIndex, int totalPosts, ProgressCallback progressCallback, double baseProgress) {
        List<String> comments = new ArrayList<>();

        try {
            WebElement popup = wait.until(ExpectedConditions.visibilityOfElementLocated(
                By.xpath("(//div[@role='dialog'])[last()]")
            ));
            WebElement scrollableDiv = findScrollableDiv(js, popup);

            if (scrollableDiv == null) {
                logger.warn("Could not find scrollable div for comments");
                return comments;
            }

            logger.debug("Found scrollable div, starting to load comments");

            // Scroll to load comments
            int scrollCount = 0;
            int lastCommentCount = 0;
            int sameCount = 0;

            while (scrollCount < ScraperConfig.getMaxScrolls()) {
                scrollCount++;
                js.executeScript("arguments[0].scrollTop = arguments[0].scrollTop + 500", scrollableDiv);

                // Wait for comments to load after scroll
                //noinspection BusyWait - Intentional delay for dynamic comment loading
                Thread.sleep(2000);

                int commentCount = popup.findElements(By.xpath(".//span/div/div")).size();
                logger.debug("Scroll {}: {} comments", scrollCount, commentCount);

                if (progressCallback != null) {
                    progressCallback.onProgress(baseProgress, ProgressHelper.formatMessage(2, BranchType.BRANCH, "Post " + postIndex + "/" + totalPosts + ": Scrolling comments... (scroll " + scrollCount + ", found " + commentCount + " comments)"));
                }

                if (commentCount == lastCommentCount) {
                    sameCount++;
                } else {
                    sameCount = 0;
                    lastCommentCount = commentCount;
                }

                if (sameCount >= 5) {
                    logger.debug("Loaded all comments");
                    if (progressCallback != null) {
                        progressCallback.onProgress(baseProgress, ProgressHelper.formatMessage(3, BranchType.END, "Post " + postIndex + "/" + totalPosts + ": All comments loaded (" + commentCount + " comments)"));
                    }
                    break;
                }

                if (scrollCount == ScraperConfig.getMaxScrolls()) {
                    logger.debug("Reached max scrolls");
                }
            }

            // Extract comment texts
            Set<String> seenComments = new HashSet<>();
            List<WebElement> commentElems = popup.findElements(
                By.xpath(".//div[@dir='auto' and string-length(text()) > 5]")
            );

            for (WebElement elem : commentElems) {
                try {
                    String text = elem.getText().trim();
                    if (isValidComment(text, seenComments)) {
                        seenComments.add(text);
                        comments.add(text);
                    }
                } catch (Exception e) {
                    // Skip invalid elements
                }
            }

        } catch (Exception e) {
            logger.warn("Failed to extract comments: {}", e.getMessage());
        }

        logger.debug("Extracted {} comments", comments.size());
        return comments;
    }

    /**
     * Extracts the main post content.
     */
    private String extractPostContent(WebDriver driver) {
        try {
            List<WebElement> contentElements = driver.findElements(
                By.xpath("//div[@infrastructure-ad-preview='message']")
            );

            if (!contentElements.isEmpty()) {
                return contentElements.getFirst().getText();
            }
        } catch (Exception e) {
            logger.debug("Could not extract post content: {}", e.getMessage());
        }

        return "";
    }

    /**
     * Finds a scrollable div within a popup.
     */
    private WebElement findScrollableDiv(JavascriptExecutor js, WebElement popup) {
        List<WebElement> allDivs = popup.findElements(By.xpath(".//div"));
        WebElement bestCandidate = null;
        long maxScrollHeight = 0;

        for (int i = 0; i < Math.min(allDivs.size(), 200); i++) {
            WebElement div = allDivs.get(i);
            try {
                long scrollHeight = (Long) js.executeScript("return arguments[0].scrollHeight", div);
                long clientHeight = (Long) js.executeScript("return arguments[0].clientHeight", div);

                if (scrollHeight > clientHeight && scrollHeight > 300) {
                    if (scrollHeight > maxScrollHeight) {
                        maxScrollHeight = scrollHeight;
                        bestCandidate = div;
                    }
                }
            } catch (Exception e) {
                // Skip
            }
        }

        if (bestCandidate != null) {
            logger.debug("Found scrollable div with height: {}", maxScrollHeight);
        }

        return bestCandidate;
    }

    /**
     * Validates if text is a valid comment.
     */
    private boolean isValidComment(String text, Set<String> seenComments) {
        if (text == null || text.length() <= 5) {
            return false;
        }
        if (seenComments.contains(text)) {
            return false;
        }
        for (String skip : SKIP_TEXTS) {
            if (text.contains(skip)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Parses a number string that may contain "k" suffix.
     */
    private int parseNumber(String s) {
        s = s.trim().toLowerCase().replace(",", ".");
        if (s.endsWith("k")) {
            String num = s.substring(0, s.length() - 1);
            double value = Double.parseDouble(num);
            return (int) (value * 1000);
        }
        return Integer.parseInt(s);
    }

    /**
     * Sets up reaction pattern mappings.
     */
    private Map<String, String> setupReactionPatterns() {
        Map<String, String> patterns = new LinkedHashMap<>();
        patterns.put("Like", "An-HX414PnqCVzyEq9OFF");
        patterns.put("Love", "An8VnwvdkGMXIQcr4C62");
        patterns.put("Care", "An95QHaxAbMTp2SyUXLp");
        patterns.put("Haha", "An8jKAygX0kuKnUS351U");
        patterns.put("Wow", "An-r5ENfro_aq4TtchBwMAVpq461");
        patterns.put("Sad", "An855a_dxeehKWf2PSOqZw5jG");
        patterns.put("Angry", "An-POmkU-_NNTTsdRMlBuMN");
        return patterns;
    }

    /**
     * Utility class for parsing Vietnamese date formats into LocalDate objects.
     * Supports various date formats commonly found in Vietnamese text.
     */
    public static final class DateParser {
        private static final Logger logger = LoggerFactory.getLogger(DateParser.class);

        /**
         * Private constructor to prevent instantiation.
         */
        private DateParser() {}

        /**
         * Parses a Vietnamese date string into a LocalDate.
         * Supports multiple formats including:
         * - "ngày DD tháng MM năm YYYY"
         * - "DD/MM/YYYY"
         * - "đêm ngày DD và rạng sáng ngày DD+1 tháng MM năm YYYY"
         * - "chiều ngày DD tháng MM năm YYYY"
         *
         * @param text the text containing a date
         * @return LocalDate if parsing successful, null otherwise
         */
        public static LocalDate parseVietnameseDate(String text) {
            if (text == null || text.isEmpty()) {
                return null;
            }

            try {
                // Normalize text
                text = text.trim().replaceAll("\\s+", " ");

                // Try day of week with time pattern first
                // "Chủ Nhật, 5 Tháng 10, 2025 lúc 15:56"
                LocalDate date = tryDayOfWeekPattern(text);
                if (date != null) return date;

                // Try consecutive days pattern
                // "đêm ngày 28 và rạng sáng ngày 29 tháng 9 năm 2024"
                date = tryConsecutiveDaysPattern(text);
                if (date != null) return date;

                // Try single day with time pattern
                // "chiều ngày 27/10/2024" or "vào khoảng trưa và chiều ngày 7 tháng 9 năm 2024"
                date = trySingleDayWithTimePattern(text);
                if (date != null) return date;

                // Try date with location pattern
                // "ngày 7 tháng 9 năm 2024 tại khu vực..."
                date = tryDateWithLocationPattern(text);
                if (date != null) return date;

                // Standard format: "ngày DD tháng MM năm YYYY"
                date = tryStandardDatePattern(text);
                if (date != null) return date;

                // Slash format: "DD/MM/YYYY"
                date = trySlashDatePattern(text);
                if (date != null) return date;

                // Format with location prefix: "Địa điểm, ngày DD tháng MM năm YYYY"
                date = tryLocationPrefixPattern(text);
                if (date != null) return date;

                logger.debug("No valid date pattern found in: {}", text);
                return null;

            } catch (Exception e) {
                logger.warn("Error parsing date from '{}': {}", text, e.getMessage());
                return null;
            }
        }

        /**
         * Helper method to extract month and year from matched groups.
         * Prioritizes word-based month/year over slash-based ones.
         *
         * @return int array with [month, year], or null if both are not found
         */
        private static int[] extractMonthAndYear(String slashMonth, String slashYear, String wordMonth, String wordYear) {
            int month = -1, year = -1;

            if (wordYear != null) {
                year = Integer.parseInt(wordYear);
            } else if (slashYear != null) {
                year = Integer.parseInt(slashYear);
            }

            if (wordMonth != null) {
                month = Integer.parseInt(wordMonth);
            } else if (slashMonth != null) {
                month = Integer.parseInt(slashMonth);
            }

            if (month > 0 && year > 0) {
                return new int[]{month, year};
            }
            return null;
        }

        /**
         * Helper method to apply a pattern and extract date using extractMonthAndYear helper.
         * Used for patterns with slash and word-based month/year groups.
         *
         * @param text           the text to match
         * @param pattern        the regex pattern
         * @param wordMonthGroup the group index for word-format month (or -1 if not applicable)
         * @param wordYearGroup  the group index for word-format year (or -1 if not applicable)
         * @return LocalDate if successfully parsed, null otherwise
         */
        private static LocalDate tryPatternWithMonthYearExtraction(String text, Pattern pattern,
                                                                   int wordMonthGroup,
                                                                   int wordYearGroup) {
            Matcher m = pattern.matcher(text);
            if (m.find()) {
                int day = Integer.parseInt(m.group(1));
                String slashMonth = m.group(2);
                String slashYear = m.group(3);
                String wordMonth = wordMonthGroup > 0 ? m.group(wordMonthGroup) : null;
                String wordYear = wordYearGroup > 0 ? m.group(wordYearGroup) : null;

                int[] monthYear = extractMonthAndYear(slashMonth, slashYear, wordMonth, wordYear);
                if (monthYear != null) {
                    return createDate(monthYear[1], monthYear[0], day);
                }
            }
            return null;
        }

        /**
         * Helper method to apply a simple pattern and extract date directly.
         * Used for patterns where day, month, and year are directly captured.
         *
         * @param text    the text to match
         * @param pattern the regex pattern
         * @return LocalDate if successfully parsed, null otherwise
         */
        private static LocalDate trySimplePattern(String text, Pattern pattern) {
            Matcher m = pattern.matcher(text);
            if (m.find()) {
                int day = Integer.parseInt(m.group(1));
                int month = Integer.parseInt(m.group(2));
                int year = Integer.parseInt(m.group(3));
                return createDate(year, month, day);
            }
            return null;
        }

        private static LocalDate tryDayOfWeekPattern(String text) {
            // Matches: "Chủ Nhật, 5 Tháng 10, 2025 lúc 15:56"
            // Day of week can be: Thứ Hai, Thứ Ba, Thứ Tư, Thứ Năm, Thứ Sáu, Thứ Bảy, Chủ Nhật
            Pattern pattern = Pattern.compile(
                "(?:Thứ Hai|Thứ Ba|Thứ Tư|Thứ Năm|Thứ Sáu|Thứ Bảy|Chủ Nhật),\\s*(\\d{1,2})\\s+Tháng\\s+(\\d{1,2}),\\s+(\\d{4})(?:\\s+lúc\\s+\\d{1,2}:\\d{2})?",
                Pattern.CASE_INSENSITIVE
            );
            return trySimplePattern(text, pattern);
        }

        private static LocalDate tryConsecutiveDaysPattern(String text) {
            Pattern pattern = Pattern.compile(
                "(?:đêm|chiều|tối|trưa|sáng)?\\s*ngày\\s+(\\d{1,2})(?:/(\\d{1,2})(?:/(\\d{4}))?)?\\s+và\\s+(?:rạng sáng|sáng|đêm)?\\s*ngày\\s+(\\d{1,2})(?:\\s+tháng\\s+(\\d{1,2}))?(?:\\s+năm\\s+(\\d{4}))?"
            );
            return tryPatternWithMonthYearExtraction(text, pattern, 5, 6);
        }

        private static LocalDate trySingleDayWithTimePattern(String text) {
            Pattern pattern = Pattern.compile(
                "(?:vào|Tuy nhiên,?)?\\s*(?:khoảng)?\\s*(?:trưa|chiều|tối|đêm|sáng)(?:\\s+và\\s+(?:trưa|chiều|tối|đêm|sáng))?\\s+ngày\\s+(\\d{1,2})(?:/(\\d{1,2}))?(?:/(\\d{4}))?(?:\\s+tháng\\s+(\\d{1,2}))?(?:\\s+năm\\s+(\\d{4}))?"
            );
            return tryPatternWithMonthYearExtraction(text, pattern, 4, 5);
        }

        private static LocalDate tryDateWithLocationPattern(String text) {
            Pattern pattern = Pattern.compile(
                "(?:vào|Tuy nhiên,?)?\\s*(?:khoảng)?\\s*(?:trưa|chiều|tối|đêm|sáng)?(?:\\s+và\\s+(?:trưa|chiều|tối|đêm|sáng))?\\s+ngày\\s+(\\d{1,2})(?:/(\\d{1,2}))?(?:/(\\d{4}))?(?:\\s+tháng\\s+(\\d{1,2}))?(?:\\s+năm\\s+(\\d{4}))?\\s+tại"
            );
            return tryPatternWithMonthYearExtraction(text, pattern, 4, 5);
        }

        private static LocalDate tryStandardDatePattern(String text) {
            Pattern pattern = Pattern.compile("(?:ngày\\s+)?(\\d{1,2})\\s+tháng\\s+(\\d{1,2})\\s+năm\\s+(\\d{4})");
            return trySimplePattern(text, pattern);
        }

        private static LocalDate trySlashDatePattern(String text) {
            Pattern pattern = Pattern.compile("\\b(\\d{1,2})/(\\d{1,2})/(\\d{4})\\b");
            return trySimplePattern(text, pattern);
        }

        private static LocalDate tryLocationPrefixPattern(String text) {
            Pattern pattern = Pattern.compile("[^,]+,\\s*(?:ngày\\s+)?(\\d{1,2})\\s+tháng\\s+(\\d{1,2})\\s+năm\\s+(\\d{4})");
            return trySimplePattern(text, pattern);
        }

        private static LocalDate createDate(int year, int month, int day) {
            // Validate
            if (month < 1 || month > 12 || day < 1 || day > 31 || year < 1900 || year > 2100) {
                logger.warn("Invalid date values: {}/{}/{}", day, month, year);
                return null;
            }

            try {
                return LocalDate.of(year, month, day);
            } catch (Exception e) {
                logger.warn("Failed to create date {}/{}/{}: {}", day, month, year, e.getMessage());
                return null;
            }
        }

        /**
         * Formats a LocalDate to Vietnamese date string format.
         *
         * @param date the date to format
         * @return formatted string "DD/MM/YYYY"
         */
        public static String formatDate(LocalDate date) {
            if (date == null) {
                return null;
            }
            return date.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        }
    }
}

