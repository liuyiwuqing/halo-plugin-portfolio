package site.muyin.portfolio.endpoint;

import lombok.RequiredArgsConstructor;
import org.springdoc.webflux.core.fn.SpringdocRouteBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.ServerResponse;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.GroupVersion;
import site.muyin.portfolio.endpoint.routes.ProjectRoutes;

/**
 * 作品集公开接口。
 *
 * @author Lywq
 * @since 1.0.0
 */
@Component
@RequiredArgsConstructor
public class PortfolioPublicEndpoint implements CustomEndpoint {

    public static final String PUBLIC_GROUP_VERSION = "public.portfolio.muyin.site/v1alpha1";
    public static final String PUBLIC_TAG = PUBLIC_GROUP_VERSION + "/Portfolio";

    private final ProjectRoutes projectRoutes;

    @Override
    public RouterFunction<ServerResponse> endpoint() {
        return SpringdocRouteBuilder.route()
            .nest(RequestPredicates.path("projects"), projectRoutes::publicRoutes)
            .build();
    }

    @Override
    public GroupVersion groupVersion() {
        return GroupVersion.parseAPIVersion(PUBLIC_GROUP_VERSION);
    }
}
