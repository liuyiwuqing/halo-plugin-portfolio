<script setup lang="ts">
import {
  Dialog,
  Toast,
  VButton,
  VCard,
  VEmpty,
  VLoading,
  VModal,
  VPageHeader,
  VPagination,
  VSpace,
} from '@halo-dev/components'
import { computed, onMounted, reactive, ref, watch } from 'vue'
import RiAddLine from '~icons/ri/add-line'
import RiCodeBoxLine from '~icons/ri/code-box-line'
import RiDeleteBinLine from '~icons/ri/delete-bin-line'
import RiEditLine from '~icons/ri/edit-line'
import RiExternalLinkLine from '~icons/ri/external-link-line'
import RiImageAddLine from '~icons/ri/image-add-line'
import RiGithubFill from '~icons/ri/github-fill'
import RiRefreshLine from '~icons/ri/refresh-line'
import RiSearchLine from '~icons/ri/search-line'
import RiGridFill from '~icons/ri/grid-fill'
import RiListCheck from '~icons/ri/list-check'
import RiFilter3Line from '~icons/ri/filter-3-line'
import { utils, type AttachmentLike, type AttachmentSimple } from '@halo-dev/ui-shared'
import {
  portfolioApi,
  type OptionItem,
  type Project,
  type ProjectOptions,
  type ProjectStatus,
} from '@/api/portfolio'

const statusOptions: Array<{ label: string; value: ProjectStatus | '' }> = [
  { label: '全部', value: '' },
  { label: '草稿', value: 'draft' },
  { label: '已发布', value: 'published' },
  { label: '已归档', value: 'archived' },
]

const loading = ref(false)
const optionsLoading = ref(false)
const modalVisible = ref(false)
const coverPickerVisible = ref(false)
const editing = ref(false)
const projects = ref<Project[]>([])
const projectOptions = ref<ProjectOptions>({
  platformOptions: [],
  typeOptions: [],
})
const total = ref(0)
const page = ref(1)
const size = ref(12)
const viewMode = ref<'grid' | 'list'>('grid')

const filters = reactive({
  keyword: '',
  platform: '',
  type: '',
  status: '' as ProjectStatus | '',
})

const form = reactive<Project>({
  title: '',
  slug: '',
  summary: '',
  content: '',
  cover: '',
  platform: '',
  type: '',
  techStacks: [],
  tags: [],
  repoUrl: '',
  demoUrl: '',
  docsUrl: '',
  sourceProvider: '',
  repoOwner: '',
  repoName: '',
  priority: 0,
  featured: false,
  status: 'draft',
})

const techStacksText = ref('')
const tagsText = ref('')

const hasProjects = computed(() => projects.value.length > 0)
const platformOptions = computed(() => projectOptions.value.platformOptions || [])
const typeOptions = computed(() => projectOptions.value.typeOptions || [])
const isEmpty = computed(() => !loading.value && !hasProjects.value)

function optionLabel(options: OptionItem[], value?: string) {
  return options.find((item) => item.value === value)?.label || '其他'
}

function statusClass(status?: ProjectStatus) {
  return `status-${status || 'draft'}`
}

function statusLabel(status?: ProjectStatus) {
  return statusOptions.find((item) => item.value === status)?.label || '草稿'
}

function projectExternalUrl(project: Project) {
  return project.demoUrl || project.repoUrl || project.docsUrl || ''
}

function repositoryLabel(project: Project) {
  const source = `${project.sourceProvider || project.platform || ''} ${project.repoUrl || ''}`
  if (/github/i.test(source)) return 'GitHub'
  if (/gitee/i.test(source)) return 'Gitee'
  if (project.repoUrl) return '代码仓库'
  if (project.demoUrl) return '演示站点'
  if (project.docsUrl) return '文档'
  return optionLabel(platformOptions.value, project.platform)
}

function isGithubRepository(project: Project) {
  return /github/i.test(repositoryLabel(project))
}

function handleCoverSelect(attachments: AttachmentLike[]) {
  const items = attachments
    .map((attachment) => utils.attachment.convertToSimple(attachment))
    .filter(Boolean) as AttachmentSimple[]

  if (!items.length) {
    Toast.warning('请选择一张图片')
    return
  }

  form.cover = items[0].url || ''
  coverPickerVisible.value = false
}

function resetForm() {
  Object.assign(form, {
    metadata: undefined,
    title: '',
    slug: '',
    summary: '',
    content: '',
    cover: '',
    platform: platformOptions.value[0]?.value || '',
    type: typeOptions.value[0]?.value || '',
    techStacks: [],
    tags: [],
    repoUrl: '',
    demoUrl: '',
    docsUrl: '',
    sourceProvider: '',
    repoOwner: '',
    repoName: '',
    priority: 0,
    featured: false,
    status: 'draft',
  })
  techStacksText.value = ''
  tagsText.value = ''
}

function openCreateModal() {
  editing.value = false
  resetForm()
  modalVisible.value = true
}

function openEditModal(project: Project) {
  editing.value = true
  Object.assign(form, JSON.parse(JSON.stringify(project)))
  techStacksText.value = project.techStacks?.join(', ') || ''
  tagsText.value = project.tags?.join(', ') || ''
  modalVisible.value = true
}

function splitText(value: string) {
  return value
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean)
}

function validateForm() {
  if (!form.title?.trim()) {
    Toast.warning('请输入项目标题')
    return false
  }
  if (!form.slug?.trim()) {
    Toast.warning('请输入项目 slug')
    return false
  }
  return true
}

async function fetchProjects() {
  loading.value = true
  try {
    const { data } = await portfolioApi.list({
      page: page.value,
      size: size.value,
      keyword: filters.keyword || undefined,
      platform: filters.platform || undefined,
      type: filters.type || undefined,
      status: filters.status || undefined,
    })
    projects.value = data.items || []
    total.value = data.total || 0
  } finally {
    loading.value = false
  }
}

async function fetchOptions() {
  optionsLoading.value = true
  try {
    const { data } = await portfolioApi.options()
    projectOptions.value = {
      platformOptions: data.platformOptions || [],
      typeOptions: data.typeOptions || [],
    }
    if (!form.platform) {
      form.platform = data.platformOptions?.[0]?.value || 'other'
    }
    if (!form.type) {
      form.type = data.typeOptions?.[0]?.value || 'other'
    }
  } finally {
    optionsLoading.value = false
  }
}

async function submitForm() {
  if (!validateForm()) return
  const payload: Project = {
    ...form,
    slug: form.slug?.trim(),
    title: form.title?.trim(),
    techStacks: splitText(techStacksText.value),
    tags: splitText(tagsText.value),
  }
  if (!editing.value) {
    delete payload.metadata
  }
  try {
    if (editing.value) {
      await portfolioApi.update(payload)
      Toast.success('更新成功')
    } else {
      await portfolioApi.create(payload)
      Toast.success('创建成功')
    }
    modalVisible.value = false
    await fetchProjects()
  } catch (error) {
    Toast.error(editing.value ? '更新失败' : '创建失败')
    throw error
  }
}

