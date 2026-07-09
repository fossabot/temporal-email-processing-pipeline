package emailpipeline.after;

import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.worker.WorkerFactory;

/**
 * THE WORKER = the process that hosts and runs your workflow + activity code.
 *
 * It long-polls a "task queue" on the Temporal Server for work to do. The
 * Server itself never runs your code — it just durably stores state and hands
 * out tasks. That separation is what makes crash-recovery possible: kill this
 * Worker, restart it, and it picks up in-flight workflows from the Server's
 * Event History.
 *
 * >>> Run this FIRST (leave it running), then run a starter. <<<
 * >>> To demo crash-recovery: stop this Worker mid-run, then start it again. <<<
 */
public class Worker {

    public static final String TASK_QUEUE = "email-pipeline";

    public static void main(String[] args) {
        // Connects to Temporal Server on localhost:7233 (the default).
        WorkflowServiceStubs service = WorkflowServiceStubs.newLocalServiceStubs();
        WorkflowClient client = WorkflowClient.newInstance(service);
        WorkerFactory factory = WorkerFactory.newInstance(client);

        io.temporal.worker.Worker worker = factory.newWorker(TASK_QUEUE);

        // Register workflows (the recipes)...
        worker.registerWorkflowImplementationTypes(
                EmailWorkflowImpl.class,
                DeliveryWorkflowImpl.class);

        // ...and activities (the risky I/O). One instance each is fine here.
        worker.registerActivitiesImplementations(
                new ScanActivitiesImpl(),
                new DeliveryActivitiesImpl());

        System.out.println("Worker started on task queue '" + TASK_QUEUE + "'. Waiting for work...");
        System.out.println("(Stop me mid-run and restart to demo crash-recovery.)");

        factory.start();
    }
}
