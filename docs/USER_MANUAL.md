# Execution Report Generator — User Manual

**Version:** 1.1.0
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
network-command-executor and generates up to two self-contained HTML reports:

**Summary Report** (multi-node mode — all nodes, generated unless `--generate-summary false`):
- Overall pass/fail statistics cards (Total Nodes, Passed Nodes, Failed Nodes).
- Phase matrix table: one row per node, one column per phase that was executed on at least one node.
- Each cell shows a PASSED, FAILED, or SKIPPED badge for that node–phase combination.
- Phases not executed on any node are excluded entirely from the table.
- Quick visual overview of which nodes and phases succeeded or failed.

**Detail Report — Multi-node mode** (failed nodes only):
- Full per-node collapsible panels with a complete command-by-command table.
- Commands grouped and sequenced by execution phase.
- Per-node search box and **Failed Only** toggle to focus on failures.
- Paginated display (25 nodes per page) for efficient browsing of large rollouts.
- Raw command output and validation failure details loaded on demand.
- An XLSX export button (browser-side, no server needed).

**Detail Report — Single-node mode** (always generated, uses a dedicated template):
- **Phase Execution Summary** table at the top: Phase | Total | Success | Failed | Status.
- Node detail panel auto-expanded below the phase table.
- No search/filter, pagination, or expand/collapse controls (single node only).
- Same command-by-command table, XLSX export, and lazy output loading as multi-node.

**Single-node mode:** pass a single JSON file path via `--json-file` (or pass the file path
directly to `--json-dir` — it is auto-detected). The summary report is skipped automatically.

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

// ── Multi-node mode (directory of JSON files) ──────────────────────
ReportConfig config = ReportConfig.builder()
    .nodeType("MRF")
    .activity("ANNOUNCEMENT_LOADING")
    .crGroup("GroupA")
    .jsonDir("/path/to/execution/jsons")
    .outputHtmlPath("/path/to/output")
    .outputHtmlName("report_detail.html")
    .summaryHtmlName("report_summary.html")
    .requestId("CR-98765")              // optional
    .timestamp("2026-04-19 14:00:00")   // optional — defaults to now
    .nodeNames(Arrays.asList("MRF1", "MRF2", "MRF3"))  // optional — defaults to all
    .generateSummary(true)              // optional — default: true
    .build();

// ── Single-node mode (one JSON file) ────────────────────────────────
ReportConfig singleConfig = ReportConfig.builder()
    .nodeType("MRF")
    .activity("ANNOUNCEMENT_LOADING")
    .crGroup("GroupA")
    .jsonFile("/path/to/MRF1_execution_report.json")  // single file — summary skipped
    .outputHtmlPath("/path/to/output")
    .outputHtmlName("MRF1_report_detail.html")
    .requestId("CR-98765")
    .build();

