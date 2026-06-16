<script setup lang="ts">
import { portfolioApi, type Project } from '@/api/portfolio'
import { NodeViewWrapper } from '@halo-dev/richtext-editor'
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import RiArrowDownSLine from '~icons/ri/arrow-down-s-line'
import RiCheckLine from '~icons/ri/check-line'
import RiCloseLine from '~icons/ri/close-line'
import RiCodeBoxLine from '~icons/ri/code-box-line'
import RiSearchLine from '~icons/ri/search-line'

type ProjectCardAttrs = {
  slug?: string
}

const props = defineProps<{
  node: {
    attrs?: ProjectCardAttrs
  }
  updateAttributes: (attrs: ProjectCardAttrs) => void
}>()

const loading = ref(false)
const loadError = ref('')
const keyword = ref('')
const pickerOpen = ref(false)
const selectedSlug = ref(props.node?.attrs?.slug || '')
const projects = ref<Project[]>([])
const projectIndex = ref(new Map<string, Project>())
const pickerRoot = ref<HTMLElement>()
const searchInput = ref<HTMLInputElement>()

let searchTimer: ReturnType<typeof setTimeout> | undefined
let requestId = 0

const selectedProject = computed(() =>
  selectedSlug.value ? projectIndex.value.get(selectedSlug.value) : undefined,
)

const selectionLabel = computed(() => {
  return selectedProject.value?.title || selectedSlug.value || '选择项目'
})

const selectionSummary = computed(() => {
  if (selectedProject.value?.summary) {
    return selectedProject.value.summary
  }
  if (selectedSlug.value) {
    return '已选择项目，正在等待项目详情同步'
  }
  return '插入一个已发布项目的展示卡片'
})

const selectionMeta = computed(() => {
  const project = selectedProject.value
  if (!project) {
    return []
  }
  return [project.platform, project.type, ...(project.techStacks || []).slice(0, 3)]
    .map((item) => item?.trim())
    .filter(Boolean) as string[]
})

const hasProjects = computed(() => projects.value.length > 0)
const hasSelection = computed(() => Boolean(selectedSlug.value))

function rememberProjects(items: Project[]) {
  const nextIndex = new Map(projectIndex.value)
  for (const project of items) {
    if (project.slug) {
      nextIndex.set(project.slug, project)
    }
  }
  projectIndex.value = nextIndex
}

async function fetchProjects(query = keyword.value.trim()) {
  const currentRequestId = ++requestId
  loading.value = true
  loadError.value = ''
  try {
    const { data } = await portfolioApi.list({
      page: 1,
      size: 20,
      keyword: query || undefined,
      status: 'published',
    })
    if (currentRequestId !== requestId) {
      return
    }
    const items = data.items || []
    projects.value = items
    rememberProjects(items)
  } catch (error) {
    if (currentRequestId !== requestId) {
      return
    }
    loadError.value = '项目加载失败，请稍后重试'
    console.error('Failed to load portfolio projects for editor card:', error)
  } finally {
    if (currentRequestId === requestId) {
      loading.value = false
    }
  }
}

async function hydrateSelectedProject(slug: string) {
  if (!slug || projectIndex.value.has(slug)) {
    return
  }
  try {
    const { data } = await portfolioApi.list({
      page: 1,
      size: 1,
      keyword: slug,
      status: 'published',
    })
    rememberProjects(data.items || [])
  } catch (error) {
    console.error('Failed to hydrate selected portfolio project:', error)
  }
}

async function openPicker() {
  pickerOpen.value = true
  if (!projects.value.length) {
    void fetchProjects()
  }
  await nextTick()
  searchInput.value?.focus()
}

function closePicker() {
  pickerOpen.value = false
}

function togglePicker() {
  if (pickerOpen.value) {
    closePicker()
    return
  }
  void openPicker()
}

function selectProject(project: Project) {
  if (!project.slug) {
    return
  }
  rememberProjects([project])
  selectedSlug.value = project.slug
  props.updateAttributes({ slug: project.slug })
  closePicker()
}

function clearSelection() {
  selectedSlug.value = ''
  props.updateAttributes({ slug: '' })
}

function handleDocumentPointerDown(event: PointerEvent) {
  const target = event.target
  if (target instanceof Node && pickerRoot.value && !pickerRoot.value.contains(target)) {
    closePicker()
  }
}

