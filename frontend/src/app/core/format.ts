export function shortDate(iso: string | null): string {
  if (!iso) return '—';
  const [, month, day] = iso.slice(0, 10).split('-');
  return `${day}.${month}`;
}

export function fullDate(iso: string | null): string {
  if (!iso) return '—';
  const [year, month, day] = iso.slice(0, 10).split('-');
  return `${day}.${month}.${year}`;
}

export function dateTime(iso: string | null): string {
  if (!iso) return '—';
  const [datePart, timePart = ''] = iso.split('T');
  return `${shortDate(datePart)} ${timePart.slice(0, 5)}`.trim();
}

export function timeOnly(iso: string | null): string {
  if (!iso) return '';
  const [, timePart = ''] = iso.split('T');
  return timePart.slice(0, 5);
}

export function isOverdue(due: string | null, today: string): boolean {
  if (!due) return false;
  return due.slice(0, 10) <= today;
}

export function daysBetween(fromIso: string, toIso: string): number {
  const from = Date.parse(`${fromIso.slice(0, 10)}T00:00:00Z`);
  const to = Date.parse(`${toIso.slice(0, 10)}T00:00:00Z`);
  return Math.round((to - from) / 86_400_000);
}

export function money(value: number): string {
  return `${value.toLocaleString('pl-PL')} PLN`;
}
