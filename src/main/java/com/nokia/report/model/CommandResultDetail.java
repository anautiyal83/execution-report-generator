package com.nokia.report.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mirrors the JSON structure written by network-command-executor's ReportWriter.
 *
 * JSON field names match the @SerializedName values used by the executor:
 * {
 *   "phase":                     "PRE_NODE_HEALTH_CHECK",
 *   "target":                   "...",
 *   "command":                  "...",
 *   "description":              "...",
 *   "success":                  true,
 *   "reason":                   "...",
 *   "validate":                 false,
 *   "validation_criteria":      "...",
 *   "validation_status":        "SKIPPED",
 *   "validation_conclusion":     "...",
 *   "output":                   "..."
 * }
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CommandResultDetail {

    @JsonProperty("phase")
    private String phase;

    @JsonProperty("target")
    private String target;

    @JsonProperty("command")
    private String command;

    @JsonProperty("description")
    private String description;

    @JsonProperty("success")
    private boolean success;

    @JsonProperty("reason")
    private String reason;

    @JsonProperty("failure")
    private String failure;

    @JsonProperty("validate")
    private boolean validate;

    @JsonProperty("validation_criteria")
    private String validationCriteria;

    @JsonProperty("validation_status")
    private String validationStatus;

    @JsonProperty("validation_conclusion")
    private String validationConclusion;

    @JsonProperty("output")
    private String output;

    @JsonProperty("startDateAndTime")
    private String startDateAndTime;

    @JsonProperty("endDateAndTime")
    private String endDateAndTime;

    public String getPhase()                  { return phase; }
    public String getTarget()                 { return target; }
    public String getCommand()                { return command; }
    public String getDescription()            { return description; }
    public boolean isSuccess()                { return success; }
    public String getReason()                 { return reason; }
    public String getFailure()                { return failure; }
    public boolean isValidate()               { return validate; }
    public String getValidationCriteria()     { return validationCriteria; }
    public String getValidationStatus()       { return validationStatus; }
    public String getValidationConclusion()   { return validationConclusion; }
    public String getOutput()                 { return output; }
    public String getStartDateAndTime()       { return startDateAndTime; }
    public String getEndDateAndTime()         { return endDateAndTime; }
}
