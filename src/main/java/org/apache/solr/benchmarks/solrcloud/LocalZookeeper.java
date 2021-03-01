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

import org.apache.commons.io.FileUtils;
import org.apache.log4j.Logger;
import org.apache.solr.benchmarks.Util;

import java.io.File;
import java.io.FilenameFilter;
import java.net.URL;

/**
 * This class provides blueprint for Zookeeper Node.
 */
public class LocalZookeeper implements Zookeeper {

  public final static Logger logger = Logger.getLogger(LocalZookeeper.class);

  public static String zooCommand;
  public static String zooCleanCommand;
  private String releaseName;

  static {
    zooCommand = "bin" + File.separator + "zkServer.sh ";
  }

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

    logger.info("Installing Zookeeper Node ...");

    File base = new File(Util.ZOOKEEPER_DIR);
    if (!base.exists()) {
      base.mkdir();
      base.setExecutable(true);
    }
    String[] fName = new String[1];
    new File(Util.DOWNLOAD_DIR).list(new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        if(name.startsWith("apache-zookeeper") && name.endsWith(".tar.gz"))
          fName[0] = name;
        return false;
      }
    });
    File release = null;
      if (fName[0] != null) {
          release = new File(Util.DOWNLOAD_DIR, fName[0]);
      } else {
          release = new File(Util.DOWNLOAD_DIR + "zookeeper-" + Util.ZOOKEEPER_RELEASE + ".tar.gz");
      }

    if (!release.exists()) {
      logger.info("Attempting to download zookeeper release ..." + " : " + Util.ZOOKEEPER_RELEASE);

      String fileName = "apache-zookeeper-" + Util.ZOOKEEPER_RELEASE + "-bin.tar.gz";

      FileUtils.copyURLToFile(
          new URL(Util.ZOOKEEPER_DOWNLOAD_URL + "zookeeper-" + Util.ZOOKEEPER_RELEASE + File.separator + fileName),
          new File(Util.DOWNLOAD_DIR + fileName));
    } else {
      logger.info("Release " +release.getName()+
              " present nothing to download ..." );
    }

    releaseName = release.getName().replace(".tar.gz", "");
    File urelease = new File(Util.DOWNLOAD_DIR , releaseName);
    if (!urelease.exists()) {

      Util.execute("tar -xf " + release.getAbsolutePath() + " -C "
          + Util.ZOOKEEPER_DIR, Util.ZOOKEEPER_DIR);

      Util.execute(
          "mv " + Util.ZOOKEEPER_DIR + releaseName  + File.separator + "conf"
              + File.separator + "zoo_sample.cfg " + Util.ZOOKEEPER_DIR + releaseName +
                  File.separator + "conf" + File.separator + "zoo.cfg",
              Util.ZOOKEEPER_DIR);

    } else {
      logger.info("Release extracted already nothing to do ..." + " : " + Util.ZOOKEEPER_RELEASE);
    }
  }

  public int start() throws Exception {
    new File(Util.ZOOKEEPER_DIR + releaseName + File.separator + zooCommand).setExecutable(true);
    return Util.execute(
        Util.ZOOKEEPER_DIR + releaseName + "/" + zooCommand + " start",
        Util.ZOOKEEPER_DIR + releaseName + File.separator);
  }

  public int stop() throws Exception {
    new File(Util.ZOOKEEPER_DIR + releaseName + File.separator + zooCommand).setExecutable(true);
    return Util.execute(
        Util.ZOOKEEPER_DIR + releaseName + File.separator + zooCommand + " stop",
        Util.ZOOKEEPER_DIR + releaseName + File.separator);
  }

  public void cleanup() throws Exception {
    new File(Util.ZOOKEEPER_DIR + releaseName + File.separator + zooCommand).setExecutable(true);
    Util.execute("rm -r -f " + Util.ZOOKEEPER_DIR, Util.ZOOKEEPER_DIR);
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

}