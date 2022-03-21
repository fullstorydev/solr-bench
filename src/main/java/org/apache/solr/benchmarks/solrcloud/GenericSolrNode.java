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

import java.lang.invoke.MethodHandles;

import org.apache.solr.benchmarks.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenericSolrNode implements SolrNode {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  private final String host;
  private final String port = "8983";

  public GenericSolrNode(String host) throws Exception {
    this.host = host;
  }

  @Override
  public void provision() throws Exception {
    // no-op
  }

  @Override
  public void init() throws Exception {
	  // no-op
  }

  @Override
  public int start() throws Exception {
	  return 0;
  }

  @Override
  public int stop() throws Exception {
	  return 0;
  }
  
  @Override
  public int restart() throws Exception {
	  Util.execute("./restartsolr.sh " + host, Util.getWorkingDir());
	  return 0;
  }


  @Override
  public String getBaseUrl() {
    return "http://"+host+":" + port + "/solr/";
  }

  @Override
  public void cleanup() throws Exception {
    // no-op
  }

  @Override
  public String getNodeName() {
	  return host + ":" + port;
  }

  @Override
  public int pause(int seconds) throws Exception {
	  throw new UnsupportedOperationException();
  }

}