package emailpipeline.after;

import emailpipeline.EmailMessage;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

@WorkflowInterface
public interface DeliveryWorkflow {

    @WorkflowMethod
    void deliverOutbound(EmailMessage msg);
}
