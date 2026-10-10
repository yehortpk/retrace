import { useMutation, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, type KeyboardEvent, useState } from 'react';
import { Link, Outlet, useNavigate, useParams } from 'react-router';
import {
  artifactHistoryKey, artifactsKey, deleteVersion, renameArtifact, toDownloadUrl, useArtifactHistory, useArtifacts,
} from '../api/artifacts.ts';
import { ApiError, describeError } from '../api/client.ts';
import { useProject } from '../api/projects.ts';
import { timelineKey } from '../api/timeline.ts';
import type { ArtifactHistory, ArtifactSummary, ArtifactVersionHistoryItem } from '../api/types.ts';
import { ArtifactPreview } from '../components/ArtifactPreview.tsx';
import {
  CheckIcon, CloseIcon, CopyIcon, DownloadIcon, EntryLinkIcon, FileIcon, PencilIcon, TrashIcon,
} from '../components/icons.tsx';
import { Topbar } from '../components/Topbar.tsx';
import { useClipboard } from '../components/useClipboard.ts';
import { formatBytes, formatDateTime, formatShortDate, pluralize } from '../lib/format.ts';
import { ProjectNotFound } from './TimelinePage.tsx';

/** The project's artifacts, with the selected one's version history beside them. */
export function ArtifactsPage() {
  const { projectId = '', artifactId } = useParams();
  const { project, isLoading } = useProject(projectId);
  const artifacts = useArtifacts(projectId);

  if (isLoading) {
    return null;
  }
  if (!project) {
    return <ProjectNotFound />;
  }
  return (
    <>
      <title>{`Artifacts · ${project.name} · Retrace`}</title>
      <Topbar parent={project.name} title="Artifacts"
              count={artifacts.data && pluralize(artifacts.data.length, 'artifact')} />
      <div className="artifacts-layout">
        <section className="artifact-list" aria-label="Artifact list">
          {artifacts.isError && <p className="form-error" role="alert">{artifacts.error.message}</p>}
          {artifacts.data?.length === 0 && (
            <p className="empty-state">
              No artifacts yet. Files attached to an entry show up here, each with every version it has had.
            </p>
          )}
          {artifacts.data && artifacts.data.length > 0 && (
            <ArtifactList projectId={projectId} artifacts={artifacts.data} selectedId={artifactId} />
          )}
        </section>
        <Outlet />
      </div>
    </>
  );
}

interface ArtifactListProps {
  projectId: string;
  artifacts: ArtifactSummary[];
  selectedId: string | undefined;
}

function ArtifactList({ projectId, artifacts, selectedId }: ArtifactListProps) {
  return (
    <div className="row-box">
      <div className="row-box__header artifact-row" aria-hidden="true">
        <span>Name</span>
        <span>Current</span>
        <span className="artifact-row__updated">Updated</span>
      </div>
      <ul className="row-box__rows plain-list">
        {artifacts.map(artifact => (
          <li key={artifact.id}>
            <Link className="artifact-row artifact-row--link" to={`/projects/${projectId}/artifacts/${artifact.id}`}
                  aria-current={artifact.id === selectedId ? 'true' : undefined}>
              <span className="artifact-row__name">
                <span className="file-icon"><FileIcon size={13} /></span>
                <span className="artifact-row__filename">{artifact.name}</span>
                <span className="artifact-row__count">{pluralize(artifact.versionCount, 'version')}</span>
              </span>
              <span className="artifact-row__version">v{artifact.currentVersion.ordinal}</span>
              <span className="artifact-row__updated">{formatShortDate(artifact.currentVersion.createdAt)}</span>
            </Link>
          </li>
        ))}
      </ul>
    </div>
  );
}

/** The selected artifact, rendered beside the list by the `:artifactId` child route. */
export function ArtifactDetailPanel() {
  const { projectId = '', artifactId = '' } = useParams();
  return <ArtifactDetail key={artifactId} projectId={projectId} artifactId={artifactId} />;
}

interface ArtifactDetailProps {
  projectId: string;
  artifactId: string;
}

function ArtifactDetail({ projectId, artifactId }: ArtifactDetailProps) {
  const history = useArtifactHistory(projectId, artifactId);
  const listPath = `/projects/${projectId}/artifacts`;

  if (history.isPending) {
    return <aside className="artifact-detail" aria-busy="true" />;
  }
  if (history.isError) {
    const isGone = history.error instanceof ApiError && history.error.status === 404;
    return (
      <aside className="artifact-detail">
        <div className="artifact-detail__header">
          <div className="artifact-detail__title-row">
            <p className="artifact-detail__title">
              {isGone ? 'This artifact no longer exists' : history.error.message}
            </p>
            <Link className="icon-button" to={listPath} aria-label="Close"><CloseIcon size={15} /></Link>
          </div>
        </div>
      </aside>
    );
  }

  const artifact = history.data;
  const current = artifact.versions[artifact.versions.length - 1];
  return (
    <aside className="artifact-detail" aria-labelledby="artifact-title">
      <ArtifactHeader projectId={projectId} artifact={artifact} listPath={listPath} />
      {current && <ArtifactPreview projectId={projectId} artifactId={artifact.id} version={current} />}
      <VersionList projectId={projectId} artifact={artifact} listPath={listPath} />
    </aside>
  );
}

interface ArtifactHeaderProps {
  projectId: string;
  artifact: ArtifactHistory;
  listPath: string;
}

