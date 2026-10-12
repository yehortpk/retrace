import { CheckIcon, CopyIcon } from './icons.tsx';
import { useClipboard } from './useClipboard.ts';

interface CopyableSecretProps {
  text: string;
  /** What is being copied, for the button's accessible name. */
  label: string;
  isMuted?: boolean;
}

/** Text a person has to copy exactly — a key, or the file that carries one — with a copy button. */
export function CopyableSecret({ text, label, isMuted = false }: CopyableSecretProps) {
  const { isCopied, copyText } = useClipboard();
  const isMultiline = text.includes('\n');

  return (
    <div className={isMuted ? 'secret secret--muted' : 'secret'}>
      {isMultiline
        ? <pre className="secret__value secret__value--block"><code>{text}</code></pre>
        : <code className="secret__value">{text}</code>}
      <button className="button button--outline button--small" type="button" aria-label={`Copy ${label}`}
              onClick={() => copyText(text)}>
        {isCopied ? <CheckIcon size={13} /> : <CopyIcon size={13} />}
        {isCopied ? 'Copied' : 'Copy'}
      </button>
    </div>
  );
}
