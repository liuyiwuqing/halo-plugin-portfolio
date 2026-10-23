package site.muyin.portfolio.router;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.ListResult;
import run.halo.app.theme.TemplateNameResolver;
import site.muyin.portfolio.content.ProjectCardRenderer;
import site.muyin.portfolio.content.ProjectContentRenderer;
import site.muyin.portfolio.query.ProjectQuery;
import site.muyin.portfolio.scheme.Project;
import site.muyin.portfolio.service.PortfolioSettingService;
import site.muyin.portfolio.service.ProjectService;
import site.muyin.portfolio.service.impl.ProjectServiceImpl;
import site.muyin.portfolio.setting.PortfolioSetting;
import site.muyin.portfolio.setting.PortfolioSetting.OptionItem;

/**
 * 默认公开项目页面路由。
 *
 * @author Lywq
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class PortfolioPageRouter {

    private static final String LIST_PATH = "/portfolio";
    private static final String DETAIL_PATH = "/portfolio/{slug}";
    private static final String LIST_TEMPLATE = "portfolio";
    private static final String DETAIL_TEMPLATE = "portfolio-detail";

    private final TemplateNameResolver templateNameResolver;
    private final PortfolioSettingService settingService;
    private final ProjectService projectService;
    private final ProjectContentRenderer contentRenderer;
    private final ProjectCardRenderer projectCardRenderer;

    @Bean
    RouterFunction<ServerResponse> portfolioPageRouterFunction() {
        return RouterFunctions.route()
            .GET(LIST_PATH, this::listPage)
            .GET(DETAIL_PATH, this::detailPage)
            .build();
    }

    private Mono<ServerResponse> listPage(ServerRequest request) {
        return getSetting()
            .filter(this::isDefaultPageEnabled)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
            .flatMap(setting -> {
                var pageQuery = PortfolioPageQuery.from(request.queryParams(), setting.getPageSize());
                var query = new ProjectQuery(request.exchange());
                pageQuery.filters().forEach(query::setQueryParam);
                return projectService.list(ProjectServiceImpl.publishedListOptions(query),
                        PageRequestImpl.of(pageQuery.page(), pageQuery.size(), pageQuery.sort()))
                    .flatMap(projects -> render(request, LIST_TEMPLATE, pageModel(setting, projects, pageQuery)));
            });
    }

    private Mono<ServerResponse> detailPage(ServerRequest request) {
        return getSetting()
            .filter(this::isDefaultPageEnabled)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
            .flatMap(setting -> projectService.getPublishedBySlug(request.pathVariable("slug"))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在")))
                .flatMap(project -> renderProjectContent(project).flatMap(projectContentHtml -> {
                    var model = new HashMap<String, Object>();
                    model.put("title", project.getTitle() + " - " + setting.getSeoTitle());
                    model.put("description", project.getSummary() == null ? setting.getSeoDescription() : project.getSummary());
                    model.put("project", project);
                    model.put("projectContentHtml", projectContentHtml);
                    addOptionLabels(model, setting);
                    return render(request, DETAIL_TEMPLATE, model);
                })));
    }

    private Map<String, Object> pageModel(PortfolioSetting setting, ListResult<Project> projects,
        PortfolioPageQuery pageQuery) {
        var model = new HashMap<String, Object>();
        model.put("title", setting.getSeoTitle());
        model.put("description", setting.getSeoDescription());
        model.put("projects", projects);
        model.put("pageSize", pageQuery.size());
        model.put("filters", pageQuery.filters());
        model.put("hasFilters", pageQuery.filtered());
        model.put("currentPage", pageQuery.page());
        model.put("totalProjects", projects.getTotal());
        var totalPages = (int) Math.max(1, Math.min(Integer.MAX_VALUE / pageQuery.size(),
            (projects.getTotal() + pageQuery.size() - 1) / pageQuery.size()));
        model.put("totalPages", totalPages);
        model.put("previousPageUrl", pageQuery.page() > 1
            ? pageQuery.pageUrl(Math.min(pageQuery.page() - 1, totalPages)) : "");
        model.put("nextPageUrl", pageQuery.page() < totalPages ? pageQuery.pageUrl(pageQuery.page() + 1) : "");
        model.put("firstPageUrl", pageQuery.pageUrl(1));
        addOptionLabels(model, setting);
        return model;
    }

    private void addOptionLabels(Map<String, Object> model, PortfolioSetting setting) {
        model.put("platformLabels", optionLabels(setting.getPlatformOptions(), PortfolioSetting.defaultPlatformOptions()));
        model.put("typeLabels", optionLabels(setting.getTypeOptions(), PortfolioSetting.defaultTypeOptions()));
    }

    private Map<String, String> optionLabels(List<OptionItem> configured, List<OptionItem> defaults) {
        var labels = toOptionLabelMap(configured);
        return labels.isEmpty() ? toOptionLabelMap(defaults) : labels;
    }

    private Map<String, String> toOptionLabelMap(List<OptionItem> options) {
        var labels = new LinkedHashMap<String, String>();
        if (options == null) {
            return labels;
        }
        for (var option : options) {
            if (option == null || !StringUtils.hasText(option.getValue()) || !StringUtils.hasText(option.getLabel())) {
                continue;
            }
            labels.putIfAbsent(option.getValue().trim(), option.getLabel().trim());
        }
        return labels;
    }

    private Mono<ServerResponse> render(ServerRequest request, String template, Map<String, Object> model) {
        return templateNameResolver.resolveTemplateNameOrDefault(request.exchange(), template)
            .flatMap(templateName -> ServerResponse.ok().render(templateName, model));
    }

    private Mono<PortfolioSetting> getSetting() {
        return settingService.getGeneralSetting();
    }

    private String detailContent(Project project) {
        return StringUtils.hasText(project.getContent()) ? project.getContent() : project.getSummary();
    }

    private Mono<String> renderProjectContent(Project project) {
        return projectCardRenderer.render(contentRenderer.renderMarkdown(detailContent(project)));
    }

    private boolean isDefaultPageEnabled(PortfolioSetting setting) {
        return !Boolean.FALSE.equals(setting.getDefaultPageEnabled());
    }

}