/** Title with inline rename: Enter or leaving the field saves, Escape cancels. */
function ArtifactHeader({ projectId, artifact, listPath }: ArtifactHeaderProps) {
  const [draftName, setDraftName] = useState<string | null>(null);
  const { isCopied, copyText } = useClipboard();
  const queryClient = useQueryClient();

  const renaming = useMutation({
    mutationFn: (name: string) => renameArtifact(projectId, artifact.id, name),
    onSuccess: async renamed => {
      queryClient.setQueryData(artifactHistoryKey(projectId, artifact.id), renamed);
      // The list and every timeline chip show the name, so both are stale now.
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: artifactsKey(projectId), exact: true }),
        queryClient.invalidateQueries({ queryKey: timelineKey(projectId) }),
      ]);
    },
  });

  function saveName(event?: FormEvent) {
    event?.preventDefault();
    if (draftName === null) {
      return;
    }
    const name = draftName.trim();
    setDraftName(null);
    if (name && name !== artifact.name) {
      renaming.mutate(name);
    }
  }

  function cancelOnEscape(event: KeyboardEvent) {
    if (event.key === 'Escape') {
      // Escape would otherwise also reach anything above that listens for it.
      event.stopPropagation();
      setDraftName(null);
    }
  }

  const displayedName = renaming.isPending ? renaming.variables : artifact.name;

  return (
    <div className="artifact-detail__header">
      <div className="artifact-detail__title-row">
        <span className="file-icon file-icon--large"><FileIcon size={15} /></span>
        {draftName === null ? (
          <>
            <h2 className="artifact-detail__title" id="artifact-title">{displayedName}</h2>
            <button className="icon-button" type="button" aria-label="Rename artifact"
                    onClick={() => { renaming.reset(); setDraftName(artifact.name); }}>
              <PencilIcon size={15} />
            </button>
          </>
        ) : (
          <form className="artifact-detail__rename" onSubmit={saveName}>
            <label className="visually-hidden" htmlFor="artifact-name">Artifact name</label>
            <input className="input" id="artifact-name" type="text" maxLength={300} required autoFocus
                   value={draftName} onChange={event => setDraftName(event.target.value)}
                   onKeyDown={cancelOnEscape} onBlur={() => saveName()} />
          </form>
        )}
        <Link className="icon-button" to={listPath} aria-label="Close"><CloseIcon size={15} /></Link>
      </div>
      {renaming.isError && <p className="form-error" role="alert">{describeError(renaming.error)}</p>}
      <div className="artifact-detail__id">
        <code>{artifact.id}</code>
        <button className="icon-button icon-button--tiny" type="button"
                aria-label={isCopied ? 'Artifact ID copied' : 'Copy artifact ID'} onClick={() => copyText(artifact.id)}>
          {isCopied ? <CheckIcon size={13} /> : <CopyIcon size={13} />}
        </button>
      </div>
    </div>
  );
}

interface VersionListProps {
  projectId: string;
  artifact: ArtifactHistory;
  listPath: string;
}

function VersionList({ projectId, artifact, listPath }: VersionListProps) {
  const queryClient = useQueryClient();
  const navigate = useNavigate();
  const totalBytes = artifact.versions.reduce((total, version) => total + version.sizeBytes, 0);
  const newestFirst = [...artifact.versions].reverse();

  const deletion = useMutation({
    mutationFn: (ordinal: number) => deleteVersion(projectId, artifact.id, ordinal),
    onSuccess: async () => {
      const wasLastVersion = artifact.versions.length === 1;
      if (wasLastVersion) {
        await navigate(listPath, { replace: true });
      }
      // Ordinals are dense, so the versions after a deleted one are renumbered — refetch, don't patch.
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: artifactsKey(projectId) }),
        queryClient.invalidateQueries({ queryKey: timelineKey(projectId) }),
      ]);
    },
  });

  function confirmDeletion(version: ArtifactVersionHistoryItem) {
    const consequence = artifact.versions.length === 1
      ? ' It is the only version, so the artifact will be removed too.'
      : '';
    if (window.confirm(`Delete v${version.ordinal} of “${artifact.name}”? This cannot be undone.${consequence}`)) {
      deletion.mutate(version.ordinal);
    }
  }

  return (
    <section className="versions" aria-labelledby="versions-title">
      <div className="versions__heading">
        <h3 className="versions__title" id="versions-title">Versions</h3>
        <span className="versions__summary">{artifact.versions.length} · {formatBytes(totalBytes)} in total</span>
      </div>
      {deletion.isError && <p className="form-error" role="alert">{describeError(deletion.error)}</p>}
      <ol className="plain-list" reversed>
        {newestFirst.map((version, index) => (
          <li className={index === 0 ? 'version version--current' : 'version'} key={version.ordinal}>
            <div className="version__head">
              <span className="version__ordinal">v{version.ordinal}</span>
              <span className="version__label">{version.label}</span>
              <a className="icon-button" href={toDownloadUrl(projectId, artifact.id, version.ordinal)} download
                 aria-label={`Download v${version.ordinal}`}>
                <DownloadIcon size={15} />
              </a>
              <button className="icon-button" type="button" aria-label={`Delete v${version.ordinal}`}
                      disabled={deletion.isPending} onClick={() => confirmDeletion(version)}>
                <TrashIcon size={15} />
              </button>
            </div>
            <span className="version__meta">
              {version.filename ?? 'unnamed file'} · {formatBytes(version.sizeBytes)} ·{' '}
              <time dateTime={version.createdAt}>{formatDateTime(version.createdAt)}</time>
            </span>
            <Link className="version__entry" to={`/projects/${projectId}#entry-${version.entryId}`}>
              <EntryLinkIcon size={14} />
              <span>{version.entryDescription}</span>
            </Link>
          </li>
        ))}
      </ol>
      <p className="versions__footnote">Deleting the only remaining version removes the artifact.</p>
    </section>
  );
}
