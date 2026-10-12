import { BIND_REPO_COMMAND, buildRepoConfig } from '../lib/repo-config.ts';
import { CopyableSecret } from './CopyableSecret.tsx';

interface RepoConnectionProps {
  apiKey: string;
  isMuted?: boolean;
}

/**
 * How to connect a repository with a freshly issued key: the key lives in the repository's own
 * gitignored `.project-memory/config.json`, and nowhere else.
 */
export function RepoConnection({ apiKey, isMuted = false }: RepoConnectionProps) {
  return (
    <ol className="repo-connection">
      <li>
        <span>In the repository’s root, create the directory and keep it out of git:</span>
        <CopyableSecret text={BIND_REPO_COMMAND} label="the setup command" isMuted={isMuted} />
      </li>
      <li>
        <span>Save this as <code>.project-memory/config.json</code>:</span>
        <CopyableSecret text={buildRepoConfig(apiKey)} label="the config file" isMuted={isMuted} />
      </li>
      <li>
        <span>Install the Project Memory plugin for your agent and start a session.</span>
      </li>
    </ol>
  );
}
