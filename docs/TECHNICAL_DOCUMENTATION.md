# Execution Report Generator — Technical Documentation

**Version:** 1.0.0
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
  │               │  │              │  │               │
  │ Reads *.json  │  │ Loads HTML   │  │ Writes final  │
  │ from jsonDir  │  │ template;    │  │ HTML to disk  │
  │               │  │ extracts     │  └──────────────┘
  └───────┬───────┘  │ sub-templates│
          │          └──────┬───────┘
          ▼                 ▼
  List<NodeExecutionData>  nodePanelTemplate (String)
          │                 │
          └────────┬────────┘
                   ▼
         ┌──────────────────────┐
         │  HtmlFragmentBuilder │
         │                      │
         │  buildNodesSummaryRows()
         │  buildAllNodeSections()
         └──────────────────────┘
```

The design separates concerns into four layers:

1. **API layer** — `ReportGenerator` + `ReportConfig` + `ReportGeneratorResult` — the only surface callers need.
2. **Reading layer** — `NodeJsonReader` deserialises per-node JSON files into the internal model.
3. **Rendering layer** — `TemplateEngine` + `HtmlFragmentBuilder` produce the final HTML string.
4. **Writing layer** — `ReportWriter` persists the HTML to disk.

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
├── ReportGenerator              Public API — instantiate to generate a report
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
│   ├── ExecutionStats           Computed statistics for one node
│   └── ReportGeneratorResult    Generation outcome (status / errors / parameters)
│
├── reader
│   └── NodeJsonReader           Loads and filters per-node JSON files
│
├── template
│   └── TemplateEngine           Loads HTML template; resolves {{PLACEHOLDER}} markers;
│                                extracts sub-templates
├── builder
│   └── HtmlFragmentBuilder      Builds HTML strings for nodes summary rows and
│                                per-node detail panels (including phase grouping)
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
// Builder
ReportConfig config = ReportConfig.builder()
    .nodeType("MRF")
    .activity("ANNOUNCEMENT_LOADING")
    .crGroup("GroupA")
    .jsonDir("/path/to/jsons")
    .outputHtmlPath("/path/to/output")
    .outputHtmlName("report.html")
    .requestId("CR-98765")           // optional — default: "N/A"
    .timestamp("2026-04-19 14:00")   // optional — default: current time
    .nodeNames(nodeList)             // optional — default: all JSON files in jsonDir
    .templatePath("/custom.html")    // optional — default: built-in classpath template
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
| `jsonDir` | String | Yes | — |
| `outputHtmlPath` | String | Yes | — |
| `outputHtmlName` | String | Yes | — |
| `requestId` | String | No | `"N/A"` |
| `timestamp` | String | No | Current `yyyy-MM-dd HH:mm:ss` |
| `nodeNames` | `List<String>` | No | All JSON files in `jsonDir` |
| `templatePath` | String | No | Built-in classpath template |

`getOutputHtml()` is a convenience method returning `outputHtmlPath + File.separator + outputHtmlName`.

### 4.3 ReportGeneratorResult

```java
package com.nokia.report.model;

public class ReportGeneratorResult {
    public boolean isSuccess();
    public String  getStatus();          // "SUCCESS" or "FAILED"
    public int     getErrors();          // 0 on success, 1 on failure
    public LinkedHashMap<String, String> getParameters();
    // On success: parameters contains REPORT_FILENAME=<filename>
    // On failure: parameters contains ERROR=<message>
}
```

Static factory methods:
```java
ReportGeneratorResult.success("report.html");
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
    public List<NodeExecutionData> read(String jsonDir, List<String> nodeNames)
            throws IOException;
}
```

**Behaviour:**
- Lists all `*.json` files in `jsonDir`, sorted alphabetically.
- Deserialises each file into `NodeExecutionJson` using Jackson `ObjectMapper`.
- Node name is resolved from `metadata.nodeName`; if absent, falls back to filename stem
  with a warning log.
- Duplicate `metadata.nodeName` values (across files) are skipped with a warning.
- If `nodeNames` is non-null and non-empty, only nodes whose name appears in the list are
  included, in list order. A warning is logged for any requested name with no matching file.
  Throws `IOException` if none of the requested names are found.

### 5.2 TemplateEngine

```java
package com.nokia.report.template;

