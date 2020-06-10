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

public class GenericZookeeper implements Zookeeper {

	private final String port = "2181";
	private final String host;
	public GenericZookeeper(String host) {
		this.host = host;
	}

	private void init() throws Exception {
		// no-op
	}

	public int start() throws Exception {
		return 0;
	}

	public int stop() throws Exception {
		return 0;
	}

	public void cleanup() throws Exception {
		// no-op
	}

	public String getHost() {
		return host;
	}

	public String getPort() {
		return port;
	}

}