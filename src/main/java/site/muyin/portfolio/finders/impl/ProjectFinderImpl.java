package site.muyin.portfolio.finders.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.index.query.Queries;
import run.halo.app.theme.finders.Finder;
import site.muyin.portfolio.content.ProjectCardRenderer;
import site.muyin.portfolio.content.ProjectContentRenderer;
import site.muyin.portfolio.enums.ProjectStatus;
import site.muyin.portfolio.finders.ProjectFinder;
import site.muyin.portfolio.model.ProjectOptions;
import site.muyin.portfolio.query.ProjectQuery;
import site.muyin.portfolio.scheme.Project;
import site.muyin.portfolio.service.PortfolioSettingService;
import site.muyin.portfolio.service.ProjectService;
import site.muyin.portfolio.setting.PortfolioSetting;
import site.muyin.portfolio.setting.PortfolioSetting.OptionItem;

import static run.halo.app.extension.index.query.Queries.contains;
import static run.halo.app.extension.index.query.Queries.equal;
import static site.muyin.portfolio.service.impl.ProjectServiceImpl.publishedListOptions;

/**
 * 主题侧项目查询实现。
 *
 * @author Lywq
 * @since 1.0.0
 */
@Finder("projectFinder")
public class ProjectFinderImpl implements ProjectFinder {

    private final ProjectService projectService;
    private final PortfolioSettingService settingService;
    private final ProjectContentRenderer contentRenderer;
    private final ProjectCardRenderer projectCardRenderer;

    public ProjectFinderImpl(ProjectService projectService, PortfolioSettingService settingService,
        ProjectContentRenderer contentRenderer, ProjectCardRenderer projectCardRenderer) {
        this.projectService = projectService;
        this.settingService = settingService;
        this.contentRenderer = contentRenderer;
        this.projectCardRenderer = projectCardRenderer;
    }

    @Override
    public Mono<ListResult<Project>> list(Integer page, Integer size) {
        return projectService.list(publishedListOptions(), PageRequestImpl.of(pageOrDefault(page),
            sizeOrDefault(size), ProjectQuery.defaultSort()));
    }

    @Override
    public Mono<ListResult<Project>> listBy(String keyword, String platform, String type, String tag, Integer page,
        Integer size) {
        var cleanKeyword = clean(keyword);
        var cleanPlatform = clean(platform);
        var cleanType = clean(type);
        var cleanTag = clean(tag);
        var query = ListOptions.builder()
            .andQuery(equal("status", ProjectStatus.PUBLISHED.getValue()));
        if (hasText(cleanKeyword)) {
            query.andQuery(Queries.or(
                contains("title", cleanKeyword),
                contains("summary", cleanKeyword),
                equal("slug", cleanKeyword)
            ));
        }
        if (hasText(cleanPlatform)) {
            query.andQuery(equal("platform", cleanPlatform));
        }
        if (hasText(cleanType)) {
            query.andQuery(equal("type", cleanType));
        }
        if (hasText(cleanTag)) {
            query.andQuery(Queries.or(equal("tags", cleanTag), equal("techStacks", cleanTag)));
        }
        return projectService.list(query.build(), PageRequestImpl.of(pageOrDefault(page), sizeOrDefault(size),
            ProjectQuery.defaultSort()));
    }

    @Override
    public Mono<ListResult<Project>> listByPlatform(String platform, Integer page, Integer size) {
        return listByField("platform", platform, page, size);
    }

    @Override
    public Mono<ListResult<Project>> listByType(String type, Integer page, Integer size) {
        return listByField("type", type, page, size);
    }

    @Override
    public Mono<ListResult<Project>> listByTechStack(String techStack, Integer page, Integer size) {
        return listByField("techStacks", techStack, page, size);
    }

    @Override
    public Mono<ListResult<Project>> listByTag(String tag, Integer page, Integer size) {
        return listByField("tags", tag, page, size);
    }

    @Override
    public Mono<ListResult<Project>> recent(Integer size) {
        return projectService.list(publishedListOptions(), PageRequestImpl.of(1, sizeOrDefault(size),
            recentSort()));
    }

    @Override
    public Mono<ListResult<Project>> featured(Integer page, Integer size) {
        var query = ListOptions.builder()
            .andQuery(equal("status", ProjectStatus.PUBLISHED.getValue()))
            .andQuery(equal("featured", true))
            .build();
        return projectService.list(query, PageRequestImpl.of(pageOrDefault(page), sizeOrDefault(size),
            ProjectQuery.defaultSort()));
    }

    @Override
    public Mono<Long> count() {
        return projectService.list(publishedListOptions(), PageRequestImpl.of(1, 1, ProjectQuery.defaultSort()))
            .map(ListResult::getTotal);
    }

    @Override
    public Mono<ProjectOptions> options() {
        return settingService.getProjectOptions();
    }

    @Override
    public Mono<PortfolioSetting> setting() {
        return settingService.getGeneralSetting();
    }

    @Override
    public Mono<Map<String, String>> platformLabels() {
        return options().map(ProjectOptions::platformOptions).map(ProjectFinderImpl::toLabelMap);
    }

    @Override
    public Mono<Map<String, String>> typeLabels() {
        return options().map(ProjectOptions::typeOptions).map(ProjectFinderImpl::toLabelMap);
    }

    @Override
    public String renderContent(Project project) {
        if (project == null) {
            return "";
        }
        var content = hasText(project.getContent()) ? project.getContent() : project.getSummary();
        var rendered = projectCardRenderer.render(contentRenderer.renderMarkdown(content)).block();
        return rendered == null ? "" : rendered;
    }

    @Override
    public Flux<Project> listAll() {
        return projectService.listAll(publishedListOptions(), ProjectQuery.defaultSort());
    }

    @Override
    public Mono<Project> getBySlug(String slug) {
        return projectService.getPublishedBySlug(slug);
    }

    private static int pageOrDefault(Integer page) {
        return page == null || page < 1 ? 1 : page;
    }

    private static int sizeOrDefault(Integer size) {
        return size == null || size < 1 ? 10 : size;
    }

    private static Sort recentSort() {
        return Sort.by(Sort.Order.desc("metadata.creationTimestamp"));
    }

    private static Map<String, String> toLabelMap(List<OptionItem> options) {
        var labels = new LinkedHashMap<String, String>();
        if (options == null) {
            return labels;
        }
        for (var option : options) {
            if (option == null) {
                continue;
            }
            var value = clean(option.getValue());
            var label = clean(option.getLabel());
            if (!hasText(value) || !hasText(label)) {
                continue;
            }
            labels.putIfAbsent(value, label);
        }
        return labels;
    }

    private Mono<ListResult<Project>> listByField(String fieldName, String value, Integer page, Integer size) {
        var cleanValue = clean(value);
        var query = ListOptions.builder()
            .andQuery(equal("status", ProjectStatus.PUBLISHED.getValue()));
        if (hasText(cleanValue)) {
            query.andQuery(equal(fieldName, cleanValue));
        }
        return projectService.list(query.build(), PageRequestImpl.of(pageOrDefault(page), sizeOrDefault(size),
            ProjectQuery.defaultSort()));
    }

    private static String clean(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
