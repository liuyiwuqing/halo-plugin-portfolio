package site.muyin.portfolio.content;

import java.util.Locale;
import java.util.StringJoiner;
import java.util.regex.Pattern;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.jsoup.Jsoup;
import org.jsoup.safety.Cleaner;
import org.jsoup.safety.Safelist;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProjectContentRenderer {

    private static final Pattern SAFE_DIMENSION = Pattern.compile(
        "(?:auto|0|(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)(?:px|%|em|rem|vh|vw))(?:\\s*!important)?");

    private static final Safelist CONTENT_SAFELIST = Safelist.relaxed()
        .addTags("div", "span", "del", "s", "kbd", "details", "summary", "portfolio-project-card")
        .addAttributes(":all", "style")
        .addAttributes("code", "class")
        .addAttributes("portfolio-project-card", "data-slug", "data-portfolio-project-card")
        .addAttributes("div", "data-slug", "data-portfolio-project-card")
        .removeProtocols("a", "href", "ftp")
        .addProtocols("a", "href", "#")
        .preserveRelativeLinks(true);

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
        // Parse entities and malformed HTML before checking attributes; regex checks miss encoded URLs.
        // The synthetic base only validates relative URLs; preserveRelativeLinks keeps stored links intact.
        var document = Jsoup.parseBodyFragment(html, "https://portfolio.invalid/");
        document.select("script, style, iframe, object, embed, svg, math, template, noscript, "
            + "form, input, button, select, textarea, meta, link, base").remove();
        document.select("[style]").forEach(element -> {
            var style = sanitizeStyle(element.attr("style"));
            if (style.isEmpty()) {
                element.removeAttr("style");
            } else {
                element.attr("style", style);
            }
        });
        var cleaned = new Cleaner(CONTENT_SAFELIST).clean(document);
        cleaned.outputSettings().prettyPrint(false);
        return cleaned.body().html();
    }

    private String sanitizeStyle(String style) {
        var safe = new StringJoiner(" ");
        for (var declaration : style.split(";")) {
            var separator = declaration.indexOf(':');
            if (separator < 0) {
                continue;
            }
            var property = declaration.substring(0, separator).trim().toLowerCase(Locale.ROOT);
            var value = declaration.substring(separator + 1).trim().toLowerCase(Locale.ROOT);
            var allowed = switch (property) {
                case "height", "width", "min-height", "min-width", "max-height", "max-width" ->
                    SAFE_DIMENSION.matcher(value).matches();
                case "object-fit" -> value.matches("(?:contain|cover|fill|none|scale-down)");
                case "text-align" -> value.matches("(?:left|right|center|justify|start|end)");
                default -> false;
            };
            if (allowed) {
                safe.add(property + ": " + value + ";");
            }
        }
        return safe.toString();
    }
}
