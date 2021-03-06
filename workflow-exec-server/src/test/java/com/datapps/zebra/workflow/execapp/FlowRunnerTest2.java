/*
 * Copyright 2017 LinkedIn Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.datapps.zebra.workflow.execapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import com.datapps.zebra.workflow.execapp.jmx.JmxJobMBeanManager;
import com.datapps.zebra.workflow.executor.*;
import com.datapps.zebra.workflow.executor.ExecutionOptions.FailureAction;
import com.datapps.zebra.workflow.flow.Flow;
import com.datapps.zebra.workflow.jobExecutor.AllJobExecutorTests;
import com.datapps.zebra.workflow.jobtype.JobTypeManager;
import com.datapps.zebra.workflow.jobtype.JobTypePluginSet;
import com.datapps.zebra.workflow.project.MockProjectLoader;
import com.datapps.zebra.workflow.project.Project;
import com.datapps.zebra.workflow.project.ProjectLoader;
import com.datapps.zebra.workflow.test.Utils;
import com.datapps.zebra.workflow.utils.Props;
import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Test the flow run, especially with embedded flows.
 *
 * This test uses executions/embedded2. It also mainly uses the flow named jobf. The test is
 * designed to control success/failures explicitly so we don't have to time the flow exactly.
 *
 * Flow jobf looks like the following:
 *
 * <pre>
 *       joba       joba1
 *      /  |  \      |
 *     /   |   \     |
 *  jobb  jobd jobc  |
 *     \   |   /    /
 *      \  |  /    /
 *        jobe    /
 *         |     /
 *         |    /
 *        jobf
 * </pre>
 *
 * The job 'jobb' is an embedded flow:
 *
 * jobb:innerFlow
 *
 * <pre>
 *        innerJobA
 *        /       \
 *   innerJobB   innerJobC
 *        \       /
 *        innerFlow
 *
 * </pre>
 *
 * The job 'jobd' is a simple embedded flow:
 *
 * jobd:innerFlow2
 *
 * <pre>
 *       innerJobA
 *           |
 *       innerFlow2
 * </pre>
 *
 * The following tests checks each stage of the flow run by forcing jobs to succeed or fail.
 */
public class FlowRunnerTest2 extends FlowRunnerTestBase {

  private static final File TEST_DIR = new File(
      "../test/src/test/resources/azkaban/test/executions/embedded2");
  private static int id = 101;
  private final Logger logger = Logger.getLogger(FlowRunnerTest2.class);
  private File workingDir;
  private JobTypeManager jobtypeManager;
  private ProjectLoader fakeProjectLoader;
  private ExecutorLoader fakeExecutorLoader;
  private Project project;
  private Map<String, Flow> flowMap;

  @Before
  public void setUp() throws Exception {
    System.out.println("Create temp dir");
    this.workingDir = new File("build/tmp/_AzkabanTestDir_" + System.currentTimeMillis());
    if (this.workingDir.exists()) {
      FileUtils.deleteDirectory(this.workingDir);
    }
    FileUtils.forceMkdir(this.workingDir);
    this.jobtypeManager = new JobTypeManager(null, null,
        this.getClass().getClassLoader());
    final JobTypePluginSet pluginSet = this.jobtypeManager.getJobTypePluginSet();

    pluginSet.setCommonPluginLoadProps(AllJobExecutorTests.setUpCommonProps());
    pluginSet.addPluginClass("java", JavaJob.class);
    pluginSet.addPluginClass("test", InteractiveTestJob.class);
    this.fakeProjectLoader = new MockProjectLoader(this.workingDir);
    this.fakeExecutorLoader = new MockExecutorLoader();
    this.project = new Project(1, "testProject");
    Utils.initServiceProvider();
    JmxJobMBeanManager.getInstance().initialize(new Props());

    this.flowMap = FlowRunnerTestUtil
        .prepareProject(this.project, TEST_DIR, this.logger, this.workingDir);

    InteractiveTestJob.clearTestJobs();
  }

  @After
  public void tearDown() throws IOException {
    System.out.println("Teardown temp dir");
    if (this.workingDir != null) {
      FileUtils.deleteDirectory(this.workingDir);
      this.workingDir = null;
    }
  }

