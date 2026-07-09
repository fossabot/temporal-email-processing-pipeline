package emailpipeline.after;

import emailpipeline.EmailMessage;
import emailpipeline.ScanResult;
import io.temporal.workflow.QueryMethod;
import io.temporal.workflow.WorkflowInterface;
import io.temporal.workflow.WorkflowMethod;

/**
 * WORKFLOW = the orchestration ("the recipe").
 *
 * Beyond the main method it exposes a QUERY. A query lets an operator ask a
 * RUNNING workflow "where is this message right now?" without touching a
 * database — the answer comes straight from live workflow state. This is the
 * durable, built-in replacement for the "SELECT status FROM messages" you used
 * to run against the retry-queue table.
 */
@WorkflowInterface
public interface EmailWorkflow {

    @WorkflowMethod
    ScanResult processInbound(EmailMessage msg);

    /** Live status of the in-flight message (e.g. "SCANNING", "SANDBOX", "DONE"). */
    @QueryMethod
    String getStatus();
}
