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

package com.datapps.zebra.workflow.execapp.event;

import com.datapps.zebra.workflow.executor.ExecutableFlow;
import com.datapps.zebra.workflow.executor.ExecutableNode;
import com.datapps.zebra.workflow.executor.Status;
import org.apache.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class FlowWatcher {

  private final int execId;
  private final Map<String, BlockingStatus> map =
      new ConcurrentHashMap<>();
  private Logger logger;
  private ExecutableFlow flow;
  private boolean cancelWatch = false;

  public FlowWatcher(final int execId) {
    this.execId = execId;
  }

  public void setFlow(final ExecutableFlow flow) {
    this.flow = flow;
  }

  protected Logger getLogger() {
    return this.logger;
  }

  public void setLogger(final Logger logger) {
    this.logger = logger;
  }

  /**
   * Called to fire events to the JobRunner listeners
   */
  protected synchronized void handleJobStatusChange(final String jobId, final Status status) {
    final BlockingStatus block = this.map.get(jobId);
    if (block != null) {
      block.changeStatus(status);
    }
  }

  public int getExecId() {
    return this.execId;
  }

  public synchronized BlockingStatus getBlockingStatus(final String jobId) {
    if (this.cancelWatch) {
      return null;
    }

    final ExecutableNode node = this.flow.getExecutableNodePath(jobId);
    if (node == null) {
      return null;
    }

    BlockingStatus blockingStatus = this.map.get(jobId);
    if (blockingStatus == null) {
      blockingStatus = new BlockingStatus(this.execId, jobId, node.getStatus());
      this.map.put(jobId, blockingStatus);
    }

    return blockingStatus;
  }

  public Status peekStatus(final String jobId) {
    final ExecutableNode node = this.flow.getExecutableNodePath(jobId);
    if (node != null) {
      return node.getStatus();
    }

    return null;
  }

  public synchronized void unblockAllWatches() {
    this.logger.info("Unblock all watches on " + this.execId);
    this.cancelWatch = true;

    for (final BlockingStatus status : this.map.values()) {
      this.logger.info("Unblocking " + status.getJobId());
      status.changeStatus(Status.SKIPPED);
      status.unblock();
    }

    this.logger.info("Successfully unblocked all watches on " + this.execId);
  }

  public boolean isWatchCancelled() {
    return this.cancelWatch;
  }

  public abstract void stopWatcher();
}
