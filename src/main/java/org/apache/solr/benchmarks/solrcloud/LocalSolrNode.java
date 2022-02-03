/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.solr.benchmarks.solrcloud;

import java.io.File;
import java.lang.invoke.MethodHandles;
import java.util.List;
import java.util.UUID;

import org.apache.commons.io.FileUtils;
import org.apache.solr.benchmarks.Util;
import org.apache.solr.benchmarks.beans.Cluster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalSolrNode implements SolrNode {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  public String baseDirectory;
  public final String port;
  private String binDirectory;
  private final Zookeeper zookeeper;
  private final String solrPackagePath;
  private final String startupParams;
  private final List<String> startupParamsOverrides;
  private final int nodeIndex;
  
  public LocalSolrNode(String solrPackagePath, int nodeIndex, String port, Cluster clusterConfig, Zookeeper zookeeper)
      throws Exception {
    this.zookeeper = zookeeper;
    this.solrPackagePath = solrPackagePath;
    this.startupParams = clusterConfig.startupParams;
    this.startupParamsOverrides = clusterConfig.startupParamsOverrides;
    this.nodeIndex = nodeIndex;
    this.port = port;
  }

    @Override
  public void provision() throws Exception {
    // no-op
  }

  /**
   * A method used for initializing the solr node.
   * @throws Exception 
   * @throws InterruptedException 
   */
  @Override
  public void init() throws Exception {
    log.debug("Installing Solr Node ...");

    this.baseDirectory = Util.SOLR_DIR + UUID.randomUUID().toString() + File.separator;
    this.binDirectory = this.baseDirectory;

    try {

      log.debug("Checking if SOLR node directory exists ...");

      File node = new File(binDirectory);

      if (!node.exists()) {

        log.debug("Node directory does not exist, creating it ...");
        node.mkdir();
      } 

    } catch (Exception e) {
      log.error(e.getMessage());
      throw new Exception(e.getMessage());
    }

    Util.extract(solrPackagePath, binDirectory);
    for (File file : new File(this.binDirectory).listFiles()) {
      if (file.isDirectory() && file.getName().startsWith("solr-")) {
        this.binDirectory = file.getPath() + File.separator + "bin" + File.separator;
      }
    }



  }

  @Override
  public int start() throws Exception {
    long start = 0;
    long end = 0;
    int returnValue = 0;

    start = System.currentTimeMillis();
    new File(binDirectory + "solr").setExecutable(true);
	
    String startup = (startupParams != null ? startupParams : "");
    if (startupParamsOverrides != null && startupParamsOverrides.size() >= nodeIndex && startupParamsOverrides.get(nodeIndex-1).trim().length()>0) startup = startupParamsOverrides.get(nodeIndex-1);

    returnValue = Util.execute(binDirectory + "solr start -force -Dhost=localhost " + "-p " + port + " "
			+ startup + " -V " + " -z " + zookeeper.getHost() + ":"
			+ zookeeper.getPort(), binDirectory);

    end = System.currentTimeMillis();

    log.info("Time taken for the node start activity is: " + (end - start) + " millisecond(s)");
    return returnValue;
  }

  @Override
  public int stop() throws Exception {
	  long start = 0;
	  long end = 0;
	  int returnValue = 0;

	  start = System.currentTimeMillis();
	  new File(binDirectory + "solr").setExecutable(true);
	  returnValue = Util.execute(
			  binDirectory + "solr stop -p " + port + " -z " + zookeeper.getHost() + ":" + zookeeper.getPort() + " -force",
			  binDirectory);
	  end = System.currentTimeMillis();

	  log.info("Time taken for the node stop activity is: " + (end - start) + " millisecond(s)");
	  return returnValue;
  }

  /**
   * A method used for getting the URL for the solr node for communication. 
   * @return
   */
  @Override
  public String getBaseUrl() {
    return "http://localhost:" + port + "/solr/";
  }

  /**
   * A method used for cleaning up the files for the solr node. 
   * @throws Exception 
   */
  @Override
  public void cleanup() throws Exception {
	  Util.execute("rm -r -f " + baseDirectory, baseDirectory);
  }

  @Override
  public String getNodeName() {
	  return "localhost:"+port;
  }
  
  public void restart() throws Exception {
	    long start = System.currentTimeMillis();
	    stop();
	    start();
	    long end = System.currentTimeMillis();

	    log.info("Time taken for the node restart is: " + (end - start)/1000.0 + " seconds");
  }

  @Override
  public int pause(int seconds) throws Exception {
	  long start = 0;
	  long end = 0;
	  int returnValue = 0;

	  start = System.currentTimeMillis();
	  new File(binDirectory + "solr").setExecutable(true);
	  
	  String pidFile = binDirectory + "solr-"+port+".pid";
	  String pid = FileUtils.readFileToString(new File(pidFile));
	  log.info("PID: "+pid);
	  returnValue = Util.execute("kill -STOP "+pid, binDirectory);
	  Thread.sleep(seconds*1000);
	  returnValue = Util.execute("kill -CONT "+pid, binDirectory);
	  end = System.currentTimeMillis();

	  log.info("Time taken for the pause is: " + (end - start)/1000.0 + " second(s)");
	  return returnValue;
  }
}