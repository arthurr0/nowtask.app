#!/usr/bin/env node
import { randomUUID } from 'node:crypto';
import { createServer as createHttpServer, type IncomingMessage, type ServerResponse } from 'node:http';
import { StdioServerTransport } from '@modelcontextprotocol/sdk/server/stdio.js';
import { StreamableHTTPServerTransport } from '@modelcontextprotocol/sdk/server/streamableHttp.js';
import { NowtaskClient } from './client.js';
import { ConfigError, loadConfig, type ServerConfig } from './config.js';
import { createServer } from './server.js';

const MCP_PATH = '/mcp';

function log(message: string): void {
  process.stderr.write(`[nowtask-mcp] ${message}\n`);
}

async function runStdio(config: ServerConfig, client: NowtaskClient): Promise<void> {
  const server = createServer(client);
  await server.connect(new StdioServerTransport());
  log(`gotowy na stdio, API: ${config.apiUrl}`);
}

async function readBody(request: IncomingMessage): Promise<unknown> {
  const chunks: Buffer[] = [];
  for await (const chunk of request) {
    chunks.push(chunk as Buffer);
  }
  if (chunks.length === 0) {
    return undefined;
  }
  const raw = Buffer.concat(chunks).toString('utf8');
  if (raw.trim() === '') {
    return undefined;
  }
  return JSON.parse(raw);
}

function reject(response: ServerResponse, status: number, message: string): void {
  response.writeHead(status, { 'Content-Type': 'application/json' });
  response.end(JSON.stringify({ jsonrpc: '2.0', error: { code: -32000, message }, id: null }));
}

function bearerToken(request: IncomingMessage): string | null {
  const header = request.headers['authorization'];
  if (typeof header !== 'string') {
    return null;
  }
  const match = /^Bearer\s+(.+)$/i.exec(header.trim());
  return match === null ? null : match[1]!.trim();
}

async function runHttp(config: ServerConfig): Promise<void> {
  const transports = new Map<string, StreamableHTTPServerTransport>();

  const httpServer = createHttpServer((request, response) => {
    void (async () => {
      const url = new URL(request.url ?? '/', `http://${request.headers.host ?? 'localhost'}`);

      if (url.pathname === '/health') {
        response.writeHead(200, { 'Content-Type': 'application/json' });
        response.end(JSON.stringify({ status: 'ok', sessions: transports.size, api: config.apiUrl }));
        return;
      }

      if (url.pathname !== MCP_PATH) {
        reject(response, 404, `Unknown path ${url.pathname}. The MCP server listens on ${MCP_PATH}.`);
        return;
      }

      try {
        const sessionId = request.headers['mcp-session-id'];
        const existing = typeof sessionId === 'string' ? transports.get(sessionId) : undefined;

        if (existing) {
          await existing.handleRequest(request, response, request.method === 'POST' ? await readBody(request) : undefined);
          return;
        }

        if (request.method !== 'POST') {
          reject(response, 400, 'Missing mcp-session-id header. Start with an initialize request over POST.');
          return;
        }

        const apiKey = bearerToken(request) ?? config.apiKey;
        if (apiKey === null) {
          reject(
            response,
            401,
            'Missing API key. Attach the header Authorization: Bearer nt_... You generate a key in nowtask: AI agents -> New key.'
          );
          return;
        }
        if (!apiKey.startsWith('nt_')) {
          reject(response, 401, 'The Authorization header does not carry a nowtask key. A valid key starts with "nt_".');
          return;
        }

        const body = await readBody(request);
        const transport = new StreamableHTTPServerTransport({
          sessionIdGenerator: () => randomUUID(),
          enableDnsRebindingProtection: true,
          allowedHosts: config.allowedHosts,
          onsessioninitialized: id => {
            transports.set(id, transport);
            log(`new HTTP session ${id}`);
          }
        });

        transport.onclose = () => {
          if (transport.sessionId) {
            transports.delete(transport.sessionId);
            log(`HTTP session ${transport.sessionId} closed`);
          }
        };

        const client = new NowtaskClient(config, apiKey);
        if ((await client.refreshIdentity()) === null) {
          reject(
            response,
            401,
            `nowtask did not confirm this key at ${config.apiUrl}. The key is invalid, revoked or expired, or the backend is not responding.`
          );
          return;
        }

        await createServer(client).connect(transport);
        await transport.handleRequest(request, response, body);
      } catch (error) {
        const reason = error instanceof Error ? error.message : String(error);
        log(`HTTP request handling error: ${reason}`);
        if (!response.headersSent) {
          reject(response, 500, `The MCP server did not handle the request: ${reason}`);
        }
      }
    })();
  });

  await new Promise<void>(resolve => httpServer.listen(config.httpPort, config.httpHost, resolve));
  log(`ready on Streamable HTTP: http://${config.httpHost}:${config.httpPort}${MCP_PATH}, API: ${config.apiUrl}`);
}

async function main(): Promise<void> {
  let config: ServerConfig;
  try {
    config = loadConfig();
  } catch (error) {
    if (error instanceof ConfigError) {
      log(`Configuration error: ${error.message}`);
      process.exit(1);
    }
    throw error;
  }

  if (config.transport === 'stdio') {
    const client = new NowtaskClient(config, config.apiKey!);
    const identity = await client.refreshIdentity();
    if (identity === null) {
      log(
        `Warning: the key could not be confirmed at ${config.apiUrl}. The server will start, but the tools will return errors until the API responds.`
      );
    } else {
      log(`key "${identity.label}" (${identity.prefix}), scopes: ${identity.scopes.join(', ') || 'none'}`);
    }
    await runStdio(config, client);
    return;
  }

  log(
    config.apiKey === null
      ? 'HTTP mode: every client brings its own key in the Authorization header'
      : 'HTTP mode: a client may bring its own key in the Authorization header, otherwise the key from the environment is used'
  );
  await runHttp(config);
}

main().catch(error => {
  log(`Serwer nie wystartowal: ${error instanceof Error ? error.stack ?? error.message : String(error)}`);
  process.exit(1);
});
