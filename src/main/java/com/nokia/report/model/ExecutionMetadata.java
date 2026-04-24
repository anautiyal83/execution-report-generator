package com.nokia.report.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Metadata section of a per-node execution JSON file.
 *
 * JSON structure:
 * <pre>
 * {
 *   "metadata": {
 *     "nodeName": "MRF1"
 *   },
 *   "data": { ... }
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExecutionMetadata {

    @JsonProperty("nodeName")
    private String nodeName;

    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }
}
