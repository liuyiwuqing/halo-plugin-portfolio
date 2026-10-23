package site.muyin.portfolio.templates;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.linkbuilder.AbstractLinkBuilder;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import site.muyin.portfolio.scheme.Project;
import site.muyin.portfolio.model.RepositorySnapshot;
import org.springframework.web.util.UriUtils;

import static org.assertj.core.api.Assertions.assertThat;

class PortfolioTemplateTest {

    private final SpringTemplateEngine templateEngine = templateEngine();

    @Test
    void publicTemplatesRenderProjectWithStringPlatformAndType() {
        var project = new Project()
            .setTitle("Halo Portfolio")
            .setSlug("halo-portfolio")
            .setSummary("集中展示项目")
            .setPlatform("github")
            .setType("plugin")
            .setFeatured(false)
            .setTechStacks(List.of("Java"));

        var listHtml = render("portfolio", Map.of(
            "title", "项目作品集",
            "description", "集中展示项目",
            "projects", new ProjectsModel(List.of(project)),
            "platformLabels", Map.of("github", "GitHub"),
            "typeLabels", Map.of("plugin", "插件")
        ));
        var detailHtml = render("portfolio-detail", Map.of(
            "title", "Halo Portfolio - 项目作品集",
            "description", "集中展示项目",
            "project", project,
            "projectContentHtml", "<h2>详情</h2><p>支持 <strong>Markdown</strong></p>",
            "platformLabels", Map.of("github", "GitHub"),
            "typeLabels", Map.of("plugin", "插件")
        ));

        assertThat(listHtml).contains("GitHub", "插件");
        assertThat(listHtml).contains("href=\"/portfolio/halo-portfolio\"");
        assertThat(detailHtml).contains("GitHub", "插件");
        assertThat(detailHtml).contains("href=\"/portfolio\"");
        assertThat(detailHtml)
            .contains("<h2>详情</h2>", "<strong>Markdown</strong>")
            .doesNotContain("&lt;h2&gt;详情&lt;/h2&gt;");
    }

    @Test
    void listPageRendersSelectedFiltersPaginationAndEncodedTagLinks() {
        var project = new Project().setTitle("Discovery").setSlug("discovery")
            .setTags(List.of("A&B")).setTechStacks(List.of("C++"));
        var model = new LinkedHashMap<String, Object>();
        model.put("projects", new ProjectsModel(List.of(project)));
        model.put("platformLabels", Map.of("github", "GitHub"));
        model.put("typeLabels", Map.of("plugin", "插件"));
        model.put("filters", Map.of("keyword", "Halo", "platform", "github", "type", "plugin",
            "tag", "C++", "featured", "true", "sort", "updated"));
        model.put("hasFilters", true);
        model.put("currentPage", 2);
        model.put("totalPages", 3);
        model.put("totalProjects", 30L);
        model.put("previousPageUrl", "/portfolio?page=1&tag=C%2B%2B&sort=updated");
        model.put("nextPageUrl", "/portfolio?page=3&tag=C%2B%2B&sort=updated");

        var html = render("portfolio", model);

        assertThat(html).contains("method=\"get\"", "name=\"keyword\" value=\"Halo\"",
            "value=\"updated\" selected=\"selected\"", "共 30 个项目", "第 2 页 / 共 3 页",
            "href=\"/portfolio?tag=C%2B%2B\"", "href=\"/portfolio?tag=A%26B\"",
            "href=\"/portfolio?page=3&amp;tag=C%2B%2B&amp;sort=updated\"");
    }

    @Test
    void emptyFilteredPageOffersRecoveryInsteadOfClaimingNoPublishedProjects() {
        var html = render("portfolio", Map.of(
            "projects", new ProjectsModel(List.of()),
            "platformLabels", Map.of(), "typeLabels", Map.of(), "hasFilters", true,
            "totalProjects", 0L));
        assertThat(html).contains("没有找到匹配的项目", "查看全部项目", "重置筛选")
            .doesNotContain("暂无公开项目</h2>");
    }

    @Test
    void repositorySnapshotIsVisibleWithoutRequiringItOnOlderProjects() {
        var snapshot = new RepositorySnapshot("github", "halo-dev", "halo", "https://github.com/halo-dev/halo",
            "halo", "CMS", null, "Java", "GPL-3.0", "main", 123, 45, 6, List.of("cms"), true,
            "2026-10-01T08:00:00Z", "2026-10-11T08:00:00Z", null, null);
        var project = new Project().setTitle("Halo").setSlug("halo").setRepository(snapshot);
        var listHtml = render("portfolio", Map.of("projects", new ProjectsModel(List.of(project)),
            "platformLabels", Map.of(), "typeLabels", Map.of()));
        var detailHtml = render("portfolio-detail", Map.of("project", project, "projectContentHtml", "",
            "platformLabels", Map.of(), "typeLabels", Map.of()));
        assertThat(listHtml).contains("123 Stars", "45 Forks", "仓库快照");
        assertThat(detailHtml).contains("Java", "GPL-3.0", "已归档", "2026-10-01T08:00:00Z",
            "2026-10-11T08:00:00Z", "同步快照");
    }

    private String render(String template, Map<String, Object> model) {
        var context = new Context();
        var variables = new LinkedHashMap<String, Object>();
        if ("portfolio".equals(template)) {
            variables.put("filters", Map.of("keyword", "", "platform", "", "type", "", "tag", "",
                "featured", "", "sort", "priority"));
            variables.put("pageSize", 12);
            variables.put("hasFilters", false);
            variables.put("currentPage", 1);
            variables.put("totalProjects", 1L);
            variables.put("totalPages", 1);
            variables.put("previousPageUrl", "");
            variables.put("nextPageUrl", "");
            variables.put("firstPageUrl", "/portfolio?page=1&size=12");
        }
        variables.putAll(model);
        context.setVariables(variables);
        return templateEngine.process(template, context);
    }

    private static SpringTemplateEngine templateEngine() {
        var resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding(StandardCharsets.UTF_8.name());
        resolver.setCacheable(false);

        var engine = new SpringTemplateEngine();
        engine.setTemplateResolver(resolver);
        engine.setLinkBuilder(new TestLinkBuilder());
        return engine;
    }

    private record ProjectsModel(List<Project> items) {
    }

    private static class TestLinkBuilder extends AbstractLinkBuilder {

        private TestLinkBuilder() {
            setName("test");
            setOrder(0);
        }

        @Override
        public String buildLink(IExpressionContext context, String base, Map<String, Object> parameters) {
            if (parameters == null || parameters.isEmpty()) {
                return base;
            }
            var pairs = parameters.entrySet().stream()
                .map(entry -> UriUtils.encode(entry.getKey(), StandardCharsets.UTF_8) + "="
                    + UriUtils.encode(String.valueOf(entry.getValue()), StandardCharsets.UTF_8))
                .toList();
            return base + "?" + String.join("&", pairs);
        }
    }
}
