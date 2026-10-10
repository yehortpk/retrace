import { useQuery } from '@tanstack/react-query';
import { findVersionText, toDownloadUrl } from '../api/artifacts.ts';
import type { ArtifactVersionHistoryItem } from '../api/types.ts';
import { FileIcon, ImageIcon } from './icons.tsx';

// Larger text files are not pulled in whole just to show their first screen.
const MAX_TEXT_PREVIEW_BYTES = 256 * 1024;

const TEXT_CONTENT_TYPES = new Set([
  'application/json', 'application/xml', 'application/yaml', 'application/x-yaml', 'application/javascript',
  'application/x-sh', 'application/sql', 'application/toml',
]);

// Uploads often arrive as application/octet-stream, so the filename is the fallback signal.
const TEXT_EXTENSIONS = new Set([
  'txt', 'md', 'markdown', 'json', 'yaml', 'yml', 'toml', 'xml', 'csv', 'sql', 'sh', 'bash', 'zsh', 'env',
  'js', 'jsx', 'ts', 'tsx', 'css', 'html', 'java', 'kt', 'kts', 'py', 'go', 'rs', 'rb', 'properties', 'log',
]);

interface ArtifactPreviewProps {
  projectId: string;
  artifactId: string;
  version: ArtifactVersionHistoryItem;
}

/**
 * Images are shown through an <img>, which renders the download despite its attachment disposition
 * and never runs script, SVG included. Small text files are shown as text. Anything else says so.
 */
export function ArtifactPreview({ projectId, artifactId, version }: ArtifactPreviewProps) {
  const url = toDownloadUrl(projectId, artifactId, version.ordinal);
  const isText = isTextVersion(version) && version.sizeBytes <= MAX_TEXT_PREVIEW_BYTES;
  const text = useQuery({
    queryKey: ['version-text', projectId, artifactId, version.ordinal, version.createdAt],
    queryFn: () => findVersionText(projectId, artifactId, version.ordinal),
    enabled: isText,
    staleTime: Infinity,
  });

  if (version.contentType?.startsWith('image/')) {
    return (
      <div className="artifact-preview">
        <div className="artifact-preview__frame">
          <img className="artifact-preview__image" src={url} alt={`v${version.ordinal} of ${version.filename ?? 'this artifact'}`} />
        </div>
      </div>
    );
  }
  if (isText && text.data !== undefined) {
    return (
      <div className="artifact-preview">
        <pre className="artifact-preview__text">{text.data}</pre>
      </div>
    );
  }
  return (
    <div className="artifact-preview">
      <div className="artifact-preview__frame">
        {isText ? <FileIcon size={22} strokeWidth={1.3} /> : <ImageIcon size={22} />}
        <span>
          {isText && text.isPending && 'Loading preview…'}
          {isText && text.isError && 'The preview could not be loaded'}
          {!isText && `No preview for ${version.contentType ?? 'this file type'}`}
        </span>
      </div>
    </div>
  );
}

function isTextVersion({ contentType, filename }: ArtifactVersionHistoryItem) {
  if (contentType) {
    const mediaType = contentType.split(';')[0].trim().toLowerCase();
    if (mediaType.startsWith('text/') || TEXT_CONTENT_TYPES.has(mediaType)
        || mediaType.endsWith('+json') || mediaType.endsWith('+xml')) {
      return true;
    }
  }
  const extension = filename?.split('.').pop()?.toLowerCase();
  return extension !== undefined && TEXT_EXTENSIONS.has(extension);
}
