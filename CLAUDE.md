# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Java 8 library + CLI tool that reads per-node execution JSON files (produced by network-command-executor) and generates HTML MOP execution reports. Produces up to two reports: a summary report (all nodes, phase matrix) and a detail report (failed nodes or single-node with command-level output).

## Build Commands

```bash
# Compile and package (produces thin JAR + fat CLI JAR)
mvn package

# Compile only
mvn compile

# Clean build
mvn clean package

# Run tests (JUnit 4 — no tests exist yet)
mvn test
```

**Artifacts produced by `mvn package`:**
- `target/execution-report-generator-1.0.1.jar` — thin library JAR
- `target/execution-report-generator-1.0.1-cli.jar` — fat runnable JAR (all deps shaded)

## Running the CLI

```bash
java -jar target/execution-report-generator-1.0.1-cli.jar \
    --node-type MRF --activity ANNOUNCEMENT_LOADING --cr-group GroupA \
    --json-dir /path/to/jsons --output-html-path /path/to/output \
    --output-html-name report.html --output-summary-name summary.html
```

## Architecture

Four-layer pipeline: **Config → Read → Render → Write**

1. **API layer** — `ReportGenerator` is the single entry point. Takes a `ReportConfig` (built via fluent builder or setters), validates it, orchestrates the pipeline, returns `ReportGeneratorResult`.
2. **Reading layer** — `NodeJsonReader` deserializes per-node JSON files into `NodeExecutionData` using Jackson. Supports single-file mode (`--json-file`) and directory mode (`--json-dir`).
3. **Rendering layer** — `TemplateEngine` loads HTML templates with `{{PLACEHOLDER}}` markers and `<script type="text/x-html-template">` sub-template blocks. `HtmlFragmentBuilder` builds detail report HTML; `SummaryHtmlFragmentBuilder` builds summary report HTML.
4. **Writing layer** — `ReportWriter` writes rendered HTML to disk.

**Two report modes:**
- **Multi-node** (via `--json-dir`): Summary report for all nodes + detail report for failed nodes only. Detail report skipped if all nodes pass.
- **Single-node** (via `--json-file`): Detail report only with phase summary table. Uses a separate template (`mop_execution_single_node_report_template.html`).

## Key Conventions

- **Java 8** target — no streams, lambdas only where already used, no `var`, no `List.of()`.
- **PHASE_ORDER** is duplicated in three classes (`HtmlFragmentBuilder`, `SummaryHtmlFragmentBuilder`, `PhaseStats`) — all three must stay in sync when phases change.
- Large command outputs are stored in JS data stores (`rawOutputStore`/`valConclusionStore`) rather than inline HTML, loaded lazily on click.
- HTML templates live in `src/main/resources/` — three templates for three report variants.
- `@JsonIgnoreProperties(ignoreUnknown = true)` on model classes — extra JSON fields are silently ignored.
- `HtmlFragmentBuilder.esc()` is used for all user-supplied values injected into HTML.

## Package Structure

```
com.nokia.report
├── ReportGenerator              — Public API entry point
├── ReportGeneratorMain          — CLI entry point (main class)
├── ReportGeneratorException     — Checked exception for all failures
├── config/ReportConfig          — Config + Builder + validate()
├── model/                       — Data model (NodeExecutionJson, CommandResultDetail, ExecutionStats, PhaseStats, etc.)
├── reader/NodeJsonReader        — JSON file loading
├── template/TemplateEngine      — HTML template loading + placeholder rendering
├── builder/                     — HtmlFragmentBuilder (detail), SummaryHtmlFragmentBuilder (summary)
└── writer/ReportWriter          — File output
```
