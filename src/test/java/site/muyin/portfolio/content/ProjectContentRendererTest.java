package site.muyin.portfolio.content;

import org.junit.jupiter.api.Test;
import org.jsoup.Jsoup;

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

    @Test
    void removesEntityEncodedAndWhitespaceObfuscatedExecutableUrls() {
        var html = renderer.renderMarkdown("""
            <a href="jav&#x61;script:alert(1)">hex</a>
            <a href="&#106;avascript:alert(2)">decimal</a>
            <a href="java&#x09;script:alert(3)">tab</a>
            <a href="java&#10;script:alert(4)">newline</a>
            <img src="d&#97;ta:text/html,unsafe" onerror=alert(5)>
            <a href=javascript:alert(6)>unquoted</a>
            """);

        var document = Jsoup.parseBodyFragment(html);
        assertThat(document.select("a[href], img[src], [onerror]")).isEmpty();
        assertThat(document.text()).contains("hex", "decimal", "tab", "newline", "unquoted");
    }

    @Test
    void removesExecutableContainersAndNonAllowlistedAttributes() {
        var html = renderer.renderMarkdown("""
            <div onclick="alert(1)">safe</div>
            <script>alert(2)</script>
            <iframe srcdoc="unsafe">iframe content</iframe>
            <svg><a xlink:href="javascript:alert(3)">svg content</a></svg>
            <math><mtext>math content</mtext></math>
            <object data="https://example.com">object content</object>
            <form action="https://example.com"><input name="password"></form>
            """);

        assertThat(html).contains("<div>safe</div>")
            .doesNotContain("onclick", "script", "iframe", "svg", "math", "object", "form", "input");
    }

    @Test
    void allowsOnlyLayoutStylesWithoutCssUrlsOrExpressions() {
        var html = renderer.renderMarkdown("""
            <img src="/cover.png" style="height: 400px !important; width: auto; object-fit: contain;
              background: url(https://example.com/track); width: expression(alert(1));
              behavior: url(evil.htc); position: fixed; max-width: 100%;">
            <p style="text-align: center; color: red; width: e&#120;pression(alert(1))">text</p>
            """);

        assertThat(html)
            .contains("src=\"/cover.png\"", "height: 400px !important; width: auto; object-fit: contain; max-width: 100%;")
            .contains("style=\"text-align: center;\"")
            .doesNotContain("background", "expression", "behavior", "position", "color:", "url(");
    }

    @Test
    void preservesMarkdownRelativeLinksAndBothProjectCardPlaceholders() {
        var html = renderer.renderMarkdown("""
            # 项目介绍

            **说明**与[详情](/projects/demo)，[外链](https://example.com)，[联系](mailto:hi@example.com)。

            - 第一项

            ```java
            var value = "<safe>";
            ```

            <portfolio-project-card data-slug="demo" data-portfolio-project-card="" onclick="bad()"></portfolio-project-card>

            <div data-portfolio-project-card="" data-slug="legacy"></div>
            """);

        var document = Jsoup.parseBodyFragment(html);
        assertThat(document.select("h1").text()).isEqualTo("项目介绍");
        assertThat(document.select("strong").text()).isEqualTo("说明");
        assertThat(document.select("li").text()).isEqualTo("第一项");
        assertThat(document.select("a").eachAttr("href"))
            .containsExactly("/projects/demo", "https://example.com", "mailto:hi@example.com");
        assertThat(document.select("pre code.language-java").text()).contains("<safe>");
        assertThat(document.select("portfolio-project-card[data-slug=demo]")).hasSize(1);
        assertThat(document.select("div[data-portfolio-project-card][data-slug=legacy]")).hasSize(1);
        assertThat(html).doesNotContain("onclick");
    }
}
