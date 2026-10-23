package site.muyin.portfolio.endpoint.routes;

import io.swagger.v3.oas.annotations.enums.ParameterIn;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.fn.builders.schema.Builder;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;
import site.muyin.portfolio.model.RepositoryRequest;
import site.muyin.portfolio.model.RepositorySnapshot;
import site.muyin.portfolio.scheme.Project;
import site.muyin.portfolio.service.RepositoryService;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static site.muyin.portfolio.endpoint.PortfolioConsoleEndpoint.CONSOLE_TAG;

@Component
@RequiredArgsConstructor
public class RepositoryRoutes {

    private final RepositoryService repositoryService;

    public RouterFunction<ServerResponse> routes() {
        return SpringdocRouteBuilder.route()
            .POST("/repository/preview", this::preview, builder -> builder
                .operationId("repository-preview-for-console").tag(CONSOLE_TAG).description("预览公开仓库元数据")
                .requestBody(requestBodyBuilder().required(true).content(contentBuilder()
                    .mediaType(MediaType.APPLICATION_JSON_VALUE)
                    .schema(Builder.schemaBuilder().implementation(RepositoryRequest.class))))
                .response(responseBuilder().implementation(RepositorySnapshot.class)))
            .POST("/repository/import", this::importRepository, builder -> builder
                .operationId("repository-import-for-console").tag(CONSOLE_TAG).description("将公开仓库导入为草稿")
                .requestBody(requestBodyBuilder().required(true).content(contentBuilder()
                    .mediaType(MediaType.APPLICATION_JSON_VALUE)
                    .schema(Builder.schemaBuilder().implementation(RepositoryRequest.class))))
                .response(responseBuilder().implementation(Project.class)))
            .POST("/{slug}/sync", this::sync, builder -> builder
                .operationId("repository-sync-for-console").tag(CONSOLE_TAG)
                .description("同步公开仓库快照，保留人工维护的项目内容")
                .parameter(parameterBuilder().in(ParameterIn.PATH).name("slug").required(true)
                    .implementation(String.class))
                .response(responseBuilder().implementation(Project.class)))
            .build();
    }

    private Mono<ServerResponse> preview(ServerRequest request) {
        return body(request).flatMap(body -> repositoryService.preview(body.repoUrl()))
            .flatMap(snapshot -> ServerResponse.ok().bodyValue(snapshot));
    }

    private Mono<ServerResponse> importRepository(ServerRequest request) {
        return body(request).flatMap(body -> repositoryService.importRepository(body.repoUrl()))
            .flatMap(project -> ServerResponse.ok().bodyValue(project));
    }

    private Mono<ServerResponse> sync(ServerRequest request) {
        return repositoryService.sync(request.pathVariable("slug"))
            .flatMap(project -> ServerResponse.ok().bodyValue(project));
    }

    private Mono<RepositoryRequest> body(ServerRequest request) {
        return request.bodyToMono(RepositoryRequest.class)
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "请提供仓库地址")));
    }
}
