package com.nokia.report;

import com.nokia.report.config.ReportConfig;
import com.nokia.report.model.ReportGeneratorResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;

/**
 * CLI entry point for the Execution Report Generator.
 *
 * For programmatic use, instantiate {@link ReportGenerator} directly:
 * <pre>
 *   ReportConfig config = ReportConfig.builder()
 *       .nodeType("MRF")
 *       .activity("ANNOUNCEMENT_LOADING")
 *       .crGroup("GroupA")
 *       .jsonDir("/path/to/jsons")
 *       .outputHtmlPath("/path/to/output")
 *       .outputHtmlName("report.html")
 *       .build();
 *   ReportGeneratorResult result = new ReportGenerator().generate(config);
 * </pre>
 *
 * CLI usage:
 * <pre>
 *   java -jar execution-report-generator-1.0.0-cli.jar \
 *       --node-type        MRF                                          \
 *       --activity         ANNOUNCEMENT_LOADING                        \
 *       --cr-group         GroupA                                      \
 *       --json-dir         /path/to/execution/jsons                    \
 *       --output-html-path /path/to/output/directory                   \
 *       --output-html-name report.html                                  \
 *       --template         /path/to/mop_execution_report_template.html \
 *       [--node-names      node1,node2,node3]                          \
 *       [--request-id      CR-12345]                                   \
 *       [--timestamp       "2026-04-18 10:00:00"]
 * </pre>
 */
public class ReportGeneratorMain {

    private static final Logger log = LoggerFactory.getLogger(ReportGeneratorMain.class);

    public static void main(String[] args) {
        ReportGeneratorResult result;
        try {
            ReportConfig config = parseArgs(args);
            result = new ReportGenerator().generate(config);
        } catch (Exception e) {
            log.error("FAILED to generate report: {}: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            result = ReportGeneratorResult.failure(e.getMessage());
        }

        System.out.println("STATUS=" + result.getStatus());
        System.out.println("ERRORS=" + result.getErrors());
        result.getParameters().forEach((k, v) -> System.out.println(k + "=" + v));
        System.exit(result.isSuccess() ? 0 : 1);
    }

    // -------------------------------------------------------------------------
    // CLI argument parsing
    // -------------------------------------------------------------------------

    private static ReportConfig parseArgs(String[] args) {
        ReportConfig config = new ReportConfig();

        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--node-type":        config.setNodeType(args[++i]);       break;
                case "--activity":         config.setActivity(args[++i]);       break;
                case "--cr-group":         config.setCrGroup(args[++i]);        break;
                case "--json-dir":         config.setJsonDir(args[++i]);        break;
                case "--output-html-path": config.setOutputHtmlPath(args[++i]); break;
                case "--output-html-name": config.setOutputHtmlName(args[++i]); break;
                case "--template":         config.setTemplatePath(args[++i]);   break;
                case "--request-id":       config.setRequestId(args[++i]);      break;
                case "--timestamp":        config.setTimestamp(args[++i]);      break;
                case "--node-names":
                    String[] names = args[++i].split(",");
                    for (int j = 0; j < names.length; j++) names[j] = names[j].trim();
                    config.setNodeNames(Arrays.asList(names));
                    break;
                default: break;
            }
        }

        return config;
        // validation is performed inside ReportGenerator.generate() via config.validate()
    }
}