  /**
   * Tests the basic successful flow run, and also tests all output variables from each job.
   */
  @Test
  public void testBasicRun() throws Exception {
    final EventCollectorListener eventCollector = new EventCollectorListener();
    this.runner = createFlowRunner(eventCollector);

    // 1. START FLOW
    runFlowRunnerInThread(this.runner);

    // After it starts up, only joba should be running
    assertStatus("joba", Status.RUNNING);
    assertStatus("joba1", Status.RUNNING);

    final Props joba = this.runner.getExecutableFlow().getExecutableNodePath("joba")
        .getInputProps();
    assertEquals("joba.1", joba.get("param1"));
    assertEquals("test1.2", joba.get("param2"));
    assertEquals("test1.3", joba.get("param3"));
    assertEquals("override.4", joba.get("param4"));
    assertEquals("test2.5", joba.get("param5"));
    assertEquals("test2.6", joba.get("param6"));
    assertEquals("test2.7", joba.get("param7"));
    assertEquals("test2.8", joba.get("param8"));

    final Props joba1 = this.runner.getExecutableFlow().getExecutableNodePath("joba1")
        .getInputProps();
    assertEquals("test1.1", joba1.get("param1"));
    assertEquals("test1.2", joba1.get("param2"));
    assertEquals("test1.3", joba1.get("param3"));
    assertEquals("override.4", joba1.get("param4"));
    assertEquals("test2.5", joba1.get("param5"));
    assertEquals("test2.6", joba1.get("param6"));
    assertEquals("test2.7", joba1.get("param7"));
    assertEquals("test2.8", joba1.get("param8"));

    // 2. JOB A COMPLETES SUCCESSFULLY
    InteractiveTestJob.getTestJob("joba").succeedJob(
        Props.of("output.joba", "joba", "output.override", "joba"));
    assertStatus("joba", Status.SUCCEEDED);
    assertStatus("joba1", Status.RUNNING);
    assertStatus("jobb", Status.RUNNING);
    assertStatus("jobc", Status.RUNNING);
    assertStatus("jobd", Status.RUNNING);
    assertStatus("jobd:innerJobA", Status.RUNNING);
    assertStatus("jobb:innerJobA", Status.RUNNING);

    final ExecutableNode node = this.runner.getExecutableFlow().getExecutableNodePath("jobb");
    assertEquals(Status.RUNNING, node.getStatus());
    final Props jobb = node.getInputProps();
    assertEquals("override.4", jobb.get("param4"));
    // Test that jobb properties overwrites the output properties
    assertEquals("moo", jobb.get("testprops"));
    assertEquals("jobb", jobb.get("output.override"));
    assertEquals("joba", jobb.get("output.joba"));

    final Props jobbInnerJobA = this.runner.getExecutableFlow()
        .getExecutableNodePath("jobb:innerJobA")
        .getInputProps();
    assertEquals("test1.1", jobbInnerJobA.get("param1"));
    assertEquals("test1.2", jobbInnerJobA.get("param2"));
    assertEquals("test1.3", jobbInnerJobA.get("param3"));
    assertEquals("override.4", jobbInnerJobA.get("param4"));
    assertEquals("test2.5", jobbInnerJobA.get("param5"));
    assertEquals("test2.6", jobbInnerJobA.get("param6"));
    assertEquals("test2.7", jobbInnerJobA.get("param7"));
    assertEquals("test2.8", jobbInnerJobA.get("param8"));
    assertEquals("joba", jobbInnerJobA.get("output.joba"));

    // 3. jobb:Inner completes
    /// innerJobA completes
    InteractiveTestJob.getTestJob("jobb:innerJobA").succeedJob(
        Props.of("output.jobb.innerJobA", "jobb.innerJobA"));
    assertStatus("jobb:innerJobA", Status.SUCCEEDED);
    assertStatus("jobb:innerJobB", Status.RUNNING);
    assertStatus("jobb:innerJobC", Status.RUNNING);
    final Props jobbInnerJobB = this.runner.getExecutableFlow()
        .getExecutableNodePath("jobb:innerJobB")
        .getInputProps();
    assertEquals("test1.1", jobbInnerJobB.get("param1"));
    assertEquals("override.4", jobbInnerJobB.get("param4"));
    assertEquals("jobb.innerJobA",
        jobbInnerJobB.get("output.jobb.innerJobA"));
    assertEquals("moo", jobbInnerJobB.get("testprops"));
    /// innerJobB, C completes
    InteractiveTestJob.getTestJob("jobb:innerJobB").succeedJob(
        Props.of("output.jobb.innerJobB", "jobb.innerJobB"));
    InteractiveTestJob.getTestJob("jobb:innerJobC").succeedJob(
        Props.of("output.jobb.innerJobC", "jobb.innerJobC"));
    assertStatus("jobb:innerJobB", Status.SUCCEEDED);
    assertStatus("jobb:innerJobC", Status.SUCCEEDED);
    assertStatus("jobb:innerFlow", Status.RUNNING);

    final Props jobbInnerJobD = this.runner.getExecutableFlow()
        .getExecutableNodePath("jobb:innerFlow")
        .getInputProps();
    assertEquals("test1.1", jobbInnerJobD.get("param1"));
    assertEquals("override.4", jobbInnerJobD.get("param4"));
    assertEquals("jobb.innerJobB",
        jobbInnerJobD.get("output.jobb.innerJobB"));
    assertEquals("jobb.innerJobC",
        jobbInnerJobD.get("output.jobb.innerJobC"));

    // 4. Finish up on inner flow for jobb
    InteractiveTestJob.getTestJob("jobb:innerFlow").succeedJob(
        Props.of("output1.jobb", "test1", "output2.jobb", "test2"));
    assertStatus("jobb:innerFlow", Status.SUCCEEDED);
    assertStatus("jobb", Status.SUCCEEDED);
    final Props jobbOutput = this.runner.getExecutableFlow().getExecutableNodePath("jobb")
        .getOutputProps();
    assertEquals("test1", jobbOutput.get("output1.jobb"));
    assertEquals("test2", jobbOutput.get("output2.jobb"));

    // 5. Finish jobc, jobd
    InteractiveTestJob.getTestJob("jobc").succeedJob(
        Props.of("output.jobc", "jobc"));
    assertStatus("jobc", Status.SUCCEEDED);
    InteractiveTestJob.getTestJob("jobd:innerJobA").succeedJob();
    InteractiveTestJob.getTestJob("jobd:innerFlow2").succeedJob();
    assertStatus("jobd:innerJobA", Status.SUCCEEDED);
    assertStatus("jobd:innerFlow2", Status.SUCCEEDED);
    assertStatus("jobd", Status.SUCCEEDED);
    assertStatus("jobe", Status.RUNNING);

    final Props jobd = this.runner.getExecutableFlow().getExecutableNodePath("jobe")
        .getInputProps();
    assertEquals("test1", jobd.get("output1.jobb"));
    assertEquals("jobc", jobd.get("output.jobc"));

    // 6. Finish off flow
    InteractiveTestJob.getTestJob("joba1").succeedJob();
    InteractiveTestJob.getTestJob("jobe").succeedJob();
    assertStatus("joba1", Status.SUCCEEDED);
    assertStatus("jobe", Status.SUCCEEDED);
    assertStatus("jobf", Status.RUNNING);

    InteractiveTestJob.getTestJob("jobf").succeedJob();
    assertStatus("jobf", Status.SUCCEEDED);
    assertFlowStatus(Status.SUCCEEDED);
  }

