package emailpipeline.after;

import emailpipeline.EmailMessage;
import io.temporal.activity.Activity;
import io.temporal.activity.ActivityExecutionContext;
import io.temporal.failure.ApplicationFailure;

/**
 * Simulated microservices. Same flaky behavior as the "before" demo so the
 * comparison is fair, plus two senior additions:
 *
 *   - antivirusScan raises a NON-RETRYABLE failure on a malware "verdict".
 *     Demo trigger: put the word "malware" in the subject (e.g. an EICAR test).
 *   - sandboxDetonate HEARTBEATS during its long run.
 */
public class ScanActivitiesImpl implements ScanActivities {

    private static final java.util.Map<String, Integer> sandboxAttempts =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public String antivirusScan(EmailMessage msg) {
        sleep(300);
        // A malware verdict is a definitive NO. Retrying it is pointless and
        // unsafe, so we throw a non-retryable failure with a stable type the
        // workflow can recognize.
        if (msg.getSubject() != null && msg.getSubject().toLowerCase().contains("malware")) {
            System.out.println("   [AV]       MALWARE signature hit  (" + msg.getId() + ")");
            throw ApplicationFailure.newNonRetryableFailure(
                    "Malware signature detected in " + msg.getId(),
                    "MalwareDetected");
        }
        System.out.println("   [AV]       clean            (" + msg.getId() + ")");
        return "clean";
    }

    @Override
    public String extractText(EmailMessage msg) {
        sleep(300);
        System.out.println("   [Extract]  text extracted   (" + msg.getId() + ")");
        return "1423 chars extracted";
    }

    @Override
    public int scanUrls(EmailMessage msg) {
        sleep(250);
        System.out.println("   [URLscan]  3 urls checked   (" + msg.getId() + ")");
        return 3;
    }

    @Override
    public String sandboxDetonate(EmailMessage msg) {
        ActivityExecutionContext ctx = Activity.getExecutionContext();
        int attempt = sandboxAttempts.merge(msg.getId(), 1, Integer::sum);

        // Detonation is slow: do work in slices and HEARTBEAT between them.
        // If this process died mid-detonation, Temporal notices via the missed
        // heartbeat (heartbeatTimeout) and reschedules quickly.
        for (int i = 1; i <= 4; i++) {
            sleep(150);
            ctx.heartbeat("detonation progress " + (i * 25) + "%");
        }

        if (attempt < 3) {
            System.out.println("   [Sandbox]  TIMEOUT  (attempt " + attempt + ")  (" + msg.getId() + ")");
            // A transient timeout IS retryable — a plain exception retries.
            throw new RuntimeException("sandbox timed out on attempt " + attempt);
        }
        System.out.println("   [Sandbox]  verdict: safe    (attempt " + attempt + ")  (" + msg.getId() + ")");
        return "safe";
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