public class TemplateEngine {
    public TemplateEngine() throws IOException;                     // classpath template
    public TemplateEngine(String templatePath) throws IOException;  // external file
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

```java
package com.nokia.report.builder;

public class HtmlFragmentBuilder {
    public HtmlFragmentBuilder(String nodePanelTemplate);
    public String buildNodesSummaryRows(List<NodeExecutionData> nodes);
    public String buildAllNodeSections(List<NodeExecutionData> nodes);
    public static String esc(String s);
    public static String nodeId(String nodeName);
}
```

**`buildNodesSummaryRows`**
Iterates nodes, computes `ExecutionStats` per node, and emits one `<tr class="summary-row">` per
node with an `onclick="scrollToNode(...)"` handler.

**`buildAllNodeSections`**
Iterates nodes, calls `buildNodePanel(node, index)` for each. `buildNodePanel` fills the
`nodePanelTemplate` string via a chain of `String.replace()` calls on `{{NP_*}}` placeholders,
with `{{NP_COMMAND_ROWS}}` built by `buildCommandTableRows`.

**`buildCommandTableRows`**

1. Groups commands into a `Map<String, List<Entry>>` keyed by phase.
   Commands with no phase are placed in `"OTHER"`.
2. Sorts phases by `PHASE_ORDER`; unrecognised phases are appended after.
3. For each phase emits a `<tr class="phase-header-row" onclick="togglePhase(this)">` spanning
   all 9 columns, followed by one `<tr class="row-data" data-phase="...">` per command.
4. The `data-phase` attribute is used by the browser-side XLSX export to populate the Phase
   column without requiring a visible Phase table column.

**`esc(String s)`**
HTML-escapes `&`, `<`, `>`, `"`. Used on all user-data values injected into HTML.

**`nodeId(String nodeName)`**
Replaces all characters outside `[a-zA-Z0-9_-]` with `_` to produce a safe HTML `id` attribute.

**Phase order constant:**
```java
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

---

## 7. HTML Template System

The template file (`mop_execution_report_template.html`) serves a dual purpose:

1. **Main template** — the outer HTML page with `{{PLACEHOLDER}}` markers.
2. **Sub-template host** — contains `<script type="text/x-html-template">` blocks that are
   extracted by `TemplateEngine` before rendering. These blocks never appear in the final output.

### Rendering Pipeline

```
Template file (HTML)
       │
       ▼
TemplateEngine.process()
  ├── Extracts <script type="text/x-html-template"> blocks → subTemplates map
  └── Returns main template string (blocks removed)
       │
       ▼
ReportGenerator.doGenerate()
  ├── Retrieves "tpl-node-panel" sub-template
  ├── Builds values map (placeholders → replacement strings)
  │     ├── Scalar values: PAGE_TITLE, META_*, TOTAL_NODES, etc.
  │     ├── NODES_SUMMARY_ROWS: HtmlFragmentBuilder.buildNodesSummaryRows()
  │     └── NODE_DETAIL_SECTIONS: HtmlFragmentBuilder.buildAllNodeSections()
  └── Calls TemplateEngine.render(values)
       │
       ▼
Final HTML string → ReportWriter.write()
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
│      ├── Check required fields (nodeType, activity, crGroup, jsonDir,
│      │   outputHtmlPath, outputHtmlName)
│      ├── Apply defaults (requestId="N/A", timestamp=now)
│      └── Validate paths (jsonDir exists + is directory;
│                          outputHtmlPath exists or is created)
│
├── 2. NodeJsonReader.read(jsonDir, nodeNames)
│      ├── List *.json files, sort alphabetically
│      ├── Parse each file → NodeExecutionJson (Jackson)
│      ├── Resolve node name (metadata.nodeName or filename stem)
│      └── Filter/order by nodeNames if provided
│
├── 3. Compute overall stats
│      └── Count passed/failed nodes via ExecutionStats.from()
│
├── 4. TemplateEngine(templatePath or classpath)
│      ├── Load HTML template
│      └── Extract sub-templates → subTemplates map
│
├── 5. Build HTML values map
│      ├── Scalar placeholders (PAGE_TITLE, META_*, node counts)
│      ├── NODES_SUMMARY_ROWS → HtmlFragmentBuilder.buildNodesSummaryRows()
│      └── NODE_DETAIL_SECTIONS → HtmlFragmentBuilder.buildAllNodeSections()
│             └── Per node: buildNodePanel()
│                   └── buildCommandTableRows()
│                         ├── Group commands by phase
│                         ├── Sort phases by PHASE_ORDER
│                         └── Emit phase-header-row + row-data rows
│
├── 6. TemplateEngine.render(values) → HTML string
│
├── 7. ReportWriter.write(html, outputPath)
│
└── 8. Return ReportGeneratorResult.success(filename)
```

Any exception in steps 2–7 is wrapped in a `ReportGeneratorException` and re-thrown.

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
The template must include all required `{{PLACEHOLDER}}` markers and the
`<script type="text/x-html-template" id="tpl-node-panel">` sub-template block.
See the [User Manual — section 11](USER_MANUAL.md#11-custom-html-template) for the full
placeholder reference.

### Adding New Placeholders

1. Add a new `{{MY_PLACEHOLDER}}` to the HTML template.
2. In `ReportGenerator.doGenerate()`, add an entry to the `values` map:
   ```java
   values.put("MY_PLACEHOLDER", someComputedValue);
   ```

### Changing Phase Order

Edit `HtmlFragmentBuilder.PHASE_ORDER`. Add, remove, or reorder entries. Phases not in the
list are still displayed — they are appended after all known phases in first-seen order.

### Replacing the JSON Reader

`NodeJsonReader` is instantiated directly inside `ReportGenerator`. To substitute a different
reader (e.g. reading from a database or remote store), extract an interface from
`NodeJsonReader` and inject it via a constructor parameter on `ReportGenerator`.

### Replacing the Template Engine

`TemplateEngine` is instantiated inside `ReportGenerator.doGenerate()`. The same injection
pattern applies if a different templating strategy is needed.
