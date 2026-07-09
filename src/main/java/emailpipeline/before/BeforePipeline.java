package emailpipeline.before;

import emailpipeline.EmailMessage;

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * ============================================================================
 *  THE "BEFORE" WORLD  —  no Temporal
 * ============================================================================
 *
 * This is the pattern we ran at scale for the inbound email pipeline.
 *
 * The BUSINESS LOGIC we actually care about is tiny:
 *
 *        avScan(msg);  extractText(msg);  sandboxDetonate(msg);
 *
 * That's THREE lines. Everything else in this file exists only to survive a
 * flaky sandbox. When any service timed out, we couldn't just fail the
 * message, so we:
 *
 *   1. Wrote the message back into a "retry queue" (a DB table).
 *   2. Recorded WHICH STEP it had reached, so we wouldn't redo finished work.
 *   3. Ran a background sweeper ("cron") that periodically pulled due
 *      messages out of the queue and re-drove them through the pipeline.
 *
 * Read how much code the reliability concern costs us below. This is the
 * "wall of retry logic" slide.
 */
public class BeforePipeline {

    // --- The "database" retry queue (in-memory for the demo) -----------------
    // Each entry remembers where the message got to, so a re-drive can skip
    // the steps that already succeeded.
    enum Step { AV, EXTRACT, SANDBOX, DONE }

    static class QueuedMessage {
        EmailMessage msg;
        Step nextStep;      // where to resume
        int attempts;       // how many times we've re-driven it
        long nextAttemptAt; // epoch millis — don't retry before this time

        QueuedMessage(EmailMessage msg, Step nextStep) {
            this.msg = msg;
            this.nextStep = nextStep;
            this.attempts = 0;
            this.nextAttemptAt = System.currentTimeMillis();
        }
    }

    // The retry queue table.
    private static final Queue<QueuedMessage> retryQueue = new ConcurrentLinkedQueue<>();
    // Tracks progress per message id (the "status column").
    private static final Map<String, Step> progress = new ConcurrentHashMap<>();

    private static final int MAX_ATTEMPTS = 5;
    private static final long RETRY_BACKOFF_MS = 1000;

    public static void main(String[] args) throws InterruptedException {
        System.out.println("=== BEFORE: hand-rolled retry pipeline ===\n");

        EmailMessage msg = new EmailMessage("msg-001", "alice@example.com", "Q3 report");

        // First attempt.
        drive(new QueuedMessage(msg, Step.AV));

        // --- The sweeper / "cron job" ---------------------------------------
        // In production this was a scheduled job scanning the DB every N minutes
        // for messages whose nextAttemptAt has passed, then re-driving them.
        // We simulate it with a loop until the queue drains.
        while (!retryQueue.isEmpty()) {
            Thread.sleep(200);
            QueuedMessage q = retryQueue.peek();
            if (q == null) continue;

            if (System.currentTimeMillis() < q.nextAttemptAt) {
                continue; // not due yet
            }
            retryQueue.poll();

            if (q.attempts >= MAX_ATTEMPTS) {
                System.out.println(">> GAVE UP on " + q.msg.getId() + " after " + q.attempts + " attempts (dead-letter)\n");
                continue;
            }
            System.out.println(">> SWEEPER re-driving " + q.msg.getId()
                    + " from step " + q.nextStep + " (attempt " + (q.attempts + 1) + ")");
            drive(q);
        }

        System.out.println("=== done ===");
    }

    /**
     * Drives a message through the pipeline STARTING AT its recorded step.
     * On a timeout, it records progress and re-enqueues for the sweeper.
     *
     * Notice how the retry/resume/backoff bookkeeping dwarfs the three lines
     * of real work.
     */
    private static void drive(QueuedMessage q) {
        EmailMessage msg = q.msg;
        Step step = q.nextStep;
        try {
            if (step == Step.AV) {
                ScanningServices.antivirusScan(msg);
                step = Step.EXTRACT;
                progress.put(msg.getId(), step);
            }
            if (step == Step.EXTRACT) {
                ScanningServices.extractText(msg);
                step = Step.SANDBOX;
                progress.put(msg.getId(), step);
            }
            if (step == Step.SANDBOX) {
                ScanningServices.sandboxDetonate(msg);
                step = Step.DONE;
                progress.put(msg.getId(), step);
            }
            System.out.println(">> " + msg.getId() + " fully processed\n");

        } catch (ScanningServices.ServiceTimeoutException e) {
            // A service timed out. Save where we were, back off, re-enqueue.
            q.nextStep = step;                 // resume here next time
            q.attempts += 1;
            q.nextAttemptAt = System.currentTimeMillis() + RETRY_BACKOFF_MS;
            retryQueue.add(q);
            System.out.println("   !! timeout at " + step + " — re-queued, "
                    + "backing off " + RETRY_BACKOFF_MS + "ms\n");
        }
    }
}
