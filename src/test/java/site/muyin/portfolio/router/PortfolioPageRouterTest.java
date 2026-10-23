package site.muyin.portfolio.router;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.server.HandlerStrategies;
import org.springframework.web.reactive.result.view.View;
import org.springframework.web.reactive.result.view.ViewResolver;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.PageRequest;
import run.halo.app.theme.TemplateNameResolver;
import site.muyin.portfolio.content.ProjectCardRenderer;
import site.muyin.portfolio.content.ProjectContentRenderer;
import site.muyin.portfolio.enums.ProjectStatus;
import site.muyin.portfolio.scheme.Project;
import site.muyin.portfolio.service.PortfolioSettingService;
import site.muyin.portfolio.service.ProjectService;
import site.muyin.portfolio.setting.PortfolioSetting;
import site.muyin.portfolio.setting.PortfolioSetting.OptionItem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PortfolioPageRouterTest {

    @Mock
    TemplateNameResolver templateNameResolver;

    @Mock
    PortfolioSettingService settingService;

    @Mock
    ProjectService projectService;

    @Test
    void listPageProvidesOptionLabelMapsToViewModel() {
        var setting = new PortfolioSetting();
        setting.setPlatformOptions(List.of(new OptionItem("GitHub", "github")));
        setting.setTypeOptions(List.of(new OptionItem("插件", "plugin")));
        when(settingService.getGeneralSetting()).thenReturn(Mono.just(setting));
        when(templateNameResolver.resolveTemplateNameOrDefault(any(), eq("portfolio")))
            .thenReturn(Mono.just("portfolio"));

        var project = new Project()
            .setTitle("Halo Portfolio")
            .setSlug("halo-portfolio")
            .setSummary("集中展示项目")
            .setPlatform("github")
            .setType("plugin")
            .setStatus(ProjectStatus.PUBLISHED);
        when(projectService.list(any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(List.of(project))));

        var router = new PortfolioPageRouter(templateNameResolver, settingService, projectService,
            new ProjectContentRenderer(), new ProjectCardRenderer(projectService));
        var client = WebTestClient.bindToRouterFunction(router.portfolioPageRouterFunction())
            .handlerStrategies(HandlerStrategies.builder()
                .viewResolver(new ModelEchoViewResolver())
                .build())
            .build();

        client.get()
            .uri("/portfolio")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .isEqualTo("GitHub|插件");
    }

    @Test
    void listPageKeepsFiltersInPaginationAndUsesStableUpdatedSort() {
        var setting = new PortfolioSetting();
        when(settingService.getGeneralSetting()).thenReturn(Mono.just(setting));
        when(templateNameResolver.resolveTemplateNameOrDefault(any(), eq("portfolio")))
            .thenReturn(Mono.just("portfolio"));
        when(projectService.list(any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(2, 2, 7, List.of(new Project()))));
        var router = new PortfolioPageRouter(templateNameResolver, settingService, projectService,
            new ProjectContentRenderer(), new ProjectCardRenderer(projectService));
        var client = WebTestClient.bindToRouterFunction(router.portfolioPageRouterFunction())
            .handlerStrategies(HandlerStrategies.builder().viewResolver(new NavigationEchoViewResolver()).build())
            .build();

        client.get()
            .uri("/portfolio?page=2&size=2&keyword=Halo&platform=github&type=plugin&tag=Java&featured=true&sort=updated&status=draft")
            .exchange().expectStatus().isOk().expectBody(String.class)
            .value(body -> assertThat(body)
                .contains("page=1", "page=3", "keyword=Halo", "platform=github", "type=plugin", "tag=Java",
                    "featured=true", "sort=updated", "size=2", "total=7")
                .doesNotContain("draft"));
        var options = ArgumentCaptor.forClass(ListOptions.class);
        var page = ArgumentCaptor.forClass(PageRequest.class);
        verify(projectService).list(options.capture(), page.capture());
        assertThat(options.getValue().toString()).contains("status = published", "tags = Java", "techStacks = Java");
        assertThat(page.getValue().getSort().toString()).isEqualTo("updateTime: DESC,metadata.name: ASC");
    }

    @ParameterizedTest
    @CsvSource({
        "page=bad&size=bad&sort=unsafe&featured=oops,1,12,priority",
        "page=-1&size=0&sort=recent,1,12,recent",
        "page=999999999999&size=999999,1,100,priority"
    })
    void listPageBoundsPagingAndIgnoresUnsupportedParameters(String params, int expectedPage,
        int expectedSize, String expectedSort) {
        when(settingService.getGeneralSetting()).thenReturn(Mono.just(new PortfolioSetting()));
        when(templateNameResolver.resolveTemplateNameOrDefault(any(), eq("portfolio")))
            .thenReturn(Mono.just("portfolio"));
        when(projectService.list(any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, expectedSize, 0, List.of())));
        var router = new PortfolioPageRouter(templateNameResolver, settingService, projectService,
            new ProjectContentRenderer(), new ProjectCardRenderer(projectService));
        var client = WebTestClient.bindToRouterFunction(router.portfolioPageRouterFunction())
            .handlerStrategies(HandlerStrategies.builder().viewResolver(new NavigationEchoViewResolver()).build())
            .build();

        client.get().uri("/portfolio?" + params).exchange().expectStatus().isOk();

        var options = ArgumentCaptor.forClass(ListOptions.class);
        var page = ArgumentCaptor.forClass(PageRequest.class);
        verify(projectService).list(options.capture(), page.capture());
        // Halo keeps the selector parser's neutral EMPTY condition in its debug string.
        // Verify the effective filters, not that internal string formatting.
        assertThat(options.getValue().toString()).contains("status = published")
            .doesNotContain("featured =", "unsafe", "oops");
        assertThat(page.getValue().getPageNumber()).isEqualTo(expectedPage);
        assertThat(page.getValue().getPageSize()).isEqualTo(expectedSize);
        assertThat(page.getValue().getSort().toString()).isEqualTo("recent".equals(expectedSort)
            ? "metadata.creationTimestamp: DESC,metadata.name: ASC"
            : "priority: DESC,metadata.creationTimestamp: DESC,metadata.name: ASC");
    }

    @Test
    void listPageEncodesSpecialCharactersInPaginationUrls() {
        when(settingService.getGeneralSetting()).thenReturn(Mono.just(new PortfolioSetting()));
        when(templateNameResolver.resolveTemplateNameOrDefault(any(), eq("portfolio")))
            .thenReturn(Mono.just("portfolio"));
        when(projectService.list(any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 2, List.of(new Project()))));
        var router = new PortfolioPageRouter(templateNameResolver, settingService, projectService,
            new ProjectContentRenderer(), new ProjectCardRenderer(projectService));
        var client = WebTestClient.bindToRouterFunction(router.portfolioPageRouterFunction())
            .handlerStrategies(HandlerStrategies.builder().viewResolver(new NavigationEchoViewResolver()).build())
            .build();

        client.get().uri(java.net.URI.create("/portfolio?size=1&tag=C%2B%2B&keyword=A%26B"))
            .exchange().expectStatus().isOk().expectBody(String.class)
            .value(body -> assertThat(body).contains("tag=C%2B%2B", "keyword=A%26B", "page=2"));
    }

    @Test
    void detailPageProvidesOptionLabelMapsToViewModel() {
        var setting = new PortfolioSetting();
        setting.setPlatformOptions(List.of(new OptionItem("GitHub", "github")));
        setting.setTypeOptions(List.of(new OptionItem("插件", "plugin")));
        when(settingService.getGeneralSetting()).thenReturn(Mono.just(setting));
        when(templateNameResolver.resolveTemplateNameOrDefault(any(), eq("portfolio-detail")))
            .thenReturn(Mono.just("portfolio-detail"));

        var project = new Project()
            .setTitle("Halo Portfolio")
            .setSlug("halo-portfolio")
            .setSummary("集中展示项目")
            .setPlatform("github")
            .setType("plugin")
            .setStatus(ProjectStatus.PUBLISHED);
        when(projectService.getPublishedBySlug("halo-portfolio"))
            .thenReturn(Mono.just(project));

        var router = new PortfolioPageRouter(templateNameResolver, settingService, projectService,
            new ProjectContentRenderer(), new ProjectCardRenderer(projectService));
        var client = WebTestClient.bindToRouterFunction(router.portfolioPageRouterFunction())
            .handlerStrategies(HandlerStrategies.builder()
                .viewResolver(new ModelEchoViewResolver())
                .build())
            .build();

        client.get()
            .uri("/portfolio/halo-portfolio")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .isEqualTo("GitHub|插件");
    }

    @Test
    void detailPageProvidesRenderedMarkdownContentToViewModel() {
        var setting = new PortfolioSetting();
        when(settingService.getGeneralSetting()).thenReturn(Mono.just(setting));
        when(templateNameResolver.resolveTemplateNameOrDefault(any(), eq("portfolio-detail")))
            .thenReturn(Mono.just("portfolio-detail"));

        var project = new Project()
            .setTitle("Halo Portfolio")
            .setSlug("halo-portfolio")
            .setSummary("集中展示项目")
            .setContent("## 亮点\n\n支持 **Markdown** 和 [文档](https://example.com/docs)。")
            .setPlatform("github")
            .setType("plugin")
            .setStatus(ProjectStatus.PUBLISHED);
        when(projectService.getPublishedBySlug("halo-portfolio"))
            .thenReturn(Mono.just(project));

        var router = new PortfolioPageRouter(templateNameResolver, settingService, projectService,
            new ProjectContentRenderer(), new ProjectCardRenderer(projectService));
        var client = WebTestClient.bindToRouterFunction(router.portfolioPageRouterFunction())
            .handlerStrategies(HandlerStrategies.builder()
                .viewResolver(new MarkdownContentEchoViewResolver())
                .build())
            .build();

        client.get()
            .uri("/portfolio/halo-portfolio")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(body -> assertThat(body)
                .contains("<h2>亮点</h2>", "<strong>Markdown</strong>", "<a ",
                    "href=\"https://example.com/docs\"", ">文档</a>")
                .doesNotContain("## 亮点", "**Markdown**"));
    }

    @Test
    void detailPageRendersPortfolioProjectCardsInContent() {
        var setting = new PortfolioSetting();
        when(settingService.getGeneralSetting()).thenReturn(Mono.just(setting));
        when(templateNameResolver.resolveTemplateNameOrDefault(any(), eq("portfolio-detail")))
            .thenReturn(Mono.just("portfolio-detail"));

        var project = new Project()
            .setTitle("Halo Portfolio")
            .setSlug("halo-portfolio")
            .setSummary("集中展示项目")
            .setContent("""
                正文推荐：

                <portfolio-project-card data-slug="embedded-project"></portfolio-project-card>
                """)
            .setPlatform("github")
            .setType("plugin")
            .setStatus(ProjectStatus.PUBLISHED);
        var embeddedProject = new Project()
            .setTitle("Embedded Project")
            .setSlug("embedded-project")
            .setSummary("被详情页正文引用的项目")
            .setPlatform("github")
            .setType("plugin")
            .setStatus(ProjectStatus.PUBLISHED);
        when(projectService.getPublishedBySlug("halo-portfolio"))
            .thenReturn(Mono.just(project));
        when(projectService.getPublishedBySlug("embedded-project"))
            .thenReturn(Mono.just(embeddedProject));

        var router = new PortfolioPageRouter(templateNameResolver, settingService, projectService,
            new ProjectContentRenderer(), new ProjectCardRenderer(projectService));
        var client = WebTestClient.bindToRouterFunction(router.portfolioPageRouterFunction())
            .handlerStrategies(HandlerStrategies.builder()
                .viewResolver(new MarkdownContentEchoViewResolver())
                .build())
            .build();

        client.get()
            .uri("/portfolio/halo-portfolio")
            .exchange()
            .expectStatus().isOk()
            .expectBody(String.class)
            .value(body -> assertThat(body)
                .contains("data-portfolio-rendered-project-card", "Embedded Project",
                    "href=\"/portfolio/embedded-project\"")
                .doesNotContain("<portfolio-project-card"));
    }

    @Test
    void oldProjectsPageRouteIsNotRegistered() {
        var router = new PortfolioPageRouter(templateNameResolver, settingService, projectService,
            new ProjectContentRenderer(), new ProjectCardRenderer(projectService));
        var client = WebTestClient.bindToRouterFunction(router.portfolioPageRouterFunction()).build();

        client.get()
            .uri("/projects")
            .exchange()
            .expectStatus().isNotFound();
    }

    private static class NavigationEchoViewResolver implements ViewResolver {

        @Override
        public Mono<View> resolveViewName(String viewName, Locale locale) {
            return Mono.just((model, contentType, exchange) -> {
                var body = model.get("previousPageUrl") + "|" + model.get("nextPageUrl")
                    + "|total=" + model.get("totalProjects");
                return exchange.getResponse().writeWith(Mono.just(new DefaultDataBufferFactory()
                    .wrap(body.getBytes(StandardCharsets.UTF_8))));
            });
        }
    }

    private static class ModelEchoViewResolver implements ViewResolver {

        @Override
        public Mono<View> resolveViewName(String viewName, Locale locale) {
            return Mono.just((model, contentType, exchange) -> writeModel(model, exchange));
        }

        @SuppressWarnings("unchecked")
        private Mono<Void> writeModel(Map<String, ?> model, ServerWebExchange exchange) {
            var platformLabels = (Map<String, String>) model.get("platformLabels");
            var typeLabels = (Map<String, String>) model.get("typeLabels");
            var body = platformLabels.get("github") + "|" + typeLabels.get("plugin");
            var buffer = new DefaultDataBufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        }
    }

    private static class MarkdownContentEchoViewResolver implements ViewResolver {

        @Override
        public Mono<View> resolveViewName(String viewName, Locale locale) {
            return Mono.just((model, contentType, exchange) -> writeModel(model, exchange));
        }

        private Mono<Void> writeModel(Map<String, ?> model, ServerWebExchange exchange) {
            var body = String.valueOf(model.get("projectContentHtml"));
            var buffer = new DefaultDataBufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            exchange.getResponse().getHeaders().setContentType(MediaType.TEXT_PLAIN);
            return exchange.getResponse().writeWith(Mono.just(buffer));
        }
    }
}
