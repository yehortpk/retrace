import { useMutation, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { createProject, projectsKey, useProjects } from '../api/projects.ts';
import type { CreatedProjectResponse } from '../api/types.ts';
import { CheckIcon, ChevronRightIcon, PlusIcon } from '../components/icons.tsx';
import { RepoConnection } from '../components/RepoConnection.tsx';
import { Topbar } from '../components/Topbar.tsx';
import { pluralize } from '../lib/format.ts';
import { toSwatchStyle } from '../lib/swatch.ts';

export function ProjectsPage() {
  const projects = useProjects();
  const [searchParams, setSearchParams] = useSearchParams();
  // Held only in memory: once dismissed or navigated away from, the key's plaintext is gone for good.
  const [created, setCreated] = useState<CreatedProjectResponse | null>(null);
  const hasNoProjects = projects.data?.length === 0;
  // The list is briefly empty right after the first project is created; that must not reopen the form
  // under its key. Following "New project" does, beneath the key, which stays until navigated away from.
  const isFormOpen = searchParams.has('new') || (hasNoProjects && created === null);

  function showCreated(project: CreatedProjectResponse) {
    setCreated(project);
    setSearchParams({}, { replace: true });
  }

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
        {created && <ProjectCreated created={created} />}

        {isFormOpen && (
          // Keyed on the query string, so following "New project" again re-mounts and refocuses it.
          <ProjectForm key={searchParams.toString()} canCancel={!hasNoProjects} onCreated={showCreated}
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

/**
 * The new project's first key, shown exactly once, as the file that connects a repository to it.
 * Only a hash is stored, so a key not copied from here has to be replaced from the project's keys.
 */
function ProjectCreated({ created }: { created: CreatedProjectResponse }) {
  return (
    <section className="key-issued" aria-labelledby="project-created-title">
      <div className="key-issued__title" id="project-created-title">
        <CheckIcon />
        <span>“{created.project.name}” is ready</span>
      </div>
      <p className="key-issued__warning">
        This is the project’s API key, inside the file your agent reads. It reaches this project and no other.
        <strong> It’s shown only now</strong> — copy it before you leave this page.
      </p>
      <RepoConnection apiKey={created.key.apiKey} />
      <Link className="button button--primary key-issued__dismiss" to={`/projects/${created.project.id}`}>
        I’ve saved it — open the project
      </Link>
    </section>
  );
}

interface ProjectFormProps {
  canCancel: boolean;
  onCreated: (created: CreatedProjectResponse) => void;
  onCancel: () => void;
}

function ProjectForm({ canCancel, onCreated, onCancel }: ProjectFormProps) {
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const queryClient = useQueryClient();

  const creation = useMutation({
    mutationFn: () => createProject({ name: name.trim(), description: description.trim() || null }),
    onSuccess: async created => {
      onCreated(created);
      await queryClient.invalidateQueries({ queryKey: projectsKey });
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
