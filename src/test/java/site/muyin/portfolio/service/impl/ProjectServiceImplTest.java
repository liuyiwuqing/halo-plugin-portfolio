package site.muyin.portfolio.service.impl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Unstructured;
import site.muyin.portfolio.enums.ProjectStatus;
import site.muyin.portfolio.scheme.Project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProjectServiceImplTest {

    @Mock
    ReactiveExtensionClient client;

    @InjectMocks
    ProjectServiceImpl projectService;

    @Test
    void createNormalizesProjectDefaults() {
        when(client.create(any(Unstructured.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        var created = projectService.create(new Project()
                .setTitle("Halo Portfolio")
                .setSlug(" Halo Portfolio ")
                .setSummary("集中展示项目"))
            .block();

        assertThat(created).isNotNull();
        assertThat(created.getSlug()).isEqualTo("halo-portfolio");
        assertThat(created.getMetadata().getName()).isEqualTo("project-halo-portfolio");
        assertThat(created.getStatus()).isEqualTo(ProjectStatus.DRAFT);
        assertThat(created.getPlatform()).isEqualTo("other");
        assertThat(created.getType()).isEqualTo("other");
        assertThat(created.getPriority()).isZero();
        assertThat(created.getFeatured()).isFalse();
        assertThat(created.getCreateTime()).isNotBlank();
        assertThat(created.getUpdateTime()).isNotBlank();

        var captor = ArgumentCaptor.forClass(Unstructured.class);
        verify(client).create(captor.capture());
        assertThat(captor.getValue().getMetadata().getName()).isEqualTo("project-halo-portfolio");
    }

    @Test
    void extensionNameUsesProjectPrefix() {
        assertThat(ProjectServiceImpl.toExtensionName("my-tool")).isEqualTo("project-my-tool");
    }
}
