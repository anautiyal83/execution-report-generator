package com.nokia.report.builder;

import com.nokia.report.model.CommandResultDetail;
import com.nokia.report.model.ExecutionStats;
import com.nokia.report.model.NodeExecutionData;
import com.nokia.report.template.TemplateEngine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds HTML fragment strings for the detail report.
 *
 * Raw command outputs and validation conclusions are NOT embedded in the HTML DOM.
 * Instead they are stored in JS objects (RAW_OUTPUTS, VAL_CONCLUSIONS) injected once
 * at the bottom of the page and loaded into the DOM lazily on user interaction.
 * This keeps the HTML file small regardless of output volume.
 */
public class HtmlFragmentBuilder {

    private static final List<String> PHASE_ORDER = Arrays.asList(
        "PRE_NODE_HEALTH_CHECK", "BACKUP", "ACTIVITY_PRECHECK",
        "ACTIVITY_CONFIGURATION", "ACTIVITY_POSTCHECK",
        "ROLLBACK_PRECHECK", "ROLLBACK_CONFIGURATION", "ROLLBACK_POSTCHECK",
        "POST_NODE_HEALTH_CHECK"
    );

    private final String nodePanelTemplate;
    private final String summaryRowTemplate;
    private final String phaseHeaderTemplate;
    private final String commandRowTemplate;
    private final String valDetailStaticTemplate;
    private final String valDetailExpandableTemplate;

    // JS data stores — populated during buildAllNodeSections()
    private final Map<String, String> rawOutputStore     = new LinkedHashMap<>();
    private final Map<String, String> valConclusionStore = new LinkedHashMap<>();
    private int rowCounter = 0;

    public HtmlFragmentBuilder(TemplateEngine engine) {
        this.nodePanelTemplate          = engine.getSubTemplate("tpl-node-panel");
        this.summaryRowTemplate         = engine.getSubTemplate("tpl-summary-row");
        this.phaseHeaderTemplate        = engine.getSubTemplate("tpl-phase-header");
        this.commandRowTemplate         = engine.getSubTemplate("tpl-command-row");
        this.valDetailStaticTemplate    = engine.getSubTemplate("tpl-val-detail-static");
        this.valDetailExpandableTemplate = engine.getSubTemplate("tpl-val-detail-expandable");
    }

    // -------------------------------------------------------------------------
    // JS data stores
    // -------------------------------------------------------------------------

    /** Serialise the raw-output store as a JS object literal for {{RAW_OUTPUTS_JS}}. */
    public String getRawOutputsJs() {
        return toJsObject(rawOutputStore);
    }

    /** Serialise the val-conclusion store as a JS object literal for {{VAL_CONCLUSIONS_JS}}. */
    public String getValConclusionsJs() {
        return toJsObject(valConclusionStore);
    }

    // -------------------------------------------------------------------------
    // Nodes summary table rows
    // -------------------------------------------------------------------------

