package com.nokia.report.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nokia.report.model.NodeExecutionData;
import com.nokia.report.model.NodeExecutionJson;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

/**
 * Reads per-node execution JSON files from a directory.
 *
 * Expected JSON structure per file:
 * <pre>
 * {
 *   "metadata": { "nodeName": "MRF1" },
 *   "data":     { "command string": { ...CommandResultDetail... }, ... }
 * }
 * </pre>
 *
 * Node name is always taken from metadata.nodeName — the filename is used only
 * for file discovery and log messages, never as the display name in the report.
 *
 * If --node-names is supplied, files are filtered by their metadata nodeName;
 * otherwise all JSON files in the directory are loaded.
 */
public class NodeJsonReader {

    private static final Logger log = LoggerFactory.getLogger(NodeJsonReader.class);

    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * Load execution JSON files from jsonDir.
     *
     * @param jsonDir   directory containing per-node JSON files
     * @param nodeNames ordered list of node names to include (matched against metadata.nodeName);
     *                  null = include all JSON files found in jsonDir
     * @return list of NodeExecutionData in the requested order
     */
    public List<NodeExecutionData> read(String jsonDir, List<String> nodeNames) throws IOException {
        File dir = new File(jsonDir);
        if (!dir.isDirectory()) {
            throw new IOException("json-dir is not a directory: " + jsonDir);
        }

        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".json"));
        if (files == null || files.length == 0) {
            throw new IOException("No JSON files found in: " + jsonDir);
        }
        Arrays.sort(files);

        // Parse every file; build map: metadata.nodeName → NodeExecutionData
        LinkedHashMap<String, NodeExecutionData> dataMap = new LinkedHashMap<>();
        for (File f : files) {
            NodeExecutionJson raw = parseFile(f);
            String nodeName = resolveNodeName(raw, f);
            if (dataMap.containsKey(nodeName)) {
                log.warn("Duplicate nodeName '{}' from file '{}' — skipping", nodeName, f.getName());
                continue;
            }
            NodeExecutionData data = new NodeExecutionData(nodeName, raw.getData());
            dataMap.put(nodeName, data);
            log.info("Loaded {} commands for node '{}' from {}", raw.getData().size(), nodeName, f.getName());
        }

        // Filter / order by requested node names, or return all in file order
        if (nodeNames != null && !nodeNames.isEmpty()) {
            List<NodeExecutionData> result = new ArrayList<>();
            for (String name : nodeNames) {
                NodeExecutionData d = dataMap.get(name);
                if (d != null) {
                    result.add(d);
                } else {
                    log.warn("No JSON file found with metadata.nodeName='{}' in {}", name, jsonDir);
                }
            }
            if (result.isEmpty()) {
                throw new IOException("None of the requested node names were found in: " + jsonDir);
            }
            return result;
        }

        return new ArrayList<>(dataMap.values());
    }

    private NodeExecutionJson parseFile(File f) throws IOException {
        try {
            NodeExecutionJson raw = mapper.readValue(f, NodeExecutionJson.class);
            if (raw.getData() == null) {
                throw new IOException("Missing 'data' section in: " + f.getName());
            }
            return raw;
        } catch (IOException e) {
            throw new IOException("Failed to parse JSON file '" + f.getName() + "': " + e.getMessage(), e);
        }
    }

    private String resolveNodeName(NodeExecutionJson raw, File f) {
        if (raw.getMetadata() != null
                && raw.getMetadata().getNodeName() != null
                && !raw.getMetadata().getNodeName().trim().isEmpty()) {
            return raw.getMetadata().getNodeName().trim();
        }
        // Metadata or nodeName missing — warn and fall back to filename stem
        log.warn("metadata.nodeName missing in '{}' — falling back to filename", f.getName());
        return stem(f.getName());
    }

    private static String stem(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(0, dot) : filename;
    }
}
