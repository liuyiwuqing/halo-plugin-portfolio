import { axiosInstance } from '@halo-dev/api-client'
import {
  ConsolePortfolioMuyinSiteV1alpha1PortfolioApi,
  type ConsolePortfolioMuyinSiteV1alpha1PortfolioApiProjectListForConsoleRequest,
  type Project,
  type ProjectList,
  type ProjectStatusEnum,
} from './generated'
import type { AxiosResponse } from 'axios'

const consolePortfolioApi = new ConsolePortfolioMuyinSiteV1alpha1PortfolioApi(
  undefined,
  '',
  axiosInstance,
)

export type { Project, ProjectList }
export type ProjectStatus = ProjectStatusEnum

export type ProjectQuery = ConsolePortfolioMuyinSiteV1alpha1PortfolioApiProjectListForConsoleRequest

export interface OptionItem {
  label?: string
  value?: string
}

export interface ProjectOptions {
  platformOptions: OptionItem[]
  typeOptions: OptionItem[]
}

export const portfolioApi = {
  list: (params: ProjectQuery = {}) => consolePortfolioApi.projectListForConsole(params),
  create: (project: Project) => consolePortfolioApi.projectCreateForConsole({ project }),
  update: (project: Project) => consolePortfolioApi.projectUpdateForConsole({ project }),
  delete: (slug: string) => consolePortfolioApi.projectDeleteForConsole({ slug }),
  options: () =>
    consolePortfolioApi.projectOptionsForConsole() as unknown as Promise<
      AxiosResponse<ProjectOptions>
    >,
}
