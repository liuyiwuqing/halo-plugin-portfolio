package site.muyin.portfolio.templates;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.linkbuilder.AbstractLinkBuilder;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import site.muyin.portfolio.scheme.Project;

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

        var listHtml = render("projects", Map.of(
            "title", "项目作品集",
            "description", "集中展示项目",
            "projects", new ProjectsModel(List.of(project)),
            "platformLabels", Map.of("github", "GitHub"),
            "typeLabels", Map.of("plugin", "插件")
        ));
        var detailHtml = render("project-detail", Map.of(
            "title", "Halo Portfolio - 项目作品集",
            "description", "集中展示项目",
            "project", project,
            "platformLabels", Map.of("github", "GitHub"),
            "typeLabels", Map.of("plugin", "插件")
        ));

        assertThat(listHtml).contains("GitHub", "插件");
        assertThat(detailHtml).contains("GitHub", "插件");
    }

    private String render(String template, Map<String, Object> model) {
        var context = new Context();
        context.setVariables(model);
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
            return base;
        }
    }
}
