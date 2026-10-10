import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { createBrowserRouter, Navigate } from 'react-router';
import { RouterProvider } from 'react-router/dom';
import { ApiError, setUnauthorizedListener } from './api/client.ts';
import { AppShell } from './components/AppShell.tsx';
import { ArtifactDetailPanel, ArtifactsPage } from './pages/ArtifactsPage.tsx';
import { KeysPage } from './pages/KeysPage.tsx';
import { LoginPage } from './pages/LoginPage.tsx';
import { ProjectsPage } from './pages/ProjectsPage.tsx';
import { RegisterPage } from './pages/RegisterPage.tsx';
import { TimelinePage } from './pages/TimelinePage.tsx';

import './styles/tokens.css';
import './styles/base.css';
import './styles/controls.css';
import './styles/app-shell.css';
import './styles/auth.css';
import './styles/projects.css';
import './styles/timeline.css';
import './styles/entry-dialog.css';
import './styles/artifacts.css';
import './styles/keys.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // A 4xx will not answer differently a second time; only a server or network failure is retried.
      retry: (failureCount, error) => !(error instanceof ApiError && error.status < 500) && failureCount < 2,
    },
  },
});

const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  { path: '/register', element: <RegisterPage /> },
  {
    path: '/',
    element: <AppShell />,
    children: [
      { index: true, element: <ProjectsPage /> },
      { path: 'projects/:projectId', element: <TimelinePage /> },
      {
        // The list stays mounted while artifacts are selected; the detail renders beside it.
        path: 'projects/:projectId/artifacts',
        element: <ArtifactsPage />,
        children: [{ path: ':artifactId', element: <ArtifactDetailPanel /> }],
      },
      { path: 'settings/keys', element: <KeysPage /> },
      { path: '*', element: <Navigate to="/" replace /> },
    ],
  },
]);

setUnauthorizedListener(() => {
  const { pathname, search } = window.location;
  if (pathname === '/login' || pathname === '/register') {
    return;
  }
  const next = encodeURIComponent(pathname + search);
  // Navigate first, so nothing still mounted refetches into the cache as it is being emptied.
  void router.navigate(`/login?next=${next}`, { replace: true }).then(() => queryClient.clear());
});

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
    </QueryClientProvider>
  </StrictMode>,
);
