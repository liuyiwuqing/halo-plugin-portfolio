package site.muyin.portfolio.repository;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.util.DigestUtils;
import org.springframework.web.server.ResponseStatusException;
import site.muyin.portfolio.service.impl.ProjectServiceImpl;

/** 仅提取仓库身份；请求目标始终由固定官方 API 地址构建。 */
public record RepositoryAddress(String provider, String owner, String name) {

    public static RepositoryAddress parse(String value) {
        try {
            var uri = URI.create(value == null ? "" : value.trim());
            var host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getRawUserInfo() != null
                || uri.getPort() != -1 || uri.getRawQuery() != null || uri.getRawFragment() != null
                || !(host.equals("github.com") || host.equals("gitee.com"))) {
                throw new IllegalArgumentException();
            }
            var path = uri.getRawPath().replaceFirst("/$", "");
            var parts = path.split("/", -1);
            if (parts.length != 3 || !parts[0].isEmpty()) {
                throw new IllegalArgumentException();
            }
            var owner = parts[1];
            var name = parts[2].replaceFirst("\\.git$", "");
            if (!validSegment(owner) || !validSegment(name)) {
                throw new IllegalArgumentException();
            }
            return new RepositoryAddress(host.equals("github.com") ? "github" : "gitee", owner, name);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "请输入完整的公开仓库地址：https://github.com/owner/repo 或 https://gitee.com/owner/repo");
        }
    }

    public URI apiUri() {
        var base = provider.equals("github") ? "https://api.github.com/repos/" : "https://gitee.com/api/v5/repos/";
        return URI.create(base + owner + "/" + name);
    }

    public URI readmeUri() {
        return URI.create(apiUri() + "/readme");
    }

    public String url() {
        return "https://" + provider + ".com/" + owner + "/" + name;
    }

    public String suggestedSlug() {
        var identity = provider + "-" + owner.toLowerCase(Locale.ROOT) + "-" + name.toLowerCase(Locale.ROOT);
        var slug = ProjectServiceImpl.normalizeSlug(identity, null);
        // 哈希保留点号、下划线和分隔边界，避免不同仓库归一化后产生同一个 slug。
        var hash = DigestUtils.md5DigestAsHex((provider + "/" + owner.toLowerCase(Locale.ROOT)
            + "/" + name.toLowerCase(Locale.ROOT)).getBytes(StandardCharsets.UTF_8)).substring(0, 10);
        return slug.substring(0, Math.min(69, slug.length())) + "-" + hash;
    }

    private static boolean validSegment(String value) {
        return value.length() <= 100 && value.matches("[A-Za-z0-9_.-]+")
            && !value.equals(".") && !value.equals("..");
    }
}
