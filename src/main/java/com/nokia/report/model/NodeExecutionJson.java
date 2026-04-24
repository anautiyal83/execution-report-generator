package com.nokia.report.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.LinkedHashMap;

/**
 * Root structure of a per-node execution JSON file.
 *
 * JSON structure:
 * <pre>
 * {
 *   "metadata": {
 *     "nodeName": "MRF1"
 *   },
 *   "data": {
 *     "command string": { ...CommandResultDetail... },
 *     ...
 *   }
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class NodeExecutionJson {

    @JsonProperty("metadata")
    private ExecutionMetadata metadata;

    @JsonProperty("data")
    private LinkedHashMap<String, CommandResultDetail> data;

    public ExecutionMetadata getMetadata() { return metadata; }
    public void setMetadata(ExecutionMetadata metadata) { this.metadata = metadata; }

    public LinkedHashMap<String, CommandResultDetail> getData() { return data; }
    public void setData(LinkedHashMap<String, CommandResultDetail> data) { this.data = data; }
}
