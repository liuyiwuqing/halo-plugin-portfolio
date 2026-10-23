package site.muyin.portfolio.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;
import site.muyin.portfolio.enums.ProjectStatus;
import site.muyin.portfolio.model.RepositorySnapshot;
import site.muyin.portfolio.repository.RepositoryAddress;
import site.muyin.portfolio.repository.RepositoryClient;
import site.muyin.portfolio.scheme.Project;

@Service
@RequiredArgsConstructor
public class RepositoryService {

    private final RepositoryClient repositoryClient;
    private final ProjectService projectService;
    private final ReactiveExtensionClient extensionClient;

    public Mono<RepositorySnapshot> preview(String repoUrl) {
        return Mono.defer(() -> repositoryClient.fetch(RepositoryAddress.parse(repoUrl)));
    }

    public Mono<Project> importRepository(String repoUrl) {
        return Mono.defer(() -> {
            var address = RepositoryAddress.parse(repoUrl);
            return projectService.getBySlug(address.suggestedSlug()).hasElement()
                .flatMap(exists -> exists
                    ? Mono.error(new ResponseStatusException(HttpStatus.CONFLICT, "该仓库已导入，请在已有项目上同步"))
                    : repositoryClient.fetch(address).flatMap(snapshot -> createImportedProject(new Project()
                        .setTitle(snapshot.name())
                        .setSlug(snapshot.suggestedSlug())
                        .setSummary(limit(snapshot.description(), 500))
                        .setPlatform(snapshot.provider())
                        // 公开可见不等于授予开源许可，分类交给作者确认。
                        .setType("other")
                        .setRepoUrl(snapshot.url())
                        .setDemoUrl(snapshot.homepage())
                        .setDocsUrl(StringUtils.hasText(snapshot.readmeUrl()) ? snapshot.readmeUrl() : null)
                        .setContent(StringUtils.hasText(snapshot.readme()) ? snapshot.readme() : null)
                        .setTechStacks(snapshot.language().isBlank() ? List.of() : List.of(snapshot.language()))
                        .setTags(snapshot.topics())
                        .setSourceProvider(snapshot.provider())
                        .setRepoOwner(snapshot.owner())
                        .setRepoName(snapshot.name())
                        .setRepository(snapshot)
                        .setStatus(ProjectStatus.DRAFT))));
        });
    }

    private Mono<Project> createImportedProject(Project project) {
        return projectService.create(project).onErrorResume(error ->
            // 两个导入可能同时通过前置检查；唯一资源名保证不覆盖，再将竞争失败转为可理解的冲突。
            projectService.getBySlug(project.getSlug()).hasElement()
                .onErrorReturn(false)
                .flatMap(exists -> Mono.error(exists
                    ? new ResponseStatusException(HttpStatus.CONFLICT, "该仓库已导入，请在已有项目上同步")
                    : error)));
    }

    public Mono<Project> sync(String slug) {
        return requiredProject(slug).flatMap(initial -> preview(initial.getRepoUrl())
            // 远端请求之后重新读取，保留请求期间保存的人工编辑并使用最新资源版本。
            .flatMap(snapshot -> requiredProject(slug).flatMap(current -> {
                if (!Objects.equals(initial.getRepoUrl(), current.getRepoUrl())) {
                    return Mono.error(new ResponseStatusException(HttpStatus.CONFLICT,
                        "项目仓库地址已改变，请刷新后重新同步"));
                }
                current.setRepository(snapshot);
                // 详情内容与文档地址为空时默认回填 README；已人工编辑则保留。
                if (!StringUtils.hasText(current.getContent()) && StringUtils.hasText(snapshot.readme())) {
                    current.setContent(snapshot.readme());
                }
                if (!StringUtils.hasText(current.getDocsUrl()) && StringUtils.hasText(snapshot.readmeUrl())) {
                    current.setDocsUrl(snapshot.readmeUrl());
                }
                current.setUpdateTime(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                return extensionClient.update(current);
            })));
    }

    private Mono<Project> requiredProject(String slug) {
        return projectService.getBySlug(slug)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在")));
    }

    private static String limit(String value, int length) {
        return value.substring(0, Math.min(length, value.length()));
    }
}
