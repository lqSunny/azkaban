package com.datapps.zebra.workflow.execapp.jmx;

import com.datapps.zebra.workflow.jmx.DisplayName;

public interface JmxJobCallbackMBean {

  @DisplayName("OPERATION: getNumJobCallbacks")
  public long getNumJobCallbacks();

  @DisplayName("OPERATION: getNumSuccessfulJobCallbacks")
  public long getNumSuccessfulJobCallbacks();

  @DisplayName("OPERATION: getNumFailedJobCallbacks")
  public long getNumFailedJobCallbacks();

  @DisplayName("OPERATION: getNumActiveJobCallbacks")
  public long getNumActiveJobCallbacks();

}
