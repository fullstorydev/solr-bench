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

import org.apache.solr.benchmarks.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class ExternalSolrNode extends GenericSolrNode {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
  private final String restartScript;
  private final String protocol;

  public ExternalSolrNode(String host, int port, String user, String restartScript, String protocol) {
    super(host, port, user);
    this.restartScript = restartScript;
    this.protocol = protocol;
  }

  @Override
  public int restart() throws Exception {
    return Util.execute(restartScript + " " + host + " " + (user != null ? user : ""), Util.getWorkingDir());
  }

  @Override
  public String getBaseUrl() {
    return protocol + "://"+host+":" + port + "/solr/";
  }

  @Override
  public String getHostUrl() {
    return protocol + "://"+host;
  }


  @Override
  public String toString() {
    return "ExternalSolrNode{" +
            "host='" + host + '\'' +
            ", port=" + port +
            '}';
  }
}
