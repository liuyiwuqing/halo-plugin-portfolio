package site.muyin.portfolio.content;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProjectContentRendererTest {

    private final ProjectContentRenderer renderer = new ProjectContentRenderer();

    @Test
    void renderMarkdownAllowsRawImageHtmlAndSanitizesUnsafeHtml() {
        var html = renderer.renderMarkdown("""
            <img src="https://github.com/user-attachments/assets/example"
                 style="height: 400px !important; width: auto; object-fit: contain;"
                 onerror="alert(1)" />

            [危险链接](javascript:alert(1))

            <a href="javascript:alert(1)" onclick="alert(1)">坏链接</a>

            <script>alert(1)</script>
            """);

        assertThat(html)
            .contains("<img src=\"https://github.com/user-attachments/assets/example\"")
            .contains("style=\"height: 400px !important; width: auto; object-fit: contain;\"")
            .contains(">危险链接</a>", ">坏链接</a>")
            .doesNotContain("&lt;img", "onerror", "onclick", "<script>", "javascript:alert");
    }
}
