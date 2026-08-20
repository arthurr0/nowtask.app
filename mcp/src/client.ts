import type { ServerConfig } from './config.js';
import { SCOPE_PURPOSE, type Scope } from './scopes.js';

export interface ApiKeyIdentity {
  prefix: string;
  label: string;
  scopes: string[];
  owner: { id: string; name: string; email: string; role: string } | null;
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly agentMessage: string,
    readonly requiredScope?: string
  ) {
    super(agentMessage);
    this.name = 'ApiError';
  }
}

interface RequestOptions {
  method?: string;
  query?: Record<string, string | number | boolean | undefined | null>;
  body?: unknown;
}

function describeScope(scope: string): string {
  const known = SCOPE_PURPOSE[scope as Scope];
  return known ? `${scope} (${known})` : scope;
}

export class NowtaskClient {
  private identity: ApiKeyIdentity | null = null;

  constructor(
    private readonly config: ServerConfig,
    private readonly apiKey: string
  ) {}

  grantedScopes(): string[] | null {
    return this.identity ? this.identity.scopes : null;
  }

  currentIdentity(): ApiKeyIdentity | null {
    return this.identity;
  }

  missingScopeMessage(required: Scope): string | null {
    const granted = this.grantedScopes();
    if (granted === null || granted.includes(required)) {
      return null;
    }
    return [
      `This tool needs the ${describeScope(required)} scope.`,
      `The configured nowtask API key "${this.identity?.label ?? 'unknown'}" only has: ${granted.join(', ') || '(none)'}.`,
      `Ask a nowtask administrator to issue a key that includes ${required}, then restart this MCP server with the new NOWTASK_API_KEY. Do not retry this tool until then.`
    ].join(' ');
  }

  async refreshIdentity(): Promise<ApiKeyIdentity | null> {
    try {
      this.identity = await this.request<ApiKeyIdentity>('/api/auth/api-key');
      return this.identity;
    } catch {
      this.identity = null;
      return null;
    }
  }

  async request<T>(path: string, options: RequestOptions = {}): Promise<T> {
    const url = new URL(this.config.apiUrl + path);
    for (const [key, value] of Object.entries(options.query ?? {})) {
      if (value !== undefined && value !== null && value !== '') {
        url.searchParams.set(key, String(value));
      }
    }

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.config.timeoutMs);

    let response: Response;
    try {
      response = await fetch(url, {
        method: options.method ?? 'GET',
        headers: {
          Authorization: `Bearer ${this.apiKey}`,
          Accept: 'application/json',
          ...(options.body === undefined ? {} : { 'Content-Type': 'application/json' })
        },
        body: options.body === undefined ? undefined : JSON.stringify(options.body),
        signal: controller.signal
      });
    } catch (error) {
      const reason = error instanceof Error ? error.message : String(error);
      const aborted = error instanceof Error && error.name === 'AbortError';
      throw new ApiError(
        0,
        aborted
          ? `The nowtask API at ${this.config.apiUrl} did not answer within ${this.config.timeoutMs} ms. The backend may be down or overloaded. Report this to the user instead of retrying in a loop.`
          : `Cannot reach the nowtask API at ${this.config.apiUrl}: ${reason}. Check that the backend runs and that NOWTASK_API_URL points at it.`
      );
    } finally {
      clearTimeout(timer);
    }

    const text = await response.text();
    const payload = text.length > 0 ? safeParse(text) : null;

    if (!response.ok) {
      throw this.toApiError(response.status, payload, text, url.pathname);
    }

    return (payload ?? {}) as T;
  }

  private toApiError(status: number, payload: unknown, raw: string, path: string): ApiError {
    const detail = asRecord(payload);
    const apiMessage = typeof detail?.['message'] === 'string' ? detail['message'] : raw.slice(0, 400);
    const requiredScope = typeof detail?.['requiredScope'] === 'string' ? detail['requiredScope'] : undefined;
    const grantedScopes = Array.isArray(detail?.['grantedScopes']) ? (detail['grantedScopes'] as string[]) : [];

    if (status === 401) {
      return new ApiError(
        status,
        `nowtask rejected the API key (401). It is missing, invalid, revoked or expired. A nowtask administrator must issue a new key and restart this MCP server with it. Server message: ${apiMessage}`
      );
    }

    if (status === 403) {
      if (requiredScope) {
        return new ApiError(
          status,
          `nowtask refused the call (403) because the API key lacks the ${describeScope(requiredScope)} scope. The key currently has: ${grantedScopes.join(', ') || '(none)'}. Ask an administrator for a key with ${requiredScope} and restart this server. Do not retry.`,
          requiredScope
        );
      }
      return new ApiError(
        status,
        `nowtask refused the call (403): ${apiMessage}. This path is not open to API keys at all, so no scope will unlock it.`
      );
    }

    if (status === 404) {
      return new ApiError(status, `nowtask found nothing at ${path} (404): ${apiMessage}. Check the task key, rule id or subtask id.`);
    }

    if (status === 400) {
      return new ApiError(status, `nowtask rejected the request body (400): ${apiMessage}. Fix the arguments and call again.`);
    }

    if (status === 422) {
      return new ApiError(status, `nowtask refused the change because it breaks a workspace rule (422): ${apiMessage}. Do not retry with the same values.`);
    }

    if (status >= 500) {
      return new ApiError(status, `The nowtask backend failed with ${status}: ${apiMessage}. This is a server-side problem, report it to the user.`);
    }

    return new ApiError(status, `nowtask answered ${status}: ${apiMessage}`);
  }
}

function safeParse(text: string): unknown {
  try {
    return JSON.parse(text);
  } catch {
    return { message: text };
  }
}

function asRecord(value: unknown): Record<string, unknown> | null {
  return typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : null;
}