function deleteProject(project: Project) {
  if (!project.slug) return
  Dialog.warning({
    title: '删除项目',
    description: `确定删除「${project.title || project.slug}」吗？此操作不可恢复。`,
    confirmType: 'danger',
    confirmText: '删除',
    cancelText: '取消',
    onConfirm: async () => {
      await portfolioApi.delete(project.slug as string)
      Toast.success('删除成功')
      await fetchProjects()
    },
  })
}

function resetFilters() {
  filters.keyword = ''
  filters.platform = ''
  filters.type = ''
  filters.status = ''
  page.value = 1
  fetchProjects()
}

function handleRouteToFront() {
  window.open('/projects', '_blank')
}

watch([() => filters.status, () => filters.platform, () => filters.type], () => {
  page.value = 1
  fetchProjects()
})

onMounted(async () => {
  await fetchOptions()
  await fetchProjects()
})
</script>

<template>
  <VPageHeader title="作品管理">
    <template #icon>
      <RiCodeBoxLine />
    </template>
    <template #actions>
      <VSpace>
        <button type="button" class="portfolio-header-action-button" @click="handleRouteToFront">
          <RiExternalLinkLine />
          <span>查看前台</span>
        </button>
        <VButton
          type="primary"
          size="sm"
          class="portfolio-header-primary-button"
          @click="openCreateModal"
        >
          <template #icon>
            <RiAddLine />
          </template>
          新增项目
        </VButton>
      </VSpace>
    </template>
  </VPageHeader>

  <div class="portfolio-manager-container">
    <!-- 状态页签 -->
    <div class="portfolio-status-tabs">
      <div
        v-for="option in statusOptions"
        :key="option.value"
        class="status-tab-item"
        :class="{ active: filters.status === option.value }"
        @click="filters.status = option.value"
      >
        {{ option.label }}
      </div>
    </div>

    <VCard class="portfolio-project-card" :body-class="['!p-0']">
      <template #header>
        <div class="portfolio-toolbar">
          <div class="toolbar-left">
            <div class="search-input-wrapper">
              <RiSearchLine class="search-icon" />
              <input
                v-model="filters.keyword"
                type="text"
                placeholder="搜索项目..."
                @keyup.enter="fetchProjects"
              />
            </div>
          </div>
          <div class="toolbar-right">
            <VSpace spacing="lg">
              <div class="filter-dropdown-group">
                <div class="filter-select-wrapper">
                  <RiFilter3Line class="filter-icon" />
                  <select v-model="filters.platform">
                    <option value="">所有平台</option>
                    <option v-for="item in platformOptions" :key="item.value" :value="item.value">
                      {{ item.label }}
                    </option>
                  </select>
                </div>
                <div class="filter-select-wrapper">
                  <select v-model="filters.type">
                    <option value="">所有类型</option>
                    <option v-for="item in typeOptions" :key="item.value" :value="item.value">
                      {{ item.label }}
                    </option>
                  </select>
                </div>
              </div>

              <div class="view-mode-switcher">
                <button
                  :class="{ active: viewMode === 'grid' }"
                  class="view-mode-button"
                  title="网格视图"
                  @click="viewMode = 'grid'"
                >
                  <RiGridFill />
                </button>
                <button
                  :class="{ active: viewMode === 'list' }"
                  class="view-mode-button"
                  title="列表视图"
                  @click="viewMode = 'list'"
                >
                  <RiListCheck />
                </button>
              </div>

              <button
                type="button"
                class="toolbar-icon-button is-refresh"
                title="刷新数据"
                aria-label="刷新项目列表"
                @click="fetchProjects"
              >
                <RiRefreshLine :class="{ 'animate-spin': loading }" />
              </button>
            </VSpace>
          </div>
        </div>
      </template>

      <div class="portfolio-content-area">
        <VLoading v-if="loading" class="py-20" />

        <VEmpty
          v-else-if="isEmpty"
          title="空空如也"
          message="没有找到匹配的项目，试着换个搜索词或筛选条件。"
        >
          <template #actions>
            <VButton type="secondary" @click="resetFilters">清空筛选</VButton>
          </template>
        </VEmpty>

        <div v-else :class="['project-view', `view-${viewMode}`]">
          <div
            v-for="project in projects"
            :key="project.slug"
            :class="['project-item-card', statusClass(project.status)]"
          >
            <!-- 封面图区域 -->
            <div class="project-cover-box">
              <img
                v-if="project.cover"
                :src="project.cover"
                :alt="project.title"
                class="project-cover-img"
              />
              <div v-else class="project-cover-fallback">
                <RiCodeBoxLine />
              </div>
              <div v-if="project.featured && viewMode === 'grid'" class="project-featured-tag">
                推荐
              </div>
              <div class="project-item-overlay">
                <div class="project-overlay-actions">
                  <button
                    type="button"
                    class="project-overlay-action is-edit"
                    :aria-label="`编辑 ${project.title || '项目'}`"
                    @click="openEditModal(project)"
                  >
                    <RiEditLine />
                    <span>编辑</span>
                  </button>
                  <button
                    type="button"
                    class="project-overlay-action is-danger"
                    :aria-label="`删除 ${project.title || '项目'}`"
                    @click="deleteProject(project)"
                  >
                    <RiDeleteBinLine />
                    <span>删除</span>
                  </button>
                </div>
              </div>
            </div>

            <!-- 信息区域 -->
            <div class="project-info-box">
              <div class="project-info-header">
                <div class="project-title-line">
                  <div class="project-title-group">
                    <h3 class="project-title" :title="project.title">{{ project.title }}</h3>
                    <span v-if="viewMode === 'list' && project.slug" class="project-slug">
                      /{{ project.slug }}
                    </span>
                  </div>
                  <div v-if="viewMode === 'list'" class="project-list-badges">
                    <span :class="['project-status-badge', statusClass(project.status)]">
                      <span class="status-badge-dot"></span>
                      {{ statusLabel(project.status) }}
                    </span>
                    <span v-if="project.featured" class="project-featured-badge">推荐</span>
                  </div>
                  <span v-else :class="['status-dot', statusClass(project.status)]"></span>
                </div>
                <p class="project-summary" :title="project.summary">
                  {{ project.summary || '暂无简介' }}
                </p>
              </div>

              <div v-if="viewMode === 'grid'" class="project-info-footer">
                <div class="project-meta-line">
                  <span class="meta-tag">{{ optionLabel(platformOptions, project.platform) }}</span>
                  <span class="meta-divider">·</span>
                  <span class="meta-tag">{{ optionLabel(typeOptions, project.type) }}</span>
                </div>
                <div v-if="project.techStacks?.length" class="project-stacks">
                  <span v-for="stack in project.techStacks.slice(0, 3)" :key="stack">
                    {{ stack }}
                  </span>
                  <span v-if="project.techStacks.length > 3" class="more-stacks">...</span>
                </div>
              </div>

              <div v-else class="project-list-detail-panel">
                <div class="project-list-meta-grid">
                  <div class="project-list-meta-block">
                    <span class="project-list-meta-label">平台</span>
                    <span class="project-list-pill is-platform">
                      {{ optionLabel(platformOptions, project.platform) }}
                    </span>
                  </div>
                  <div class="project-list-meta-block">
                    <span class="project-list-meta-label">分类</span>
                    <span class="project-list-pill is-type">
                      {{ optionLabel(typeOptions, project.type) }}
                    </span>
                  </div>
                  <div class="project-list-meta-block">
                    <span class="project-list-meta-label">来源</span>
                    <span class="project-list-source">
                      <RiGithubFill v-if="isGithubRepository(project)" />
                      {{ repositoryLabel(project) }}
                    </span>
                  </div>
                  <div class="project-list-meta-block">
                    <span class="project-list-meta-label">优先级</span>
                    <span class="project-list-priority">{{ project.priority ?? 0 }}</span>
                  </div>
                </div>

                <div
                  v-if="project.techStacks?.length || project.tags?.length"
                  class="project-list-chip-rows"
                >
                  <div v-if="project.techStacks?.length" class="project-list-chip-row">
                    <span class="project-list-chip-label">技术栈</span>
                    <div class="project-list-chips">
                      <span
                        v-for="stack in project.techStacks.slice(0, 5)"
                        :key="stack"
                        class="project-list-chip is-stack"
                      >
                        {{ stack }}
                      </span>
                      <span v-if="project.techStacks.length > 5" class="project-list-chip is-more">
                        +{{ project.techStacks.length - 5 }}
                      </span>
                    </div>
                  </div>
                  <div v-if="project.tags?.length" class="project-list-chip-row">
                    <span class="project-list-chip-label">标签</span>
                    <div class="project-list-chips">
                      <span
                        v-for="tag in project.tags.slice(0, 5)"
                        :key="tag"
                        class="project-list-chip is-tag"
                      >
                        {{ tag }}
                      </span>
                      <span v-if="project.tags.length > 5" class="project-list-chip is-more">
                        +{{ project.tags.length - 5 }}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            </div>

            <!-- 列表视图下的操作按钮 -->
            <div v-if="viewMode === 'list'" class="project-list-actions">
              <a
                v-if="projectExternalUrl(project)"
                :href="projectExternalUrl(project)"
                class="project-list-action-button is-link"
                target="_blank"
                rel="noopener noreferrer"
              >
                <RiExternalLinkLine />
                访问
              </a>
              <button
                type="button"
                class="project-list-action-button is-edit"
                @click="openEditModal(project)"
              >
                <RiEditLine />
                编辑
              </button>
              <button
                type="button"
                class="project-list-action-button is-danger"
                @click="deleteProject(project)"
              >
                <RiDeleteBinLine />
                删除
              </button>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <VPagination
          v-model:page="page"
          v-model:size="size"
          :total="total"
          :size-options="[12, 24, 48, 96]"
          @change="fetchProjects"
        />
      </template>
    </VCard>
  </div>

  <VModal
    v-model:visible="modalVisible"
    :title="editing ? '编辑项目' : '新增项目'"
    :width="860"
    :mount-to-body="true"
  >
    <div class="portfolio-admin-form">
      <!-- 基本信息 -->
      <div class="portfolio-admin-form-section">
        <h3 class="section-title">核心信息</h3>
        <div class="section-grid">
          <label class="is-required">
            <span>项目标题</span>
            <input
              v-model="form.title"
              class="portfolio-admin-input"
              placeholder="例如：Halo Portfolio"
            />
          </label>
          <label class="is-required">
            <span>Slug (唯一标识)</span>
            <input
              v-model="form.slug"
              class="portfolio-admin-input"
              placeholder="例如：halo-portfolio"
              :disabled="editing"
            />
          </label>
          <label class="portfolio-admin-form-wide">
            <span>项目摘要</span>
            <textarea
              v-model="form.summary"
              class="portfolio-admin-textarea"
              rows="2"
              placeholder="一句话说明这个项目解决什么问题"
            />
          </label>
          <label class="portfolio-admin-form-wide">
            <span>详情内容 (Markdown)</span>
            <textarea
              v-model="form.content"
              class="portfolio-admin-textarea is-content"
              rows="5"
              placeholder="支持保存 Markdown 文本，默认页面按纯文本展示"
            />
          </label>
        </div>
      </div>

      <!-- 展示与分类 -->
      <div class="portfolio-admin-form-section">
        <h3 class="section-title">展示与分类</h3>
        <div class="section-grid">
          <label class="portfolio-admin-form-wide">
            <span>封面地址</span>
            <div class="portfolio-admin-cover-field">
              <div class="portfolio-admin-cover-preview">
                <img v-if="form.cover" :src="form.cover" alt="封面预览" />
                <RiCodeBoxLine v-else />
              </div>
              <div class="portfolio-admin-cover-control">
                <input
                  v-model="form.cover"
                  class="portfolio-admin-input"
                  placeholder="https://example.com/cover.png"
                />
                <div class="portfolio-admin-cover-actions">
                  <VButton type="secondary" size="sm" @click="coverPickerVisible = true">
                    <template #icon>
                      <RiImageAddLine />
                    </template>
                    从附件选择
                  </VButton>
                  <VButton
                    v-if="form.cover"
                    type="default"
                    size="sm"
                    class="portfolio-admin-cover-clear"
                    @click="form.cover = ''"
                  >
                    清空
                  </VButton>
                </div>
              </div>
            </div>
          </label>
          <label>
            <span>平台</span>
            <select v-model="form.platform" class="portfolio-admin-select">
              <option v-if="optionsLoading" value="">加载中...</option>
              <option v-for="item in platformOptions" :key="item.value" :value="item.value">
                {{ item.label }}
              </option>
            </select>
          </label>
          <label>
            <span>类型</span>
            <select v-model="form.type" class="portfolio-admin-select">
              <option v-if="optionsLoading" value="">加载中...</option>
              <option v-for="item in typeOptions" :key="item.value" :value="item.value">
                {{ item.label }}
              </option>
            </select>
          </label>
          <label>
            <span>状态</span>
            <select v-model="form.status" class="portfolio-admin-select">
              <option v-for="item in statusOptions" :key="item.value" :value="item.value">
                {{ item.label }}
              </option>
            </select>
          </label>
          <label>
            <span>排序优先级</span>
            <input v-model.number="form.priority" class="portfolio-admin-input" type="number" />
          </label>
          <label class="portfolio-admin-form-wide">
            <span>技术栈 (英文逗号分隔)</span>
            <input
              v-model="techStacksText"
              class="portfolio-admin-input"
              placeholder="Java, Vue, Halo"
            />
          </label>
          <label class="portfolio-admin-form-wide">
            <span>标签 (英文逗号分隔)</span>
            <input v-model="tagsText" class="portfolio-admin-input" placeholder="开源, 插件" />
          </label>
        </div>
      </div>

      <!-- 链接与同步 -->
      <div class="portfolio-admin-form-section">
        <h3 class="section-title">链接与同步</h3>
        <div class="section-grid">
          <label>
            <span>演示地址</span>
            <input v-model="form.demoUrl" class="portfolio-admin-input" placeholder="https://..." />
          </label>
          <label>
            <span>文档地址</span>
            <input v-model="form.docsUrl" class="portfolio-admin-input" placeholder="https://..." />
          </label>
          <label class="portfolio-admin-form-wide">
            <span>代码仓库</span>
            <input
              v-model="form.repoUrl"
              class="portfolio-admin-input"
              placeholder="https://github.com/username/repo"
            />
          </label>
          <label>
            <span>仓库所有者</span>
            <input
              v-model="form.repoOwner"
              class="portfolio-admin-input"
              placeholder="例如：halo-dev"
            />
          </label>
          <label>
            <span>仓库名称</span>
            <input
              v-model="form.repoName"
              class="portfolio-admin-input"
              placeholder="例如：halo-portfolio"
            />
          </label>
        </div>
      </div>

      <!-- 附加选项 -->
      <div class="portfolio-admin-form-section is-last">
        <div class="portfolio-admin-checkbox-group">
          <label class="portfolio-admin-checkbox">
            <input v-model="form.featured" type="checkbox" />
            <div class="checkbox-content">
              <span class="checkbox-label">推荐展示</span>
              <span class="checkbox-desc">勾选后该项目将在首页“推荐”位置优先展示。</span>
            </div>
          </label>
        </div>
      </div>
    </div>
    <template #footer>
      <div class="portfolio-admin-modal-actions">
        <VButton type="secondary" @click="modalVisible = false">
          <span class="portfolio-admin-button-content">取消</span>
        </VButton>
        <VButton type="primary" @click="submitForm">
          <span class="portfolio-admin-button-content">保存</span>
        </VButton>
      </div>
    </template>
  </VModal>

  <AttachmentSelectorModal
    v-if="coverPickerVisible"
    :accepts="['image/*']"
    :min="1"
    :max="1"
    @close="coverPickerVisible = false"
    @select="handleCoverSelect"
  />
