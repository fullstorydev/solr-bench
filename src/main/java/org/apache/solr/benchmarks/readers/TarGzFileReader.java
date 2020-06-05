package org.apache.solr.benchmarks.readers;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.function.Predicate;

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

public class TarGzFileReader {
    public static void readFilesFromZip(File f, Predicate<String> fileNamePredicate,
                                         Sink<String> jsonObjSink) throws IOException {
        FileInputStream fis = new FileInputStream(f);

        TarArchiveInputStream tis = new TarArchiveInputStream(new GzipCompressorInputStream(fis));
        TarArchiveEntry tarEntry = null;
        byte[] buffer = new byte[0];
        while ((tarEntry = tis.getNextTarEntry()) != null) {
            if (tarEntry.isDirectory()) {
                continue;
            } else {
                int size = (int) tarEntry.getSize();
                if (buffer.length < size) {
                    buffer = new byte[size];
                }
                tis.read(buffer, 0, size);
                if (!fileNamePredicate.test(tarEntry.getName())) continue;
                if (!jsonObjSink.item(new String(buffer, 0, size, UTF_8))) break;
            }
        }
        tis.close();

    }
}
