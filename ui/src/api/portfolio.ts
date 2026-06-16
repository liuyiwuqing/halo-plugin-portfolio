import { axiosInstance } from '@halo-dev/api-client'

const consoleBase = '/apis/console.portfolio.muyin.site/v1alpha1/projects'

export type ProjectStatus = 'draft' | 'published' | 'archived'

export interface Metadata {
  name?: string
  creationTimestamp?: string
  annotations?: Record<string, string>
  labels?: Record<string, string>
}

export interface Project {
  metadata?: Metadata
  title?: string
  slug?: string
  summary?: string
  content?: string
  cover?: string
  platform?: string
  type?: string
  techStacks?: string[]
  tags?: string[]
  repoUrl?: string
  demoUrl?: string
  docsUrl?: string
  sourceProvider?: string
  repoOwner?: string
  repoName?: string
  priority?: number
  featured?: boolean
  status?: ProjectStatus
  createTime?: string
  updateTime?: string
}

export interface ListResult<T> {
  page: number
  size: number
  total: number
  items: T[]
}

export interface ProjectQuery {
  page?: number
  size?: number
  keyword?: string
  platform?: string
  type?: string
  status?: string
  tag?: string
}

export interface OptionItem {
  label?: string
  value?: string
}

export interface ProjectOptions {
  platformOptions: OptionItem[]
  typeOptions: OptionItem[]
}

export const portfolioApi = {
  list: (params: ProjectQuery) =>
    axiosInstance.get<ListResult<Project>>(`${consoleBase}/list`, { params }),
  create: (data: Project) => axiosInstance.post<Project>(`${consoleBase}/create`, data),
  update: (data: Project) => axiosInstance.post<Project>(`${consoleBase}/update`, data),
  delete: (slug: string) =>
    axiosInstance.delete<Project>(`${consoleBase}/${encodeURIComponent(slug)}/delete`),
  options: () => axiosInstance.get<ProjectOptions>(`${consoleBase}/settings/options`),
}
