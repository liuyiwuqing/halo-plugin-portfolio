package site.muyin.portfolio.scheme;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;
import run.halo.app.extension.AbstractExtension;
import run.halo.app.extension.GVK;
import site.muyin.portfolio.enums.ProjectStatus;

/**
 * 作品集项目。
 *
 * @author Lywq
 * @since 1.0.0
 */
@Data
@Accessors(chain = true)
@EqualsAndHashCode(callSuper = true)
@GVK(group = "portfolio.muyin.site", version = "v1alpha1", kind = "Project", plural = "projects",
    singular = "project")
@AllArgsConstructor
@NoArgsConstructor
public class Project extends AbstractExtension {

    public static final String PROJECT_EXTENSION_NAME_PREFIX = "project-";

    @Schema(description = "项目标题", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 120)
    private String title;

    @Schema(description = "稳定访问标识", requiredMode = Schema.RequiredMode.REQUIRED, maxLength = 80)
    private String slug;

    @Schema(description = "项目摘要", maxLength = 500)
    private String summary;

    @Schema(description = "项目详情，支持 Markdown 文本")
    private String content;

    @Schema(description = "封面图地址")
    private String cover;

    @Schema(description = "项目来源平台")
    private String platform;

    @Schema(description = "项目类型")
    private String type;

    @Schema(description = "技术栈标签")
    private List<String> techStacks;

    @Schema(description = "普通展示标签")
    private List<String> tags;

    @Schema(description = "仓库地址")
    private String repoUrl;

    @Schema(description = "演示地址")
    private String demoUrl;

    @Schema(description = "文档地址")
    private String docsUrl;

    @Schema(description = "来源提供方，为后续同步预留")
    private String sourceProvider;

    @Schema(description = "仓库所有者，为后续同步预留")
    private String repoOwner;

    @Schema(description = "仓库名称，为后续同步预留")
    private String repoName;

    @Schema(description = "排序值，越大越靠前")
    private Integer priority;

    @Schema(description = "是否推荐")
    private Boolean featured;

    @Schema(description = "项目状态")
    private ProjectStatus status;

    @Schema(description = "创建时间")
    private String createTime;

    @Schema(description = "更新时间")
    private String updateTime;
}
