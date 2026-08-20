import { SCOPES, type Scope } from './scopes.js';

export interface ServerConfig {
  apiUrl: string;
  apiKey: string | null;
  transport: 'stdio' | 'http';
  httpHost: string;
  httpPort: number;
  allowedHosts: string[];
  timeoutMs: number;
}

export class ConfigError extends Error {}

function requiredEnv(name: string, hint: string): string {
  const value = process.env[name];
  if (value === undefined || value.trim() === '') {
    throw new ConfigError(`The ${name} environment variable is missing. ${hint}`);
  }
  return value.trim();
}

function optionalKey(): string | null {
  const value = process.env['NOWTASK_API_KEY'];
  return value === undefined || value.trim() === '' ? null : value.trim();
}

function optionalNumber(name: string, fallback: number): number {
  const raw = process.env[name];
  if (raw === undefined || raw.trim() === '') {
    return fallback;
  }
  const parsed = Number(raw);
  if (!Number.isFinite(parsed) || parsed <= 0) {
    throw new ConfigError(`The ${name} variable has to be a positive number, and it is "${raw}"`);
  }
  return parsed;
}

export function loadConfig(): ServerConfig {
  const transportRaw = (process.env['NOWTASK_MCP_TRANSPORT'] ?? 'stdio').trim().toLowerCase();
  if (transportRaw !== 'stdio' && transportRaw !== 'http') {
    throw new ConfigError(
      `NOWTASK_MCP_TRANSPORT has to be "stdio" or "http", and it is "${transportRaw}".`
    );
  }

  // On stdio the server serves one agent and the key has to be in the environment. Over HTTP the
  // server serves many clients at once, so each brings its own key in the Authorization header,
  // and the key from the environment is only an optional fallback.
  const apiKey =
    transportRaw === 'stdio'
      ? requiredEnv(
          'NOWTASK_API_KEY',
          'Generate a key in nowtask: AI agents -> New key. A key starts with "nt_".'
        )
      : optionalKey();

  if (apiKey !== null && !apiKey.startsWith('nt_')) {
    throw new ConfigError(
      'NOWTASK_API_KEY does not look like a nowtask key. A valid key starts with "nt_".'
    );
  }

  const apiUrl = (process.env['NOWTASK_API_URL'] ?? 'http://localhost:8081').trim().replace(/\/+$/, '');
  try {
    new URL(apiUrl);
  } catch {
    throw new ConfigError(`NOWTASK_API_URL is not a valid address: "${apiUrl}"`);
  }

  const httpHost = (process.env['NOWTASK_MCP_HTTP_HOST'] ?? '127.0.0.1').trim();
  const httpPort = optionalNumber('NOWTASK_MCP_HTTP_PORT', 8765);
  const extraHosts = (process.env['NOWTASK_MCP_ALLOWED_HOSTS'] ?? '')
    .split(',')
    .map(entry => entry.trim())
    .filter(entry => entry !== '');

  return {
    apiUrl,
    apiKey,
    transport: transportRaw,
    httpHost,
    httpPort,
    allowedHosts: [...new Set([`${httpHost}:${httpPort}`, `localhost:${httpPort}`, `127.0.0.1:${httpPort}`, ...extraHosts])],
    timeoutMs: optionalNumber('NOWTASK_API_TIMEOUT_MS', 15000)
  };
}

export function isKnownScope(value: string): value is Scope {
  return (SCOPES as readonly string[]).includes(value);
}
