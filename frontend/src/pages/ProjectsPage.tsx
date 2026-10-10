import { useMutation, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { createProject, projectsKey, useProjects } from '../api/projects.ts';
import { ChevronRightIcon, PlusIcon } from '../components/icons.tsx';
import { Topbar } from '../components/Topbar.tsx';
import { pluralize } from '../lib/format.ts';
import { toSwatchStyle } from '../lib/swatch.ts';

export function ProjectsPage() {
  const projects = useProjects();
  const [searchParams, setSearchParams] = useSearchParams();
  const hasNoProjects = projects.data?.length === 0;
  const isFormOpen = searchParams.has('new') || hasNoProjects;

  return (
    <>
      <title>Projects · Retrace</title>
      <Topbar title="Projects">
        <Link className="button button--primary" to="?new">
          <PlusIcon size={14} strokeWidth={1.8} />
          New project
        </Link>
      </Topbar>

      <div className="content">
        {isFormOpen && (
          // Keyed on the query string, so following "New project" again re-mounts and refocuses it.
          <ProjectForm key={searchParams.toString()} canCancel={!hasNoProjects}
                       onCancel={() => setSearchParams({}, { replace: true })} />
        )}

        {projects.isError && <p className="form-error" role="alert">{projects.error.message}</p>}

        {projects.data && projects.data.length > 0 && (
          <ul className="row-box row-box__rows plain-list" aria-label="Your projects">
            {projects.data.map(project => (
              <li key={project.id}>
                <Link className="project-row" to={`/projects/${project.id}`}>
                  <span className="swatch" style={toSwatchStyle(project.id)} />
                  <span className="project-row__text">
                    <span className="project-row__name">{project.name}</span>
                    {project.description && (
                      <span className="project-row__description">{project.description}</span>
                    )}
                  </span>
                  <span className="project-row__count">{pluralize(project.entryCount, 'entry', 'entries')}</span>
                  <ChevronRightIcon size={14} className="project-row__chevron" />
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </>
  );
}

interface ProjectFormProps {
  canCancel: boolean;
  onCancel: () => void;
}

function ProjectForm({ canCancel, onCancel }: ProjectFormProps) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const creation = useMutation({
    mutationFn: () => createProject({ name: name.trim(), description: description.trim() || null }),
    onSuccess: async created => {
      await queryClient.invalidateQueries({ queryKey: projectsKey });
      await navigate(`/projects/${created.id}`);
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    creation.mutate();
  }

  return (
    <form className="project-form" aria-label="New project" onSubmit={submit}>
      <div className="field">
        <label className="field__label" htmlFor="project-name">Name</label>
        <input className="input" id="project-name" name="name" type="text" maxLength={200} required autoFocus
               value={name} onChange={event => setName(event.target.value)} />
      </div>
      <div className="field">
        <label className="field__label" htmlFor="project-description">
          Description <span className="field__optional">— optional</span>
        </label>
        <textarea className="input" id="project-description" name="description" rows={2} maxLength={2000}
                  placeholder="What is this project about?" value={description}
                  onChange={event => setDescription(event.target.value)} />
      </div>
      {creation.isError && <p className="form-error" role="alert">{creation.error.message}</p>}
      <div className="form-actions">
        {canCancel && <button className="button button--ghost" type="button" onClick={onCancel}>Cancel</button>}
        <button className="button button--primary" type="submit" disabled={creation.isPending}>
          {creation.isPending ? 'Creating…' : 'Create project'}
        </button>
      </div>
    </form>
  );
}
