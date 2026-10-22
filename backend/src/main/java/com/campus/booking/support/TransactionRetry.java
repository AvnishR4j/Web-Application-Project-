package com.campus.booking.support;

import com.campus.booking.exception.ApiExceptions.ConflictException;
import com.mongodb.MongoException;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs work in a MongoDB transaction and retries when MongoDB reports a write conflict, which is what
 * happens when two requests lock the same document at once. The loser retries and sees the winner's result.
 */
public final class TransactionRetry {

    private TransactionRetry() {
    }

    /**
     * Retries until {@code maxWait} has passed. A time budget (not an attempt count) matters when many requests
     * arrive at once: each has to wait for the ones ahead of it, and each turn is a round trip to the database.
     */
    public static <T> T run(TransactionTemplate tx, Duration maxWait, Supplier<T> work) {
        long deadline = System.nanoTime() + maxWait.toNanos();
        for (int attempt = 1; ; attempt++) {
            try {
                return tx.execute(status -> work.get());
            } catch (RuntimeException e) {
                if (!isTransient(e)) {
                    throw e;
                }
                if (System.nanoTime() > deadline) {
                    throw new ConflictException("This is busy right now. Please try again.");
                }
                backoff(attempt);
            }
        }
    }

    /** MongoDB marks write conflicts and similar retryable transaction failures with a label or code 112. */
    public static boolean isTransient(Throwable error) {
        for (Throwable t = error; t != null; t = t.getCause() == t ? null : t.getCause()) {
            if (t instanceof MongoException me
                    && (me.hasErrorLabel(MongoException.TRANSIENT_TRANSACTION_ERROR_LABEL) || me.getCode() == 112)) {
                return true;
            }
        }
        return false;
    }

    /** Random pause that grows with each attempt (up to 400 ms), so waiting requests spread out instead of colliding. */
    private static void backoff(int attempt) {
        long max = Math.min(400, 10L << Math.min(attempt, 6));
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(5, max + 5));
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ConflictException("Interrupted while waiting to retry");
        }
    }
}
