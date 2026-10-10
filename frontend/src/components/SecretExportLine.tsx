import { CheckIcon, CopyIcon } from './icons.tsx';
import { useClipboard } from './useClipboard.ts';

const API_KEY_VARIABLE = 'PROJECT_MEMORY_API_KEY';

interface SecretExportLineProps {
  apiKey: string;
  isMuted?: boolean;
}

/** A key shown once, as the shell line that installs it, with a copy button. */
export function SecretExportLine({ apiKey, isMuted = false }: SecretExportLineProps) {
  const { isCopied, copyText } = useClipboard();
  const exportLine = `export ${API_KEY_VARIABLE}=${apiKey}`;

  return (
    <div className={isMuted ? 'secret secret--muted' : 'secret'}>
      <code className="secret__value">{exportLine}</code>
      <button className="button button--outline button--small" type="button" onClick={() => copyText(exportLine)}>
        {isCopied ? <CheckIcon size={13} /> : <CopyIcon size={13} />}
        {isCopied ? 'Copied' : 'Copy'}
      </button>
    </div>
  );
}
