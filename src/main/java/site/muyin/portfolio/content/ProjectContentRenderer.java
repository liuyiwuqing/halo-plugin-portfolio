package site.muyin.portfolio.content;

import java.util.Locale;
import java.util.regex.Pattern;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProjectContentRenderer {

    private static final Pattern DANGEROUS_CONTAINER_TAGS = Pattern.compile(
        "(?is)<\\s*(script|style|iframe|object|embed|svg|math)\\b[^>]*>.*?<\\s*/\\s*\\1\\s*>");

    private static final Pattern DANGEROUS_SINGLE_TAGS = Pattern.compile(
        "(?is)<\\s*/?\\s*(script|style|iframe|object|embed|meta|link|base|form|input|button|select|textarea|svg|math)\\b[^>]*>");

    private static final Pattern EVENT_HANDLER_ATTRIBUTES = Pattern.compile(
        "(?i)\\s+on[\\w:-]+\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");

    private static final Pattern DANGEROUS_QUOTED_URL_ATTRIBUTES = Pattern.compile(
        "(?i)(\\s(?:href|src|xlink:href|action|formaction)\\s*=\\s*)([\"'])\\s*(?:javascript|vbscript|data)\\s*:[^\"']*\\2");

    private static final Pattern DANGEROUS_UNQUOTED_URL_ATTRIBUTES = Pattern.compile(
        "(?i)(\\s(?:href|src|xlink:href|action|formaction)\\s*=\\s*)(?![\"'])\\s*(?:javascript|vbscript|data)\\s*:[^\\s>]*");

    private static final Pattern STYLE_ATTRIBUTES = Pattern.compile(
        "(?i)\\s+style\\s*=\\s*(\"[^\"]*\"|'[^']*'|[^\\s>]+)");

    private final Parser parser;
    private final HtmlRenderer htmlRenderer;

    public ProjectContentRenderer() {
        this.parser = Parser.builder().build();
        this.htmlRenderer = HtmlRenderer.builder()
            .escapeHtml(false)
            .sanitizeUrls(true)
            .build();
    }

    public String renderMarkdown(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return "";
        }
        return sanitizeRenderedHtml(htmlRenderer.render(parser.parse(markdown))).trim();
    }

    private String sanitizeRenderedHtml(String html) {
        var sanitized = DANGEROUS_CONTAINER_TAGS.matcher(html).replaceAll("");
        sanitized = DANGEROUS_SINGLE_TAGS.matcher(sanitized).replaceAll("");
        sanitized = EVENT_HANDLER_ATTRIBUTES.matcher(sanitized).replaceAll("");
        sanitized = sanitizeStyleAttributes(sanitized);
        sanitized = DANGEROUS_QUOTED_URL_ATTRIBUTES.matcher(sanitized).replaceAll("$1$2$2");
        return DANGEROUS_UNQUOTED_URL_ATTRIBUTES.matcher(sanitized).replaceAll("$1\"\"");
    }

    private String sanitizeStyleAttributes(String html) {
        return STYLE_ATTRIBUTES.matcher(html).replaceAll(match -> {
            var attribute = match.group();
            var normalized = attribute.toLowerCase(Locale.ROOT);
            if (normalized.contains("javascript:")
                || normalized.contains("expression(")
                || normalized.contains("behavior:")
                || normalized.contains("-moz-binding")) {
                return "";
            }
            return attribute;
        });
    }
}
