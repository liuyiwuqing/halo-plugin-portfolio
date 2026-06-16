package site.muyin.portfolio.service;

import org.springframework.data.domain.Sort;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.PageRequest;
import site.muyin.portfolio.query.ProjectQuery;
import site.muyin.portfolio.scheme.Project;

/**
 * 项目服务。
 *
 * @author Lywq
 * @since 1.0.0
 */
public interface ProjectService {

    Mono<Project> create(Project project);

    Mono<Project> update(Project project);

    Mono<Project> getBySlug(String slug);

    Mono<Project> getByName(String name);

    Mono<Project> getPublishedBySlug(String slug);

    Mono<Project> deleteBySlug(String slug);

    Mono<ListResult<Project>> list(ProjectQuery query);

    Mono<ListResult<Project>> list(ListOptions listOptions, PageRequest pageRequest);

    Flux<Project> listAll(ListOptions listOptions, Sort sort);
}
