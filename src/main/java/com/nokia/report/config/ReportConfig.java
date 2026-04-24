package com.nokia.report.config;

import com.nokia.report.ReportGeneratorException;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

/**
 * Input parameters for report generation.
 *
 * <p>Construct via the fluent builder for programmatic use:
 * <pre>
 *   ReportConfig config = ReportConfig.builder()
 *       .nodeType("MRF")
 *       .activity("ANNOUNCEMENT_LOADING")
 *       .crGroup("GroupA")
 *       .jsonDir("/path/to/jsons")
 *       .outputHtmlPath("/path/to/output")
 *       .outputHtmlName("report.html")
 *       .requestId("CR-98765")           // optional
 *       .timestamp("2026-04-19 14:00:00") // optional
 *       .nodeNames(List.of("MRF1","MRF2")) // optional
 *       .build();
 * </pre>
 *
 * <p>CLI arguments (for reference):
 * <pre>
 *   --node-type        MRF
 *   --activity         ANNOUNCEMENT_LOADING
 *   --cr-group         GroupA
 *   --json-dir         /path/to/execution/jsons
 *   --output-html-path /path/to/output/directory
 *   --output-html-name report.html
 *   --template         /path/to/mop_execution_report_template.html  (optional)
 *   --node-names       node1,node2,node3                            (optional)
 *   --request-id       CR-12345                                     (optional)
 *   --timestamp        "2026-04-18 10:00:00"                        (optional)
 * </pre>
 */
public class ReportConfig {

    private String nodeType;
    private String activity;
    private String crGroup;
    private String jsonDir;
    private String outputHtmlPath;
    private String outputHtmlName;
    private String templatePath;      // null = load from classpath (built-in default)
    private List<String> nodeNames;   // null = include all JSON files found in jsonDir
    private String requestId;
    private String timestamp;

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getNodeType()        { return nodeType; }
    public String getActivity()        { return activity; }
    public String getCrGroup()         { return crGroup; }
    public String getJsonDir()         { return jsonDir; }
    public String getOutputHtmlPath()  { return outputHtmlPath; }
    public String getOutputHtmlName()  { return outputHtmlName; }
    public String getTemplatePath()    { return templatePath; }
    public List<String> getNodeNames() { return nodeNames; }
    public String getRequestId()       { return requestId; }
    public String getTimestamp()       { return timestamp; }

    /** Convenience: full output file path = outputHtmlPath + separator + outputHtmlName */
    public String getOutputHtml() {
        return outputHtmlPath + File.separator + outputHtmlName;
    }

    // -------------------------------------------------------------------------
    // Setters (for direct use or CLI parsing)
    // -------------------------------------------------------------------------

    public void setNodeType(String nodeType)               { this.nodeType = nodeType; }
    public void setActivity(String activity)               { this.activity = activity; }
    public void setCrGroup(String crGroup)                 { this.crGroup = crGroup; }
    public void setJsonDir(String jsonDir)                 { this.jsonDir = jsonDir; }
    public void setOutputHtmlPath(String outputHtmlPath)   { this.outputHtmlPath = outputHtmlPath; }
    public void setOutputHtmlName(String outputHtmlName)   { this.outputHtmlName = outputHtmlName; }
    public void setTemplatePath(String templatePath)       { this.templatePath = templatePath; }
    public void setNodeNames(List<String> nodeNames)       { this.nodeNames = nodeNames; }
    public void setRequestId(String requestId)             { this.requestId = requestId; }
    public void setTimestamp(String timestamp)             { this.timestamp = timestamp; }

    // -------------------------------------------------------------------------
    // Validation
    // -------------------------------------------------------------------------

    /**
     * Validate that all required fields are set and all paths are accessible.
     *
     * @throws ReportGeneratorException if any required field is missing or a path is invalid
     */
    public void validate() throws ReportGeneratorException {
        requireField(nodeType,       "--node-type");
        requireField(activity,       "--activity");
        requireField(crGroup,        "--cr-group");
        requireField(jsonDir,        "--json-dir");
        requireField(outputHtmlPath, "--output-html-path");
        requireField(outputHtmlName, "--output-html-name");

        // Apply defaults for optional fields
        if (requestId == null || requestId.trim().isEmpty()) requestId = "N/A";
        if (timestamp  == null || timestamp.trim().isEmpty())
            timestamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());

