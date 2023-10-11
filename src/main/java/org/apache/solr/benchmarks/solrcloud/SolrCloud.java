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
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.solr.benchmarks.Util;
import org.apache.solr.benchmarks.beans.Cluster;
import org.apache.solr.benchmarks.beans.IndexBenchmark;
import org.apache.solr.client.solrj.SolrRequest;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.request.CollectionAdminRequest.Create;
import org.apache.solr.client.solrj.request.CollectionAdminRequest.Delete;
import org.apache.solr.client.solrj.request.CollectionAdminRequest.OverseerStatus;
import org.apache.solr.client.solrj.request.ConfigSetAdminRequest;
import org.apache.solr.client.solrj.request.HealthCheckRequest;
import org.apache.solr.client.solrj.request.RequestWriter;
import org.apache.solr.client.solrj.response.CollectionAdminResponse;
import org.apache.solr.client.solrj.response.HealthCheckResponse;
import org.apache.solr.cloud.ZkConfigSetService;
import org.apache.solr.common.cloud.SolrZkClient;
import org.apache.solr.common.cloud.ZkStateReader;
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
  public List<SolrNode> nodes = Collections.synchronizedList(new ArrayList()); //all nodes

  public enum NodeRole {
    DATA, COORDINATOR, OVERSEER
  }

  //Node list by role, though unlikely, a node can appear in multiple lists, ie a node with multiple roles
  public Map<NodeRole, List<? extends SolrNode>> nodesByRole = new HashMap<>();

  private Set<String> configsets = new HashSet<>();

  private Set<String> colls = new HashSet<>();

  final Cluster cluster;
  private final String solrPackagePath;
  private final boolean shouldUploadConfigSet;

  public SolrCloud(Cluster cluster, String solrPackagePath) throws Exception {
    this.cluster = cluster;
    this.solrPackagePath = solrPackagePath;
    this.shouldUploadConfigSet = !"external".equalsIgnoreCase(cluster.provisioningMethod);
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

      ExecutorService executor = Executors.newFixedThreadPool(1, new ThreadFactoryBuilder().setNameFormat("nodestarter-threadpool").build()); 
    		  //Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()/2+1, new ThreadFactoryBuilder().setNameFormat("nodestarter-threadpool").build()); 

      int basePort = Util.getFreePort(cluster.numSolrNodes);

      for (int i = 1; i <= cluster.numSolrNodes; i++) {
    	  int nodeIndex = i;
    	  Callable c = () -> {
    		  SolrNode node = new LocalSolrNode(solrPackagePath, nodeIndex, String.valueOf(basePort + nodeIndex - 1), cluster.startupParams, cluster.startupParamsOverrides, zookeeper);
    		  
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
      
      try (CloudSolrClient client = new CloudSolrClient.Builder(Arrays.asList(getZookeeperUrl()), Optional.ofNullable(getZookeeperChroot())).build();) {
	      log.info("Cluster state: " + client.getClusterStateProvider().getClusterState());
	      log.info("Overseer: " + client.request(new OverseerStatus()));
      }

      
    } else if ("terraform-gcp".equalsIgnoreCase(cluster.provisioningMethod)) {
    	System.out.println("Solr nodes: "+getSolrNodesFromTFState());
    	System.out.println("ZK node: "+getZkNodeFromTFState());
    	zookeeper = new GenericZookeeper(getZkNodeFromTFState());
    	for (String host: getSolrNodesFromTFState()) {
    		nodes.add(new GenericSolrNode(host, cluster.terraformGCPConfig.get("user").toString()));
    	}
    } else if ("vagrant".equalsIgnoreCase(cluster.provisioningMethod)) {
    	System.out.println("Solr nodes: "+getSolrNodesFromVagrant());
    	System.out.println("ZK node: "+getZkNodeFromVagrant());
    	zookeeper = new GenericZookeeper(getZkNodeFromVagrant());
    	for (String host: getSolrNodesFromVagrant()) {
    		nodes.add(new GenericSolrNode(host, null)); // TODO fix username for vagrant
    	}
    } else if ("external".equalsIgnoreCase(cluster.provisioningMethod)) {
        log.info("ZK node: " + cluster.externalSolrConfig.zkHost);
        String[] tokens = cluster.externalSolrConfig.zkHost.split(":");
        zookeeper = new GenericZookeeper(tokens[0], tokens.length > 1 ? Integer.parseInt(tokens[1]) : null, cluster.externalSolrConfig.zkAdminPort, cluster.externalSolrConfig.zkChroot);

        try (CloudSolrClient client = new CloudSolrClient.Builder(Arrays.asList(cluster.externalSolrConfig.zkHost), Optional.ofNullable(cluster.externalSolrConfig.zkChroot)).build()) {
          for (String liveNode : client.getClusterStateProvider().getLiveNodes()) {
            nodes.add(new ExternalSolrNode(
                    liveNode.split("_solr")[0].split(":")[0],
                    Integer.valueOf(liveNode.split("_solr")[0].split(":")[1]),
                    cluster.externalSolrConfig.sshUserName,
                    cluster.externalSolrConfig.restartScript));
          }
          List<SolrNode> dataNodes = getExternalDataNodes(client).stream().map(dataNode -> new ExternalSolrNode(
                  dataNode.split("_solr")[0].split(":")[0],
                  Integer.valueOf(dataNode.split("_solr")[0].split(":")[1]),
                  cluster.externalSolrConfig.sshUserName,
                  cluster.externalSolrConfig.restartScript)).collect(Collectors.toList());
          if (!dataNodes.isEmpty()) {
            nodesByRole.put(NodeRole.DATA, dataNodes);
          }
          List<SolrNode> queryNodes = getExternalQueryNodes(client).stream().map(queryNode -> new ExternalSolrNode(
                  queryNode.split("_solr")[0].split(":")[0],
                  Integer.valueOf(queryNode.split("_solr")[0].split(":")[1]),
                  cluster.externalSolrConfig.sshUserName,
                  cluster.externalSolrConfig.restartScript)).collect(Collectors.toList());
          if (!queryNodes.isEmpty()) {
            nodesByRole.put(NodeRole.COORDINATOR, queryNodes);
          }
          List<SolrNode> overseerNodes = getExternalPreferredOverseerNodes(client).stream().map(overseerNode -> new ExternalSolrNode(
                  overseerNode.split("_solr")[0].split(":")[0],
                  Integer.valueOf(overseerNode.split("_solr")[0].split(":")[1]),
                  cluster.externalSolrConfig.sshUserName,
                  cluster.externalSolrConfig.restartScript)).collect(Collectors.toList());
          if (!queryNodes.isEmpty()) {
            nodesByRole.put(NodeRole.OVERSEER, overseerNodes);
          }
        }
        log.info("Cluster initialized with nodes: " + nodes + ", zkHost: " + zookeeper + ", nodes by role: " + nodesByRole);
    }


  }

  private ZkStateReader getZkStateReader() {
      SolrZkClient zkClient = new SolrZkClient.Builder().withUrl(getZookeeperUrl()).build();
      return new ZkStateReader(zkClient);
  }

    private Collection<String> getExternalDataNodes(CloudSolrClient client) {
        try {
            if (getZkStateReader().getZkClient().exists("/node_roles/data/on", true)) {
                List<String> liveNodes = new ArrayList(client.getClusterStateProvider().getLiveNodes());
                liveNodes.retainAll(getZkStateReader().getZkClient().getChildren("/node_roles/data/on", null, true));
                if (!liveNodes.isEmpty()) {
                    return liveNodes;
                }
            }
        } catch (Exception e) {
            //ok. just use cluster state then
        }
        return client.getClusterStateProvider().getLiveNodes();
    }
    private Collection<String> getExternalQueryNodes(CloudSolrClient client) {
        try {
            if (getZkStateReader().getZkClient().exists("/node_roles/coordinator/on", true)) {
                List<String> liveNodes = new ArrayList(client.getClusterStateProvider().getLiveNodes());
                liveNodes.retainAll(getZkStateReader().getZkClient().getChildren("/node_roles/coordinator/on", null, true));
                if (!liveNodes.isEmpty()) {
                    return liveNodes;
                }
            }
        } catch (Exception e) {
            //ok, just use the /live_query_nodes
        }
      try {
        return getZkStateReader().getZkClient().getChildren("/live_query_nodes", null, true);
      } catch (Exception e) {
        log.warn("Failed to look up query nodes. Skipping");
        return Collections.emptyList();
      }
    }

  private Collection<String> getExternalPreferredOverseerNodes(CloudSolrClient client)  {
    try {
      if (getZkStateReader().getZkClient().exists("/node_roles/overseer/preferred", true)) {
        List<String> liveNodes = new ArrayList(client.getClusterStateProvider().getLiveNodes());
        liveNodes.retainAll(getZkStateReader().getZkClient().getChildren("/node_roles/overseer/preferred", null, true));
        if (!liveNodes.isEmpty()) {
          return liveNodes;
        }
      }
    } catch (Exception e) {
      log.warn("Failed to look up overseer nodes. Skipping");
    }
    return Collections.emptyList();
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

  List<String> getSolrNodesFromVagrant() throws JsonMappingException, JsonProcessingException, IOException {
	  List<String> out = new ArrayList<String>();
	  Map<String, Object> servers = new ObjectMapper().readValue(FileUtils.readFileToString(new File("vagrant/solr-servers.json"), "UTF-8"), Map.class);
	  for (String server: servers.keySet()) {
		  out.add(servers.get(server).toString());
	  }
	  return out;
  }

  String getZkNodeFromVagrant() throws JsonMappingException, JsonProcessingException, IOException {
	  Map<String, Object> servers = new ObjectMapper().readValue(FileUtils.readFileToString(new File("vagrant/zk-servers.json"), "UTF-8"), Map.class);
	  for (String server: servers.keySet()) {
		  return servers.get(server).toString();
	  }
	  throw new RuntimeException("Couldn't get ZK node from tfstate");
  }

  /**
   * A method used for creating a collection.
   * 
   * @param collectionName
   * @throws Exception 
   */
  public void createCollection(IndexBenchmark.Setup setup, String collectionName, String configsetName) throws Exception {
	  try (HttpSolrClient hsc = createClient()) {
		  Create create;
		  if (setup.replicationFactor != null) {
			  create = Create.createCollection(collectionName, configsetName, setup.shards, setup.replicationFactor);
		  } else {
			  create = Create.createCollection(collectionName, configsetName, setup.shards,
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
          log.info("Collection delete: "+resp);
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

  public String getZookeeperChroot() {
      return zookeeper.getChroot();
  }

  /**
   * A method used to get the zookeeper url for communication with the solr
   * cloud.
   * 
   * @return String
   */
  public String getZookeeperAdminUrl() {
    return "http://" + zookeeper.getHost() + ":" + zookeeper.getAdminPort();
  }

  public Zookeeper getZookeeper() {
	  return zookeeper;
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

      // Collect logs
      if (cluster.provisioningMethod.equalsIgnoreCase("local")) {
    	  for (SolrNode node: nodes) {
    		  LocalSolrNode localNode = (LocalSolrNode) node;
    		  String tarfile = Util.RUN_DIR + "logs-"+localNode.port+".tar";
    		  if (new File(tarfile).exists()) new File(tarfile).delete();
    		  String tarCommand = "tar -cf " + tarfile + " -C " + localNode.binDirectory.substring(0, localNode.binDirectory.length()-4) + "server/logs .";
    		  log.info("Trying command: " + tarCommand);
    		  Util.execute(tarCommand, Util.getWorkingDir());
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

  public boolean shouldUploadConfigSet() {
      return shouldUploadConfigSet;
  }

  public void uploadConfigSet(String configsetFile, boolean shareConfigset, String configsetZkName) throws Exception {
    if(configsetFile==null || configsets.contains(configsetZkName)) return;

    log.info("Configset: " +configsetZkName+
            " does not exist. creating... ");

    File f = Util.resolveSuitePath((configsetFile + ".zip"));

    if (!f.exists()) {
      throw new RuntimeException("Could not find configset file: " + configsetFile);
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
	      delete.setConfigSetName(configsetZkName);
	      delete.process(hsc);
      } catch (Exception ex) {
    	  //log.warn("Exception trying to delete configset", ex);
      }
      create.setMethod(SolrRequest.METHOD.POST);
      create.setConfigSetName(configsetZkName);
      try {
    	  create.process(hsc);
      } catch (Exception ex) {
    	  if (shareConfigset) {
    		  // ignore, since this configset might've been already created and we'll share it now
    	  } else {
    		  throw new RuntimeException("Couldn't create configset " + configsetZkName, ex);
    	  }    		  
      }
      configsets.add(configsetZkName);

      // This is a hack. We want all configsets to be trusted. Hence, unsetting the data on the znode that has trusted=false.
      try (SolrZkClient zkClient = new SolrZkClient.Builder().withUrl(getZookeeperUrl()).withTimeout( 100, TimeUnit.SECONDS).build()) {
        zkClient.setData(ZkConfigSetService.CONFIGS_ZKNODE + "/" + configsetZkName, (byte[]) null, true);
      }
      log.info("Configset: " + configsetZkName + " created successfully ");

    }


  }

  /**
   * Gets the list of nodes by its NodeRole. If NodeRole is not supported, then return all nodes.
   *
   * Take note that currently only ExternalSolrNode has support for NodeRole.
   * @param role
   * @return
   */
  public List<? extends SolrNode> getNodesByRole(NodeRole role) {
    return nodesByRole.containsKey(role) ? nodesByRole.get(role) : nodes;
  }

  public String getProvisioningMethod() {
      return cluster.provisioningMethod;
  }
}
