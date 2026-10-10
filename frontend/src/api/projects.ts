import { useQuery } from '@tanstack/react-query';
import { request } from './client.ts';
import type { CreateProjectRequest, ProjectSummary } from './types.ts';

export const projectsKey = ['projects'] as const;

export function findProjects() {
  return request<ProjectSummary[]>('/api/projects');
}

export function createProject(project: CreateProjectRequest) {
  return request<ProjectSummary>('/api/projects', { method: 'POST', json: project });
}

export function useProjects() {
  return useQuery({ queryKey: projectsKey, queryFn: findProjects });
}

/**
 * There is no endpoint for one project, so a page finds its project in the list the sidebar
 * already holds. `project` is undefined both while loading and when the id is unknown or not the
 * caller's — `isLoading` tells the two apart.
 */
export function useProject(projectId: string) {
  const projects = useProjects();
  return {
    project: projects.data?.find(project => project.id === projectId),
    isLoading: projects.isLoading,
    error: projects.error,
  };
}
