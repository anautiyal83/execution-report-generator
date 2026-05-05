# Execution Report Generator — Technical Documentation

**Version:** 1.1.0
**Group ID:** `com.nokia`
**Artifact ID:** `execution-report-generator`
**Java:** 8+
**Build:** Maven 3.x

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Maven Artifacts](#2-maven-artifacts)
3. [Package and Class Structure](#3-package-and-class-structure)
4. [Public API](#4-public-api)
   - [4.1 ReportGenerator](#41-reportgenerator)
   - [4.2 ReportConfig / ReportConfig.Builder](#42-reportconfig--reportconfigbuilder)
   - [4.3 ReportGeneratorResult](#43-reportgeneratorresult)
   - [4.4 ReportGeneratorException](#44-reportgeneratorexception)
5. [Internal Components](#5-internal-components)
   - [5.1 NodeJsonReader](#51-nodejsonreader)
   - [5.2 TemplateEngine](#52-templateengine)
   - [5.3 HtmlFragmentBuilder](#53-htmlfragmentbuilder)
   - [5.4 ReportWriter](#54-reportwriter)
6. [Data Model](#6-data-model)
7. [HTML Template System](#7-html-template-system)
8. [Execution Flow](#8-execution-flow)
9. [CLI Entry Point](#9-cli-entry-point)
10. [Dependencies](#10-dependencies)
11. [Extension Points](#11-extension-points)

---

## 1. Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     Calling code / CLI                          │
│   ReportConfig (built via builder or setters)                   │
└────────────────────────────┬────────────────────────────────────┘
                             │
                             ▼
                    ┌─────────────────┐
                    │  ReportGenerator │  ← Public API entry point
                    └────────┬────────┘
                             │
          ┌──────────────────┼──────────────────┐
          ▼                  ▼                  ▼
  ┌───────────────┐  ┌──────────────┐  ┌──────────────┐
  │ NodeJsonReader│  │TemplateEngine│  │  ReportWriter │
  │               │  │  (×2)        │  │               │
  │ read() or     │  │ Summary +    │  │ Writes final  │
  │ readSingle()  │  │ Detail       │  │ HTML to disk  │
  │               │  │ templates    │  └──────────────┘
  └───────┬───────┘  └──────┬───────┘
          │                 │
          ▼                 ▼
  List<NodeExecutionData>  sub-templates (String)
          │                 │
          └────────┬────────┘
                   ▼
    ┌──────────────────────────────────┐
    │  SummaryHtmlFragmentBuilder      │  → summary report (multi-node, optional)
    │  buildSummaryPhaseHeaders()      │
    │  buildSummaryTableRows()         │
    │  (uses PhaseStats per node)      │
    ├──────────────────────────────────┤
    │  HtmlFragmentBuilder             │  → detail report
    │  buildNodesSummaryRows()         │    (failed nodes — multi-node)
    │  buildAllNodeSections()          │    (all nodes   — single-node)
    └──────────────────────────────────┘
```

The design separates concerns into four layers:

1. **API layer** — `ReportGenerator` + `ReportConfig` + `ReportGeneratorResult` — the only surface callers need.
2. **Reading layer** — `NodeJsonReader` deserialises per-node JSON files into the internal model.
3. **Rendering layer** — `TemplateEngine` × 2 + `SummaryHtmlFragmentBuilder` + `HtmlFragmentBuilder` produce two HTML reports.
4. **Writing layer** — `ReportWriter` persists each HTML report to disk.

---

## 2. Maven Artifacts

`mvn package` produces two JARs:

| Artifact | Classifier | Purpose |
|---|---|---|
| `execution-report-generator-1.0.0.jar` | *(none)* | Thin library JAR — use as a Maven dependency in other projects |
| `execution-report-generator-1.0.0-cli.jar` | `cli` | Fat runnable JAR — all dependencies shaded in; run with `java -jar` |

The Shade plugin is configured with `shadedArtifactAttached=true` and
`shadedClassifierName=cli`, so the primary (unclassified) artifact is always the thin
library JAR. The fat JAR is a secondary attached artifact.

**Dependency declaration for library use:**
```xml
<dependency>
    <groupId>com.nokia</groupId>
    <artifactId>execution-report-generator</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## 3. Package and Class Structure

```
com.nokia.report
├── ReportGenerator              Public API — instantiate to generate reports
├── ReportGeneratorException     Checked exception for all generation failures
├── ReportGeneratorMain          CLI entry point (main method only)
│
├── config
│   └── ReportConfig             Input parameters + fluent Builder + validate()
│
├── model
│   ├── NodeExecutionJson        Root JSON structure (metadata + data map)
│   ├── ExecutionMetadata        metadata.nodeName
│   ├── CommandResultDetail      Per-command result fields
│   ├── NodeExecutionData        Parsed node: name + ordered commands map
│   ├── ExecutionStats           Computed node-level statistics
│   ├── PhaseStats               Computed per-phase statistics for one node
│   └── ReportGeneratorResult    Generation outcome (status / errors / parameters)
│
├── reader
│   └── NodeJsonReader           Loads and filters per-node JSON files
│
├── template
│   └── TemplateEngine           Loads HTML template; resolves {{PLACEHOLDER}} markers;
│                                extracts sub-templates
├── builder
│   ├── HtmlFragmentBuilder      Builds HTML for detail report (node panels, command rows)
│   └── SummaryHtmlFragmentBuilder  Builds HTML for summary report (phase breakdown panels)
│
└── writer
    └── ReportWriter             Writes the rendered HTML string to a file
```

---

## 4. Public API

### 4.1 ReportGenerator

```java
package com.nokia.report;

public class ReportGenerator {
    /**
     * Generate the HTML report described by config.
     * Calls config.validate() before starting.
     *
     * @throws ReportGeneratorException on invalid config, bad paths,
     *         malformed JSON, template errors, or IO failures
     */
    public ReportGeneratorResult generate(ReportConfig config)
            throws ReportGeneratorException;
}
```

`ReportGenerator` is stateless and thread-safe. A new instance can be created per call or
shared.

### 4.2 ReportConfig / ReportConfig.Builder

`ReportConfig` carries all input parameters. It can be constructed with the fluent builder
or by calling setters directly (e.g. when building from a property map).

```java
// Builder — multi-node mode
ReportConfig config = ReportConfig.builder()
    .nodeType("MRF")
    .activity("ANNOUNCEMENT_LOADING")
    .crGroup("GroupA")
    .jsonDir("/path/to/jsons")            // directory of JSON files
    .outputHtmlPath("/path/to/output")
    .outputHtmlName("report_detail.html")
    .summaryHtmlName("report_summary.html")
    .generateSummary(true)               // optional — default: true
    .requestId("CR-98765")               // optional — default: "N/A"
    .timestamp("2026-04-19 14:00")       // optional — default: current time
    .nodeNames(nodeList)                 // optional — default: all JSON files in jsonDir
    .templatePath("/custom.html")        // optional — default: built-in detail template
    .summaryTemplatePath("/custom_sum.html") // optional — default: built-in summary template
    .build();

// Builder — single-node mode
ReportConfig single = ReportConfig.builder()
    .nodeType("MRF")
    .activity("ANNOUNCEMENT_LOADING")
    .crGroup("GroupA")
    .jsonFile("/path/to/MRF1.json")      // single file — summary report skipped
    .outputHtmlPath("/path/to/output")
    .outputHtmlName("MRF1_detail.html")
    .build();

// Direct setters (equivalent)
ReportConfig config = new ReportConfig();
config.setNodeType("MRF");
config.setActivity("ANNOUNCEMENT_LOADING");
// ...

// Pre-flight validation (also called automatically inside generate())
config.validate(); // throws ReportGeneratorException if invalid
```

**Fields:**

| Field | Type | Required | Default |
|---|---|---|---|
| `nodeType` | String | Yes | — |
| `activity` | String | Yes | — |
| `crGroup` | String | Yes | — |
| `jsonDir` | String | One of `jsonDir`/`jsonFile` | — |
| `jsonFile` | String | One of `jsonDir`/`jsonFile` | — |
| `outputHtmlPath` | String | Yes | — |
| `outputHtmlName` | String | Yes | — |
| `summaryHtmlName` | String | Only when `generateSummary=true` | — |
| `generateSummary` | boolean | No | `true` |
| `requestId` | String | No | `"N/A"` |
| `timestamp` | String | No | Current `yyyy-MM-dd HH:mm:ss` |
| `nodeNames` | `List<String>` | No | All JSON files in `jsonDir` |
| `templatePath` | String | No | Built-in detail classpath template |
| `summaryTemplatePath` | String | No | Built-in summary classpath template |

`getOutputHtml()` returns `outputHtmlPath + File.separator + outputHtmlName`.
`getSummaryHtml()` returns `outputHtmlPath + File.separator + summaryHtmlName`.

**`validate()` auto-detection:** if `jsonDir` is set and the path points to a regular file,
`validate()` promotes it to `jsonFile` (sets `jsonDir = null`). Callers can therefore pass
a single file path via `--json-dir` without error.

### 4.3 ReportGeneratorResult

```java
package com.nokia.report.model;

public class ReportGeneratorResult {
    public boolean isSuccess();
    public String  getStatus();          // "SUCCESS" or "FAILED"
    public int     getErrors();          // 0 on success, 1 on failure
    public LinkedHashMap<String, String> getParameters();
    // On success: parameters may contain:
    //   SUMMARY_REPORT_FILENAME=<filename>  (omitted in single-node mode or generateSummary=false)
    //   DETAIL_REPORT_FILENAME=<filename>   (omitted in multi-node mode when all nodes passed)
    // On failure: parameters contains ERROR=<message>
}
```

Static factory methods:
```java
ReportGeneratorResult.success("summary.html", "detail.html");  // both reports
ReportGeneratorResult.success("summary.html", null);            // summary only (all passed, multi-node)
ReportGeneratorResult.success(null, "detail.html");             // detail only (single-node mode)
ReportGeneratorResult.failure("error message");
```

### 4.4 ReportGeneratorException

```java
package com.nokia.report;

public class ReportGeneratorException extends Exception {
    public ReportGeneratorException(String message);
    public ReportGeneratorException(String message, Throwable cause);
}
```

Thrown by `ReportGenerator.generate()` for all error conditions. The `cause` wraps the
underlying IOException, IllegalArgumentException, or other exception if applicable.

---

## 5. Internal Components

### 5.1 NodeJsonReader

```java
package com.nokia.report.reader;

public class NodeJsonReader {
    /** Multi-node: reads all *.json files in a directory. */
    public List<NodeExecutionData> read(String jsonDir, List<String> nodeNames)
            throws IOException;

    /** Single-node: reads exactly one JSON file. */
    public List<NodeExecutionData> readSingle(String jsonFilePath)
            throws IOException;
}
```

**`read()` behaviour:**
- Lists all `*.json` files in `jsonDir`, sorted alphabetically.
- Deserialises each file into `NodeExecutionJson` using Jackson `ObjectMapper`.
- Node name is resolved from `metadata.nodeName`; if absent, falls back to filename stem
  with a warning log.
- Duplicate `metadata.nodeName` values (across files) are skipped with a warning.
- If `nodeNames` is non-null and non-empty, only nodes whose name appears in the list are
  included, in list order. A warning is logged for any requested name with no matching file.
  Throws `IOException` if none of the requested names are found.

**`readSingle()` behaviour:**
- Deserialises the single specified JSON file.
- Returns a `List` containing exactly one `NodeExecutionData`.
- Node name resolution and fallback are identical to `read()`.

### 5.2 TemplateEngine

```java
package com.nokia.report.template;

public class TemplateEngine {
    public TemplateEngine() throws IOException;                                       // built-in detail classpath template
    public TemplateEngine(String templatePath) throws IOException;                    // external file path
    public static TemplateEngine fromClasspathResource(String name) throws IOException; // named classpath resource
    public String render(Map<String, String> values);
    public String getSubTemplate(String id);
}
```

**Behaviour:**
- On construction, loads the HTML template (classpath or file) and calls `process()`.
- `process()` uses a regex to find all `<script type="text/x-html-template" id="...">...</script>`
  blocks, stores them in `subTemplates` map, and removes them from the main template string.
- `render(values)` replaces every `{{KEY}}` in the main template with the corresponding value.
  Unknown placeholders are left in place (no error).
- `getSubTemplate(id)` returns the content of the named sub-template block.
  Throws `IllegalArgumentException` if the id is not found.

**Sub-template regex:**
```
<script\s+type="text/x-html-template"\s+id="([^"]+)">(.*?)</script>
```
Compiled with `Pattern.DOTALL` to match multi-line content.

### 5.3 HtmlFragmentBuilder

Used for the **detail report** (failed nodes in multi-node mode; the single node in single-node mode).

```java
package com.nokia.report.builder;

public class HtmlFragmentBuilder {
    public HtmlFragmentBuilder(TemplateEngine engine);
    public String buildNodesSummaryRows(List<NodeExecutionData> nodes);
    public String buildAllNodeSections(List<NodeExecutionData> nodes);
    public String getRawOutputsJs();
    public String getValConclusionsJs();
    public static String esc(String s);
    public static String nodeId(String nodeName);
}
```

Constructor loads all required sub-templates from the engine:
`tpl-node-panel`, `tpl-summary-row`, `tpl-phase-header`, `tpl-command-row`,
`tpl-val-detail-static`, `tpl-val-detail-expandable`.

#### JS Data Stores (file size optimisation)

During `buildAllNodeSections()` each command row is assigned a unique key (`r0`, `r1`, …).
Raw command outputs and validation conclusions are stored in two internal maps
(`rawOutputStore`, `valConclusionStore`) rather than being embedded in the HTML DOM.

After building all sections, call:

- `getRawOutputsJs()` → serialises `rawOutputStore` as a JS object literal for `{{RAW_OUTPUTS_JS}}`
- `getValConclusionsJs()` → serialises `valConclusionStore` as a JS object literal for `{{VAL_CONCLUSIONS_JS}}`

These are injected into a `<script>` block once in the page. The browser loads raw output and
validation detail lazily into the DOM only when the user clicks **View Output** / **View Details**.
This eliminates 70–90% of file size for typical executions with large command outputs.

**`buildNodesSummaryRows`**
Iterates nodes, computes `ExecutionStats` per node, fills `tpl-summary-row` with `{{SR_*}}`
placeholders and emits one `<tr>` per node.

**`buildAllNodeSections`**
Iterates nodes, calls `buildNodePanel(node, index)` for each. `buildNodePanel` fills
`tpl-node-panel` via `{{NP_*}}` placeholders. `{{NP_COMMAND_ROWS}}` is built by
`buildCommandTableRows`.

**`buildCommandTableRows`**

1. Groups commands by phase; commands with no phase go into `"OTHER"`.
2. Sorts phases by `PHASE_ORDER`; unrecognised phases appended after.
3. For each phase: fills `tpl-phase-header` (`{{PH_PHASE}}`).
4. For each command: assigns a unique key, stores output/val-conclusion in JS stores,
   fills `tpl-command-row` with `{{CR_*}}` placeholders including:
   - `{{CR_IS_FAILED}}` — `"1"` or `"0"` (drives the per-node Failed Only filter)
   - `{{CR_KEY}}` — unique row key for JS store lookup
   - Output cell: `<button onclick="loadOutput(this,'KEY')">` + empty `<div>` (no inline output)
5. Validation detail uses `tpl-val-detail-static` (success/skipped — text inline) or
   `tpl-val-detail-expandable` (failed/warning — lazy load button with empty div).

**`esc(String s)`**
HTML-escapes `&`, `<`, `>`, `"`.

**`nodeId(String nodeName)`**
Replaces non-`[a-zA-Z0-9_-]` characters with `_`.

**`toJsObject / jsonString`** (private)
Serialises the output/val-conclusion maps to compact JSON without adding a new dependency.
Handles `"`, `\`, `\n`, `\r`, `\t`, and control characters.

### 5.3a SummaryHtmlFragmentBuilder

Used for the **summary report** (all nodes).

```java
package com.nokia.report.builder;

public class SummaryHtmlFragmentBuilder {
    public SummaryHtmlFragmentBuilder(TemplateEngine engine);
    public String buildSummaryPhaseHeaders(List<NodeExecutionData> nodes);
    public String buildSummaryTableRows(List<NodeExecutionData> nodes);
}
```

The summary template no longer uses sub-templates; HTML is generated directly in Java.

**`buildSummaryPhaseHeaders`**
Calls `collectPhases(nodes)` to determine visible columns, then emits one
`<th class="col-phase">` element per phase for `{{SUMMARY_PHASE_HEADERS}}`.

**`buildSummaryTableRows`**
For each node emits one `<tr>`. Each row has: node name cell, one cell per phase (PASSED /
FAILED badge, or **SKIPPED** grey badge if the node has no commands in that phase), and an
Overall status cell. The row class is `row-failed` when the node failed.
Uses `PhaseStats.from(node)` to build a phase→PhaseStats lookup map per node.

**`buildNodePhaseSummaryRows(NodeExecutionData node)`**
Used by the **single-node detail report**. Iterates `PhaseStats.from(node)` and emits one
`<tr>` per phase with columns: Phase | Total | Success | Failed | Status (PASSED/FAILED badge).
Failed phase rows get the `phase-failed` CSS class (red background).
Fills the `{{PHASE_SUMMARY_ROWS}}` placeholder in the single-node template.

**`collectPhases(nodes)`** (private)
- Collects all phases that appear in at least one node's data.
- **Phases absent from all nodes are excluded entirely** — they are not added as columns.
- Sorts the result: known phases first in `PHASE_ORDER`, then any unrecognised phases in
  first-seen order.

**Phase order constant** (all three of `HtmlFragmentBuilder`, `SummaryHtmlFragmentBuilder`,
`PhaseStats` — must be kept in sync):
```java
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
```

### 5.4 ReportWriter

```java
package com.nokia.report.writer;

public class ReportWriter {
    public void write(String html, String outputPath) throws IOException;
}
```

Writes the HTML string as UTF-8 to the given path. Creates parent directories if they do not
exist. Overwrites any existing file at that path.

---

## 6. Data Model

### NodeExecutionJson (JSON root)

```
NodeExecutionJson
├── metadata: ExecutionMetadata
│   └── nodeName: String           (from metadata.nodeName)
└── data: LinkedHashMap<String, CommandResultDetail>
    └── key = command string
        └── CommandResultDetail
            ├── phase:                     String
            ├── target:                    String
            ├── description:               String
            ├── success:                   boolean
            ├── reason:                    String
            ├── failure:                   String
            ├── validate:                  boolean
            ├── validation_criteria:       String
            ├── validation_status:         String
            └── validation_conclusion:     String
            └── output:                    String
```

`@JsonIgnoreProperties(ignoreUnknown = true)` is applied to both `NodeExecutionJson` and
`CommandResultDetail`, so additional fields in the JSON file are silently ignored.

### NodeExecutionData (internal model)

`NodeJsonReader` converts `NodeExecutionJson` into `NodeExecutionData`:

```java
public class NodeExecutionData {
    private final String nodeName;
    private final LinkedHashMap<String, CommandResultDetail> commands;
}
```

Insertion order of `commands` is preserved from the JSON file.

### ExecutionStats (computed)

Built on demand by `ExecutionStats.from(NodeExecutionData)`:

| Field | Computed as |
|---|---|
| `total` | Number of entries in `commands` |
| `success` | Commands where `success == true` |
| `failed` | Commands where `success == false` |
| `valEnabled` | Commands where `validate == true` |
| `valPass` | Commands where `validation_status == "success"` (case-insensitive) |
| `valFail` | Commands where `validation_status == "failed"` |
| `valWarn` | Commands where `validation_status == "warning"` |
| `infoOnly` | Commands where `validation_status == "skipped"` |
| `overallStatus` | `"FAILED"` if `failed > 0`, else `"SUCCESS"` |

### PhaseStats (computed)

Built on demand by `PhaseStats.from(NodeExecutionData)`. Returns a `List<PhaseStats>` sorted
by the standard `PHASE_ORDER`; unrecognised phases are appended at the end.

| Field | Computed as |
|---|---|
| `phase` | Phase name (normalised; blank → `"OTHER"`) |
| `total` | Commands in that phase |
| `success` | Commands in that phase where `success == true` |
| `failed` | Commands in that phase where `success == false` |
| `isSuccess()` | `true` if `failed == 0` |

Used by `SummaryHtmlFragmentBuilder` to build per-phase table rows in the summary report.

---

## 7. HTML Template System

Three template files are used:

| File | Purpose |
|---|---|
| `mop_execution_report_template.html` | Multi-node detail report — command table + node search/pagination |
| `mop_execution_single_node_report_template.html` | Single-node detail report — phase summary table + auto-expanded node panel |
| `mop_execution_summary_report_template.html` | Multi-node summary report — phase matrix table |

Each template file serves a dual purpose:

1. **Main template** — the outer HTML page with `{{PLACEHOLDER}}` markers.
2. **Sub-template host** — contains `<script type="text/x-html-template">` blocks that are
   extracted by `TemplateEngine` before rendering. These blocks never appear in the final output.

The single-node template includes a stub `tpl-summary-row` sub-template (empty content) to
satisfy the `HtmlFragmentBuilder` constructor, which always loads all sub-templates at
construction time. The stub is never used for rendering.

### Rendering Pipeline

```
Summary template (multi-node)
       │
       ▼
TemplateEngine.fromClasspathResource("mop_execution_summary_report_template.html")
  └── No sub-templates needed
       │
       ▼
SummaryHtmlFragmentBuilder
  ├── buildSummaryPhaseHeaders(nodes)
  └── buildSummaryTableRows(nodes)
       │
       ▼
values → summaryEngine.render() → ReportWriter.write(summaryHtml)


Multi-node detail template                Single-node detail template
       │                                         │
       ▼                                         ▼
TemplateEngine()                    TemplateEngine.fromClasspathResource(
  └── Extracts tpl-node-panel,        "mop_execution_single_node_report_template.html")
      tpl-summary-row,                  └── Extracts tpl-node-panel, tpl-summary-row (stub),
      tpl-phase-header,                     tpl-phase-header, tpl-command-row,
      tpl-command-row,                      tpl-val-detail-*
      tpl-val-detail-*                        │
       │                                      ▼
       ▼                              SummaryHtmlFragmentBuilder
HtmlFragmentBuilder                    └── buildNodePhaseSummaryRows(node) → PHASE_SUMMARY_ROWS
  ├── buildNodesSummaryRows(nodes)             │
  ├── buildAllNodeSections(nodes)     HtmlFragmentBuilder
  ├── getRawOutputsJs()     ← JS       └── buildAllNodeSections([node])
  └── getValConclusionsJs() ← JS           └── getRawOutputsJs() / getValConclusionsJs()
       │                                        │
       ▼                                        ▼
values → detailEngine.render()         values → detailEngine.render()
       │                                        │
       ▼                                        ▼
ReportWriter.write(detailHtml)         ReportWriter.write(detailHtml)
```

### Placeholder Resolution

`render()` performs a sequential `String.replace()` for each entry in the values map.
Unresolved `{{PLACEHOLDER}}` markers remain in the output unchanged. There is no escaping
of literal `{{` sequences; avoid using that pattern in user-data values (all user data goes
through `HtmlFragmentBuilder.esc()` before being placed in the values map).

---

## 8. Execution Flow

```
ReportGenerator.generate(config)
│
├── 1. config.validate()
│      ├── Check required fields (nodeType, activity, crGroup,
│      │   one of jsonDir/jsonFile, outputHtmlPath, outputHtmlName)
│      ├── summaryHtmlName required only when generateSummary=true
│      ├── Apply defaults (requestId="N/A", timestamp=now)
│      ├── Auto-detect: if jsonDir path is a file → promote to jsonFile
│      └── Validate paths (jsonDir exists + is directory, or jsonFile exists;
│                          outputHtmlPath exists or is created)
│
├── 2. Load nodes
│      ├── singleNode = (jsonFile != null)
│      ├── If singleNode: NodeJsonReader.readSingle(jsonFile) → 1 node
│      └── If multi-node: NodeJsonReader.read(jsonDir, nodeNames) → N nodes
│
├── 3. Compute overall stats
│      └── Partition nodes into passed/failedNodes via ExecutionStats.from()
│
├── 4a. Summary report — all nodes (skipped if generateSummary=false or singleNode=true)
│      ├── Load summary TemplateEngine
│      │     (summaryTemplatePath if set, else classpath "mop_execution_summary_report_template.html")
│      ├── SummaryHtmlFragmentBuilder(summaryEngine)
│      ├── Build summary values map
│      │     ├── Scalar placeholders (PAGE_TITLE, META_*, node counts for all nodes)
│      │     ├── SUMMARY_PHASE_HEADERS → buildSummaryPhaseHeaders(nodes)
│      │     │     └── collectPhases(): union of phases across all nodes, sorted by PHASE_ORDER
│      │     │         Phases absent from all nodes excluded entirely.
│      │     └── SUMMARY_TABLE_ROWS → buildSummaryTableRows(nodes)
│      │           └── Per node: one <tr>; per phase: PASSED/FAILED/SKIPPED badge
│      ├── summaryEngine.render(summaryValues) → HTML string
│      └── ReportWriter.write(summaryHtml, getSummaryHtml())
│
├── 4b. Detail report
│      ├── detailNodes = singleNode ? nodes : failedNodes
│      ├── Skipped if detailNodes is empty (multi-node, all passed)
│      ├── Load TemplateEngine:
│      │     singleNode → templatePath if set, else classpath
│      │     │            "mop_execution_single_node_report_template.html"
│      │     multi-node → templatePath if set, else classpath
│      │                  "mop_execution_report_template.html"
│      ├── HtmlFragmentBuilder(detailEngine)
│      ├── Build detail values map
│      │     ├── If singleNode:
│      │     │     PHASE_SUMMARY_ROWS → SummaryHtmlFragmentBuilder
│      │     │                          .buildNodePhaseSummaryRows(detailNodes[0])
│      │     │                          (Phase|Total|Success|Failed|Status per phase)
│      │     ├── If multi-node:
│      │     │     STAT_LABEL_TOTAL="Total Nodes", STAT_LABEL_PASSED="Nodes Passed" (0),
│      │     │     STAT_LABEL_FAILED="Nodes Failed", counts = detailNodes.size()
│      │     │     NODES_SUMMARY_ROWS → buildNodesSummaryRows(detailNodes)
│      │     ├── NODE_DETAIL_SECTIONS → buildAllNodeSections(detailNodes)
│      │     │     └── Per node: buildNodePanel()
│      │     │           └── buildCommandTableRows()
│      │     │                 ├── Group by phase; sort by PHASE_ORDER
│      │     │                 ├── Assign unique key per command row
│      │     │                 ├── Store raw output in rawOutputStore[key]
│      │     │                 ├── Store val conclusion in valConclusionStore[key]
│      │     │                 ├── Emit data-failed="0|1" on each row
│      │     │                 └── Fill tpl-phase-header + tpl-command-row (no inline output)
│      │     ├── RAW_OUTPUTS_JS → getRawOutputsJs()     (compact JSON object)
│      │     └── VAL_CONCLUSIONS_JS → getValConclusionsJs() (compact JSON object)
│      ├── detailEngine.render(detailValues) → HTML string
│      └── ReportWriter.write(detailHtml, getOutputHtml())
│
└── 5. Return ReportGeneratorResult.success(summaryFilename|null, detailFilename|null)
```

Any exception in steps 2–4 is wrapped in a `ReportGeneratorException` and re-thrown.

---

## 9. CLI Entry Point

`ReportGeneratorMain.main(String[] args)` is the only entry point for CLI use.

**Responsibilities:**
- Parse `String[] args` into a `ReportConfig` using a positional switch loop.
- Delegate to `new ReportGenerator().generate(config)`.
- Print `STATUS=`, `ERRORS=`, and parameter key=value lines to stdout.
- Call `System.exit(0)` on success, `System.exit(1)` on failure.

The CLI does **not** call `config.validate()` directly — validation is performed inside
`ReportGenerator.generate()`. Any `ReportGeneratorException` or unexpected exception is caught,
wrapped in a `ReportGeneratorResult.failure(message)`, and printed.

**Supported flags:**
`--node-type`, `--activity`, `--cr-group`, `--json-dir`, `--json-file`, `--output-html-path`,
`--output-html-name`, `--output-summary-name`, `--generate-summary`, `--request-id`,
`--timestamp`, `--node-names`, `--template`, `--summary-template`.

`--generate-summary false` sets `config.setGenerateSummary(false)`.
`--json-file <path>` sets `config.setJsonFile(path)`.

**Arg parser note:** The loop runs `for (i = 0; i < args.length - 1; i++)`, meaning the last
token is skipped if it stands alone (i.e. is not the value following a recognised flag). This is
a guard against bare trailing tokens and does not affect correct usage where every flag is
followed by a value.

---

## 10. Dependencies

| Dependency | Version | Scope | Purpose |
|---|---|---|---|
| `jackson-databind` | 2.15.4 | compile | JSON deserialisation of per-node execution files |
| `jackson-annotations` | 2.15.4 | compile | `@JsonProperty`, `@JsonIgnoreProperties` |
| `jackson-core` | 2.15.4 | compile | Jackson streaming core (transitive) |
| `slf4j-api` | 1.7.36 | compile | Logging facade |
| `logback-classic` | 1.2.13 | compile | Logging implementation (bundled in fat JAR) |
| `junit` | 4.13.2 | test | Unit testing |

---

## 11. Extension Points

### Custom HTML Template

Pass an external template file via `ReportConfig.builder().templatePath(...)`.
The template path is used for **both** single-node and multi-node detail reports when set,
so ensure it contains the appropriate `{{PLACEHOLDER}}` markers for the target mode.

Any custom detail template must include all required sub-template blocks consumed by
`HtmlFragmentBuilder`:
- `tpl-node-panel`, `tpl-summary-row` (stub is fine for single-node), `tpl-phase-header`
- `tpl-command-row`, `tpl-val-detail-static`, `tpl-val-detail-expandable`

See the [User Manual — section 11](USER_MANUAL.md#11-custom-html-template) for the full
placeholder reference.

### Adding New Placeholders

1. Add a new `{{MY_PLACEHOLDER}}` to the HTML template.
2. In `ReportGenerator.doGenerate()`, add an entry to the `values` map:
   ```java
   values.put("MY_PLACEHOLDER", someComputedValue);
   ```

### Changing Phase Order

The `PHASE_ORDER` constant is duplicated in three classes:
- `HtmlFragmentBuilder` (detail report command grouping)
- `SummaryHtmlFragmentBuilder` (summary report column ordering)
- `PhaseStats` (per-phase stats computation)

Edit all three to keep them in sync. Phases not in the list are still displayed — they are
appended after all known phases in first-seen order.

### Replacing the JSON Reader

`NodeJsonReader` is instantiated directly inside `ReportGenerator`. To substitute a different
reader (e.g. reading from a database or remote store), extract an interface from
`NodeJsonReader` and inject it via a constructor parameter on `ReportGenerator`.

### Replacing the Template Engine

`TemplateEngine` is instantiated inside `ReportGenerator.doGenerate()`. The same injection
pattern applies if a different templating strategy is needed.
