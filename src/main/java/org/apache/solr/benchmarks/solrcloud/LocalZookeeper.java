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

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.solr.benchmarks.Util;

/**
 * This class provides blueprint for Zookeeper Node.
 */
public class LocalZookeeper implements Zookeeper {

  public final static Logger log = Logger.getLogger(LocalZookeeper.class);

  //public static String zooCleanCommand;

  public static final String ZK_TARBALL = Util.WORK_DIRECTORY + "apache-zookeeper-3.6.3-bin.tar.gz";
  public static final String ZK_DIR = Util.RUN_DIR + "apache-zookeeper-3.6.3-bin";
  public static final String ZK_COMMAND = "bin/zkServer.sh";

  /**
   * Constructor.
   *
   * @throws Exception
   */
  LocalZookeeper() throws Exception {
    super();
    this.init();
  }

  /**
   * A method for setting up zookeeper node.
   *
   * @throws Exception
   */
  private void init() throws Exception {
    log.info("Installing Zookeeper Node ...");

    log.info("ZK Tarball is here: " + ZK_TARBALL);
    
    if (new File(ZK_TARBALL).exists()) {
        Util.execute("tar -xf " + ZK_TARBALL + " -C "
                + Util.RUN_DIR, Util.RUN_DIR);
        log.info("After untarring, ZK dir is here: " + ZK_DIR);
        Util.execute("cp "+ZK_DIR+"/conf/zoo_sample.cfg "+ZK_DIR+"/conf/zoo.cfg", Util.RUN_DIR);

        String jmxEnvs = "JMXLOCALONLY=false\n" + 
        		"JMXDISABLE=false\n" + 
        		"JMXPORT=4048\n" + 
        		"JMXAUTH=false\n" + 
        		"JMXSSL=false";
        FileUtils.writeStringToFile(new File(ZK_DIR+"/conf/zookeeper-env.sh"), jmxEnvs, "UTF-8");
    } else {
    	throw new RuntimeException("ZK tarball not found at: " + ZK_TARBALL);
    }
  }

  public int start() throws Exception {
    return Util.execute(ZK_DIR + "/" + ZK_COMMAND + " start", ZK_DIR);
  }

  public int stop() throws Exception {
	  return Util.execute(ZK_DIR + "/" + ZK_COMMAND + " stop", ZK_DIR);
  }

  public void cleanup() throws Exception {
    Util.execute("rm -r -f " + ZK_DIR, Util.RUN_DIR);
    try {
      Util.execute("rm -r -f /tmp/zookeeper/", "/tmp/zookeeper/");
    } catch (Exception e) {
      //the tmp directory may not exist ignore
    }
  }

  /**
   * A method for getting the zookeeper IP.
   *
   * @return
   */
  public String getHost() {
    return "localhost";
  }

  /**
   * A method for getting the zookeeper Port.
   *
   * @return
   */
  public String getPort() {
    return "2181";
  }

  @Override
  public String getAdminPort() {
	  // TODO Auto-generated method stub
	  return "8080";
  }

    public String getChroot() {
        return null;
    }

}