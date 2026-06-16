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
import run.halo.app.theme.TemplateNameResolver;
import site.muyin.portfolio.query.ProjectQuery;
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

    private final TemplateNameResolver templateNameResolver;
    private final PortfolioSettingService settingService;
    private final ProjectService projectService;

    @Bean
    RouterFunction<ServerResponse> portfolioPageRouterFunction() {
        return RouterFunctions.route()
            .GET("/projects", this::listPage)
            .GET("/projects/{slug}", this::detailPage)
            .build();
    }

    private Mono<ServerResponse> listPage(ServerRequest request) {
        return getSetting()
            .filter(this::isDefaultPageEnabled)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
            .flatMap(setting -> {
                var page = request.queryParam("page").map(this::parsePositiveInt).orElse(1);
                var size = request.queryParam("size").map(this::parsePositiveInt)
                    .orElseGet(() -> setting.getPageSize() == null ? 12 : setting.getPageSize());
                var query = new ProjectQuery(request.exchange());
                return projectService.list(ProjectServiceImpl.publishedListOptions(query),
                        PageRequestImpl.of(page, size, ProjectQuery.defaultSort()))
                    .flatMap(projects -> render(request, "projects", pageModel(setting, projects, size)));
            });
    }

    private Mono<ServerResponse> detailPage(ServerRequest request) {
        return getSetting()
            .filter(this::isDefaultPageEnabled)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND)))
            .flatMap(setting -> projectService.getPublishedBySlug(request.pathVariable("slug"))
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在")))
                .flatMap(project -> {
                    var model = new HashMap<String, Object>();
                    model.put("title", project.getTitle() + " - " + setting.getSeoTitle());
                    model.put("description", project.getSummary() == null ? setting.getSeoDescription() : project.getSummary());
                    model.put("project", project);
                    addOptionLabels(model, setting);
                    return render(request, "project-detail", model);
                }));
    }

    private Map<String, Object> pageModel(PortfolioSetting setting, Object projects, int size) {
        var model = new HashMap<String, Object>();
        model.put("title", setting.getSeoTitle());
        model.put("description", setting.getSeoDescription());
        model.put("projects", projects);
        model.put("pageSize", size);
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

    private boolean isDefaultPageEnabled(PortfolioSetting setting) {
        return !Boolean.FALSE.equals(setting.getDefaultPageEnabled());
    }

    private int parsePositiveInt(String value) {
        try {
            var parsed = Integer.parseInt(value);
            return parsed > 0 ? parsed : 1;
        } catch (NumberFormatException e) {
            return 1;
        }
    }
}
