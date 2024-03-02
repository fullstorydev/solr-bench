package org.apache.solr.benchmarks.task;

import org.apache.logging.log4j.util.Strings;
import org.apache.solr.benchmarks.BenchmarksMain;
import org.apache.solr.benchmarks.ControlledExecutor;
import org.apache.solr.benchmarks.Util;
import org.apache.solr.benchmarks.beans.TaskByClass;
import org.apache.solr.benchmarks.solrcloud.SolrCloud;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A task to execute the script supplied. Does not support concurrent or repeated executions at this moment.
 */
public class ScriptExecutionTask extends AbstractTask<ScriptExecutionTask.ExecutionResult> {
  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final String script;
  private final List<?> scriptParameters;
  private AtomicBoolean actionSubmitted = new AtomicBoolean(false);

  public ScriptExecutionTask(TaskByClass taskSpec, SolrCloud solrCloud) {
    super(taskSpec, true); //does not support repeated execution
    script = (String) taskSpec.params.get("script");
    if (script == null) {
      throw new IllegalArgumentException("script param of script execution task should not be null!");
    }
    scriptParameters = (List<?>) taskSpec.params.get("script-params");
  }

  @Override
  public ExecutionResult runAction() throws Exception {
    String cmd = script;
    if (scriptParameters != null) {
      cmd += (" " + Strings.join(scriptParameters, ' '));
    }
    log.info("Executing cmd {}", cmd);
    int exitCode = Util.execute(cmd, Util.getWorkingDir());
    return new ExecutionResult(exitCode);
  }

  @Override
  public ControlledExecutor.ExecutionListener<BenchmarksMain.OperationKey, ExecutionResult>[] getExecutionListeners() {
    return new ControlledExecutor.ExecutionListener[0];
  }

  @Override
  public Map<String, Object> getAdditionalResult() {
    return null;
  }

  @Override
  public void postTask() throws IOException {

  }


  public static class ExecutionResult {
    private int exitCode;

    public ExecutionResult(int exitCode) {
      this.exitCode = exitCode;
    }

    public int getExitCode() {
      return exitCode;
    }
  }

  @Override
  protected boolean hasNext() {
    return !actionSubmitted.getAndSet(true);
  }
}

