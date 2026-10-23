package site.muyin.portfolio.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 仓库数据快照，与作者维护的项目介绍分开保存。 */
@Schema(description = "公开仓库元数据；手动同步不会覆盖项目介绍")
public record RepositorySnapshot(
    String provider, String owner, String name, String url, String suggestedSlug,
    String description, String homepage, String language, String license, String defaultBranch,
    long stars, long forks, long openIssues, List<String> topics, boolean archived,
    String pushedAt, String syncedAt,
    @Schema(description = "README 原始 Markdown 内容") String readme,
    @Schema(description = "README 文档地址") String readmeUrl) {
}
