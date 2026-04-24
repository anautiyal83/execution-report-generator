package com.nokia.report.builder;

import com.nokia.report.model.CommandResultDetail;
import com.nokia.report.model.ExecutionStats;
import com.nokia.report.model.NodeExecutionData;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds HTML fragment strings for the dynamic sections of the MOP execution report.
 *
 * Static HTML structure (cards, panel shell, table headers, metadata items) lives in
 * the HTML template file. This class only handles data that requires Java loops:
 *   - Per-node summary table rows  ({{NODES_SUMMARY_ROWS}})
 *   - Per-node detail panels       ({{NODE_DETAIL_SECTIONS}}) — filled via sub-template
 *   - Per-command table rows       (inner loop inside each panel)
 */
public class HtmlFragmentBuilder {

    private static final List<String> PHASE_ORDER = Arrays.asList(
        "PRE_NODE_HEALTH_CHECK",
        "BACKUP",
        "ACTIVITY_PRECHECK",
        "ACTIVITY_CONFIGURATION",
        "ACTIVITY_POSTCHECK",
        "POST_NODE_HEALTH_CHECK",
        "ROLLBACK_PRECHECK",
        "ROLLBACK_CONFIGURATION",
        "ROLLBACK_POSTCHECK"
    );

    private final String nodePanelTemplate;

    /**
     * @param nodePanelTemplate the "tpl-node-panel" sub-template extracted from the HTML template.
     *                          Contains {{NP_*}} placeholders that are filled per node.
     */
    public HtmlFragmentBuilder(String nodePanelTemplate) {
        this.nodePanelTemplate = nodePanelTemplate;
    }

    // -------------------------------------------------------------------------
    // Nodes summary table rows
    // -------------------------------------------------------------------------

    public String buildNodesSummaryRows(List<NodeExecutionData> nodes) {
        StringBuilder sb = new StringBuilder();
        for (NodeExecutionData node : nodes) {
            ExecutionStats s = ExecutionStats.from(node);
            String nid      = nodeId(node.getNodeName());
            String badgeCls = s.isSuccess() ? "badge-success" : "badge-error";
            sb.append("  <tr class=\"summary-row\" onclick=\"scrollToNode('").append(nid)
              .append("')\" title=\"Click to jump to ").append(esc(node.getNodeName())).append(" details\">\n")
              .append("    <td><strong>").append(esc(node.getNodeName())).append("</strong></td>\n")
              .append("    <td><span class=\"badge ").append(badgeCls).append("\">").append(s.getOverallStatus()).append("</span></td>\n")
              .append("    <td>").append(s.getTotal()).append("</td>\n")
              .append("    <td class=\"cell-success\">").append(s.getSuccess()).append("</td>\n")
              .append("    <td class=\"cell-failed\">").append(s.getFailed()).append("</td>\n")
              .append("    <td>").append(s.getValEnabled()).append("</td>\n")
              .append("    <td class=\"cell-success\">").append(s.getValPass()).append("</td>\n")
              .append("    <td class=\"cell-failed\">").append(s.getValFail()).append("</td>\n")
              .append("    <td class=\"cell-warning\">").append(s.getValWarn()).append("</td>\n")
              .append("    <td>").append(s.getInfoOnly()).append("</td>\n")
              .append("  </tr>\n");
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Node detail panels (filled from sub-template)
    // -------------------------------------------------------------------------

    public String buildAllNodeSections(List<NodeExecutionData> nodes) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nodes.size(); i++) {
            sb.append(buildNodePanel(nodes.get(i), i + 1));
        }
        return sb.toString();
    }

    private String buildNodePanel(NodeExecutionData node, int index) {
        ExecutionStats s = ExecutionStats.from(node);
        String nid       = nodeId(node.getNodeName());

        return nodePanelTemplate
            .replace("{{NP_PANEL_CLS}}",      s.isSuccess() ? "" : " panel-failed")
            .replace("{{NP_ID}}",             nid)
            .replace("{{NP_INDEX}}",          String.valueOf(index))
            .replace("{{NP_NAME}}",           esc(node.getNodeName()))
            .replace("{{NP_BADGE_CLS}}",      s.isSuccess() ? "badge-success" : "badge-error")
            .replace("{{NP_STATUS}}",         s.getOverallStatus())
            .replace("{{NP_TOTAL}}",          String.valueOf(s.getTotal()))
            .replace("{{NP_SUCCESS}}",        String.valueOf(s.getSuccess()))
            .replace("{{NP_FAILED}}",         String.valueOf(s.getFailed()))
            .replace("{{NP_VAL_TOTAL}}",      String.valueOf(s.getValEnabled()))
            .replace("{{NP_VAL_PASS}}",       String.valueOf(s.getValPass()))
            .replace("{{NP_VAL_FAIL}}",       String.valueOf(s.getValFail()))
            .replace("{{NP_VAL_WARN}}",       String.valueOf(s.getValWarn()))
            .replace("{{NP_INFO_ONLY}}",      String.valueOf(s.getInfoOnly()))
            .replace("{{NP_COMMAND_ROWS}}",   buildCommandTableRows(node));
    }

    // -------------------------------------------------------------------------
    // Command table rows (dynamic: one row per command result)
    // -------------------------------------------------------------------------