try {
    ReportGeneratorResult result = new ReportGenerator().generate(config);
    if (result.isSuccess()) {
        String summary = result.getParameters().get("SUMMARY_REPORT_FILENAME"); // null in single-node mode
        String detail  = result.getParameters().get("DETAIL_REPORT_FILENAME");  // null if all nodes passed (multi-node)
        if (summary != null) System.out.println("Summary report: " + summary);
        if (detail  != null) System.out.println("Detail report:  " + detail);
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

#### Basic example (multi-node)

```bash
java -jar execution-report-generator-1.0.0-cli.jar \
  --node-type           MRF                        \
  --activity            ANNOUNCEMENT_LOADING       \
  --cr-group            GroupA                     \
  --json-dir            /path/to/execution/jsons   \
  --output-html-path    /path/to/output            \
  --output-html-name    report_detail.html         \
  --output-summary-name report_summary.html
```

#### Single-node mode

Pass a single JSON file via `--json-file`. The summary report is skipped automatically.
The detail report is always generated and shows command-level statistics.

```bash
java -jar execution-report-generator-1.0.0-cli.jar \
  --node-type        MRF                                              \
  --activity         ANNOUNCEMENT_LOADING                             \
  --cr-group         GroupA                                           \
  --json-file        /opt/nce/results/MRF1_execution_report.json      \
  --output-html-path /opt/reports/2026-04-19                          \
  --output-html-name MRF1_ANNOUNCEMENT_LOADING_detail.html
```

> **Tip:** You can also pass a file path to `--json-dir` — it is automatically detected
> as a single file and promoted to `--json-file` mode.

#### Skip the summary report (multi-node)

```bash
java -jar execution-report-generator-1.0.0-cli.jar \
  --node-type           MRF                        \
  --activity            ANNOUNCEMENT_LOADING       \
  --cr-group            GroupA                     \
  --json-dir            /path/to/execution/jsons   \
  --output-html-path    /path/to/output            \
  --output-html-name    report_detail.html         \
  --generate-summary    false
```

#### With optional arguments

```bash
java -jar execution-report-generator-1.0.0-cli.jar \
  --node-type           MRF                                          \
  --activity            ANNOUNCEMENT_LOADING                         \
  --cr-group            GroupA                                       \
  --json-dir            /opt/nce/results/GroupA                      \
  --output-html-path    /opt/reports/2026-04-19                      \
  --output-html-name    MRF_ANNOUNCEMENT_LOADING_detail.html         \
  --output-summary-name MRF_ANNOUNCEMENT_LOADING_summary.html        \
  --request-id          CR-98765                                     \
  --timestamp           "2026-04-19 14:30:00"                        \
  --node-names          MRF1,MRF2,MRF3
```

#### With custom templates

```bash
java -jar execution-report-generator-1.0.0-cli.jar \
  --node-type           MRF                                  \
  --activity            ANNOUNCEMENT_LOADING                 \
  --cr-group            GroupA                               \
  --json-dir            /path/to/jsons                       \
  --output-html-path    /path/to/output                      \
  --output-html-name    report_detail.html                   \
  --output-summary-name report_summary.html                  \
  --template            /path/to/custom_detail_template.html \
  --summary-template    /path/to/custom_summary_template.html
```

#### CLI arguments

**Required (one of `--json-dir` or `--json-file` must be provided):**

| Argument | Description |
|---|---|
| `--node-type <value>` | Node type label (e.g. `MRF`, `SBC`). Shown in title and header. |
| `--activity <value>` | Activity name (e.g. `ANNOUNCEMENT_LOADING`). Shown in title and header. |
| `--cr-group <value>` | Change Request group name (e.g. `GroupA`). Shown in subtitle and metadata. |
| `--json-dir <path>` | Directory containing per-node execution JSON files. Auto-detected as a file if the path is a file. |
| `--json-file <path>` | Path to a single execution JSON file (single-node mode). |
| `--output-html-path <path>` | Directory where HTML reports will be written. Created if absent. |
| `--output-html-name <filename>` | Filename for the detail report (e.g. `report_detail.html`). |
| `--output-summary-name <filename>` | Filename for the summary report. Required unless `--generate-summary false` or single-node mode. |

**Optional:**

| Argument | Default | Description |
|---|---|---|
| `--generate-summary <true\|false>` | `true` | Set to `false` to skip summary report generation (multi-node mode only). |
| `--request-id <value>` | `N/A` | Change Request ID shown in the metadata bar. |
| `--timestamp <value>` | Current date/time | Format: `yyyy-MM-dd HH:mm:ss`. |
| `--node-names <list>` | All JSON files in `--json-dir` | Comma-separated node names. Controls inclusion and display order. Matched against `metadata.nodeName`. |
| `--template <path>` | Built-in classpath template | Path to a custom HTML detail template. See [section 11](#11-custom-html-template). |
| `--summary-template <path>` | Built-in classpath summary template | Path to a custom HTML summary template. |

---

## 5. ReportConfig Reference

Whether constructing via the builder or using setters directly, all fields behave identically.

| Field | Builder method | Setter | Required | Default |
|---|---|---|---|---|
| Node type | `.nodeType(String)` | `setNodeType(String)` | Yes | — |
| Activity | `.activity(String)` | `setActivity(String)` | Yes | — |
| CR group | `.crGroup(String)` | `setCrGroup(String)` | Yes | — |
| JSON dir | `.jsonDir(String)` | `setJsonDir(String)` | One of `jsonDir` / `jsonFile` | — |
| JSON file | `.jsonFile(String)` | `setJsonFile(String)` | One of `jsonDir` / `jsonFile` | — |
| Output HTML path | `.outputHtmlPath(String)` | `setOutputHtmlPath(String)` | Yes | — |
| Output HTML name | `.outputHtmlName(String)` | `setOutputHtmlName(String)` | Yes | — |
| Summary HTML name | `.summaryHtmlName(String)` | `setSummaryHtmlName(String)` | Only when `generateSummary=true` | — |
| Generate summary | `.generateSummary(boolean)` | `setGenerateSummary(boolean)` | No | `true` |
| Request ID | `.requestId(String)` | `setRequestId(String)` | No | `N/A` |
| Timestamp | `.timestamp(String)` | `setTimestamp(String)` | No | Current time |
| Node names | `.nodeNames(List<String>)` | `setNodeNames(List<String>)` | No | All JSON files |
| Template path | `.templatePath(String)` | `setTemplatePath(String)` | No | Built-in detail template |
| Summary template path | `.summaryTemplatePath(String)` | `setSummaryTemplatePath(String)` | No | Built-in summary template |

**`jsonDir` auto-detection:** if the path supplied to `jsonDir` is a file (not a directory),
it is automatically promoted to `jsonFile` mode. `summaryHtmlName` is not required in this case.

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
      "target":                    "niam_server",
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
      "target":                    "local",
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
| `target` | string | Optional | Target node or host where the command was executed (e.g. `niam_server`, `local`). Displayed in the **Target Node** column. |
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
6. ROLLBACK_PRECHECK
7. ROLLBACK_CONFIGURATION
8. ROLLBACK_POSTCHECK
9. POST_NODE_HEALTH_CHECK
```

Any unrecognised phase value is appended after the standard phases, in first-seen order.

---

## 7. Output

### Generated HTML Files

| File | When generated | Path |
|---|---|---|
| Summary report | Multi-node mode when `generateSummary=true` (default) | `<output-html-path>/<output-summary-name>` |
| Detail report | Multi-node: only when ≥1 node failed. Single-node: always. | `<output-html-path>/<output-html-name>` |

The output directory is created automatically if it does not exist.

### Programmatic Result (`ReportGeneratorResult`)

`ReportGenerator.generate()` returns a `ReportGeneratorResult`:

```java
result.isSuccess()                                       // true / false
result.getStatus()                                       // "SUCCESS" or "FAILED"
result.getErrors()                                       // 0 on success, 1 on failure
result.getParameters().get("SUMMARY_REPORT_FILENAME")    // e.g. "report_summary.html"
                                                         //   null in single-node mode or when generateSummary=false
result.getParameters().get("DETAIL_REPORT_FILENAME")     // e.g. "report_detail.html"
                                                         //   null in multi-node mode when all nodes passed
result.getParameters().get("ERROR")                      // error message (on failure)
```

### CLI stdout

The CLI prints a machine-readable block to stdout after execution:

**Multi-node success with failures detected:**
```
STATUS=SUCCESS
ERRORS=0
SUMMARY_REPORT_FILENAME=report_summary.html
DETAIL_REPORT_FILENAME=report_detail.html
```

**Multi-node success, all nodes passed (no detail report):**
```
STATUS=SUCCESS
ERRORS=0
SUMMARY_REPORT_FILENAME=report_summary.html
```

**Single-node mode (summary always skipped):**
```
STATUS=SUCCESS
ERRORS=0
DETAIL_REPORT_FILENAME=MRF1_detail.html
```

**Failure:**
```
STATUS=FAILED
ERRORS=1
ERROR=<error message>
```

---

## 8. Understanding the HTML Reports

All reports share the same page header and metadata bar.

### Page Header

Displays the node type, activity name, and CR group subtitle.

### Metadata Bar

Shows: Generated On, Request ID, Node Type, Activity, CR Group.

---

### Summary Report

The summary report covers **all nodes** and gives a high-level phase-by-phase status overview.
It is only generated in multi-node mode (and can be disabled with `--generate-summary false`).

#### Phase Matrix Table

One row per node, one column per phase. Only phases executed on at least one node appear;
phases absent from all nodes are excluded entirely.

Each cell shows one of three badges:

| Badge | Meaning |
|---|---|
| **PASSED** (green) | All commands in that phase succeeded for this node |
| **FAILED** (red) | One or more commands in that phase failed for this node |
| **SKIPPED** (grey) | This node did not execute any commands in that phase |

Failed-node rows have a red background. Hovering a row highlights it in blue.

#### Statistics Cards (Summary)

| Card | Description |
|---|---|
| Total Nodes | Total number of nodes |
| Passed Nodes | Nodes where all commands succeeded |
| Failed Nodes | Nodes where one or more commands failed |

---

### Detail Report — Multi-node Mode

Covers **only failed nodes**. Generated only when at least one node failed.

#### Statistics Cards

| Card | Description |
|---|---|
| Total Nodes | Number of failed nodes included in this report |
| Nodes Passed | Always 0 (only failed nodes are shown) |
| Nodes Failed | Same as Total Nodes |

#### Node Summary Table

A clickable table listing all failed nodes. Each row shows:

- **Node Name** — click to jump directly to that node's detail section
- **Status** — SUCCESS / FAILED badge
- **Total** — total command count
- **Success / Failed** — execution outcome counts
- **Val Enabled** — count of commands with `validate: true`
- **Val Pass / Val Fail / Val Warn** — validation outcome breakdown
- **Info Only** — commands where validation was SKIPPED

#### Node Detail Panels

One collapsible panel per node. Click the panel header to expand or collapse it.

Each panel contains:
- A mini stat card row for that node (command counts + validation counts).
- A per-node toolbar with a **search box** and a **Failed Only** toggle button.
- The command detail table.

**Failed Only toggle:** When active, passing command rows are hidden so only failed commands are visible. Click again to restore all rows.

---

### Detail Report — Single-node Mode

Always generated regardless of pass/fail. Uses a dedicated template
(`mop_execution_single_node_report_template.html`).

#### Phase Execution Summary Table

Shown at the top of the report. One row per phase executed by the node:

| Column | Description |
|---|---|
| Phase | Phase name (e.g. PRE_NODE_HEALTH_CHECK) |
| Total | Total commands in that phase |
| Success | Commands that succeeded |
| Failed | Commands that failed |
| Status | PASSED (green) / FAILED (red) badge |

Failed phase rows have a red background.

#### Node Detail Panel

The single node's detail panel is displayed below the phase table and is **auto-expanded** on load.
It contains the same command stat cards, command detail table, and output/validation buttons
as the multi-node panels — but without the search box, Failed Only toggle, or
global Expand All / Collapse All controls.

### Command Detail Table

Ten columns per command row:

| # | Column | Description |
|---|---|---|
| 1 | Command | Command string in a monospace dark code block |
| 2 | Description | Human-readable description |
| 3 | Target Node | Host or system where the command was executed |
| 4 | Exec Status | SUCCESS / FAILED badge |
| 5 | Fail Reason | `reason — failure` text; `#N/A` if command succeeded |
| 6 | Validate | True / False badge (whether validation was enabled) |
| 7 | Val Criteria | Validation criteria description |
| 8 | Val Status | SUCCESS / FAILED / WARNING / SKIPPED badge |
| 9 | Val Conclusion | Expandable validation failure detail, or `#N/A` |
| 10 | Raw Output | "View Output" button — expands/collapses the full command output |

### Phase Headers

Commands are grouped by phase. Each phase is preceded by a full-width clickable header row.
Click the header to collapse or expand all command rows in that phase.

---

## 9. Filtering and Interacting with the Report

> The features in this section apply to the **multi-node detail report** only.
> The single-node report has no search, pagination, or expand/collapse controls.

### Global Node Search

A search box above the node summary table filters both the summary rows and the node
panels by node name in real time. The panel count and pagination are updated automatically.

### Pagination

The detail report displays **25 nodes per page**. Prev / Next buttons appear when there
are more than 25 nodes. Clicking a node name in the summary table automatically jumps
to the correct page and expands that node's panel.

### Node Detail Controls

Buttons above the node panels:
- **Expand All** — expand all visible node panels at once
- **Collapse All** — collapse all visible node panels

### Per-Node Toolbar

Each expanded node panel has:
- **Search box** — filters command table rows by any visible text in real time.
- **Failed Only button** — when active, hides all passing command rows so only failures are visible. Click again (now labelled **Show All**) to restore all rows.

### Expanding Output / Validation Details

- **View Output** button — loads and shows the raw command output on demand (output is not pre-loaded into the page).
- **View Details** button (Val Conclusion column) — loads and shows validation failure detail on demand when validation status is FAILED or WARNING.

> Raw outputs and validation conclusions are stored in a compact JavaScript data store and
> injected into the DOM only when requested. This keeps the detail report file size small
> regardless of output volume.

---

## 10. Exporting to Excel

Click **Export XLSX** at the bottom of the report. An Excel workbook is generated entirely
in the browser (no server required) using the SheetJS library loaded from CDN.

One sheet is created per node. Columns per sheet:
Node, Phase, Command, Description, Target Node, Exec Status, Fail Reason, Validate, Val Criteria, Val Status, Val Conclusion.

> **Note:** The XLSX export requires an internet connection on first load to fetch SheetJS from CDN.
> For offline environments, use a custom template (see below) with SheetJS bundled locally.

---

## 11. Custom HTML Template

Three built-in templates are embedded in the JAR:

| Template file | Used for |
|---|---|
| `mop_execution_report_template.html` | Multi-node detail report |
| `mop_execution_single_node_report_template.html` | Single-node detail report |
| `mop_execution_summary_report_template.html` | Multi-node summary report |

Override the detail/single-node template with `--template` (CLI) or
`ReportConfig.builder().templatePath(...)` (library).
Override the summary template with `--summary-template` or `.summaryTemplatePath(...)`.

### Multi-node Detail Template Placeholders

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
| `{{STAT_LABEL_TOTAL}}` | Card title: `"Total Nodes"` |
| `{{STAT_LABEL_PASSED}}` | Card title: `"Nodes Passed"` |
| `{{STAT_LABEL_FAILED}}` | Card title: `"Nodes Failed"` |
| `{{TOTAL_NODES}}` | Failed node count |
| `{{PASSED_NODES}}` | Always `0` |
| `{{FAILED_NODES}}` | Failed node count |
| `{{NODES_SUMMARY_ROWS}}` | Generated `<tr>` rows for the node summary table |
| `{{NODE_DETAIL_SECTIONS}}` | Generated HTML for all node detail panels |
| `{{RAW_OUTPUTS_JS}}` | JS object literal mapping row key → raw command output text |
| `{{VAL_CONCLUSIONS_JS}}` | JS object literal mapping row key → validation conclusion text |

### Single-node Detail Template Placeholders

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
| `{{PHASE_SUMMARY_ROWS}}` | Generated `<tr>` rows for the Phase Execution Summary table |
| `{{NODE_DETAIL_SECTIONS}}` | Generated HTML for the node detail panel |
| `{{RAW_OUTPUTS_JS}}` | JS object literal mapping row key → raw command output text |
| `{{VAL_CONCLUSIONS_JS}}` | JS object literal mapping row key → validation conclusion text |

### Summary Template Placeholders

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
| `{{SUMMARY_PHASE_HEADERS}}` | `<th>` elements for each phase column |
| `{{SUMMARY_TABLE_ROWS}}` | `<tr>` rows for the phase matrix table (one per node) |

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

#### CR_* Placeholders (inside tpl-command-row)

| Placeholder | Description |
|---|---|
| `{{CR_PHASE}}` | Phase name (written to `data-phase` attribute for XLSX export) |
| `{{CR_IS_FAILED}}` | `1` if command failed, `0` if succeeded (used by Failed Only filter) |
| `{{CR_KEY}}` | Unique row key used to look up output and val-conclusion from JS stores |
| `{{CR_CMD}}` | Command string (HTML-escaped) |
| `{{CR_DESC}}` | Description (HTML-escaped) |
| `{{CR_TARGET}}` | Target node/host (HTML-escaped) |
| `{{CR_EXEC_BADGE}}` | `badge-success` or `badge-error` |
| `{{CR_EXEC_LABEL}}` | `Success` or `Failed` |
| `{{CR_FAIL_REASON}}` | Combined reason/failure text; `#N/A` if command succeeded |
| `{{CR_VALIDATE_BADGE}}` | `badge-enabled` or `badge-disabled` |
| `{{CR_VALIDATE_LABEL}}` | `True` or `False` |
| `{{CR_VAL_CRITERIA}}` | Validation criteria text |
| `{{CR_VAL_BADGE}}` | Badge class for validation status |
| `{{CR_VAL_STATUS}}` | Validation status text |
| `{{CR_VAL_DETAIL}}` | Rendered from `tpl-val-detail-static` or `tpl-val-detail-expandable` |

---

## 12. Exit Codes and stdout Format (CLI)

| Exit Code | Meaning |
|---|---|
| `0` | Reports generated successfully |
| `1` | Report generation failed |

The stdout block is designed for machine parsing by orchestration tools:

```
STATUS=SUCCESS
ERRORS=0
SUMMARY_REPORT_FILENAME=report_summary.html
DETAIL_REPORT_FILENAME=report_detail.html
```

- `SUMMARY_REPORT_FILENAME` is omitted when in single-node mode or `--generate-summary false`.
- `DETAIL_REPORT_FILENAME` is omitted in multi-node mode when all nodes passed.

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
The JSON path does not exist. Ensure the network-command-executor has already run and
the path is correct.

### "Invalid path: --json-file '...' is not a file"
The path given to `--json-file` does not point to an existing file.

### "Required field missing: --node-type" (or similar)
A required `ReportConfig` field was not set. See [section 5](#5-reportconfig-reference) for
the full list of required fields.

### Detail report not generated (multi-node, all passed)
In multi-node mode the detail report is only written when at least one node failed. If all
nodes passed, only the summary report is written. Use single-node mode (`--json-file`) to
always produce a detail report.

### "Sub-template not found in HTML template: tpl-node-panel"
A custom template was supplied but is missing the
`<script type="text/x-html-template" id="tpl-node-panel">` block.
See [section 11](#11-custom-html-template).

### Report Opens Blank in Browser
The HTML file requires JavaScript. Ensure the browser is not blocking scripts.
For local `file://` access in Chrome, launch with `--allow-file-access-from-files`
or serve via a local HTTP server.
