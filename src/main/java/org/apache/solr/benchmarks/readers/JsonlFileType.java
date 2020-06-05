package org.apache.solr.benchmarks.readers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.zip.GZIPInputStream;

import org.apache.solr.common.SolrInputDocument;
import org.apache.solr.common.util.Utils;

public class JsonlFileType implements DocsFileStreamer {
    final String type;

    public JsonlFileType(String type) {
        this.type = type;
    }


    @Override
    public void stream(File datasetFile, Sink<SolrInputDocument> sink) throws IOException {
        BufferedReader br = getBufferedReader(datasetFile);

        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (line.isEmpty()) continue;
            SolrInputDocument doc = createDoc(line);
            if (!sink.item(doc)) break;
        }
        br.close();
    }

    static SolrInputDocument createDoc(String line) {
        Map m = (Map) Utils.fromJSONString(line);
        SolrInputDocument doc = new SolrInputDocument();
        m.forEach((k, v) -> doc.addField(String.valueOf(k), v));
        return doc;
    }

    public static BufferedReader getBufferedReader(File datasetFile) throws IOException {
        String fName = datasetFile.getName();
        BufferedReader br = null;

        if (fName.endsWith(".json")) {
            br = new BufferedReader(new FileReader(datasetFile));

        } else if (fName.endsWith(".gz")) {
            GZIPInputStream gzip = new GZIPInputStream(new FileInputStream(datasetFile));
            br = new BufferedReader(new InputStreamReader(gzip));

        }
        return br;
    }

    public static void main(String[] args) throws IOException {
        File file = new File("/tmp/1ENq.json.gz");
        new JsonlFileType("jsonl").stream(file, new Sink<SolrInputDocument>() {
            int count = 0;

            @Override
            public boolean item(SolrInputDocument doc) {
                System.out.println("id " + count + " : "+ doc.getFieldValue("Id"));
                count++;
                return count <= 100;
            }
        });
    }
}
