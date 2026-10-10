import type { ReactNode } from 'react';
import { ChevronRightIcon } from './icons.tsx';

interface TopbarProps {
  title: string;
  parent?: string;
  count?: string;
  children?: ReactNode;
}

/** The page header: an optional parent crumb, the page title, a count pill, and actions on the right. */
export function Topbar({ title, parent, count, children }: TopbarProps) {
  return (
    <header className="topbar">
      <div className="topbar__title">
        {parent && (
          <>
            <span className="topbar__parent">{parent}</span>
            <ChevronRightIcon size={14} className="topbar__separator" />
          </>
        )}
        <h1 className="page-title">{title}</h1>
        {count && <span className="count-pill">{count}</span>}
      </div>
      {children}
    </header>
  );
}
