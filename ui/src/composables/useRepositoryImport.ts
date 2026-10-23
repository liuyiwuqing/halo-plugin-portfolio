import { computed, onScopeDispose, ref, watch } from 'vue'
import { isAxiosError } from 'axios'
import type { portfolioApi, Project, RepositorySnapshot } from '../api/portfolio'

export function repositoryErrorMessage(error: unknown) {
  const status = isAxiosError(error) ? error.response?.status : undefined
  if (status === 400) return '请输入完整的 GitHub 或 Gitee 公开仓库地址'
  if (status === 404) return '仓库不存在或不是公开仓库，请核对地址'
  if (status === 409) return '该仓库已导入或标识冲突，请在已有项目上同步'
  if (status === 429) return '仓库平台请求过于频繁，请稍后重试'
  if (status === 401 || status === 403) return '当前账号没有仓库管理权限'
  return '无法获取仓库数据，请检查网络后重试'
}

export function useRepositoryImport(
  api: Pick<typeof portfolioApi, 'repositoryPreview' | 'importRepository'>,
  onImported: (project: Project) => void,
) {
  const repoUrl = ref('')
  const snapshot = ref<RepositorySnapshot>()
  const previewing = ref(false)
  const importing = ref(false)
  const error = ref('')
  let revision = 0
  let previewUrl = ''

  const canImport = computed(() => !!snapshot.value && previewUrl === repoUrl.value.trim())
  watch(
    repoUrl,
    () => {
      revision++
      snapshot.value = undefined
      previewing.value = false
      error.value = ''
    },
    { flush: 'sync' },
  )
  onScopeDispose(() => revision++)

  async function preview() {
    if (previewing.value || importing.value) return
    const url = repoUrl.value.trim()
    if (!url) return
    const currentRevision = ++revision
    previewing.value = true
    snapshot.value = undefined
    error.value = ''
    try {
      const { data } = await api.repositoryPreview(url)
      if (currentRevision !== revision) return
      previewUrl = url
      snapshot.value = data
    } catch (cause) {
      if (currentRevision === revision) error.value = repositoryErrorMessage(cause)
    } finally {
      if (currentRevision === revision) previewing.value = false
    }
  }

  async function importProject() {
    if (!canImport.value || importing.value) return
    importing.value = true
    error.value = ''
    try {
      const { data } = await api.importRepository(previewUrl)
      onImported(data)
    } catch (cause) {
      error.value = repositoryErrorMessage(cause)
    } finally {
      importing.value = false
    }
  }

  return { repoUrl, snapshot, previewing, importing, error, canImport, preview, importProject }
}
