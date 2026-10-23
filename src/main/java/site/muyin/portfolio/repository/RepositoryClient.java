package site.muyin.portfolio.repository;

import com.fasterxml.jackson.databind.JsonNode;
import io.netty.channel.ChannelOption;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import site.muyin.portfolio.model.RepositorySnapshot;
import run.halo.app.extension.Unstructured;

@Component
public class RepositoryClient {

    private final WebClient webClient;

    public RepositoryClient() {
        this(WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(HttpClient.create()
                .followRedirect(false)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
                .responseTimeout(Duration.ofSeconds(10))))
            .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(512 * 1024))
            .defaultHeader("User-Agent", "Halo-Portfolio")
            .defaultHeader("Accept", "application/json")
            .build());
    }

    RepositoryClient(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<RepositorySnapshot> fetch(RepositoryAddress address) {
        return webClient.get().uri(address.apiUri()).exchangeToMono(response -> {
                var status = response.statusCode().value();
                if (status == 200) {
                    return response.bodyToMono(String.class)
                        .switchIfEmpty(Mono.error(upstreamError()))
                        .flatMap(body -> fetchReadme(address)
                            .onErrorResume(error -> Mono.just(Readme.empty()))
                            .map(readme -> snapshot(address, parseBody(body), readme)));
                }
                var error = switch (status) {
                    case 404 -> new ResponseStatusException(HttpStatus.NOT_FOUND, "仓库不存在或不是公开仓库");
                    case 401, 403, 429 -> new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                        "仓库平台拒绝访问或触发匿名访问限流，请稍后重试");
                    default -> upstreamError();
                };
                return response.releaseBody().then(Mono.error(error));
            })
            .timeout(Duration.ofSeconds(15))
            .onErrorMap(error -> !(error instanceof ResponseStatusException), error -> upstreamError());
    }

    private Mono<Readme> fetchReadme(RepositoryAddress address) {
        return webClient.get().uri(address.readmeUri()).exchangeToMono(response -> {
                if (response.statusCode().value() != 200) {
                    // README 缺失或上游异常不影响仓库元数据导入，降级为空内容。
                    return response.releaseBody().then(Mono.just(Readme.empty()));
                }
                return response.bodyToMono(String.class)
                    .switchIfEmpty(Mono.just(""))
                    .map(this::parseReadme);
            });
    }

    private JsonNode parseBody(String body) {
        try {
            return Unstructured.OBJECT_MAPPER.readTree(body);
        } catch (java.io.IOException error) {
            throw upstreamError();
        }
    }

    private Readme parseReadme(String body) {
        try {
            var json = Unstructured.OBJECT_MAPPER.readTree(body);
            return new Readme(decodeReadme(text(json, "content")), safeHomepage(text(json, "html_url")));
        } catch (java.io.IOException error) {
            return Readme.empty();
        }
    }

    private static String decodeReadme(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return "";
        }
        try {
            return new String(Base64.getMimeDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException error) {
            return "";
        }
    }

    private RepositorySnapshot snapshot(RepositoryAddress address, JsonNode body, Readme readme) {
        if (!body.isObject() || body.path("name").asText().isBlank()
            || !body.has("private") || !body.get("private").isBoolean() || body.path("private").asBoolean()) {
            throw upstreamError();
        }
        var license = body.path("license");
        var licenseName = license.isTextual() ? license.asText() : text(license, "spdx_id");
        if (licenseName.isBlank() || licenseName.equals("NOASSERTION")) {
            licenseName = text(license, "name");
        }
        return new RepositorySnapshot(address.provider(), address.owner(), address.name(), address.url(),
            address.suggestedSlug(), text(body, "description"), safeHomepage(text(body, "homepage")),
            text(body, "language"), licenseName, text(body, "default_branch"),
            count(body, "stargazers_count"), count(body, "forks_count"), count(body, "open_issues_count"),
            topics(body.path("topics")), body.path("archived").asBoolean(false),
            text(body, "pushed_at"), Instant.now().toString(), readme.content(), readme.url());
    }

    private record Readme(String content, String url) {
        static Readme empty() {
            return new Readme("", "");
        }
    }

    private static String text(JsonNode body, String field) {
        return body.path(field).isTextual() ? body.path(field).asText() : "";
    }

    private static long count(JsonNode body, String field) {
        return Math.max(0, body.path(field).asLong(0));
    }

    private static List<String> topics(JsonNode node) {
        var topics = new ArrayList<String>();
        if (node.isArray()) {
            node.forEach(item -> {
                if (item.isTextual() && !item.asText().isBlank() && topics.size() < 30) {
                    topics.add(item.asText());
                }
            });
        }
        return topics.stream().distinct().toList();
    }

    private static String safeHomepage(String value) {
        try {
            var uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                && uri.getHost() != null && uri.getRawUserInfo() == null ? value : "";
        } catch (IllegalArgumentException error) {
            return "";
        }
    }

    private static ResponseStatusException upstreamError() {
        return new ResponseStatusException(HttpStatus.BAD_GATEWAY,
            "仓库平台暂时不可用、请求超时或返回数据异常，请稍后重试");
    }
}
