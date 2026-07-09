package emailpipeline.after;

import emailpipeline.EmailMessage;
import io.temporal.activity.ActivityOptions;
import io.temporal.common.RetryOptions;
import io.temporal.workflow.Workflow;

import java.time.Duration;

/**
 * THE CLOSER.
 *
 * The old outbound delivery-retry queue + cron sweeper — the thing that
 * retried a message over minutes/hours/days on greylisting or a down server —
 * is now this one activity call plus a retry policy.
 *
 * The retry policy IS the delivery schedule. Temporal's durable timers hold
 * the "wait, then try again" state, and it survives worker restarts. There is
 * no queue table and no sweeper anywhere in this codebase.
 *
 * (Intervals here are short so the demo finishes fast. In production you'd set
 *  initial=1m, backoff=2, maximumInterval=4h, and a maximumAttempts or
 *  workflow timeout that spans days.)
 */
public class DeliveryWorkflowImpl implements DeliveryWorkflow {

    private final ActivityOptions options = ActivityOptions.newBuilder()
            .setStartToCloseTimeout(Duration.ofSeconds(5))
            .setRetryOptions(RetryOptions.newBuilder()
                    .setInitialInterval(Duration.ofSeconds(2)) // prod: minutes
                    .setBackoffCoefficient(2.0)                // grows each retry
                    .setMaximumInterval(Duration.ofSeconds(10))// prod: hours
                    .setMaximumAttempts(10)
                    .build())
            .build();

    private final DeliveryActivities activities =
            Workflow.newActivityStub(DeliveryActivities.class, options);

    @Override
    public void deliverOutbound(EmailMessage msg) {
        Workflow.getLogger(DeliveryWorkflowImpl.class)
                .info(">> delivering {}", msg.getId());
        activities.deliver(msg);
        Workflow.getLogger(DeliveryWorkflowImpl.class)
                .info(">> {} delivered", msg.getId());
    }
}