  /**
   * Tests a flow with Disabled jobs and flows. They should properly SKIP executions
   */
  @Test
  public void testDisabledNormal() throws Exception {
    final EventCollectorListener eventCollector = new EventCollectorListener();
    this.runner = createFlowRunner(eventCollector);
    final ExecutableFlow flow = this.runner.getExecutableFlow();
    flow.getExecutableNode("jobb").setStatus(Status.DISABLED);
    ((ExecutableFlowBase) flow.getExecutableNode("jobd")).getExecutableNode(
        "innerJobA").setStatus(Status.DISABLED);

    // 1. START FLOW
    runFlowRunnerInThread(this.runner);

    // After it starts up, only joba should be running
    assertStatus("joba", Status.RUNNING);
    assertStatus("joba1", Status.RUNNING);

    // 2. JOB A COMPLETES SUCCESSFULLY, others should be skipped
    InteractiveTestJob.getTestJob("joba").succeedJob();
    assertStatus("joba", Status.SUCCEEDED);
    assertStatus("joba1", Status.RUNNING);
    assertStatus("jobb", Status.SKIPPED);
    assertStatus("jobc", Status.RUNNING);
    assertStatus("jobd", Status.RUNNING);
    assertStatus("jobd:innerJobA", Status.SKIPPED);
    assertStatus("jobd:innerFlow2", Status.RUNNING);
    assertStatus("jobb:innerJobA", Status.READY);
    assertStatus("jobb:innerJobB", Status.READY);
    assertStatus("jobb:innerJobC", Status.READY);
    assertStatus("jobb:innerFlow", Status.READY);

    // 3. jobb:Inner completes
    /// innerJobA completes
    InteractiveTestJob.getTestJob("jobc").succeedJob();
    InteractiveTestJob.getTestJob("jobd:innerFlow2").succeedJob();
    assertStatus("jobd:innerFlow2", Status.SUCCEEDED);
    assertStatus("jobd", Status.SUCCEEDED);
    assertStatus("jobc", Status.SUCCEEDED);
    assertStatus("jobe", Status.RUNNING);

    InteractiveTestJob.getTestJob("jobe").succeedJob();
    InteractiveTestJob.getTestJob("joba1").succeedJob();
    assertStatus("jobe", Status.SUCCEEDED);
    assertStatus("joba1", Status.SUCCEEDED);
    assertStatus("jobf", Status.RUNNING);

    // 4. Finish up on inner flow for jobb
    InteractiveTestJob.getTestJob("jobf").succeedJob();
    assertStatus("jobf", Status.SUCCEEDED);

    assertFlowStatus(Status.SUCCEEDED);
    assertThreadShutDown();
  }

  /**
   * Tests a failure with the default FINISH_CURRENTLY_RUNNING. After the first failure, every job
   * that started should complete, and the rest of the jobs should be skipped.
   */
  @Test
  public void testNormalFailure1() throws Exception {
    // Test propagation of KILLED status to embedded flows.
    final EventCollectorListener eventCollector = new EventCollectorListener();
    this.runner = createFlowRunner(eventCollector);

    // 1. START FLOW
    runFlowRunnerInThread(this.runner);

    // After it starts up, only joba should be running
    assertStatus("joba", Status.RUNNING);
    assertStatus("joba1", Status.RUNNING);

    // 2. JOB A COMPLETES SUCCESSFULLY, others should be skipped
    InteractiveTestJob.getTestJob("joba").failJob();
    assertFlowStatus(Status.FAILED_FINISHING);
    assertStatus("joba", Status.FAILED);
    assertStatus("joba1", Status.RUNNING);
    assertStatus("jobb", Status.CANCELLED);
    assertStatus("jobc", Status.CANCELLED);
    assertStatus("jobd", Status.CANCELLED);
    assertStatus("jobd:innerJobA", Status.READY);
    assertStatus("jobd:innerFlow2", Status.READY);
    assertStatus("jobb:innerJobA", Status.READY);
    assertStatus("jobb:innerFlow", Status.READY);
    assertStatus("jobe", Status.CANCELLED);

    // 3. jobb:Inner completes
    /// innerJobA completes
    InteractiveTestJob.getTestJob("joba1").succeedJob();
    assertStatus("jobf", Status.CANCELLED);
    assertFlowStatus(Status.FAILED);
    assertThreadShutDown();
  }

  /**
   * Test #2 on the default failure case.
   */
  @Test
  public void testNormalFailure2() throws Exception {
    // Test propagation of KILLED status to embedded flows different branch
    final EventCollectorListener eventCollector = new EventCollectorListener();
    this.runner = createFlowRunner(eventCollector);

    // 1. START FLOW
    runFlowRunnerInThread(this.runner);

    // After it starts up, only joba should be running
    assertStatus("joba", Status.RUNNING);
    assertStatus("joba1", Status.RUNNING);

    // 2. JOB A COMPLETES SUCCESSFULLY, others should be skipped
    InteractiveTestJob.getTestJob("joba").succeedJob();
    assertStatus("joba", Status.SUCCEEDED);
    assertStatus("jobb", Status.RUNNING);
    assertStatus("jobc", Status.RUNNING);
    assertStatus("jobd", Status.RUNNING);
    assertStatus("jobb:innerJobA", Status.RUNNING);
    assertStatus("jobc", Status.RUNNING);
    assertStatus("jobd:innerJobA", Status.RUNNING);

    InteractiveTestJob.getTestJob("joba1").failJob();
    assertStatus("joba1", Status.FAILED);

    // 3. joba completes, everything is killed
    InteractiveTestJob.getTestJob("jobb:innerJobA").succeedJob();
    InteractiveTestJob.getTestJob("jobd:innerJobA").succeedJob();
    assertStatus("jobb:innerJobA", Status.SUCCEEDED);
    assertStatus("jobd:innerJobA", Status.SUCCEEDED);
    assertStatus("jobb:innerJobB", Status.CANCELLED);
    assertStatus("jobb:innerJobC", Status.CANCELLED);
    assertStatus("jobb:innerFlow", Status.CANCELLED);
    assertStatus("jobd:innerFlow2", Status.CANCELLED);
    assertStatus("jobb", Status.KILLED);
    assertStatus("jobd", Status.KILLED);
    assertFlowStatus(Status.FAILED_FINISHING);

    InteractiveTestJob.getTestJob("jobc").succeedJob();
    assertStatus("jobc", Status.SUCCEEDED);
    assertStatus("jobe", Status.CANCELLED);
    assertStatus("jobf", Status.CANCELLED);
    assertFlowStatus(Status.FAILED);
    assertThreadShutDown();
  }

