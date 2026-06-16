package site.muyin.portfolio.finders.impl;

import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.PageRequestImpl;
import run.halo.app.extension.index.query.Queries;
import run.halo.app.theme.finders.Finder;
import site.muyin.portfolio.enums.ProjectStatus;
import site.muyin.portfolio.finders.ProjectFinder;
import site.muyin.portfolio.query.ProjectQuery;
import site.muyin.portfolio.scheme.Project;
import site.muyin.portfolio.service.ProjectService;

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

    public ProjectFinderImpl(ProjectService projectService) {
        this.projectService = projectService;
    }

    @Override
    public Mono<ListResult<Project>> list(Integer page, Integer size) {
        return projectService.list(publishedListOptions(), PageRequestImpl.of(pageOrDefault(page),
            sizeOrDefault(size), ProjectQuery.defaultSort()));
    }

    @Override
    public Mono<ListResult<Project>> listBy(String keyword, String platform, String type, String tag, Integer page,
        Integer size) {
        var query = ListOptions.builder()
            .andQuery(equal("status", ProjectStatus.PUBLISHED.getValue()));
        if (hasText(keyword)) {
            query.andQuery(Queries.or(contains("title", keyword), contains("summary", keyword), equal("slug", keyword)));
        }
        if (hasText(platform)) {
            query.andQuery(equal("platform", platform));
        }
        if (hasText(type)) {
            query.andQuery(equal("type", type));
        }
        if (hasText(tag)) {
            query.andQuery(Queries.or(equal("tags", tag), equal("techStacks", tag)));
        }
        return projectService.list(query.build(), PageRequestImpl.of(pageOrDefault(page), sizeOrDefault(size),
            ProjectQuery.defaultSort()));
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

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}
