# Execution Report Generator — User Manual

**Version:** 1.0.0
**Maven artifact:** `com.nokia:execution-report-generator:1.0.0`
**CLI JAR:** `execution-report-generator-1.0.0-cli.jar`

---

## Table of Contents

1. [Overview](#1-overview)
2. [Prerequisites](#2-prerequisites)
3. [Building](#3-building)
4. [Usage Modes](#4-usage-modes)
   - [4.1 Programmatic (Library)](#41-programmatic-library)
   - [4.2 Command-Line (CLI)](#42-command-line-cli)
5. [ReportConfig Reference](#5-reportconfig-reference)
6. [Input: Per-Node Execution JSON Files](#6-input-per-node-execution-json-files)
7. [Output](#7-output)
8. [Understanding the HTML Report](#8-understanding-the-html-report)
9. [Filtering and Interacting with the Report](#9-filtering-and-interacting-with-the-report)
10. [Exporting to Excel](#10-exporting-to-excel)
11. [Custom HTML Template](#11-custom-html-template)
12. [Exit Codes and stdout Format (CLI)](#12-exit-codes-and-stdout-format-cli)
13. [Troubleshooting](#13-troubleshooting)

---

## 1. Overview

The **Execution Report Generator** reads per-node execution JSON files produced by the
network-command-executor and generates a single self-contained HTML report. The report shows:

- An overall summary of all nodes (pass/fail counts, validation statistics).
- Per-node collapsible detail sections with a full command table.
- Commands grouped and sequenced by execution phase.
- Collapsible phase sections within each node panel.
- In-line raw command output and validation failure details (expandable).
- An XLSX export button (browser-side, no server needed).

The tool can be used in two ways:

| Mode | How | Artifact |
|---|---|---|
| **Library** | Instantiate `ReportGenerator` in Java code | `execution-report-generator-1.0.0.jar` (thin) |
| **CLI** | Run with `java -jar` | `execution-report-generator-1.0.0-cli.jar` (fat) |

---

## 2. Prerequisites

| Requirement | Minimum |
|---|---|
| Java | JDK / JRE 8 or later |
| Input JSON files | Per-node execution results from network-command-executor |

---

## 3. Building

```bash
mvn package
```

This produces two artifacts in `target/`:

| File | Purpose |
|---|---|
| `execution-report-generator-1.0.0.jar` | Thin library JAR — use as a Maven dependency |
| `execution-report-generator-1.0.0-cli.jar` | Fat runnable JAR — use with `java -jar` |

---

## 4. Usage Modes

### 4.1 Programmatic (Library)

Add the thin JAR as a Maven dependency:

```xml
<dependency>
    <groupId>com.nokia</groupId>
    <artifactId>execution-report-generator</artifactId>
    <version>1.0.0</version>
</dependency>
```

Build a `ReportConfig` using the fluent builder, then call `ReportGenerator.generate()`:

```java
import com.nokia.report.ReportGenerator;
import com.nokia.report.ReportGeneratorException;
import com.nokia.report.config.ReportConfig;
import com.nokia.report.model.ReportGeneratorResult;

ReportConfig config = ReportConfig.builder()
    .nodeType("MRF")
    .activity("ANNOUNCEMENT_LOADING")
    .crGroup("GroupA")
    .jsonDir("/path/to/execution/jsons")
    .outputHtmlPath("/path/to/output")
    .outputHtmlName("report.html")
    .requestId("CR-98765")              // optional
    .timestamp("2026-04-19 14:00:00")   // optional — defaults to now
    .nodeNames(Arrays.asList("MRF1", "MRF2", "MRF3"))  // optional — defaults to all
    .build();

try {
    ReportGeneratorResult result = new ReportGenerator().generate(config);
    if (result.isSuccess()) {
        String filename = result.getParameters().get("REPORT_FILENAME");
        System.out.println("Report written: " + filename);
    }
} catch (ReportGeneratorException e) {
    System.err.println("Report generation failed: " + e.getMessage());
}
```

`generate()` throws `ReportGeneratorException` on:
- Missing required fields in `ReportConfig`
- Invalid or inaccessible paths
- Malformed or missing JSON files
- Template errors or IO failures

### 4.2 Command-Line (CLI)

#### Basic example

```bash
java -jar execution-report-generator-1.0.0-cli.jar \
  --node-type        MRF                        \
  --activity         ANNOUNCEMENT_LOADING       \
  --cr-group         GroupA                     \
  --json-dir         /path/to/execution/jsons   \
  --output-html-path /path/to/output            \
  --output-html-name report.html
```

#### With optional arguments

```bash
java -jar execution-report-generator-1.0.0-cli.jar \
  --node-type        MRF                                  \
  --activity         ANNOUNCEMENT_LOADING                 \
  --cr-group         GroupA                               \
  --json-dir         /opt/nce/results/GroupA              \
  --output-html-path /opt/reports/2026-04-19              \
  --output-html-name MRF_ANNOUNCEMENT_LOADING_report.html \
  --request-id       CR-98765                             \
  --timestamp        "2026-04-19 14:30:00"                \
  --node-names       MRF1,MRF2,MRF3
```

#### With a custom template

```bash
java -jar execution-report-generator-1.0.0-cli.jar \
  --node-type        MRF                           \
  --activity         ANNOUNCEMENT_LOADING          \
  --cr-group         GroupA                        \
  --json-dir         /path/to/jsons                \
  --output-html-path /path/to/output               \
  --output-html-name report.html                   \
  --template         /path/to/custom_template.html
```

#### CLI arguments

**Required:**

| Argument | Description |
|---|---|
| `--node-type <value>` | Node type label (e.g. `MRF`, `SBC`). Shown in title and header. |
| `--activity <value>` | Activity name (e.g. `ANNOUNCEMENT_LOADING`). Shown in title and header. |
| `--cr-group <value>` | Change Request group name (e.g. `GroupA`). Shown in subtitle and metadata. |
| `--json-dir <path>` | Directory containing per-node execution JSON files. Must exist. |
| `--output-html-path <path>` | Directory where the HTML report will be written. Created if absent. |
| `--output-html-name <filename>` | Filename for the generated report (e.g. `report.html`). |

**Optional:**

| Argument | Default | Description |
|---|---|---|
| `--request-id <value>` | `N/A` | Change Request ID shown in the metadata bar. |
| `--timestamp <value>` | Current date/time | Format: `yyyy-MM-dd HH:mm:ss`. |
| `--node-names <list>` | All JSON files in `--json-dir` | Comma-separated node names. Controls inclusion and display order. Matched against `metadata.nodeName`. |
| `--template <path>` | Built-in classpath template | Path to a custom HTML template. See [section 11](#11-custom-html-template). |

---

## 5. ReportConfig Reference

Whether constructing via the builder or using setters directly, all fields behave identically.

| Field | Builder method | Setter | Required | Default |
|---|---|---|---|---|
| Node type | `.nodeType(String)` | `setNodeType(String)` | Yes | — |
| Activity | `.activity(String)` | `setActivity(String)` | Yes | — |
| CR group | `.crGroup(String)` | `setCrGroup(String)` | Yes | — |
| JSON dir | `.jsonDir(String)` | `setJsonDir(String)` | Yes | — |
| Output HTML path | `.outputHtmlPath(String)` | `setOutputHtmlPath(String)` | Yes | — |
| Output HTML name | `.outputHtmlName(String)` | `setOutputHtmlName(String)` | Yes | — |
| Request ID | `.requestId(String)` | `setRequestId(String)` | No | `N/A` |
| Timestamp | `.timestamp(String)` | `setTimestamp(String)` | No | Current time |
| Node names | `.nodeNames(List<String>)` | `setNodeNames(List<String>)` | No | All JSON files |
| Template path | `.templatePath(String)` | `setTemplatePath(String)` | No | Built-in template |

Validation is applied automatically inside `ReportGenerator.generate()`.
Calling `config.validate()` directly is also supported for pre-flight checks.

---

## 6. Input: Per-Node Execution JSON Files

Each node requires one `.json` file in the input directory. The filename can be anything.
The display name in the report is always taken from `metadata.nodeName`.

### JSON Structure

```json
{
  "metadata": {
    "nodeName": "MRF1"
  },
  "data": {
    "show version": {
      "phase":                     "PRE_NODE_HEALTH_CHECK",
      "description":               "Check software version",
      "success":                   true,
      "reason":                    null,
      "failure":                   null,
      "validate":                  true,
      "validation_criteria":       "Version must be 17.0.x",
      "validation_status":         "SUCCESS",
      "validation_conclusion":     null,
      "output":                    "Nokia MRF version 17.0.3.7 ..."
    },
    "configure terminal": {
      "phase":                     "ACTIVITY_CONFIGURATION",
      "description":               "Enter configuration mode",
      "success":                   true,
      "reason":                    null,
      "failure":                   null,
      "validate":                  false,
      "validation_criteria":       null,
      "validation_status":         "SKIPPED",
      "validation_conclusion":     null,
      "output":                    "Enter configuration commands..."
    }
  }
}
```

### Field Descriptions

| Field | Type | Required | Description |
|---|---|---|---|
| `metadata.nodeName` | string | Recommended | Display name for the node. Falls back to the filename stem if absent. |
| `data` | object | Required | Map of command string → result. Insertion order is preserved. |
| `phase` | string | Optional | Execution phase. If absent or blank, the command is placed in an **OTHER** group. |
| `description` | string | Optional | Human-readable description of the command. |
| `success` | boolean | Required | `true` if the command executed without error. |
| `reason` | string | Optional | Short failure reason. |
| `failure` | string | Optional | Detailed failure message. Combined with `reason` in the Fail Reason column. |
| `validate` | boolean | Required | `true` if output validation was enabled for this command. |
| `validation_criteria` | string | Optional | Description of what was validated. |
| `validation_status` | string | Optional | `SUCCESS`, `FAILED`, `WARNING`, or `SKIPPED`. |
| `validation_conclusion` | string | Optional | Explanation of why validation failed or warned. |
| `output` | string | Optional | Raw command output (shown behind a "View Output" button). |

### Phase Execution Order

Commands are grouped and displayed in this sequence:

```
1. PRE_NODE_HEALTH_CHECK
2. BACKUP
3. ACTIVITY_PRECHECK
4. ACTIVITY_CONFIGURATION
5. ACTIVITY_POSTCHECK
6. POST_NODE_HEALTH_CHECK
7. ROLLBACK_PRECHECK
8. ROLLBACK_CONFIGURATION
9. ROLLBACK_POSTCHECK
```

Any unrecognised phase value is appended after the standard phases, in first-seen order.

---

## 7. Output

### Generated HTML File

Written to: `<output-html-path>/<output-html-name>`

The output directory is created automatically if it does not exist.

### Programmatic Result (`ReportGeneratorResult`)

`ReportGenerator.generate()` returns a `ReportGeneratorResult`:

```java
result.isSuccess()                              // true / false
result.getStatus()                              // "SUCCESS" or "FAILED"
result.getErrors()                              // 0 on success, 1 on failure
result.getParameters().get("REPORT_FILENAME")   // e.g. "report.html"   (on success)
result.getParameters().get("ERROR")             // error message         (on failure)
```

### CLI stdout

The CLI prints a machine-readable block to stdout after execution:

**Success:**
```
STATUS=SUCCESS
ERRORS=0
REPORT_FILENAME=report.html
```

**Failure:**
```
STATUS=FAILED
ERRORS=1
ERROR=<error message>
```

---

## 8. Understanding the HTML Report

### Page Header

Displays the node type, activity name, and CR group subtitle.

### Metadata Bar

Shows: Generated On, Request ID, Node Type, Activity, CR Group.

### Overall Statistics Cards

| Card | Description |
|---|---|
| Total Nodes | Total number of nodes in the report |
| Passed Nodes | Nodes where all commands succeeded |
| Failed Nodes | Nodes where one or more commands failed |
| *(second row)* | Command and validation counts: Total, Success, Failed, Val Enabled, Val Pass, Val Fail, Val Warn, Info Only |

### Node Summary Table

A clickable table listing all nodes. Each row shows:

- **Node Name** — click to jump directly to that node's detail section
- **Status** — SUCCESS / FAILED badge
- **Total** — total command count
- **Success / Failed** — execution outcome counts
- **Val Enabled** — count of commands with `validate: true`
- **Val Pass / Val Fail / Val Warn** — validation outcome breakdown
- **Info Only** — commands where validation was SKIPPED

### Node Detail Panels

One collapsible panel per node. Click the panel header to expand or collapse it.

Each panel contains:
- A mini stat card row for that node.
- A per-node command search box.
- The command detail table.

### Command Detail Table

Nine columns per command row:

| # | Column | Description |
|---|---|---|
| 1 | Command | Command string in a monospace dark code block |
| 2 | Description | Human-readable description |
| 3 | Exec Status | SUCCESS / FAILED badge |
| 4 | Fail Reason | `reason — failure` text; `#N/A` if command succeeded |
| 5 | Validate | True / False badge (whether validation was enabled) |
| 6 | Val Criteria | Validation criteria description |
| 7 | Val Status | SUCCESS / FAILED / WARNING / SKIPPED badge |
| 8 | Val Conclusion | Expandable validation failure detail, or `#N/A` |
| 9 | Raw Output | "View Output" button — expands/collapses the full command output |

### Phase Headers

Commands are grouped by phase. Each phase is preceded by a full-width clickable header row.
Click the header to collapse or expand all command rows in that phase.

---

## 9. Filtering and Interacting with the Report

### Global Node Search

A search box above the node summary table filters summary rows by node name in real time.

### Node Detail Controls

Buttons above the node panels:
- **Expand All** — expand all node panels at once
- **Collapse All** — collapse all node panels at once

### Per-Node Command Search

Each expanded node panel has its own search box that filters the command table rows
by any visible text (command, description, phase, etc.).

### Expanding Output / Validation Details

- **View Output** button — expands/collapses the raw command output.
- **View Details** button (Val Conclusion column) — expands/collapses validation failure
  detail when validation status is FAILED or WARNING.

---

## 10. Exporting to Excel

Click **Export XLSX** at the bottom of the report. An Excel workbook is generated entirely
in the browser (no server required) using the SheetJS library loaded from CDN.

One sheet is created per node. Columns per sheet:
Phase, Command, Description, Exec Status, Fail Reason, Validate, Val Criteria, Val Status, Val Conclusion.

> **Note:** The XLSX export requires an internet connection on first load to fetch SheetJS from CDN.
> For offline environments, use a custom template (see below) with SheetJS bundled locally.

---

## 11. Custom HTML Template

The built-in template is embedded in the JAR. Override it with `--template` (CLI) or
`ReportConfig.builder().templatePath(...)` (library) to customise branding or layout.

### Main Template Placeholders

| Placeholder | Value injected |
|---|---|
| `{{PAGE_TITLE}}` | Browser tab title |
| `{{REPORT_HEADER}}` | `NodeType — Activity` |
| `{{CR_GROUP_SUBTITLE}}` | `CR Group: <name>` |
| `{{META_GENERATED_ON}}` | Timestamp |
| `{{META_REQUEST_ID}}` | Request ID |
| `{{META_NODE_TYPE}}` | Node type |
| `{{META_ACTIVITY}}` | Activity name |
| `{{META_CR_GROUP}}` | CR group name |
| `{{TOTAL_NODES}}` | Total node count |
| `{{PASSED_NODES}}` | Passed node count |
| `{{FAILED_NODES}}` | Failed node count |
| `{{NODES_SUMMARY_ROWS}}` | Generated `<tr>` rows for the node summary table |
| `{{NODE_DETAIL_SECTIONS}}` | Generated HTML for all node detail panels |

### Sub-Template: tpl-node-panel

The per-node panel HTML is defined as a sub-template block inside the HTML file:

```html
<script type="text/x-html-template" id="tpl-node-panel">
  <!-- node panel HTML using {{NP_*}} placeholders -->
</script>
```

This block is extracted at load time and applied once per node. It must not appear directly
in the rendered `<body>`.

#### NP_* Placeholders (inside tpl-node-panel)

| Placeholder | Description |
|---|---|
| `{{NP_PANEL_CLS}}` | Extra CSS class — empty for success, ` panel-failed` for failure |
| `{{NP_ID}}` | Sanitised HTML element id derived from node name |
| `{{NP_INDEX}}` | 1-based node index |
| `{{NP_NAME}}` | Node name (HTML-escaped) |
| `{{NP_BADGE_CLS}}` | `badge-success` or `badge-error` |
| `{{NP_STATUS}}` | `SUCCESS` or `FAILED` |
| `{{NP_TOTAL}}` | Total command count |
| `{{NP_SUCCESS}}` | Success count |
| `{{NP_FAILED}}` | Failed count |
| `{{NP_VAL_TOTAL}}` | Validation-enabled count |
| `{{NP_VAL_PASS}}` | Validation pass count |
| `{{NP_VAL_FAIL}}` | Validation fail count |
| `{{NP_VAL_WARN}}` | Validation warning count |
| `{{NP_INFO_ONLY}}` | Validation skipped count |
| `{{NP_COMMAND_ROWS}}` | Generated HTML for all command rows (including phase headers) |

---

## 12. Exit Codes and stdout Format (CLI)

| Exit Code | Meaning |
|---|---|
| `0` | Report generated successfully |
| `1` | Report generation failed |

The stdout block is designed for machine parsing by orchestration tools:

```
STATUS=SUCCESS
ERRORS=0
REPORT_FILENAME=report.html
```

```
STATUS=FAILED
ERRORS=1
ERROR=Required field missing: --node-type
```

---

## 13. Troubleshooting

### "No JSON files found in: /path"
The `--json-dir` directory has no `.json` files. Verify the network-command-executor wrote
output files to the correct location.

### "None of the requested node names were found"
The names in `--node-names` / `nodeNames(...)` did not match any `metadata.nodeName` in the
JSON files. Node names are case-sensitive.

### "metadata.nodeName missing — falling back to filename"
The JSON file has no `metadata.nodeName`. The filename stem is used as the display name.
Add `"metadata": { "nodeName": "..." }` to the JSON to control the name.

### "Invalid path: --json-dir '...' does not exist"
The JSON directory does not exist. Ensure the network-command-executor has already run and
the path is correct.

### "Required field missing: --node-type" (or similar)
A required `ReportConfig` field was not set. See [section 5](#5-reportconfig-reference) for
the full list of required fields.

### "Sub-template not found in HTML template: tpl-node-panel"
A custom template was supplied but is missing the
`<script type="text/x-html-template" id="tpl-node-panel">` block.
See [section 11](#11-custom-html-template).

### Report Opens Blank in Browser
The HTML file requires JavaScript. Ensure the browser is not blocking scripts.
For local `file://` access in Chrome, launch with `--allow-file-access-from-files`
or serve via a local HTTP server.
