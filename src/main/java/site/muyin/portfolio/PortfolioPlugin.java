package site.muyin.portfolio;

import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import run.halo.app.extension.Scheme;
import run.halo.app.extension.SchemeManager;
import run.halo.app.extension.index.IndexSpecs;
import run.halo.app.plugin.BasePlugin;
import run.halo.app.plugin.PluginContext;
import site.muyin.portfolio.enums.ProjectStatus;
import site.muyin.portfolio.scheme.Project;

/**
 * 作品集插件入口。
 *
 * @author Lywq
 * @since 1.0.0
 */
@Slf4j
@Component
public class PortfolioPlugin extends BasePlugin {

    private final SchemeManager schemeManager;

    public PortfolioPlugin(PluginContext pluginContext, SchemeManager schemeManager) {
        super(pluginContext);
        this.schemeManager = schemeManager;
    }

    @Override
    public void start() {
        schemeManager.register(Project.class, indexSpecs -> {
            indexSpecs.add(IndexSpecs.<Project, String>single("title", String.class)
                .indexFunc(Project::getTitle));
            indexSpecs.add(IndexSpecs.<Project, String>single("slug", String.class)
                .unique(true)
                .nullable(false)
                .indexFunc(Project::getSlug));
            indexSpecs.add(IndexSpecs.<Project, String>single("summary", String.class)
                .indexFunc(Project::getSummary));
            indexSpecs.add(IndexSpecs.<Project, String>single("platform", String.class)
                .indexFunc(Project::getPlatform));
            indexSpecs.add(IndexSpecs.<Project, String>single("type", String.class)
                .indexFunc(Project::getType));
            indexSpecs.add(IndexSpecs.<Project, String>single("status", String.class)
                .indexFunc(project -> project.getStatus() == null ? null : project.getStatus().getValue()));
            indexSpecs.add(IndexSpecs.<Project, String>multi("tags", String.class)
                .indexFunc(project -> project.getTags() == null ? Set.of() : Set.copyOf(project.getTags())));
            indexSpecs.add(IndexSpecs.<Project, String>multi("techStacks", String.class)
                .indexFunc(project -> project.getTechStacks() == null ? Set.of() : Set.copyOf(project.getTechStacks())));
            indexSpecs.add(IndexSpecs.<Project, Boolean>single("featured", Boolean.class)
                .indexFunc(Project::getFeatured));
            indexSpecs.add(IndexSpecs.<Project, Integer>single("priority", Integer.class)
                .indexFunc(Project::getPriority));
        });
        log.info("Portfolio plugin started.");
    }

    @Override
    public void stop() {
        schemeManager.unregister(Scheme.buildFromType(Project.class));
        log.info("Portfolio plugin stopped.");
    }
}
