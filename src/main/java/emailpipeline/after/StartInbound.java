package emailpipeline.after;

import emailpipeline.EmailMessage;
import emailpipeline.ScanResult;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowOptions;
import io.temporal.client.WorkflowStub;
import io.temporal.serviceclient.WorkflowServiceStubs;

/**
 * SCENARIO A starter.
 *
 * Default run  : clean message -> fan-out scan -> flaky sandbox retries -> CLEAN.
 * Malware demo : pass "malware" as an arg (or any subject containing it) to see
 *                the NON-RETRYABLE fast-fail path abort the workflow immediately.
 *
 *   Run:  StartInbound                 (clean)
 *         StartInbound malware         (fast-fail)
 *
 * Also demonstrates a live QUERY against the running workflow.
 */
public class StartInbound {

    public static void main(String[] args) throws Exception {
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient client = WorkflowClient.newInstance(service);

        String subject = (args.length > 0) ? "Contains " + args[0] : "Q3 report";

        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setTaskQueue(Worker.TASK_QUEUE)
                .setWorkflowId("inbound-msg-001")
                .build();

        EmailWorkflow workflow = client.newWorkflowStub(EmailWorkflow.class, options);
        EmailMessage msg = new EmailMessage("msg-001", "alice@example.com", subject);

        System.out.println("Starting inbound workflow (subject=\"" + subject + "\")...");

        // Start async so we can query the running workflow mid-flight.
        WorkflowClient.start(workflow::processInbound, msg);
        WorkflowStub stub = WorkflowStub.fromTyped(workflow);

        // Poll the live status a couple of times via a QUERY (no DB needed).
        for (int i = 0; i < 3; i++) {
            Thread.sleep(500);
            try {
                System.out.println("   [query] status = " + stub.query("getStatus", String.class));
            } catch (Exception ignore) { /* workflow may already be finishing */ }
        }

        try {
            ScanResult result = stub.getResult(ScanResult.class);
            System.out.println("RESULT: " + result);
        } catch (Exception e) {
            // Malware path: the non-retryable failure surfaces here.
            System.out.println("WORKFLOW FAILED (as expected for malware): " + e.getMessage());
        }
        System.out.println("Inspect the run at http://localhost:8233");
        System.exit(0);
    }
}
