import { useMutation, useQueryClient } from '@tanstack/react-query';
import { type DragEvent, type FormEvent, type KeyboardEvent, useEffect, useRef, useState } from 'react';
import { artifactsKey, useArtifacts } from '../api/artifacts.ts';
import { describeError } from '../api/client.ts';
import { projectsKey } from '../api/projects.ts';
import { recordEntry, timelineKey } from '../api/timeline.ts';
import type { ArtifactAttachment, ArtifactSummary, ProjectSummary } from '../api/types.ts';
import { formatBytes } from '../lib/format.ts';
import { toSwatchStyle } from '../lib/swatch.ts';
import { ChevronRightIcon, ClockIcon, CloseIcon, FileIcon, PaperclipIcon } from './icons.tsx';

const NEW_ARTIFACT = 'new';

interface PendingAttachment {
  key: number;
  file: File;
  /** An existing artifact's id to add a version to, or NEW_ARTIFACT. */
  target: string;
  label: string;
}

interface EntryDialogProps {
  project: ProjectSummary;
  onClose: () => void;
}

/**
 * Records an entry by hand. It posts the same multipart request the agent's curl does; the server
 * cannot tell the two apart, and nothing here tries to make it.
 */
export function EntryDialog({ project, onClose }: EntryDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null);
  const formRef = useRef<HTMLFormElement>(null);
  const descriptionRef = useRef<HTMLInputElement>(null);
  const nextAttachmentKey = useRef(0);
  const [description, setDescription] = useState('');
  const [note, setNote] = useState('');
  const [occurredAt, setOccurredAt] = useState<string | null>(null);
  const [attachments, setAttachments] = useState<PendingAttachment[]>([]);
  const [isDraggingOver, setIsDraggingOver] = useState(false);
  const artifacts = useArtifacts(project.id);
  const queryClient = useQueryClient();

  useEffect(() => {
    // No close() in a cleanup: removing the element closes it, and a close() here would fire a
    // close event during StrictMode's remount and dismiss the dialog the moment it opened.
    const dialog = dialogRef.current;
    if (dialog && !dialog.open) {
      dialog.showModal();
      descriptionRef.current?.focus();
    }
  }, []);

  const recording = useMutation({
    mutationFn: () => recordEntry(project.id, {
      occurredAt: occurredAt ? new Date(occurredAt).toISOString() : null,
      description: description.trim(),
      note: note.trim() || null,
      sessionId: null,
      artifacts: attachments.map(toArtifactAttachment),
    }, attachments.map(attachment => attachment.file)),
    onSuccess: async () => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: timelineKey(project.id) }),
        queryClient.invalidateQueries({ queryKey: projectsKey }),
        queryClient.invalidateQueries({ queryKey: artifactsKey(project.id) }),
      ]);
      onClose();
    },
  });

  function addFiles(files: FileList | null) {
    if (!files) {
      return;
    }
    const added = Array.from(files).map(file => ({
      key: nextAttachmentKey.current++,
      file,
      target: findMatchingArtifact(artifacts.data ?? [], file)?.id ?? NEW_ARTIFACT,
      label: '',
    }));
    setAttachments(current => [...current, ...added]);
  }

  function updateAttachment(key: number, change: Partial<PendingAttachment>) {
    setAttachments(current => current.map(attachment =>
      attachment.key === key ? { ...attachment, ...change } : attachment));
  }

  function removeAttachment(key: number) {
    setAttachments(current => current.filter(attachment => attachment.key !== key));
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!recording.isPending) {
      recording.mutate();
    }
  }

  function submitOnControlEnter(event: KeyboardEvent) {
    if (event.key === 'Enter' && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      formRef.current?.requestSubmit();
    }
  }

  function dropFiles(event: DragEvent) {
    event.preventDefault();
    setIsDraggingOver(false);
    addFiles(event.dataTransfer.files);
  }

  return (
    <dialog className="entry-dialog" ref={dialogRef} aria-labelledby="new-entry-title" onClose={onClose}>
      <form className="entry-dialog__form" ref={formRef} onSubmit={submit} onKeyDown={submitOnControlEnter}>
        <div className="entry-dialog__header">
          <span className="project-tag">
            <span className="swatch swatch--small" style={toSwatchStyle(project.id)} />
            {project.name}
          </span>
          <ChevronRightIcon size={13} />
          <h2 className="entry-dialog__title" id="new-entry-title">New entry</h2>
          <button className="icon-button" type="button" aria-label="Close" onClick={onClose}>
            <CloseIcon size={15} />
          </button>
        </div>

        <label className="visually-hidden" htmlFor="entry-description">What happened?</label>
        <input className="entry-dialog__description" id="entry-description" name="description" type="text"
               ref={descriptionRef} placeholder="What happened?" maxLength={2000} required value={description}
               onChange={event => setDescription(event.target.value)} />
        <label className="visually-hidden" htmlFor="entry-note">Note</label>
        <textarea className="entry-dialog__note" id="entry-note" name="note" rows={3} maxLength={10000}
                  placeholder="Add a note — why it happened, what was decided" value={note}
                  onChange={event => setNote(event.target.value)} />

        <div className="attachments">
          <div className="attachments__title">Attachments</div>

          {attachments.map(attachment => (
            <AttachmentRow key={attachment.key} attachment={attachment} artifacts={artifacts.data ?? []}
                           onChange={change => updateAttachment(attachment.key, change)}
                           onRemove={() => removeAttachment(attachment.key)} />
          ))}

          <div className={isDraggingOver ? 'dropzone dropzone--active' : 'dropzone'}
               onDragOver={event => { event.preventDefault(); setIsDraggingOver(true); }}
               onDragLeave={() => setIsDraggingOver(false)}
               onDrop={dropFiles}>
            <PaperclipIcon size={14} />
            Drop files here or
            <label className="dropzone__browse" htmlFor="entry-files">browse</label>
            <input className="visually-hidden" id="entry-files" name="files" type="file" multiple
                   onChange={event => { addFiles(event.target.files); event.target.value = ''; }} />
          </div>
        </div>

        {recording.isError && (
          <p className="form-error entry-dialog__error" role="alert">{describeError(recording.error)}</p>
        )}

        <div className="entry-dialog__footer">
          {occurredAt === null ? (
            <button className="button button--outline button--small" type="button"
                    title="Set when it happened" onClick={() => setOccurredAt(toLocalInputValue(new Date()))}>
              <ClockIcon size={14} />
              Now
            </button>
          ) : (
            <span className="entry-dialog__occurred-at">
              <label className="visually-hidden" htmlFor="entry-occurred-at">When it happened</label>
              <input className="input input--small" id="entry-occurred-at" type="datetime-local" required
                     autoFocus value={occurredAt} onChange={event => setOccurredAt(event.target.value)} />
              <button className="icon-button icon-button--small" type="button" aria-label="Use the current time"
                      onClick={() => setOccurredAt(null)}>
                <CloseIcon size={13} />
              </button>
            </span>
          )}
          <span className="entry-dialog__shortcut">Ctrl ↵ to record</span>
          <button className="button button--ghost" type="button" onClick={onClose}>Cancel</button>
          <button className="button button--primary" type="submit" disabled={recording.isPending}>
            {recording.isPending ? 'Recording…' : 'Record entry'}
          </button>
        </div>
      </form>
    </dialog>
  );
}

