import { McpServer, ResourceTemplate } from '@modelcontextprotocol/sdk/server/mcp.js';
import { ApiError, NowtaskClient } from './client.js';
import { type Scope } from './scopes.js';

interface TaskSummary {
  key: string;
  title: string;
}

async function readJson(client: NowtaskClient, path: string, scope: Scope): Promise<unknown | string> {
  const missing = client.missingScopeMessage(scope);
  if (missing !== null) {
    return missing;
  }
  try {
    return await client.request(path);
  } catch (error) {
    return error instanceof ApiError ? error.agentMessage : String(error);
  }
}

export function registerResources(server: McpServer, client: NowtaskClient): void {
  server.registerResource(
    'nowtask-workspace',
    'nowtask://workspace',
    {
      title: 'nowtask workspace configuration',
      description:
        'Projects, statuses, people, epics and saved views of the workspace. Attach it when you need stable ids or the exact status names.',
      mimeType: 'application/json'
    },
    async uri => ({
      contents: [
        {
          uri: uri.href,
          mimeType: 'application/json',
          text: JSON.stringify(await readJson(client, '/api/bootstrap', 'workspace:read'), null, 2)
        }
      ]
    })
  );

  server.registerResource(
    'nowtask-task',
    new ResourceTemplate('nowtask://task/{key}', {
      list: async () => {
        const missing = client.missingScopeMessage('tasks:read');
        if (missing !== null) {
          return { resources: [] };
        }
        try {
          const page = await client.request<{ items: TaskSummary[] }>('/api/tasks', { query: { size: 50, page: 0 } });
          return {
            resources: page.items.map(task => ({
              uri: `nowtask://task/${task.key}`,
              name: `${task.key} ${task.title}`,
              mimeType: 'application/json'
            }))
          };
        } catch {
          return { resources: [] };
        }
      }
    }),
    {
      title: 'nowtask task',
      description:
        'One task addressed by its key, for example nowtask://task/NOW-172. Attach it when you want to quote a task verbatim in an answer instead of paraphrasing a tool result.',
      mimeType: 'application/json'
    },
    async (uri, variables) => {
      const key = Array.isArray(variables['key']) ? variables['key'][0] : variables['key'];
      return {
        contents: [
          {
            uri: uri.href,
            mimeType: 'application/json',
            text: JSON.stringify(await readJson(client, `/api/tasks/${encodeURIComponent(String(key))}`, 'tasks:read'), null, 2)
          }
        ]
      };
    }
  );
}
