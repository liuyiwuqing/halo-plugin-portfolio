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
import run.halo.app.extension.ListResult;
import site.muyin.portfolio.model.ProjectOptions;
import site.muyin.portfolio.query.ProjectQuery;
import site.muyin.portfolio.scheme.Project;
import site.muyin.portfolio.service.PortfolioSettingService;
import site.muyin.portfolio.service.ProjectService;
import site.muyin.portfolio.service.impl.ProjectServiceImpl;

import static org.springdoc.core.fn.builders.apiresponse.Builder.responseBuilder;
import static org.springdoc.core.fn.builders.content.Builder.contentBuilder;
import static org.springdoc.core.fn.builders.parameter.Builder.parameterBuilder;
import static org.springdoc.core.fn.builders.requestbody.Builder.requestBodyBuilder;
import static site.muyin.portfolio.endpoint.PortfolioConsoleEndpoint.CONSOLE_TAG;
import static site.muyin.portfolio.endpoint.PortfolioPublicEndpoint.PUBLIC_TAG;

/**
 * 项目接口路由。
 *
 * @author Lywq
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class ProjectRoutes {

    private final ProjectService projectService;
    private final PortfolioSettingService settingService;

    public RouterFunction<ServerResponse> consoleRoutes() {
        final var tag = CONSOLE_TAG;
        return SpringdocRouteBuilder.route()
            .GET("/list", this::listForConsole, builder -> {
                builder.operationId("project-list-for-console").description("获取项目列表").tag(tag)
                    .response(responseBuilder().implementation(ListResult.generateGenericClass(Project.class)));
                ProjectQuery.buildParameters(builder);
            })
            .GET("/settings/options", this::optionsForConsole,
                builder -> builder.operationId("project-options-for-console").description("获取项目配置选项").tag(tag)
                    .response(responseBuilder().implementation(ProjectOptions.class)))
            .GET("/{slug}", this::detailForConsole,
                builder -> builder.operationId("project-detail-for-console").description("获取项目详情").tag(tag)
                    .parameter(parameterBuilder().name("slug").in(ParameterIn.PATH).description("项目 slug")
                        .required(true).implementation(String.class))
                    .response(responseBuilder().implementation(Project.class)))
            .POST("/create", this::createForConsole,
                builder -> builder.operationId("project-create-for-console").description("创建项目").tag(tag)
                    .requestBody(requestBodyBuilder().required(true)
                        .content(contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)
                            .schema(Builder.schemaBuilder().implementation(Project.class))))
                    .response(responseBuilder().implementation(Project.class)))
            .POST("/update", this::updateForConsole,
                builder -> builder.operationId("project-update-for-console").description("更新项目").tag(tag)
                    .requestBody(requestBodyBuilder().required(true)
                        .content(contentBuilder().mediaType(MediaType.APPLICATION_JSON_VALUE)
                            .schema(Builder.schemaBuilder().implementation(Project.class))))
                    .response(responseBuilder().implementation(Project.class)))
            .DELETE("/{slug}/delete", this::deleteForConsole,
                builder -> builder.operationId("project-delete-for-console").description("删除项目").tag(tag)
                    .parameter(parameterBuilder().name("slug").in(ParameterIn.PATH).description("项目 slug")
                        .required(true).implementation(String.class))
                    .response(responseBuilder().implementation(Project.class)))
            .build();
    }

    public RouterFunction<ServerResponse> publicRoutes() {
        final var tag = PUBLIC_TAG;
        return SpringdocRouteBuilder.route()
            .GET("/list", this::listForPublic, builder -> {
                builder.operationId("project-list-for-public").description("获取公开项目列表").tag(tag)
                    .response(responseBuilder().implementation(ListResult.generateGenericClass(Project.class)));
                ProjectQuery.buildParameters(builder);
            })
            .GET("/featured", this::featuredForPublic, builder -> {
                builder.operationId("project-featured-for-public").description("获取推荐项目列表").tag(tag)
                    .response(responseBuilder().implementation(ListResult.generateGenericClass(Project.class)));
                ProjectQuery.buildParameters(builder);
            })
            .GET("/{slug}", this::detailForPublic,
                builder -> builder.operationId("project-detail-for-public").description("获取公开项目详情").tag(tag)
                    .parameter(parameterBuilder().name("slug").in(ParameterIn.PATH).description("项目 slug")
                        .required(true).implementation(String.class))
                    .response(responseBuilder().implementation(Project.class)))
            .build();
    }

    public Mono<ServerResponse> listForConsole(ServerRequest request) {
        var query = new ProjectQuery(request.exchange());
        return projectService.list(query)
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    public Mono<ServerResponse> detailForConsole(ServerRequest request) {
        return projectService.getBySlug(request.pathVariable("slug"))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在")))
            .flatMap(project -> ServerResponse.ok().bodyValue(project));
    }

    public Mono<ServerResponse> createForConsole(ServerRequest request) {
        return request.bodyToMono(Project.class)
            .flatMap(projectService::create)
            .flatMap(project -> ServerResponse.ok().bodyValue(project));
    }

    public Mono<ServerResponse> updateForConsole(ServerRequest request) {
        return request.bodyToMono(Project.class)
            .flatMap(projectService::update)
            .flatMap(project -> ServerResponse.ok().bodyValue(project));
    }

    public Mono<ServerResponse> deleteForConsole(ServerRequest request) {
        return projectService.deleteBySlug(request.pathVariable("slug"))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在")))
            .flatMap(project -> ServerResponse.ok().bodyValue(project));
    }

    public Mono<ServerResponse> optionsForConsole(ServerRequest request) {
        return settingService.getProjectOptions()
            .flatMap(options -> ServerResponse.ok().bodyValue(options));
    }

    public Mono<ServerResponse> listForPublic(ServerRequest request) {
        var query = new ProjectQuery(request.exchange());
        return projectService.list(ProjectServiceImpl.publishedListOptions(query), query.toPageRequest())
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    public Mono<ServerResponse> featuredForPublic(ServerRequest request) {
        var query = new ProjectQuery(request.exchange());
        return projectService.list(ProjectServiceImpl.featuredListOptions(query), query.toPageRequest())
            .flatMap(result -> ServerResponse.ok().bodyValue(result));
    }

    public Mono<ServerResponse> detailForPublic(ServerRequest request) {
        return projectService.getPublishedBySlug(request.pathVariable("slug"))
            .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "项目不存在")))
            .flatMap(project -> ServerResponse.ok().bodyValue(project));
    }
}
