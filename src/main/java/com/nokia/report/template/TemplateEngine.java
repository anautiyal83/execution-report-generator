package com.nokia.report.template;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimal template engine that resolves {{PLACEHOLDER}} markers in an HTML template.
 *
 * The template is loaded from an external file path when provided, otherwise falls
 * back to the built-in classpath resource: mop_execution_report_template.html
 *
 * Sub-templates are defined in the HTML file as:
 *   <script type="text/x-html-template" id="some-id">...HTML...</script>
 * They are extracted before rendering and removed from the final output.
 * Use getSubTemplate(id) to retrieve them for use in Java fragment builders.
 */
public class TemplateEngine {

    private static final Logger log = LoggerFactory.getLogger(TemplateEngine.class);
    private static final String TEMPLATE_RESOURCE = "/mop_execution_report_template.html";

    private static final Pattern SUB_TPL_PATTERN = Pattern.compile(
            "<script\\s+type=\"text/x-html-template\"\\s+id=\"([^\"]+)\">(.*?)</script>",
            Pattern.DOTALL);

    private final String templateContent;
    private final Map<String, String> subTemplates = new LinkedHashMap<>();

    /** Load template from the built-in classpath resource. */
    public TemplateEngine() throws IOException {
        this.templateContent = process(loadFromClasspath());
    }

    /** Load template from an external file path. */
    public TemplateEngine(String templatePath) throws IOException {
        this.templateContent = process(loadFromFile(templatePath));
    }

    /**
     * Resolve all {{PLACEHOLDER}} markers in the template and return the final HTML string.
     *
     * @param values map of placeholder name (without braces) → replacement HTML
     */
    public String render(Map<String, String> values) {
        String html = templateContent;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            String placeholder = "{{" + entry.getKey() + "}}";
            html = html.replace(placeholder, entry.getValue() != null ? entry.getValue() : "");
        }
        return html;
    }

    /**
     * Return a sub-template by its id attribute.
     * Throws if the id was not found in the template file.
     */
    public String getSubTemplate(String id) {
        String tpl = subTemplates.get(id);
        if (tpl == null) {
            throw new IllegalArgumentException("Sub-template not found in HTML template: " + id);
        }
        return tpl;
    }

    // -------------------------------------------------------------------------
    // Internal
    // -------------------------------------------------------------------------

    /**
     * Extract all sub-templates from the raw HTML, store them in the map,
     * and return the main template with those blocks removed.
     */
    private String process(String raw) {
        Matcher m = SUB_TPL_PATTERN.matcher(raw);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String id      = m.group(1);
            String content = m.group(2);
            // Trim a single leading/trailing newline that surrounds the block content
            content = content.replaceAll("^\\r?\\n", "").replaceAll("\\r?\\n$", "");
            subTemplates.put(id, content);
            m.appendReplacement(sb, "");
            log.debug("Extracted sub-template '{}' ({} chars)", id, content.length());
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private String loadFromFile(String templatePath) throws IOException {
        File file = new File(templatePath);
        if (!file.exists()) {
            throw new IOException("Invalid path: --template '" + templatePath + "' does not exist");
        }
        if (!file.isFile()) {
            throw new IOException("Invalid path: --template '" + templatePath + "' is not a file");
        }
        InputStream is = new FileInputStream(file);
        try {
            byte[] bytes = readAllBytes(is);
            log.debug("Loaded template from file '{}' ({} bytes)", templatePath, bytes.length);
            return new String(bytes, StandardCharsets.UTF_8);
        } finally {
            is.close();
        }
    }

    private String loadFromClasspath() throws IOException {
        InputStream is = TemplateEngine.class.getResourceAsStream(TEMPLATE_RESOURCE);
        if (is == null) {
            throw new IOException("HTML template not found on classpath: " + TEMPLATE_RESOURCE);
        }
        try {
            byte[] bytes = readAllBytes(is);
            log.debug("Loaded template from classpath ({} bytes)", bytes.length);
            return new String(bytes, StandardCharsets.UTF_8);
        } finally {
            is.close();
        }
    }

    private static byte[] readAllBytes(InputStream is) throws IOException {
        byte[] buffer = new byte[8192];
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        int read;
        while ((read = is.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
        return out.toByteArray();
    }
}