    private String buildCommandTableRows(NodeExecutionData node) {
        // Group commands by phase, preserving insertion order within each phase
        Map<String, List<Map.Entry<String, CommandResultDetail>>> byPhase = new LinkedHashMap<>();
        for (Map.Entry<String, CommandResultDetail> entry : node.getCommands().entrySet()) {
            String ph = entry.getValue().getPhase();
            ph = (ph != null && !ph.trim().isEmpty()) ? ph.trim() : "OTHER";
            if (!byPhase.containsKey(ph)) byPhase.put(ph, new ArrayList<>());
            byPhase.get(ph).add(entry);
        }

        // Sort phases by defined sequence; unrecognised phases go at the end
        List<String> sortedPhases = new ArrayList<>();
        for (String p : PHASE_ORDER) {
            if (byPhase.containsKey(p)) sortedPhases.add(p);
        }
        for (String p : byPhase.keySet()) {
            if (!sortedPhases.contains(p)) sortedPhases.add(p);
        }

        StringBuilder sb = new StringBuilder();
        for (String phase : sortedPhases) {
            List<Map.Entry<String, CommandResultDetail>> cmds = byPhase.get(phase);

            // Phase header row spanning all 10 data columns — collapsible
            sb.append("        <tr class=\"phase-header-row\" onclick=\"togglePhase(this)\">\n")
              .append("          <td colspan=\"10\"><span class=\"phase-toggle-icon\">&#9660;</span> ").append(esc(phase)).append("</td>\n")
              .append("        </tr>\n");

            for (int i = 0; i < cmds.size(); i++) {
                Map.Entry<String, CommandResultDetail> entry = cmds.get(i);
                String cmd = entry.getKey();
                CommandResultDetail d = entry.getValue();

                boolean success    = d.isSuccess();
                String target      = orNA(d.getTarget());
                String description = orNA(d.getDescription());
                String failReason  = success ? "#N/A" : buildFailReason(d.getReason(), d.getFailure());
                boolean validate   = d.isValidate();
                String valCriteria = orNA(d.getValidationCriteria());
                String valStatus   = d.getValidationStatus() != null ? d.getValidationStatus().trim() : "SKIPPED";
                String valDetail   = orNA(d.getValidationConclusion());
                String rawOutput   = d.getOutput() != null ? d.getOutput() : "";

                String execBadge     = success ? "badge-success" : "badge-error";
                String execLabel     = success ? "Success" : "Failed";
                String valLower      = valStatus.toLowerCase();
                String valBadge      = valLower.equals("success") || valLower.equals("skipped")
                                         ? "badge-success"
                                         : valLower.equals("warning") ? "badge-warning" : "badge-error";
                String validateBadge = validate ? "badge-enabled" : "badge-disabled";

                String valDetailHtml;
                if (valLower.equals("success") || valLower.equals("skipped")) {
                    valDetailHtml = "<span class=\"na-text\">" + esc(valDetail) + "</span>";
                } else {
                    valDetailHtml = "<button class=\"output-toggle\" onclick=\"toggleOutput(this)\">View Details</button>"
                                  + "<div class=\"output-content\"><pre>" + esc(valDetail) + "</pre></div>";
                }

                sb.append("        <tr class=\"row-data\" data-phase=\"").append(esc(phase)).append("\">\n")
                  .append("          <td>").append(esc(target)).append("</td>\n")
                  .append("          <td><code class=\"command-code\">").append(esc(cmd)).append("</code></td>\n")
                  .append("          <td>").append(esc(description)).append("</td>\n")
                  .append("          <td><span class=\"badge ").append(execBadge).append("\">")
                  .append("<span class=\"status-icon\"></span>").append(execLabel).append("</span></td>\n")
                  .append("          <td><span class=\"na-text\">").append(esc(failReason)).append("</span></td>\n")
                  .append("          <td><span class=\"badge ").append(validateBadge).append("\">")
                  .append(validate ? "True" : "False").append("</span></td>\n")
                  .append("          <td>").append(esc(valCriteria)).append("</td>\n")
                  .append("          <td><span class=\"badge ").append(valBadge).append("\">")
                  .append("<span class=\"status-icon\"></span>").append(esc(valStatus)).append("</span></td>\n")
                  .append("          <td>").append(valDetailHtml).append("</td>\n")
                  .append("          <td><button class=\"output-toggle\" onclick=\"toggleOutput(this)\">View Output</button>")
                  .append("<div class=\"output-content\"><pre>").append(esc(rawOutput)).append("</pre></div></td>\n")
                  .append("        </tr>\n");
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Escape HTML special characters to prevent XSS in report output. */
    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String orNA(String s) {
        return (s == null || s.trim().isEmpty()) ? "#N/A" : s;
    }

    /** Combine reason and failure fields into a single display string. */
    private static String buildFailReason(String reason, String failure) {
        String r = (reason  != null && !reason.trim().isEmpty())  ? reason.trim()  : null;
        String f = (failure != null && !failure.trim().isEmpty()) ? failure.trim() : null;
        if (r != null && f != null) return r + " \u2014 " + f;
        if (r != null) return r;
        if (f != null) return f;
        return "#N/A";
    }

    /** Derive a safe HTML element id from a node name. */
    public static String nodeId(String nodeName) {
        return nodeName.replaceAll("[^a-zA-Z0-9_-]", "_");
    }
}
