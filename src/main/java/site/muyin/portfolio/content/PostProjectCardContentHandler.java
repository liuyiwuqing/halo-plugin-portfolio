package site.muyin.portfolio.content;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import run.halo.app.theme.ReactivePostContentHandler;

@Component
@RequiredArgsConstructor
public class PostProjectCardContentHandler implements ReactivePostContentHandler {

    private final ProjectCardRenderer renderer;

    @Override
    public Mono<PostContentContext> handle(PostContentContext postContent) {
        return renderer.render(postContent.getContent())
            .map(content -> {
                postContent.setContent(content);
                return postContent;
            });
    }
}