  @Test
  public void testNormalFailure3() throws Exception {
    // Test propagation of CANCELLED status to embedded flows different branch
    final EventCollectorListener eventCollector = new EventCollectorListener();
    this.runner = createFlowRunner(eventCollector);

    // 1. START FLOW
    runFlowRunnerInThread(this.runner);

    // After it starts up, only joba should be running
    assertStatus("joba", Status.RUNNING);
    assertStatus("joba1", Status.RUNNING);

    // 2. JOB in subflow FAILS
    InteractiveTestJob.getTestJob("joba").succeedJob();
    assertStatus("joba", Status.SUCCEEDED);
    assertStatus("jobb", Status.RUNNING);
    assertStatus("jobc", Status.RUNNING);
    assertStatus("jobd", Status.RUNNING);
    assertStatus("jobb:innerJobA", Status.RUNNING);
    assertStatus("jobd:innerJobA", Status.RUNNING);

    InteractiveTestJob.getTestJob("joba1").succeedJob();
    InteractiveTestJob.getTestJob("jobb:innerJobA").succeedJob();
    assertStatus("jobb:innerJobA", Status.SUCCEEDED);
    assertStatus("joba1", Status.SUCCEEDED);
    assertStatus("jobb:innerJobB", Status.RUNNING);
    assertStatus("jobb:innerJobC", Status.RUNNING);

    InteractiveTestJob.getTestJob("jobb:innerJobB").failJob();
    assertStatus("jobb", Status.FAILED_FINISHING);
    assertStatus("jobb:innerJobB", Status.FAILED);
    assertFlowStatus(Status.FAILED_FINISHING);

    InteractiveTestJob.getTestJob("jobb:innerJobC").succeedJob();
    InteractiveTestJob.getTestJob("jobd:innerJobA").succeedJob();
    assertStatus("jobd:innerJobA", Status.SUCCEEDED);
    assertStatus("jobd:innerFlow2", Status.CANCELLED);
    assertStatus("jobd", Status.KILLED);
    assertStatus("jobb:innerJobC", Status.SUCCEEDED);
    assertStatus("jobb:innerFlow", Status.CANCELLED);
    assertStatus("jobb", Status.FAILED);

    // 3. jobc completes, everything is killed
    InteractiveTestJob.getTestJob("jobc").succeedJob();
    assertStatus("jobc", Status.SUCCEEDED);
    assertStatus("jobe", Status.CANCELLED);
    assertStatus("jobf", Status.CANCELLED);
    assertFlowStatus(Status.FAILED);
    assertThreadShutDown();
  }

  /**
   * Tests failures when the fail behaviour is FINISH_ALL_POSSIBLE. In this case, all jobs which
   * have had its pre-requisite met can continue to run. Finishes when the failure is propagated to
   * the last node of the flow.
   */
  @Test
  public void testFailedFinishingFailure3() throws Exception {
    // Test propagation of KILLED status to embedded flows different branch
    final EventCollectorListener eventCollector = new EventCollectorListener();
    this.runner = createFlowRunner(eventCollector,
        FailureAction.FINISH_ALL_POSSIBLE);

    // 1. START FLOW
    runFlowRunnerInThread(this.runner);

    // After it starts up, only joba should be running
    assertStatus("joba", Status.RUNNING);
    assertStatus("joba1", Status.RUNNING);

    // 2. JOB in subflow FAILS
    InteractiveTestJob.getTestJob("joba").succeedJob();
    assertStatus("joba", Status.SUCCEEDED);
    assertStatus("jobb", Status.RUNNING);
    assertStatus("jobc", Status.RUNNING);
    assertStatus("jobd", Status.RUNNING);
    assertStatus("jobb:innerJobA", Status.RUNNING);
    assertStatus("jobd:innerJobA", Status.RUNNING);

    InteractiveTestJob.getTestJob("joba1").succeedJob();
    InteractiveTestJob.getTestJob("jobb:innerJobA").succeedJob();
    assertStatus("jobb:innerJobA", Status.SUCCEEDED);
    assertStatus("joba1", Status.SUCCEEDED);
    assertStatus("jobb:innerJobB", Status.RUNNING);
    assertStatus("jobb:innerJobC", Status.RUNNING);

    InteractiveTestJob.getTestJob("jobb:innerJobB").failJob();
    assertStatus("jobb", Status.FAILED_FINISHING);
    assertStatus("jobb:innerJobB", Status.FAILED);
    assertFlowStatus(Status.FAILED_FINISHING);

    InteractiveTestJob.getTestJob("jobb:innerJobC").succeedJob();
    InteractiveTestJob.getTestJob("jobd:innerJobA").succeedJob();
    assertStatus("jobb", Status.FAILED);
    assertStatus("jobd:innerJobA", Status.SUCCEEDED);
    assertStatus("jobd:innerFlow2", Status.RUNNING);
    assertStatus("jobb:innerJobC", Status.SUCCEEDED);
    assertStatus("jobb:innerFlow", Status.CANCELLED);

    InteractiveTestJob.getTestJob("jobd:innerFlow2").succeedJob();
    assertStatus("jobd:innerFlow2", Status.SUCCEEDED);
    assertStatus("jobd", Status.SUCCEEDED);

    // 3. jobc completes, everything is killed
    InteractiveTestJob.getTestJob("jobc").succeedJob();
    assertStatus("jobc", Status.SUCCEEDED);
    assertStatus("jobe", Status.CANCELLED);
    assertStatus("jobf", Status.CANCELLED);
    assertFlowStatus(Status.FAILED);
    assertThreadShutDown();
  }

  /**
   * Tests the failure condition when a failure invokes a cancel (or killed) on the flow.
   *
   * Any jobs that are running will be assigned a KILLED state, and any nodes which were skipped due
   * to prior errors will be given a CANCELLED state.
   */
  @Test
  public void testCancelOnFailure() throws Exception {
    // Test propagation of KILLED status to embedded flows different branch
    final EventCollectorListener eventCollector = new EventCollectorListener();
    this.runner = createFlowRunner(eventCollector,
        FailureAction.CANCEL_ALL);

    // 1. START FLOW
    runFlowRunnerInThread(this.runner);

    // After it starts up, only joba should be running
    assertStatus("joba", Status.RUNNING);
    assertStatus("joba1", Status.RUNNING);

    // 2. JOB in subflow FAILS
    InteractiveTestJob.getTestJob("joba").succeedJob();
    assertStatus("joba", Status.SUCCEEDED);
    assertStatus("jobb", Status.RUNNING);
    assertStatus("jobc", Status.RUNNING);
    assertStatus("jobd", Status.RUNNING);
    assertStatus("jobb:innerJobA", Status.RUNNING);
    assertStatus("jobd:innerJobA", Status.RUNNING);

    InteractiveTestJob.getTestJob("joba1").succeedJob();
    InteractiveTestJob.getTestJob("jobb:innerJobA").succeedJob();
    assertStatus("jobb:innerJobA", Status.SUCCEEDED);
    assertStatus("joba1", Status.SUCCEEDED);
    assertStatus("jobb:innerJobB", Status.RUNNING);
    assertStatus("jobb:innerJobC", Status.RUNNING);

    InteractiveTestJob.getTestJob("jobb:innerJobB").failJob();
    assertStatus("jobb", Status.FAILED);
    assertStatus("jobb:innerJobB", Status.FAILED);
    assertStatus("jobb:innerJobC", Status.KILLED);
    assertStatus("jobb:innerFlow", Status.CANCELLED);
    assertStatus("jobc", Status.KILLED);
    assertStatus("jobd", Status.KILLED);
    assertStatus("jobd:innerJobA", Status.KILLED);
    assertStatus("jobd:innerFlow2", Status.CANCELLED);
    assertStatus("jobe", Status.CANCELLED);
    assertStatus("jobf", Status.CANCELLED);

    assertThreadShutDown();
    assertFlowStatus(Status.KILLED);
  }

