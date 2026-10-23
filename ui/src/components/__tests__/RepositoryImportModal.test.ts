// @vitest-environment jsdom
import { readFileSync } from 'node:fs'
import { compileScript, parse } from 'vue/compiler-sfc'
import { ModuleKind, transpileModule } from 'typescript'
import * as Vue from 'vue'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useRepositoryImport } from '../../composables/useRepositoryImport'

function compileComponent(source: string, dependencies: Record<string, unknown>) {
  const { descriptor } = parse(source)
  const script = compileScript(descriptor, {
    id: 'repository-import-modal-test',
    inlineTemplate: true,
  })
  const { outputText } = transpileModule(script.content, {
    compilerOptions: { module: ModuleKind.CommonJS },
  })
  const exports: { default?: Vue.Component } = {}
  new Function('require', 'exports', outputText)((name: string) => dependencies[name], exports)
  return exports.default!
}

// Load the installed bundle so the real VModal, Teleport and OverlayScrollbars remain in the test.
// Its browser-global build avoids adding router dependencies just for the test harness.
function haloComponents() {
  const bundle = readFileSync('node_modules/@halo-dev/components/dist/index.iife.js', 'utf8')
  return new Function('Vue', 'VueRouter', 'VueUse', `${bundle}\nreturn HaloComponents`)(
    Vue,
    { useRouter: () => ({ push: vi.fn() }) },
    {},
  ) as { VModal: Vue.Component; VButton: Vue.Component }
}

const FormKit = Vue.defineComponent({
  props: ['type', 'label', 'modelValue', 'name', 'disabled'],
  emits: ['submit', 'node', 'update:modelValue'],
  setup(props, { slots, emit }) {
    const form = Vue.ref<HTMLFormElement>()
    Vue.onMounted(() => {
      if (props.type === 'form') emit('node', { submit: () => form.value!.requestSubmit() })
    })
    return () => {
      if (props.type === 'form') {
        return Vue.h(
          'form',
          {
            ref: form,
            onSubmit: (event: SubmitEvent) => {
              event.preventDefault()
              emit('submit')
            },
          },
          slots.default?.(),
        )
      }
      if (props.type === 'submit') {
        return Vue.h('button', { type: 'submit', disabled: props.disabled }, props.label)
      }
      return Vue.h('input', {
        name: props.name,
        type: props.type,
        required: true,
        disabled: props.disabled,
        value: props.modelValue,
        onInput: (event: Event) =>
          emit('update:modelValue', (event.target as HTMLInputElement).value),
      })
    }
  },
})

const dispose: Array<() => void> = []
beforeEach(() => {
  vi.stubGlobal(
    'ResizeObserver',
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  )
  vi.stubGlobal(
    'IntersectionObserver',
    class {
      observe() {}
      unobserve() {}
      disconnect() {}
    },
  )
  vi.stubGlobal('matchMedia', (query: string) => ({
    matches: false,
    media: query,
    addListener() {},
    removeListener() {},
    addEventListener() {},
    removeEventListener() {},
  }))
  const getStyle = window.getComputedStyle.bind(window)
  vi.spyOn(window, 'getComputedStyle').mockImplementation((element) => getStyle(element))
})
afterEach(() => {
  dispose.splice(0).forEach((close) => close())
  vi.restoreAllMocks()
  vi.unstubAllGlobals()
})

async function mountModal() {
  const api = { repositoryPreview: vi.fn(), importRepository: vi.fn() }
  const source = readFileSync('src/components/RepositoryImportModal.vue', 'utf8')
  const dependencies: Record<string, unknown> = {
    vue: Vue,
    '@halo-dev/components': { ...haloComponents(), Toast: { success: vi.fn() } },
    '@/api/portfolio': { portfolioApi: api },
    '@/composables/useRepositoryImport': { useRepositoryImport },
  }
  const root = document.createElement('div')
  document.body.append(root)
  const errors: unknown[] = []
  const app = Vue.createApp(compileComponent(source, dependencies))
  app.config.errorHandler = (error) => errors.push(error)
  app.component('FormKit', FormKit)
  app.directive('permission', {})
  app.mount(root)
  dispose.push(() => {
    app.unmount()
    root.remove()
  })
  await Vue.nextTick()
  await vi.waitFor(() =>
    expect(document.querySelector('[data-overlayscrollbars-viewport]')).not.toBeNull(),
  )
  return { root: document.querySelector<HTMLElement>('[role="dialog"]')!, api, errors }
}

function button(root: ParentNode, label: string) {
  const element = [...root.querySelectorAll('button')].find(
    (element) => element.textContent?.trim() === label,
  )
  expect(element, label).toBeDefined()
  return element!
}

async function enterUrl(root: ParentNode, value: string) {
  const input = root.querySelector('input')!
  input.value = value
  input.dispatchEvent(new Event('input', { bubbles: true }))
  await Vue.nextTick()
}

describe('repository import modal', () => {
  it('renders a successful preview after the modal content has moved into its scroll viewport', async () => {
    const { root, api, errors } = await mountModal()
    api.repositoryPreview.mockResolvedValue({
      data: { owner: 'halo-dev', name: 'halo', provider: 'github', stars: 42 },
    })
    await enterUrl(root, 'https://github.com/halo-dev/halo')
    button(root, '预览仓库').click()
    await vi.waitFor(() => {
      expect(errors).toEqual([])
      expect(root.textContent).toContain('halo-dev/halo')
    })
    expect(
      root.querySelector('[data-overlayscrollbars-viewport] [aria-label="仓库预览"]'),
    ).not.toBeNull()
    expect(api.repositoryPreview).toHaveBeenCalledWith('https://github.com/halo-dev/halo')
    expect(button(root, '导入为草稿').disabled).toBe(false)
    expect(errors).toEqual([])
  })

  it('shows a failed preview and allows retrying from the footer', async () => {
    const { root, api, errors } = await mountModal()
    api.repositoryPreview.mockRejectedValue({ isAxiosError: true, response: { status: 404 } })
    await enterUrl(root, 'https://github.com/halo-dev/missing')
    button(root, '预览仓库').click()
    await vi.waitFor(() =>
      expect(root.querySelector('[role="alert"]')?.textContent).toContain('不存在'),
    )
    expect(root.querySelector('.modal-footer')?.contains(button(root, '预览仓库'))).toBe(true)
    expect(button(root, '预览仓库').disabled).toBe(false)
    expect(button(root, '导入为草稿').disabled).toBe(true)
    expect(errors).toEqual([])
  })

  it('validates the address before previewing and invalidates the preview when it changes', async () => {
    const { root, api, errors } = await mountModal()
    api.repositoryPreview.mockResolvedValue({ data: { owner: 'halo-dev', name: 'halo' } })
    button(root, '预览仓库').click()
    await Vue.nextTick()
    expect(api.repositoryPreview).not.toHaveBeenCalled()
    await enterUrl(root, 'not-a-url')
    button(root, '预览仓库').click()
    await Vue.nextTick()
    expect(api.repositoryPreview).not.toHaveBeenCalled()
    await enterUrl(root, 'https://github.com/halo-dev/halo')
    button(root, '预览仓库').click()
    await vi.waitFor(() => expect(button(root, '导入为草稿').disabled).toBe(false))
    await enterUrl(root, 'https://gitee.com/halo-dev/halo')
    expect(root.querySelector('[aria-label="仓库预览"]')).toBeNull()
    expect(button(root, '导入为草稿').disabled).toBe(true)
    expect(errors).toEqual([])
  })
})
