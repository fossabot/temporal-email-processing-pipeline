package emailpipeline.after;

import emailpipeline.EmailMessage;
import emailpipeline.ScanResult;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowFailedException;
import io.temporal.client.WorkflowOptions;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Staff-level tests exercise more than the happy path. All run against the
 * in-memory TestWorkflowEnvironment (no server) and its virtual clock auto-
 * skips retry backoff, so even the flaky sandbox resolves in milliseconds.
 */
public class EmailWorkflowTest {

    private TestWorkflowEnvironment env;
    private Worker worker;
    private WorkflowClient client;

    @BeforeEach
    void setUp() {
        env = TestWorkflowEnvironment.newInstance();
        worker = env.newWorker("test-queue");
        worker.registerWorkflowImplementationTypes(
                EmailWorkflowImpl.class, DeliveryWorkflowImpl.class);
        worker.registerActivitiesImplementations(
                new ScanActivitiesImpl(), new DeliveryActivitiesImpl());
        client = env.getWorkflowClient();
        env.start();
    }

    @AfterEach
    void tearDown() {
        env.close();
    }

    private EmailWorkflow inboundStub() {
        return client.newWorkflowStub(EmailWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue("test-queue").build());
    }

    @Test
    void cleanMessageSurvivesFlakySandboxAndReturnsClean() {
        ScanResult result = inboundStub()
                .processInbound(new EmailMessage("t-clean", "bob@example.com", "hello"));
        assertEquals(ScanResult.Verdict.CLEAN, result.getVerdict());
        assertEquals("safe", result.getSandboxVerdict());
        assertEquals(3, result.getUrlsScanned());
    }

    @Test
    void malwareVerdictFailsFastAndIsNotRetried() {
        // A subject containing "malware" makes AV throw a non-retryable failure.
        // The workflow must FAIL rather than retry into the sandbox.
        WorkflowFailedException ex = assertThrows(WorkflowFailedException.class, () ->
                inboundStub().processInbound(
                        new EmailMessage("t-mal", "evil@example.com", "malware sample")));
        // The root cause carries our stable error type.
        assertTrue(ex.getCause().getMessage().contains("Malware")
                || ex.getCause().toString().contains("MalwareDetected"));
    }

    @Test
    void outboundDeliveryEventuallySucceedsThroughGreylisting() {
        // Greylisted twice then accepted; virtual clock skips the backoff waits.
        DeliveryWorkflow d = client.newWorkflowStub(DeliveryWorkflow.class,
                WorkflowOptions.newBuilder().setTaskQueue("test-queue").build());
        assertDoesNotThrow(() ->
                d.deliverOutbound(new EmailMessage("t-deliver", "x@example.com", "hi")));
    }
}