  /**
   * Tests retries after a failure
   */
  @Test
  public void testRetryOnFailure() throws Exception {
    // Test propagation of KILLED status to embedded flows different branch
    final EventCollectorListener eventCollector = new EventCollectorListener();
    this.runner = createFlowRunner(eventCollector);
    final ExecutableFlow flow = this.runner.getExecutableFlow();
    flow.getExecutableNode("joba").setStatus(Status.DISABLED);
    ((ExecutableFlowBase) flow.getExecutableNode("jobb")).getExecutableNode(
        "innerFlow").setStatus(Status.DISABLED);

    // 1. START FLOW
    runFlowRunnerInThread(this.runner);

    assertStatus("joba", Status.SKIPPED);
    assertStatus("joba1", Status.RUNNING);
    assertStatus("jobb", Status.RUNNING);
    assertStatus("jobc", Status.RUNNING);
    assertStatus("jobd", Status.RUNNING);
    assertStatus("jobb:innerJobA", Status.RUNNING);
    assertStatus("jobd:innerJobA", Status.RUNNING);

    InteractiveTestJob.getTestJob("jobb:innerJobA").succeedJob();
    assertStatus("jobb:innerJobA", Status.SUCCEEDED);
    assertStatus("jobb:innerJobB", Status.RUNNING);
    assertStatus("jobb:innerJobC", Status.RUNNING);

    InteractiveTestJob.getTestJob("jobb:innerJobB").failJob();
    InteractiveTestJob.getTestJob("jobb:innerJobC").failJob();
    assertStatus("jobb:innerJobB", Status.FAILED);
    assertStatus("jobb:innerJobC", Status.FAILED);
    assertStatus("jobb", Status.FAILED);
    assertStatus("jobb:innerFlow", Status.SKIPPED);
    InteractiveTestJob.getTestJob("jobd:innerJobA").succeedJob();
    assertStatus("jobd:innerJobA", Status.SUCCEEDED);
    assertStatus("jobd:innerFlow2", Status.CANCELLED);
    assertStatus("jobd", Status.KILLED);
    assertFlowStatus(Status.FAILED_FINISHING);

    final ExecutableNode node = this.runner.getExecutableFlow()
        .getExecutableNodePath("jobd:innerFlow2");
    final ExecutableFlowBase base = node.getParentFlow();
    for (final String nodeId : node.getInNodes()) {
      final ExecutableNode inNode = base.getExecutableNode(nodeId);
      System.out.println(inNode.getId() + " > " + inNode.getStatus());
    }

    assertStatus("jobb:innerFlow", Status.SKIPPED);
    InteractiveTestJob.clearTestJobs("jobb:innerJobB", "jobb:innerJobC");
    this.runner.retryFailures("me");
    assertStatus("jobb:innerJobB", Status.RUNNING);
    assertStatus("jobb:innerJobC", Status.RUNNING);
    assertStatus("jobb", Status.RUNNING);
    assertStatus("jobd", Status.RUNNING);
    assertStatus("jobb:innerFlow", Status.DISABLED);
    assertStatus("jobd:innerFlow2", Status.RUNNING);
    assertFlowStatus(Status.RUNNING);
    assertThreadRunning();

    InteractiveTestJob.getTestJob("jobb:innerJobB").succeedJob();
    InteractiveTestJob.getTestJob("jobb:innerJobC").succeedJob();
    InteractiveTestJob.getTestJob("jobd:innerFlow2").succeedJob();
    InteractiveTestJob.getTestJob("jobc").succeedJob();
    assertStatus("jobb:innerFlow", Status.SKIPPED);
    assertStatus("jobb", Status.SUCCEEDED);
    assertStatus("jobb:innerJobB", Status.SUCCEEDED);
    assertStatus("jobb:innerJobC", Status.SUCCEEDED);
    assertStatus("jobc", Status.SUCCEEDED);
    assertStatus("jobd", Status.SUCCEEDED);
    assertStatus("jobd:innerFlow2", Status.SUCCEEDED);
    assertStatus("jobe", Status.RUNNING);

    InteractiveTestJob.getTestJob("jobe").succeedJob();
    assertStatus("jobe", Status.SUCCEEDED);

    InteractiveTestJob.getTestJob("joba1").succeedJob();
    assertStatus("joba1", Status.SUCCEEDED);
    assertStatus("jobf", Status.RUNNING);

    InteractiveTestJob.getTestJob("jobf").succeedJob();
    assertStatus("jobf", Status.SUCCEEDED);
    assertFlowStatus(Status.SUCCEEDED);
    assertThreadShutDown();
  }

