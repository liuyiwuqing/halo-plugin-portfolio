package site.muyin.portfolio.model;

import io.swagger.v3.oas.annotations.media.Schema;

public record RepositoryRequest(
    @Schema(description = "公开 GitHub 或 Gitee 仓库的 HTTPS 地址", requiredMode = Schema.RequiredMode.REQUIRED)
    String repoUrl) {
}
