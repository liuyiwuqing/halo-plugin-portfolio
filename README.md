# portfolio

portfolio - Halo 插件

## 简介

这是一个基于 Halo 的插件项目。

## 开发环境

- Java 21+
- Node.js 18+
- pnpm

## 开发

```bash
# 启用插件
./gradlew haloServer
# 开发前端
cd ui
pnpm install
pnpm dev
```

## 主题侧 Finder API

插件会注册 `projectFinder`，主题模板可直接通过 Thymeleaf 调用公开项目数据。Finder 只返回已发布项目。

常用方法：

| 方法 | 用途 |
| --- | --- |
| `list(page, size)` | 分页获取公开项目 |
| `listBy(keyword, platform, type, tag, page, size)` | 按关键词、平台、类型、标签组合筛选 |
| `listByPlatform(platform, page, size)` | 按平台筛选 |
| `listByType(type, page, size)` | 按类型筛选 |
| `listByTechStack(techStack, page, size)` | 按技术栈筛选 |
| `listByTag(tag, page, size)` | 按展示标签筛选 |
| `featured(page, size)` | 获取推荐项目 |
| `recent(size)` | 获取最近项目 |
| `count()` | 获取公开项目总数 |
| `options()` | 获取平台和类型选项 |
| `setting()` | 获取通用设置 |
| `platformLabels()` / `typeLabels()` | 获取可直接查表的选项标签 Map |
| `renderContent(project)` | 将项目 Markdown 内容渲染为安全 HTML |
| `listAll()` | 获取全部公开项目流 |
| `getBySlug(slug)` | 按 slug 获取公开项目 |

示例：

```html
<th:block th:with="projects=${projectFinder.featured(1, 6)},
                   platformLabels=${projectFinder.platformLabels()},
                   typeLabels=${projectFinder.typeLabels()}">
  <article th:each="project : ${projects.items}">
    <h2 th:text="${project.title}">项目标题</h2>
    <span th:text="${platformLabels[project.platform] ?: project.platform}">github</span>
    <span th:text="${typeLabels[project.type] ?: project.type}">plugin</span>
    <div th:utext="${projectFinder.renderContent(project)}"></div>
  </article>
</th:block>
```

## 构建

```bash
./gradlew build
```

构建完成后，可以在 `build/libs` 目录找到插件 jar 文件。

## 许可证

[GPL-3.0](./LICENSE) © Lywq