  /**
   * Tests the manual Killing of a flow. In this case, the flow is just fine before the cancel is
   * called.
   */
  @Test
  public void testCancel() throws Exception {
    // Test propagation of KILLED status to embedded flows different branch
    final EventCollectorListener eventCollector = new EventCollectorListener();
    this.runner = createFlowRunner(eventCollector,
        FailureAction.CANCEL_ALL);

    // 1. START FLOW
    runFlowRunnerInThread(this.runner);

    // After it starts up, only joba should be running
    assertStatus("joba", Status.RUNNING);
    assertStatus("joba1", Status.RUNNING);

    // 2. JOB in subflow FAILS
    InteractiveTestJob.getTestJob("joba").succeedJob();
    assertStatus("joba", Status.SUCCEEDED);
    assertStatus("jobb", Status.RUNNING);
    assertStatus("jobc", Status.RUNNING);
    assertStatus("jobd", Status.RUNNING);
    assertStatus("jobb:innerJobA", Status.RUNNING);
    assertStatus("jobd:innerJobA", Status.RUNNING);

    InteractiveTestJob.getTestJob("joba1").succeedJob();
    InteractiveTestJob.getTestJob("jobb:innerJobA").succeedJob();
    assertStatus("jobb:innerJobA", Status.SUCCEEDED);
    assertStatus("joba1", Status.SUCCEEDED);
    assertStatus("jobb:innerJobB", Status.RUNNING);
    assertStatus("jobb:innerJobC", Status.RUNNING);

    this.runner.kill("me");

    assertStatus("jobb", Status.KILLED);
    assertStatus("jobb:innerJobB", Status.KILLED);
    assertStatus("jobb:innerJobC", Status.KILLED);
    assertStatus("jobb:innerFlow", Status.CANCELLED);
    assertStatus("jobc", Status.KILLED);
    assertStatus("jobd", Status.KILLED);
    assertStatus("jobd:innerJobA", Status.KILLED);
    assertStatus("jobd:innerFlow2", Status.CANCELLED);
    assertStatus("jobe", Status.CANCELLED);
    assertStatus("jobf", Status.CANCELLED);

    assertFlowStatus(Status.KILLED);
    assertThreadShutDown();
  }

  /**
   * Tests the manual invocation of cancel on a flow that is FAILED_FINISHING
   */
  @Test
  public void testManualCancelOnFailure() throws Exception {
    // Test propagation of KILLED status to embedded flows different branch
    final EventCollectorListener eventCollector = new EventCollectorListener();
    this.runner = createFlowRunner(eventCollector);

    // 1. START FLOW
    runFlowRunnerInThread(this.runner);

    // After it starts up, only joba should be running
    assertStatus("joba", Status.RUNNING);
    assertStatus("joba1", Status.RUNNING);

    // 2. JOB in subflow FAILS
    InteractiveTestJob.getTestJob("joba").succeedJob();
    assertStatus("joba", Status.SUCCEEDED);
    assertStatus("jobb", Status.RUNNING);
    assertStatus("jobc", Status.RUNNING);
    assertStatus("jobd", Status.RUNNING);
    assertStatus("jobb:innerJobA", Status.RUNNING);
    assertStatus("jobd:innerJobA", Status.RUNNING);

    InteractiveTestJob.getTestJob("joba1").succeedJob();
    InteractiveTestJob.getTestJob("jobb:innerJobA").succeedJob();
    assertStatus("jobb:innerJobA", Status.SUCCEEDED);
    assertStatus("joba1", Status.SUCCEEDED);
    assertStatus("jobb:innerJobB", Status.RUNNING);
    assertStatus("jobb:innerJobC", Status.RUNNING);

    InteractiveTestJob.getTestJob("jobb:innerJobB").failJob();
    assertStatus("jobb:innerJobB", Status.FAILED);
    assertStatus("jobb", Status.FAILED_FINISHING);
    assertFlowStatus(Status.FAILED_FINISHING);

    this.runner.kill("me");

    assertStatus("jobb", Status.FAILED);
    assertStatus("jobb:innerJobC", Status.KILLED);
    assertStatus("jobb:innerFlow", Status.CANCELLED);
    assertStatus("jobc", Status.KILLED);
    assertStatus("jobd", Status.KILLED);
    assertStatus("jobd:innerJobA", Status.KILLED);
    assertStatus("jobd:innerFlow2", Status.CANCELLED);
    assertStatus("jobe", Status.CANCELLED);
    assertStatus("jobf", Status.CANCELLED);

    assertFlowStatus(Status.KILLED);
    assertThreadShutDown();
  }

  /**
   * Tests that pause and resume work
   */
  @Test
  public void testPause() throws Exception {
    final EventCollectorListener eventCollector = new EventCollectorListener();
    this.runner = createFlowRunner(eventCollector);

    // 1. START FLOW
    runFlowRunnerInThread(this.runner);

    // After it starts up, only joba should be running
    assertStatus("joba", Status.RUNNING);
    assertStatus("joba1", Status.RUNNING);

    this.runner.pause("test");
    InteractiveTestJob.getTestJob("joba").succeedJob();
    // 2.1 JOB A COMPLETES SUCCESSFULLY AFTER PAUSE
    assertStatus("joba", Status.SUCCEEDED);
    assertFlowStatus(Status.PAUSED);

    // 2.2 Flow is unpaused
    this.runner.resume("test");
    assertFlowStatus(Status.RUNNING);
    assertStatus("joba", Status.SUCCEEDED);
    assertStatus("joba1", Status.RUNNING);
    assertStatus("jobb", Status.RUNNING);
    assertStatus("jobc", Status.RUNNING);
    assertStatus("jobd", Status.RUNNING);
    assertStatus("jobd:innerJobA", Status.RUNNING);
    assertStatus("jobb:innerJobA", Status.RUNNING);

    // 3. jobb:Inner completes
    this.runner.pause("test");

    /// innerJobA completes, but paused
    InteractiveTestJob.getTestJob("jobb:innerJobA").succeedJob(
        Props.of("output.jobb.innerJobA", "jobb.innerJobA"));
    assertStatus("jobb:innerJobA", Status.SUCCEEDED);

    this.runner.resume("test");
    assertStatus("jobb:innerJobB", Status.RUNNING);
    assertStatus("jobb:innerJobC", Status.RUNNING);

    /// innerJobB, C completes
    InteractiveTestJob.getTestJob("jobb:innerJobB").succeedJob(
        Props.of("output.jobb.innerJobB", "jobb.innerJobB"));
    InteractiveTestJob.getTestJob("jobb:innerJobC").succeedJob(
        Props.of("output.jobb.innerJobC", "jobb.innerJobC"));
    assertStatus("jobb:innerJobB", Status.SUCCEEDED);
    assertStatus("jobb:innerJobC", Status.SUCCEEDED);
    assertStatus("jobb:innerFlow", Status.RUNNING);

    // 4. Finish up on inner flow for jobb
    InteractiveTestJob.getTestJob("jobb:innerFlow").succeedJob(
        Props.of("output1.jobb", "test1", "output2.jobb", "test2"));
    assertStatus("jobb:innerFlow", Status.SUCCEEDED);
    assertStatus("jobb", Status.SUCCEEDED);

    // 5. Finish jobc, jobd
    InteractiveTestJob.getTestJob("jobc").succeedJob(
        Props.of("output.jobc", "jobc"));
    assertStatus("jobc", Status.SUCCEEDED);
    InteractiveTestJob.getTestJob("jobd:innerJobA").succeedJob();
    InteractiveTestJob.getTestJob("jobd:innerFlow2").succeedJob();
    assertStatus("jobd:innerJobA", Status.SUCCEEDED);
    assertStatus("jobd:innerFlow2", Status.SUCCEEDED);
    assertStatus("jobd", Status.SUCCEEDED);
    assertStatus("jobe", Status.RUNNING);

    // 6. Finish off flow
    InteractiveTestJob.getTestJob("joba1").succeedJob();
    InteractiveTestJob.getTestJob("jobe").succeedJob();
    assertStatus("joba1", Status.SUCCEEDED);
    assertStatus("jobe", Status.SUCCEEDED);
    assertStatus("jobf", Status.RUNNING);

    InteractiveTestJob.getTestJob("jobf").succeedJob();
    assertStatus("jobf", Status.SUCCEEDED);
    assertFlowStatus(Status.SUCCEEDED);
    assertThreadShutDown();
  }

