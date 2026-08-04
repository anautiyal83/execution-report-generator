package com.nokia.report.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Computed execution statistics for one phase within a node.
 */
public class PhaseStats {

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

    private final String phase;
    private final int total;
    private final int success;
    private final int failed;
    private final int warning;

    public PhaseStats(String phase, int total, int success, int failed, int warning) {
        this.phase   = phase;
        this.total   = total;
        this.success = success;
        this.failed  = failed;
        this.warning = warning;
    }

    public String  getPhase()   { return phase; }
    public int     getTotal()   { return total; }
    public int     getSuccess() { return success; }
    public int     getFailed()  { return failed; }
    public int     getWarning() { return warning; }
    public boolean isSuccess()  { return failed == 0; }
    public boolean isWarning()  { return failed == 0 && warning > 0; }

    /**
     * Compute per-phase stats from a node, sorted by the standard phase order.
     * Unrecognised phases are appended at the end in first-seen order.
     */
    public static List<PhaseStats> from(NodeExecutionData node) {
        Map<String, int[]> byPhase = new LinkedHashMap<>();
        for (CommandResultDetail d : node.getCommands().values()) {
            String ph = d.getPhase();
            ph = (ph != null && !ph.trim().isEmpty()) ? ph.trim() : "OTHER";
            if (!byPhase.containsKey(ph)) byPhase.put(ph, new int[4]); // [total, success, failed, warning]
            int[] s = byPhase.get(ph);
            s[0]++;
            boolean commandFailed  = (!d.isSuccess() && !d.isWarning()) || "FAILED".equalsIgnoreCase(d.getValidationStatus());
            boolean commandWarning = !commandFailed && (d.isWarning() || "WARNING".equalsIgnoreCase(d.getValidationStatus()));
            if (commandFailed)       s[2]++;
            else if (commandWarning) s[3]++;
            else                     s[1]++;
        }

        List<String> sorted = new ArrayList<>();
        for (String p : PHASE_ORDER) { if (byPhase.containsKey(p)) sorted.add(p); }
        for (String p : byPhase.keySet()) { if (!sorted.contains(p)) sorted.add(p); }

        List<PhaseStats> result = new ArrayList<>();
        for (String p : sorted) {
            int[] s = byPhase.get(p);
            result.add(new PhaseStats(p, s[0], s[1], s[2], s[3]));
        }
        return result;
    }
}
