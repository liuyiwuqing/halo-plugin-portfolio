/// <reference types="@rsbuild/core/types" />
/// <reference types="unplugin-icons/types/vue" />

declare module '@halo-dev/richtext-editor' {
  import type { Component } from 'vue'

  export type AnyExtension = object
  export type Range = {
    from: number
    to: number
  }

  export interface Commands<ReturnType> {
    readonly __returnType?: ReturnType
  }

  export interface EditorChain {
    focus: (...args: unknown[]) => EditorChain
    deleteRange: (range: Range) => EditorChain
    insertPortfolioProjectCard: (attrs?: unknown) => EditorChain
    run: () => boolean
  }

  export interface Editor {
    chain: () => EditorChain
    commands: Record<string, (...args: unknown[]) => boolean>
    state: unknown
    view: unknown
  }

  export type NodeConfigThis = {
    name: string
    parent?: () => Record<string, unknown>
  }

  export const Node: {
    create: (config: Record<string, unknown> & ThisType<NodeConfigThis>) => AnyExtension
  }

  export const ToolboxItem: Component
  export const NodeViewWrapper: Component
  export const NodeViewContent: Component

  export function VueNodeViewRenderer(component: Component): unknown
  export function mergeAttributes(...attributes: Array<Record<string, unknown> | undefined>): Record<string, unknown>
  export function isActive(state: unknown, name: string): boolean
}
