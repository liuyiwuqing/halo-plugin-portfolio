package site.muyin.portfolio.router;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.data.domain.Sort;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriUtils;

/** Normalized, bounded parameters for the default public page. */
record PortfolioPageQuery(int page, int size, Map<String, String> filters) {

    static PortfolioPageQuery from(MultiValueMap<String, String> params, Integer configuredSize) {
        var fallbackSize = configuredSize == null || configuredSize < 1 ? 12 : Math.min(configuredSize, 100);
        var size = Math.min(positiveInt(params.getFirst("size"), fallbackSize), 100);
        var page = Math.min(positiveInt(params.getFirst("page"), 1), Integer.MAX_VALUE / size);
        var filters = new LinkedHashMap<String, String>();
        for (var key : new String[]{"keyword", "platform", "type", "tag"}) {
            var value = params.getFirst(key);
            filters.put(key, StringUtils.hasText(value) ? value.trim() : "");
        }
        var featured = params.getFirst("featured");
        filters.put("featured", "true".equalsIgnoreCase(featured) ? "true"
            : "false".equalsIgnoreCase(featured) ? "false" : "");
        var sort = params.getFirst("sort");
        filters.put("sort", "recent".equals(sort) || "updated".equals(sort) ? sort : "priority");
        return new PortfolioPageQuery(page, size, filters);
    }

    Sort sort() {
        var order = switch (filters.get("sort")) {
            case "recent" -> Sort.by(Sort.Order.desc("metadata.creationTimestamp"));
            case "updated" -> Sort.by(Sort.Order.desc("updateTime"));
            default -> Sort.by(Sort.Order.desc("priority"), Sort.Order.desc("metadata.creationTimestamp"));
        };
        // A unique final key prevents equal-priority projects moving between pages.
        return order.and(Sort.by(Sort.Order.asc("metadata.name")));
    }

    boolean filtered() {
        return filters.entrySet().stream()
            .anyMatch(entry -> !"sort".equals(entry.getKey()) && StringUtils.hasText(entry.getValue()));
    }

    String pageUrl(int targetPage) {
        var url = new StringBuilder("/portfolio?page=").append(targetPage).append("&size=").append(size);
        filters.forEach((key, value) -> {
            if (StringUtils.hasText(value)) {
                url.append('&').append(key).append('=')
                    .append(UriUtils.encode(value, StandardCharsets.UTF_8));
            }
        });
        return url.toString();
    }

    private static int positiveInt(String value, int fallback) {
        try {
            var parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }
}
