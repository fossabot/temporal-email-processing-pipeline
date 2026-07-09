package emailpipeline.after;

import emailpipeline.EmailMessage;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * ACTIVITIES = the risky calls to the outside world (one per microservice).
 *
 * Two senior points are encoded in these signatures:
 *
 *  1. ERROR TAXONOMY. antivirusScan can fail two very different ways:
 *       - a TIMEOUT (service slow/unavailable)  -> RETRYABLE
 *       - a MALWARE VERDICT (the answer is "no") -> NON-RETRYABLE
 *     Retrying a malware verdict five times is pointless and dangerous. The
 *     activity signals "do not retry" with an Applicationfailure that carries a
 *     stable error type ("MalwareDetected") the workflow can branch on. This is
 *     the difference between "retries good" and understanding FAILURE MODES.
 *
 *  2. MEANINGFUL RETURNS. Activities return data (extracted text summary, url
 *     count, sandbox verdict) so the workflow can assemble a real ScanResult,
 *     rather than everything being void side effects.
 */
@ActivityInterface
public interface ScanActivities {

    /** @return "clean"; throws NON-RETRYABLE "MalwareDetected" if a signature hits. */
    @ActivityMethod
    String antivirusScan(EmailMessage msg);

    /** @return short summary of extracted body text. */
    @ActivityMethod
    String extractText(EmailMessage msg);

    /** @return number of URLs checked. */
    @ActivityMethod
    int scanUrls(EmailMessage msg);

    /**
     * Long-running sandbox detonation. HEARTBEATS while it works so a stuck
     * sandbox is detected in seconds via heartbeatTimeout, not by waiting out
     * the whole startToClose window. Flaky (retryable) in the demo.
     * @return sandbox verdict, e.g. "safe".
     */
    @ActivityMethod
    String sandboxDetonate(EmailMessage msg);
}
