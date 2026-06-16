package site.muyin.portfolio.finders;

import java.util.Map;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListResult;
import site.muyin.portfolio.model.ProjectOptions;
import site.muyin.portfolio.scheme.Project;
import site.muyin.portfolio.setting.PortfolioSetting;

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

    Mono<ListResult<Project>> listByPlatform(String platform, Integer page, Integer size);

    Mono<ListResult<Project>> listByType(String type, Integer page, Integer size);

    Mono<ListResult<Project>> listByTechStack(String techStack, Integer page, Integer size);

    Mono<ListResult<Project>> listByTag(String tag, Integer page, Integer size);

    Mono<ListResult<Project>> recent(Integer size);

    Mono<ListResult<Project>> featured(Integer page, Integer size);

    Mono<Long> count();

    Mono<ProjectOptions> options();

    Mono<PortfolioSetting> setting();

    Mono<Map<String, String>> platformLabels();

    Mono<Map<String, String>> typeLabels();

    String renderContent(Project project);

    Flux<Project> listAll();

    Mono<Project> getBySlug(String slug);
}
