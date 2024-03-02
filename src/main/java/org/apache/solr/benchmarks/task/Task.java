package org.apache.solr.benchmarks.task;

import org.apache.solr.benchmarks.BenchmarksMain;
import org.apache.solr.benchmarks.ControlledExecutor;
import org.apache.solr.benchmarks.ControlledExecutor.ExecutionListener;
import org.apache.solr.benchmarks.beans.TaskByClass;
import org.apache.solr.benchmarks.beans.TaskType;
import org.apache.solr.benchmarks.solrcloud.SolrCloud;

import java.io.IOException;
import java.util.Map;

/**
 * A Task would invoke runAction repeatedly based on the rpm and duration defined in {@link TaskByClass}
 *
 * @param <T> The response type of each executed Action of such Task
 */
public interface Task<T> {

  Map<String, Object> runTask() throws Exception;

  T runAction() throws Exception;

  /**
   * Listeners to be registered and notified when each "action" of the task is completed.
   * <p>
   * The implementation of the listener is expected to record stats specific to the task and
   * later reporting the stats via @
   * @return  an array of execution listeners to be registered and notified
   */
  ExecutionListener<BenchmarksMain.OperationKey, T>[] getExecutionListeners();

  /**
   * Additional results (on top of basics like task duration) from this task to be included in the benchmarking final result (json)
   * <p>
   * Stats are to be recorded and accumulated by execution listener by the concrete implementation of the Task
   * @return  stats result as a map
   */
  Map<String, Object> getAdditionalResult();

  /**
   * cleanup etc
   */
  void postTask() throws IOException;
}
