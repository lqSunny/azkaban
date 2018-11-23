package com.datapps.zebra.workflow.execapp.jmx;

import com.datapps.zebra.workflow.event.Event;
import com.datapps.zebra.workflow.event.EventData;
import com.datapps.zebra.workflow.event.EventListener;
import com.datapps.zebra.workflow.execapp.JobRunner;
import com.datapps.zebra.workflow.executor.ExecutableNode;
import com.datapps.zebra.workflow.executor.Status;
import com.datapps.zebra.workflow.utils.Props;
import org.apache.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Responsible keeping track of job related MBean attributes through listening
 * to job related events.
 *
 * @author hluu
 */
public class JmxJobMBeanManager implements JmxJobMXBean, EventListener {

  private static final Logger logger = Logger
      .getLogger(JmxJobMBeanManager.class);

  private static final JmxJobMBeanManager INSTANCE = new JmxJobMBeanManager();

  private final AtomicInteger runningJobCount = new AtomicInteger(0);
  private final AtomicInteger totalExecutedJobCount = new AtomicInteger(0);
  private final AtomicInteger totalFailedJobCount = new AtomicInteger(0);
  private final AtomicInteger totalSucceededJobCount = new AtomicInteger(0);

  private final Map<String, AtomicInteger> jobTypeFailureMap =
      new HashMap<>();

  private final Map<String, AtomicInteger> jobTypeSucceededMap =
      new HashMap<>();

  private boolean initialized;

  private JmxJobMBeanManager() {
  }

  public static JmxJobMBeanManager getInstance() {
    return INSTANCE;
  }

  public void initialize(final Props props) {
    logger.info("Initializing " + getClass().getName());
    this.initialized = true;
  }

  @Override
  public int getNumRunningJobs() {
    return this.runningJobCount.get();
  }

  @Override
  public int getTotalNumExecutedJobs() {
    return this.totalExecutedJobCount.get();
  }

  @Override
  public int getTotalFailedJobs() {
    return this.totalFailedJobCount.get();
  }

  @Override
  public int getTotalSucceededJobs() {
    return this.totalSucceededJobCount.get();
  }

  @Override
  public Map<String, Integer> getTotalSucceededJobsByJobType() {
    return convertMapValueToInteger(this.jobTypeSucceededMap);
  }

  @Override
  public Map<String, Integer> getTotalFailedJobsByJobType() {
    return convertMapValueToInteger(this.jobTypeFailureMap);
  }

  private Map<String, Integer> convertMapValueToInteger(
      final Map<String, AtomicInteger> map) {
    final Map<String, Integer> result = new HashMap<>(map.size());

    for (final Map.Entry<String, AtomicInteger> entry : map.entrySet()) {
      result.put(entry.getKey(), entry.getValue().intValue());
    }

    return result;
  }

  @Override
  public void handleEvent(final Event event) {
    if (!this.initialized) {
      throw new RuntimeException("JmxJobMBeanManager has not been initialized");
    }

    if (event.getRunner() instanceof JobRunner) {
      final JobRunner jobRunner = (JobRunner) event.getRunner();
      final EventData eventData = event.getData();
      final ExecutableNode node = jobRunner.getNode();

      if (logger.isDebugEnabled()) {
        logger.debug("*** got " + event.getType() + " " + node.getId() + " "
            + event.getRunner().getClass().getName() + " status: "
            + eventData.getStatus());
      }

      if (event.getType() == Event.Type.JOB_STARTED) {
        this.runningJobCount.incrementAndGet();
      } else if (event.getType() == Event.Type.JOB_FINISHED) {
        this.totalExecutedJobCount.incrementAndGet();
        if (this.runningJobCount.intValue() > 0) {
          this.runningJobCount.decrementAndGet();
        } else {
          logger.warn("runningJobCount not messed up, it is already zero "
              + "and we are trying to decrement on job event "
              + Event.Type.JOB_FINISHED);
        }

        if (eventData.getStatus() == Status.FAILED) {
          this.totalFailedJobCount.incrementAndGet();
        } else if (eventData.getStatus() == Status.SUCCEEDED) {
          this.totalSucceededJobCount.incrementAndGet();
        }

        handleJobFinishedCount(eventData.getStatus(), node.getType());
      }

    } else {
      logger.warn("((((((((( Got a different runner: "
          + event.getRunner().getClass().getName());
    }
  }

  private void handleJobFinishedCount(final Status status, final String jobType) {
    switch (status) {
      case FAILED:
        handleJobFinishedByType(this.jobTypeFailureMap, jobType);
        break;
      case SUCCEEDED:
        handleJobFinishedByType(this.jobTypeSucceededMap, jobType);
        break;
      default:
    }
  }

  private void handleJobFinishedByType(final Map<String, AtomicInteger> jobTypeMap,
      final String jobType) {

    synchronized (jobTypeMap) {
      AtomicInteger count = jobTypeMap.get(jobType);
      if (count == null) {
        count = new AtomicInteger();
      }

      count.incrementAndGet();
      jobTypeMap.put(jobType, count);
    }
  }
}
