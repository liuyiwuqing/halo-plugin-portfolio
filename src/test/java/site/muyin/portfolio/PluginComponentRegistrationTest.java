package site.muyin.portfolio;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.PluginClassLoader;
import org.pf4j.PluginManager;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.beans.factory.support.DefaultBeanNameGenerator;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.reactive.server.WebTestClient;
import run.halo.app.core.extension.endpoint.CustomEndpoint;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.SchemeManager;
import run.halo.app.plugin.PluginContext;
import run.halo.app.plugin.ReactiveSettingFetcher;
import run.halo.app.theme.TemplateNameResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PluginComponentRegistrationTest {

    private static final String COMPONENT_INDEX = "META-INF/plugin-components.idx";
    private static final String CONSOLE_ENDPOINT = "site.muyin.portfolio.endpoint.PortfolioConsoleEndpoint";
    private static final List<String> REPOSITORY_COMPONENTS = List.of(
        "site.muyin.portfolio.endpoint.routes.RepositoryRoutes",
        "site.muyin.portfolio.service.RepositoryService",
        "site.muyin.portfolio.repository.RepositoryClient"
    );

    @TempDir
    Path tempDirectory;

    @Test
    void developmentContextStartsWhenHistoricalJarHasAnOutdatedIndex() throws Exception {
        var pluginJar = Path.of(System.getProperty("portfolio.pluginJar"));
        var historicalJar = historicalJarWithoutRepositoryComponents(pluginJar);
        var classesDirectory = Path.of(PortfolioPlugin.class.getProtectionDomain()
            .getCodeSource().getLocation().toURI());
        var resourcesDirectory = Path.of(Objects.requireNonNull(
            getClass().getResource("/plugin.yaml")).toURI()).getParent();

        try (var classLoader = pluginClassLoader()) {
            // Halo/PF4J 开发模式先加入 classes/resources，再加入 build/libs 中的 JAR。
            classLoader.addFile(classesDirectory.toFile());
            classLoader.addFile(resourcesDirectory.toFile());
            classLoader.addFile(historicalJar.toFile());
            classLoader.addFile(pluginJar.toFile());

            assertContextStarts(classLoader);
            assertThat(classLoader.getResource(COMPONENT_INDEX))
                .isEqualTo(resourcesDirectory.resolve(COMPONENT_INDEX).toUri().toURL());
        }
    }

    @Test
    void packagedContextStartsFromGeneratedIndex() throws Exception {
        var pluginJar = Path.of(System.getProperty("portfolio.pluginJar"));
        try (var classLoader = pluginClassLoader()) {
            classLoader.addFile(pluginJar.toFile());
            assertContextStarts(classLoader);
        }
        try (var jar = new JarFile(pluginJar.toFile())) {
            assertThat(jar.stream().filter(entry -> COMPONENT_INDEX.equals(entry.getName())).count())
                .isEqualTo(1);
        }
    }

    private Path historicalJarWithoutRepositoryComponents(Path pluginJar) throws Exception {
        List<String> outdatedIndex;
        try (var jar = new JarFile(pluginJar.toFile());
             var reader = new BufferedReader(new InputStreamReader(
                 jar.getInputStream(jar.getJarEntry(COMPONENT_INDEX)), StandardCharsets.UTF_8))) {
            outdatedIndex = reader.lines().filter(line -> !REPOSITORY_COMPONENTS.contains(line)).toList();
        }
        var historicalJar = tempDirectory.resolve("plugin-portfolio-old.jar");
        try (var output = new JarOutputStream(Files.newOutputStream(historicalJar))) {
            output.putNextEntry(new JarEntry(COMPONENT_INDEX));
            output.write(String.join("\n", outdatedIndex).getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return historicalJar;
    }

    private PluginClassLoader pluginClassLoader() {
        var descriptor = new DefaultPluginDescriptor("portfolio", "", PortfolioPlugin.class.getName(),
            "1.0.0", "*", "", "");
        return new PluginClassLoader(mock(PluginManager.class), descriptor, getClass().getClassLoader());
    }

    private void assertContextStarts(PluginClassLoader classLoader) throws Exception {
        var manifestLoader = new YamlPropertiesFactoryBean();
        manifestLoader.setResources(new ClassPathResource("plugin.yaml", classLoader));
        assertThat(Objects.requireNonNull(manifestLoader.getObject()).getProperty("spec.version"))
            .isNotBlank();
        var previousClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(classLoader);
        try (var sharedContext = new GenericApplicationContext();
             var pluginContext = new AnnotationConfigApplicationContext(
                 new DefaultListableBeanFactory())) {
            sharedContext.getBeanFactory().registerSingleton("extensionClient", mock(ReactiveExtensionClient.class));
            sharedContext.getBeanFactory().registerSingleton("schemeManager", mock(SchemeManager.class));
            sharedContext.getBeanFactory().registerSingleton("settingFetcher", mock(ReactiveSettingFetcher.class));
            sharedContext.getBeanFactory().registerSingleton("templateNameResolver", mock(TemplateNameResolver.class));
            sharedContext.getBeanFactory().registerSingleton("pluginContext", mock(PluginContext.class));
            sharedContext.refresh();

            pluginContext.setParent(sharedContext);
            pluginContext.setClassLoader(classLoader);
            pluginContext.getBeanFactory().setBeanClassLoader(classLoader);
            pluginContext.setBeanNameGenerator(DefaultBeanNameGenerator.INSTANCE);
            // 与 Halo 相同：按实际类加载器读取的索引逐个注册，不能用包扫描绕过索引。
            try (var reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                classLoader.getResourceAsStream(COMPONENT_INDEX)), StandardCharsets.UTF_8))) {
                for (var component : reader.lines().filter(line -> !line.isBlank() && !line.startsWith("#")).toList()) {
                    pluginContext.registerBean(classLoader.loadClass(component));
                }
            }
            pluginContext.refresh();

            for (var component : REPOSITORY_COMPONENTS) {
                var type = classLoader.loadClass(component);
                assertThat(type.getClassLoader()).isSameAs(classLoader);
                assertThat(pluginContext.getBeansOfType(type)).hasSize(1);
            }
            var endpoint = (CustomEndpoint) pluginContext.getBean(classLoader.loadClass(CONSOLE_ENDPOINT));
            WebTestClient.bindToRouterFunction(endpoint.endpoint()).build()
                .post().uri("/projects/repository/import").exchange().expectStatus().isBadRequest();
        } finally {
            Thread.currentThread().setContextClassLoader(previousClassLoader);
        }
    }
}