watch(
  () => props.node?.attrs?.slug,
  (slug) => {
    const nextSlug = slug || ''
    if (selectedSlug.value !== nextSlug) {
      selectedSlug.value = nextSlug
    }
    if (nextSlug) {
      void hydrateSelectedProject(nextSlug)
    }
  },
)

watch(keyword, () => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  searchTimer = setTimeout(() => {
    void fetchProjects()
  }, 220)
})

watch(pickerOpen, (open) => {
  if (open) {
    document.addEventListener('pointerdown', handleDocumentPointerDown)
    return
  }
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
})

onMounted(() => {
  void fetchProjects()
  if (selectedSlug.value) {
    void hydrateSelectedProject(selectedSlug.value)
  }
})

onBeforeUnmount(() => {
  if (searchTimer) {
    clearTimeout(searchTimer)
  }
  document.removeEventListener('pointerdown', handleDocumentPointerDown)
})
</script>

<template>
  <NodeViewWrapper as="section" class="portfolio-project-card-node" contenteditable="false">
    <div class="portfolio-project-card-node__preview" :class="{ 'is-empty': !hasSelection }">
      <div class="portfolio-project-card-node__cover" :class="{ 'has-image': selectedProject?.cover }">
        <img
          v-if="selectedProject?.cover"
          :src="selectedProject.cover"
          :alt="selectedProject.title || selectedProject.slug"
        />
        <div v-else class="portfolio-project-card-node__cover-placeholder">
          <RiCodeBoxLine />
        </div>
      </div>

      <div class="portfolio-project-card-node__body">
        <div class="portfolio-project-card-node__eyebrow">
          <span>Portfolio project</span>
          <span v-if="hasSelection" class="portfolio-project-card-node__status">已选择</span>
        </div>
        <strong :title="selectionLabel">{{ selectionLabel }}</strong>
        <p>{{ selectionSummary }}</p>
        <div v-if="selectionMeta.length" class="portfolio-project-card-node__meta">
          <span v-for="item in selectionMeta" :key="item">{{ item }}</span>
        </div>
      </div>
    </div>

    <div ref="pickerRoot" class="portfolio-project-picker">
      <div class="portfolio-project-picker__trigger-wrap">
        <button
          type="button"
          class="portfolio-project-picker__trigger"
          :class="{ 'is-open': pickerOpen, 'has-selection': hasSelection }"
          :aria-expanded="pickerOpen"
          @click="togglePicker"
        >
          <RiSearchLine />
          <span>{{ selectedProject?.title || selectedSlug || '搜索并选择已发布项目' }}</span>
          <RiArrowDownSLine class="portfolio-project-picker__chevron" />
        </button>
        <button
          v-if="hasSelection"
          type="button"
          class="portfolio-project-picker__clear"
          aria-label="清除项目选择"
          @click.stop="clearSelection"
        >
          <RiCloseLine />
        </button>
      </div>

      <div v-if="pickerOpen" class="portfolio-project-picker__panel">
        <div class="portfolio-project-picker__search">
          <RiSearchLine />
          <input
            ref="searchInput"
            v-model="keyword"
            type="text"
            placeholder="输入标题、slug 或摘要"
          />
        </div>

        <div class="portfolio-project-picker__list" role="listbox">
          <div v-if="loading" class="portfolio-project-picker__skeletons">
            <span v-for="item in 4" :key="item"></span>
          </div>

          <div v-else-if="loadError" class="portfolio-project-picker__state is-error">
            {{ loadError }}
          </div>

          <div v-else-if="!hasProjects" class="portfolio-project-picker__state">
            没有找到已发布项目
          </div>

          <button
            v-for="project in projects"
            v-else
            :key="project.slug"
            type="button"
            role="option"
            class="portfolio-project-picker__option"
            :class="{ 'is-selected': project.slug === selectedSlug }"
            @click="selectProject(project)"
          >
            <span class="portfolio-project-picker__option-cover">
              <img v-if="project.cover" :src="project.cover" :alt="project.title || project.slug" />
              <RiCodeBoxLine v-else />
            </span>
            <span class="portfolio-project-picker__option-main">
              <strong>{{ project.title || project.slug }}</strong>
              <span>{{ project.summary || project.slug }}</span>
            </span>
            <RiCheckLine v-if="project.slug === selectedSlug" />
          </button>
        </div>
      </div>
    </div>
  </NodeViewWrapper>
</template>

