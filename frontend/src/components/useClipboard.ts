import { useEffect, useState } from 'react';

/** Copies text and reports `isCopied` for a moment afterwards, so a button can say it worked. */
export function useClipboard() {
  const [isCopied, setIsCopied] = useState(false);

  useEffect(() => {
    if (!isCopied) {
      return;
    }
    const timer = window.setTimeout(() => setIsCopied(false), 1600);
    return () => window.clearTimeout(timer);
  }, [isCopied]);

  async function copyText(text: string) {
    try {
      await navigator.clipboard.writeText(text);
      setIsCopied(true);
    } catch {
      // Clipboard access denied or unavailable (e.g. non-HTTPS page): leave the button as is.
      setIsCopied(false);
    }
  }

  return { isCopied, copyText };
}
