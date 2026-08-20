import { McpServer } from '@modelcontextprotocol/sdk/server/mcp.js';
import { NowtaskClient } from './client.js';
import { registerResources } from './resources.js';
import { registerAllTools } from './tools.js';

export function createServer(client: NowtaskClient): McpServer {
  const server = new McpServer(
    { name: 'nowtask', version: '0.1.0' },
    {
      instructions: [
        'nowtask is a task board: tasks live in a workflow of statuses, carry labels, subtasks, comments and automation rules.',
        'Tasks are addressed by their key, such as NOW-172, never by UUID. People, statuses and epics are addressed by UUID, which you get from nowtask_workspace.',
        'Start with nowtask_whoami to learn which scopes this API key has, then nowtask_workspace to learn the ids.',
        'Every write you make is recorded in the task history and the audit log as done by an agent, under the label of the API key, so humans can tell your changes from theirs.',
        'Deleting is irreversible and has no undo. Prefer moving a task to a done status over deleting it.'
      ].join(' ')
    }
  );

  registerAllTools(server, client);
  registerResources(server, client);
  return server;
}
