package site.muyin.portfolio.content;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import site.muyin.portfolio.scheme.Project;
import site.muyin.portfolio.service.ProjectService;

@Component
@RequiredArgsConstructor
public class ProjectCardRenderer {

    private static final Pattern PROJECT_CARD_PATTERN = Pattern.compile(
        "(?is)<portfolio-project-card\\b([^>]*)>(?:\\s*</portfolio-project-card>)?"
            + "|<div\\b(?=[^>]*\\bdata-portfolio-project-card\\b)([^>]*)>\\s*</div>");

    private static final Pattern SLUG_ATTRIBUTE_PATTERN = Pattern.compile(
        "(?is)\\bdata-slug\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");

    private static final String CARD_STYLE = "display:block;margin:22px 0;border:1px solid rgba(148,163,184,.22);"
        + "border-color:color-mix(in srgb,currentColor 16%,transparent);border-radius:8px;"
        + "background:rgba(148,163,184,.08);background:color-mix(in srgb,currentColor 7%,transparent);"
        + "box-shadow:0 18px 42px -34px rgba(15,23,42,.68);overflow:hidden;color:inherit;";

    private static final String CARD_LINK_STYLE = "display:grid;grid-template-columns:minmax(150px,32%) minmax(0,1fr);"
        + "align-items:stretch;gap:0;color:inherit;text-decoration:none;";

    private static final String COVER_STYLE = "display:block;position:relative;overflow:hidden;min-height:136px;"
        + "aspect-ratio:16/10;background:rgba(148,163,184,.14);"
        + "background:color-mix(in srgb,currentColor 10%,transparent);";

    private static final String COVER_PLACEHOLDER_STYLE = COVER_STYLE + "display:flex;align-items:center;justify-content:center;";

    private static final String COVER_MARK_STYLE = "display:flex;width:48px;height:48px;align-items:center;justify-content:center;"
        + "border:1px solid rgba(148,163,184,.28);border-color:color-mix(in srgb,currentColor 20%,transparent);"
        + "border-radius:8px;background:rgba(148,163,184,.12);"
        + "background:color-mix(in srgb,currentColor 8%,transparent);font-size:18px;font-weight:800;opacity:.72;";

    private static final String CARD_BODY_STYLE = "display:flex;min-width:0;flex-direction:column;justify-content:center;"
        + "gap:8px;padding:16px 18px;";

    private static final String CARD_EYEBROW_STYLE = "display:block;color:inherit;font-size:12px;font-weight:700;"
        + "letter-spacing:.04em;line-height:1.2;opacity:.58;";

    private static final String CARD_TITLE_STYLE = "display:block;color:inherit;font-size:18px;line-height:1.35;"
        + "font-weight:750;letter-spacing:0;text-wrap:balance;";

    private static final String CARD_SUMMARY_STYLE = "display:-webkit-box;-webkit-line-clamp:2;-webkit-box-orient:vertical;"
        + "overflow:hidden;color:inherit;font-size:14px;line-height:1.7;opacity:.72;";

    private static final String CARD_META_STYLE = "display:flex;flex-wrap:wrap;gap:6px;margin-top:2px;";

    private static final String CARD_CHIP_STYLE = "display:inline-flex;align-items:center;border:1px solid rgba(148,163,184,.22);"
        + "border-color:color-mix(in srgb,currentColor 18%,transparent);border-radius:6px;padding:3px 7px;"
        + "color:inherit;background:rgba(148,163,184,.08);background:color-mix(in srgb,currentColor 6%,transparent);"
        + "font-size:12px;font-weight:600;line-height:1.35;opacity:.82;";

    private static final String WARNING_CARD_STYLE = "display:block;margin:22px 0;padding:14px 16px;"
        + "border:1px dashed rgba(245,158,11,.48);border-radius:8px;background:rgba(245,158,11,.12);"
        + "color:inherit;";

    private final ProjectService projectService;

    public Mono<String> render(String html) {
        if (html == null) {
            return Mono.just("");
        }

        var scan = scan(html);
        if (!scan.hasProjectCard()) {
            return Mono.just(html);
        }

        return Flux.fromIterable(scan.slugs())
            .flatMapSequential(slug -> projectService.getPublishedBySlug(slug)
                .map(project -> Map.entry(slug, renderProjectCard(project)))
                .defaultIfEmpty(Map.entry(slug, renderFallbackCard(slug))))
            .collectMap(Map.Entry::getKey, Map.Entry::getValue)
            .map(cards -> replaceProjectCards(html, cards));
    }

    private ProjectCardScan scan(String html) {
        var matcher = PROJECT_CARD_PATTERN.matcher(html);
        var slugs = new LinkedHashSet<String>();
        var hasProjectCard = false;
        while (matcher.find()) {
            hasProjectCard = true;
            var slug = extractSlug(attributes(matcher));
            if (StringUtils.hasText(slug)) {
                slugs.add(slug);
            }
        }
        return new ProjectCardScan(hasProjectCard, slugs);
    }