        validatePaths();
    }

    private void requireField(String value, String argName) throws ReportGeneratorException {
        if (value == null || value.trim().isEmpty()) {
            throw new ReportGeneratorException("Required field missing: " + argName);
        }
    }

    private void validatePaths() throws ReportGeneratorException {
        // --json-dir must exist and be a directory
        File jsonDirFile = new File(jsonDir);
        if (!jsonDirFile.exists()) {
            throw new ReportGeneratorException(
                "Invalid path: --json-dir '" + jsonDir + "' does not exist");
        }
        if (!jsonDirFile.isDirectory()) {
            throw new ReportGeneratorException(
                "Invalid path: --json-dir '" + jsonDir + "' is not a directory");
        }

        // --output-html-path must exist as a directory or be creatable
        File outputDir = new File(outputHtmlPath);
        if (!outputDir.exists()) {
            if (!outputDir.mkdirs()) {
                throw new ReportGeneratorException(
                    "Invalid path: --output-html-path '" + outputHtmlPath
                    + "' does not exist and could not be created");
            }
        } else if (!outputDir.isDirectory()) {
            throw new ReportGeneratorException(
                "Invalid path: --output-html-path '" + outputHtmlPath + "' is not a directory");
        }
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    /** Return a new builder for constructing a {@code ReportConfig}. */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link ReportConfig}.
     *
     * <p>Required fields: {@code nodeType}, {@code activity}, {@code crGroup},
     * {@code jsonDir}, {@code outputHtmlPath}, {@code outputHtmlName}.
     * All other fields are optional.
     */
    public static final class Builder {

        private final ReportConfig config = new ReportConfig();

        private Builder() {}

        /** Node type label, e.g. {@code "MRF"}. Required. */
        public Builder nodeType(String nodeType) {
            config.nodeType = nodeType;
            return this;
        }

        /** Activity name, e.g. {@code "ANNOUNCEMENT_LOADING"}. Required. */
        public Builder activity(String activity) {
            config.activity = activity;
            return this;
        }

        /** CR group name, e.g. {@code "GroupA"}. Required. */
        public Builder crGroup(String crGroup) {
            config.crGroup = crGroup;
            return this;
        }

        /** Directory containing per-node execution JSON files. Required. */
        public Builder jsonDir(String jsonDir) {
            config.jsonDir = jsonDir;
            return this;
        }

        /** Directory where the HTML report will be written. Required. */
        public Builder outputHtmlPath(String outputHtmlPath) {
            config.outputHtmlPath = outputHtmlPath;
            return this;
        }

        /** Filename for the generated HTML report, e.g. {@code "report.html"}. Required. */
        public Builder outputHtmlName(String outputHtmlName) {
            config.outputHtmlName = outputHtmlName;
            return this;
        }

        /**
         * Path to a custom HTML template file. Optional.
         * If not set, the built-in classpath template is used.
         */
        public Builder templatePath(String templatePath) {
            config.templatePath = templatePath;
            return this;
        }

        /**
         * Ordered list of node names to include in the report. Optional.
         * If not set, all JSON files in {@code jsonDir} are included.
         * Names are matched against {@code metadata.nodeName} in each JSON file.
         */
        public Builder nodeNames(List<String> nodeNames) {
            config.nodeNames = nodeNames;
            return this;
        }

        /**
         * Change Request ID shown in the metadata bar. Optional. Defaults to {@code "N/A"}.
         */
        public Builder requestId(String requestId) {
            config.requestId = requestId;
            return this;
        }

        /**
         * Report generation timestamp. Optional. Defaults to the current date/time.
         * Format: {@code "yyyy-MM-dd HH:mm:ss"}.
         */
        public Builder timestamp(String timestamp) {
            config.timestamp = timestamp;
            return this;
        }

        /**
         * Build and return the configured {@link ReportConfig}.
         * This does not validate — call {@link ReportConfig#validate()} or let
         * {@link com.nokia.report.ReportGenerator#generate(ReportConfig)} validate it.
         */
        public ReportConfig build() {
            return config;
        }
    }
}
