package emailpipeline.after;

import emailpipeline.EmailMessage;

/**
 * Simulated remote mail server that greylists the first two delivery attempts
 * ("try again later"), then accepts on the third.
 */
public class DeliveryActivitiesImpl implements DeliveryActivities {

    private static final java.util.Map<String, Integer> attempts =
            new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void deliver(EmailMessage msg) {
        int attempt = attempts.merge(msg.getId(), 1, Integer::sum);
        if (attempt < 3) {
            System.out.println("   [Deliver]  GREYLISTED — try later  (attempt " + attempt + ")");
            throw new RuntimeException("greylisted, attempt " + attempt);
        }
        System.out.println("   [Deliver]  accepted by remote server (attempt " + attempt + ")");
    }
}