  /**
   * Test the condition for a manual invocation of a KILL (cancel) on a flow that has been paused.
   * The flow should unpause and be killed immediately.
   */
  @Test
  public void testPauseKill() throws Exception {
    final EventCollectorListener eventCollector = new EventCollectorListener();
    this.runner = createFlowRunner(eventCollector);

    // 1. START FLOW
    runFlowRunnerInThread(this.runner);

    // After it starts up, only joba should be running
    assertStatus("joba", Status.RUNNING);
    assertStatus("joba1", Status.RUNNING);

    // 2. JOB A COMPLETES SUCCESSFULLY
    InteractiveTestJob.getTestJob("joba").succeedJob();
    assertStatus("joba", Status.SUCCEEDED);
    assertStatus("joba1", Status.RUNNING);
    assertStatus("jobb", Status.RUNNING);
    assertStatus("jobc", Status.RUNNING);
    assertStatus("jobd", Status.RUNNING);
    assertStatus("jobd:innerJobA", Status.RUNNING);
    assertStatus("jobb:innerJobA", Status.RUNNING);

    this.runner.pause("me");
    assertFlowStatus(Status.PAUSED);
    InteractiveTestJob.getTestJob("jobb:innerJobA").succeedJob();
    InteractiveTestJob.getTestJob("jobd:innerJobA").succeedJob();
    assertStatus("jobb:innerJobA", Status.SUCCEEDED);
    assertStatus("jobd:innerJobA", Status.SUCCEEDED);

    this.runner.kill("me");
    assertStatus("joba1", Status.KILLED);
    assertStatus("jobb:innerJobB", Status.CANCELLED);
    assertStatus("jobb:innerJobC", Status.CANCELLED);
    assertStatus("jobb:innerFlow", Status.CANCELLED);
    assertStatus("jobb", Status.KILLED);
    assertStatus("jobc", Status.KILLED);
    assertStatus("jobd:innerFlow2", Status.CANCELLED);
    assertStatus("jobd", Status.KILLED);
    assertStatus("jobe", Status.CANCELLED);
    assertStatus("jobf", Status.CANCELLED);

    assertFlowStatus(Status.KILLED);
    assertThreadShutDown();
  }

  /**
   * Tests the case where a failure occurs on a Paused flow. In this case, the flow should stay
   * paused.
   */
  @Test
  public void testPauseFail() throws Exception {
    this.eventCollector = new EventCollectorListener();
    this.runner = createFlowRunner(this.eventCollector,
        FailureAction.FINISH_CURRENTLY_RUNNING);

    // 1. START FLOW
    runFlowRunnerInThread(this.runner);

    // After it starts up, only joba should be running
    assertStatus("joba", Status.RUNNING);
    assertStatus("joba1", Status.RUNNING);

    // 2. JOB A COMPLETES SUCCESSFULLY
    InteractiveTestJob.getTestJob("joba").succeedJob();
    assertStatus("joba", Status.SUCCEEDED);
    assertStatus("joba1", Status.RUNNING);
    assertStatus("jobb", Status.RUNNING);
    assertStatus("jobc", Status.RUNNING);
    assertStatus("jobd", Status.RUNNING);
    assertStatus("jobd:innerJobA", Status.RUNNING);
    assertStatus("jobb:innerJobA", Status.RUNNING);

    this.runner.pause("me");
    assertFlowStatus(Status.PAUSED);
    InteractiveTestJob.getTestJob("jobb:innerJobA").succeedJob();
    InteractiveTestJob.getTestJob("jobd:innerJobA").failJob();
    assertStatus("jobd:innerJobA", Status.FAILED);
    assertStatus("jobb:innerJobA", Status.SUCCEEDED);
    // When flow is paused, no new jobs are started. So these two jobs that were already running
    // are allowed to finish, but their dependencies aren't started.
    // Now, ensure that jobd:innerJobA has completely finished as failed before resuming.
    // If we would resume before the job failure has been completely processed, FlowRunner would be
    // able to start some new jobs instead of cancelling everything.
    waitEventFired("jobd:innerJobA", Status.FAILED);
    assertFlowStatus(Status.PAUSED);

    this.runner.resume("me");
    assertStatus("jobb:innerJobB", Status.CANCELLED);
    assertStatus("jobb:innerJobC", Status.CANCELLED);
    assertStatus("jobb:innerFlow", Status.CANCELLED);
    assertStatus("jobb", Status.KILLED);
    assertStatus("jobd:innerFlow2", Status.CANCELLED);
    assertStatus("jobd", Status.FAILED);

    InteractiveTestJob.getTestJob("jobc").succeedJob();
    InteractiveTestJob.getTestJob("joba1").succeedJob();
    assertStatus("jobc", Status.SUCCEEDED);
    assertStatus("joba1", Status.SUCCEEDED);
    assertStatus("jobf", Status.CANCELLED);
    assertStatus("jobe", Status.CANCELLED);

    assertFlowStatus(Status.FAILED);
    assertThreadShutDown();
  }

