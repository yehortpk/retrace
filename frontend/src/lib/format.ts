const DAY_MS = 24 * 60 * 60 * 1000;

const timeFormat = new Intl.DateTimeFormat(undefined, { hour: '2-digit', minute: '2-digit', hourCycle: 'h23' });
const weekdayFormat = new Intl.DateTimeFormat(undefined, { weekday: 'long' });
const weekdayDateFormat = new Intl.DateTimeFormat(undefined, { weekday: 'short', month: 'short', day: 'numeric' });
const shortDateFormat = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric' });
const shortDateWithYearFormat = new Intl.DateTimeFormat(undefined, { month: 'short', day: 'numeric', year: 'numeric' });
const relativeFormat = new Intl.RelativeTimeFormat(undefined, { numeric: 'auto' });

/** 24-hour "14:40", so every time in the timeline's column has the same width. */
export function formatTime(iso: string) {
  return timeFormat.format(new Date(iso));
}

/** "Thu, Oct 1" */
export function formatWeekdayDate(date: Date) {
  return weekdayDateFormat.format(date);
}

/** "Oct 4", with the year only when it is not this one. */
export function formatShortDate(iso: string) {
  const date = new Date(iso);
  return date.getFullYear() === new Date().getFullYear()
    ? shortDateFormat.format(date)
    : shortDateWithYearFormat.format(date);
}

/** "Thu, Oct 1 · 17:45" */
export function formatDateTime(iso: string) {
  return `${formatWeekdayDate(new Date(iso))} · ${formatTime(iso)}`;
}

/** A day heading: "Today", "Yesterday", a weekday within the last week, then a plain date. */
export function formatDayName(date: Date) {
  const daysAgo = Math.round((startOfDay(new Date()).getTime() - startOfDay(date).getTime()) / DAY_MS);
  if (daysAgo === 0) {
    return 'Today';
  }
  if (daysAgo === 1) {
    return 'Yesterday';
  }
  if (daysAgo > 1 && daysAgo < 7) {
    return weekdayFormat.format(date);
  }
  return formatShortDate(date.toISOString());
}

/** "Just now", "2 hours ago", then a date once it is more than a day old. */
export function formatSince(iso: string) {
  const elapsedMs = Date.now() - new Date(iso).getTime();
  const minutes = Math.floor(elapsedMs / 60_000);
  if (minutes < 1) {
    return 'Just now';
  }
  if (minutes < 60) {
    return relativeFormat.format(-minutes, 'minute');
  }
  const hours = Math.floor(minutes / 60);
  if (hours < 24) {
    return relativeFormat.format(-hours, 'hour');
  }
  return formatShortDate(iso);
}

export function formatBytes(bytes: number) {
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  const units = ['KB', 'MB', 'GB'];
  let value = bytes / 1024;
  let unit = 0;
  while (value >= 1024 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  return `${value < 10 ? value.toFixed(1) : Math.round(value)} ${units[unit]}`;
}

export function pluralize(count: number, singular: string, plural = `${singular}s`) {
  return `${count} ${count === 1 ? singular : plural}`;
}

export function startOfDay(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

/** A local calendar day, so entries group by the day the reader lived them. */
export function toDayKey(date: Date) {
  return `${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()}`;
}
