package com.nokia.report.reader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nokia.report.model.CommandResultDetail;
import com.nokia.report.model.ExecutionMetadata;
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
            boolean notExecuted = raw.getData() == null;
            NodeExecutionData data = new NodeExecutionData(nodeName, raw.getData());
            if (notExecuted) data.setNotExecuted(true);
            applyTiming(data, raw);
            dataMap.put(nodeName, data);
            if (notExecuted) {
                log.info("Node '{}' loaded as NOT_EXECUTED from {}", nodeName, f.getName());
            } else {
                log.info("Loaded {} commands for node '{}' from {}", raw.getData().size(), nodeName, f.getName());
            }
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

    /**
     * Load a single execution JSON file (single-node mode).
     *
     * @param jsonFilePath absolute path to the JSON file
     * @return list containing exactly one NodeExecutionData
     */
    public List<NodeExecutionData> readSingle(String jsonFilePath) throws IOException {
        File f = new File(jsonFilePath);
        if (!f.exists() || !f.isFile()) {
            throw new IOException("json-file not found: " + jsonFilePath);
        }
        NodeExecutionJson raw = parseFile(f);
        String nodeName = resolveNodeName(raw, f);
        boolean notExecuted = raw.getData() == null;
        NodeExecutionData data = new NodeExecutionData(nodeName, raw.getData());
        if (notExecuted) {
            data.setNotExecuted(true);
            log.info("Node '{}' loaded as NOT_EXECUTED from {}", nodeName, f.getName());
        } else {
            log.info("Loaded {} commands for node '{}' from {}", raw.getData().size(), nodeName, f.getName());
        }
        applyTiming(data, raw);
        List<NodeExecutionData> result = new ArrayList<>();
        result.add(data);
        return result;
    }

    private NodeExecutionJson parseFile(File f) throws IOException {
        try {
            NodeExecutionJson raw = mapper.readValue(f, NodeExecutionJson.class);
            if (raw.getData() == null) {
                String status = raw.getMetadata() != null ? raw.getMetadata().getOverallStatus() : null;
                if (!"NOT_EXECUTED".equalsIgnoreCase(status)) {
                    throw new IOException("Missing 'data' section in: " + f.getName());
                }
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

    private static void applyTiming(NodeExecutionData data, NodeExecutionJson raw) {
        // Primary: use metadata.execution_summary if present and populated
        if (raw.getMetadata() != null && raw.getMetadata().getExecutionSummary() != null) {
            ExecutionMetadata.ExecutionSummary summary = raw.getMetadata().getExecutionSummary();
            if (summary.getStartDateAndTime() != null && !summary.getStartDateAndTime().isEmpty()) {
                data.setActivityStartTime(summary.getStartDateAndTime());
            }
            if (summary.getEndDateAndTime() != null && !summary.getEndDateAndTime().isEmpty()) {
                data.setActivityEndTime(summary.getEndDateAndTime());
            }
        }
        // Fallback: derive from the first command's start and last command's end
        if ((data.getActivityStartTime() == null || data.getActivityEndTime() == null)
                && raw.getData() != null && !raw.getData().isEmpty()) {
            List<CommandResultDetail> cmds = new ArrayList<>(raw.getData().values());
            if (data.getActivityStartTime() == null) {
                data.setActivityStartTime(cmds.get(0).getStartDateAndTime());
            }
            if (data.getActivityEndTime() == null) {
                data.setActivityEndTime(cmds.get(cmds.size() - 1).getEndDateAndTime());
            }
        }
    }

    private static String stem(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(0, dot) : filename;
    }
}
