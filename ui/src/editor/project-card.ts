import type { Editor, Range } from '@halo-dev/richtext-editor'
import { mergeAttributes, Node, ToolboxItem, VueNodeViewRenderer } from '@halo-dev/richtext-editor'
import { markRaw } from 'vue'
import RiCodeBoxLine from '~icons/ri/code-box-line'
import ProjectCardView from './ProjectCardView.vue'

export interface PortfolioProjectCardAttrs {
  slug?: string
}

declare module '@halo-dev/richtext-editor' {
  interface Commands<ReturnType> {
    portfolioProjectCard: {
      insertPortfolioProjectCard: (attrs?: PortfolioProjectCardAttrs) => ReturnType
      setPortfolioProjectCardAttrs: (attrs: PortfolioProjectCardAttrs) => ReturnType
    }
  }
}

export const PortfolioProjectCardExtension = Node.create({
  name: 'portfolioProjectCard',
  group: 'block',
  atom: true,
  selectable: true,
  draggable: true,
  isolating: true,
  defining: true,

  addAttributes() {
    return {
      slug: {
        default: '',
        parseHTML: (element: HTMLElement) => element.getAttribute('data-slug') || '',
        renderHTML: (attributes: PortfolioProjectCardAttrs) => {
          if (!attributes.slug) {
            return {}
          }
          return { 'data-slug': attributes.slug }
        },
      },
    }
  },

  parseHTML() {
    return [{ tag: 'portfolio-project-card' }, { tag: 'div[data-portfolio-project-card]' }]
  },

  renderHTML({ HTMLAttributes }: { HTMLAttributes: Record<string, unknown> }) {
    return [
      'portfolio-project-card',
      mergeAttributes(HTMLAttributes, { 'data-portfolio-project-card': '' }),
    ]
  },

  addCommands() {
    return {
      insertPortfolioProjectCard:
        (attrs: PortfolioProjectCardAttrs = {}) =>
        ({ commands }: { commands: Record<string, (...args: unknown[]) => boolean> }) => {
          return commands.insertContent([
            {
              type: this.name,
              attrs,
            },
            {
              type: 'paragraph',
            },
          ])
        },
      setPortfolioProjectCardAttrs:
        (attrs: PortfolioProjectCardAttrs) =>
        ({ commands }: { commands: Record<string, (...args: unknown[]) => boolean> }) => {
          return commands.updateAttributes(this.name, attrs)
        },
    }
  },

  addNodeView() {
    return VueNodeViewRenderer(ProjectCardView)
  },

  addOptions() {
    return {
      ...this.parent?.(),
      getToolboxItems({ editor }: { editor: Editor }) {
        return {
          priority: 122530,
          component: markRaw(ToolboxItem),
          props: {
            editor,
            icon: markRaw(RiCodeBoxLine),
            title: '插入项目卡片',
            action: () => {
              editor.chain().focus().insertPortfolioProjectCard().run()
            },
          },
        }
      },
      getCommandMenuItems() {
        return {
          priority: 122530,
          icon: markRaw(RiCodeBoxLine),
          title: '项目卡片',
          keywords: ['portfolio', 'project', '项目', '作品集'],
          command: ({ editor, range }: { editor: Editor; range: Range }) => {
            editor.chain().focus().deleteRange(range).insertPortfolioProjectCard().run()
          },
        }
      },
    }
  },
})
