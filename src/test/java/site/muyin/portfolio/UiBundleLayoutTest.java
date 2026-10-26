package site.muyin.portfolio;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 校验插件 JAR 内的控制台资源目录与打包器写入的资源基础路径一致。
 *
 * <p>{@code @halo-dev/ui-plugin-bundler-kit} 会依据 {@code plugin.yaml} 的 {@code spec.requires} 选择资源目录：
 * 最低版本不低于 2.25.0 时使用 {@code ui}，并把 {@code __webpack_require__.p} 写成
 * {@code /plugins/{name}/assets/ui/}；否则使用 {@code console} 与 {@code /plugins/{name}/assets/console/}。
 * 若 Gradle 把产物复制到与基础路径不一致的目录，入口 {@code main.js} 仍能被 Halo 加载，
 * 但异步 chunk 会全部 404，表现为控制台页面报 ChunkLoadError。
 */
class UiBundleLayoutTest {

    private static final Pattern PUBLIC_PATH = Pattern.compile("\\.p=\"([^\"]+)\"");
    private static final String UI_ENTRY = "ui/main.js";
    private static final String CONSOLE_ENTRY = "console/main.js";

    @Test
    void uiBundleDirectoryMatchesBundlerPublicPath() throws IOException {
        var pluginJar = Path.of(System.getProperty("portfolio.pluginJar"));
        try (var jar = new JarFile(pluginJar.toFile())) {
            var uiEntry = jar.getJarEntry(UI_ENTRY);
            var consoleEntry = jar.getJarEntry(CONSOLE_ENTRY);

            // Halo 优先选择 ui 目录，打包器在 requires >= 2.25.0 时也只输出 ui 路径，两者必须同时成立。
            assertThat(uiEntry)
                .describedAs("JAR 应包含 %s", UI_ENTRY)
                .isNotNull();
            assertThat(consoleEntry)
                .describedAs("已切换到 ui 资源目录时不应再打包 %s，否则 Halo 可能回退到与基础路径不匹配的旧目录",
                    CONSOLE_ENTRY)
                .isNull();

            var publicPath = readPublicPath(jar, uiEntry);
            assertThat(publicPath)
                .describedAs("ui/main.js 中的资源基础路径必须指向 Halo 的 ui 资源路由")
                .isEqualTo("/plugins/portfolio/assets/ui/");
        }
    }

    private String readPublicPath(JarFile jar, JarEntry entry) throws IOException {
        try (var stream = jar.getInputStream(entry)) {
            var content = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            var matcher = PUBLIC_PATH.matcher(content);
            assertThat(matcher.find()).describedAs("未在 %s 中找到资源基础路径", entry.getName()).isTrue();
            return matcher.group(1);
        }
    }
}
