package site.muyin.portfolio.finders;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListResult;
import site.muyin.portfolio.scheme.Project;

/**
 * 主题侧项目查询能力。
 *
 * @author Lywq
 * @since 1.0.0
 */
public interface ProjectFinder {

    Mono<ListResult<Project>> list(Integer page, Integer size);

    Mono<ListResult<Project>> listBy(String keyword, String platform, String type, String tag, Integer page,
        Integer size);

    Mono<ListResult<Project>> featured(Integer page, Integer size);

    Flux<Project> listAll();

    Mono<Project> getBySlug(String slug);
}
