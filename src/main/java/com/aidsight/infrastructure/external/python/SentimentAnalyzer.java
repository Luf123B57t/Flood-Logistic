package com.aidsight.infrastructure.external.python;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Singleton class to manage sentiment analysis through a Python process.
 * <p>
 * This class launches a long-running Python process that uses a Vietnamese sentiment model
 * to analyze comments. Each comment is scored as positive (+1), negative (-1), or neutral (0).
 * The Python process stays alive for the duration of analysis and is terminated when complete.
 * </p>
 * <p>
 * Usage:
 * <pre>
 *   SentimentAnalyzer analyzer = SentimentAnalyzer.getInstance();
 *   analyzer.start();
 *   try {
 *       int score = analyzer.analyzeSentiment("This is a comment");
 *   } finally {
 *       analyzer.stop();
 *   }
 * </pre>
 * </p>
 */
public class SentimentAnalyzer {
    private static final Logger logger = LoggerFactory.getLogger(SentimentAnalyzer.class);
    private static final String SENTINEL = "<<STOP>>";
    private static final int SHUTDOWN_TIMEOUT_SECONDS = 5;

    private static SentimentAnalyzer instance;

    private Process process;
    private BufferedWriter stdin;
    private BufferedReader stdout;
    private BufferedReader stderr;
    private volatile boolean modelReady = false;
    private Thread stderrReaderThread;

    /**
     * Private constructor for singleton pattern.
     */
    private SentimentAnalyzer() {
    }

    /**
     * Gets the singleton instance of SentimentAnalyzer.
     *
     * @return the singleton instance
     */
    public static synchronized SentimentAnalyzer getInstance() {
        if (instance == null) {
            instance = new SentimentAnalyzer();
        }
        return instance;
    }

