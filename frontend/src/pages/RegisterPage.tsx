import { useMutation, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { Link } from 'react-router';
import { ApiError } from '../api/client.ts';
import { logIn, register } from '../api/auth.ts';
import { AuthLayout } from '../components/AuthLayout.tsx';
import { CheckIcon } from '../components/icons.tsx';
import { SecretExportLine } from '../components/SecretExportLine.tsx';

/**
 * Registration, then the account's first API key — shown exactly once. The key lives only in this
 * component's state: not in the URL, not in history state, so a reload or Back cannot show it again.
 */
export function RegisterPage() {
  const [apiKey, setApiKey] = useState<string | null>(null);

  return (
    <AuthLayout>
      {apiKey === null ? <RegisterForm onRegistered={setApiKey} /> : <AccountReady apiKey={apiKey} />}
    </AuthLayout>
  );
}

function RegisterForm({ onRegistered }: { onRegistered: (apiKey: string) => void }) {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const queryClient = useQueryClient();

  const registration = useMutation({
    mutationFn: async () => {
      const registered = await register({ username, email, password });
      // Registering does not open a session, and the next screen leads into the app, so sign in
      // with the credentials just chosen rather than asking for them a second time. A failure here
      // must not hide the key, which can never be shown again — "continue" then lands on /login.
      await logIn(username, password).catch(() => undefined);
      return registered;
    },
    onSuccess: registered => {
      queryClient.clear();
      onRegistered(registered.apiKey);
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    registration.mutate();
  }

  const fieldErrors = registration.error instanceof ApiError ? registration.error.problem?.errors : undefined;

  return (
    <>
      <title>Create account · Retrace</title>
      <form className="auth-card" onSubmit={submit}>
        <div className="auth-card__header">
          <h1 className="auth-card__title">Create your account</h1>
          <p className="auth-card__lede">You’ll get an API key for Claude Code right after.</p>
        </div>
        <div className="field">
          <label className="field__label" htmlFor="username">Username</label>
          <input className="input input--large" id="username" name="username" type="text" autoComplete="username"
                 maxLength={64} required autoFocus value={username}
                 onChange={event => setUsername(event.target.value)} />
          {fieldErrors?.username && <span className="form-error">{fieldErrors.username}</span>}
        </div>
        <div className="field">
          <label className="field__label" htmlFor="email">Email</label>
          <input className="input input--large" id="email" name="email" type="email" autoComplete="email"
                 maxLength={255} required value={email} onChange={event => setEmail(event.target.value)} />
          {fieldErrors?.email && <span className="form-error">{fieldErrors.email}</span>}
        </div>
        <div className="field">
          <label className="field__label" htmlFor="password">Password</label>
          <input className="input input--large" id="password" name="password" type="password"
                 autoComplete="new-password" minLength={8} maxLength={200} aria-describedby="password-hint" required
                 value={password} onChange={event => setPassword(event.target.value)} />
          <span className="field__hint" id="password-hint">At least 8 characters</span>
          {fieldErrors?.password && <span className="form-error">{fieldErrors.password}</span>}
        </div>
        {registration.isError && !fieldErrors && (
          <p className="form-error" role="alert">{registration.error.message}</p>
        )}
        <button className="button button--primary button--large auth-card__submit" type="submit"
                disabled={registration.isPending}>
          {registration.isPending ? 'Creating account…' : 'Create account'}
        </button>
      </form>
      <p className="auth-switch">Already have an account? <Link className="text-link" to="/login">Sign in</Link></p>
    </>
  );
}

function AccountReady({ apiKey }: { apiKey: string }) {
  return (
    <section className="auth-card auth-card--wide" aria-labelledby="ready-title">
      <title>Account ready · Retrace</title>
      <div className="auth-card__header">
        <h1 className="auth-card__title" id="ready-title">
          <CheckIcon size={18} />
          Your account is ready
        </h1>
        <p className="auth-card__lede">
          This is your API key. Claude Code sends it with every entry it records. <strong>It’s shown only
          now</strong> — copy it before you leave this page.
        </p>
      </div>

      <SecretExportLine apiKey={apiKey} isMuted />

      <ol className="setup-steps">
        <li>Add that line to your shell profile, e.g. <code>~/.zshrc</code></li>
        <li>Create your first project</li>
        <li>Bind a repository to it and install the Claude Code plugin</li>
      </ol>

      <Link className="button button--primary button--large auth-card__continue" to="/?new" replace>
        I’ve saved it — continue
      </Link>
    </section>
  );
}
