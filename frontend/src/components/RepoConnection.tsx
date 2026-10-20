import { KEY_FILE_NAME, buildBindCommand, buildManualBindCommand } from '../lib/repo-config.ts';
import { CopyableSecret } from './CopyableSecret.tsx';

interface RepoConnectionProps {
  apiKey: string;
  isMuted?: boolean;
}

/**
 * How to connect a repository with a freshly issued key: the key lives in the repository's own
 * gitignored key file, and nowhere else.
 */
export function RepoConnection({ apiKey, isMuted = false }: RepoConnectionProps) {
  return (
    <ol className="repo-connection">
      <li>
        <span>Install the Retrace plugin for your agent.</span>
      </li>
      <li>
        <span>Paste this into the agent, in the repository you want to record:</span>
        <CopyableSecret text={buildBindCommand(apiKey)} label="the bind command" isMuted={isMuted} />
      </li>
      <li>
        <span>
          The agent checks the key, writes it to <code>{KEY_FILE_NAME}</code>, keeps it out of git,
          and tells you which project it bound. Or do it yourself:
        </span>
        <CopyableSecret text={buildManualBindCommand(apiKey)} label="the setup command"
                        isMuted={isMuted} />
      </li>
    </ol>
  );
}
