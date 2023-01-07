import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandles;
import java.net.HttpURLConnection;
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
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPInputStream;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.solr.benchmarks.BenchmarksMain;
import org.apache.solr.benchmarks.MetricsCollector;
import org.apache.solr.benchmarks.beans.Cluster;
import org.apache.solr.benchmarks.solrcloud.CreateWithAdditionalParameters;
import org.apache.solr.benchmarks.solrcloud.GenericSolrNode;
import org.apache.solr.benchmarks.solrcloud.LocalSolrNode;
import org.apache.solr.benchmarks.solrcloud.SolrCloud;
import org.apache.solr.benchmarks.solrcloud.SolrNode;
import org.apache.solr.client.solrj.impl.CloudSolrClient;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.request.CollectionAdminRequest.Create;
import org.apache.solr.client.solrj.response.RequestStatusState;
import org.apache.solr.common.cloud.*;
import org.apache.solr.common.util.Pair;
import org.apache.zookeeper.KeeperException;
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

		String solrPackagePath = cluster.provisioningMethod.equalsIgnoreCase("existing") ? null : BenchmarksMain.getSolrPackagePath(workflow.repo, workflow.solrPackage);
		SolrCloud solrCloud = new SolrCloud(cluster, solrPackagePath);
		solrCloud.init();
		try {
			executeWorkflow(workflow, solrCloud);
		} finally {
			log.info("Shutting down...");
			solrCloud.shutdown(true);
		}
	}

	private static void executeWorkflow(Workflow workflow, SolrCloud cloud) throws InterruptedException, JsonGenerationException, JsonMappingException, IOException {
		Map<String, AtomicInteger> globalVariables = new ConcurrentHashMap<String, AtomicInteger>();

		// Initialize the common threadpools
		Map<String, ExecutorService> commonThreadpools = new HashMap<>();
		for (Workflow.ThreadpoolInfo info: workflow.threadpools) {
			commonThreadpools.put(info.name, Executors.newFixedThreadPool(info.size, new ThreadFactoryBuilder().setNameFormat(info.name).build()));
		}

		// Start metrics collection
		MetricsCollector metricsCollector = null;
		Thread metricsThread = null;

		Map<String, List<Future>> taskFutures = new HashMap<String, List<Future>>();
		Map<String, ExecutorService> taskExecutors = new HashMap<String, ExecutorService>();

		Map<String, List<Map>> finalResults = new ConcurrentHashMap<String, List<Map>>();

		if (workflow.metrics != null) {
			metricsCollector = new MetricsCollector(cloud, workflow.zkMetrics, workflow.metrics, 2);
			metricsThread = new Thread(metricsCollector);
			metricsThread.start();
			//results.put("solr-metrics", metricsCollector.metrics);
		}

		long executionStart = System.currentTimeMillis();

		for (String var: workflow.globalVariables.keySet()) {
			globalVariables.put(var, new AtomicInteger(workflow.globalVariables.get(var)));
		}


		List<Pair<String, Pair<Callable, ExecutorService>>> commonTasks = new ArrayList();

		for (String taskName: workflow.executionPlan.keySet()) {
			TaskInstance instance = workflow.executionPlan.get(taskName);
			TaskType type = workflow.taskTypes.get(instance.type);
			System.out.println(taskName+" is of type: "+new ObjectMapper().writeValueAsString(type));

			taskFutures.put(taskName, new ArrayList<Future>());

			ExecutorService executor;

			boolean commonThreadpoolTask = instance.threadpool != null;

			if (commonThreadpoolTask) {
				executor = commonThreadpools.get(instance.threadpool);
			} else {
				executor = Executors.newFixedThreadPool(instance.concurrency, new ThreadFactoryBuilder().setNameFormat(taskName + "-threadpool").build());
			}

			taskExecutors.put(taskName, executor);

			finalResults.put(taskName, new ArrayList<>());
			for (int i=1; i<=instance.instances; i++) {

				Callable c = taskCallable(workflow, cloud, globalVariables, taskExecutors, finalResults, executionStart, taskName, instance, type);

				if (!commonThreadpoolTask) {
					taskFutures.get(taskName).add(executor.submit(c));
				} else {
					// Don't submit them right away, but instead add to a list, shuffle the list and then submit them.
					// Doing so ensures that different types of tasks are executed at similar points in time.
					commonTasks.add(new Pair<String, Pair<Callable, ExecutorService>>(taskName, new Pair<Callable, ExecutorService>(c, executor)));
				}
			}

			// if not a common threadpool, shutdown the executor
			if (!commonThreadpoolTask) executor.shutdown();
		}

		Collections.shuffle(commonTasks);
		log.info("Order of operations submitted via common threadpools: "+commonTasks);
		for (Pair<String, Pair<Callable, ExecutorService>> task: commonTasks) {
			String taskName = task.first();
			Callable c = task.second().first();
			ExecutorService ex = task.second().second();
			taskFutures.get(taskName).add(ex.submit(c));
		}

		for (String tp: commonThreadpools.keySet())
			commonThreadpools.get(tp).shutdown();

		for (String task: taskExecutors.keySet()) {
			taskExecutors.get(task).awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);
		}

		for (String tp: commonThreadpools.keySet())
			commonThreadpools.get(tp).awaitTermination(Integer.MAX_VALUE, TimeUnit.SECONDS);

		// Stop metrics collection
		if (workflow.metrics != null) {
			metricsCollector.stop();
			metricsThread.stop();
		}

		log.info("Final results: "+finalResults);
		new ObjectMapper().writeValue(new File("results-stress.json"), finalResults);
		if (metricsCollector != null) {
			metricsCollector.metrics.put("zookeeper", metricsCollector.zkMetrics);
			new ObjectMapper().writeValue(new File("metrics-stress.json"), metricsCollector.metrics);
		}
		exportToGrafana(finalResults);
	}

	private static void exportToGrafana(Map<String, List<Map>> finalResults) {
		for (Map.Entry<String, List<Map>> entry : finalResults.entrySet()) {
			String taskName = entry.getKey();
			List<Map> metrics = entry.getValue();
			for (int i = 0 ; i < metrics.size(); i++) {
				String iteration = taskName + "-" + i;
				//get the timestamps
				for (Object metricKey : metrics.get(i).entrySet()) {
					if (metricKey instanceof String && ((String) metricKey).endsWith("-timestamp")) {

					}
				}

			}
		}

	}

	private static Callable taskCallable(Workflow workflow, SolrCloud cloud, Map<String, AtomicInteger> globalVariables, Map<String, ExecutorService> taskExecutors, Map<String, List<Map>> finalResults, long executionStart, String taskName, TaskInstance instance, TaskType type) {
		Callable c = () -> {
			if (instance.waitFor != null) {
				log.info("Waiting for "+ instance.waitFor+" to finish");
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

			log.debug("Hello1: "+params+", "+copyOfGlobalVarialbes+", "+ instance.parameters+", "+ globalVariables);

			if (type.pauseSolrNode != null) {
				String nodeIndex = resolveString(resolveString(type.pauseSolrNode, params), workflow.globalConstants);
				int pauseDuration = type.pauseSeconds;
				long taskStart = System.currentTimeMillis();
				LocalSolrNode node = ((LocalSolrNode) cloud.nodes.get(Integer.valueOf(nodeIndex) - 1));
				try {
					node.pause(pauseDuration);
				} catch (Exception ex) {
					ex.printStackTrace();
				}

				if (type.awaitRecoveries) {
					try (CloudSolrClient client = new CloudSolrClient.Builder().withZkHost(cloud.getZookeeperUrl()).build();) {

						int numInactive = 0;
						do {
							numInactive = 0;
							Set<String> inactive = new HashSet<>();
							ClusterState state = client.getClusterStateProvider().getClusterState();
							for (String coll: state.getCollectionsMap().keySet()) {
								PRS prs = new PRS(ZkStateReader.getCollectionPath(coll), client.getZkStateReader().getZkClient());
								for (Slice shard: state.getCollection(coll).getActiveSlices()) {
									for (Replica replica: shard.getReplicas()) {
										if (replica.getState() != Replica.State.ACTIVE) {
											if (replica.getNodeName().contains(node.port)) {
												numInactive++;
												inactive.add(coll+"_"+shard.getName());
												System.out.println("\tNon active Replica: "+replica.getName()+" in "+replica.getNodeName() +" PRS : "+ prs.getState(replica.getName()));
											}
										}
									}
								}

							}

							System.out.println("\tInactive replicas on restarted node ("+node.port+"): "+inactive);
							if (numInactive != 0) {
								Thread.sleep(2000);
								client.getZkStateReader().forciblyRefreshAllClusterStateSlow();
							}
						} while (numInactive > 0);
					}

				}
				long taskEnd = System.currentTimeMillis();

				finalResults.get(taskName).add(Map.of("total-time", (taskEnd-taskStart)/1000.0, "start-time", (taskStart- executionStart)/1000.0, "end-time", (taskEnd- executionStart)/1000.0,
						"init-timestamp", executionStart, "start-timestamp", taskStart, "end-timestamp", taskEnd));

			} else if (type.restartSolrNode != null) {
				log.info("Restarting node: "+type.restartSolrNode);

				String nodeIndex = resolveString(resolveString(type.restartSolrNode, params), workflow.globalConstants);
				long taskStart = System.currentTimeMillis();
				log.info("Restarting node "+Integer.valueOf(nodeIndex));
				SolrNode node = cloud.nodes.get(Integer.valueOf(nodeIndex) - 1);
				log.info("Restarting " + node.getNodeName());

				try {
					node.restart();
				} catch (Exception ex) {
					ex.printStackTrace();
				}

				if (type.awaitRecoveries) {
					try (CloudSolrClient client = new CloudSolrClient.Builder().withZkHost(cloud.getZookeeperUrl()).build();) {
						int numInactive = 0;
						do {
							numInactive = getNumInactiveReplicas(node, client);
						} while (numInactive > 0);
					} catch (Exception ex) {
						ex.printStackTrace();
					}
				}
				long taskEnd = System.currentTimeMillis();

				finalResults.get(taskName).add(Map.of("total-time", (taskEnd-taskStart)/1000.0, "start-time", (taskStart- executionStart)/1000.0, "end-time", (taskEnd- executionStart)/1000.0,
						"init-timestamp", executionStart, "start-timestamp", taskStart, "end-timestamp", taskEnd));

			} else if (type.indexBenchmark != null) {
				log.info("Running benchmarking task: "+ type.indexBenchmark.datasetFile);

				// resolve the collection name using template
				Map<String, String> solrurlMap = Map.of("SOLRURL", cloud.nodes.get(new Random().nextInt(cloud.nodes.size())).getBaseUrl());
				//String collectionName = resolveString(resolveInteger(resolveString(type.indexBenchmark.setups.get(0).collection, params), copyOfGlobalVarialbes), solrurlMap);
				String collectionName = resolveString(type.indexBenchmark.setups.get(0).collection, params);

				log.info("Global variables: "+ instance.parameters);
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
					finalResults.get(taskName).add(Map.of(
							"total-time", totalTime, "start-time", (taskStart- executionStart)/1000.0, "end-time", (taskEnd- executionStart)/1000.0,
							"init-timestamp", executionStart, "start-timestamp", taskStart, "end-timestamp", taskEnd));
				} catch (Exception ex) {
					//ex.printStackTrace();
				}
			} else if (type.queryBenchmark != null) {
				log.info("Running benchmarking task: "+ type.queryBenchmark.queryFile);

				// resolve the collection name using template
				Map<String, String> solrurlMap = Map.of("SOLRURL", cloud.nodes.get(new Random().nextInt(cloud.nodes.size())).getBaseUrl());
				String collectionName = resolveString(type.queryBenchmark.collection, params);

				log.info("Global variables: "+ instance.parameters);
				log.info("Query benchmarks for collection: "+collectionName);

				Map<String, Map> results = new HashMap<String, Map>();
				results.put("query-benchmarks", new LinkedHashMap<String, List<Map>>());
				long taskStart = System.currentTimeMillis();
				try {
					BenchmarksMain.runQueryBenchmarks(Collections.singletonList(type.queryBenchmark), collectionName, cloud, results);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				long taskEnd = System.currentTimeMillis();
				log.info("Results: "+results.get("query-benchmarks"));
				try {
					//String totalTime = ((List<Map>)((Map.Entry)((Map)((Map.Entry)results.get("query-benchmarks").entrySet().iterator().next()).getValue()).entrySet().iterator().next()).getValue()).get(0).get("total-time").toString();
					String totalTime = String.valueOf(taskEnd - taskStart);

					finalResults.get(taskName).add(Map.of("total-time", totalTime, "start-time", (taskStart- executionStart)/1000.0,
							"end-time", (taskEnd- executionStart)/1000.0, "timings", ((Map.Entry)results.get("query-benchmarks").entrySet().iterator().next()).getValue(),
							"init-timestamp", executionStart, "start-timestamp", taskStart, "end-timestamp", taskEnd));
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			} else if (type.clusterStateBenchmark!=null) {
				TaskType.ClusterStateBenchmark clusterStateBenchmark = type.clusterStateBenchmark;
				log.info("starting cluster state task...");
				try {
					InputStream is = clusterStateBenchmark.filename.endsWith(".gz")? 
							new GZIPInputStream(new FileInputStream(new File(clusterStateBenchmark.filename)))
							: new FileInputStream(new File(clusterStateBenchmark.filename));
					SolrClusterStatus status = new ObjectMapper().readValue(is, SolrClusterStatus.class);
					is.close();
					log.info("starting cluster state task... "+status);

					long taskStart = System.currentTimeMillis();
					int shards = 0;
					for (String name: status.getCluster().getCollections().keySet()) {
						Collection coll = status.getCluster().getCollections().get(name);
						shards += coll.getShards().size();
					}

					// Translate node names in cluster state to indexes of nodes we have
					Map<String, Integer> nodeMap = new LinkedHashMap<String, Integer>(); // mapping of node name in supplied cluster state to index of nodes we have
					for (int j=0, k=0; k<status.getCluster().getLiveNodes().size(); j++, k++) {
						if (clusterStateBenchmark.excludeNodes.contains(j % cloud.nodes.size() + 1)) {
							k--; continue;
						}
						nodeMap.put(status.getCluster().getLiveNodes().get(k), j % cloud.nodes.size());
					}
					log.info("Node mapping: "+nodeMap);

					try (CloudSolrClient client = new CloudSolrClient.Builder().withSolrUrl(cloud.nodes.get(0).getBaseUrl()).build();) {
						ClusterState state = client.getClusterStateProvider().getClusterState();
						log.info("Live nodes: "+state.getLiveNodes());
					}

					log.info("Summary: "+status.getCluster().getCollections().size()+" collections loaded, to be executed across "+status.getCluster().getLiveNodes().size()+" nodes. Total shards: "+shards);

					// Start the simulation
					AtomicInteger collectionCounter = new AtomicInteger(0);
					AtomicInteger shardCounter = new AtomicInteger(0);
					ConcurrentHashMap<String, Integer> failedCollections = new ConcurrentHashMap<String, Integer>();
					int simpleCounter = 0;

					int threadPoolSize = (int)((double)Runtime.getRuntime().availableProcessors() * clusterStateBenchmark.simulationConcurrencyFraction) + 1;
					ExecutorService clusterStateExecutor = Executors.newFixedThreadPool(threadPoolSize, new ThreadFactoryBuilder().setNameFormat("clusterstate-task-threadpool").build());

					for (String name: status.getCluster().getCollections().keySet()) {
						if ((++simpleCounter) > clusterStateBenchmark.collectionsLimit && type.clusterStateBenchmark.collectionsLimit > 0) {
							log.info("Setting up tasks for " + clusterStateBenchmark.collectionsLimit+" collections...");
							break;
						}

						final String configSet[] = new String[1];
						if (clusterStateBenchmark.configset != null) {
							cloud.uploadConfigSet(clusterStateBenchmark.configset, true, clusterStateBenchmark.configset + ".SOLRBENCH");
							configSet[0] = clusterStateBenchmark.configset + ".SOLRBENCH";
						} else {
							configSet[0] = "_default";
						}

						Callable callable = () -> {
							Collection coll = status.getCluster().getCollections().get(name);
							Set<Integer> nodes = new HashSet<>();
							for (Shard sh: coll.getShards().values()) {
								nodes.add(nodeMap.get(sh.getReplicas().values().iterator().next().getNodeName()));
							}

							String nodeSet = "";
							for (int n: nodes) {
								nodeSet += cloud.nodes.get(n).getNodeName() + 
										(cloud.nodes.get(n).getNodeName().endsWith("_solr")? "": "_solr") +",";
							}
							nodeSet = nodeSet.substring(0, nodeSet.length()-1);
							shardCounter.addAndGet(coll.getShards().size());

							final int COLLECTION_TIMEOUT_SECS = 200;

							try (HttpSolrClient client = new HttpSolrClient.Builder(cloud.nodes.get(nodes.iterator().next()).getBaseUrl()).build();) {
								Create create = Create.createCollection(name, configSet[0], coll.getShards().size(), 1).
										setMaxShardsPerNode(coll.getShards().size()).
										setCreateNodeSet(nodeSet);
								create.setAsyncId(name);

								Map<String, String> additional = clusterStateBenchmark.collectionCreationParams==null? new HashMap<>(): clusterStateBenchmark.collectionCreationParams;
								CreateWithAdditionalParameters newCreate = new CreateWithAdditionalParameters(create, name, additional);
								String asyncId = newCreate.processAsync(name, client);
								RequestStatusState state = CollectionAdminRequest.requestStatus(asyncId).waitFor(client, COLLECTION_TIMEOUT_SECS);
								if (state != RequestStatusState.COMPLETED) { // timeout
									log.error("Waited "+COLLECTION_TIMEOUT_SECS+" seconds, but collection "+name+" failed: "+name+", shards: "+coll.getShards().size());
									log.error("Collection creation failed, last status on the async task: "
												+ CollectionAdminRequest.requestStatus(asyncId).process(client).toString());
									failedCollections.put(name, coll.getShards().size());
								}
							} catch (Exception ex) {
								log.error("Collection creation failed: "+name+", shards: "+coll.getShards().size());
								failedCollections.put(name, coll.getShards().size());
								new RuntimeException("Collection creation failed: ", ex).printStackTrace();
							}

							int currentCounter = collectionCounter.incrementAndGet();
							if (currentCounter % 10 == 0) {
								log.info(collectionCounter + ": Time elapsed for this task: " + (System.currentTimeMillis()-taskStart)/1000 + " seconds (approx),"
										+ " total shards: " + shardCounter + ", per node: " + (shardCounter.get() / cloud.nodes.size()));
							}

							return true;
						};

						clusterStateExecutor.submit(callable);
					}

					clusterStateExecutor.shutdown();
					clusterStateExecutor.awaitTermination(24, TimeUnit.HOURS);

					log.info("Number of failed collections: "+failedCollections.size());
					log.info("Failed collections: "+failedCollections);

					long taskEnd = System.currentTimeMillis();
					log.info("Task took time: "+(taskEnd-taskStart)/1000.0+" seconds.");
					finalResults.get(taskName).add(Map.of("total-time", (taskEnd-taskStart), "start-time", (taskStart- executionStart)/1000.0, "end-time", (taskEnd- executionStart)/1000.0,
							"init-timestamp", executionStart, "start-timestamp", taskStart, "end-timestamp", taskEnd));
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			} else if (type.command != null) {
				Map<String, String> solrurlMap = new HashMap<>();
				solrurlMap.put("SOLRURL", cloud.nodes.get(new Random().nextInt(cloud.nodes.size())).getBaseUrl());
				log.info("Resolving random params: "+type.command);
				try {
					resolveRandomParams(cloud, type, params, solrurlMap);
				} catch (Exception ex) {
					log.error("Failed to populate random params", ex);
				}
				String command = resolveString(resolveString(resolveString(type.command, params), workflow.globalConstants), solrurlMap); //nocommit resolve instance.parameters as well
				log.info("Resolved command: " + command);
				log.info("Running in "+ instance.mode+" mode: "+command);

				long taskStart = System.currentTimeMillis();
				int responseCode = -1;
				try {
					HttpURLConnection connection = (HttpURLConnection) new URL(command).openConnection();
					responseCode = connection.getResponseCode();
					String output = IOUtils.toString((InputStream)connection.getContent(), Charset.forName("UTF-8"));
					log.info("Output ("+responseCode+"): "+output);
				} catch (Exception ex) {
					ex.printStackTrace();
				}
				long taskEnd = System.currentTimeMillis();
				finalResults.get(taskName).add(Map.of("total-time", (taskEnd-taskStart)/1000.0, "start-time", (taskStart- executionStart)/1000.0, "end-time", (taskEnd- executionStart)/1000.0, "status", responseCode,
						"init-timestamp", executionStart, "start-timestamp", taskStart, "end-timestamp", taskEnd));
			}
			long end = System.currentTimeMillis();

			runValidations(instance.validations, workflow, cloud);

			
			return end-start;			
		};
		return c;
	}

	private static int getNumInactiveReplicas(SolrNode node, CloudSolrClient client)
			throws KeeperException, InterruptedException, IOException {
		int numInactive;
		numInactive = 0;
		Map<String, String> inactive = new HashMap<>();
		client.getZkStateReader().forciblyRefreshAllClusterStateSlow();
		ClusterState state = client.getClusterStateProvider().getClusterState();
		for (String coll: state.getCollectionsMap().keySet()) {
			for (Slice shard: state.getCollection(coll).getActiveSlices()) {
				for (Replica replica: shard.getReplicas()) {
					if (replica.getState() != Replica.State.ACTIVE) {
						//if (node == null || replica.getNodeName().contains(node.port)) {
						if (node instanceof LocalSolrNode && replica.getNodeName().contains(((LocalSolrNode)node).port)
								|| node instanceof GenericSolrNode && replica.getNodeName().contains(node.getNodeName())) {
							numInactive++;
							inactive.put(replica.getName(), replica.getState().toString());
						}
					}
				}
			}
		}
		if (node != null) {
			System.out.println("\tInactive replicas on restarted node ("+node.getBaseUrl()+"): " + numInactive );
			if (numInactive != 0) {
				Thread.sleep(1000);
				client.getZkStateReader().forciblyRefreshAllClusterStateSlow();
			}
		}
		return numInactive;
	}
	
	public static void runValidations(List<String> validations, Workflow workflow, SolrCloud cloud) {
		if (validations == null) return;
		for (String v: validations) {
			Workflow.Validation validationDefinition = workflow.validations.get(v);
			if (validationDefinition.numInactiveReplicas != null) {
				// get num inactive replicas
				try (CloudSolrClient client = new CloudSolrClient.Builder().withZkHost(cloud.getZookeeperUrl()).build();) {
					int numInactive = getNumInactiveReplicas(null, client);
					log.info("Validation: inactive replicas are " + numInactive);
					if (numInactive > validationDefinition.numInactiveReplicas) {
						log.error("Failed validation: " + new ObjectMapper().writeValueAsString(validationDefinition));
					}
				} catch (KeeperException | InterruptedException | IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * Populate some random variables like RANDOM_COLLECTION or RANDOM_SHARD
	 */
	private static void resolveRandomParams(SolrCloud cloud, TaskType type, Map<String, String> params,
											Map<String, String> solrurlMap) throws InterruptedException, KeeperException, IOException {

		String collection = null;

		if (type.command.contains("RANDOM_BOOLEAN")) {
			solrurlMap.put("RANDOM_BOOLEAN", String.valueOf(new Random().nextBoolean()));
		}

		if (type.command.contains("RANDOM_COLLECTION")) {
			try(CloudSolrClient client = new CloudSolrClient.Builder().withZkHost(cloud.getZookeeperUrl()).build()) {
				log.info("Trying to get list of collections: ");
				Thread.sleep(500);
				//List<Slice> slices = getActiveSlicedShuffled(client, collection);
				List<String> colls = new ArrayList(client.getZkStateReader().getClusterState().getCollectionsMap().keySet());
				Collections.shuffle(colls);
				log.info("Active collections: "+colls.size());
				collection = colls.get(0);
				solrurlMap.put("RANDOM_COLLECTION", colls.get(0));
				
				// If a collection is randomly chosen, populate the RANDOM_BOOLEAN in a way that's opposite of the PRS state.
				solrurlMap.put("RANDOM_BOOLEAN", String.valueOf(!client.getZkStateReader().getClusterState().getCollection(colls.get(0)).isPerReplicaState()));
			}
		}


		if (type.command.contains("RANDOM_SHARD")) {
			try(CloudSolrClient client = new CloudSolrClient.Builder().withZkHost(cloud.getZookeeperUrl()).build()) {
				collection = collection==null? params.get("COLLECTION"): collection;
				if (collection == null) {
					throw new RuntimeException("To use RANDOM_SHARD, you must provide a 'COLLECTION' or use ${RANDOM_COLLECTION} parameter.");
				}
				log.info("Trying to get shards for collection: "+collection);
				Thread.sleep(500);
				List<Slice> slices = getActiveSlicedShuffled(client, collection);
				log.info("Active slices: "+slices.size());
				solrurlMap.put("RANDOM_SHARD", slices.get(0).getName());
			}
		}
	}

	private static List<Slice> getActiveSlicedShuffled(CloudSolrClient client, String collection)
			throws KeeperException, InterruptedException {
		List<Slice> slices = new ArrayList();
		client.getZkStateReader().forciblyRefreshAllClusterStateSlow();
		for (Slice s: client.getZkStateReader().getClusterState().getCollection(collection).getSlices()) {
			if (s.getState().equals(Slice.State.ACTIVE)) {
				slices.add(s);
			}
		}
		Collections.shuffle(slices);
		return slices;
	}

	private static class PRS {

		private final String path;
		private final SolrZkClient zkClient;
		private List<String> children;


		private PRS(String path, SolrZkClient zkClient) {
			this.path = path;
			this.zkClient = zkClient;
		}

		public String getState(String replica) throws Exception{
			if(children == null) {
				children = zkClient.getChildren(path, null, true);
			}
			for (String child : children) {
				if(child.startsWith(replica)) {
					return child;
				}
			}
			return null;

		}

		public boolean isActive(String replica) throws Exception {
			String st = getState(replica);
			return st != null && st.contains(":A");
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
			if (ex.startsWith("delay(")) {
				log.info("Executing delay: " + ex);

				int seconds = Integer.valueOf(ex.split("(delay\\()|(\\))|(,)")[1]);
				try {
					log.info("Waiting for a delay of " + seconds + " seconds.");
					Thread.sleep(seconds * 1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			}

			if (ex.startsWith("random(")) {
				String varName = (ex.split("(random\\()|(\\))|(,)")[1]);
				int randomStart = Integer.valueOf(ex.split("(random\\()|(\\))|(,)")[2]);
				int randomEnd = Integer.valueOf(ex.split("(random\\()|(\\))|(,)")[3]);

				int num = randomStart + new Random().nextInt(randomEnd - randomStart + 1);
				globalVariables.get(varName).set(num);

				ret.put(varName, num);
			}

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
