import type { AnnouncementDto } from './api-types';

const STORAGE_KEY = 'nowtask.announcement.dismissed';

export function announcementVisible(
  announcement: AnnouncementDto | null,
  now: number = Date.now(),
): announcement is AnnouncementDto {
  if (!announcement || !announcement.text.trim()) return false;
  if (!announcement.until) return true;
  const until = Date.parse(announcement.until);
  return Number.isNaN(until) || until > now;
}

export function announcementKey(announcement: AnnouncementDto): string {
  return `${announcement.level}:${announcement.text}`;
}

export function readDismissedAnnouncement(): string | null {
  if (typeof localStorage === 'undefined') return null;
  try {
    return localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

export function writeDismissedAnnouncement(key: string): void {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.setItem(STORAGE_KEY, key);
  } catch {
    return;
  }
}

export function announcementTone(level: string): string {
  if (level === 'critical') return 'border-warn bg-warn-soft text-ink';
  if (level === 'warning') return 'border-signal/50 bg-signal/10 text-ink';
  return 'border-line bg-accent-soft text-ink';
}

export function announcementIconTone(level: string): string {
  if (level === 'critical') return 'text-warn';
  if (level === 'warning') return 'text-signal-ink';
  return 'text-ink-2';
}
