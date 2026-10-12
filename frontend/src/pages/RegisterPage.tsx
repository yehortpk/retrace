import { useMutation, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { Link, useNavigate } from 'react-router';
import { ApiError } from '../api/client.ts';
import { logIn, register } from '../api/auth.ts';
import { AuthLayout } from '../components/AuthLayout.tsx';

/**
 * Registration. No key is issued here — a key belongs to a project — so a new account goes straight to
 * the project list with the create form open, where the first project hands out its key.
 */
export function RegisterPage() {
  return (
    <AuthLayout>
      <RegisterForm />
    </AuthLayout>
  );
}

function RegisterForm() {
  const [username, setUsername] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const queryClient = useQueryClient();
  const navigate = useNavigate();

  const registration = useMutation({
    mutationFn: async () => {
      await register({ username, email, password });
      // Registering does not open a session, so sign in with the credentials just chosen rather than
      // asking for them a second time. If that fails the account still exists: send them to sign in.
      return logIn(username, password).then(() => true, () => false);
    },
    onSuccess: async isSignedIn => {
      queryClient.clear();
      await navigate(isSignedIn ? '/?new' : '/login', { replace: true });
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
          <p className="auth-card__lede">
            Next you’ll create a project — it comes with the key that connects your agent.
          </p>
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
