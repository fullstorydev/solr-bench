package org.apache.solr.benchmarks.readers;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.zip.GZIPInputStream;

import org.apache.commons.compress.compressors.lzma.LZMACompressorInputStream;
import org.apache.solr.common.SolrInputDocument;

public class TsvFile implements DocsFileStreamer {
    @Override
    public void stream(File datasetFile, Sink<SolrInputDocument> sink) throws IOException {

        String fileName = datasetFile.getName();
        BufferedReader br;
        if (fileName.endsWith(".lzma") || fileName.endsWith(".xz")) {
            LZMACompressorInputStream lzma = new LZMACompressorInputStream(new FileInputStream(datasetFile));
            br = new BufferedReader(new InputStreamReader(lzma));
        } else if (fileName.endsWith(".gz")) {
            GZIPInputStream gzip = new GZIPInputStream(new FileInputStream(datasetFile));
            br = new BufferedReader(new InputStreamReader(gzip));
        } else {
            br = new BufferedReader(new FileReader(datasetFile));
        }
        String line;
        int counter = 0;
        while ((line = br.readLine()) != null) {
            counter++;

            String fields[] = line.split("\t");
            int id = counter;
            String title = fields[0];
            String date = fields[1];
            String text = fields[2];

            SolrInputDocument doc = new SolrInputDocument("id", String.valueOf(id),
                    "title", title, "timestamp_s", date, "text_t", text);
            if(!sink.item(doc)) break;
        }
        br.close();

    }


}