</template>

<style scoped lang="scss">
.portfolio-header-action-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.375rem;
  height: 2rem;
  padding: 0 0.75rem;
  border: 1px solid #d7dee8;
  border-radius: 6px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(248, 250, 252, 0.96)), #fff;
  color: #344054;
  cursor: pointer;
  font-size: 0.8125rem;
  font-weight: 600;
  line-height: 1;
  box-shadow:
    0 1px 2px rgba(16, 24, 40, 0.05),
    inset 0 1px 0 rgba(255, 255, 255, 0.76);
  transition:
    border-color 0.18s ease,
    background-color 0.18s ease,
    box-shadow 0.18s ease,
    color 0.18s ease,
    transform 0.18s ease;

  svg {
    width: 0.875rem;
    height: 0.875rem;
    color: #475467;
  }

  &:hover {
    border-color: color-mix(in srgb, var(--halo-primary-color, #006aff), #d7dee8 58%);
    background: #fff;
    color: #101828;
    box-shadow:
      0 6px 14px rgba(15, 23, 42, 0.08),
      0 0 0 2px color-mix(in srgb, var(--halo-primary-color, #006aff), transparent 88%);

    svg {
      color: var(--halo-primary-color, #006aff);
    }
  }

  &:active {
    transform: translateY(1px);
    box-shadow:
      0 1px 2px rgba(16, 24, 40, 0.06),
      inset 0 1px 0 rgba(255, 255, 255, 0.7);
  }

  &:focus-visible {
    outline: none;
    border-color: var(--halo-primary-color, #006aff);
    box-shadow: 0 0 0 3px color-mix(in srgb, var(--halo-primary-color, #006aff), transparent 82%);
  }
}

.portfolio-header-primary-button {
  height: 2rem;
  border-radius: 6px;
  font-weight: 600;
  box-shadow:
    0 6px 14px color-mix(in srgb, var(--halo-primary-color, #006aff), transparent 82%),
    inset 0 1px 0 rgba(255, 255, 255, 0.22);
}

.portfolio-manager-container {
  --portfolio-primary: var(--halo-primary-color, #006aff);
  --portfolio-ink: oklch(20% 0.04 250);
  --portfolio-muted: oklch(50% 0.03 250);
  --portfolio-subtle: oklch(70% 0.02 250);
  --portfolio-line: oklch(88% 0.02 250);
  --portfolio-soft: oklch(97% 0.01 250);
  --portfolio-bg: #ffffff;
  --portfolio-focus: color-mix(in srgb, var(--portfolio-primary), transparent 84%);
  --portfolio-success: #12b76a;
  --portfolio-warning: #f79009;
  --portfolio-archived: #667085;
  --portfolio-danger: #d92d20;

  --radius-sm: 6px;
  --radius-md: 10px;
  --radius-lg: 14px;

  --shadow-sm: 0 1px 2px rgba(16, 24, 40, 0.06);
  --shadow-md: 0 4px 12px rgba(16, 24, 40, 0.08);

  display: flex;
  flex-direction: column;
  padding: 1rem 1.5rem;
  background-color: var(--portfolio-bg);
  min-height: 100%;
}

/* 状态页签 */
.portfolio-status-tabs {
  display: flex;
  gap: 1.5rem;
  margin-bottom: 1.25rem;
  border-bottom: 1px solid var(--portfolio-line);
}

.status-tab-item {
  position: relative;
  padding: 0.75rem 0.25rem;
  color: var(--portfolio-muted);
  font-size: 0.9375rem;
  font-weight: 500;
  cursor: pointer;
  transition: color 0.2s;

  &:hover {
    color: var(--portfolio-ink);
  }

  &.active {
    color: var(--portfolio-ink);
    font-weight: 600;

    &::after {
      content: '';
      position: absolute;
      bottom: -1px;
      left: 0;
      width: 100%;
      height: 2px;
      background-color: var(--portfolio-primary);
      border-radius: 2px 2px 0 0;
    }
  }
}

/* 工具栏 */
.portfolio-toolbar {
  display: flex;
  flex: 1 1 100%;
  flex-wrap: wrap;
  justify-content: space-between;
  align-items: center;
  gap: 1rem;
  width: 100%;
  box-sizing: border-box;
  padding: 1rem 1.25rem;
  background: transparent;
}

.toolbar-left {
  flex: 1;
  min-width: 240px;
}

.search-input-wrapper {
  display: flex;
  align-items: center;
  background: #fff;
  border: 1px solid var(--portfolio-line);
  border-radius: var(--radius-sm);
  padding: 0 0.75rem;
  height: 2.25rem;
  width: 100%;
  max-width: 320px;
  transition: border-color 0.2s;

  &:focus-within {
    border-color: var(--portfolio-primary);
  }

  .search-icon {
    color: var(--portfolio-subtle);
    margin-right: 0.5rem;
  }

  input {
    border: none;
    outline: none;
    width: 100%;
    font-size: 0.875rem;
    color: var(--portfolio-ink);

    &::placeholder {
      color: var(--portfolio-subtle);
    }
  }
}

.toolbar-right {
  display: flex;
  align-items: center;
}

.filter-dropdown-group {
  display: flex;
  gap: 0.5rem;
}

.filter-select-wrapper {
  display: flex;
  align-items: center;
  background: #fff;
  border: 1px solid var(--portfolio-line);
  border-radius: var(--radius-sm);
  height: 2.25rem;
  padding: 0 0.5rem;
  transition: border-color 0.2s;

  &:hover {
    border-color: var(--portfolio-subtle);
  }

  .filter-icon {
    color: var(--portfolio-subtle);
    margin-right: 0.25rem;
  }

  select {
    border: none;
    outline: none;
    background: transparent;
    font-size: 0.875rem;
    color: var(--portfolio-ink);
    cursor: pointer;
    appearance: none;
    padding-right: 0.25rem;
  }
}

.view-mode-switcher {
  display: flex;
  gap: 0.375rem;

  .view-mode-button {
    display: flex;
    align-items: center;
    justify-content: center;
    width: 2.125rem;
    height: 2rem;
    border: 1px solid var(--portfolio-line);
    border-radius: var(--radius-sm);
    background: #fff;
    color: var(--portfolio-subtle);
    cursor: pointer;
    transition:
      border-color 0.18s ease,
      color 0.18s ease,
      background-color 0.18s ease,
      box-shadow 0.18s ease;

    &:hover {
      border-color: #c7cfdb;
      color: var(--portfolio-ink);
    }

    &.active {
      border-color: var(--portfolio-primary);
      background: #f0f7ff;
      color: var(--portfolio-ink);
      box-shadow: 0 0 0 2px color-mix(in srgb, var(--portfolio-primary), transparent 86%);
    }
  }
}

.toolbar-icon-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 2.125rem;
  height: 2rem;
  border: 1px solid var(--portfolio-line);
  border-radius: var(--radius-sm);
  background: #fff;
  color: #667085;
  cursor: pointer;
  box-shadow:
    0 1px 2px rgba(16, 24, 40, 0.04),
    inset 0 1px 0 rgba(255, 255, 255, 0.74);
  transition:
    border-color 0.18s ease,
    background-color 0.18s ease,
    box-shadow 0.18s ease,
    color 0.18s ease,
    transform 0.18s ease;

  svg {
    width: 0.875rem;
    height: 0.875rem;
  }

  &:hover {
    border-color: color-mix(in srgb, var(--portfolio-primary), #c7cfdb 68%);
    background: #f7fbff;
    color: var(--portfolio-primary);
    box-shadow:
      0 4px 10px rgba(33, 56, 94, 0.08),
      0 0 0 2px color-mix(in srgb, var(--portfolio-primary), transparent 88%);
  }

  &:active {
    transform: translateY(1px);
    box-shadow:
      0 1px 2px rgba(16, 24, 40, 0.05),
      inset 0 1px 0 rgba(255, 255, 255, 0.7);
  }

  &:focus-visible {
    outline: none;
    border-color: var(--portfolio-primary);
    box-shadow: 0 0 0 3px var(--portfolio-focus);
  }
}

/* 内容区 */
.portfolio-content-area {
  padding: 1.5rem;
  background: #fff;
}

/* 视图模式: 网格 (Grid) */
.project-view.view-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
  gap: 1.5rem;
}

/* 视图模式: 列表 (List) */
.project-view.view-list {
  display: grid;
  gap: 0.875rem;

  .project-item-card {
    position: relative;
    display: grid;
    grid-template-columns: 4.75rem minmax(0, 1fr) 7.5rem;
    align-items: stretch;
    gap: 1rem;
    overflow: hidden;
    padding: 1rem;
    border: 1px solid #dbe3ee;
    border-radius: var(--radius-md);
    background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(248, 250, 252, 0.92)), #fff;
    box-shadow: 0 1px 2px rgba(16, 24, 40, 0.04);

    &::before {
      position: absolute;
      top: 0;
      bottom: 0;
      left: 0;
      width: 3px;
      background: var(--portfolio-warning);
      content: '';
    }

    &.status-published::before {
      background: var(--portfolio-success);
    }

    &.status-archived::before {
      background: var(--portfolio-archived);
    }

    &:last-child {
      border-bottom: none;
    }

    &:hover {
      border-color: color-mix(in srgb, var(--portfolio-primary), #dbe3ee 74%);
      background:
        linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(245, 249, 255, 0.94)), #fff;
      transform: none;
      box-shadow:
        0 10px 24px rgba(33, 56, 94, 0.08),
        0 1px 2px rgba(16, 24, 40, 0.04);
    }

    .project-cover-box {
      width: 4.75rem;
      aspect-ratio: 1;
      border-radius: var(--radius-sm);
      align-self: start;
      border: 1px solid #d8e1ec;
      box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72);
    }

    .project-item-overlay {
      display: none;
    }

    .project-info-box {
      display: grid;
      gap: 0.875rem;
      min-width: 0;
      padding: 0.0625rem 0;
    }

    .project-info-header {
      display: grid;
      gap: 0.4rem;
      min-width: 0;
    }

    .project-title-line {
      align-items: flex-start;
      gap: 0.75rem;
    }

    .project-summary {
      min-height: 0;
      max-width: 72ch;
      -webkit-line-clamp: 2;
    }
  }
}

/* 卡片通用样式 */
.project-item-card {
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid var(--portfolio-line);
  border-radius: var(--radius-md);
  overflow: hidden;
  transition:
    transform 0.2s,
    box-shadow 0.2s;

  &:hover {
    transform: translateY(-2px);
    box-shadow: var(--shadow-md);

    .project-item-overlay {
      opacity: 1;
      pointer-events: auto;
    }

    .project-overlay-actions {
      opacity: 1;
      transform: translateY(0) scale(1);
    }
  }
}

.project-cover-box {
  position: relative;
  aspect-ratio: 16 / 10;
  background: var(--portfolio-soft);
  overflow: hidden;
  border-bottom: 1px solid var(--portfolio-line);
}

.project-cover-img {
  width: 100%;
  height: 100%;
  object-fit: cover;
  transition:
    filter 0.28s ease,
    transform 0.4s;
}

.project-view.view-grid .project-item-card:hover .project-cover-img {
  filter: saturate(0.96) brightness(0.82);
  transform: scale(1.05);
}

.project-cover-fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100%;
  color: var(--portfolio-subtle);
  font-size: 2rem;
}

.project-featured-tag {
  position: absolute;
  top: 0.75rem;
  left: 0.75rem;
  background: rgba(0, 0, 0, 0.6);
  color: #fff;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 0.75rem;
  font-weight: 500;
  backdrop-filter: blur(4px);
}

.project-item-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 1rem;
  background:
    radial-gradient(circle at 50% 35%, rgba(15, 23, 42, 0.18), rgba(15, 23, 42, 0.62)),
    linear-gradient(180deg, rgba(15, 23, 42, 0.08), rgba(15, 23, 42, 0.44));
  opacity: 0;
  pointer-events: none;
  transition: opacity 0.22s ease;
  backdrop-filter: blur(2px) saturate(112%);
}

.project-cover-box:focus-within {
  .project-item-overlay {
    opacity: 1;
    pointer-events: auto;
  }

  .project-overlay-actions {
    opacity: 1;
    transform: translateY(0) scale(1);
  }
}

.project-overlay-actions {
  display: flex;
  gap: 0.5rem;
  padding: 0.45rem;
  border: 1px solid rgba(255, 255, 255, 0.24);
  border-radius: var(--radius-md);
  background: rgba(15, 23, 42, 0.58);
  box-shadow:
    0 14px 32px rgba(15, 23, 42, 0.28),
    inset 0 1px 0 rgba(255, 255, 255, 0.2);
  opacity: 0;
  transform: translateY(0.5rem) scale(0.98);
  transition:
    opacity 0.22s ease,
    transform 0.22s ease;
}

.project-overlay-action {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.375rem;
  min-width: 4.875rem;
  height: 2.125rem;
  padding: 0 0.75rem;
  border: 1px solid rgba(255, 255, 255, 0.28);
  border-radius: var(--radius-sm);
  background: rgba(255, 255, 255, 0.92);
  color: #1d2939;
  cursor: pointer;
  font-size: 0.8125rem;
  font-weight: 600;
  line-height: 1;
  box-shadow:
    0 1px 2px rgba(15, 23, 42, 0.1),
    inset 0 1px 0 rgba(255, 255, 255, 0.75);
  transition:
    background-color 0.18s ease,
    border-color 0.18s ease,
    color 0.18s ease,
    transform 0.18s ease,
    box-shadow 0.18s ease;

  svg {
    width: 0.875rem;
    height: 0.875rem;
    flex-shrink: 0;
  }

  &:hover {
    border-color: rgba(255, 255, 255, 0.66);
    background: #fff;
    color: #101828;
    transform: translateY(-1px);
    box-shadow:
      0 8px 18px rgba(15, 23, 42, 0.18),
      inset 0 1px 0 rgba(255, 255, 255, 0.82);
  }

  &:active {
    transform: translateY(0);
    box-shadow:
      0 1px 2px rgba(15, 23, 42, 0.14),
      inset 0 1px 0 rgba(255, 255, 255, 0.65);
  }

  &:focus-visible {
    outline: none;
    border-color: #fff;
    box-shadow:
      0 0 0 3px rgba(255, 255, 255, 0.38),
      0 0 0 5px color-mix(in srgb, var(--portfolio-primary), transparent 30%);
  }

  &.is-edit {
    color: #1849a9;
  }

  &.is-danger {
    color: #b42318;

    &:hover {
      border-color: rgba(254, 202, 202, 0.92);
      background: #fef3f2;
      color: #912018;
    }
  }
}

.project-info-box {
  padding: 1.25rem;
  display: flex;
  flex-direction: column;
  gap: 0.75rem;
}

.project-title-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 0.5rem;
}

.project-title-group {
  display: flex;
  flex: 1 1 auto;
  flex-wrap: wrap;
  align-items: baseline;
  gap: 0.35rem 0.5rem;
  min-width: 0;
}

.project-title {
  margin: 0;
  font-size: 1rem;
  font-weight: 600;
  color: var(--portfolio-ink);
  min-width: 0;
  max-width: 100%;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.project-slug {
  overflow: hidden;
  max-width: min(22rem, 100%);
  color: #64748b;
  font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
  font-size: 0.75rem;
  font-weight: 500;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  flex-shrink: 0;

  &.status-published {
    background-color: var(--portfolio-success);
  }
  &.status-draft {
    background-color: var(--portfolio-warning);
  }
  &.status-archived {
    background-color: var(--portfolio-archived);
  }
}

.project-list-badges {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 0.375rem;
  flex-shrink: 0;
}

.project-status-badge,
.project-featured-badge {
  display: inline-flex;
  align-items: center;
  gap: 0.35rem;
  height: 1.5rem;
  padding: 0 0.5rem;
  border: 1px solid transparent;
  border-radius: var(--radius-sm);
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1;
  white-space: nowrap;
}

.project-status-badge {
  color: #b54708;
  background: #fffaeb;
  border-color: #fedf89;

  &.status-published {
    color: #067647;
    background: #ecfdf3;
    border-color: #abefc6;
  }

  &.status-archived {
    color: #475467;
    background: #f2f4f7;
    border-color: #d0d5dd;
  }
}

.status-badge-dot {
  width: 0.425rem;
  height: 0.425rem;
  border-radius: 999px;
  background: var(--portfolio-warning);
  box-shadow: 0 0 0 2px rgba(247, 144, 9, 0.14);
}

.project-status-badge.status-published .status-badge-dot {
  background: var(--portfolio-success);
  box-shadow: 0 0 0 2px rgba(18, 183, 106, 0.14);
}

.project-status-badge.status-archived .status-badge-dot {
  background: var(--portfolio-archived);
  box-shadow: 0 0 0 2px rgba(102, 112, 133, 0.14);
}

.project-featured-badge {
  color: #7a2e0e;
  background: #fff4ed;
  border-color: #fed7aa;
}

.project-summary {
  margin: 0;
  font-size: 0.875rem;
  color: #475467;
  line-height: 1.6;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 2.8rem;
}

.project-meta-line {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  font-size: 0.75rem;
  color: var(--portfolio-muted);
}

.meta-divider {
  color: var(--portfolio-line);
}

.project-stacks {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
  margin-top: 0.25rem;

  span {
    background: var(--portfolio-soft);
    color: var(--portfolio-muted);
    font-size: 0.6875rem;
    padding: 2px 6px;
    border-radius: 4px;
    border: 1px solid var(--portfolio-line);
    white-space: nowrap;

    &.more-stacks {
      background: transparent;
      border: none;
      padding: 2px 0;
    }
  }
}

.project-list-detail-panel {
  display: grid;
  gap: 0.75rem;
  min-width: 0;
}

.project-list-meta-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  overflow: hidden;
  border: 1px solid #e4eaf2;
  border-radius: var(--radius-sm);
  background: #f8fafc;
}

.project-list-meta-block {
  display: grid;
  gap: 0.325rem;
  min-width: 0;
  padding: 0.625rem 0.75rem;
  border-right: 1px solid #e4eaf2;

  &:last-child {
    border-right: 0;
  }
}

.project-list-meta-label,
.project-list-chip-label {
  color: #667085;
  font-size: 0.6875rem;
  font-weight: 600;
  line-height: 1;
}

.project-list-pill,
.project-list-source,
.project-list-priority {
  display: inline-flex;
  align-items: center;
  width: fit-content;
  max-width: 100%;
  min-height: 1.5rem;
  min-width: 0;
  overflow: hidden;
  border-radius: 5px;
  font-size: 0.75rem;
  font-weight: 600;
  line-height: 1;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.project-list-pill {
  padding: 0 0.5rem;
  border: 1px solid #b9e6fe;
  color: #026aa2;
  background: #f0f9ff;

  &.is-type {
    border-color: #c7d7fe;
    color: #3538cd;
    background: #eef4ff;
  }
}

.project-list-source {
  gap: 0.35rem;
  color: #344054;

  svg {
    width: 0.875rem;
    height: 0.875rem;
    color: #24292f;
  }
}

.project-list-priority {
  color: #475467;
  font-variant-numeric: tabular-nums;
}

.project-list-chip-rows {
  display: grid;
  gap: 0.5rem;
}

.project-list-chip-row {
  display: grid;
  grid-template-columns: 3rem minmax(0, 1fr);
  align-items: start;
  gap: 0.625rem;
}

.project-list-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 0.375rem;
  min-width: 0;
}

.project-list-chip {
  display: inline-flex;
  align-items: center;
  max-width: 11rem;
  min-height: 1.375rem;
  overflow: hidden;
  padding: 0 0.45rem;
  border: 1px solid #e4e7ec;
  border-radius: 5px;
  background: #fff;
  color: #475467;
  font-size: 0.6875rem;
  font-weight: 500;
  line-height: 1;
  text-overflow: ellipsis;
  white-space: nowrap;

  &.is-stack {
    border-color: #bae6fd;
    color: #0369a1;
    background: #f0f9ff;
  }

  &.is-tag {
    border-color: #bbf7d0;
    color: #15803d;
    background: #f0fdf4;
  }

  &.is-more {
    border-color: #d0d5dd;
    color: #667085;
    background: #f8fafc;
  }
}

.project-list-actions {
  display: grid;
  align-content: start;
  gap: 0.5rem;
  min-width: 7.5rem;
}

.project-list-action-button {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 0.4rem;
  width: 100%;
  height: 2.125rem;
  padding: 0 0.75rem;
  border: 1px solid #d0d5dd;
  border-radius: var(--radius-sm);
  background: #fff;
  color: #344054;
  cursor: pointer;
  font-size: 0.8125rem;
  font-weight: 600;
  line-height: 1;
  text-decoration: none;
  box-shadow: none;
  transition:
    border-color 0.18s ease,
    background-color 0.18s ease,
    color 0.18s ease,
    transform 0.18s ease;

  svg {
    width: 0.875rem;
    height: 0.875rem;
    flex-shrink: 0;
  }

  &:hover {
    border-color: #98a2b3;
    background: #f8fafc;
    color: #101828;
  }

  &:active {
    transform: translateY(1px);
  }

  &:focus-visible {
    outline: none;
    box-shadow: 0 0 0 3px var(--portfolio-focus);
  }

  &.is-link {
    border-color: #b9e6fe;
    color: #026aa2;
    background: #f0f9ff;

    &:hover {
      border-color: #7dd3fc;
      background: #e0f2fe;
      color: #075985;
    }
  }

  &.is-edit {
    border-color: #c7d7fe;
    color: #3538cd;
    background: #eef4ff;

    &:hover {
      border-color: #a4bcfd;
      background: #e0eaff;
      color: #2d31a6;
    }
  }

  &.is-danger {
    border-color: #fecaca;
    color: var(--portfolio-danger);
    background: #fef3f2;

    &:hover {
      border-color: #fda29b;
      background: #fee4e2;
      color: #b42318;
    }
  }
}

/* Modal Form Styles */
.portfolio-admin-form {
  --portfolio-primary: var(--halo-primary-color, #006aff);
  --portfolio-ink: #101828;
  --portfolio-muted: #667085;
  --portfolio-subtle: #98a2b3;
  --portfolio-line: #d0d5dd;
  --portfolio-soft: #f8fafc;
  --portfolio-panel: #ffffff;
  --portfolio-focus: color-mix(in srgb, var(--portfolio-primary), transparent 84%);
  --radius-sm: 6px;
  --radius-md: 10px;

  display: flex;
  flex-direction: column;
  gap: 1rem;
  padding: 0.25rem 0.25rem 0;
  color: var(--portfolio-ink);
}

.portfolio-admin-form-section {
  display: flex;
  flex-direction: column;
  gap: 0.875rem;
  padding: 1rem;
  border: 1px solid #e4e7ec;
  border-radius: var(--radius-md);
  background: #fbfcfe;

  &.is-last {
    padding: 0;
    border: 0;
    background: transparent;
  }
}

.section-title {
  display: flex;
  align-items: center;
  gap: 0.5rem;
  margin: 0;
  font-size: 0.9375rem;
  font-weight: 600;
  color: var(--portfolio-ink);

  &::before {
    width: 3px;
    height: 1rem;
    border-radius: 999px;
    background: var(--portfolio-primary);
    content: '';
  }
}

.section-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 1rem;

  label {
    display: flex;
    flex-direction: column;
    gap: 0.5rem;
    min-width: 0;
    font-size: 0.8125rem;
    color: var(--portfolio-ink);
    font-weight: 600;

    &.is-required span::after {
      content: ' *';
      color: #d92d20;
    }

    > span {
      line-height: 1.25;
    }
  }
}

.portfolio-admin-form-wide {
  grid-column: 1 / -1;
}

.portfolio-admin-cover-field {
  display: grid;
  grid-template-columns: 7rem minmax(0, 1fr);
  gap: 0.875rem;
  align-items: start;
}

.portfolio-admin-cover-preview {
  display: grid;
  place-items: center;
  width: 7rem;
  aspect-ratio: 16 / 10;
  overflow: hidden;
  border: 1px solid var(--portfolio-line);
  border-radius: var(--radius-md);
  background: linear-gradient(180deg, #f8fafc 0%, #eef2f6 100%);
  color: var(--portfolio-subtle);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8);

  img {
    width: 100%;
    height: 100%;
    object-fit: cover;
  }

  svg {
    width: 1.5rem;
    height: 1.5rem;
  }
}

.portfolio-admin-cover-control {
  display: grid;
  gap: 0.625rem;
}

.portfolio-admin-cover-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 0.5rem;
}

.portfolio-admin-cover-clear {
  border-color: #d0d5dd !important;
  background: #fff !important;
  color: #526071 !important;
  box-shadow:
    0 1px 2px rgba(16, 24, 40, 0.05),
    inset 0 1px 0 rgba(255, 255, 255, 0.7);

  &:hover {
    border-color: #98a2b3 !important;
    background: #f8fafc !important;
    color: #101828 !important;
  }

  &:active {
    transform: translateY(1px);
  }
}

.portfolio-admin-input,
.portfolio-admin-select,
.portfolio-admin-textarea {
  width: 100%;
  border: 1px solid var(--portfolio-line);
  border-radius: var(--radius-sm);
  background: var(--portfolio-panel);
  color: var(--portfolio-ink);
  font-size: 0.875rem;
  line-height: 1.5;
  outline: none;
  box-shadow:
    0 1px 2px rgba(16, 24, 40, 0.05),
    inset 0 1px 0 rgba(255, 255, 255, 0.7);
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    background-color 0.18s ease;

  &::placeholder {
    color: #8a9ab0;
  }

  &:hover {
    border-color: #98a2b3;
  }

  &:focus {
    border-color: var(--portfolio-primary);
    background: #fff;
    box-shadow:
      0 0 0 3px var(--portfolio-focus),
      0 1px 2px rgba(16, 24, 40, 0.05);
  }

  &:disabled {
    border-color: #e4e7ec;
    background: #f2f4f7;
    color: #98a2b3;
    cursor: not-allowed;
  }
}

.portfolio-admin-input,
.portfolio-admin-select {
  height: 2.5rem;
  padding: 0 0.75rem;
}

.portfolio-admin-select {
  cursor: pointer;
}

.portfolio-admin-textarea {
  padding: 0.625rem 0.75rem;
  resize: vertical;
  min-height: 5rem;

  &.is-content {
    min-height: 8.5rem;
    font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, monospace;
    font-size: 0.8125rem;
  }
}

.portfolio-admin-checkbox {
  display: flex;
  align-items: flex-start;
  gap: 0.75rem;
  padding: 0.875rem 1rem;
  border: 1px solid #e4e7ec;
  border-radius: var(--radius-md);
  background: #fff;
  cursor: pointer;
  transition:
    border-color 0.18s ease,
    background-color 0.18s ease;

  &:hover {
    border-color: var(--portfolio-line);
    background: #fbfcfe;
  }

  input[type='checkbox'] {
    margin-top: 0.25rem;
    cursor: pointer;
    accent-color: var(--portfolio-primary);
  }

  .checkbox-content {
    display: flex;
    flex-direction: column;
  }

  .checkbox-label {
    font-weight: 500;
    font-size: 0.875rem;
    color: var(--portfolio-ink);
  }

  .checkbox-desc {
    font-size: 0.75rem;
    color: var(--portfolio-muted);
    margin-top: 0.25rem;
  }
}

.portfolio-project-card :deep(.card__header) {
  padding: 0;
  border-bottom: 1px solid var(--portfolio-line);
  border-radius: var(--radius-md) var(--radius-md) 0 0;
  background:
    linear-gradient(180deg, rgba(248, 250, 252, 0.98), rgba(244, 247, 251, 0.98)), #f8fafc;
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72);
}

.portfolio-project-card :deep(.card__footer) {
  padding: 1rem 1.25rem;
  border-top: 1px solid var(--portfolio-line);
  background: #fff;
}

:deep(.modal-footer) {
  padding: 1rem 1.25rem;
}

.portfolio-admin-modal-actions {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  gap: 0.875rem;
  width: 100%;
}

.portfolio-admin-modal-actions :deep(.btn) {
  min-width: 4.875rem;
  height: 2.5rem;
}

.portfolio-admin-modal-actions :deep(.btn-secondary) {
  background: #fff;
}

.portfolio-admin-modal-actions :deep(.btn-primary) {
  box-shadow:
    0 1px 2px rgba(16, 24, 40, 0.08),
    inset 0 1px 0 rgba(255, 255, 255, 0.2);
}

@media (max-width: 768px) {
  .portfolio-manager-container {
    padding: 0.75rem;
  }

  .toolbar-left,
  .toolbar-right {
    width: 100%;
  }

  .search-input-wrapper {
    max-width: none;
  }

  .filter-dropdown-group {
    width: 100%;
  }

  .filter-select-wrapper {
    flex: 1 1 0;
  }

  .project-view.view-list {
    .project-item-card {
      grid-template-columns: 4rem minmax(0, 1fr);
      gap: 0.875rem;
      padding: 0.875rem;

      .project-cover-box {
        width: 4rem;
      }
    }
  }

  .project-list-actions {
    grid-column: 1 / -1;
    grid-template-columns: repeat(3, minmax(0, 1fr));
    min-width: 0;
  }

  .project-list-meta-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }

  .project-list-meta-block:nth-child(2n) {
    border-right: 0;
  }

  .section-grid {
    grid-template-columns: 1fr;
  }

  .portfolio-admin-cover-field {
    grid-template-columns: 1fr;
  }

  .portfolio-admin-cover-preview {
    width: 100%;
  }
}

@media (max-width: 520px) {
  .project-view.view-list {
    .project-item-card {
      grid-template-columns: 1fr;
    }

    .project-cover-box {
      width: 100% !important;
      aspect-ratio: 16 / 9;
    }
  }

  .project-title-line,
  .project-list-badges {
    justify-content: flex-start;
  }

  .project-list-meta-grid,
  .project-list-actions {
    grid-template-columns: 1fr;
  }

  .project-list-meta-block {
    border-right: 0;
    border-bottom: 1px solid #e4eaf2;

    &:last-child {
      border-bottom: 0;
    }
  }

  .project-list-chip-row {
    grid-template-columns: 1fr;
  }
}
</style>