<style scoped>
.portfolio-project-card-node {
  position: relative;
  display: block;
  margin: 12px 0;
  overflow: visible;
  border: 1px solid #dde5ef;
  border-radius: 8px;
  background: #ffffff;
  box-shadow: 0 18px 38px -34px rgba(15, 23, 42, 0.66);
}

.portfolio-project-card-node__preview {
  display: grid;
  grid-template-columns: minmax(148px, 192px) minmax(0, 1fr);
  gap: 16px;
  align-items: center;
  min-height: 0;
  border-radius: 8px 8px 0 0;
  padding: 14px;
  background:
    radial-gradient(circle at 0 0, rgba(37, 99, 235, 0.06), transparent 38%),
    linear-gradient(180deg, #ffffff 0%, #f8fafc 100%);
}

.portfolio-project-card-node__preview.is-empty {
  grid-template-columns: 132px minmax(0, 1fr);
}

.portfolio-project-card-node__cover {
  position: relative;
  overflow: hidden;
  width: 100%;
  aspect-ratio: 16 / 10;
  border: 1px solid #e2e8f0;
  border-radius: 7px;
  background: #eef2f7;
  box-shadow: 0 16px 30px -28px rgba(15, 23, 42, 0.72);
}

.portfolio-project-card-node__cover.has-image::after {
  position: absolute;
  inset: 0;
  border: 1px solid rgba(255, 255, 255, 0.58);
  border-radius: inherit;
  content: '';
  pointer-events: none;
}

.portfolio-project-card-node__cover img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.portfolio-project-card-node__cover-placeholder {
  display: flex;
  height: 100%;
  align-items: center;
  justify-content: center;
  background:
    linear-gradient(135deg, rgba(255, 255, 255, 0.82), rgba(255, 255, 255, 0) 48%),
    repeating-linear-gradient(135deg, #eef2f7 0, #eef2f7 10px, #e5ebf3 10px, #e5ebf3 20px);
  color: #64748b;
}

.portfolio-project-card-node__cover-placeholder svg {
  width: 34px;
  height: 34px;
}

.portfolio-project-card-node__body {
  display: flex;
  min-width: 0;
  flex-direction: column;
  justify-content: center;
  padding: 4px 2px;
}

.portfolio-project-card-node__eyebrow {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-bottom: 7px;
  color: #64748b;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0.06em;
  line-height: 1.2;
  text-transform: uppercase;
}

.portfolio-project-card-node__status {
  flex: 0 0 auto;
  border-radius: 5px;
  padding: 3px 6px;
  background: #ecfdf3;
  color: #067647;
  font-size: 11px;
  font-weight: 700;
  letter-spacing: 0;
  text-transform: none;
}

.portfolio-project-card-node__body strong {
  overflow: hidden;
  color: #111827;
  font-size: 19px;
  font-weight: 750;
  line-height: 1.28;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.portfolio-project-card-node__body p {
  display: -webkit-box;
  margin: 7px 0 0;
  overflow: hidden;
  max-width: 64ch;
  color: #475569;
  font-size: 13px;
  line-height: 1.62;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
}

.portfolio-project-card-node__meta {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 12px;
}

.portfolio-project-card-node__meta span {
  display: inline-flex;
  align-items: center;
  border-radius: 5px;
  padding: 4px 7px;
  background: #eef4ff;
  color: #344054;
  font-size: 12px;
  font-weight: 600;
  line-height: 1.4;
}

.portfolio-project-picker {
  position: relative;
  border-top: 1px solid #edf1f6;
  padding: 10px;
  background: #fbfcfe;
  border-radius: 0 0 8px 8px;
}

.portfolio-project-picker__trigger-wrap {
  position: relative;
}

.portfolio-project-picker__trigger {
  display: grid;
  width: 100%;
  min-height: 38px;
  grid-template-columns: 18px minmax(0, 1fr) 18px;
  align-items: center;
  gap: 8px;
  border: 1px solid #d4dce8;
  border-radius: 7px;
  background: #ffffff;
  color: #667085;
  cursor: pointer;
  font-size: 13px;
  font-weight: 500;
  line-height: 1.4;
  padding: 0 10px;
  text-align: left;
  transition:
    border-color 0.18s ease,
    box-shadow 0.18s ease,
    transform 0.18s ease;
}

.portfolio-project-picker__trigger.has-selection {
  padding-right: 40px;
}

.portfolio-project-picker__trigger:hover {
  border-color: #b8c4d4;
  box-shadow: 0 8px 22px -20px rgba(15, 23, 42, 0.8);
}

.portfolio-project-picker__trigger:active {
  transform: translateY(1px);
}

.portfolio-project-picker__trigger.is-open {
  border-color: #2563eb;
  box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12);
}

.portfolio-project-picker__trigger.has-selection {
  color: #111827;
}

.portfolio-project-picker__trigger > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.portfolio-project-picker__clear {
  position: absolute;
  top: 7px;
  right: 34px;
  display: inline-flex;
  width: 24px;
  height: 24px;
  align-items: center;
  justify-content: center;
  border: 0;
  border-radius: 5px;
  background: #f2f4f7;
  color: #667085;
  cursor: pointer;
  padding: 0;
}

.portfolio-project-picker__clear:hover {
  background: #e4e7ec;
  color: #344054;
}

.portfolio-project-picker__chevron {
  transition: transform 0.18s ease;
}

.portfolio-project-picker__trigger.is-open .portfolio-project-picker__chevron {
  transform: rotate(180deg);
}

.portfolio-project-picker__panel {
  position: absolute;
  z-index: 40;
  right: 10px;
  bottom: calc(100% + 8px);
  left: 10px;
  overflow: hidden;
  border: 1px solid #d9e0ea;
  border-radius: 8px;
  background: #ffffff;
  box-shadow:
    0 22px 54px -30px rgba(15, 23, 42, 0.68),
    0 0 0 1px rgba(255, 255, 255, 0.72) inset;
}

.portfolio-project-picker__search {
  display: grid;
  grid-template-columns: 18px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  border-bottom: 1px solid #edf1f6;
  padding: 10px 12px;
  color: #667085;
}

.portfolio-project-picker__search input {
  width: 100%;
  border: 0;
  background: transparent;
  color: #111827;
  font-size: 13px;
  line-height: 1.5;
  outline: none;
}

.portfolio-project-picker__search input::placeholder {
  color: #98a2b3;
}

.portfolio-project-picker__list {
  max-height: 280px;
  overflow-y: auto;
  padding: 6px;
}

.portfolio-project-picker__option {
  display: grid;
  width: 100%;
  grid-template-columns: 42px minmax(0, 1fr) 18px;
  align-items: center;
  gap: 10px;
  border: 0;
  border-radius: 7px;
  background: transparent;
  cursor: pointer;
  padding: 8px;
  text-align: left;
  transition:
    background-color 0.16s ease,
    color 0.16s ease;
}

.portfolio-project-picker__option:hover,
.portfolio-project-picker__option.is-selected {
  background: #f1f5f9;
}

.portfolio-project-picker__option-cover {
  display: flex;
  width: 42px;
  height: 42px;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 6px;
  background: #e9eef5;
  color: #64748b;
}

.portfolio-project-picker__option-cover img {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.portfolio-project-picker__option-main {
  display: flex;
  min-width: 0;
  flex-direction: column;
  gap: 3px;
}

.portfolio-project-picker__option-main strong,
.portfolio-project-picker__option-main span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.portfolio-project-picker__option-main strong {
  color: #111827;
  font-size: 13px;
  font-weight: 650;
  line-height: 1.35;
}

.portfolio-project-picker__option-main span {
  color: #667085;
  font-size: 12px;
  line-height: 1.35;
}

.portfolio-project-picker__option > svg:last-child {
  color: #2563eb;
}

.portfolio-project-picker__state {
  padding: 22px 12px;
  color: #667085;
  font-size: 13px;
  line-height: 1.6;
  text-align: center;
}

.portfolio-project-picker__state.is-error {
  color: #b42318;
}

.portfolio-project-picker__skeletons {
  display: grid;
  gap: 6px;
  padding: 4px;
}

.portfolio-project-picker__skeletons span {
  height: 50px;
  border-radius: 7px;
  background: linear-gradient(90deg, #f2f4f7 0%, #e4e7ec 48%, #f2f4f7 100%);
  background-size: 240% 100%;
  animation: portfolio-project-picker-loading 1.1s ease-in-out infinite;
}

@keyframes portfolio-project-picker-loading {
  to {
    background-position: -240% 0;
  }
}

@media (max-width: 640px) {
  .portfolio-project-card-node__preview {
    grid-template-columns: 1fr;
  }

  .portfolio-project-card-node__cover {
    max-width: none;
  }
}
</style>
