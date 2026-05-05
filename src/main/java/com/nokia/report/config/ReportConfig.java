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
    private String jsonDir;             // directory of per-node JSON files (CR / multi-node mode)
    private String jsonFile;            // single JSON file path (single-node mode); mutually exclusive with jsonDir
    private String outputHtmlPath;
    private String outputHtmlName;
    private String templatePath;        // null = load from classpath (built-in default)
    private String summaryTemplatePath; // null = load from classpath (built-in default)
    private List<String> nodeNames;     // null = include all JSON files found in jsonDir
    private String requestId;
    private String timestamp;
    private String summaryHtmlName;     // filename for summary report
    private boolean generateSummary = true; // false = skip writing summary HTML

    // -------------------------------------------------------------------------
    // Getters
    // -------------------------------------------------------------------------

    public String getNodeType()            { return nodeType; }
    public String getActivity()            { return activity; }
    public String getCrGroup()             { return crGroup; }
    public String getJsonDir()             { return jsonDir; }
    public String getJsonFile()            { return jsonFile; }
    public String getOutputHtmlPath()      { return outputHtmlPath; }
    public String getOutputHtmlName()      { return outputHtmlName; }
    public String getTemplatePath()        { return templatePath; }
    public String getSummaryTemplatePath() { return summaryTemplatePath; }
    public List<String> getNodeNames()     { return nodeNames; }
    public String getRequestId()           { return requestId; }
    public String getTimestamp()           { return timestamp; }
    public String getSummaryHtmlName()     { return summaryHtmlName; }
    public boolean isGenerateSummary()     { return generateSummary; }

    /** Convenience: full detail report path = outputHtmlPath + separator + outputHtmlName */
    public String getOutputHtml() {
        return outputHtmlPath + File.separator + outputHtmlName;
    }

    /** Convenience: full summary report path = outputHtmlPath + separator + summaryHtmlName */
    public String getSummaryHtml() {
        return outputHtmlPath + File.separator + summaryHtmlName;
    }

    // -------------------------------------------------------------------------
    // Setters (for direct use or CLI parsing)
    // -------------------------------------------------------------------------

    public void setNodeType(String nodeType)                       { this.nodeType = nodeType; }
    public void setActivity(String activity)                       { this.activity = activity; }
    public void setCrGroup(String crGroup)                         { this.crGroup = crGroup; }
    public void setJsonDir(String jsonDir)                         { this.jsonDir = jsonDir; }
    public void setJsonFile(String jsonFile)                       { this.jsonFile = jsonFile; }
    public void setOutputHtmlPath(String outputHtmlPath)           { this.outputHtmlPath = outputHtmlPath; }
    public void setOutputHtmlName(String outputHtmlName)           { this.outputHtmlName = outputHtmlName; }
    public void setTemplatePath(String templatePath)               { this.templatePath = templatePath; }
    public void setSummaryTemplatePath(String summaryTemplatePath) { this.summaryTemplatePath = summaryTemplatePath; }
    public void setNodeNames(List<String> nodeNames)               { this.nodeNames = nodeNames; }
    public void setRequestId(String requestId)                     { this.requestId = requestId; }
    public void setTimestamp(String timestamp)                     { this.timestamp = timestamp; }
    public void setSummaryHtmlName(String summaryHtmlName)         { this.summaryHtmlName = summaryHtmlName; }
    public void setGenerateSummary(boolean generateSummary)        { this.generateSummary = generateSummary; }

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
        requireField(outputHtmlPath, "--output-html-path");
        requireField(outputHtmlName, "--output-html-name");

        // If --json-dir points to a file, silently promote it to --json-file
        if ((jsonFile == null || jsonFile.trim().isEmpty())
                && jsonDir != null && !jsonDir.trim().isEmpty()
                && new File(jsonDir.trim()).isFile()) {
            jsonFile = jsonDir;
            jsonDir  = null;
        }

        // Require exactly one of --json-file or --json-dir
        boolean hasFile = jsonFile != null && !jsonFile.trim().isEmpty();
        boolean hasDir  = jsonDir  != null && !jsonDir.trim().isEmpty();
        if (!hasFile && !hasDir) {
            throw new ReportGeneratorException("Required field missing: --json-file or --json-dir");
        }

        // Summary report name is only required when summary generation is enabled
        if (generateSummary) {
            requireField(summaryHtmlName, "--output-summary-name");
        }

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
        boolean hasFile = jsonFile != null && !jsonFile.trim().isEmpty();

        if (hasFile) {
            // --json-file must exist and be a regular file
            File f = new File(jsonFile.trim());
            if (!f.exists()) {
                throw new ReportGeneratorException(
                    "Invalid path: --json-file '" + jsonFile + "' does not exist");
            }
            if (!f.isFile()) {
                throw new ReportGeneratorException(
                    "Invalid path: --json-file '" + jsonFile + "' is not a file");
            }
        } else {
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

        /** Directory containing per-node execution JSON files. Required unless {@code jsonFile} is set. */
        public Builder jsonDir(String jsonDir) {
            config.jsonDir = jsonDir;
            return this;
        }

        /**
         * Single JSON file path for single-node mode. Required unless {@code jsonDir} is set.
         * Mutually exclusive with {@code jsonDir}.
         */
        public Builder jsonFile(String jsonFile) {
            config.jsonFile = jsonFile;
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
         * Path to a custom detail HTML template file. Optional.
         * If not set, the built-in classpath template is used.
         */
        public Builder templatePath(String templatePath) {
            config.templatePath = templatePath;
            return this;
        }

        /**
         * Path to a custom summary HTML template file. Optional.
         * If not set, the built-in classpath summary template is used.
         */
        public Builder summaryTemplatePath(String summaryTemplatePath) {
            config.summaryTemplatePath = summaryTemplatePath;
            return this;
        }

        /** Filename for the generated summary HTML report, e.g. {@code "summary.html"}. Required when generateSummary is true. */
        public Builder summaryHtmlName(String summaryHtmlName) {
            config.summaryHtmlName = summaryHtmlName;
            return this;
        }

        /**
         * Whether to generate the summary HTML report. Defaults to {@code true}.
         * Set to {@code false} to skip writing the summary report (e.g. single-node mode).
         */
        public Builder generateSummary(boolean generateSummary) {
            config.generateSummary = generateSummary;
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
