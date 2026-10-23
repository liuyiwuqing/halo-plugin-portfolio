# 项目集优化与仓库聚合扩展分析

核查日期：2026-10-11。产品方向按本次确认确定为 **GitHub / Gitee 仓库聚合工具**，沿用 Halo 的管理、权限和主题能力。

## 结论

插件已有完整的作品内容管理基础：草稿 / 发布 / 归档、平台和类型分类、技术栈与标签、推荐、Markdown、Finder 和编辑器卡片。最有价值的扩展是让仓库成为项目资料来源，同时保留人工撰写的作品介绍。

本轮实现“公开仓库地址 → 元数据预览 → 草稿导入 → 人工编辑发布 → 手动同步快照”闭环。并修复公开页取前 12 条却没有分页入口、后台请求竞态和重复提交等实际问题。

## 同类与参考工具

下表依据官方文档或项目 README，不代表对商业产品完整账户流程或性能的验收，也不构成市场份额排名。

| 对标对象 | 已核实能力与来源 | 取舍与本项目对应能力 |
| --- | --- | --- |
| GitHub Profile / Topics | [固定精选仓库](https://docs.github.com/en/account-and-profile/how-tos/profile-customization/pinning-items-to-your-profile)支持固定最多 6 个仓库 / gist 并调整顺序；[Topics](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/classifying-your-repository-with-topics)用于按领域、用途、语言分类发现仓库 | 复用已有推荐、优先级与标签；本轮增加可点击技术栈 / 标签和推荐筛选，仓库主题用于首次导入标签 |
| Astral | [官方开源 README](https://github.com/astralapp/astral#readme)明确定位为整理 GitHub Stars，支持托管与自建，依赖 GitHub OAuth | 仓库聚合是直接参考方向；本轮不引入 OAuth。账号收藏同步、集合管理属于后续能力。README 的旧版运行环境不能直接作为现代部署建议 |
| developerFolio | [官方 README](https://github.com/saadpasta/developerFolio#readme)包含 GitHub 精选仓库聚合、独立项目、技能、博客等模块，GitHub 数据在构建 / 部署阶段生成 | 借鉴仓库元数据与作品介绍并存；改为 Halo 控制台导入及手动同步，不依赖重新部署，不照搬历史 Token 配置方法 |
| DevPortfolio | [官方 README](https://github.com/ryanfitzgerald/devportfolio#readme)以配置文件维护项目名称、简介、链接和技术栈，未配置模块自动隐藏 | 保留简单项目模型与可选信息；旧项目没有仓库快照也正常展示，不为聚合功能迁移现有技术栈 |
| Webflow CMS | [官方 CMS 功能](https://webflow.com/feature/cms)包含集合、筛选排序、引用、CSV 导入、SEO 和 CMS API | 参考发现与管理体验。本轮落地组合筛选、排序和分页；结构化批量导入后续独立实现，不引入可视化建站器 |
| Adobe Portfolio | [官方功能页](https://portfolio.adobe.com/features)包含作品集合、响应式、多页 / 单页、照片网格及 Behance / Lightroom 集成 | 参考公开展示和来源整合；截图画廊可后续增加，密码保护和自由布局不属于本轮仓库聚合主线 |

历史参考限制：本次 `bento.me` 跳转 Linktree，`read.cv` 返回 402 / `DEPLOYMENT_DISABLED`。二者不能作为仍提供原服务的当前竞品证据。

## 本轮实现与字段策略

### 导入

- 接受 `https://github.com/owner/repo` 与 `https://gitee.com/owner/repo`，支持 `.git` 和尾部斜杠。
- 先只读预览，再确认导入；后端导入时重新获取元数据，不信任浏览器提交的快照。
- 新项目始终是草稿。按提供方、所有者和仓库名生成稳定 slug，附加短哈希，避免点号 / 下划线归一化冲突。重复导入和并发创建同名项目返回冲突。
- 首次映射：仓库名 → 标题，描述 → 摘要，主语言 → 技术栈，主题 → 标签，安全的 HTTP(S) 主页 → 演示地址。
- 类型默认“其他”，由作者确认。公开仓库不等于获得开源许可。
- 首次映射把 README 原始 Markdown 作为详情内容、README 页面地址作为文档地址的默认值；远端 HTML、封面或外链资源不自动导入。

### 同步

`Project.repository` 保存可选快照：提供方、所有者、仓库名、规范地址、描述、主页、主语言、许可证、默认分支、Stars、Forks、未关闭 Issue 数、主题、归档标记、最近推送时间、同步时间，以及 README 原始 Markdown 内容和 README 页面地址。

后续同步更新快照及项目更新时间；详情内容（Markdown）与文档地址为空时用 README 内容 / 地址回填，已人工编辑则保留。标题、摘要、封面、平台 / 类型、技术栈、标签、演示 / 仓库链接、推荐、优先级与发布状态始终保留。人工介绍和仓库元数据可以不同；此处选择保护人工编辑，而非自动覆盖。

同步先读取仓库地址，远端请求完成后重新读取项目，使用最新资源版本更新。期间仓库地址发生变化则返回冲突；上游失败不会清除旧快照。编辑项目更换仓库地址时清除旧快照，避免把旧仓库统计显示在新仓库上。原先手工创建的项目只要配置受支持的仓库地址，也可手动同步。

### 查询与维护

- 公开页支持关键词、平台、类型、标签 / 技术栈、推荐条件，以及优先级、最近收录、最近更新排序。
- 分页保留筛选条件；每页最多 100 条，非法页码和排序回退到默认值；相同排序值使用资源名稳定排序。
- 默认列表与详情展示可选仓库快照；旧数据无须迁移。
- 管理端处理过期请求、失败重试、删除后页码回退、保存重复提交和新建复用旧 metadata。
- Markdown 改为 DOM 白名单清洗，修复实体编码可执行 URL 绕过，保留安全图片布局样式及项目卡片短码。
- UI 资源打包改为 `Sync`，防止历史 hash 资源残留在插件 JAR 中。

## 官方 API 依据与差异

| 项目 | GitHub | Gitee |
| --- | --- | --- |
| 仓库接口 | `GET https://api.github.com/repos/{owner}/{repo}` | `GET https://gitee.com/api/v5/repos/{owner}/{repo}` |
| 官方依据 | [Get a repository](https://docs.github.com/en/rest/repos/repos#get-a-repository) | [Swagger 页面](https://gitee.com/api/v5/swagger)、[官方 OpenAPI JSON](https://gitee.com/api/v5/doc_json) |
| 匿名访问 | 公开仓库允许 | 官方 OpenAPI 的 `access_token` 为非必填；实测公开仓库成功 |
| 许可证 | `license` 对象，优先 `spdx_id` | 本次响应为字符串 |
| 主题 / 归档 | 可提供 `topics` / `archived` | 本次样本未提供两字段；主题记为空，归档默认 false，只在明确为 true 时显示“已归档”，不能据此推断活跃维护 |
| 地址 | 不使用 API 返回地址作为下一跳请求 | 本次 `html_url` 带 `.git`；统一根据已校验的身份构造项目地址 |
| 限流 | [官方速率限制](https://docs.github.com/en/rest/using-the-rest-api/rate-limits-for-the-rest-api)：匿名通常每 IP 每小时 60 次 | 本次响应头观察到 limit=60，未确认官方时间窗口，不按 GitHub 的每小时配额推断 |

2026-10-11 实际匿名读取 `halo-dev/halo`：GitHub 和 Gitee 均返回 HTTP 200；两个平台的不存在仓库样例均返回 404。GitHub 许可证对象和 Gitee 字符串均成功映射，`private` 均为布尔 false。另使用本轮编译的 `RepositoryClient` 从 Java 发起真实请求，两端均成功解析主语言 Java、许可证 GPL-3.0 及统计字段。

这些属于公开 API 读取验证，**不等于已在真实 Halo 数据库完成项目导入、更新、权限和升级验收**。

## 请求和权限边界

- 只请求固定官方 HTTPS API；不接受自定义 API 主机、端口、userinfo、查询串或 fragment，拒绝额外路径和编码路径片段。
- 禁止跟随重定向，连接超时 5 秒、响应超时 10 秒、整体超时 15 秒、响应体上限 512 KiB。
- 使用响应式 WebClient；本轮不读取或保存任何平台 Token。
- 仓库接口仅注册在 Console，沿用项目管理权限；仅查看权限与匿名权限没有新增写入能力。
- 404 映射为“仓库不存在或非公开”；401 / 403 / 429 映射为平台拒绝或限流；其他上游异常、异常响应、超时明确失败，不保存伪造零值结果。
- 未取到必需身份 / 公开性字段时拒绝导入；可选统计缺失则回退为空或零，属于源平台能力差异。

## 后续扩展优先级

| 优先级 | 能力 | 必须先明确的规则 |
| --- | --- | --- |
| P1 | 账号 / 组织仓库批量导入 | 分页、选择预览、排除 fork / archived、每条结果和重复检测，部分失败不能回滚成功项 |
| P1 | 定时同步 | 显式开关、频率和配额预算、退避、手动任务互斥、插件停用后的任务释放 |
| P1 | Secret Token / 私有仓库 | 使用 Halo Secret；禁止凭据进入 Project、ConfigMap、日志和公开响应；确认私有元数据发布规则 |
| P2 | 收藏仓库与集合 | 自有作品和收藏分别管理；OAuth 授权范围、取消收藏 / 同步删除行为需独立决定 |
| P2 | 按字段接受上游变更 | 展示前后差异，用户选择覆盖字段；人工编辑记录和来源跟踪，避免静默覆盖 |
| P2 | 截图、版本和发布信息 | 资源大小、相对链接、安全渲染、缓存、来源署名与增量更新 |

本轮未实现批量、定时、Token、私有仓库或任意 Git 服务支持；上述为后续方案，不能作为已交付能力宣传。

## 验证记录

- Java：78 项测试通过，包含 URL 校验、两种平台响应格式、平台错误、导入 / 同步路由、重复导入、并发冲突、人工数据保护、渲染与分页回归。
- 前端：10 项行为测试通过，类型检查、定向 ESLint 与格式检查通过。
- 构建：Java 21 下 `./gradlew build -x :ui:pnpmInstall --console=plain` 通过；复用本地已安装前端依赖，没有执行依赖升级。
- 客户端：以现有 OpenAPI 为基线，从当前 Java 路由注解和模型导出新增接口 / schema，运行项目的 `generateApiClient -x generateOpenApiDocs` 生成；本地没有运行中的 Halo 服务，未从真实 Halo `/v3/api-docs` 拉取。
- 差异检查：手写文件 `git diff --check` 通过；OpenAPI Generator 输出仍含其默认模板的尾随空白，未对生成文件手工修改格式。
- 模板：真实 Thymeleaf 渲染回归通过，测试数据的桌面与 390px 窄屏检查通过（窄屏内容宽度为 390px）。
- 未覆盖：真实 Halo 安装 / 升级、账号权限联调、持久化导入 / 同步和管理界面完整浏览器操作。插件未部署、未发布、未提交或推送。
