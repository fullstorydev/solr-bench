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
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.solr.benchmarks.beans.Cluster;
import org.apache.solr.benchmarks.beans.IndexBenchmark;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.request.CollectionAdminRequest.Create;
import org.apache.solr.client.solrj.request.CollectionAdminRequest.Delete;
import org.apache.solr.client.solrj.request.ConfigSetAdminRequest;
import org.apache.solr.client.solrj.request.HealthCheckRequest;
import org.apache.solr.client.solrj.request.RequestWriter;
import org.apache.solr.client.solrj.response.CollectionAdminResponse;
import org.apache.solr.client.solrj.response.HealthCheckResponse;
import org.apache.solr.common.cloud.SolrZkClient;
import org.apache.solr.common.cloud.ZkConfigManager;
import org.apache.solr.common.params.ConfigSetParams;
import org.apache.solr.common.params.SolrParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

public class SolrCloud {

  private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  Zookeeper zookeeper;
  public List<SolrNode> nodes = Collections.synchronizedList(new ArrayList());

  private Set<String> configsets = new HashSet<>();

  private Set<String> colls = new HashSet<>();

  final Cluster cluster;
  private final String solrPackagePath;

  public SolrCloud(Cluster cluster, String solrPackagePath) throws Exception {
    this.cluster = cluster;
    this.solrPackagePath = solrPackagePath;
    
    log.info("Provisioning method: " + cluster.provisioningMethod);
  }

