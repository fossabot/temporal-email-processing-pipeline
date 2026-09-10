# Temporalizing an email processing pipeline
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fgurmeetgold%2Ftemporal-email-processing-pipeline.svg?type=shield)](https://app.fossa.com/projects/git%2Bgithub.com%2Fgurmeetgold%2Ftemporal-email-processing-pipeline?ref=badge_shield)


A small, runnable before/after that shows what changes when you move a
retry-heavy pipeline onto Temporal. It's based on a real system I worked on --
an email-security gateway -- but you don't need to know anything about email to
follow it. If your service calls other flaky services and you've ever hand-built
a retry queue, this will look familiar.

## The pipeline at a glance

An inbound email runs through a chain of services before it's allowed through:
antivirus, text extraction, URL scanning, and a sandbox that detonates
attachments in a throwaway VM. These services are flaky -- the sandbox is slow
and times out under load -- and you're not allowed to drop a message when that
happens.

## Before vs after (flow)

Without Temporal, a single timeout kicks the whole message out to a queue, and a
background sweeper drags it back in to retry. That machinery is all your code.

```
WITHOUT TEMPORAL  (retry logic = your code)

  AV -> Extract -> URL scan -> Sandbox
                                  |  timeout
                                  v
                       write to DB retry queue
                       (save step, attempts, backoff)
                                  |
                                  v
                       cron SWEEPER re-drives  --> back to Sandbox
                                  |  (repeat until success or dead-letter)
                                  v
                                done
```

With Temporal, the timeout is just a retry the platform handles. No queue, no
sweeper. Independent steps run in parallel; the dependent ones stay ordered.

```
WITH TEMPORAL  (retry logic = config)

            +--> AV scan ------------------+
   start ---|                              +--> Sandbox --> ScanResult
            +--> Extract --> URL scan ------+     ^
                                                  |  timeout? Temporal
                                                  |  auto-retries this step
                                                  |  with backoff (config)

  AV runs in parallel; URL scan waits for Extract (it needs the body text).
  Completed steps are remembered -- a crash resumes at Sandbox, not from scratch.
```

## What each construct maps to

| Temporal construct | In this project            | Think of it as                 |
|--------------------|----------------------------|--------------------------------|
| Worker             | `after/Worker.java`        | the process that runs your code|
| Workflow           | `EmailWorkflowImpl`        | the recipe / orchestration     |
| Activity           | `ScanActivitiesImpl`       | one call to one microservice   |
| Retry policy       | `RetryOptions` in workflow | the queue + sweeper, as config |

## What changes with Temporal

- **Retries become configuration.** A retry policy per step says how to back off
  and when to give up. No retry loop, no queue, no sweeper.
- **Progress is durable.** Each completed step is recorded; a crash resumes where
  it left off instead of re-running finished work.
- **Failures are typed.** A timeout retries; a malware verdict does not -- the
  pipeline stops immediately on a definitive "no".
- **Slow steps heartbeat.** The sandbox reports progress, so a wedged run is
  caught in seconds, not by waiting out a long timeout.
- **You can ask a running job where it is.** A status query replaces the
  "SELECT status FROM messages" you used to run against the queue table.

In this example the retry machinery drops from roughly 48 lines of queue,
sweeper, backoff, and attempt-tracking (plus a DB table and a cron) down to about
10 lines of retry config -- and the Temporal version does more, not less.

## What's in here

- `before/` -- the hand-rolled version, no Temporal. Uses an in-memory map in
  place of the database so there's nothing to install. Read this first.
- `after/` -- the Temporal version: a Worker, the inbound Workflow and its
  Activities, plus a short outbound delivery Workflow that handles greylisting
  with a retry policy alone.

## Running it

You'll need a JDK 17+, Maven, and a local Temporal dev server:

```
temporal server start-dev
```

Server runs on `localhost:7233`, web UI on `localhost:8233`.

**Before:** run `before/BeforePipeline.java`. Watch the sandbox time out, the
message get re-queued, and the sweeper re-drive it until it succeeds. Every
"re-queued" / "SWEEPER" line is code you'd have to maintain.

**After:**

1. Run `after/Worker.java` and leave it running.
2. Run `after/StartInbound.java`. You'll see the scanners run (AV alongside
   extract, URL scan after extract), the sandbox fail twice and get retried
   automatically, then succeed. The starter prints the live status and the result.

Two more to try:

- **Fast-fail on malware:** run `StartInbound` with program argument `malware`.
  Antivirus raises a non-retryable failure; the workflow stops immediately.
- **Crash recovery:** start a clean run, and the moment extract finishes, stop
  the worker, then start it again. The workflow resumes at the sandbox; finished
  steps don't run twice.

**Outbound:** run `after/StartOutbound.java` to see greylisting handled by a
retry policy instead of a delivery queue.

## What the console shows (quick guide)

- Scanner lines in order (AV, Extract, URL scan) = the dependency graph running.
- `SandboxDetonate ... attempt=1/2` stack traces = **not crashes.** That's the
  activity failing, Temporal recording it, and retrying per policy.
- Timestamps on the retries growing apart = exponential backoff, from config.
- `status = SCANNING` then `SANDBOX` = the live query watching state change.

## Reading it in the web UI

Open `localhost:8233`, find `inbound-msg-001`, and click through the Event
History. Every step is a durable event; the sandbox retries show their backoff
intervals. That history is the state that used to live in your database. After
the crash-recovery demo, the same run continues in the same history rather than a
new one starting -- proof it resumed instead of restarting.

## Scope

A teaching example, kept deliberately small. For production you'd also reach for
workflow versioning (to change long-running workflows safely), replay tests,
search attributes (query messages by sender or verdict in the UI), and signals
for human-in-the-loop steps. Happy to talk through any of those.

## Layout

```
before/  BeforePipeline.java, ScanningServices.java   the hand-rolled version
after/   Worker.java                                  hosts everything, run first
         StartInbound.java                            inbound starter (arg "malware" = fast-fail)
         StartOutbound.java                           outbound starter
         EmailWorkflow / ScanActivities               inbound workflow + activities
         DeliveryWorkflow / DeliveryActivities        outbound workflow + activity
EmailMessage.java, ScanResult.java                    shared message + result types
test/    EmailWorkflowTest.java                        clean path, malware fast-fail, delivery backoff
```


## License
[![FOSSA Status](https://app.fossa.com/api/projects/git%2Bgithub.com%2Fgurmeetgold%2Ftemporal-email-processing-pipeline.svg?type=large)](https://app.fossa.com/projects/git%2Bgithub.com%2Fgurmeetgold%2Ftemporal-email-processing-pipeline?ref=badge_large)