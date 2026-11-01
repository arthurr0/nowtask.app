type Translate = (key: string, params?: Record<string, string | number>) => string;

interface ErrorBody {
  status?: number;
  error?: { code?: string; message?: string };
}

export function errorStatus(error: unknown): number {
  return typeof error === 'object' && error !== null ? ((error as ErrorBody).status ?? 0) : 0;
}

export function errorCode(error: unknown): string {
  return typeof error === 'object' && error !== null
    ? ((error as ErrorBody).error?.code ?? '')
    : '';
}

export function instanceErrorMessage(error: unknown, t: Translate): string {
  const code = errorCode(error);
  if (code === 'SELF_ACTION') return t('instance.error.selfAction');
  if (code === 'LAST_INSTANCE_ADMIN') return t('instance.error.lastAdmin');
  if (code === 'MAIL_NOT_CONFIGURED') return t('instance.error.mailNotConfigured');
  if (code === 'MAIL_SEND_FAILED') return t('instance.error.mailSendFailed');
  if (code === 'ANNOUNCEMENT_TOO_LONG') return t('instance.error.announcementTooLong');
  if (code === 'SLUG_MISMATCH') return t('instance.error.slugMismatch');
  if (errorStatus(error) === 409) return t('instance.error.conflict');
  const message =
    typeof error === 'object' && error !== null ? (error as ErrorBody).error?.message : '';
  return message || t('common.actionFailed');
}

export function pageCount(total: number, size: number): number {
  return Math.max(1, Math.ceil(total / Math.max(1, size)));
}

function pad(value: number): string {
  return String(value).padStart(2, '0');
}

export function isoToLocalInput(iso: string | null): string {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
}

export function localInputToIso(value: string): string | null {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}
