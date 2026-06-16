# 项目集（Portfolio）

[![CI](https://github.com/liuyiwuqing/plugin-portfolio/actions/workflows/ci.yaml/badge.svg)](https://github.com/liuyiwuqing/plugin-portfolio/actions/workflows/ci.yaml)
[![License](https://img.shields.io/github/license/liuyiwuqing/plugin-portfolio)](./LICENSE)
[![Halo](https://img.shields.io/badge/Halo-%3E%3D2.24.0-blue)](https://halo.run)

> Halo CMS 插件 —— 统一管理和展示 GitHub、Gitee、产品、插件、工具等项目作品。

## 交流群

[点击链接加入群聊【halo博客-lywq插件】](https://qm.qq.com/q/wuC7NZr0sw)

<img src="https://github.com/user-attachments/assets/bf162401-07fd-49ec-b50f-5218c9510937" style="height: 400px !important; width: auto; object-fit: contain;" />

## 功能特性

- **项目管理** — 在 Halo 控制台创建、编辑、删除项目，支持草稿 / 已发布 / 已归档三种状态
- **多平台支持** — 内置 GitHub、Gitee、独立站点、私有项目等平台选项，可在设置中自定义扩展
- **多类型分类** — 开源项目、产品、插件、网站、工具、类库，同样支持自定义
- **技术栈 & 标签** — 每个项目可关联多个技术栈和展示标签，方便筛选和检索
- **推荐项目** — 标记重点项目，在首页或侧边栏优先展示
- **默认页面** — 开箱即用的 `/projects` 列表页和 `/projects/{slug}` 详情页，支持主题覆盖
- **Finder API** — 注册 `projectFinder`，主题模板可直接调用，灵活控制渲染
- **编辑器卡片** — 在 Halo 富文本编辑器中插入项目卡片，在文章中嵌入项目展示
- **Markdown 内容** — 项目详情支持 Markdown 编写，自动渲染为安全 HTML
- **RBAC 权限** — 内置查看 / 管理两级角色模板，支持细粒度权限控制
- **SEO 友好** — 自定义页面标题和描述，详情页自动生成 meta 信息

## 安装

### 方式一：从 Release 安装（推荐）

1. 前往 [Releases](https://github.com/liuyiwuqing/plugin-portfolio/releases) 下载最新 `.jar` 文件
2. 在 Halo 控制台 → 插件管理 → 安装插件，上传 jar 文件
3. 启用插件

### 方式二：从源码构建

```bash
git clone https://github.com/liuyiwuqing/plugin-portfolio.git
cd plugin-portfolio
./gradlew build
```

构建产物位于 `build/libs/plugin-portfolio-*.jar`，在 Halo 控制台上传安装。

## 配置

插件启用后，在 **控制台 → 插件 → 项目集 → 设置** 中可配置：

| 配置项 | 说明 | 默认值 |
| --- | --- | --- |
| 启用默认展示页 | 开启后提供 `/projects` 和 `/projects/{slug}` 路由 | 开启 |
| 默认每页数量 | 列表页每页显示项目数 | 12 |
| 默认 SEO 标题 | 页面 `<title>` 和 OG 标题 | 项目作品集 |
| 默认 SEO 描述 | 页面 `<meta description>` | 集中展示开源项目、产品、插件、工具和其他开发作品。 |
| 平台选项 | 项目来源平台下拉选项 | GitHub / Gitee / 独立站点 / 私有项目 / 其他 |
| 类型选项 | 项目类型下拉选项 | 开源项目 / 产品 / 插件 / 网站 / 工具 / 类库 / 其他 |

平台和类型选项支持自由增删改，`value` 建议使用小写英文、数字和下划线。

## 主题集成

### Finder API

插件注册 `projectFinder`，在 Thymeleaf 模板中直接注入使用。Finder 只返回已发布项目。

#### 方法列表

| 方法 | 返回值 | 说明 |
| --- | --- | --- |
| `list(page, size)` | `ListResult<Project>` | 分页获取项目 |
| `listBy(keyword, platform, type, tag, page, size)` | `ListResult<Project>` | 按关键词、平台、类型、标签组合筛选 |
| `listByPlatform(platform, page, size)` | `ListResult<Project>` | 按平台筛选 |
| `listByType(type, page, size)` | `ListResult<Project>` | 按类型筛选 |
| `listByTechStack(techStack, page, size)` | `ListResult<Project>` | 按技术栈筛选 |
| `listByTag(tag, page, size)` | `ListResult<Project>` | 按展示标签筛选 |
| `featured(page, size)` | `ListResult<Project>` | 获取推荐项目 |
| `recent(size)` | `List<Project>` | 获取最近项目（不分页） |
| `count()` | `long` | 公开项目总数 |
| `options()` | `ProjectOptions` | 获取平台和类型选项列表 |
| `setting()` | `PortfolioSetting` | 获取插件通用设置 |
| `platformLabels()` | `Map<String, String>` | 平台 value → label 映射表 |
| `typeLabels()` | `Map<String, String>` | 类型 value → label 映射表 |
| `renderContent(project)` | `String` | 将项目 Markdown 内容渲染为安全 HTML |
| `listAll()` | `Flux<Project>` | 获取全部公开项目流 |
| `getBySlug(slug)` | `Mono<Project>` | 按 slug 获取单个项目 |

#### Thymeleaf 示例

**展示推荐项目：**

```html
<th:block th:with="projects=${projectFinder.featured(1, 6)},
                   platformLabels=${projectFinder.platformLabels()},
                   typeLabels=${projectFinder.typeLabels()}">
  <div class="project-grid">
    <article th:each="project : ${projects.items}" class="project-card">
      <a th:href="@{/projects/{slug}(slug=${project.slug})}">
        <img th:if="${project.cover}" th:src="${project.cover}" th:alt="${project.title}" />
        <h3 th:text="${project.title}">项目标题</h3>
        <p th:text="${project.summary}">项目简介</p>
        <span th:text="${platformLabels[project.platform] ?: project.platform}">平台</span>
        <span th:text="${typeLabels[project.type] ?: project.type}">类型</span>
      </a>
    </article>
  </div>
</th:block>
```

**渲染项目详情内容：**

```html
<div th:utext="${projectFinder.renderContent(project)}"></div>
```

**按技术栈筛选：**

```html
<th:block th:with="projects=${projectFinder.listByTechStack('Vue', 1, 10)}">
  <div th:each="project : ${projects.items}">
    <a th:href="@{/projects/{slug}(slug=${project.slug})}" th:text="${project.title}">标题</a>
  </div>
</th:block>
```

### 默认页面

插件内置两个 Thymeleaf 模板：

| 路由 | 模板 | 说明 |
| --- | --- | --- |
| `/projects` | `projects.html` | 项目列表页，响应式网格布局，支持分页 |
| `/projects/{slug}` | `project-detail.html` | 项目详情页，展示封面、元数据和 Markdown 内容 |

主题可通过提供同名模板覆盖默认页面。关闭设置中的「启用默认展示页」后，插件不再注册路由，完全由主题自行渲染。

### 编辑器项目卡片

在 Halo 富文本编辑器中，可通过工具栏或斜杠命令 `/` 插入「项目卡片」节点。选择一个已发布项目后，文章中会嵌入一个可交互的卡片组件。

卡片在前端渲染为 `<portfolio-project-card data-slug="xxx">` 标签，发布时由插件自动替换为带样式的 HTML 卡片，包含封面、标题、摘要和元数据标签。

## 项目数据模型

每个项目（`Project`）包含以下字段：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `title` | String | ✅ | 项目标题（最长 120 字符） |
| `slug` | String | ✅ | URL 友好标识（最长 80 字符） |
| `summary` | String | - | 项目摘要（最长 500 字符） |
| `content` | String | - | 项目详情，支持 Markdown |
| `cover` | String | - | 封面图 URL |
| `platform` | String | - | 来源平台（如 `github`） |
| `type` | String | - | 项目类型（如 `open_source`） |
| `techStacks` | List\<String\> | - | 技术栈标签 |
| `tags` | List\<String\> | - | 展示标签 |
| `repoUrl` | String | - | 仓库地址 |
| `demoUrl` | String | - | 演示地址 |
| `docsUrl` | String | - | 文档地址 |
| `priority` | Integer | - | 排序值，越大越靠前 |
| `featured` | Boolean | - | 是否推荐 |
| `status` | Enum | - | `DRAFT` / `PUBLISHED` / `ARCHIVED` |
| `createTime` | String | - | 创建时间 |
| `updateTime` | String | - | 更新时间 |

另有 `sourceProvider`、`repoOwner`、`repoName` 字段为后续仓库同步功能预留。

## 开发

### 环境要求

- **Java 21+**（Gradle toolchain 强制）
- **Node.js 20+**
- **pnpm 10+**

### 启动开发服务

```bash
# 终端 1：启动 Halo 开发服务器（含后端热重载）
./gradlew haloServer

# 终端 2：启动前端开发服务器（含 HMR）
cd ui
pnpm install
pnpm dev
```

Halo 开发服务器默认运行在 `http://localhost:8090`，前端开发服务器提供模块热替换。

### 项目结构

```
src/main/java/site/muyin/portfolio/
├── PortfolioPlugin.java          # 插件生命周期，注册 GVK 和索引
├── scheme/Project.java           # 核心数据模型
├── enums/                        # 枚举（项目状态）
├── model/                        # 数据传输对象
├── query/                        # 查询参数构建
├── setting/                      # 插件设置 POJO
├── service/                      # 业务逻辑接口
│   └── impl/                     # 业务逻辑实现
├── endpoint/                     # REST API（Console + Public）
│   └── routes/                   # 路由处理器
├── finders/                      # Finder API 接口
│   └── impl/                     # Finder API 实现
├── content/                      # 内容渲染（Markdown + 项目卡片）
└── router/                       # 默认页面路由

ui/src/
├── index.ts                      # 插件入口，注册路由和编辑器扩展
├── views/ProjectManagerView.vue  # 项目管理页面
├── api/portfolio.ts              # API 客户端和类型定义
├── editor/                       # TipTap 编辑器扩展
└── assets/                       # 静态资源
```

### 构建

```bash
# 完整构建（后端 + 前端）
./gradlew build

# 仅构建前端
cd ui && pnpm build

# 运行后端测试
./gradlew test

# 运行前端检查
cd ui && pnpm check
```

构建产物：`build/libs/plugin-portfolio-*.jar`

### CI/CD

项目使用 GitHub Actions 自动化：

- **CI**（`ci.yaml`）— 推送或 PR 到 `main` 分支时触发，执行构建和测试
- **CD**（`cd.yaml`）— 发布 GitHub Release 时触发，构建并产出插件 jar

## 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端语言 | Java 21 |
| 后端框架 | Spring WebFlux（响应式） |
| 插件平台 | Halo Plugin SDK |
| 构建工具 | Gradle 9.4 |
| 前端框架 | Vue 3（Composition API） |
| 前端语言 | TypeScript |
| 前端构建 | Rsbuild（基于 Rspack） |
| 包管理 | pnpm |
| Markdown | CommonMark |
| 测试 | JUnit 5 / Vitest |

## 许可证

[GPL-3.0](./LICENSE) © [Lywq](https://github.com/liuyiwuqing)