    public String buildNodesSummaryRows(List<NodeExecutionData> nodes) {
        StringBuilder sb = new StringBuilder();
        for (NodeExecutionData node : nodes) {
            ExecutionStats s = ExecutionStats.from(node);
            String nid = nodeId(node.getNodeName());
            String badgeCls = s.isNotExecuted() ? "badge-warning" : (s.isSuccess() ? "badge-success" : "badge-error");
            sb.append(summaryRowTemplate
                .replace("{{SR_NID}}",        nid)
                .replace("{{SR_NAME}}",        esc(node.getNodeName()))
                .replace("{{SR_BADGE_CLS}}",   badgeCls)
                .replace("{{SR_STATUS}}",      s.getOverallStatus())
                .replace("{{SR_TOTAL}}",       String.valueOf(s.getTotal()))
                .replace("{{SR_SUCCESS}}",     String.valueOf(s.getSuccess()))
                .replace("{{SR_FAILED}}",      String.valueOf(s.getFailed()))
                .replace("{{SR_VAL_ENABLED}}", String.valueOf(s.getValEnabled()))
                .replace("{{SR_VAL_PASS}}",    String.valueOf(s.getValPass()))
                .replace("{{SR_VAL_FAIL}}",    String.valueOf(s.getValFail()))
                .replace("{{SR_VAL_WARN}}",    String.valueOf(s.getValWarn()))
                .replace("{{SR_INFO_ONLY}}",   String.valueOf(s.getInfoOnly()))
                .replace("{{SR_START_TIME}}", esc(orNA(node.getActivityStartTime())))
                .replace("{{SR_END_TIME}}",   esc(orNA(node.getActivityEndTime())))
                .replace("{{SR_DURATION}}",   computeDuration(node.getActivityStartTime(), node.getActivityEndTime()))
            );
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Node detail panels
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
        String nid = nodeId(node.getNodeName());
        String panelCls = s.isNotExecuted() ? " panel-not-executed" : (s.isSuccess() ? "" : " panel-failed");
        String badgeCls = s.isNotExecuted() ? "badge-warning" : (s.isSuccess() ? "badge-success" : "badge-error");
        return nodePanelTemplate
            .replace("{{NP_PANEL_CLS}}",    panelCls)
            .replace("{{NP_ID}}",           nid)
            .replace("{{NP_INDEX}}",        String.valueOf(index))
            .replace("{{NP_NAME}}",         esc(node.getNodeName()))
            .replace("{{NP_BADGE_CLS}}",    badgeCls)
            .replace("{{NP_STATUS}}",       s.getOverallStatus())
            .replace("{{NP_TOTAL}}",        String.valueOf(s.getTotal()))
            .replace("{{NP_SUCCESS}}",      String.valueOf(s.getSuccess()))
            .replace("{{NP_FAILED}}",       String.valueOf(s.getFailed()))
            .replace("{{NP_VAL_TOTAL}}",    String.valueOf(s.getValEnabled()))
            .replace("{{NP_VAL_PASS}}",     String.valueOf(s.getValPass()))
            .replace("{{NP_VAL_FAIL}}",     String.valueOf(s.getValFail()))
            .replace("{{NP_VAL_WARN}}",     String.valueOf(s.getValWarn()))
            .replace("{{NP_INFO_ONLY}}",    String.valueOf(s.getInfoOnly()))
            .replace("{{NP_START_TIME}}",   esc(orNA(node.getActivityStartTime())))
            .replace("{{NP_END_TIME}}",     esc(orNA(node.getActivityEndTime())))
            .replace("{{NP_DURATION}}",     computeDuration(node.getActivityStartTime(), node.getActivityEndTime()))
            .replace("{{NP_COMMAND_ROWS}}", node.isNotExecuted()
                ? "<tr><td colspan=\"13\" style=\"text-align:center;padding:16px;font-style:italic;color:#888;\">Execution not started — no commands to display</td></tr>"
                : buildCommandTableRows(node));
    }

    // -------------------------------------------------------------------------
    // Command table rows
    // -------------------------------------------------------------------------

    private String buildCommandTableRows(NodeExecutionData node) {
        Map<String, List<Map.Entry<String, CommandResultDetail>>> byPhase = new LinkedHashMap<>();
        for (Map.Entry<String, CommandResultDetail> entry : node.getCommands().entrySet()) {
            String ph = entry.getValue().getPhase();
            ph = (ph != null && !ph.trim().isEmpty()) ? ph.trim() : "OTHER";
            if (!byPhase.containsKey(ph)) byPhase.put(ph, new ArrayList<>());
            byPhase.get(ph).add(entry);
        }

        List<String> sortedPhases = new ArrayList<>();
        for (String p : PHASE_ORDER) { if (byPhase.containsKey(p)) sortedPhases.add(p); }
        for (String p : byPhase.keySet()) { if (!sortedPhases.contains(p)) sortedPhases.add(p); }

        StringBuilder sb = new StringBuilder();
        for (String phase : sortedPhases) {
            boolean phaseFailed = byPhase.get(phase).stream().anyMatch(e -> {
                CommandResultDetail d = e.getValue();
                return !d.isSuccess() || "FAILED".equalsIgnoreCase(d.getValidationStatus());
            });
            sb.append(phaseHeaderTemplate
                    .replace("{{PH_PHASE}}", esc(phase))
                    .replace("{{PH_BADGE_CLS}}", phaseFailed ? "badge-error" : "badge-success")
                    .replace("{{PH_STATUS}}", phaseFailed ? "FAILED" : "PASSED"));
            for (Map.Entry<String, CommandResultDetail> entry : byPhase.get(phase)) {
                sb.append(buildCommandRow(phase, entry.getKey(), entry.getValue()));
            }
        }
        return sb.toString();
    }

    private String buildCommandRow(String phase, String cmd, CommandResultDetail d) {
        String key      = "r" + rowCounter++;
        boolean success = d.isSuccess();
        String target   = d.getTarget() != null ? d.getTarget().trim() : "";
        String desc     = orNA(d.getDescription());
        String failReason = success ? "#N/A" : buildFailReason(d.getReason(), d.getFailure());

        boolean validate  = d.isValidate();
        String valStatus  = d.getValidationStatus() != null ? d.getValidationStatus().trim() : "SKIPPED";
        String valLower   = valStatus.toLowerCase();
        String valBadge   = valLower.equals("success") || valLower.equals("skipped") ? "badge-success"
                          : valLower.equals("warning") ? "badge-warning" : "badge-error";
        String valCriteria = orNA(d.getValidationCriteria());
        String valConclusion = d.getValidationConclusion() != null ? d.getValidationConclusion() : "";

        // Store raw output in JS store (not in DOM)
        String rawOutput = d.getOutput() != null ? d.getOutput() : "";
        rawOutputStore.put(key, rawOutput);

        // Val conclusion: static text for success/skipped, lazy-load button for fail/warning
        String valDetailHtml;
        boolean needsExpand = !valLower.equals("success") && !valLower.equals("skipped")
                              && !valConclusion.isEmpty();
        if (needsExpand) {
            valConclusionStore.put(key, valConclusion);
            valDetailHtml = valDetailExpandableTemplate
                .replace("{{VD_TEXT}}", "")      // not used — loaded lazily
                .replace("{{CR_KEY}}", key);
        } else {
            valDetailHtml = valDetailStaticTemplate
                .replace("{{VD_TEXT}}", esc(orNA(valConclusion)));
        }

        return commandRowTemplate
            .replace("{{CR_PHASE}}",          esc(phase))
            .replace("{{CR_IS_FAILED}}",      success ? "0" : "1")
            .replace("{{CR_KEY}}",            key)
            .replace("{{CR_CMD}}",            esc(cmd))
            .replace("{{CR_DESC}}",           esc(desc))
            .replace("{{CR_TARGET}}",         esc(target))
            .replace("{{CR_EXEC_BADGE}}",     success ? "badge-success" : "badge-error")
            .replace("{{CR_EXEC_LABEL}}",     success ? "Success" : "Failed")
            .replace("{{CR_FAIL_REASON}}",    esc(failReason))
            .replace("{{CR_VALIDATE_BADGE}}", validate ? "badge-enabled" : "badge-disabled")
            .replace("{{CR_VALIDATE_LABEL}}", validate ? "True" : "False")
            .replace("{{CR_VAL_CRITERIA}}",   esc(valCriteria))
            .replace("{{CR_VAL_BADGE}}",      valBadge)
            .replace("{{CR_VAL_STATUS}}",     esc(valStatus))
            .replace("{{CR_VAL_DETAIL}}",     valDetailHtml)
            .replace("{{CR_START_TIME}}",     esc(orNA(d.getStartDateAndTime())))
            .replace("{{CR_END_TIME}}",       esc(orNA(d.getEndDateAndTime())))
            .replace("{{CR_DURATION}}",       computeDuration(d.getStartDateAndTime(), d.getEndDateAndTime()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    public static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String orNA(String s) {
        return (s == null || s.trim().isEmpty()) ? "#N/A" : s;
    }

    private static String buildFailReason(String reason, String failure) {
        String r = (reason  != null && !reason.trim().isEmpty())  ? reason.trim()  : null;
        String f = (failure != null && !failure.trim().isEmpty()) ? failure.trim() : null;
        if (r != null && f != null) return r + " \u2014 " + f;
        if (r != null) return r;
        if (f != null) return f;
        return "#N/A";
    }

    public static String computeDuration(String start, String end) {
        if (start == null || end == null || start.isEmpty() || end.isEmpty()) return "-";
        try {
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            long diff = sdf.parse(end).getTime() - sdf.parse(start).getTime();
            long seconds = diff / 1000;
            if (seconds < 0) return "-";
            long mins = seconds / 60;
            long secs = seconds % 60;
            return mins > 0 ? mins + "m " + secs + "s" : secs + "s";
        } catch (Exception e) {
            return "-";
        }
    }

    public static String nodeId(String nodeName) {
        return nodeName.replaceAll("[^a-zA-Z0-9_-]", "_");
    }

    /** Serialise a String→String map to a JS object literal using JSON encoding. */
    private static String toJsObject(Map<String, String> map) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : map.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(jsonString(e.getKey())).append(':').append(jsonString(e.getValue()));
        }
        sb.append('}');
        return sb.toString();
    }

    /** Minimal JSON string encoding (handles the characters that matter in output text). */
    private static String jsonString(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < 0x20) { sb.append(String.format("\\u%04x", (int) c)); }
                    else           { sb.append(c); }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