  /**
   * Test the condition when a Finish all possible is called during a pause. The Failure is not
   * acted upon until the flow is resumed.
   */
  @Test
  public void testPauseFailFinishAll() throws Exception {
    final EventCollectorListener eventCollector = new EventCollectorListener();
    this.runner = createFlowRunner(eventCollector,
        FailureAction.FINISH_ALL_POSSIBLE);

    // 1. START FLOW
    runFlowRunnerInThread(this.runner);

    // After it starts up, only joba should be running
    assertStatus("joba", Status.RUNNING);
    assertStatus("joba1", Status.RUNNING);

    // 2. JOB A COMPLETES SUCCESSFULLY
    InteractiveTestJob.getTestJob("joba").succeedJob();

    assertStatus("joba", Status.SUCCEEDED);
    assertStatus("joba1", Status.RUNNING);
    assertStatus("jobb", Status.RUNNING);
    assertStatus("jobc", Status.RUNNING);
    assertStatus("jobd", Status.RUNNING);
    assertStatus("jobd:innerJobA", Status.RUNNING);
    assertStatus("jobb:innerJobA", Status.RUNNING);

    this.runner.pause("me");
    assertFlowStatus(Status.PAUSED);
    InteractiveTestJob.getTestJob("jobb:innerJobA").succeedJob();
    InteractiveTestJob.getTestJob("jobd:innerJobA").failJob();
    assertStatus("jobd:innerJobA", Status.FAILED);
    assertStatus("jobb:innerJobA", Status.SUCCEEDED);

    this.runner.resume("me");
    assertStatus("jobb:innerJobB", Status.RUNNING);
    assertStatus("jobb:innerJobC", Status.RUNNING);
    assertStatus("jobd:innerFlow2", Status.CANCELLED);
    assertStatus("jobd", Status.FAILED);

    InteractiveTestJob.getTestJob("jobc").succeedJob();
    InteractiveTestJob.getTestJob("joba1").succeedJob();
    InteractiveTestJob.getTestJob("jobb:innerJobB").succeedJob();
    InteractiveTestJob.getTestJob("jobb:innerJobC").succeedJob();
    InteractiveTestJob.getTestJob("jobb:innerFlow").succeedJob();
    assertStatus("jobc", Status.SUCCEEDED);
    assertStatus("joba1", Status.SUCCEEDED);
    assertStatus("jobb:innerJobB", Status.SUCCEEDED);
    assertStatus("jobb:innerJobC", Status.SUCCEEDED);
    assertStatus("jobb:innerFlow", Status.SUCCEEDED);
    assertStatus("jobb", Status.SUCCEEDED);
    assertStatus("jobe", Status.CANCELLED);
    assertStatus("jobf", Status.CANCELLED);

    assertFlowStatus(Status.FAILED);
    assertThreadShutDown();
  }

  /**
   * Tests the case when a flow is paused and a failure causes a kill. The flow should die
   * immediately regardless of the 'paused' status.
   */
  @Test
  public void testPauseFailKill() throws Exception {
    final EventCollectorListener eventCollector = new EventCollectorListener();
    this.runner = createFlowRunner(eventCollector,
        FailureAction.CANCEL_ALL);

    // 1. START FLOW
    runFlowRunnerInThread(this.runner);
    // After it starts up, only joba should be running
    assertStatus("joba", Status.RUNNING);
    assertStatus("joba1", Status.RUNNING);

    // 2. JOB A COMPLETES SUCCESSFULLY
    InteractiveTestJob.getTestJob("joba").succeedJob();
    assertStatus("joba", Status.SUCCEEDED);
    assertStatus("joba1", Status.RUNNING);
    assertStatus("jobb", Status.RUNNING);
    assertStatus("jobc", Status.RUNNING);
    assertStatus("jobd", Status.RUNNING);
    assertStatus("jobd:innerJobA", Status.RUNNING);
    assertStatus("jobb:innerJobA", Status.RUNNING);

    this.runner.pause("me");
    assertFlowStatus(Status.PAUSED);
    InteractiveTestJob.getTestJob("jobd:innerJobA").failJob();
    assertStatus("jobd:innerJobA", Status.FAILED);
    assertStatus("jobd:innerFlow2", Status.CANCELLED);
    assertStatus("jobd", Status.FAILED);
    assertStatus("jobb:innerJobA", Status.KILLED);
    assertStatus("jobb:innerJobB", Status.CANCELLED);
    assertStatus("jobb:innerJobC", Status.CANCELLED);
    assertStatus("jobb:innerFlow", Status.CANCELLED);
    assertStatus("jobb", Status.KILLED);
    assertStatus("jobc", Status.KILLED);
    assertStatus("jobe", Status.CANCELLED);
    assertStatus("jobf", Status.CANCELLED);
    assertStatus("joba1", Status.KILLED);

    assertFlowStatus(Status.KILLED);
    assertThreadShutDown();
  }

  private void runFlowRunnerInThread(final FlowRunner runner) {
    final Thread thread = new Thread(runner);
    thread.start();
  }

  private FlowRunner createFlowRunner(final EventCollectorListener eventCollector)
      throws Exception {
    return createFlowRunner(eventCollector,
        FailureAction.FINISH_CURRENTLY_RUNNING);
  }

  private FlowRunner createFlowRunner(final EventCollectorListener eventCollector,
      final FailureAction action) throws Exception {
    return createFlowRunner(eventCollector, action, new Props());
  }

  private FlowRunner createFlowRunner(final EventCollectorListener eventCollector,
      final FailureAction action, final Props azkabanProps)
      throws Exception {
    final Flow flow = this.flowMap.get("jobf");

    final int exId = id++;
    final ExecutableFlow exFlow = new ExecutableFlow(this.project, flow);
    exFlow.setExecutionPath(this.workingDir.getPath());
    exFlow.setExecutionId(exId);

    final Map<String, String> flowParam = new HashMap<>();
    flowParam.put("param4", "override.4");
    flowParam.put("param10", "override.10");
    flowParam.put("param11", "override.11");
    exFlow.getExecutionOptions().addAllFlowParameters(flowParam);
    exFlow.getExecutionOptions().setFailureAction(action);
    this.fakeExecutorLoader.uploadExecutableFlow(exFlow);

    final FlowRunner runner = new FlowRunner(
        this.fakeExecutorLoader.fetchExecutableFlow(exId), this.fakeExecutorLoader,
        this.fakeProjectLoader, this.jobtypeManager, azkabanProps);

    runner.addListener(eventCollector);

    return runner;
  }

}
