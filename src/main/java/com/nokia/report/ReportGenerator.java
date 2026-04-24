package com.nokia.report;

import com.nokia.report.builder.HtmlFragmentBuilder;
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
        // 1. Load per-node JSON files
        NodeJsonReader reader = new NodeJsonReader();
        List<NodeExecutionData> nodes = reader.read(config.getJsonDir(), config.getNodeNames());
        log.info("Loaded {} node(s): {}", nodes.size(), nodeNames(nodes));

        // 2. Compute overall stats
        int total  = nodes.size();
        int passed = 0;
        for (NodeExecutionData n : nodes) {
            if (ExecutionStats.from(n).isSuccess()) passed++;
        }
        int failed = total - passed;

        // 3. Load template and extract sub-templates
        TemplateEngine engine = config.getTemplatePath() != null
                ? new TemplateEngine(config.getTemplatePath())
                : new TemplateEngine();

        // 4. Build HTML fragments
        HtmlFragmentBuilder builder = new HtmlFragmentBuilder(engine.getSubTemplate("tpl-node-panel"));
        Map<String, String> values = new LinkedHashMap<>();
        values.put("PAGE_TITLE",           HtmlFragmentBuilder.esc(config.getNodeType()) + " " + HtmlFragmentBuilder.esc(config.getActivity()) + " \u2014 MOP Execution Report");
        values.put("REPORT_HEADER",        HtmlFragmentBuilder.esc(config.getNodeType()) + " \u2014 " + HtmlFragmentBuilder.esc(config.getActivity()));
        values.put("CR_GROUP_SUBTITLE",    "CR Group: " + HtmlFragmentBuilder.esc(config.getCrGroup()));
        values.put("META_GENERATED_ON",    HtmlFragmentBuilder.esc(config.getTimestamp()));
        values.put("META_REQUEST_ID",      HtmlFragmentBuilder.esc(config.getRequestId()));
        values.put("META_NODE_TYPE",       HtmlFragmentBuilder.esc(config.getNodeType()));
        values.put("META_ACTIVITY",        HtmlFragmentBuilder.esc(config.getActivity()));
        values.put("META_CR_GROUP",        HtmlFragmentBuilder.esc(config.getCrGroup()));
        values.put("TOTAL_NODES",          String.valueOf(total));
        values.put("PASSED_NODES",         String.valueOf(passed));
        values.put("FAILED_NODES",         String.valueOf(failed));
        values.put("NODES_SUMMARY_ROWS",   builder.buildNodesSummaryRows(nodes));
        values.put("NODE_DETAIL_SECTIONS", builder.buildAllNodeSections(nodes));

        // 5. Render template
        String html = engine.render(values);

        // 6. Write output
        new ReportWriter().write(html, config.getOutputHtml());

        String reportFilename = new File(config.getOutputHtml()).getName();
        log.info("SUCCESS: MOP execution report generated successfully");
        return ReportGeneratorResult.success(reportFilename);
    }

    private static List<String> nodeNames(List<NodeExecutionData> nodes) {
        java.util.List<String> names = new java.util.ArrayList<>();
        for (NodeExecutionData n : nodes) names.add(n.getNodeName());
        return names;
    }
}
