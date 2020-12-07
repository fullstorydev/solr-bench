import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.solr.benchmarks.BenchmarksMain;
import org.apache.solr.benchmarks.MetricsCollector;
import org.apache.solr.benchmarks.Util;
import org.apache.solr.benchmarks.beans.Cluster;
import org.apache.solr.benchmarks.beans.Configuration;
import org.apache.solr.benchmarks.beans.IndexBenchmark;
import org.apache.solr.benchmarks.solrcloud.CreateWithAdditionalParameters;
import org.apache.solr.benchmarks.solrcloud.LocalSolrNode;
import org.apache.solr.benchmarks.solrcloud.SolrCloud;
import org.apache.solr.benchmarks.solrcloud.SolrNode;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest.Create;
import org.apache.solr.common.cloud.ClusterState;
import org.apache.solr.common.cloud.Replica;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.util.Utils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.ThreadFactoryBuilder;

import beans.Collection;
import beans.Shard;
import beans.SolrClusterStatus;

public class StressMain {

    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());


	public static void main(String[] args) throws Exception {
        String configFile = args[0];

        Workflow workflow = new ObjectMapper().readValue(FileUtils.readFileToString(new File(configFile), "UTF-8"), Workflow.class);
        Cluster cluster = workflow.cluster;

        String solrPackagePath = BenchmarksMain.getSolrPackagePath(workflow.repo, workflow.solrPackage);
        SolrCloud solrCloud = new SolrCloud(cluster, solrPackagePath);
        solrCloud.init();

		executeWorkflow(workflow, solrCloud);
		
		solrCloud.shutdown(true);
	}

	private static void executeWorkflow(Workflow workflow, SolrCloud cloud) throws InterruptedException, JsonGenerationException, JsonMappingException, IOException {
		Map<String, AtomicInteger> globalVariables = new ConcurrentHashMap<String, AtomicInteger>();

        // Start metrics collection
        MetricsCollector metricsCollector = null;
        Thread metricsThread = null;

		Map<String, List<Future>> taskFutures = new HashMap<String, List<Future>>();
		Map<String, ExecutorService> taskExecutors = new HashMap<String, ExecutorService>();

		Map<String, List<Map>> finalResults = new ConcurrentHashMap<String, List<Map>>();

        if (workflow.metrics != null) {
        	metricsCollector = new MetricsCollector(cloud.nodes, workflow.metrics, 2);
        	metricsThread = new Thread(metricsCollector);
        	metricsThread.start();
        	//results.put("solr-metrics", metricsCollector.metrics);
        }

		long executionStart = System.currentTimeMillis();

		for (String var: workflow.globalVariables.keySet()) {
			globalVariables.put(var, new AtomicInteger(workflow.globalVariables.get(var)));
		}


		for (String taskName: workflow.executionPlan.keySet()) {
			TaskInstance instance = workflow.executionPlan.get(taskName);
			TaskType type = workflow.taskTypes.get(instance.type);
			System.out.println(taskName+" is of type: "+new ObjectMapper().writeValueAsString(type));

			taskFutures.put(taskName, new ArrayList<Future>());

			ExecutorService executor = Executors.newFixedThreadPool(instance.concurrency, new ThreadFactoryBuilder().setNameFormat(taskName+"-threadpool").build()); 

			taskExecutors.put(taskName, executor);

			finalResults.put(taskName, new ArrayList<>());
			for (int i=1; i<=instance.instances; i++) {

				Callable c = () -> {

					if (instance.waitFor != null) {
						log.info("Waiting for "+instance.waitFor+" to finish");
						boolean await = taskExecutors.get(instance.waitFor).awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
						log.info(instance.waitFor+" finished! "+await);
					}
					Map<String, Integer> copyOfGlobalVarialbes = new HashMap<String, Integer>();
					if (instance.preTaskEvals != null) {
						copyOfGlobalVarialbes = evaluate(instance.preTaskEvals, globalVariables);
					}

					Map<String, String> params = new HashMap<String, String>();
					if (instance.parameters != null) {
						for (String param: instance.parameters.keySet()) {
							params.put(param, resolveInteger(instance.parameters.get(param), copyOfGlobalVarialbes));
						}
					}
					long start = System.currentTimeMillis();

					log.info("Hello1: "+params+", "+copyOfGlobalVarialbes+", "+instance.parameters+", "+globalVariables);
					
					if (type.pauseSolrNode != null) {
						String nodeIndex = resolveString(resolveString(type.pauseSolrNode, params), workflow.globalConstants);
						int pauseDuration = type.pauseSeconds;
						long taskStart = System.currentTimeMillis();
		            	LocalSolrNode node = ((LocalSolrNode)cloud.nodes.get(Integer.valueOf(nodeIndex) - 1));
			            try {
			            	node.pause(pauseDuration);
			            } catch (Exception ex) {
			            	ex.printStackTrace();
			            }
			            
			            if (type.awaitRecoveries) {
					        try (CloudSolrClient client = new CloudSolrClient.Builder().withSolrUrl(cloud.nodes.get(0).getBaseUrl()).build();) {
					        
					        	int numInactive = 0;
					        	do {
					        		numInactive = 0;
					        		Set<String> inactive = new HashSet<>();
					        		ClusterState state = client.getClusterStateProvider().getClusterState();
					        		for (String coll: state.getCollectionsMap().keySet()) {
					        			for (Slice shard: state.getCollection(coll).getActiveSlices()) {
					        				for (Replica replica: shard.getReplicas()) {
					        					if (replica.getState() != Replica.State.ACTIVE) {
					        						if (replica.getNodeName().contains(node.port)) {
					        							numInactive++;
					        							inactive.add(replica.getName());
					        							//System.out.println("\tNon active Replica: "+replica.getName()+" in "+replica.getNodeName());
					        						}
					        					}
					        				}
					        			}
					        		}
					        		System.out.println("\tInactive replicas on restarted node ("+node.port+"): "+inactive);
					        		if (numInactive != 0) Thread.sleep(1000);
					        	} while (numInactive > 0);
					        }				        

			            }
			            long taskEnd = System.currentTimeMillis();
						
						finalResults.get(taskName).add(Map.of("total-time", (taskEnd-taskStart)/1000.0, "start-time", (taskStart-executionStart)/1000.0, "end-time", (taskEnd-executionStart)/1000.0));

					} else if (type.restartSolrNode != null) {
						String nodeIndex = resolveString(resolveString(type.restartSolrNode, params), workflow.globalConstants);
						long taskStart = System.currentTimeMillis();
		            	LocalSolrNode node = ((LocalSolrNode)cloud.nodes.get(Integer.valueOf(nodeIndex) - 1));
			            try {
			            	node.restart();
			            } catch (Exception ex) {
			            	ex.printStackTrace();
			            }
			            
			            if (type.awaitRecoveries) {
					        try (CloudSolrClient client = new CloudSolrClient.Builder().withSolrUrl(cloud.nodes.get(0).getBaseUrl()).build();) {
					        
					        	int numInactive = 0;
					        	do {
					        		numInactive = 0;
					        		Set<String> inactive = new HashSet<>();
					        		ClusterState state = client.getClusterStateProvider().getClusterState();
					        		for (String coll: state.getCollectionsMap().keySet()) {
					        			for (Slice shard: state.getCollection(coll).getActiveSlices()) {
					        				for (Replica replica: shard.getReplicas()) {
					        					if (replica.getState() != Replica.State.ACTIVE) {
					        						if (replica.getNodeName().contains(node.port)) {
					        							numInactive++;
					        							inactive.add(replica.getName());
					        							//System.out.println("\tNon active Replica: "+replica.getName()+" in "+replica.getNodeName());
					        						}
					        					}
					        				}
					        			}
					        		}
					        		System.out.println("\tInactive replicas on restarted node ("+node.port+"): "+inactive);
					        		if (numInactive != 0) Thread.sleep(1000);
					        	} while (numInactive > 0);
					        }				        

			            }
			            long taskEnd = System.currentTimeMillis();
						
						finalResults.get(taskName).add(Map.of("total-time", (taskEnd-taskStart)/1000.0, "start-time", (taskStart-executionStart)/1000.0, "end-time", (taskEnd-executionStart)/1000.0));

					} else if (type.indexBenchmark != null) {
						log.info("Running benchmarking task: "+type.indexBenchmark.datasetFile);

						// resolve the collection name using template
						Map<String, String> solrurlMap = Map.of("SOLRURL", cloud.nodes.get(new Random().nextInt(cloud.nodes.size())).getBaseUrl());
						//String collectionName = resolveString(resolveInteger(resolveString(type.indexBenchmark.setups.get(0).collection, params), copyOfGlobalVarialbes), solrurlMap);
						String collectionName = resolveString(type.indexBenchmark.setups.get(0).collection, params);

						log.info("Global variables: "+instance.parameters);
						log.info("Indexing benchmarks for collection: "+collectionName);

						Map<String, Map> results = new HashMap<String, Map>();
			            results.put("indexing-benchmarks", new LinkedHashMap<String, List<Map>>());
			            long taskStart = System.currentTimeMillis();
			            try {
			            	BenchmarksMain.runIndexingBenchmarks(Collections.singletonList(type.indexBenchmark), collectionName, false, cloud, results);
			            } catch (Exception ex) {
			            	ex.printStackTrace();
			            }
			            long taskEnd = System.currentTimeMillis();
						log.info("Results: "+results.get("indexing-benchmarks"));
						try {
							String totalTime = ((List<Map>)((Map.Entry)((Map)((Map.Entry)results.get("indexing-benchmarks").entrySet().iterator().next()).getValue()).entrySet().iterator().next()).getValue()).get(0).get("total-time").toString();
							finalResults.get(taskName).add(Map.of("total-time", totalTime, "start-time", (taskStart-executionStart)/1000.0, "end-time", (taskEnd-executionStart)/1000.0));
						} catch (Exception ex) {
							//ex.printStackTrace();
						}
					} else if (type.clusterStateBenchmark!=null) {
						log.info("starting cluster state task...");
			            try {
							SolrClusterStatus status = new ObjectMapper().readValue(new File(type.clusterStateBenchmark.filename), SolrClusterStatus.class);
							log.info("starting cluster state task... "+status);
	
							long taskStart = System.currentTimeMillis();
				            int shards = 0;
				            for (String name: status.getCluster().getCollections().keySet()) {
				            	Collection coll = status.getCluster().getCollections().get(name);
				            	shards += coll.getShards().size();
				            }
				            
				            Map<String, Integer> nodeMap = new LinkedHashMap(); // mapping of node name in supplied cluster state to index of nodes we have
				            for (int j=0; j<status.getCluster().getLiveNodes().size(); j++) {
				            	nodeMap.put(status.getCluster().getLiveNodes().get(j), j % cloud.nodes.size());
				            }
				            log.info("Node mapping: "+nodeMap);
				            log.info("Summary: "+status.getCluster().getCollections().size()+" collections loaded, to be executed across "+status.getCluster().getLiveNodes().size()+" nodes. Total shards: "+shards);
				            
				            int collectionCounter = 0;
				            int shardCounter = 0;
				            for (String name: status.getCluster().getCollections().keySet()) {
				            	collectionCounter++;
				            	Collection coll = status.getCluster().getCollections().get(name);
				            	Set<Integer> nodes = new HashSet<>();
				            	for (Shard sh: coll.getShards().values()) {
				            		nodes.add(nodeMap.get(sh.getReplicas().values().iterator().next().getNodeName()));
				            	}
				            	
				            	String nodeSet = "";
				            	for (int n: nodes) {
				            		nodeSet += cloud.nodes.get(n).getNodeName() + "_solr,";
				            	}
				            	nodeSet = nodeSet.substring(0, nodeSet.length()-1);
				            	shardCounter += coll.getShards().size();
				            	//log.info(collectionCounter+": "+name+" has shards: "+coll.getShards().size() + ", nodeSet: "+nodeSet);
				            	if (collectionCounter %10 == 0) {
				            		log.info(collectionCounter+": Time elapsed for this task: "+(System.currentTimeMillis()-taskStart)/1000+" seconds (approx), total shards: "+shardCounter);
				            	}
						        try (CloudSolrClient client = new CloudSolrClient.Builder().withSolrUrl(cloud.nodes.get(0).getBaseUrl()).build();) {
						        	Create create = Create.createCollection(name, coll.getShards().size(), 1).
						        		setMaxShardsPerNode(coll.getShards().size()).
						        		setCreateNodeSet(nodeSet);
						        	Map<String, String> additional = Map.of("perReplicaState", "true");
						        	new CreateWithAdditionalParameters(create, name, additional).process(client);
						        }
						        
						        //if ((++collectionCounter) == 3) break;
				            }
				            long taskEnd = System.currentTimeMillis();
				            log.info("Task took time: "+(taskEnd-taskStart)/1000.0+" seconds.");
							finalResults.get(taskName).add(Map.of("total-time", (taskEnd-taskStart), "start-time", (taskStart-executionStart)/1000.0, "end-time", (taskEnd-executionStart)/1000.0));
			            } catch (Exception ex) {
			            	ex.printStackTrace();
			            }
					} else if (type.command != null) {
						Map<String, String> solrurlMap = Map.of("SOLRURL", cloud.nodes.get(new Random().nextInt(cloud.nodes.size())).getBaseUrl());
						String command = resolveString(resolveString(resolveString(type.command, params), workflow.globalConstants), solrurlMap); //nocommit resolve instance.parameters as well
						log.info("Running in "+instance.mode+" mode: "+command);

						long taskStart = System.currentTimeMillis();
						try {
							String output = IOUtils.toString(new URL(command).openStream(), Charset.forName("UTF-8"));
							log.info("Output: "+output);
						} catch (Exception ex) {
							ex.printStackTrace();
						}
						long taskEnd = System.currentTimeMillis();
						finalResults.get(taskName).add(Map.of("total-time", (taskEnd-taskStart)/1000.0, "start-time", (taskStart-executionStart)/1000.0, "end-time", (taskEnd-executionStart)/1000.0));
					}
					long end = System.currentTimeMillis();
					return end-start;
				};

				taskFutures.get(taskName).add(executor.submit(c));
			}
			executor.shutdown();
		}
		
		for (String task: taskExecutors.keySet()) {
			taskExecutors.get(task).awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
		}
		
        // Stop metrics collection
        if (workflow.metrics != null) {
        	metricsCollector.stop();
        	metricsThread.stop();
        }

		log.info("Final results: "+finalResults);
        new ObjectMapper().writeValue(new File("results-stress.json"), finalResults);
        if (metricsCollector != null) {
        	new ObjectMapper().writeValue(new File("metrics-stress.json"), metricsCollector.metrics);
        }
	}

	private static String resolveInteger(String cmd, Map<String, Integer> vars) {
		for (String var: vars.keySet()) {
			cmd = cmd.replace("${"+var+"}", String.valueOf(vars.get(var)));
		}
		return cmd;
	}

	private static String resolveString(String cmd, Map<String, String> vars) {
		for (String var: vars.keySet()) {
			cmd = cmd.replace("${"+var+"}", String.valueOf(vars.get(var)));
		}
		return cmd;
	}

	private static Map<String, Integer> evaluate(List<String> exprs, Map<String, AtomicInteger> globalVariables) {
		Map<String, Integer> ret = new HashMap<String, Integer>();

		// use nashorn etc. for script evals
		for (String ex: exprs) {
			if (ex.startsWith("inc(")) {
				String parts[] = Arrays.copyOfRange(ex.split("(inc\\()|(\\))|(,)"), 1, 3);

				int val = globalVariables.get(parts[0]).addAndGet(Integer.valueOf(parts[1]));
				ret.put(parts[0], val);
			}
		}
		return ret;
	}
	
	public static void validateWorkflow(Workflow w) {
		for (String typeName: w.taskTypes.keySet()) {
			TaskType type = w.taskTypes.get(typeName);
			if (type.indexBenchmark != null) {
				if (type.indexBenchmark.setups.size() != 1) {
					throw new RuntimeException("Indexing benchmark task type " + typeName + " should have only 1 setup, but has " + type.indexBenchmark.setups.size() + ".");
				}
				if (type.indexBenchmark.setups.get(0).threadStep != 1) {
					throw new RuntimeException("Indexing benchmark task type " + typeName + " should have threadStep=1, but has " + type.indexBenchmark.setups.get(0).threadStep + ".");
				}
			}
		}
	}
}
