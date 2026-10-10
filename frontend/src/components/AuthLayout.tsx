import type { ReactNode } from 'react';
import { BrandIcon } from './icons.tsx';

/** Sign in, create account, and the key shown once after registration. */
export function AuthLayout({ children }: { children: ReactNode }) {
  return (
    <main className="auth-page">
      <div className="auth-brand">
        <span className="brand-mark brand-mark--large"><BrandIcon size={20} strokeWidth={1.7} /></span>
        <span>Retrace</span>
      </div>
      {children}
    </main>
  );
}
