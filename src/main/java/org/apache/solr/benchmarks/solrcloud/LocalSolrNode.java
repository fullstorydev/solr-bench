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
import java.util.UUID;

import org.apache.solr.benchmarks.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LocalSolrNode implements SolrNode {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  public String baseDirectory;
  public String port;
  private String nodeDirectory;
  private final Zookeeper zookeeper;
  private final String solrPackagePath;
  private final String startupParams;
  
  public LocalSolrNode(String solrPackagePath, String startupParams, Zookeeper zookeeper)
      throws Exception {
    this.zookeeper = zookeeper;
    this.solrPackagePath = solrPackagePath;
    this.startupParams = startupParams;
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

    this.port = String.valueOf(Util.getFreePort());

    this.baseDirectory = Util.SOLR_DIR + UUID.randomUUID().toString() + File.separator;
    this.nodeDirectory = this.baseDirectory;

    try {

      log.debug("Checking if SOLR node directory exists ...");

      File node = new File(nodeDirectory);

      if (!node.exists()) {

        log.debug("Node directory does not exist, creating it ...");
        node.mkdir();
      } 

    } catch (Exception e) {
      log.error(e.getMessage());
      throw new Exception(e.getMessage());
    }

    Util.extract(solrPackagePath, nodeDirectory);

    this.nodeDirectory = new File(this.nodeDirectory).listFiles()[0] + File.separator + "bin" + File.separator;
  }

  @Override
  public int start() throws Exception {
    long start = 0;
    long end = 0;
    int returnValue = 0;

    start = System.currentTimeMillis();
    new File(nodeDirectory + "solr").setExecutable(true);
    	returnValue = Util.execute(nodeDirectory + "solr start -force -Dhost=localhost " + "-p " + port + " " + (startupParams!=null?startupParams:"") + " " + " -z "
    			+ zookeeper.getHost() + ":" + zookeeper.getPort(), nodeDirectory);

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
	  new File(nodeDirectory + "solr").setExecutable(true);
	  returnValue = Util.execute(
			  nodeDirectory + "solr stop -p " + port + " -z " + zookeeper.getHost() + ":" + zookeeper.getPort() + " -force",
			  nodeDirectory);
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
}