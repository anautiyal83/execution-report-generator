package com.nokia.report.writer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Writes the final rendered HTML to disk.
 */
public class ReportWriter {

    private static final Logger log = LoggerFactory.getLogger(ReportWriter.class);

    public void write(String html, String outputPath) throws IOException {
        Path path = Paths.get(outputPath);
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        Files.write(path, html.getBytes(StandardCharsets.UTF_8));
        log.info("Report written to '{}' ({} chars)", path.toAbsolutePath(), html.length());
    }
}
