import type { ReactNode } from 'react';

// The mockups' inline SVGs, one component each. All are drawn on a 16×16 grid and stroke in
// currentColor, so size and colour come from where they are placed.

interface IconProps {
  size?: number;
  strokeWidth?: number;
  className?: string;
}

interface SvgProps extends IconProps {
  children: ReactNode;
}

function Svg({ size = 16, strokeWidth = 1.5, className, children }: SvgProps) {
  return (
    <svg className={className ? `icon ${className}` : 'icon'} width={size} height={size} viewBox="0 0 16 16" fill="none" stroke="currentColor"
         strokeWidth={strokeWidth} strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      {children}
    </svg>
  );
}

export function BrandIcon(props: IconProps) {
  return <Svg strokeWidth={1.8} {...props}><path d="M3.5 8a4.5 4.5 0 1 0 1.3-3.2" /><path d="M4.5 2.5v2.5H7" /></Svg>;
}

export function GridIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <rect x="2.5" y="2.5" width="4.5" height="4.5" rx="1" />
      <rect x="9" y="2.5" width="4.5" height="4.5" rx="1" />
      <rect x="2.5" y="9" width="4.5" height="4.5" rx="1" />
      <rect x="9" y="9" width="4.5" height="4.5" rx="1" />
    </Svg>
  );
}

export function PlusIcon(props: IconProps) {
  return <Svg {...props}><path d="M8 3v10M3 8h10" /></Svg>;
}

export function KeyIcon(props: IconProps) {
  return <Svg {...props}><circle cx="5.5" cy="10.5" r="3" /><path d="M7.6 8.4L13 3M11 5l1.5 1.5M9.5 6.5L11 8" /></Svg>;
}

export function SignOutIcon(props: IconProps) {
  return <Svg {...props}><path d="M6 13H3.5a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1H6" /><path d="M10 11l3-3-3-3M13 8H6" /></Svg>;
}

export function ChevronRightIcon(props: IconProps) {
  return <Svg {...props}><path d="M6 4l4 4-4 4" /></Svg>;
}

export function TimelineIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <circle cx="3.5" cy="4" r="1" /><circle cx="3.5" cy="8" r="1" /><circle cx="3.5" cy="12" r="1" />
      <path d="M7 4h6M7 8h6M7 12h6" />
    </Svg>
  );
}

export function LayersIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M2.5 5.5L8 2.5l5.5 3L8 8.5z" /><path d="M2.5 8.5L8 11.5l5.5-3" /><path d="M2.5 11L8 14l5.5-3" />
    </Svg>
  );
}

export function TerminalIcon(props: IconProps) {
  return <Svg {...props}><rect x="2" y="3" width="12" height="10" rx="1.5" /><path d="M5 6.5L7 8l-2 1.5M8.5 10H11" /></Svg>;
}

export function NoteIcon(props: IconProps) {
  return <Svg {...props}><path d="M3 4.5h10M3 8h10M3 11.5h6" /></Svg>;
}

export function FileIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M9 2H4.5a1 1 0 0 0-1 1v10a1 1 0 0 0 1 1h7a1 1 0 0 0 1-1V5.5z" /><path d="M9 2v3.5h3.5" />
    </Svg>
  );
}

export function CloseIcon(props: IconProps) {
  return <Svg {...props}><path d="M4 4l8 8M12 4l-8 8" /></Svg>;
}

export function PaperclipIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M13 7.5l-5.2 5.2a3 3 0 0 1-4.3-4.3L9 3a2 2 0 0 1 2.8 2.8L6.4 11.2a1 1 0 0 1-1.4-1.4L10 4.8" />
    </Svg>
  );
}

export function ClockIcon(props: IconProps) {
  return <Svg {...props}><circle cx="8" cy="8" r="5.5" /><path d="M8 5v3l2 1.5" /></Svg>;
}

export function CopyIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <rect x="5.5" y="5.5" width="8" height="8" rx="1.5" />
      <path d="M10.5 5.5V4a1.5 1.5 0 0 0-1.5-1.5H4A1.5 1.5 0 0 0 2.5 4v5A1.5 1.5 0 0 0 4 10.5h1.5" />
    </Svg>
  );
}

export function CheckIcon(props: IconProps) {
  return <Svg strokeWidth={1.8} {...props}><path d="M3 8.5l3 3 7-7" /></Svg>;
}

export function PencilIcon(props: IconProps) {
  return <Svg {...props}><path d="M10.5 3.5l2 2L6 12H4v-2z" /></Svg>;
}

export function DownloadIcon(props: IconProps) {
  return <Svg {...props}><path d="M8 2.5v8M4.5 7L8 10.5 11.5 7M3 13.5h10" /></Svg>;
}

export function TrashIcon(props: IconProps) {
  return (
    <Svg {...props}>
      <path d="M3 4.5h10M6.5 4.5V3h3v1.5M4.5 4.5l.6 8.5a1 1 0 0 0 1 .9h3.8a1 1 0 0 0 1-.9l.6-8.5" />
    </Svg>
  );
}

export function ImageIcon(props: IconProps) {
  return (
    <Svg strokeWidth={1.3} {...props}>
      <rect x="2" y="3" width="12" height="10" rx="1.5" /><circle cx="6" cy="6.5" r="1.2" /><path d="M14 11l-3.5-3.5L4 13" />
    </Svg>
  );
}

export function EntryLinkIcon(props: IconProps) {
  return <Svg {...props}><path d="M4 3v5a2 2 0 0 0 2 2h6M9.5 7.5L12 10l-2.5 2.5" /></Svg>;
}
