/*
 * Copyright 2012 LinkedIn Corp.
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

package com.datapps.zebra.workflow.trigger.builtin;

import com.datapps.zebra.workflow.executor.ExecutableFlow;
import com.datapps.zebra.workflow.executor.ExecutionOptions;
import com.datapps.zebra.workflow.executor.ExecutorManagerAdapter;
import com.datapps.zebra.workflow.executor.ExecutorManagerException;
import com.datapps.zebra.workflow.flow.Flow;
import com.datapps.zebra.workflow.project.Project;
import com.datapps.zebra.workflow.project.ProjectManager;
import com.datapps.zebra.workflow.sla.SlaOption;
import com.datapps.zebra.workflow.trigger.TriggerAction;
import com.datapps.zebra.workflow.trigger.TriggerManager;
import org.apache.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExecuteFlowAction implements TriggerAction {

  public static final String type = "ExecuteFlowAction";

  public static final String EXEC_ID = "ExecuteFlowAction.execid";

  private static ExecutorManagerAdapter executorManager;
  private static TriggerManager triggerManager;
  private static ProjectManager projectManager;
  private static Logger logger = Logger.getLogger(ExecuteFlowAction.class);
  private final String actionId;
  private final String projectName;
  private int projectId;
  private String flowName;
  private String submitUser;
  private ExecutionOptions executionOptions = new ExecutionOptions();
  private List<SlaOption> slaOptions;

  public ExecuteFlowAction(final String actionId, final int projectId, final String projectName,
      final String flowName, final String submitUser, final ExecutionOptions executionOptions,
      final List<SlaOption> slaOptions) {
    this.actionId = actionId;
    this.projectId = projectId;
    this.projectName = projectName;
    this.flowName = flowName;
    this.submitUser = submitUser;
    this.executionOptions = executionOptions;
    this.slaOptions = slaOptions;
  }

  public static void setLogger(final Logger logger) {
    ExecuteFlowAction.logger = logger;
  }

  public static ExecutorManagerAdapter getExecutorManager() {
    return executorManager;
  }

  public static void setExecutorManager(final ExecutorManagerAdapter executorManager) {
    ExecuteFlowAction.executorManager = executorManager;
  }

  public static TriggerManager getTriggerManager() {
    return triggerManager;
  }

  public static void setTriggerManager(final TriggerManager triggerManager) {
    ExecuteFlowAction.triggerManager = triggerManager;
  }

  public static ProjectManager getProjectManager() {
    return projectManager;
  }

  public static void setProjectManager(final ProjectManager projectManager) {
    ExecuteFlowAction.projectManager = projectManager;
  }

  public static TriggerAction createFromJson(final HashMap<String, Object> obj) {
    final Map<String, Object> jsonObj = (HashMap<String, Object>) obj;
    final String objType = (String) jsonObj.get("type");
    if (!objType.equals(type)) {
      throw new RuntimeException("Cannot create action of " + type + " from "
          + objType);
    }
    final String actionId = (String) jsonObj.get("actionId");
    final int projectId = Integer.valueOf((String) jsonObj.get("projectId"));
    final String projectName = (String) jsonObj.get("projectName");
    final String flowName = (String) jsonObj.get("flowName");
    final String submitUser = (String) jsonObj.get("submitUser");
    ExecutionOptions executionOptions = null;
    if (jsonObj.containsKey("executionOptions")) {
      executionOptions =
          ExecutionOptions.createFromObject(jsonObj.get("executionOptions"));
    }
    List<SlaOption> slaOptions = null;
    if (jsonObj.containsKey("slaOptions")) {
      slaOptions = new ArrayList<>();
      final List<Object> slaOptionsObj = (List<Object>) jsonObj.get("slaOptions");
      for (final Object slaObj : slaOptionsObj) {
        slaOptions.add(SlaOption.fromObject(slaObj));
      }
    }
    return new ExecuteFlowAction(actionId, projectId, projectName, flowName,
        submitUser, executionOptions, slaOptions);
  }

  public String getProjectName() {
    return this.projectName;
  }

  public int getProjectId() {
    return this.projectId;
  }

  protected void setProjectId(final int projectId) {
    this.projectId = projectId;
  }

  public String getFlowName() {
    return this.flowName;
  }

  protected void setFlowName(final String flowName) {
    this.flowName = flowName;
  }

  public String getSubmitUser() {
    return this.submitUser;
  }

  protected void setSubmitUser(final String submitUser) {
    this.submitUser = submitUser;
  }

  public ExecutionOptions getExecutionOptions() {
    return this.executionOptions;
  }

  protected void setExecutionOptions(final ExecutionOptions executionOptions) {
    this.executionOptions = executionOptions;
  }

  public List<SlaOption> getSlaOptions() {
    return this.slaOptions;
  }

  protected void setSlaOptions(final List<SlaOption> slaOptions) {
    this.slaOptions = slaOptions;
  }

  @Override
  public String getType() {
    return type;
  }

  @Override
  public TriggerAction fromJson(final Object obj) {
    return createFromJson((HashMap<String, Object>) obj);
  }

  @Override
  public Object toJson() {
    final Map<String, Object> jsonObj = new HashMap<>();
    jsonObj.put("actionId", this.actionId);
    jsonObj.put("type", type);
    jsonObj.put("projectId", String.valueOf(this.projectId));
    jsonObj.put("projectName", this.projectName);
    jsonObj.put("flowName", this.flowName);
    jsonObj.put("submitUser", this.submitUser);
    if (this.executionOptions != null) {
      jsonObj.put("executionOptions", this.executionOptions.toObject());
    }
    if (this.slaOptions != null) {
      final List<Object> slaOptionsObj = new ArrayList<>();
      for (final SlaOption sla : this.slaOptions) {
        slaOptionsObj.add(sla.toObject());
      }
      jsonObj.put("slaOptions", slaOptionsObj);
    }
    return jsonObj;
  }

  @Override
  public void doAction() throws Exception {
    if (projectManager == null || executorManager == null) {
      throw new Exception("ExecuteFlowAction not properly initialized!");
    }

    final Project project = projectManager.getProject(this.projectId);
    if (project == null) {
      logger.error("Project to execute " + this.projectId + " does not exist!");
      throw new RuntimeException("Error finding the project to execute "
          + this.projectId);
    }

    final Flow flow = project.getFlow(this.flowName);
    if (flow == null) {
      logger.error("Flow " + this.flowName + " cannot be found in project "
          + project.getName());
      throw new RuntimeException("Error finding the flow to execute "
          + this.flowName);
    }

    final ExecutableFlow exflow = new ExecutableFlow(project, flow);
    exflow.setSubmitUser(this.submitUser);
    exflow.addAllProxyUsers(project.getProxyUsers());

    if (this.executionOptions == null) {
      this.executionOptions = new ExecutionOptions();
    }
    if (!this.executionOptions.isFailureEmailsOverridden()) {
      this.executionOptions.setFailureEmails(flow.getFailureEmails());
    }
    if (!this.executionOptions.isSuccessEmailsOverridden()) {
      this.executionOptions.setSuccessEmails(flow.getSuccessEmails());
    }
    exflow.setExecutionOptions(this.executionOptions);

    if (this.slaOptions != null && this.slaOptions.size() > 0) {
      exflow.setSlaOptions(this.slaOptions);
    }

    try {
      logger.info("Invoking flow " + project.getName() + "." + this.flowName);
      executorManager.submitExecutableFlow(exflow, this.submitUser);
      logger.info("Invoked flow " + project.getName() + "." + this.flowName);
    } catch (final ExecutorManagerException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public String getDescription() {
    return "Execute flow " + getFlowName() + " from project "
        + getProjectName();
  }

  @Override
  public void setContext(final Map<String, Object> context) {
  }

  @Override
  public String getId() {
    return this.actionId;
  }

}
