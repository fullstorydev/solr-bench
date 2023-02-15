package org.apache.solr.benchmarks.indexing;

import org.apache.http.client.HttpClient;
import org.apache.solr.benchmarks.BenchmarksMain;
import org.apache.solr.benchmarks.beans.IndexBenchmark;
import org.apache.solr.common.cloud.DocCollection;
import org.apache.solr.common.cloud.Slice;
import org.apache.solr.common.util.JsonRecordReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringReader;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class IndexBatchSupplier implements Supplier<Callable>, AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private final Future<?> workerFuture;
    private volatile boolean exit;
    private IndexBenchmark benchmark;
    private DocCollection docCollection;
    private HttpClient httpClient;
    private Map<String, String> shardVsLeader;
    private BlockingQueue<UploadDocs> pendingBatches = new LinkedBlockingQueue<>(10); //at most 10 pending batches

    private AtomicLong docsIndexed = new AtomicLong();

    public IndexBatchSupplier(DocReader docReader, IndexBenchmark benchmark, DocCollection docCollection, HttpClient httpClient, Map<String, String> shardVsLeader) {
        this.benchmark = benchmark;
        this.docCollection = docCollection;

        this.httpClient = httpClient;
        this.shardVsLeader = shardVsLeader;

        this.workerFuture = startWorker(docReader);
    }

    class IdParser implements JsonRecordReader.Handler {
        String idParsed;

        @Override
        public void handle(Map<String, Object> record, String path) {
            idParsed = record.get(benchmark.idField) instanceof String ?
                    (String) record.get(benchmark.idField) :
                    record.get(benchmark.idField).toString();
        }
    }

    private Future<?> startWorker(DocReader docReader) {
        ExecutorService executorService = Executors.newSingleThreadExecutor();
        //a continuously running job until either all input is exhausted or exit is flagged
        Future<?> workerFuture = executorService.submit(() -> {
            List<String> inputDocs;
            JsonRecordReader rdr = JsonRecordReader.getInst("/", Collections.singletonList(benchmark.idField + ":/" + benchmark.idField));
            IdParser idParser = new IdParser();
            Map<String, List<String>> shardVsDocs = new HashMap<>();
            try {
                while (!exit && (inputDocs = docReader.readDocs(benchmark.batchSize)) != null) { //can read more than batch size, just use batch size as a sensible value
                    for (String inputDoc : inputDocs) {
                        rdr.streamRecords(new StringReader(inputDoc), idParser);
                        Slice targetSlice = docCollection.getRouter().getTargetSlice(idParser.idParsed, null, null, null, docCollection);
                        List<String> shardDocs = shardVsDocs.get(targetSlice.getName());
                        if (shardDocs == null) {
                            shardVsDocs.put(targetSlice.getName(), shardDocs = new ArrayList<>(benchmark.batchSize));
                        }
                        shardDocs.add(inputDoc);
                        if (shardDocs.size() >= benchmark.batchSize) {
                            shardVsDocs.remove(targetSlice.getName());

                            //a shard has accumulated enough docs to be executed
                            UploadDocs uploadDocs = new UploadDocs(shardDocs, httpClient, shardVsLeader.get(targetSlice.getName()), docsIndexed);
                            while (!exit && !pendingBatches.offer(uploadDocs, 1, TimeUnit.SECONDS)) {
                                //try again
                            }
                        }
                    }
                }
                shardVsDocs.forEach((shard, docs) -> { //flush the remaining ones
                    try {
                        UploadDocs uploadDocs = new UploadDocs(docs, httpClient, shardVsLeader.get(shard), docsIndexed);
                        while (!exit && !pendingBatches.offer(uploadDocs, 1, TimeUnit.SECONDS)) {
                            //try again
                        }
                    } catch (InterruptedException e) {
                        log.warn(e.getMessage(), e);
                    }
                });
            } catch (IOException e) {
                log.warn("IO Exception while reading input docs " + e.getMessage(), e);
            } finally {
                exit = true;
            }
            return null;
        });

        executorService.shutdown();
        return workerFuture;
    }

    @Override
    public Callable get() {
        try {
            UploadDocs batch = null;
            while ((batch = pendingBatches.poll(1, TimeUnit.SECONDS)) == null && !exit) {
            }
            if (batch == null) { //rare race condition can fill the queue even if above loop exits, just try it once last time...
                batch = pendingBatches.poll(1, TimeUnit.SECONDS);
            }

            return batch;
        } catch (InterruptedException e) {
            log.warn("Cannot get the pending batches " + e.getMessage(), e);
            return null;
        }
    }

    @Override
    public void close() throws Exception {
        exit = true;
        workerFuture.get(); //this could throw exception if there are any unhandled exceptions in UploadDocs execution
    }

    public long getDocsIndexed() {
        return docsIndexed.get();
    }
}
