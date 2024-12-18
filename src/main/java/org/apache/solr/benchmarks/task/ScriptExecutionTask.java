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
  private final int maxRetry; //max retry on non zero exit code, no retry on exception
  private final int RETRY_DELAY = 10000; //10 secs delay

  public ScriptExecutionTask(TaskByClass taskSpec, SolrCloud solrCloud) {
    super(taskSpec, 1L); //does not support repeated execution
    script = (String) taskSpec.params.get("script");
    if (script == null) {
      throw new IllegalArgumentException("script param of script execution task should not be null!");
    }
    scriptParameters = (List<?>) taskSpec.params.get("script-params");
    maxRetry = (int) taskSpec.params.getOrDefault("max-retry", 0);
  }

  @Override
  public ExecutionResult runAction() throws Exception {
    String cmd = script;
    if (scriptParameters != null) {
      cmd += (" " + Strings.join(scriptParameters, ' '));
    }
    boolean keepRunning = true;
    int retryCount = 0;
    int exitCode = 0;
    while (keepRunning) {
      log.info("Executing cmd {}", cmd);
      exitCode = Util.execute(cmd, Util.getWorkingDir());
      if (exitCode != 0) {
        log.warn("Cmd exit with non-zero code {}", exitCode);
        if (++ retryCount <= maxRetry) {
          log.info("Retry #{} after {} millisecond delay", retryCount, RETRY_DELAY);
          Thread.sleep(RETRY_DELAY);
        } else {
          log.info("Exhausted all retries. Exiting!");
          keepRunning = false;
        }
      } else {
        log.info("Cmd exit with zero code.");
        keepRunning = false;
      }
    }
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
    private final int exitCode;

    public ExecutionResult(int exitCode) {
      this.exitCode = exitCode;
    }

    public int getExitCode() {
      return exitCode;
    }
  }
}

