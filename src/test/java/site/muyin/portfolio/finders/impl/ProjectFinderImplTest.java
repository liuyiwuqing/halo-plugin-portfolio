package site.muyin.portfolio.finders.impl;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.PageRequest;
import site.muyin.portfolio.content.ProjectContentRenderer;
import site.muyin.portfolio.model.ProjectOptions;
import site.muyin.portfolio.query.ProjectQuery;
import site.muyin.portfolio.scheme.Project;
import site.muyin.portfolio.service.PortfolioSettingService;
import site.muyin.portfolio.service.ProjectService;
import site.muyin.portfolio.setting.PortfolioSetting;
import site.muyin.portfolio.setting.PortfolioSetting.OptionItem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectFinderImplTest {

    @Mock
    ProjectService projectService;

    @Mock
    PortfolioSettingService settingService;

    @Test
    void listByPlatformUsesPublishedPlatformFilterAndDefaultPaging() {
        var project = new Project().setSlug("halo-portfolio").setPlatform("github");
        when(projectService.list(any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 10, 1, List.of(project))));
        var finder = finder();

        var result = finder.listByPlatform(" github ", null, -1).block();

        assertThat(result).isNotNull();
        assertThat(result.getItems()).containsExactly(project);

        var optionsCaptor = ArgumentCaptor.forClass(ListOptions.class);
        var pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(projectService).list(optionsCaptor.capture(), pageCaptor.capture());
        assertThat(optionsCaptor.getValue().toString())
            .contains("status = published", "platform = github");
        assertThat(pageCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(10);
        assertThat(pageCaptor.getValue().getSort()).isEqualTo(ProjectQuery.defaultSort());
    }

    @Test
    void recentUsesFirstPageAndRequestedSize() {
        var project = new Project().setSlug("halo-portfolio");
        when(projectService.list(any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 3, 3, List.of(project))));
        var finder = finder();

        var result = finder.recent(3).block();

        assertThat(result).isNotNull();
        assertThat(result.getItems()).containsExactly(project);

        var optionsCaptor = ArgumentCaptor.forClass(ListOptions.class);
        var pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(projectService).list(optionsCaptor.capture(), pageCaptor.capture());
        assertThat(optionsCaptor.getValue().toString()).isEqualTo("status = published");
        assertThat(pageCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(3);
        assertThat(pageCaptor.getValue().getSort())
            .isEqualTo(Sort.by(Sort.Order.desc("metadata.creationTimestamp")));
    }

    @Test
    void quickFiltersUsePublishedFilterAndTrimmedValues() {
        when(projectService.list(any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(ListResult.emptyResult()));
        var finder = finder();

        finder.listByType(" plugin ", 2, 5).block();
        finder.listByTechStack(" Java ", 2, 5).block();
        finder.listByTag(" Halo ", 2, 5).block();

        var optionsCaptor = ArgumentCaptor.forClass(ListOptions.class);
        var pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(projectService, times(3)).list(optionsCaptor.capture(), pageCaptor.capture());
        assertThat(optionsCaptor.getAllValues())
            .extracting(ListOptions::toString)
            .containsExactly(
                "(status = published AND type = plugin)",
                "(status = published AND techStacks = Java)",
                "(status = published AND tags = Halo)"
            );
        assertThat(pageCaptor.getAllValues())
            .allSatisfy(pageRequest -> {
                assertThat(pageRequest.getPageNumber()).isEqualTo(2);
                assertThat(pageRequest.getPageSize()).isEqualTo(5);
                assertThat(pageRequest.getSort()).isEqualTo(ProjectQuery.defaultSort());
            });
    }

    @Test
    void listByTrimsCombinedFilterValues() {
        when(projectService.list(any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(ListResult.emptyResult()));
        var finder = finder();

        finder.listBy(" Halo ", " github ", " plugin ", " Java ", 1, 10).block();

        var optionsCaptor = ArgumentCaptor.forClass(ListOptions.class);
        verify(projectService).list(optionsCaptor.capture(), any(PageRequest.class));
        assertThat(optionsCaptor.getValue().toString())
            .contains(
                "status = published",
                "title CONTAINS Halo",
                "summary CONTAINS Halo",
                "slug = Halo",
                "platform = github",
                "type = plugin",
                "tags = Java",
                "techStacks = Java"
            )
            .doesNotContain(
                "CONTAINS  Halo ",
                "platform =  github ",
                "type =  plugin ",
                "tags =  Java ",
                "techStacks =  Java "
            );
    }

    @Test
    void countUsesPublishedListTotal() {
        when(projectService.list(any(ListOptions.class), any(PageRequest.class)))
            .thenReturn(Mono.just(new ListResult<>(1, 1, 7, List.of(new Project()))));
        var finder = finder();

        var total = finder.count().block();

        assertThat(total).isEqualTo(7);

        var optionsCaptor = ArgumentCaptor.forClass(ListOptions.class);
        var pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(projectService).list(optionsCaptor.capture(), pageCaptor.capture());
        assertThat(optionsCaptor.getValue().toString()).isEqualTo("status = published");
        assertThat(pageCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(1);
    }

    @Test
    void exposesSettingAndOptionsForThemeTemplates() {
        var setting = new PortfolioSetting();
        setting.setSeoTitle("作品集");
        var options = new ProjectOptions(
            List.of(new OptionItem("GitHub", "github")),
            List.of(new OptionItem("插件", "plugin"))
        );
        when(settingService.getGeneralSetting()).thenReturn(Mono.just(setting));
        when(settingService.getProjectOptions()).thenReturn(Mono.just(options));
        var finder = finder();

        assertThat(finder.setting().block()).isSameAs(setting);
        assertThat(finder.options().block()).isSameAs(options);
    }

    @Test
    void exposesOptionLabelMapsForThemeLookup() {
        var options = new ProjectOptions(
            List.of(new OptionItem("GitHub", "github")),
            List.of(new OptionItem("插件", "plugin"))
        );
        when(settingService.getProjectOptions()).thenReturn(Mono.just(options));
        var finder = finder();

        assertThat(finder.platformLabels().block()).isEqualTo(Map.of("github", "GitHub"));
        assertThat(finder.typeLabels().block()).isEqualTo(Map.of("plugin", "插件"));
    }

    @Test
    void renderContentUsesProjectContentAndSanitizesMarkdownHtml() {
        var finder = finder();
        var project = new Project()
            .setSummary("摘要")
            .setContent("## 亮点\n\n<script>alert(1)</script>\n\n支持 **Markdown**。");

        var html = finder.renderContent(project);

        assertThat(html)
            .contains("<h2>亮点</h2>", "<strong>Markdown</strong>")
            .doesNotContain("<script>", "alert(1)", "摘要");
    }

    private ProjectFinderImpl finder() {
        return new ProjectFinderImpl(projectService, settingService, new ProjectContentRenderer());
    }
}
