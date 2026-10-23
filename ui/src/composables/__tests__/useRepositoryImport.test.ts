import { afterEach, describe, expect, it, vi } from 'vitest'
import { effectScope, type EffectScope } from 'vue'
import { useRepositoryImport, repositoryErrorMessage } from '../useRepositoryImport'
import type { Project, RepositorySnapshot } from '../../api/portfolio'

function deferred<T>() {
  let resolve!: (value: T) => void
  const promise = new Promise<T>((res) => {
    resolve = res
  })
  return { promise, resolve }
}
const scopes: EffectScope[] = []
function setup(api: Parameters<typeof useRepositoryImport>[0], imported = vi.fn()) {
  const scope = effectScope()
  scopes.push(scope)
  return { flow: scope.run(() => useRepositoryImport(api, imported))!, imported }
}
afterEach(() => scopes.splice(0).forEach((scope) => scope.stop()))

describe('repository import', () => {
  it('invalidates the preview when the URL changes and ignores late responses', async () => {
    const old = deferred<{ data: RepositorySnapshot }>()
    const api = {
      repositoryPreview: vi.fn().mockReturnValue(old.promise),
      importRepository: vi.fn(),
    }
    const { flow } = setup(api)
    flow.repoUrl.value = 'https://github.com/old/repo'
    const pending = flow.preview()
    flow.repoUrl.value = 'https://gitee.com/new/repo'
    old.resolve({ data: { owner: 'old', name: 'repo' } })
    await pending
    expect(flow.snapshot.value).toBeUndefined()
    expect(flow.canImport.value).toBe(false)
    await flow.importProject()
    expect(api.importRepository).not.toHaveBeenCalled()
  })

  it('requires preview and prevents double import submission', async () => {
    const pending = deferred<{ data: Project }>()
    const api = {
      repositoryPreview: vi.fn().mockResolvedValue({ data: { owner: 'halo-dev', name: 'halo' } }),
      importRepository: vi.fn().mockReturnValue(pending.promise),
    }
    const { flow, imported } = setup(api)
    flow.repoUrl.value = 'https://github.com/halo-dev/halo'
    await flow.importProject()
    expect(api.importRepository).not.toHaveBeenCalled()
    await flow.preview()
    const first = flow.importProject()
    await flow.importProject()
    expect(api.importRepository).toHaveBeenCalledTimes(1)
    expect(flow.importing.value).toBe(true)
    const project = { title: 'Halo', slug: 'github-halo-dev-halo' } as Project
    pending.resolve({ data: project })
    await first
    expect(imported).toHaveBeenCalledWith(project)
    expect(flow.importing.value).toBe(false)
  })

  it('keeps a failed import retryable with a conflict message', async () => {
    const api = {
      repositoryPreview: vi.fn().mockResolvedValue({ data: { name: 'repo' } }),
      importRepository: vi
        .fn()
        .mockRejectedValue({ isAxiosError: true, response: { status: 409 } }),
    }
    const { flow, imported } = setup(api)
    flow.repoUrl.value = 'https://github.com/owner/repo'
    await flow.preview()
    await flow.importProject()
    expect(flow.error.value).toContain('已导入')
    expect(flow.importing.value).toBe(false)
    expect(flow.canImport.value).toBe(true)
    expect(imported).not.toHaveBeenCalled()
  })

  it('distinguishes rate limits, missing repositories and network failures', () => {
    expect(repositoryErrorMessage({ isAxiosError: true, response: { status: 429 } })).toContain(
      '频繁',
    )
    expect(repositoryErrorMessage({ isAxiosError: true, response: { status: 404 } })).toContain(
      '不存在',
    )
    expect(repositoryErrorMessage(new Error('offline'))).toContain('网络')
  })
})
