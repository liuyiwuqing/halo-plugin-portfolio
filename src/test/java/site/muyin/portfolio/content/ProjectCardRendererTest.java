package site.muyin.portfolio.content;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import site.muyin.portfolio.scheme.Project;
import site.muyin.portfolio.service.ProjectService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectCardRendererTest {

    @Mock
    ProjectService projectService;

    @Test
    void leavesContentWithoutProjectCardsUntouchedAndDoesNotQueryProjects() {
        var renderer = new ProjectCardRenderer(projectService);
        var html = "<p>普通正文</p>";

        var rendered = renderer.render(html).block();

        assertThat(rendered).isEqualTo(html);
        verifyNoInteractions(projectService);
    }

    @Test
    void rendersPublishedProjectCardAndEscapesProjectFields() {
        var renderer = new ProjectCardRenderer(projectService);
        var project = new Project()
            .setSlug("halo-portfolio")
            .setTitle("<Halo Portfolio>")
            .setSummary("集中展示 \"项目\" & 插件")
            .setCover("javascript:alert(1)")
            .setPlatform("github")
            .setType("plugin");
        when(projectService.getPublishedBySlug("halo-portfolio")).thenReturn(Mono.just(project));

        var rendered = renderer.render("""
            <p>推荐项目：</p>
            <portfolio-project-card data-slug="halo-portfolio"></portfolio-project-card>
            """).block();

        assertThat(rendered)
            .contains("data-portfolio-rendered-project-card")
            .contains("href=\"/portfolio/halo-portfolio\"")
            .contains("&lt;Halo Portfolio&gt;")
            .contains("集中展示 &quot;项目&quot; &amp; 插件")
            .contains("github", "plugin")
            .doesNotContain("<portfolio-project-card", "javascript:alert");
        verify(projectService).getPublishedBySlug("halo-portfolio");
    }

    @Test
    void rendersThemeAdaptiveProjectCardWithStableCoverRatio() {
        var renderer = new ProjectCardRenderer(projectService);
        var project = new Project()
            .setSlug("hydro-minim")
            .setTitle("氢·简 / Hydro-Minim")
            .setSummary("适配 Halo 的极简主题")
            .setCover("/upload/hydro-minim.png")
            .setPlatform("halo")
            .setType("theme");
        when(projectService.getPublishedBySlug("hydro-minim")).thenReturn(Mono.just(project));

        var rendered = renderer.render("""
            <portfolio-project-card data-slug="hydro-minim"></portfolio-project-card>
            """).block();

        assertThat(rendered)
            .contains("class=\"portfolio-project-card\"")
            .contains("class=\"portfolio-project-card__link\"")
            .contains("portfolio-project-card__cover")
            .contains("class=\"portfolio-project-card__body\"")
            .contains("color:inherit")
            .contains("background:color-mix(in srgb,currentColor 7%,transparent)")
            .contains("aspect-ratio:16/10")
            .contains("role=\"img\"")
            .contains("aria-label=\"氢·简 / Hydro-Minim\"")
            .contains("background-image:")
            .contains("/upload/hydro-minim.png")
            .doesNotContain("<img");
        verify(projectService).getPublishedBySlug("hydro-minim");
    }

    @Test
    void rendersMissingProjectFallbackCard() {
        var renderer = new ProjectCardRenderer(projectService);
        when(projectService.getPublishedBySlug("missing")).thenReturn(Mono.empty());

        var rendered = renderer.render("""
            <portfolio-project-card data-slug="missing"></portfolio-project-card>
            """).block();

        assertThat(rendered)
            .contains("data-portfolio-rendered-project-card")
            .contains("missing")
            .contains("项目不存在或未发布")
            .doesNotContain("<portfolio-project-card");
        verify(projectService).getPublishedBySlug("missing");
    }
}
