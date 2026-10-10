import { useMutation, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { describeError } from '../api/client.ts';
import { issueKey, keysKey, revokeKey, useKeys } from '../api/keys.ts';
import type { ApiKeyView, IssuedKeyResponse } from '../api/types.ts';
import { CheckIcon } from '../components/icons.tsx';
import { SecretExportLine } from '../components/SecretExportLine.tsx';
import { Topbar } from '../components/Topbar.tsx';
import { formatShortDate, formatSince } from '../lib/format.ts';

export function KeysPage() {
  const keys = useKeys();
  const [name, setName] = useState('');
  // Held only in memory: once dismissed or navigated away from, the plaintext is gone for good.
  const [issued, setIssued] = useState<IssuedKeyResponse | null>(null);
  const queryClient = useQueryClient();

  const issuing = useMutation({
    mutationFn: () => issueKey(name.trim()),
    onSuccess: async issuedKey => {
      setIssued(issuedKey);
      setName('');
      await queryClient.invalidateQueries({ queryKey: keysKey });
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    issuing.mutate();
  }

  return (
    <>
      <title>API keys · Retrace</title>
      <Topbar parent="Settings" title="API keys" />

      <div className="content content--narrow">
        <p className="keys-intro">
          Claude Code sends one of these keys with every entry it records. A key is shown once, when it is issued
          — after that only its ID is kept.
        </p>

        <form className="key-form" onSubmit={submit}>
          <div className="field">
            <label className="field__label" htmlFor="key-name">New key name</label>
            <input className="input" id="key-name" name="name" type="text" placeholder="e.g. work laptop"
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
            <SecretExportLine apiKey={issued.apiKey} />
            <button className="button button--ghost key-issued__dismiss" type="button" onClick={() => setIssued(null)}>
              I’ve saved it
            </button>
          </div>
        )}

        {keys.isError && <p className="form-error" role="alert">{keys.error.message}</p>}
        {keys.data?.length === 0 && (
          <p className="empty-state">No active keys. Issue one for each machine Claude Code runs on.</p>
        )}
        {keys.data && keys.data.length > 0 && <KeysTable apiKeys={keys.data} />}
      </div>
    </>
  );
}

function KeysTable({ apiKeys }: { apiKeys: ApiKeyView[] }) {
  const queryClient = useQueryClient();
  const revocation = useMutation({
    mutationFn: (keyId: string) => revokeKey(keyId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: keysKey }),
  });

  function confirmRevocation(apiKey: ApiKeyView) {
    if (window.confirm(`Revoke “${apiKey.name}”? Anything still using it will be refused from now on.`)) {
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
