package site.muyin.portfolio.service;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;
import site.muyin.portfolio.enums.ProjectStatus;
import site.muyin.portfolio.model.RepositorySnapshot;
import site.muyin.portfolio.repository.RepositoryAddress;
import site.muyin.portfolio.repository.RepositoryClient;
import site.muyin.portfolio.scheme.Project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class RepositoryServiceTest {

    private final RepositoryClient client = mock(RepositoryClient.class);
    private final ProjectService projects = mock(ProjectService.class);
    private final ReactiveExtensionClient extensions = mock(ReactiveExtensionClient.class);
    private final RepositoryService service = new RepositoryService(client, projects, extensions);
    private final RepositoryAddress address = RepositoryAddress.parse("https://github.com/team/tool");
    private RepositorySnapshot snapshot;

    @BeforeEach
    void setup() {
        snapshot = new RepositorySnapshot("github", "team", "tool", address.url(), address.suggestedSlug(),
            "仓库介绍", "https://example.com", "Java", "MIT", "main", 12, 2, 1,
            List.of("tool"), false, "2026-10-10T00:00:00Z", "2026-10-11T00:00:00Z",
            "# Tool", "https://github.com/team/tool/blob/main/README.md");
    }

    @Test
    void importCreatesDraftAndMapsEditableFieldsOnlyInitially() {
        when(projects.getBySlug(address.suggestedSlug())).thenReturn(Mono.empty());
        when(client.fetch(address)).thenReturn(Mono.just(snapshot));
        when(projects.create(any())).thenAnswer(call -> Mono.just(call.getArgument(0)));
        var imported = service.importRepository(address.url()).block();
        assertThat(imported.getStatus()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(imported.getSummary()).isEqualTo("仓库介绍");
        assertThat(imported.getTechStacks()).containsExactly("Java");
        assertThat(imported.getTags()).containsExactly("tool");
        assertThat(imported.getRepository()).isEqualTo(snapshot);
        assertThat(imported.getContent()).isEqualTo("# Tool");
        assertThat(imported.getDocsUrl()).isEqualTo("https://github.com/team/tool/blob/main/README.md");
    }

    @Test
    void duplicateImportFailsBeforeRemoteRequestOrWrite() {
        when(projects.getBySlug(address.suggestedSlug())).thenReturn(Mono.just(new Project()));
        assertThatThrownBy(() -> service.importRepository(address.url()).block())
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(client, extensions);
        verify(projects, never()).create(any());
    }

    @Test
    void concurrentImportReturnsConflictWhenAnotherRequestCreatedTheProject() {
        when(projects.getBySlug(address.suggestedSlug())).thenReturn(Mono.empty(), Mono.just(new Project()));
        when(client.fetch(address)).thenReturn(Mono.just(snapshot));
        when(projects.create(any())).thenReturn(Mono.error(new IllegalStateException("duplicate resource")));
        assertThatThrownBy(() -> service.importRepository(address.url()).block())
            .isInstanceOfSatisfying(ResponseStatusException.class,
                error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }

    @Test
    void syncPreservesEditsSavedWhileRemoteRequestWasInFlight() {
        var initial = new Project().setRepoUrl(address.url()).setSummary("旧介绍");
        var latest = new Project().setRepoUrl(address.url()).setTitle("自定义标题")
            .setSummary("刚保存的人工介绍").setContent("手写 Markdown")
            .setTechStacks(List.of("Kotlin")).setTags(List.of("精选"))
            .setCover("/cover.png").setDemoUrl("https://custom.example.com")
            .setFeatured(true).setPriority(100).setStatus(ProjectStatus.PUBLISHED);
        when(projects.getBySlug("project")).thenReturn(Mono.just(initial), Mono.just(latest));
        when(client.fetch(address)).thenReturn(Mono.just(snapshot));
        when(extensions.update(any(Project.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        var updated = service.sync("project").block();
        assertThat(updated.getRepository()).isEqualTo(snapshot);
        assertThat(updated.getTitle()).isEqualTo("自定义标题");
        assertThat(updated.getSummary()).isEqualTo("刚保存的人工介绍");
        assertThat(updated.getContent()).isEqualTo("手写 Markdown");
        assertThat(updated.getTechStacks()).containsExactly("Kotlin");
        assertThat(updated.getTags()).containsExactly("精选");
        assertThat(updated.getCover()).isEqualTo("/cover.png");
        assertThat(updated.getDemoUrl()).isEqualTo("https://custom.example.com");
        assertThat(updated.getDocsUrl()).isEqualTo("https://github.com/team/tool/blob/main/README.md");
        assertThat(updated.getPriority()).isEqualTo(100);
        assertThat(updated.getFeatured()).isTrue();
        assertThat(updated.getStatus()).isEqualTo(ProjectStatus.PUBLISHED);
        assertThat(updated.getUpdateTime()).isNotBlank();
    }

    @Test
    void syncFillsEmptyContentAndDocsUrlFromReadme() {
        var current = new Project().setRepoUrl(address.url());
        when(projects.getBySlug("project")).thenReturn(Mono.just(current), Mono.just(current));
        when(client.fetch(address)).thenReturn(Mono.just(snapshot));
        when(extensions.update(any(Project.class))).thenAnswer(call -> Mono.just(call.getArgument(0)));
        var updated = service.sync("project").block();
        assertThat(updated.getContent()).isEqualTo("# Tool");
        assertThat(updated.getDocsUrl()).isEqualTo("https://github.com/team/tool/blob/main/README.md");
    }

    @Test
    void failedSyncDoesNotWriteOrEraseExistingSnapshot() {
        var current = new Project().setRepoUrl(address.url()).setRepository(snapshot);
        when(projects.getBySlug("project")).thenReturn(Mono.just(current));
        when(client.fetch(address)).thenReturn(Mono.error(new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS)));
        assertThatThrownBy(() -> service.sync("project").block()).isInstanceOf(ResponseStatusException.class);
        verifyNoInteractions(extensions);
        assertThat(current.getRepository()).isEqualTo(snapshot);
    }

    @Test
    void changingRepositoryDuringSyncReturnsConflictWithoutWrite() {
        when(projects.getBySlug("project")).thenReturn(Mono.just(new Project().setRepoUrl(address.url())),
            Mono.just(new Project().setRepoUrl("https://gitee.com/team/tool")));
        when(client.fetch(address)).thenReturn(Mono.just(snapshot));
        assertThatThrownBy(() -> service.sync("project").block()).isInstanceOfSatisfying(
            ResponseStatusException.class, error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verifyNoInteractions(extensions);
    }
}
