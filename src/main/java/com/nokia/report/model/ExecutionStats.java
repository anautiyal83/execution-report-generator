package com.nokia.report.model;

/**
 * Computed execution and validation statistics for one node.
 */
public class ExecutionStats {

    private final int total;
    private final int success;
    private final int failed;
    private final int valEnabled;
    private final int valPass;
    private final int valFail;
    private final int valWarn;
    private final int infoOnly;

    public ExecutionStats(int total, int success, int failed,
                          int valEnabled, int valPass, int valFail,
                          int valWarn, int infoOnly) {
        this.total      = total;
        this.success    = success;
        this.failed     = failed;
        this.valEnabled = valEnabled;
        this.valPass    = valPass;
        this.valFail    = valFail;
        this.valWarn    = valWarn;
        this.infoOnly   = infoOnly;
    }

    public int getTotal()      { return total; }
    public int getSuccess()    { return success; }
    public int getFailed()     { return failed; }
    public int getValEnabled() { return valEnabled; }
    public int getValPass()    { return valPass; }
    public int getValFail()    { return valFail; }
    public int getValWarn()    { return valWarn; }
    public int getInfoOnly()   { return infoOnly; }

    public String getOverallStatus() { return failed > 0 ? "FAILED" : "SUCCESS"; }
    public boolean isSuccess()       { return failed == 0; }

    /** Compute stats from a NodeExecutionData. */
    public static ExecutionStats from(NodeExecutionData node) {
        int total = 0, success = 0, failed = 0;
        int valEnabled = 0, valPass = 0, valFail = 0, valWarn = 0, infoOnly = 0;

        for (CommandResultDetail d : node.getCommands().values()) {
            total++;
            if (d.isSuccess()) success++; else failed++;
            if (d.isValidate()) valEnabled++;

            String vs = d.getValidationStatus() == null ? "" : d.getValidationStatus().trim().toLowerCase();
            switch (vs) {
                case "success": valPass++;  break;
                case "failed":  valFail++;  break;
                case "warning": valWarn++;  break;
                case "skipped": infoOnly++; break;
                default: break;
            }
        }
        return new ExecutionStats(total, success, failed, valEnabled, valPass, valFail, valWarn, infoOnly);
    }
}
