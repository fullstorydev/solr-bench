package org.apache.solr.benchmarks.indexing;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.apache.solr.benchmarks.BenchmarksMain;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.invoke.MethodHandles;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicLong;

public class UploadDocs implements Callable {
    private static final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    final List<String> docs;
    final HttpClient client;
    final String leaderUrl;
    private AtomicLong totalUploadedDocs;
    private static final AtomicLong debugCounter = new AtomicLong();

    UploadDocs(List<String> docs, HttpClient client, String leaderUrl, AtomicLong totalUploadedDocs) {
        this.docs = docs;
        this.client = client;
        this.leaderUrl = leaderUrl;
        this.totalUploadedDocs = totalUploadedDocs;
    }

    @Override
    public Object call() throws IOException {
        HttpPost httpPost = new HttpPost(leaderUrl);
        httpPost.setHeader(new BasicHeader("Content-Type", "application/json; charset=UTF-8"));
        httpPost.getParams().setParameter("overwrite", "false");

        httpPost.setEntity(new BasicHttpEntity() {
            @Override
            public boolean isStreaming() {
                return true;
            }

            @Override
            public void writeTo(OutputStream outstream) throws IOException {
                OutputStreamWriter writer = new OutputStreamWriter(outstream);
                for (String doc : docs) {
                    writer.append(doc).append('\n');
                }
                writer.flush();
            }
        });


        HttpResponse rsp = client.execute(httpPost);
        int statusCode = rsp.getStatusLine().getStatusCode();
        if (statusCode != 200) {
            log.error("Failed a request: " +
                    rsp.getStatusLine() + " " + EntityUtils.toString(rsp.getEntity(), StandardCharsets.UTF_8));
        } else {
            totalUploadedDocs.addAndGet(docs.size());
        }

        return null;
    }
}
