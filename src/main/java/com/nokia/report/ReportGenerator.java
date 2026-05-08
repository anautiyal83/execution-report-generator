package com.nokia.report;

import com.nokia.report.builder.HtmlFragmentBuilder;
import com.nokia.report.builder.SummaryHtmlFragmentBuilder;
import com.nokia.report.config.ReportConfig;
import com.nokia.report.model.ExecutionStats;
import com.nokia.report.model.NodeExecutionData;
import com.nokia.report.model.ReportGeneratorResult;
import com.nokia.report.reader.NodeJsonReader;
import com.nokia.report.template.TemplateEngine;
import com.nokia.report.writer.ReportWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Public API for generating a MOP execution HTML report.
 *
 * <p>Programmatic usage:
 * <pre>
 *   ReportConfig config = ReportConfig.builder()
 *       .nodeType("MRF")
 *       .activity("ANNOUNCEMENT_LOADING")
 *       .crGroup("GroupA")
 *       .jsonDir("/path/to/jsons")
 *       .outputHtmlPath("/path/to/output")
 *       .outputHtmlName("report.html")
 *       .requestId("CR-98765")
 *       .build();
 *
 *   ReportGeneratorResult result = new ReportGenerator().generate(config);
 *   if (result.isSuccess()) {
 *       System.out.println("Report: " + result.getParameters().get("REPORT_FILENAME"));
 *   }
 * </pre>
 *
 * <p>All fields on {@link ReportConfig} are validated before generation begins.
 * A {@link ReportGeneratorException} is thrown on any error (missing args, bad paths,
 * unparseable JSON, template errors, IO failures).
 */
