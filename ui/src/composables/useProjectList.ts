import { computed, onScopeDispose, reactive, ref, watch } from 'vue'
import type { Project, ProjectList, ProjectQuery, ProjectStatus } from '../api/portfolio'

type FetchProjects = (
  query: ProjectQuery,
) => Promise<{ data: Pick<ProjectList, 'items' | 'total'> }>

export function useProjectList(fetchProjects: FetchProjects) {
  const projects = ref<Project[]>([])
  const total = ref(0)
  const page = ref(1)
  const size = ref(12)
  const loading = ref(false)
  const error = ref('')
  const filters = reactive({
    keyword: '',
    platform: '',
    type: '',
    status: '' as ProjectStatus | '',
    tag: '',
    featured: '',
    sort: '',
  })

  const query = computed<ProjectQuery>(() => ({
    page: page.value,
    size: size.value,
    keyword: filters.keyword.trim() || undefined,
    platform: filters.platform || undefined,
    type: filters.type || undefined,
    status: filters.status || undefined,
    tag: filters.tag.trim() || undefined,
    featured: filters.featured ? filters.featured === 'true' : undefined,
    sort: filters.sort ? [filters.sort] : undefined,
  }))

  let requestId = 0
  let pending: { key: string; promise: Promise<void> } | undefined

  function refresh(force = false): Promise<void> {
    const params = query.value
    const key = JSON.stringify(params)
    if (!force && pending?.key === key) return pending.promise

    const currentRequest = ++requestId
    loading.value = true
    error.value = ''
    const promise = (async () => {
      try {
        const { data } = await fetchProjects(params)
        // Filter changes may leave older requests in flight; only the latest can update the view.
        if (currentRequest !== requestId) return
        total.value = data.total || 0
        const lastPage = Math.max(1, Math.ceil(total.value / size.value))
        if (page.value > lastPage) {
          page.value = lastPage
          return
        }
        projects.value = data.items || []
      } catch {
        if (currentRequest === requestId) error.value = '项目加载失败，请重试'
      } finally {
        if (currentRequest === requestId) {
          loading.value = false
          pending = undefined
        }
      }
    })()
    pending = { key, promise }
    return promise
  }

  // Reset first, then let Vue batch the query changes into one request.
  watch([filters, size], () => (page.value = 1), { flush: 'sync' })
  watch(query, () => void refresh(), { immediate: true })
  onScopeDispose(() => requestId++)

  function search(keyword: string, tag: string) {
    page.value = 1
    filters.keyword = keyword.trim()
    filters.tag = tag.trim()
  }

  function resetFilters() {
    Object.assign(filters, {
      keyword: '',
      platform: '',
      type: '',
      status: '',
      tag: '',
      featured: '',
      sort: '',
    })
    page.value = 1
  }

  return { projects, total, page, size, loading, error, filters, refresh, search, resetFilters }
}
