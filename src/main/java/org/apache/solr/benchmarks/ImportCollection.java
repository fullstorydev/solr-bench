package org.apache.solr.benchmarks;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.solr.benchmarks.beans.IndexBenchmark;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.request.CollectionAdminRequest;
import org.apache.solr.client.solrj.response.CollectionAdminResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImportCollection {

    private static final Logger LOGGER = LoggerFactory.getLogger(ImportCollection.class);

    private static void importCollection(String baseUrl, String collectionName, String configName, int shards, int replicas, String idFieldName, String datasetPath) throws Exception {
        Configurator.setRootLevel(Level.INFO);
        try(HttpSolrClient solrClient = new HttpSolrClient.Builder(baseUrl).build()) {
            try {
                CollectionAdminRequest.Delete.deleteCollection(collectionName).process(solrClient);
            } catch(Exception e) {
                LOGGER.error("failed to delete collection {}:", collectionName, e);
            }
            CollectionAdminResponse created = CollectionAdminRequest.Create.createCollection(collectionName, configName, shards, replicas).process(solrClient);
            LOGGER.info("created collection: {}", created.jsonStr());
            IndexBenchmark benchmark = new IndexBenchmark();
            benchmark.idField = idFieldName;
            benchmark.datasetFile = datasetPath;
            BenchmarksMain.indexJson(baseUrl, collectionName, 6, benchmark);
        }
    }

    public static void main(String[] args) throws Exception {
        importCollection("http://localhost:8983/solr/", "loadtest", "_FS4", 1, 1, "Id", "/Users/shan/Downloads/fs_single.json.gz");
    }
}
