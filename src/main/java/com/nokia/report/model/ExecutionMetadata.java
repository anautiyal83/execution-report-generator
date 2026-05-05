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
 *     "nodeName": "MRF1",
 *     "execution_summary": {
 *       "startDateAndTime": "2026-05-05 11:21:29",
 *       "endDateAndTime":   "2026-05-05 11:21:47"
 *     }
 *   },
 *   "data": { ... }
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExecutionMetadata {

    @JsonProperty("nodeName")
    private String nodeName;

    @JsonProperty("execution_summary")
    private ExecutionSummary executionSummary;

    public String getNodeName() { return nodeName; }
    public void setNodeName(String nodeName) { this.nodeName = nodeName; }

    public ExecutionSummary getExecutionSummary() { return executionSummary; }
    public void setExecutionSummary(ExecutionSummary executionSummary) { this.executionSummary = executionSummary; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ExecutionSummary {

        @JsonProperty("startDateAndTime")
        private String startDateAndTime;

        @JsonProperty("endDateAndTime")
        private String endDateAndTime;

        public String getStartDateAndTime() { return startDateAndTime; }
        public void setStartDateAndTime(String startDateAndTime) { this.startDateAndTime = startDateAndTime; }

        public String getEndDateAndTime() { return endDateAndTime; }
        public void setEndDateAndTime(String endDateAndTime) { this.endDateAndTime = endDateAndTime; }
    }
}
