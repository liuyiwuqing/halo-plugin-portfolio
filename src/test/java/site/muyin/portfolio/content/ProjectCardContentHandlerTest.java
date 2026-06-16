package site.muyin.portfolio.content;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import run.halo.app.theme.ReactivePostContentHandler;
import run.halo.app.theme.ReactiveSinglePageContentHandler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProjectCardContentHandlerTest {

    @Test
    void postHandlerReplacesRenderedContentOnly() {
        var renderer = mock(ProjectCardRenderer.class);
        var postContent = mock(ReactivePostContentHandler.PostContentContext.class);
        when(postContent.getContent()).thenReturn("<portfolio-project-card data-slug=\"demo\"></portfolio-project-card>");
        when(renderer.render("<portfolio-project-card data-slug=\"demo\"></portfolio-project-card>"))
            .thenReturn(Mono.just("<article>demo</article>"));

        var handler = new PostProjectCardContentHandler(renderer);

        handler.handle(postContent).block();

        verify(renderer).render("<portfolio-project-card data-slug=\"demo\"></portfolio-project-card>");
        verify(postContent).setContent("<article>demo</article>");
    }

    @Test
    void singlePageHandlerReplacesRenderedContentOnly() {
        var renderer = mock(ProjectCardRenderer.class);
        var pageContent = mock(ReactiveSinglePageContentHandler.SinglePageContentContext.class);
        when(pageContent.getContent()).thenReturn("<portfolio-project-card data-slug=\"demo\"></portfolio-project-card>");
        when(renderer.render("<portfolio-project-card data-slug=\"demo\"></portfolio-project-card>"))
            .thenReturn(Mono.just("<article>demo</article>"));

        var handler = new SinglePageProjectCardContentHandler(renderer);

        handler.handle(pageContent).block();

        verify(renderer).render("<portfolio-project-card data-slug=\"demo\"></portfolio-project-card>");
        verify(pageContent).setContent("<article>demo</article>");
    }
}
