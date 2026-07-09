package emailpipeline.before;

import emailpipeline.EmailMessage;

/**
 * Stand-ins for the real microservices an email hits on the way in:
 * AV scanner, text extractor, sandbox detonation.
 *
 * Each one can "time out" (throw) to simulate a slow/unavailable service.
 * The SANDBOX is our troublemaker: it fails the first two times it's called
 * for a given message, then succeeds — exactly the flaky-dependency behavior
 * that made the old retry code necessary.
 */
public class ScanningServices {

    // Remembers how many times the sandbox has been attempted per message id.
    // (In the real world this lived in a DB column so retries could tell how
    // many attempts had happened. Here it's a static map — same idea, simpler.)
    private static final java.util.Map<String, Integer> sandboxAttempts =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** AV scan — reliable in this demo. */
    public static void antivirusScan(EmailMessage msg) {
        sleep(300);
        System.out.println("   [AV]       clean            (" + msg.getId() + ")");
    }

    /** Text extraction — reliable in this demo. */
    public static void extractText(EmailMessage msg) {
        sleep(300);
        System.out.println("   [Extract]  text extracted   (" + msg.getId() + ")");
    }

    /**
     * Sandbox detonation — SLOW and FLAKY.
     * Throws a timeout on attempts 1 and 2, succeeds on attempt 3.
     */
    public static void sandboxDetonate(EmailMessage msg) {
        int attempt = sandboxAttempts.merge(msg.getId(), 1, Integer::sum);
        sleep(400);
        if (attempt < 3) {
            System.out.println("   [Sandbox]  TIMEOUT  (attempt " + attempt + ")  (" + msg.getId() + ")");
            throw new ServiceTimeoutException("sandbox timed out on attempt " + attempt);
        }
        System.out.println("   [Sandbox]  verdict: safe    (attempt " + attempt + ")  (" + msg.getId() + ")");
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Thrown when a downstream service is too slow / unavailable. */
    public static class ServiceTimeoutException extends RuntimeException {
        public ServiceTimeoutException(String message) {
            super(message);
        }
    }
}
