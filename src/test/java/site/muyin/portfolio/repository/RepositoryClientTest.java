package site.muyin.portfolio.repository;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoryClientTest {

    @Test
    void readsPublicGithubMetadataFromFixedApiAndDropsUnsafeHomepage() {
        var metaRequest = new AtomicReference<ClientRequest>();
        var readmeRequest = new AtomicReference<ClientRequest>();
        var encoded = Base64.getEncoder().encodeToString("# halo".getBytes(StandardCharsets.UTF_8));
        var client = new RepositoryClient(WebClient.builder().exchangeFunction(req -> {
            if (req.url().getPath().endsWith("/readme")) {
                readmeRequest.set(req);
                return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                    .body("{\"content\":\"" + encoded
                        + "\",\"html_url\":\"https://github.com/halo-dev/halo/blob/main/README.md\"}").build());
            }
            metaRequest.set(req);
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                .body("""
                    {"name":"halo", "private":false, "description":"内容管理", "homepage":"javascript:alert(1)",
                    "stargazers_count":42, "forks_count":5, "open_issues_count":2, "language":"Java",
                    "license":{"spdx_id":"GPL-3.0"}, "topics":["cms","cms","halo"],
                    "archived":false, "default_branch":"main", "pushed_at":"2026-10-01T00:00:00Z"}
                    """).build());
        }).build());

        var snapshot = client.fetch(RepositoryAddress.parse("https://github.com/halo-dev/halo.git/")).block();

        assertThat(metaRequest.get().url().toString()).isEqualTo("https://api.github.com/repos/halo-dev/halo");
        assertThat(readmeRequest.get().url().toString())
            .isEqualTo("https://api.github.com/repos/halo-dev/halo/readme");
        assertThat(metaRequest.get().headers().getFirst("Authorization")).isNull();
        assertThat(snapshot.url()).isEqualTo("https://github.com/halo-dev/halo");
        assertThat(snapshot.description()).isEqualTo("内容管理");
        assertThat(snapshot.stars()).isEqualTo(42);
        assertThat(snapshot.license()).isEqualTo("GPL-3.0");
        assertThat(snapshot.homepage()).isEmpty();
        assertThat(snapshot.topics()).containsExactly("cms", "halo");
        assertThat(snapshot.readme()).isEqualTo("# halo");
        assertThat(snapshot.readmeUrl()).isEqualTo("https://github.com/halo-dev/halo/blob/main/README.md");
        assertThat(snapshot.syncedAt()).isNotBlank();
    }

    @Test
    void mapsGiteeStringLicenseAndMissingOptionalFields() {
        var metaRequest = new AtomicReference<ClientRequest>();
        var readmeRequest = new AtomicReference<ClientRequest>();
        var client = new RepositoryClient(WebClient.builder().exchangeFunction(req -> {
            if (req.url().getPath().endsWith("/readme")) {
                readmeRequest.set(req);
                return Mono.just(ClientResponse.create(HttpStatus.NOT_FOUND).body("not found").build());
            }
            metaRequest.set(req);
            return Mono.just(ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json")
                .body("{\"name\":\"tool\",\"private\":false,\"license\":\"MIT\"}").build());
        }).build());
        var snapshot = client.fetch(RepositoryAddress.parse("https://gitee.com/team/tool")).block();
        assertThat(metaRequest.get().url().toString()).isEqualTo("https://gitee.com/api/v5/repos/team/tool");
        assertThat(readmeRequest.get().url().toString()).isEqualTo("https://gitee.com/api/v5/repos/team/tool/readme");
        assertThat(snapshot.license()).isEqualTo("MIT");
        assertThat(snapshot.stars()).isZero();
        assertThat(snapshot.topics()).isEmpty();
        assertThat(snapshot.readme()).isEmpty();
        assertThat(snapshot.readmeUrl()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://github.com/a/b", "https://github.com.evil.test/a/b",
        "https://127.0.0.1/a/b", "https://github.com@localhost/a/b", "https://github.com:443/a/b",
        "https://github.com/a/b?access_token=secret", "https://gitee.com/a/b#readme",
        "https://github.com/a/b/issues", "https://github.com/a/%2e%2e", "https://github.com/a/..",
        "https://github.com/a%2fb/c", "git@github.com:a/b.git", ""})
    void rejectsAddressesThatCouldChangeRequestDestination(String url) {
        assertThatThrownBy(() -> RepositoryAddress.parse(url)).isInstanceOfSatisfying(
            ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void stableImportIdentityDistinguishesNormalizationCollisions() {
        assertThat(RepositoryAddress.parse("https://github.com/a/b.c").suggestedSlug())
            .isNotEqualTo(RepositoryAddress.parse("https://github.com/a/b-c").suggestedSlug());
        assertThat(RepositoryAddress.parse("https://github.com/A/B.git").suggestedSlug())
            .isEqualTo(RepositoryAddress.parse("https://github.com/a/b").suggestedSlug());
        assertThat(RepositoryAddress.parse("https://gitee.com/" + "a".repeat(100) + "/" + "b".repeat(100))
            .suggestedSlug()).hasSize(80);
    }

    @ParameterizedTest
    @ValueSource(ints = {301, 302, 401, 403, 404, 429, 500})
    void mapsUpstreamFailuresWithoutFollowingRedirects(int status) {
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        var client = new RepositoryClient(WebClient.builder().exchangeFunction(req -> {
            calls.incrementAndGet();
            return Mono.just(ClientResponse.create(HttpStatus.valueOf(status))
                .header("Location", "http://127.0.0.1/private").body("upstream secret").build());
        }).build());
        var expected = status == 404 ? HttpStatus.NOT_FOUND
            : status == 401 || status == 403 || status == 429 ? HttpStatus.TOO_MANY_REQUESTS : HttpStatus.BAD_GATEWAY;
        assertThatThrownBy(() -> client.fetch(RepositoryAddress.parse("https://github.com/a/b")).block())
            .isInstanceOfSatisfying(ResponseStatusException.class, error -> {
                assertThat(error.getStatusCode()).isEqualTo(expected);
                assertThat(error.getReason()).doesNotContain("secret");
            });
        assertThat(calls.get()).isOne();
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"name\":\"repo\",\"private\":true}", "{\"name\":\"repo\"}", "not json", ""})
    void failsClosedForInvalidOrPrivateRepository(String body) {
        var client = new RepositoryClient(WebClient.builder().exchangeFunction(req -> Mono.just(
            ClientResponse.create(HttpStatus.OK).header("Content-Type", "application/json").body(body).build())).build());
        assertThatThrownBy(() -> client.fetch(RepositoryAddress.parse("https://github.com/a/b")).block())
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }
}
