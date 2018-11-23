package com.datapps.zebra.workflow.execapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.datapps.zebra.workflow.event.Event;
import com.datapps.zebra.workflow.executor.*;
import org.junit.Assert;

import java.util.function.Function;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

public class FlowRunnerTestBase {

  protected FlowRunner runner;
  protected EventCollectorListener eventCollector;

  public static boolean isStarted(final Status status) {
    if (status == Status.QUEUED) {
      return false;
    }
    return Status.isStatusFinished(status) || Status.isStatusRunning(status);
  }

  public void assertThreadShutDown() {
    waitFlowRunner(
        runner -> Status.isStatusFinished(runner.getExecutableFlow().getStatus())
        && !runner.isRunnerThreadAlive());
  }

  public void assertThreadRunning() {
    waitFlowRunner(
        runner -> Status.isStatusRunning(runner.getExecutableFlow().getStatus())
        && runner.isRunnerThreadAlive());
  }

  public void waitFlowRunner(final Function<FlowRunner, Boolean> statusCheck) {
    for (int i = 0; i < 1000; i++) {
      if (statusCheck.apply(this.runner)) {
        return;
      }
      synchronized (EventCollectorListener.handleEvent) {
        try {
          EventCollectorListener.handleEvent.wait(10L);
        } catch (final InterruptedException e) {
        }
      }
    }
    Assert.fail("Flow didn't reach expected status");
  }

  public void waitJobStatuses(final Function<Status, Boolean> statusCheck,
      final String... jobs) {
    for (int i = 0; i < 1000; i++) {
      if (checkJobStatuses(statusCheck, jobs)) {
        return;
      }
      synchronized (EventCollectorListener.handleEvent) {
        try {
          EventCollectorListener.handleEvent.wait(10L);
        } catch (final InterruptedException e) {
        }
      }
    }
    Assert.fail("Jobs didn't reach expected statuses");
  }

  public void waitJobsStarted(final FlowRunner runner, final String... jobs) {
    waitJobStatuses(FlowRunnerTest::isStarted, jobs);
  }

  protected void waitEventFired(final String nestedId, final Status status)
      throws InterruptedException {
    for (int i = 0; i < 1000; i++) {
      for (final Event event : this.eventCollector.getEventList()) {
        if (event.getData().getStatus() == status && event.getData().getNestedId()
            .equals(nestedId)) {
          return;
        }
      }
      synchronized (EventCollectorListener.handleEvent) {
        EventCollectorListener.handleEvent.wait(10L);
      }
    }
    fail("Event wasn't fired with [" + nestedId + "], " + status);
  }

  public boolean checkJobStatuses(final Function<Status, Boolean> statusCheck,
      final String[] jobs) {
    final ExecutableFlow exFlow = this.runner.getExecutableFlow();
    for (final String name : jobs) {
      final ExecutableNode node = exFlow.getExecutableNodePath(name);
      assertNotNull(name + " wasn't found", node);
      if (!statusCheck.apply(node.getStatus())) {
        return false;
      }
    }
    return true;
  }

  protected void assertFlowStatus(final Status status) {
    final ExecutableFlow flow = this.runner.getExecutableFlow();
    waitForStatus(flow, status);
    printStatuses(status, flow);
    assertEquals(status, flow.getStatus());
  }

  protected void assertStatus(final String name, final Status status) {
    final ExecutableFlow exFlow = this.runner.getExecutableFlow();
    final ExecutableNode node = exFlow.getExecutableNodePath(name);
    assertNotNull(name + " wasn't found", node);
    waitForStatus(node, status);
    printStatuses(status, node);
    assertEquals("Wrong status for [" + name + "]", status, node.getStatus());
  }

  private void waitForStatus(ExecutableNode node, Status status) {
    for (int i = 0; i < 1000; i++) {
      if (node.getStatus() == status) {
        break;
      }
      synchronized (EventCollectorListener.handleEvent) {
        try {
          EventCollectorListener.handleEvent.wait(10L);
        } catch (final InterruptedException e) {
          i--;
        }
      }
    }
  }

  protected void printStatuses(final Status status, final ExecutableNode node) {
    if (status != node.getStatus()) {
      printTestJobs();
      printFlowJobs(this.runner.getExecutableFlow());
    }
  }

  private void printTestJobs() {
    for (final String testJob : InteractiveTestJob.getTestJobNames()) {
      final ExecutableNode testNode = this.runner.getExecutableFlow()
          .getExecutableNodePath(testJob);
      System.err.println("testJob: " + testNode.getNestedId() + " " + testNode.getStatus());
    }
  }

  private void printFlowJobs(final ExecutableFlowBase flow) {
    System.err.println("ExecutableFlow: " + flow.getNestedId() + " " + flow.getStatus());
    for (final ExecutableNode node : flow.getExecutableNodes()) {
      if (node instanceof ExecutableFlowBase) {
        printFlowJobs((ExecutableFlowBase) node);
      } else {
        System.err.println("ExecutableNode: " + node.getNestedId() + " " + node.getStatus());
      }
    }
  }

  protected void succeedJobs(final String... jobs) {
    waitJobsStarted(this.runner, jobs);
    for (final String name : jobs) {
      InteractiveTestJob.getTestJob(name).succeedJob();
    }
  }

}
