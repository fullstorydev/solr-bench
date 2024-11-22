package org.apache.solr.benchmarks.task;

import org.apache.solr.benchmarks.BenchmarksMain;
import org.apache.solr.benchmarks.ControlledExecutor;
import org.apache.solr.benchmarks.beans.TaskByClass;

import java.util.Map;

/**
 *
 * @param <T> The response type of each executed Action of such Task
 */
public abstract class AbstractTask<T> implements Task<T> {
  protected final TaskByClass taskSpec;
  private final Long maxExecution; //max execution count, if defined, the runAction should only be invoked up to this number

  protected AbstractTask(TaskByClass taskSpec) {
    this(taskSpec, null);
  }
  protected AbstractTask(TaskByClass taskSpec, Long maxExecution) {
    this.maxExecution = maxExecution;
    if (taskSpec.name == null) {
      throw new IllegalArgumentException(taskSpec + " is invalid, missing task name");
    }
    if (maxExecution == null && taskSpec.durationSecs == null) {
      throw new IllegalArgumentException("duration-secs should be defined if the task [" + taskSpec.name  + "] has no max execution set");
    }
    this.taskSpec = taskSpec;
  }

  @Override
  public final Map<String, Object> runTask() throws Exception {
    int threads = Math.min(taskSpec.minThreads, taskSpec.maxThreads);
    if (threads <= 0) {
      threads = 1;
    }
    try {
      ControlledExecutor<T> controlledExecutor = new ControlledExecutor<>(
              taskSpec.name,
              threads,
              taskSpec.durationSecs,
              taskSpec.rpm,
              maxExecution,
              0,

              () -> {
                if (!AbstractTask.this.hasNext()) {
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
    } finally {
      postTask();
    }
  }

  /**
   * Whether there are more actions to be executed. Overwrite this if the task should end on certain condition
   */
  protected boolean hasNext() {
    return true;
  }
}
