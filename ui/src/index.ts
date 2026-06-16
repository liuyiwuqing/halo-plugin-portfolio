import { definePlugin } from '@halo-dev/ui-shared'
import { markRaw } from 'vue'
import RiCodeBoxLine from '~icons/ri/code-box-line'

export default definePlugin({
  components: {},
  routes: [
    {
      parentName: 'Root',
      route: {
        path: '/portfolio',
        name: 'PortfolioProjectManager',
        component: () =>
          import(/* webpackChunkName: "ProjectManagerView" */ './views/ProjectManagerView.vue'),
        meta: {
          title: '项目管理',
          searchable: true,
          permissions: ['plugin:portfolio:project:view'],
          menu: {
            name: '项目管理',
            group: 'tool',
            icon: markRaw(RiCodeBoxLine),
            priority: 0,
          },
        },
      },
    },
  ],
  extensionPoints: {},
})
