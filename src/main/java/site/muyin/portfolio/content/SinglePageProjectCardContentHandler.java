package site.muyin.portfolio.content;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.theme.ReactiveSinglePageContentHandler;

@Component
@RequiredArgsConstructor
public class SinglePageProjectCardContentHandler implements ReactiveSinglePageContentHandler {

    private final ProjectCardRenderer renderer;

    @Override
    public Mono<SinglePageContentContext> handle(SinglePageContentContext singlePageContent) {
        return renderer.render(singlePageContent.getContent())
            .map(content -> {
                singlePageContent.setContent(content);
                return singlePageContent;
            });
    }
}
