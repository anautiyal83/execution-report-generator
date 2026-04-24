package com.nokia.report.model;

import java.util.LinkedHashMap;

/**
 * Result of a report generation run.
 *
 * <p>Contains:
 * <ul>
 *   <li>{@code status}     — {@code "SUCCESS"} or {@code "FAILED"}</li>
 *   <li>{@code errors}     — number of errors (0 on success)</li>
 *   <li>{@code parameters} — additional key/value pairs, e.g. {@code REPORT_FILENAME}</li>
 * </ul>
 *
 * <p>Stdout format (same as ciq-processor):
 * <pre>
 *   STATUS=SUCCESS
 *   ERRORS=0
 *   REPORT_FILENAME=MRF_ANNOUNCEMENT_LOADING_execution_report.html
 * </pre>
 */
public class ReportGeneratorResult {

    private final String status;
    private final int errors;
    private final LinkedHashMap<String, String> parameters;

    private ReportGeneratorResult(String status, int errors, LinkedHashMap<String, String> parameters) {
        this.status     = status;
        this.errors     = errors;
        this.parameters = parameters;
    }

    public static ReportGeneratorResult success(String reportFilename) {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("REPORT_FILENAME", reportFilename);
        return new ReportGeneratorResult("SUCCESS", 0, params);
    }

    public static ReportGeneratorResult failure(String errorMessage) {
        LinkedHashMap<String, String> params = new LinkedHashMap<>();
        params.put("ERROR", errorMessage);
        return new ReportGeneratorResult("FAILED", 1, params);
    }

    public String getStatus()                             { return status; }
    public int getErrors()                                { return errors; }
    public LinkedHashMap<String, String> getParameters()  { return parameters; }
    public boolean isSuccess()                            { return "SUCCESS".equals(status); }
}
