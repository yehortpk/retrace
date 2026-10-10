import { useEffect, useState } from 'react';
import { Link, useLocation, useParams } from 'react-router';
import { useProject } from '../api/projects.ts';
import { useTimeline } from '../api/timeline.ts';
import type { ProjectSummary, TimelineEntry } from '../api/types.ts';
import { EntryDialog } from '../components/EntryDialog.tsx';
import { FileIcon, NoteIcon, PlusIcon, TerminalIcon } from '../components/icons.tsx';
import { Topbar } from '../components/Topbar.tsx';
import { formatDayName, formatTime, formatWeekdayDate, pluralize } from '../lib/format.ts';
import { type EntryGroup, groupTimeline, mergeTimelinePages } from '../lib/timeline-grouping.ts';

const SESSION_ID_DISPLAY_LENGTH = 8;

export function TimelinePage() {
  const { projectId = '' } = useParams();
  const { project, isLoading } = useProject(projectId);
  const [isDialogOpen, setIsDialogOpen] = useState(false);

  if (isLoading) {
    return null;
  }
  if (!project) {
    return <ProjectNotFound />;
  }
  return (
    <>
      <title>{`Timeline · ${project.name} · Retrace`}</title>
      <Topbar parent={project.name} title="Timeline" count={pluralize(project.entryCount, 'entry', 'entries')}>
        <button className="button button--primary" type="button" onClick={() => setIsDialogOpen(true)}>
          <PlusIcon size={14} strokeWidth={1.8} />
          New entry
        </button>
      </Topbar>
      <Timeline project={project} />
      {isDialogOpen && <EntryDialog project={project} onClose={() => setIsDialogOpen(false)} />}
    </>
  );
}

export function ProjectNotFound() {
  return (
    <>
      <title>Not found · Retrace</title>
      <Topbar title="Project not found" />
      <div className="content">
        <p className="empty-state">
          There is no such project in your account. <Link className="text-link" to="/">See all projects</Link>
        </p>
      </div>
    </>
  );
}

function Timeline({ project }: { project: ProjectSummary }) {
  const timeline = useTimeline(project.id);
  const entries = mergeTimelinePages(timeline.data?.pages ?? []);
  const days = groupTimeline(entries);
  useScrollToHashTarget(entries.length);

  if (timeline.isPending) {
    return null;
  }
  if (timeline.isError) {
    return <div className="content"><p className="form-error" role="alert">{timeline.error.message}</p></div>;
  }
  if (entries.length === 0) {
    return (
      <div className="content">
        <div className="empty-state">
          <p>Nothing recorded yet. Record the first entry here, or let Claude Code do it — configure it with this
            project’s ID:</p>
          <code className="empty-state__code">{project.id}</code>
        </div>
      </div>
    );
  }

  return (
    <div className="content timeline">
      {days.map(day => {
        const headingId = `day-${day.key}`;
        return (
          <section className="day" key={day.key} aria-labelledby={headingId}>
            <h2 className="day__heading" id={headingId}>
              {formatDayName(day.date)} <span className="day__date">{formatWeekdayDate(day.date)}</span>
            </h2>
            {day.groups.map(group => (
              <div className="entry-group" key={group.entries[0].id}>
                {group.sessionId && <SessionBar group={group} />}
                {group.entries.map(entry => <EntryRow key={entry.id} projectId={project.id} entry={entry} />)}
              </div>
            ))}
          </section>
        );
      })}
      {timeline.hasNextPage && (
        <button className="button button--outline timeline__more" type="button"
                disabled={timeline.isFetchingNextPage} onClick={() => timeline.fetchNextPage()}>
          {timeline.isFetchingNextPage ? 'Loading…' : 'Load older entries'}
        </button>
      )}
    </div>
  );
}

function SessionBar({ group }: { group: EntryGroup }) {
  const newest = group.entries[0];
  const oldest = group.entries[group.entries.length - 1];
  const span = newest === oldest
    ? formatTime(newest.occurredAt)
    : `${formatTime(oldest.occurredAt)}–${formatTime(newest.occurredAt)}`;
  return (
    <div className="session-bar">
      <TerminalIcon size={14} />
      <span className="session-bar__id" title={group.sessionId ?? undefined}>
        session {group.sessionId?.slice(0, SESSION_ID_DISPLAY_LENGTH)}
      </span>
      <span>· {pluralize(group.entries.length, 'entry', 'entries')} · {span}</span>
    </div>
  );
}

function EntryRow({ projectId, entry }: { projectId: string; entry: TimelineEntry }) {
  return (
    <article className="entry" id={`entry-${entry.id}`}>
      <time className="entry__time" dateTime={entry.occurredAt}>{formatTime(entry.occurredAt)}</time>
      <div className="entry__body">
        <p className="entry__description">{entry.description}</p>
        {entry.note && (
          <p className="entry__note">
            <NoteIcon size={14} />
            <span><span className="visually-hidden">Note: </span>{entry.note}</span>
          </p>
        )}
        {entry.versions.length > 0 && (
          <ul className="entry__artifacts plain-list" aria-label="Artifacts">
            {entry.versions.map(version => (
              <li key={`${version.artifactId}-${version.ordinal}`}>
                <Link className="artifact-chip" to={`/projects/${projectId}/artifacts/${version.artifactId}`}>
                  <FileIcon size={13} />
                  <span>{version.artifactName}</span>
                  <span className="artifact-chip__version">v{version.ordinal}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </div>
    </article>
  );
}

/**
 * An artifact's version links back to the entry that produced it as `#entry-<id>`. The router does
 * not scroll to a hash on its own, and the entry only exists once its page has loaded.
 */
function useScrollToHashTarget(loadedCount: number) {
  const { hash } = useLocation();
  useEffect(() => {
    if (hash) {
      document.getElementById(decodeURIComponent(hash.slice(1)))?.scrollIntoView({ block: 'center' });
    }
  }, [hash, loadedCount]);
}
