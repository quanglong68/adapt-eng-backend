package com.longdq.adaptengbackend.util;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class RetryExecutor {

    private RetryExecutor() {
    }

    public static void executeWithRetry(Runnable task, String taskName, int maxRetries, long retryDelayMs, long successDelayMs) {
        int attempt = 0;
        boolean success = false;

        while (attempt < maxRetries && !success) {
            try {
                attempt++;
                log.info("Processing {} (attempt {}/{})", taskName, attempt, maxRetries);
                task.run();
                success = true;
                Thread.sleep(successDelayMs);
            } catch (Exception e) {
                log.error("Failed to execute {}: {}", taskName, e.getMessage());
                if (attempt < maxRetries) {
                    log.info("Retrying {} in {} ms", taskName, retryDelayMs);
                    sleepQuietly(retryDelayMs);
                } else {
                    log.error("Abandoning {} after {} attempts", taskName, maxRetries);
                }
            }
        }
    }

    private static void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }
}
