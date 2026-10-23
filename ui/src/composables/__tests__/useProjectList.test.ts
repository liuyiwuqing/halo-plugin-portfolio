import { afterEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick, type EffectScope } from 'vue'
import { useProjectList } from '../useProjectList'
import type { Project, ProjectList } from '../../api/portfolio'

function deferred<T>() {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}
function response(slug: string, total = 1) {
  return { data: { items: [{ slug, title: slug } as Project], total } }
}
type Response = { data: Pick<ProjectList, 'items' | 'total'> }
const scopes: EffectScope[] = []
function setup(fetch: Parameters<typeof useProjectList>[0]) {
  const scope = effectScope()
  scopes.push(scope)
  return scope.run(() => useProjectList(fetch))!
}
afterEach(() => scopes.splice(0).forEach((scope) => scope.stop()))

describe('project list', () => {
  it('keeps the latest result when older requests finish after it', async () => {
    const first = deferred<Response>()
    const second = deferred<Response>()
    const fetch = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const list = setup(fetch)
    list.search('new', '')
    await nextTick()
    second.resolve(response('new'))
    await second.promise
    first.resolve(response('old'))
    await first.promise
    expect(list.projects.value[0]?.slug).toBe('new')
    expect(list.loading.value).toBe(false)
  })

  it('does not end current loading or show an error when an older request fails', async () => {
    const first = deferred<Response>()
    const second = deferred<Response>()
    const fetch = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    const list = setup(fetch)
    list.filters.platform = 'github'
    await nextTick()
    first.reject(new Error('old request'))
    await Promise.resolve()
    expect(list.loading.value).toBe(true)
    expect(list.error.value).toBe('')
    second.resolve(response('new'))
    await second.promise
  })

  it('resets search and page size to page one with a single request', async () => {
    const fetch = vi.fn().mockResolvedValue(response('project', 100))
    const list = setup(fetch)
    await nextTick()
    list.page.value = 3
    await nextTick()
    fetch.mockClear()
    list.search(' Vue ', ' frontend ')
    list.filters.featured = 'false'
    await nextTick()
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(fetch).toHaveBeenLastCalledWith(
      expect.objectContaining({ page: 1, keyword: 'Vue', tag: 'frontend', featured: false }),
    )
    list.page.value = 3
    await nextTick()
    fetch.mockClear()
    list.size.value = 24
    await nextTick()
    expect(fetch).toHaveBeenCalledTimes(1)
    expect(fetch).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, size: 24 }))
  })

  it('exposes failure and allows retry without treating it as an empty list', async () => {
    const fetch = vi
      .fn()
      .mockRejectedValueOnce(new Error('offline'))
      .mockResolvedValueOnce(response('recovered'))
    const list = setup(fetch)
    await nextTick()
    expect(list.error.value).toContain('加载失败')
    expect(list.loading.value).toBe(false)
    await list.refresh()
    expect(list.error.value).toBe('')
    expect(list.projects.value[0]?.slug).toBe('recovered')
  })

  it('deduplicates refreshes but forces a fresh request after a mutation', async () => {
    const before = deferred<Response>()
    const after = deferred<Response>()
    const fetch = vi.fn().mockReturnValueOnce(before.promise).mockReturnValueOnce(after.promise)
    const list = setup(fetch)
    void list.refresh()
    expect(fetch).toHaveBeenCalledTimes(1)
    const refreshed = list.refresh(true)
    expect(fetch).toHaveBeenCalledTimes(2)
    after.resolve(response('created'))
    await refreshed
    before.resolve(response('stale'))
    await before.promise
    expect(list.projects.value[0]?.slug).toBe('created')
  })

  it('returns to the final existing page after deleting the last item', async () => {
    const fetch = vi.fn().mockResolvedValue(response('project', 25))
    const list = setup(fetch)
    await nextTick()
    list.page.value = 3
    await nextTick()
    fetch.mockResolvedValue(response('remaining', 24))
    await list.refresh(true)
    await nextTick()
    expect(list.page.value).toBe(2)
    expect(fetch).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2 }))
  })
})
