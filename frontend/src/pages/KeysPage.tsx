import { useMutation, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { useParams } from 'react-router';
import { describeError } from '../api/client.ts';
import { issueKey, keysKey, revokeKey, useKeys } from '../api/keys.ts';
import { useProject } from '../api/projects.ts';
import type { ApiKeyView, IssuedKeyResponse } from '../api/types.ts';
import { CheckIcon } from '../components/icons.tsx';
import { RepoConnection } from '../components/RepoConnection.tsx';
import { Topbar } from '../components/Topbar.tsx';
import { formatShortDate, formatSince } from '../lib/format.ts';

/** A project's keys. Each one connects a repository to this project, and reaches nothing else. */
export function KeysPage() {
  const { projectId = '' } = useParams();
  const { project, isLoading } = useProject(projectId);

  if (isLoading) {
    return null;
  }
  if (!project) {
    return (
      <>
        <title>Project not found · Retrace</title>
        <Topbar title="Project not found" />
      </>
    );
  }
  // Keyed on the project, so a key issued for one project is never left on screen under another.
  return <ProjectKeys key={project.id} projectId={project.id} projectName={project.name} />;
}

function ProjectKeys({ projectId, projectName }: { projectId: string; projectName: string }) {
  const keys = useKeys(projectId);
  const [name, setName] = useState('');
  // Held only in memory: once dismissed or navigated away from, the plaintext is gone for good.
  const [issued, setIssued] = useState<IssuedKeyResponse | null>(null);
  const queryClient = useQueryClient();

  const issuing = useMutation({
    mutationFn: () => issueKey(projectId, name.trim()),
    onSuccess: async issuedKey => {
      setIssued(issuedKey);
      setName('');
      await queryClient.invalidateQueries({ queryKey: keysKey(projectId) });
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    issuing.mutate();
  }

  return (
    <>
      <title>{`Keys · ${projectName} · Retrace`}</title>
      <Topbar parent={projectName} title="Keys" />

      <div className="content content--narrow">
        <p className="keys-intro">
          A key connects one repository to this project: your agent’s plugin sends it with every entry it records,
          and it reaches this project and no other. A key is shown once, when it is issued — after that only its ID
          is kept.
        </p>

        <form className="key-form" onSubmit={submit}>
          <div className="field">
            <label className="field__label" htmlFor="key-name">New key name</label>
            <input className="input" id="key-name" name="name" type="text" placeholder="e.g. work laptop checkout"
                   maxLength={100} required value={name} onChange={event => setName(event.target.value)} />
          </div>
          <button className="button button--primary button--medium" type="submit" disabled={issuing.isPending}>
            {issuing.isPending ? 'Issuing…' : 'Issue key'}
          </button>
        </form>
        {issuing.isError && <p className="form-error" role="alert">{describeError(issuing.error)}</p>}

        {issued && (
          <div className="key-issued" role="status">
            <div className="key-issued__title">
              <CheckIcon />
              <span>Key “{issued.name}” issued</span>
            </div>
            <p className="key-issued__warning">
              Copy it now. It can’t be shown again — if it’s lost, issue a new one and revoke this one.
            </p>
            <RepoConnection apiKey={issued.apiKey} />
            <button className="button button--ghost key-issued__dismiss" type="button" onClick={() => setIssued(null)}>
              I’ve saved it
            </button>
          </div>
        )}

        {keys.isError && <p className="form-error" role="alert">{keys.error.message}</p>}
        {keys.data?.length === 0 && (
          <p className="empty-state">
            No active keys. Issue one for each checkout of the repository an agent works in.
          </p>
        )}
        {keys.data && keys.data.length > 0 && <KeysTable projectId={projectId} apiKeys={keys.data} />}
      </div>
    </>
  );
}

function KeysTable({ projectId, apiKeys }: { projectId: string; apiKeys: ApiKeyView[] }) {
  const queryClient = useQueryClient();
  const revocation = useMutation({
    mutationFn: (keyId: string) => revokeKey(projectId, keyId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keysKey(projectId) }),
  });

  function confirmRevocation(apiKey: ApiKeyView) {
    if (window.confirm(`Revoke “${apiKey.name}”? The repository using it will be refused from now on.`)) {
      revocation.mutate(apiKey.id);
    }
  }

  return (
    <>
      {revocation.isError && <p className="form-error" role="alert">{describeError(revocation.error)}</p>}
      <div className="keys-table-scroller">
        <table className="keys-table">
          <colgroup>
            <col />
            <col />
            <col className="keys-table__created" />
            <col className="keys-table__last-used" />
            <col className="keys-table__actions" />
          </colgroup>
          <thead>
            <tr>
              <th scope="col">Name</th>
              <th scope="col">Key ID</th>
              <th scope="col">Created</th>
              <th scope="col">Last used</th>
              <th scope="col"><span className="visually-hidden">Actions</span></th>
            </tr>
          </thead>
          <tbody>
            {apiKeys.map(apiKey => (
              <tr key={apiKey.id}>
                <td className="keys-table__name">{apiKey.name}</td>
                <td className="keys-table__id">{apiKey.id}</td>
                <td>{formatShortDate(apiKey.createdAt)}</td>
                <td>{apiKey.lastUsedAt ? formatSince(apiKey.lastUsedAt) : 'Never'}</td>
                <td>
                  <button className="button button--danger button--small" type="button"
                          aria-label={`Revoke ${apiKey.name}`} disabled={revocation.isPending}
                          onClick={() => confirmRevocation(apiKey)}>
                    Revoke
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </>
  );
}
