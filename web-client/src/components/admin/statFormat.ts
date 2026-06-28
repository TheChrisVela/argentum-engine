/** Shared formatting helpers for stat displays. */

const COLOR_NAMES: Record<string, string> = { W: 'White', U: 'Blue', B: 'Black', R: 'Red', G: 'Green' }

/**
 * Render a WUBRG color-identity string (e.g. "WU") as a readable label. Empty = colorless. Used by
 * both the profile and admin color breakdowns so they read the same way.
 */
export function colorLabel(colors: string): string {
  if (!colors) return 'Colorless'
  const names = [...colors].map((c) => COLOR_NAMES[c]).filter(Boolean)
  return names.length > 0 ? names.join('/') : colors
}
