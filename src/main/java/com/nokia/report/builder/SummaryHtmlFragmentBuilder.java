package com.nokia.report.builder;

import com.nokia.report.model.ExecutionStats;
import com.nokia.report.model.NodeExecutionData;
import com.nokia.report.model.PhaseStats;
import com.nokia.report.template.TemplateEngine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the phase-matrix table for the summary report.
 *
 * Layout: one row per node, one column per phase (union of all phases across nodes),
 * plus an Overall status column at the end.
 *
 * Placeholders filled:
 *   {{SUMMARY_PHASE_HEADERS}}  — <th> cells for each phase column
 *   {{SUMMARY_TABLE_ROWS}}     — <tr> rows, one per node
 */
public class SummaryHtmlFragmentBuilder {

    private static final List<String> PHASE_ORDER = Arrays.asList(
        "PRE_NODE_HEALTH_CHECK",
        "BACKUP",
        "ACTIVITY_PRECHECK",
        "ACTIVITY_CONFIGURATION",
        "ACTIVITY_POSTCHECK",
        "ROLLBACK_PRECHECK",
        "ROLLBACK_CONFIGURATION",
        "ROLLBACK_POSTCHECK",
        "POST_NODE_HEALTH_CHECK"
    );

    // TemplateEngine kept for potential future sub-template use; not used currently.
    public SummaryHtmlFragmentBuilder(TemplateEngine engine) {}

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Build {@code <th>} cells for all phase columns. */
    public String buildSummaryPhaseHeaders(List<NodeExecutionData> nodes) {
        StringBuilder sb = new StringBuilder();
        for (String phase : collectPhases(nodes)) {
            sb.append("<th class=\"col-phase\">")
              .append(HtmlFragmentBuilder.esc(phase))
              .append("</th>");
        }
        return sb.toString();
    }

    /**
     * Build one {@code <tr>} per phase for a single node's phase summary table.
     * Columns: Phase | Total | Success | Failed | Status
     */
    public String buildNodePhaseSummaryRows(NodeExecutionData node) {
        StringBuilder sb = new StringBuilder();
        for (PhaseStats ps : PhaseStats.from(node)) {
            boolean ok = ps.isSuccess();
            sb.append("<tr class=\"").append(ok ? "" : "phase-failed").append("\">")
              .append("<td>").append(HtmlFragmentBuilder.esc(ps.getPhase())).append("</td>")
              .append("<td>").append(ps.getTotal()).append("</td>")
              .append("<td class=\"cell-success\">").append(ps.getSuccess()).append("</td>")
              .append("<td class=\"cell-failed\">").append(ps.getFailed()).append("</td>")
              .append("<td><span class=\"badge ").append(ok ? "badge-success" : "badge-error").append("\">")
              .append(ok ? "PASSED" : "FAILED").append("</span></td>")
              .append("</tr>");
        }
        return sb.toString();
    }

    /** Build one {@code <tr>} per node, with a cell per phase and an Overall cell. */
    public String buildSummaryTableRows(List<NodeExecutionData> nodes) {
        List<String> phases = collectPhases(nodes);
        StringBuilder sb = new StringBuilder();

        for (NodeExecutionData node : nodes) {
            ExecutionStats s = ExecutionStats.from(node);
            boolean nodeOk = s.isSuccess();

            // Build phase → stats map for quick lookup
            Map<String, PhaseStats> phaseMap = new LinkedHashMap<>();
            for (PhaseStats ps : PhaseStats.from(node)) {
                phaseMap.put(ps.getPhase(), ps);
            }

            sb.append("<tr class=\"").append(nodeOk ? "" : "row-failed").append("\">");

            // Node name cell
            sb.append("<td class=\"col-node\">")
              .append(HtmlFragmentBuilder.esc(node.getNodeName()))
              .append("</td>");

            // One cell per phase
            for (String phase : phases) {
                PhaseStats ps = phaseMap.get(phase);
                if (ps == null) {
                    sb.append("<td><span class=\"badge badge-skipped\">SKIPPED</span></td>");
                } else {
                    boolean phaseOk = ps.isSuccess();
                    sb.append("<td><span class=\"badge ")
                      .append(phaseOk ? "badge-success" : "badge-error")
                      .append("\">")
                      .append(phaseOk ? "PASSED" : "FAILED")
                      .append("</span></td>");
                }
            }

            // Overall status cell
            sb.append("<td class=\"col-overall\"><span class=\"badge ")
              .append(nodeOk ? "badge-success" : "badge-error")
              .append("\">")
              .append(s.getOverallStatus())
              .append("</span></td>");

            // Timing cells
            String startTime = node.getActivityStartTime();
            String endTime   = node.getActivityEndTime();
            String duration  = HtmlFragmentBuilder.computeDuration(startTime, endTime);
            String startDisp = (startTime != null && !startTime.trim().isEmpty()) ? startTime : "-";
            String endDisp   = (endTime   != null && !endTime.trim().isEmpty())   ? endTime   : "-";
            sb.append("<td class=\"col-time\">").append(HtmlFragmentBuilder.esc(startDisp)).append("</td>");
            sb.append("<td class=\"col-time\">").append(HtmlFragmentBuilder.esc(endDisp)).append("</td>");
            sb.append("<td class=\"col-time\">").append(duration).append("</td>");

            sb.append("</tr>");
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /** Collect phases that appear in at least one node's data, sorted by PHASE_ORDER.
     *  Phases absent from all nodes are excluded entirely. */
    private static List<String> collectPhases(List<NodeExecutionData> nodes) {
        java.util.Set<String> present = new java.util.LinkedHashSet<>();
        for (NodeExecutionData n : nodes) {
            for (PhaseStats ps : PhaseStats.from(n)) present.add(ps.getPhase());
        }

        // Sort: known phases first in PHASE_ORDER, then any unrecognised ones
        List<String> sorted = new ArrayList<>();
        for (String p : PHASE_ORDER) { if (present.contains(p)) sorted.add(p); }
        for (String p : present)     { if (!sorted.contains(p)) sorted.add(p); }
        return sorted;
    }
}
