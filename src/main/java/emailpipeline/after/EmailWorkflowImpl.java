package emailpipeline.after;

import emailpipeline.EmailMessage;
import emailpipeline.ScanResult;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Async;
import io.temporal.workflow.Promise;
import io.temporal.workflow.Workflow;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * ============================================================================
 *  THE "AFTER" WORLD  —  with Temporal (staff-level pass)
 * ============================================================================
 *
 * Compared to BeforePipeline.java, the reliability code is gone AND the
 * orchestration is now genuinely non-trivial:
 *
 *   Stage 1 (FAN-OUT): AV, text extraction, and URL scanning are independent,
 *   so we run them CONCURRENTLY with Async + Promise, instead of a pointless
 *   sequential chain. They join before the message can proceed.
 *
 *   Stage 2 (GATE): sandbox detonation runs only after stage 1 succeeds.
 *
 * Two RetryOptions profiles express intent precisely:
 *   - quick services: short timeout, fast retries.
 *   - sandbox: long timeout + HEARTBEAT timeout, because detonation is slow and
 *     we want a stuck run detected in seconds, not minutes.
 *
 * A malware verdict from AV arrives as a NON-RETRYABLE failure and aborts the
 * whole workflow immediately — no retries, no sandbox, fast fail.
 */
public class EmailWorkflowImpl implements EmailWorkflow {

    // Quick scanners: fail fast, retry fast.
    private final ActivityOptions quickOptions = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(5))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofMillis(500))
                    .setBackoffCoefficient(2.0)
                    .setMaximumAttempts(4)
                    // Belt-and-suspenders: even if AV threw a plain error,
                    // this type is never retried.
                    .setDoNotRetry("MalwareDetected")
                    .build())
            .build();

    // Sandbox: long-running, so use scheduleToClose to bound the TOTAL effort
    // across retries, plus a heartbeat timeout to catch a wedged detonation.
    private final ActivityOptions sandboxOptions = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(30))
            .setScheduleToCloseTimeout(Duration.ofMinutes(5))
            .setHeartbeatTimeout(Duration.ofSeconds(5))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(1))
                    .setBackoffCoefficient(2.0)
                    .setMaximumInterval(Duration.ofSeconds(10))
                    .setMaximumAttempts(5)
                    .build())
            .build();

    private final ScanActivities quick =
            Workflow.newActivityStub(ScanActivities.class, quickOptions);
    private final ScanActivities sandbox =
            Workflow.newActivityStub(ScanActivities.class, sandboxOptions);

    // Live state exposed via the query.
    private volatile String status = "STARTING";

    @Override
    public ScanResult processInbound(EmailMessage msg) {
        Workflow.getLogger(EmailWorkflowImpl.class).info(">> workflow started for {}", msg.getId());

        // --- Stage 1: AV runs in parallel with the extract->URL chain --------
        // AV works on raw bytes, so it's independent. But URL scanning needs the
        // URLs pulled out of the body, so extract MUST finish before URL scan.
        // That's a real data dependency: we fan out AV alongside the chain, and
        // keep extract->URL sequential inside it.
        status = "SCANNING";

        Promise<String> av = Async.function(quick::antivirusScan, msg);

        // extract -> URL scan, as a dependent chain (thenApply keeps it ordered).
        Promise<String> text = Async.function(quick::extractText, msg);
        Promise<Integer> urls = text.thenApply(extracted -> quick.scanUrls(msg));

        // Join. If AV produced the non-retryable MalwareDetected failure, this
        // is where it surfaces — and it aborts the workflow before the sandbox.
        Promise.allOf(av, urls).get();

        // --- Stage 2: gated long-running sandbox -----------------------------
        status = "SANDBOX";
        String sandboxVerdict = sandbox.sandboxDetonate(msg);

        status = "DONE";
        List<String> notes = new ArrayList<>();
        notes.add("av=" + av.get());
        ScanResult result = new ScanResult(
                ScanResult.Verdict.CLEAN,
                text.get(),
                urls.get(),
                sandboxVerdict,
                notes);

        Workflow.getLogger(EmailWorkflowImpl.class).info(">> {} -> {}", msg.getId(), result);
        return result;
    }

    @Override
    public String getStatus() {
        return status;
    }
}