    /**
     * Starts the Python sentiment analysis process.
     * <p>
     * Launches the Python script with the virtual environment Python interpreter.
     * The process will load the sentiment model and signal when ready via stderr.
     * </p>
     *
     * @throws IOException if the process cannot be started
     */
    public void start() throws IOException {
        if (process != null && process.isAlive()) {
            logger.warn("Sentiment analyzer process is already running");
            return;
        }

        modelReady = false;

        // Get project root directory
        String projectDir = System.getProperty("user.dir");
        Path venvPath = Paths.get(projectDir, "venv");

        // Detect OS and set correct path for Python binary
        String os = System.getProperty("os.name").toLowerCase();
        boolean isWindows = os.contains("win");

        Path pythonBin;
        if (isWindows) {
            pythonBin = venvPath.resolve("Scripts").resolve("python.exe");
        } else {
            pythonBin = venvPath.resolve("bin").resolve("python");
        }

        if (!Files.exists(pythonBin)) {
            throw new IOException("Python not found at: " + pythonBin +
                                ". Please ensure venv is set up correctly.");
        }

        // Path to sentiment.py script
        Path sentimentScript = Paths.get(projectDir, "src", "main", "python", "sentiment", "sentiment.py");
        if (!Files.exists(sentimentScript)) {
            throw new IOException("Sentiment script not found at: " + sentimentScript);
        }

        // Build command
        List<String> command = new ArrayList<>();
        command.add(pythonBin.toString());
        command.add(sentimentScript.toString());

        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(Paths.get(projectDir).toFile());

        logger.info("Starting sentiment analysis process: {}", String.join(" ", command));
        process = pb.start();

        // Set up streams
        stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()));
        stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
        stderr = new BufferedReader(new InputStreamReader(process.getErrorStream()));

        // Start stderr reader thread to monitor loading progress
        startStderrReaderThread();

        logger.info("Sentiment analysis process started successfully");
    }

    /**
     * Starts a background thread to read and log stderr messages from the Python process.
     * <p>
     * This thread monitors for the "Model loaded" message to set the modelReady flag.
     * It also logs other messages like "Imports loaded" and any error messages.
     * </p>
     */
    private void startStderrReaderThread() {
        stderrReaderThread = new Thread(() -> {
            try {
                String line;
                while ((line = stderr.readLine()) != null) {
                    logger.info("[Python stderr] {}", line);

                    // Check if model is loaded
                    if (line.contains("Model loaded")) {
                        modelReady = true;
                        logger.info("Sentiment model is ready for analysis");
                    }
                }
            } catch (IOException e) {
                if (process != null && process.isAlive()) {
                    logger.error("Error reading stderr from Python process", e);
                }
            }
        }, "SentimentAnalyzer-StderrReader");

        stderrReaderThread.setDaemon(true);
        stderrReaderThread.start();
    }

    /**
     * Checks if the sentiment model is ready for analysis.
     *
     * @return true if the model has finished loading, false otherwise
     */
    public boolean isModelReady() {
        return modelReady;
    }

    /**
     * Analyzes the sentiment of a comment.
     * <p>
     * This method will busy-wait until the model is ready, then send the comment
     * to the Python process and read the response. The comment is sanitized by
     * replacing newlines with spaces.
     * </p>
     *
     * @param comment the comment text to analyze
     * @return sentiment score: 1 for positive, -1 for negative, 0 for neutral
     * @throws IOException if there's an error communicating with the Python process
     * @throws IllegalArgumentException if the comment equals the sentinel string
     * @throws RuntimeException if the Python process has crashed
     */
    public int analyzeSentiment(String comment) throws IOException {
        if (comment == null || comment.trim().isEmpty()) {
            return 0; // Neutral for empty comments
        }

        // Sanitize input: replace newlines with spaces
        String sanitized = comment.replace('\n', ' ').replace('\r', ' ').trim();

        // Validate not equal to sentinel
        if (sanitized.equals(SENTINEL)) {
            throw new IllegalArgumentException("Comment cannot be equal to sentinel string");
        }

        // Check if process is alive
        if (process == null || !process.isAlive()) {
            throw new RuntimeException("Python sentiment process crashed");
        }

        // Busy-wait until model is ready
        while (!isModelReady()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while waiting for model to load", e);
            }
        }

        // Send comment to Python process
        stdin.write(sanitized);
        stdin.newLine();
        stdin.flush();

        // Read response
        String response = stdout.readLine();
        if (response == null) {
            throw new IOException("Python process returned null response");
        }

        // Convert response to score
        response = response.trim();
        return switch (response) {
            case "POS" -> 1;
            case "NEG" -> -1;
            case "NEU" -> 0;
            default -> {
                logger.warn("Unexpected sentiment response: {}. Treating as neutral.", response);
                yield 0;
            }
        };
    }

    /**
     * Stops the Python sentiment analysis process.
     * <p>
     * Sends the sentinel string to gracefully shut down the Python process,
     * waits for up to 5 seconds, then forcefully terminates if needed.
     * </p>
     */
    public void stop() {
        if (process == null) {
            logger.debug("No process to stop");
            return;
        }

        if (!process.isAlive()) {
            logger.debug("Process is already terminated");
            cleanup();
            return;
        }

        try {
            logger.info("Stopping sentiment analysis process...");

            // Send sentinel string for graceful shutdown
            stdin.write(SENTINEL);
            stdin.newLine();
            stdin.flush();

            // Wait for graceful shutdown
            boolean terminated = process.waitFor(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!terminated) {
                logger.warn("Process did not stop gracefully, forcing termination...");
                process.destroyForcibly();
                process.waitFor(2, TimeUnit.SECONDS);
            }

            logger.info("Sentiment analysis process stopped");

        } catch (IOException e) {
            logger.error("Error sending stop signal to process", e);
            process.destroyForcibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            logger.error("Interrupted while waiting for process to stop", e);
            process.destroyForcibly();
        } finally {
            cleanup();
        }
    }

    /**
     * Cleans up resources.
     */
    private void cleanup() {
        modelReady = false;

        try {
            if (stdin != null) stdin.close();
        } catch (IOException e) {
            logger.debug("Error closing stdin", e);
        }

        try {
            if (stdout != null) stdout.close();
        } catch (IOException e) {
            logger.debug("Error closing stdout", e);
        }

        try {
            if (stderr != null) stderr.close();
        } catch (IOException e) {
            logger.debug("Error closing stderr", e);
        }

        if (stderrReaderThread != null && stderrReaderThread.isAlive()) {
            stderrReaderThread.interrupt();
        }

        stdin = null;
        stdout = null;
        stderr = null;
        process = null;
        stderrReaderThread = null;
    }
}

