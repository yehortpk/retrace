import type { CSSProperties } from 'react';

// Projects have no stored colour, so one is derived from the id: stable across sessions and devices,
// and different projects usually land on different colours. The first is the accent from tokens.css.
const SWATCH_COLORS = ['#5B5BD6', '#0B7A70', '#C2410C', '#A21CAF', '#2563EB', '#B45309'];

export function findSwatchColor(projectId: string) {
  let hash = 0;
  for (const char of projectId) {
    hash = (hash * 31 + char.charCodeAt(0)) | 0;
  }
  return SWATCH_COLORS[Math.abs(hash) % SWATCH_COLORS.length];
}

/** The inline style a `.swatch` reads its colour from. */
export function toSwatchStyle(projectId: string) {
  return { '--swatch': findSwatchColor(projectId) } as CSSProperties;
}