    private String replaceProjectCards(String html, Map<String, String> renderedCards) {
        var matcher = PROJECT_CARD_PATTERN.matcher(html);
        var result = new StringBuffer();
        while (matcher.find()) {
            var slug = extractSlug(attributes(matcher));
            var replacement = StringUtils.hasText(slug)
                ? renderedCards.getOrDefault(slug, renderFallbackCard(slug))
                : renderUnconfiguredCard();
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    private static String attributes(Matcher matcher) {
        return StringUtils.hasText(matcher.group(1)) ? matcher.group(1) : matcher.group(2);
    }

    private static String extractSlug(String attributes) {
        if (!StringUtils.hasText(attributes)) {
            return "";
        }
        var matcher = SLUG_ATTRIBUTE_PATTERN.matcher(attributes);
        if (!matcher.find()) {
            return "";
        }
        for (var i = 1; i <= 3; i++) {
            var value = matcher.group(i);
            if (StringUtils.hasText(value)) {
                return HtmlUtils.htmlUnescape(value).trim();
            }
        }
        return "";
    }

    private static String renderProjectCard(Project project) {
        var slug = textOrDefault(project.getSlug(), "project");
        var title = textOrDefault(project.getTitle(), slug);
        var summary = textOrDefault(project.getSummary(), "暂无项目简介");
        var cover = safeUrl(project.getCover());
        var href = "/projects/" + UriUtils.encodePathSegment(slug, StandardCharsets.UTF_8);
        var meta = projectMeta(project);

        return """
            <article class="portfolio-project-card" data-portfolio-rendered-project-card data-slug="%s" style="%s">
              <a class="portfolio-project-card__link" href="%s" style="%s">
                %s
                <span class="portfolio-project-card__body" style="%s">
                  <span class="portfolio-project-card__eyebrow" style="%s">作品集项目</span>
                  <strong class="portfolio-project-card__title" style="%s">%s</strong>
                  <span class="portfolio-project-card__summary" style="%s">%s</span>
                  %s
                </span>
              </a>
            </article>
            """.formatted(
            escapeAttr(slug),
            CARD_STYLE,
            escapeAttr(href),
            CARD_LINK_STYLE,
            renderCover(cover, title),
            CARD_BODY_STYLE,
            CARD_EYEBROW_STYLE,
            CARD_TITLE_STYLE,
            escapeText(title),
            CARD_SUMMARY_STYLE,
            escapeText(summary),
            meta
        );
    }

    private static String renderCover(String cover, String title) {
        if (StringUtils.hasText(cover)) {
            return """
                <span class="portfolio-project-card__cover portfolio-project-card__cover--image" role="img" aria-label="%s" style="%s"></span>
                """.formatted(
                escapeAttr(title),
                COVER_STYLE
                    + "background-image:linear-gradient(135deg,rgba(15,23,42,.18),rgba(15,23,42,0) 46%),url('"
                    + cssUrl(cover)
                    + "');background-size:cover;background-position:center;"
            );
        }
        return """
            <span class="portfolio-project-card__cover portfolio-project-card__cover--placeholder" aria-hidden="true" style="%s">
              <span style="%s">P</span>
            </span>
            """.formatted(
            COVER_PLACEHOLDER_STYLE,
            COVER_MARK_STYLE
        );
    }

    private static String projectMeta(Project project) {
        var items = new ArrayList<String>();
        addIfPresent(items, project.getPlatform());
        addIfPresent(items, project.getType());
        if (project.getTechStacks() != null) {
            project.getTechStacks().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .limit(3)
                .forEach(items::add);
        }
        if (items.isEmpty()) {
            return "";
        }
        var chips = items.stream()
            .map(item -> "<span class=\"portfolio-project-card__chip\" style=\"" + CARD_CHIP_STYLE + "\">"
                + escapeText(item) + "</span>")
            .toList();
        return "<span class=\"portfolio-project-card__meta\" style=\"" + CARD_META_STYLE + "\">"
            + String.join("", chips)
            + "</span>";
    }

    private static void addIfPresent(List<String> items, String value) {
        if (StringUtils.hasText(value)) {
            items.add(value.trim());
        }
    }

    private static String renderFallbackCard(String slug) {
        var safeSlug = textOrDefault(slug, "未选择项目");
        return """
            <article class="portfolio-project-card portfolio-project-card--warning" data-portfolio-rendered-project-card data-slug="%s" style="%s">
              <strong style="display:block;color:#d97706;font-size:15px;line-height:1.6;">项目卡片不可用</strong>
              <span style="display:block;color:inherit;font-size:14px;line-height:1.7;opacity:.78;">项目不存在或未发布：%s</span>
            </article>
            """.formatted(
            escapeAttr(safeSlug),
            WARNING_CARD_STYLE,
            escapeText(safeSlug)
        );
    }

    private static String renderUnconfiguredCard() {
        return """
            <article class="portfolio-project-card portfolio-project-card--warning" data-portfolio-rendered-project-card style="%s">
              <strong style="display:block;color:#d97706;font-size:15px;line-height:1.6;">项目卡片未配置</strong>
              <span style="display:block;color:inherit;font-size:14px;line-height:1.7;opacity:.78;">请选择一个项目后再发布。</span>
            </article>
            """.formatted(WARNING_CARD_STYLE);
    }

    private static String safeUrl(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        var trimmed = value.trim();
        var normalized = trimmed.toLowerCase(Locale.ROOT);
        if (normalized.startsWith("http://")
            || normalized.startsWith("https://")
            || (normalized.startsWith("/") && !normalized.startsWith("//"))) {
            return trimmed;
        }
        return "";
    }

    private static String textOrDefault(String value, String fallback) {
        return StringUtils.hasText(value) ? value.trim() : fallback;
    }

    private static String escapeText(String value) {
        return HtmlUtils.htmlEscape(textOrDefault(value, ""), StandardCharsets.UTF_8.name());
    }

    private static String escapeAttr(String value) {
        return escapeText(value);
    }

    private static String cssUrl(String value) {
        var escaped = textOrDefault(value, "")
            .replace("\\", "\\\\")
            .replace("'", "\\'")
            .replace("\n", "")
            .replace("\r", "");
        return escapeAttr(escaped);
    }

    private record ProjectCardScan(boolean hasProjectCard, LinkedHashSet<String> slugs) {
    }
}
