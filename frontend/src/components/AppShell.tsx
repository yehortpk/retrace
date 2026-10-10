import { useQueryClient } from '@tanstack/react-query';
import { Link, NavLink, Outlet, useNavigate, useParams } from 'react-router';
import { logOut, useCurrentUser } from '../api/auth.ts';
import { useProjects } from '../api/projects.ts';
import { toSwatchStyle } from '../lib/swatch.ts';
import {
  BrandIcon, GridIcon, KeyIcon, LayersIcon, PlusIcon, SignOutIcon, TimelineIcon,
} from './icons.tsx';

/**
 * Sidebar plus the routed page. Mounting it is what requires a session: until /api/auth/me answers,
 * nothing protected renders, and a 401 from it (or any later call) sends the browser to /login.
 */
export function AppShell() {
  const currentUser = useCurrentUser();

  if (currentUser.isPending) {
    return null;
  }
  if (currentUser.isError) {
    return (
      <main className="auth-page">
        <p className="form-error" role="alert">Retrace is unreachable: {currentUser.error.message}</p>
      </main>
    );
  }
  return (
    <div className="app">
      <Sidebar />
      <main className="main">
        <Outlet />
      </main>
    </div>
  );
}

function Sidebar() {
  const { projectId } = useParams();
  const projects = useProjects();
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  async function signOut() {
    await logOut().catch(() => undefined);
    await navigate('/login');
    queryClient.clear();
  }

  return (
    <nav className="sidebar" aria-label="Main">
      <Link className="sidebar__brand" to="/">
        <span className="brand-mark"><BrandIcon /></span>
        <span>Retrace</span>
      </Link>
      <NavLink className="nav-link sidebar__all-projects" to="/" end>
        <GridIcon />
        <span>All projects</span>
      </NavLink>
      <div className="sidebar__section">
        <div className="sidebar__section-title" id="sidebar-projects">Projects</div>
        <ul className="sidebar__list" aria-labelledby="sidebar-projects">
          {projects.data?.map(project => {
            const isCurrent = project.id === projectId;
            return (
              <li key={project.id}>
                <Link className={`nav-link nav-link--project${isCurrent ? ' nav-link--expanded' : ''}`}
                      to={`/projects/${project.id}`}>
                  <span className="swatch" style={toSwatchStyle(project.id)} />
                  <span className="nav-link__label">{project.name}</span>
                  <span className="nav-link__count">
                    {project.entryCount}<span className="visually-hidden"> entries</span>
                  </span>
                </Link>
                {isCurrent && (
                  <ul className="sidebar__list">
                    <li>
                      <NavLink className="nav-link nav-link--nested" to={`/projects/${project.id}`} end>
                        <TimelineIcon />
                        <span className="nav-link__label">Timeline</span>
                      </NavLink>
                    </li>
                    <li>
                      <NavLink className="nav-link nav-link--nested" to={`/projects/${project.id}/artifacts`}>
                        <LayersIcon />
                        <span className="nav-link__label">Artifacts</span>
                      </NavLink>
                    </li>
                  </ul>
                )}
              </li>
            );
          })}
          <li>
            <Link className="nav-link" to="/?new">
              <PlusIcon />
              <span>New project</span>
            </Link>
          </li>
        </ul>
      </div>
      <div className="sidebar__footer">
        <NavLink className="nav-link" to="/settings/keys">
          <KeyIcon />
          <span>API keys</span>
        </NavLink>
        <button className="nav-link" type="button" onClick={signOut}>
          <SignOutIcon />
          <span>Sign out</span>
        </button>
      </div>
    </nav>
  );
}
