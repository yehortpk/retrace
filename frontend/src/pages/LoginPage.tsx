import { useMutation, useQueryClient } from '@tanstack/react-query';
import { type FormEvent, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router';
import { ApiError } from '../api/client.ts';
import { logIn } from '../api/auth.ts';
import { AuthLayout } from '../components/AuthLayout.tsx';

export function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  const login = useMutation({
    mutationFn: () => logIn(username, password),
    onSuccess: async () => {
      // Whatever was cached belonged to whoever was signed in before.
      queryClient.clear();
      await navigate(findReturnPath(searchParams.get('next')), { replace: true });
    },
  });

  function submit(event: FormEvent) {
    event.preventDefault();
    login.mutate();
  }

  return (
    <AuthLayout>
      <title>Sign in · Retrace</title>
      <form className="auth-card" onSubmit={submit}>
        <div className="auth-card__header">
          <h1 className="auth-card__title">Sign in</h1>
        </div>
        <div className="field">
          <label className="field__label" htmlFor="username">Username</label>
          <input className="input input--large" id="username" name="username" type="text" autoComplete="username"
                 required autoFocus value={username} onChange={event => setUsername(event.target.value)} />
        </div>
        <div className="field">
          <label className="field__label" htmlFor="password">Password</label>
          <input className="input input--large" id="password" name="password" type="password"
                 autoComplete="current-password" required value={password}
                 onChange={event => setPassword(event.target.value)} />
        </div>
        {login.isError && (
          <p className="form-error" role="alert">
            {login.error instanceof ApiError && login.error.status === 401
              ? 'That username and password don’t match an account.'
              : login.error.message}
          </p>
        )}
        <button className="button button--primary button--large auth-card__submit" type="submit"
                disabled={login.isPending}>
          {login.isPending ? 'Signing in…' : 'Sign in'}
        </button>
      </form>
      <p className="auth-switch">New here? <Link className="text-link" to="/register">Create an account</Link></p>
    </AuthLayout>
  );
}

/** Only an in-app path is followed, so a crafted `?next=https://…` cannot bounce a sign-in elsewhere. */
function findReturnPath(next: string | null) {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : '/';
}
