package org.apache.solr.benchmarks.task;

import org.apache.solr.benchmarks.BenchmarksMain;
import org.apache.solr.benchmarks.ControlledExecutor;
import org.apache.solr.benchmarks.beans.TaskByClass;

import java.util.Map;

public abstract class AbstractTask<T> implements Task<T> {
  protected final TaskByClass taskSpec;

  protected AbstractTask(TaskByClass taskSpec) {
    this.taskSpec = taskSpec;
  }

  @Override
  public final Map<String, Object> runTask() throws Exception {
    int threads = Math.min(taskSpec.minThreads, taskSpec.maxThreads);
    if (threads <= 0) {
      threads = 1;
    }
    ControlledExecutor<T> controlledExecutor = new ControlledExecutor<>(
            taskSpec.name,
            threads,
            taskSpec.durationSecs,
            taskSpec.rpm,
            null,
            0,
            () -> new ControlledExecutor.CallableWithType<>() {
              @Override
              public T call() throws Exception {
                return runAction();
              }

              @Override
              public BenchmarksMain.OperationKey getType() {
                return null;
              }
            },
            getExecutionListeners());

    controlledExecutor.run();
    return getAdditionalResult();
  }
}
