package emailpipeline.after;

import emailpipeline.EmailMessage;
import io.temporal.activity.ActivityInterface;
import io.temporal.activity.ActivityMethod;

/**
 * SCENARIO B — outbound delivery (the closer).
 *
 * Delivering to a receiving mail server can fail with "greylisting"
 * (a polite "come back later") or a temporarily-down server. The old system
 * pushed the message onto a DB delivery queue with a "next attempt at" time
 * and a cron swept it for hours or days.
 *
 * With Temporal that entire queue + sweeper collapses into ONE activity plus
 * a retry policy — see DeliveryWorkflowImpl.
 */
@ActivityInterface
public interface DeliveryActivities {

    @ActivityMethod
    void deliver(EmailMessage msg);
}
