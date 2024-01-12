package org.apache.solr.benchmarks.task;

import org.apache.solr.benchmarks.BenchmarksMain;
import org.apache.solr.benchmarks.ControlledExecutor;
import org.apache.solr.benchmarks.beans.TaskByClass;

import java.util.Map;

public abstract class AbstractTask<T> implements Task<T> {
  protected final TaskByClass taskSpec;
  private final boolean isFiniteTask;

  protected AbstractTask(TaskByClass taskSpec) {
    this(taskSpec, false);
  }
  protected AbstractTask(TaskByClass taskSpec, boolean isFiniteTask) {
    this.isFiniteTask = isFiniteTask;
    if (taskSpec.name == null) {
      throw new IllegalArgumentException(taskSpec + " is invalid, missing task name");
    }
    if (!isFiniteTask && taskSpec.durationSecs == null) {
      throw new IllegalArgumentException("duration-secs should be defined if the task [" + taskSpec.name  + "] is not finite");
    }
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

            () -> {
              if (isFiniteTask && !AbstractTask.this.hasNext()) {
                return null;
              }
              return new ControlledExecutor.CallableWithType<T>() {
                @Override
                public T call() throws Exception {
                  return runAction();
                }

                @Override
                public BenchmarksMain.OperationKey getType() {
                  return null;
                }
              };
            },
            getExecutionListeners());

    controlledExecutor.run();
    return getAdditionalResult();
  }

  /**
   * Whether there are more actions to be executed. Overwrite this if {@link AbstractTask#isFiniteTask} is true
   */
  protected boolean hasNext() {
    return true;
  }
}
