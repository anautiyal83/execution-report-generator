package com.nokia.report;

/**
 * Thrown when report generation fails due to invalid configuration,
 * missing/malformed input files, template errors, or IO failures.
 */
public class ReportGeneratorException extends Exception {

    public ReportGeneratorException(String message) {
        super(message);
    }

    public ReportGeneratorException(String message, Throwable cause) {
        super(message, cause);
    }
}