public class ReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerator.class);

    /**
     * Generate a MOP execution HTML report from the given configuration.
     *
     * @param config fully-populated report configuration
     * @return result containing status, error count, and report filename on success
     * @throws ReportGeneratorException if configuration is invalid or generation fails
     */
    public ReportGeneratorResult generate(ReportConfig config) throws ReportGeneratorException {
        try {
            config.validate();
            return doGenerate(config);
        } catch (ReportGeneratorException e) {
            throw e;
        } catch (Exception e) {
            throw new ReportGeneratorException(e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    private ReportGeneratorResult doGenerate(ReportConfig config) throws Exception {
        // 1. Load per-node JSON files (single-file or directory mode)
        NodeJsonReader reader = new NodeJsonReader();
        List<NodeExecutionData> nodes = config.getJsonFile() != null
                ? reader.readSingle(config.getJsonFile())
                : reader.read(config.getJsonDir(), config.getNodeNames());
        log.info("Loaded {} node(s): {}", nodes.size(), nodeNames(nodes));

        // 2. Compute overall stats
        boolean singleNode = config.getJsonFile() != null;
        int total  = nodes.size();
        List<NodeExecutionData> failedNodes      = new ArrayList<>();
        List<NodeExecutionData> notExecutedNodes = new ArrayList<>();
        for (NodeExecutionData n : nodes) {
            ExecutionStats s = ExecutionStats.from(n);
            if (s.isNotExecuted()) notExecutedNodes.add(n);
            else if (!s.isSuccess()) failedNodes.add(n);
        }
        int passed      = total - failedNodes.size() - notExecutedNodes.size();
        int failed      = failedNodes.size();

        // 3. Shared metadata values
        String nodeTypeSafe  = HtmlFragmentBuilder.esc(config.getNodeType());
        String activitySafe  = HtmlFragmentBuilder.esc(config.getActivity());
        String crGroupSafe   = HtmlFragmentBuilder.esc(config.getCrGroup());
        String timestamp     = HtmlFragmentBuilder.esc(config.getTimestamp());
        String requestId     = HtmlFragmentBuilder.esc(config.getRequestId());

        // ── 4a. Summary report (all nodes) ───────────────────────────────────
        String summaryFilename = null;
        if (config.isGenerateSummary()) {
            TemplateEngine summaryEngine = config.getSummaryTemplatePath() != null
                    ? new TemplateEngine(config.getSummaryTemplatePath())
                    : TemplateEngine.fromClasspathResource("mop_execution_summary_report_template.html");

            SummaryHtmlFragmentBuilder summaryBuilder = new SummaryHtmlFragmentBuilder(summaryEngine);
            Map<String, String> summaryValues = new LinkedHashMap<>();
            summaryValues.put("PAGE_TITLE",             nodeTypeSafe + " " + activitySafe + " \u2014 MOP Summary Report");
            summaryValues.put("REPORT_HEADER",          nodeTypeSafe + " \u2014 " + activitySafe);
            summaryValues.put("CR_GROUP_SUBTITLE",      "CR Group: " + crGroupSafe);
            summaryValues.put("META_GENERATED_ON",      timestamp);
            summaryValues.put("META_REQUEST_ID",        requestId);
            summaryValues.put("META_NODE_TYPE",         nodeTypeSafe);
            summaryValues.put("META_ACTIVITY",          activitySafe);
            summaryValues.put("META_CR_GROUP",          crGroupSafe);
            summaryValues.put("TOTAL_NODES",            String.valueOf(total));
            summaryValues.put("PASSED_NODES",           String.valueOf(passed));
            summaryValues.put("FAILED_NODES",           String.valueOf(failed));
            summaryValues.put("SUMMARY_PHASE_HEADERS",   summaryBuilder.buildSummaryPhaseHeaders(nodes));
            summaryValues.put("SUMMARY_TABLE_ROWS",      summaryBuilder.buildSummaryTableRows(nodes));

            String summaryHtml = summaryEngine.render(summaryValues);
            new ReportWriter().write(summaryHtml, config.getSummaryHtml());
            summaryFilename = new File(config.getSummaryHtml()).getName();
            log.info("Summary report generated: {}", summaryFilename);
        } else {
            log.info("Summary report skipped (generate-summary=false)");
        }

        // ── 4b. Detail report ────────────────────────────────────────────────
        // Single-node: always generate, use single-node template (phase summary + detail).
        // Multi-node:  generate only when there are failed nodes, use standard detail template.
        //              NOT_EXECUTED nodes are excluded from detail (no commands to show).
        List<NodeExecutionData> detailNodes = singleNode ? nodes : failedNodes;
        String detailFilename = null;
        if (!detailNodes.isEmpty()) {
            TemplateEngine detailEngine;
            if (singleNode) {
                detailEngine = config.getTemplatePath() != null
                        ? new TemplateEngine(config.getTemplatePath())
                        : TemplateEngine.fromClasspathResource("mop_execution_single_node_report_template.html");
            } else {
                detailEngine = config.getTemplatePath() != null
                        ? new TemplateEngine(config.getTemplatePath())
                        : new TemplateEngine();
            }

            HtmlFragmentBuilder detailBuilder = new HtmlFragmentBuilder(detailEngine);
            Map<String, String> detailValues = new LinkedHashMap<>();
            detailValues.put("PAGE_TITLE",           nodeTypeSafe + " " + activitySafe + " \u2014 MOP Execution Report");
            detailValues.put("REPORT_HEADER",        nodeTypeSafe + " \u2014 " + activitySafe);
            detailValues.put("CR_GROUP_SUBTITLE",    "CR Group: " + crGroupSafe);
            detailValues.put("META_GENERATED_ON",    timestamp);
            detailValues.put("META_REQUEST_ID",      requestId);
            detailValues.put("META_NODE_TYPE",       nodeTypeSafe);
            detailValues.put("META_ACTIVITY",        activitySafe);
            detailValues.put("META_CR_GROUP",        crGroupSafe);

            if (singleNode) {
                // Phase summary table for the single node
                SummaryHtmlFragmentBuilder singleNodeSummaryBuilder = new SummaryHtmlFragmentBuilder(detailEngine);
                detailValues.put("PHASE_SUMMARY_ROWS", singleNodeSummaryBuilder.buildNodePhaseSummaryRows(detailNodes.get(0)));
            } else {
                detailValues.put("STAT_LABEL_TOTAL",  "Total Nodes");
                detailValues.put("STAT_LABEL_PASSED", "Nodes Passed");
                detailValues.put("STAT_LABEL_FAILED", "Nodes Failed");
                detailValues.put("TOTAL_NODES",       String.valueOf(detailNodes.size()));
                detailValues.put("PASSED_NODES",      "0");
                detailValues.put("FAILED_NODES",      String.valueOf(detailNodes.size()));
                detailValues.put("NODES_SUMMARY_ROWS", detailBuilder.buildNodesSummaryRows(detailNodes));
            }

            detailValues.put("NODE_DETAIL_SECTIONS", detailBuilder.buildAllNodeSections(detailNodes));
            detailValues.put("RAW_OUTPUTS_JS",       detailBuilder.getRawOutputsJs());
            detailValues.put("VAL_CONCLUSIONS_JS",   detailBuilder.getValConclusionsJs());

            String detailHtml = detailEngine.render(detailValues);
            new ReportWriter().write(detailHtml, config.getOutputHtml());
            detailFilename = new File(config.getOutputHtml()).getName();
            if (singleNode) {
                log.info("Detail report generated: {}", detailFilename);
            } else {
                log.info("Detail report generated: {} ({} failed node(s))", detailFilename, detailNodes.size());
            }
        } else {
            log.info("All nodes passed — detail report not generated");
        }

        log.info("SUCCESS: MOP execution reports generated successfully");
        return ReportGeneratorResult.success(summaryFilename, detailFilename);
    }

    private static List<String> nodeNames(List<NodeExecutionData> nodes) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (NodeExecutionData n : nodes) names.add(n.getNodeName());
        return names;
    }
}
