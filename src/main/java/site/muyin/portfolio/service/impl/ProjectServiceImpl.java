package site.muyin.portfolio.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ListOptions;
import run.halo.app.extension.ListResult;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.MetadataUtil;
import run.halo.app.extension.PageRequest;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Unstructured;
import site.muyin.portfolio.enums.ProjectStatus;
import site.muyin.portfolio.query.ProjectQuery;
import site.muyin.portfolio.scheme.Project;
import site.muyin.portfolio.service.ProjectService;

import static run.halo.app.extension.index.query.Queries.equal;
import static site.muyin.portfolio.scheme.Project.PROJECT_EXTENSION_NAME_PREFIX;

/**
 * 项目服务实现。
 *
 * @author Lywq
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class ProjectServiceImpl implements ProjectService {

    private static final DateTimeFormatter DATE_TIME_FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ReactiveExtensionClient client;
    private final ObjectMapper objectMapper = Unstructured.OBJECT_MAPPER;

    @Override
    public Mono<Project> create(Project project) {
        return createWithReactive(buildCreateProject(project));
    }

    @Override
    public Mono<Project> update(Project project) {
        return getBySlug(project.getSlug())
            .flatMap(oldProject -> updateWithReactive(buildUpdateProject(project, oldProject)));
    }

    @Override
    public Mono<Project> getBySlug(String slug) {
        return client.fetch(Project.class, toExtensionName(slug));
    }

    @Override
    public Mono<Project> getByName(String name) {
        return client.fetch(Project.class, name);
    }

    @Override
    public Mono<Project> getPublishedBySlug(String slug) {
        return getBySlug(slug)
            .filter(project -> ProjectStatus.PUBLISHED.equals(project.getStatus()));
    }

    @Override
    public Mono<Project> deleteBySlug(String slug) {
        return getBySlug(slug).flatMap(this::deleteWithReactive);
    }

    @Override
    public Mono<ListResult<Project>> list(ProjectQuery query) {
        return list(query.toListOptions(), query.toPageRequest());
    }

    @Override
    public Mono<ListResult<Project>> list(ListOptions listOptions, PageRequest pageRequest) {
        return client.listBy(Project.class, listOptions, pageRequest);
    }

    @Override
    public Flux<Project> listAll(ListOptions listOptions, Sort sort) {
        return client.listAll(Project.class, listOptions, sort);
    }

    public Project buildCreateProject(Project project) {
        var slug = normalizeSlug(project.getSlug(), project.getTitle());
        project.setSlug(slug);
        if (project.getMetadata() == null) {
            project.setMetadata(new Metadata());
        }
        project.getMetadata().setGenerateName(PROJECT_EXTENSION_NAME_PREFIX);
        project.getMetadata().setName(toExtensionName(slug));
        project.getMetadata().setAnnotations(MetadataUtil.nullSafeAnnotations(project));
        project.getMetadata().setLabels(MetadataUtil.nullSafeLabels(project));

        var now = now();
        project.setCreateTime(now);
        project.setUpdateTime(now);
        if (project.getStatus() == null) {
            project.setStatus(ProjectStatus.DRAFT);
        }
        if (!StringUtils.hasText(project.getPlatform())) {
            project.setPlatform("other");
        }
        if (!StringUtils.hasText(project.getType())) {
            project.setType("other");
        }
        if (project.getPriority() == null) {
            project.setPriority(0);
        }
        if (project.getFeatured() == null) {
            project.setFeatured(false);
        }
        return project;
    }

    private Project buildUpdateProject(Project newProject, Project oldProject) {
        updateIfPresent(newProject.getTitle(), oldProject::setTitle);
        updateIfPresent(newProject.getSummary(), oldProject::setSummary, true);
        updateIfPresent(newProject.getContent(), oldProject::setContent, true);
        updateIfPresent(newProject.getCover(), oldProject::setCover, true);
        updateIfPresent(newProject.getPlatform(), oldProject::setPlatform);
        updateIfPresent(newProject.getType(), oldProject::setType);
        updateIfPresent(newProject.getTechStacks(), oldProject::setTechStacks, true);
        updateIfPresent(newProject.getTags(), oldProject::setTags, true);
        updateIfPresent(newProject.getRepoUrl(), oldProject::setRepoUrl, true);
        updateIfPresent(newProject.getDemoUrl(), oldProject::setDemoUrl, true);
        updateIfPresent(newProject.getDocsUrl(), oldProject::setDocsUrl, true);
        updateIfPresent(newProject.getSourceProvider(), oldProject::setSourceProvider, true);
        updateIfPresent(newProject.getRepoOwner(), oldProject::setRepoOwner, true);
        updateIfPresent(newProject.getRepoName(), oldProject::setRepoName, true);
        updateIfPresent(newProject.getPriority(), oldProject::setPriority);
        updateIfPresent(newProject.getFeatured(), oldProject::setFeatured);
        updateIfPresent(newProject.getStatus(), oldProject::setStatus);
        if (newProject.getMetadata() != null) {
            oldProject.getMetadata().setAnnotations(MetadataUtil.nullSafeAnnotations(newProject));
            oldProject.getMetadata().setLabels(MetadataUtil.nullSafeLabels(newProject));
        }
        oldProject.setUpdateTime(now());
        return oldProject;
    }

    private Mono<Project> createWithReactive(Project project) {
        var extension = toUnstructured(project);
        return client.create(extension).map(unstructured -> objectMapper.convertValue(unstructured, Project.class));
    }

    private Mono<Project> updateWithReactive(Project project) {
        var extension = toUnstructured(project);
        return client.update(extension).map(unstructured -> objectMapper.convertValue(unstructured, Project.class));
    }

    private Mono<Project> deleteWithReactive(Project project) {
        var extension = toUnstructured(project);
        return client.delete(extension).map(unstructured -> objectMapper.convertValue(unstructured, Project.class));
    }

    private Unstructured toUnstructured(Project project) {
        Map<?, ?> extensionMap = objectMapper.convertValue(project, Map.class);
        return new Unstructured(extensionMap);
    }

    public static String toExtensionName(String slug) {
        return PROJECT_EXTENSION_NAME_PREFIX + normalizeSlug(slug, null);
    }

    public static ListOptions publishedListOptions(ProjectQuery query) {
        query.setQueryParam("status", ProjectStatus.PUBLISHED.getValue());
        return query.toListOptions();
    }

    public static ListOptions featuredListOptions(ProjectQuery query) {
        query.setQueryParam("status", ProjectStatus.PUBLISHED.getValue());
        query.setQueryParam("featured", "true");
        return query.toListOptions();
    }

    public static ListOptions publishedListOptions() {
        return ListOptions.builder()
            .andQuery(equal("status", ProjectStatus.PUBLISHED.getValue()))
            .build();
    }

    public static String normalizeSlug(String slug, String fallback) {
        var source = StringUtils.hasText(slug) ? slug : fallback;
        if (!StringUtils.hasText(source)) {
            return "project";
        }
        var normalized = Normalizer.normalize(source, Normalizer.Form.NFKD)
            .toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\u4e00-\\u9fa5]+", "-")
            .replaceAll("^-+|-+$", "");
        return StringUtils.hasText(normalized) ? normalized : "project";
    }

    private static String now() {
        return LocalDateTime.now().format(DATE_TIME_FORMATTER);
    }

    private static <T> void updateIfPresent(T value, java.util.function.Consumer<T> setter) {
        updateIfPresent(value, setter, false);
    }

    private static <T> void updateIfPresent(T value, java.util.function.Consumer<T> setter, boolean nullable) {
        if (value != null || nullable) {
            setter.accept(value);
        }
    }
}