  /**
   * A method used for getting ready to set up the Solr Cloud.
   * @throws Exception 
   */
  public void init() throws Exception {
    if ("local".equalsIgnoreCase(cluster.provisioningMethod)) {
      zookeeper = new LocalZookeeper();
      int initValue = zookeeper.start();
      if (initValue != 0) {
        log.error("Failed to start Zookeeper!");
        throw new RuntimeException("Failed to start Zookeeper!");
      }

      ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()/2+1, new ThreadFactoryBuilder().setNameFormat("nodestarter-threadpool").build()); 

      for (int i = 1; i <= cluster.numSolrNodes; i++) {
    	  Callable c = () -> {
    		  SolrNode node = new LocalSolrNode(solrPackagePath, cluster.startupParams, zookeeper);
    		  
    		  try {
	    		  node.init();
	    		  node.start();
	    		  nodes.add(node);
	    		  
	    		  log.info("Nodes started: "+nodes.size());
	    		  
	    		  //try (HttpSolrClient client = new HttpSolrClient.Builder(node.getBaseUrl()).build();) {
	    			  //log.info("Health check: "+ new HealthCheckRequest().process(client) + ", Nodes started: "+nodes.size());
	    		  //} catch (Exception ex) {
	    			//  log.error("Problem starting node: "+node.getBaseUrl());
	    			  //throw new RuntimeException("Problem starting node: "+node.getBaseUrl());
	    		  //}
	    		  return node;
    		  } catch (Exception ex) {
    			  ex.printStackTrace();
    			  log.error("Problem starting node: "+node.getBaseUrl());
    			  return null;
    		  }
    	  };
    	  executor.submit(c);
      }
      
      executor.shutdown();
      executor.awaitTermination(1, TimeUnit.HOURS);

      log.info("Looking for healthy nodes...");
      List<SolrNode> healthyNodes = new ArrayList<>();
      for (SolrNode node: nodes) {
  		try (HttpSolrClient client = new HttpSolrClient.Builder(node.getBaseUrl().substring(0, node.getBaseUrl().length()-1)).build();) {
  			HealthCheckRequest req = new HealthCheckRequest();
  			HealthCheckResponse rsp;
  			try {
  				rsp = req.process(client);
  			} catch (Exception ex) {
  				Thread.sleep(100);
  				rsp = req.process(client);
  			}
  			if (rsp.getNodeStatus().equalsIgnoreCase("ok") == false) {
  				log.error("Couldn't start node: "+node.getBaseUrl());
  				throw new RuntimeException("Couldn't start node: "+node.getBaseUrl());
  			} else {
  				healthyNodes.add(node);
  			}
  		} catch (Exception ex) {
  			ex.printStackTrace();
  			log.error("Problem starting node: "+node.getBaseUrl());
  		}
      }
      
      this.nodes = healthyNodes;
      log.info("Healthy nodes: "+healthyNodes.size());
      
    } else if ("terraform-gcp".equalsIgnoreCase(cluster.provisioningMethod)) {
    	System.out.println("Solr nodes: "+getSolrNodesFromTFState());
    	System.out.println("ZK node: "+getZkNodeFromTFState());
    	zookeeper = new GenericZookeeper(getZkNodeFromTFState());
    	for (String host: getSolrNodesFromTFState()) {
    		nodes.add(new GenericSolrNode(host));
    	}
    }

  }

  List<String> getSolrNodesFromTFState() throws JsonMappingException, JsonProcessingException, IOException {
	  List<String> out = new ArrayList<String>();
	  Map<String, Object> tfstate = new ObjectMapper().readValue(FileUtils.readFileToString(new File("terraform/terraform.tfstate"), "UTF-8"), Map.class);
	  for (Map m: (List<Map>)((Map)((Map)tfstate.get("outputs")).get("solr_node_details")).get("value")) {
		  out.add(m.get("name").toString());
	  }
	  return out;
  }

  String getZkNodeFromTFState() throws JsonMappingException, JsonProcessingException, IOException {
	  Map<String, Object> tfstate = new ObjectMapper().readValue(FileUtils.readFileToString(new File("terraform/terraform.tfstate"), "UTF-8"), Map.class);
	  for (Map m: (List<Map>)((Map)((Map)tfstate.get("outputs")).get("zookeeper_details")).get("value")) {
		  return m.get("name").toString();
	  }
	  throw new RuntimeException("Couldn't get ZK node from tfstate");
  }

  /**
   * A method used for creating a collection.
   * 
   * @param collectionName
   * @param configName
   * @param shards
   * @param replicas
   * @throws Exception 
   */
  public void createCollection(IndexBenchmark.Setup setup, String collectionName) throws Exception {
	  try (HttpSolrClient hsc = createClient()) {
		  Create create;
		  if (setup.replicationFactor != null) {
			  create = Create.createCollection(collectionName, setup.configset, setup.shards, setup.replicationFactor);
		  } else {
			  create = Create.createCollection(collectionName, setup.configset, setup.shards,
					  setup.nrtReplicas, setup.tlogReplicas, setup.pullReplicas);
		  }
		  CollectionAdminResponse resp;
		  if (setup.collectionCreationParams != null && setup.collectionCreationParams.isEmpty()==false) {
			  resp = new CreateWithAdditionalParameters(create, collectionName, setup.collectionCreationParams).process(hsc);
		  } else {
			  resp = create.process(hsc);
		  }
		  log.info("Collection created: "+ resp.jsonStr());
      }
	  colls.add(setup.collection);
  }

  HttpSolrClient createClient() {
    return new HttpSolrClient.Builder(nodes.get(0).getBaseUrl()).build();
  }

  /**
   * A method for deleting a collection.
   * 
   * @param collectionName
   * @throws Exception 
   */
  public void deleteCollection(String collectionName) throws Exception {
	  try (HttpSolrClient hsc = createClient()) {
		  Delete delete = Delete.deleteCollection(collectionName); //Create.createCollection(collectionName, shards, replicas);
		  CollectionAdminResponse resp = delete.process(hsc);
		  log.info("Collection delete: "+resp.getCollectionStatus());
	  }
  }

  /**
   * A method used to get the zookeeper url for communication with the solr
   * cloud.
   * 
   * @return String
   */
  public String getZookeeperUrl() {
    return zookeeper.getHost() + ":" + zookeeper.getPort();
  }

  /**
   * A method used for shutting down the solr cloud.
   * @throws Exception 
   */
  public void shutdown(boolean cleanup) throws Exception {
    if (cluster.provisioningMethod.equalsIgnoreCase("local")) {
      for (String coll : colls) {
          try (HttpSolrClient hsc = createClient()) {
            try {
              CollectionAdminRequest.deleteCollection(coll).process(hsc);
            } catch (Exception e) {
              //failed but continue
            }
          }
      }

      //cleanup configsets created , if any
      for (String configset : configsets) {
        try (HttpSolrClient hsc = createClient()) {
          try {
            new ConfigSetAdminRequest.Delete().setConfigSetName(configset).process(hsc);
          } catch (Exception e) {
            //failed but continue
            e.printStackTrace();
          }
        }
      }

      for (SolrNode node : nodes) {
       node.stop();
        if (cleanup) {
          node.cleanup();
        }
      }
      if (cleanup) {
        zookeeper.stop();
        zookeeper.cleanup();
      }
    }
  }

  public void uploadConfigSet(String configset) throws Exception {
    if(configset==null || configsets.contains(configset)) return;

    log.info("Configset: " +configset+
            " does not exist. creating... ");

    File f = new File(configset + ".zip");

    if (!f.exists()) {
      throw new RuntimeException("Could not find configset file: " + configset);
    }

    try (HttpSolrClient hsc = createClient()) {
      ConfigSetAdminRequest.Create create = new ConfigSetAdminRequest.Create() {

        @Override
        public RequestWriter.ContentWriter getContentWriter(String expectedType) {

          return new RequestWriter.ContentWriter() {
            @Override
            public void write(OutputStream os) throws IOException {

              try (InputStream is = new FileInputStream(f)) {
                IOUtils.copy(is, os);
              }
            }

            @Override
            public String getContentType() {
              return "Content-Type:application/octet-stream";
            }
          };
        }

        @Override
        public SolrParams getParams() {
          super.action = ConfigSetParams.ConfigSetAction.UPLOAD;
          return super.getParams();
        }
      };
      
      try {
	      ConfigSetAdminRequest.Delete delete = new ConfigSetAdminRequest.Delete();
	      delete.setConfigSetName(configset);
	      delete.process(hsc);
      } catch (Exception ex) {
    	  log.warn("Exception trying to delete configset", ex);
      }
      create.setMethod(SolrRequest.METHOD.POST);
      create.setConfigSetName(configset);
      create.process(hsc);
      configsets.add(configset);

      // This is a hack. We want all configsets to be trusted. Hence, unsetting the data on the znode that has trusted=false.
      try (SolrZkClient zkClient = new SolrZkClient(zookeeper.getHost() + ":" + zookeeper.getPort(), 100)) {
        zkClient.setData(ZkConfigManager.CONFIGS_ZKNODE + "/" + configset, (byte[]) null, true);
      }
      log.info("Configset: " + configset +
              " created successfully ");


    }


  }
}