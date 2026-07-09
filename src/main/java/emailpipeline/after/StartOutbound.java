package emailpipeline.after;

import emailpipeline.EmailMessage;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;

/**
 * SCENARIO B starter — the closer. Kicks off one outbound delivery workflow
 * that gets greylisted twice and then succeeds, all handled by the retry
 * policy (no queue, no cron).
 */
public class StartOutbound {

    public static void main(String[] args) {
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient client = WorkflowClient.newInstance(service);

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(Worker.TASK_QUEUE)
                .setWorkflowId("outbound-msg-001")
                .build();

        DeliveryWorkflow workflow = client.newWorkflowStub(DeliveryWorkflow.class, options);

        EmailMessage msg = new EmailMessage("msg-001", "alice@example.com", "Q3 report");

        System.out.println("Starting outbound delivery for " + msg.getId() + "...");
        workflow.deliverOutbound(msg);
        System.out.println("Delivered. Check the retry timeline at http://localhost:8233.");
    }
}