interface AttachmentRowProps {
  attachment: PendingAttachment;
  artifacts: ArtifactSummary[];
  onChange: (change: Partial<PendingAttachment>) => void;
  onRemove: () => void;
}

function AttachmentRow({ attachment, artifacts, onChange, onRemove }: AttachmentRowProps) {
  const { key, file, target, label } = attachment;
  return (
    <div className="attachment">
      <div className="attachment__file">
        <FileIcon size={14} />
        <span className="attachment__name">{file.name}</span>
        <span className="attachment__size">{formatBytes(file.size)}</span>
        <button className="icon-button icon-button--small" type="button" aria-label={`Remove ${file.name}`}
                onClick={onRemove}>
          <CloseIcon size={13} />
        </button>
      </div>
      <div className="attachment__options">
        <label className="visually-hidden" htmlFor={`attachment-${key}-target`}>Save as</label>
        <select className="input input--small attachment__target" id={`attachment-${key}-target`} value={target}
                onChange={event => onChange({ target: event.target.value })}>
          <option value={NEW_ARTIFACT}>New artifact named “{file.name}”</option>
          {artifacts.map(artifact => (
            <option key={artifact.id} value={artifact.id}>
              New version of {artifact.name} (v{artifact.versionCount + 1})
            </option>
          ))}
        </select>
        <label className="visually-hidden" htmlFor={`attachment-${key}-label`}>Version label</label>
        <input className="input input--small attachment__label" id={`attachment-${key}-label`} type="text"
               maxLength={300} placeholder="Version label (optional)" value={label}
               onChange={event => onChange({ label: event.target.value })} />
      </div>
    </div>
  );
}

/**
 * A file dropped in under an artifact's name, or under the filename its current version was stored
 * with, is most likely its next version — so that is offered first. It stays a suggestion: names
 * are labels, not identities, and the select can still make it a new artifact.
 */
function findMatchingArtifact(artifacts: ArtifactSummary[], file: File) {
  return artifacts.find(artifact =>
    artifact.name === file.name || artifact.currentVersion.filename === file.name);
}

function toArtifactAttachment({ file, target, label }: PendingAttachment): ArtifactAttachment {
  const trimmedLabel = label.trim() || undefined;
  return target === NEW_ARTIFACT
    ? { name: file.name, label: trimmedLabel }
    : { artifactId: target, label: trimmedLabel };
}

/** `datetime-local` wants local wall-clock time with no zone, to the minute. */
function toLocalInputValue(date: Date) {
  const offsetMs = date.getTimezoneOffset() * 60_000;
  return new Date(date.getTime() - offsetMs).toISOString().slice(0, 16);
}
