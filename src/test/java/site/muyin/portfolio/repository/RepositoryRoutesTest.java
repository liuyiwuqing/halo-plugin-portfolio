package site.muyin.portfolio.repository;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import site.muyin.portfolio.endpoint.PortfolioConsoleEndpoint;
import site.muyin.portfolio.endpoint.routes.ProjectRoutes;
import site.muyin.portfolio.endpoint.routes.RepositoryRoutes;
import site.muyin.portfolio.enums.ProjectStatus;
import site.muyin.portfolio.scheme.Project;
import site.muyin.portfolio.service.ProjectService;
import site.muyin.portfolio.service.PortfolioSettingService;
import site.muyin.portfolio.service.RepositoryService;

import static org.mockito.Mockito.*;

class RepositoryRoutesTest {
    private final RepositoryService service = mock(RepositoryService.class);
    private final WebTestClient client = WebTestClient.bindToRouterFunction(new PortfolioConsoleEndpoint(
        new ProjectRoutes(mock(ProjectService.class), mock(PortfolioSettingService.class)),
        new RepositoryRoutes(service)).endpoint()).build();

    @Test
    void repositoryImportBindsJsonAndReturnsDraft() {
        when(service.importRepository("https://github.com/team/tool")).thenReturn(Mono.just(
            new Project().setTitle("tool").setSlug("imported-tool").setStatus(ProjectStatus.DRAFT)));
        client.post().uri("/projects/repository/import").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"repoUrl\":\"https://github.com/team/tool\"}")
            .exchange().expectStatus().isOk().expectBody()
            .jsonPath("$.slug").isEqualTo("imported-tool")
            .jsonPath("$.status").isEqualTo("draft");
        verify(service).importRepository("https://github.com/team/tool");
    }

    @Test
    void missingBodyIsBadRequestAndNeverImports() {
        client.post().uri("/projects/repository/import").exchange().expectStatus().isBadRequest();
        verifyNoInteractions(service);
    }

    @Test
    void duplicateImportReturnsConflict() {
        when(service.importRepository(anyString())).thenReturn(Mono.error(new ResponseStatusException(HttpStatus.CONFLICT)));
        client.post().uri("/projects/repository/import").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"repoUrl\":\"https://github.com/team/tool\"}")
            .exchange().expectStatus().isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void syncUsesProjectSlugAndReturnsUpdatedProject() {
        when(service.sync("my-tool")).thenReturn(Mono.just(new Project().setTitle("作者标题").setSlug("my-tool")));
        client.post().uri("/projects/my-tool/sync").exchange().expectStatus().isOk()
            .expectBody().jsonPath("$.title").isEqualTo("作者标题");
        verify(service).sync("my-tool");
    }
}
