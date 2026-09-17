import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { I18nService } from '../../core/i18n/i18n.service';
import { dateTime } from '../../core/format';
import type { AgentDto } from '../../core/api-types';
import { AgentsStore } from '../../data/feature.stores';
import { WorkspaceStore } from '../../data/workspace.store';
import { ConfirmService } from '../../ui/confirm.service';
import { Icon } from '../../ui/icon';
import { Menu, type MenuItem } from '../../ui/menu';
import { PageState } from '../../ui/page-state';
import { TextField } from '../../ui/text-field';
import { ToastService } from '../../ui/toast.service';
import { Topbar } from '../../ui/topbar';
import { ViewControls } from '../../ui/view-controls';
import { ApiKeyDialog, type ApiKeyDraft } from './api-key-dialog';

type ClientId =
  | 'claude-code'
  | 'claude-desktop'
  | 'cursor'
  | 'codex'
  | 'gemini'
  | 'vscode'
  | 'chatgpt'
  | 'docker';
type SetupMode = 'hosted' | 'local';

interface ToolGroup {
  icon: string;
  title: string;
  body: string;
  scopes: readonly string[];
  tools: readonly string[];
}

const TOOL_GROUPS: readonly ToolGroup[] = [
  {
    icon: 'search',
    title: 'agents.groupRead',
    body: 'agents.groupReadBody',
    scopes: ['tasks:read', 'workspace:read'],
    tools: [
      'nowtask_whoami',
      'nowtask_workspace',
      'nowtask_tasks_search',
      'nowtask_task_get',
      'nowtask_task_comments',
      'nowtask_task_history',
      'nowtask_timeline',
      'nowtask_search',
    ],
  },
  {
    icon: 'pencil',
    title: 'agents.groupWrite',
    body: 'agents.groupWriteBody',
    scopes: ['tasks:write'],
    tools: [
      'nowtask_task_create',
      'nowtask_task_update',
      'nowtask_task_set_status',
      'nowtask_task_add_comment',
      'nowtask_task_add_label',
      'nowtask_task_remove_label',
      'nowtask_task_add_relation',
      'nowtask_task_remove_relation',
      'nowtask_task_set_custom_field',
      'nowtask_task_toggle_watch',
    ],
  },
  {
    icon: 'layers',
    title: 'agents.groupBulk',
    body: 'agents.groupBulkBody',
    scopes: ['tasks:write', 'tasks:delete'],
    tools: [
      'nowtask_subtask_add',
      'nowtask_subtask_update',
      'nowtask_subtask_toggle',
      'nowtask_subtask_delete',
      'nowtask_task_delete',
      'nowtask_tasks_bulk_assign',
      'nowtask_tasks_bulk_status',
    ],
  },
  {
    icon: 'bolt',
    title: 'agents.groupRules',
    body: 'agents.groupRulesBody',
    scopes: ['rules:read', 'rules:run', 'rules:write', 'metrics:read'],
    tools: [
      'nowtask_rules_list',
      'nowtask_rule_get',
      'nowtask_rule_runs',
      'nowtask_rule_run',
      'nowtask_rule_toggle',
      'nowtask_metrics_overview',
    ],
  },
];

const SCOPES: readonly { code: string; label: string }[] = [
  { code: 'tasks:read', label: 'agents.scope.tasksRead' },
  { code: 'tasks:write', label: 'agents.scope.tasksWrite' },
  { code: 'tasks:delete', label: 'agents.scope.tasksDelete' },
  { code: 'rules:read', label: 'agents.scope.rulesRead' },
  { code: 'rules:run', label: 'agents.scope.rulesRun' },
  { code: 'rules:write', label: 'agents.scope.rulesWrite' },
  { code: 'metrics:read', label: 'agents.scope.metricsRead' },
  { code: 'workspace:read', label: 'agents.scope.workspaceRead' },
];

const BOUNDARIES: readonly string[] = [
  'agents.boundaryAdmin',
  'agents.boundarySession',
  'agents.boundaryWorkspace',
  'agents.boundaryDefault',
];

interface Client {
  id: ClientId;
  label: string;
  icon: string;
  modes: readonly SetupMode[];
}

const CLIENTS: readonly Client[] = [
  { id: 'claude-code', label: 'Claude Code', icon: 'grip', modes: ['hosted', 'local'] },
  { id: 'claude-desktop', label: 'Claude Desktop', icon: 'agent', modes: ['hosted', 'local'] },
  { id: 'codex', label: 'Codex CLI', icon: 'shell', modes: ['hosted', 'local'] },
  { id: 'chatgpt', label: 'ChatGPT', icon: 'message', modes: ['local'] },
  { id: 'cursor', label: 'Cursor', icon: 'pencil', modes: ['hosted', 'local'] },
  { id: 'gemini', label: 'Gemini CLI', icon: 'bolt', modes: ['hosted', 'local'] },
  { id: 'vscode', label: 'VS Code', icon: 'columns', modes: ['hosted', 'local'] },
  { id: 'docker', label: 'Docker', icon: 'layers', modes: ['local'] },
];

const JSON_CLIENTS: readonly ClientId[] = ['claude-desktop', 'cursor', 'vscode', 'docker'];
const DOCKER_CLIENTS: readonly ClientId[] = ['docker', 'chatgpt'];
const CHATGPT_STEPS = ['agents.chatGpt1', 'agents.chatGpt2', 'agents.chatGpt3'] as const;

const MODES: readonly { id: SetupMode; label: string; body: string; icon: string }[] = [
  { id: 'hosted', label: 'agents.modeHosted', body: 'agents.modeHostedBody', icon: 'link' },
  { id: 'local', label: 'agents.modeLocal', body: 'agents.modeLocalBody', icon: 'layers' },
];

const REPO = 'https://github.com/arthurr0/nowtask.app.git';
const DOCS = 'https://github.com/arthurr0/nowtask.app/blob/master/docs/ai-agents.md';
const SERVER_DOCS = 'https://github.com/arthurr0/nowtask.app/tree/master/mcp';
const DEFAULT_PATH = '~/nowtask.app/mcp';
const KEY_PLACEHOLDER = 'nt_your_key';

@Component({
  selector: 'app-agents',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [Icon, Menu, Topbar, ViewControls, PageState, TextField, ApiKeyDialog],
  templateUrl: './agents.html',
})
export class Agents implements OnInit {
  protected readonly store = inject(WorkspaceStore);
  protected readonly agents = inject(AgentsStore);
  private readonly confirm = inject(ConfirmService);
  private readonly toast = inject(ToastService);
  protected readonly t = inject(I18nService).t;

  protected readonly groups = TOOL_GROUPS;
  protected readonly scopes = SCOPES;
  protected readonly boundaries = BOUNDARIES;
  protected readonly modes = MODES;
  protected readonly repo = REPO;
  protected readonly docs = DOCS;
  protected readonly serverDocs = SERVER_DOCS;
  protected readonly transports = ['stdio', 'Streamable HTTP'] as const;

  protected readonly dateTime = dateTime;
  protected readonly apiUrl = location.origin;

  protected readonly toolCount = TOOL_GROUPS.reduce(
    (total, group) => total + group.tools.length,
    0,
  );

  protected readonly mode = signal<SetupMode>('hosted');
  protected readonly client = signal<ClientId>('claude-code');

  protected readonly clients = computed(() =>
    CLIENTS.filter((item) => item.modes.includes(this.mode())),
  );

  protected readonly chatGptSteps = CHATGPT_STEPS;
  protected readonly isChatGpt = computed(() => this.client() === 'chatgpt');

  protected readonly isHosted = computed(() => this.mode() === 'hosted');
  protected readonly mcpUrl = `${location.origin}/mcp`;
  protected readonly serverPath = signal(DEFAULT_PATH);
  protected readonly keyOpen = signal(false);
  protected readonly issuedKey = signal<string | null>(null);

  protected readonly isAdmin = computed(() => this.store.currentUser()?.role?.code === 'admin');

  protected readonly activeKey = computed(() => this.issuedKey() ?? KEY_PLACEHOLDER);

  protected readonly newestAgent = computed<AgentDto | null>(() => {
    const active = this.agents.activeAgents();
    if (!active.length) return null;
    return active.reduce((newest, agent) =>
      (agent.lastUsedAt ?? '') > (newest.lastUsedAt ?? '') ? agent : newest,
    );
  });

  protected readonly connected = computed(() =>
    this.agents.activeAgents().some((agent) => agent.lastUsedAt !== null),
  );

  protected readonly buildSnippet = computed(() => {
    const path = this.trimmedPath();
    if (DOCKER_CLIENTS.includes(this.client())) {
      return [`git clone ${REPO}`, `docker build -t nowtask-mcp ${path}`].join('\n');
    }
    return [`git clone ${REPO}`, `cd ${path}`, 'npm install', 'npm run build'].join('\n');
  });

  protected readonly configSnippet = computed(() =>
    this.isHosted() ? this.hostedSnippet() : this.localSnippet(),
  );

  protected readonly configIsJson = computed(() => JSON_CLIENTS.includes(this.client()));

  protected readonly configHint = computed(() => {
    const client = this.client();
    if (this.isHosted()) {
      switch (client) {
        case 'claude-code':
          return this.t('agents.hintHostedClaudeCode');
        case 'codex':
          return this.t('agents.hintHostedCodex');
        case 'gemini':
          return this.t('agents.hintHostedGemini');
        case 'vscode':
          return this.t('agents.hintVsCode');
        default:
          return this.t('agents.hintHostedFile');
      }
    }
    switch (client) {
      case 'claude-code':
        return this.t('agents.hintClaudeCode');
      case 'claude-desktop':
        return this.t('agents.hintClaudeDesktop');
      case 'cursor':
        return this.t('agents.hintCursor');
      case 'codex':
        return this.t('agents.hintCodex');
      case 'gemini':
        return this.t('agents.hintGemini');
      case 'vscode':
        return this.t('agents.hintVsCode');
      case 'chatgpt':
        return this.t('agents.hintChatGpt');
      default:
        return this.t('agents.hintDocker');
    }
  });

  protected readonly configFileName = computed(() => {
    switch (this.client()) {
      case 'cursor':
      case 'vscode':
        return 'mcp.json';
      default:
        return 'claude_desktop_config.json';
    }
  });

  ngOnInit(): void {
    void this.agents.load();
  }

  load(): void {
    void this.agents.load();
  }

  selectMode(id: SetupMode): void {
    this.mode.set(id);
    const current = CLIENTS.find((item) => item.id === this.client());
    if (!current?.modes.includes(id)) {
      this.client.set('claude-code');
    }
  }

  selectClient(id: ClientId): void {
    this.client.set(id);
  }

  stepNo(step: number): number {
    return this.isHosted() ? step - 1 : step;
  }

  keyMenu(agent: AgentDto): MenuItem[] {
    return [
      { id: 'copy', label: 'organization.copyPrefix', icon: 'copy' },
      {
        id: 'revoke',
        label: 'organization.revokeKey',
        icon: 'trash',
        danger: true,
        disabled: agent.state !== 'active',
        separatorBefore: true,
      },
    ];
  }

  async onKeyMenu(item: MenuItem, agent: AgentDto): Promise<void> {
    if (item.id === 'copy') {
      await this.copy(agent.prefix, 'organization.keyCopied');
      return;
    }
    const confirmed = await this.confirm.ask({
      title: 'organization.revokeKey',
      message: this.t('organization.revokeKeyHint', { label: agent.label }),
      confirmLabel: 'organization.revokeKey',
      destructive: true,
    });
    if (!confirmed) return;
    try {
      await this.agents.revokeKey(agent.id);
      this.toast.success(this.t('agents.keyRevoked'));
    } catch {
      this.toast.error(this.t('state.errorTitle'));
    }
  }

  async createKey(draft: ApiKeyDraft): Promise<void> {
    this.keyOpen.set(false);
    try {
      const issued = await this.agents.createKey(draft.label, draft.scopes, draft.expiresInDays);
      this.issuedKey.set(issued.key);
      this.toast.success(this.t('agents.keyCreated'));
    } catch {
      this.toast.error(this.t('agents.keyFailed'));
    }
  }

  async copyIssuedKey(): Promise<void> {
    const key = this.issuedKey();
    if (!key) return;
    await this.copy(key, 'organization.keyCopied');
  }

  async copyConfig(): Promise<void> {
    await this.copy(this.configSnippet(), 'agents.configCopied');
  }

  async copyBuild(): Promise<void> {
    await this.copy(this.buildSnippet(), 'agents.buildCopied');
  }

  downloadConfig(): void {
    const blob = new Blob([this.configSnippet()], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = this.configFileName();
    link.click();
    URL.revokeObjectURL(url);
  }

  actionLabel(action: string): string {
    if (action === 'agent.denied') return this.t('agents.actionDenied');
    if (action.startsWith('agent.task.')) return this.t('agents.actionTask');
    if (action.startsWith('agent.rule.')) return this.t('agents.actionRule');
    return this.t('agents.actionOther');
  }

  isDenied(action: string): boolean {
    return action === 'agent.denied';
  }

  private trimmedPath(): string {
    const path = this.serverPath().trim().replace(/\/+$/, '');
    return path === '' ? DEFAULT_PATH : path;
  }

  private hostedSnippet(): string {
    const key = this.activeKey();
    const auth = `Authorization: Bearer ${key}`;
    switch (this.client()) {
      case 'claude-code':
        return [
          'claude mcp add --transport http nowtask \\',
          `  ${this.mcpUrl} \\`,
          `  --header "${auth}"`,
        ].join('\n');
      case 'codex':
        return [
          `export NOWTASK_API_KEY=${key}`,
          'codex mcp add nowtask \\',
          `  --url ${this.mcpUrl} \\`,
          '  --bearer-token-env-var NOWTASK_API_KEY',
        ].join('\n');
      case 'gemini':
        return [
          'gemini mcp add --transport http \\',
          `  --header "${auth}" \\`,
          `  nowtask ${this.mcpUrl}`,
        ].join('\n');
      case 'vscode':
        return this.json(
          { type: 'http', url: this.mcpUrl, headers: { Authorization: `Bearer ${key}` } },
          'servers',
        );
      default:
        return this.json({
          type: 'http',
          url: this.mcpUrl,
          headers: { Authorization: `Bearer ${key}` },
        });
    }
  }

  private localSnippet(): string {
    const key = this.activeKey();
    const server = `${this.trimmedPath()}/dist/index.js`;
    const env = { NOWTASK_API_URL: this.apiUrl, NOWTASK_API_KEY: key };
    switch (this.client()) {
      case 'claude-code':
        return [
          'claude mcp add nowtask \\',
          `  -e NOWTASK_API_URL=${this.apiUrl} \\`,
          `  -e NOWTASK_API_KEY=${key} \\`,
          `  -- node ${server}`,
        ].join('\n');
      case 'codex':
        return [
          'codex mcp add nowtask \\',
          `  --env NOWTASK_API_URL=${this.apiUrl} \\`,
          `  --env NOWTASK_API_KEY=${key} \\`,
          `  -- node ${server}`,
        ].join('\n');
      case 'gemini':
        return [
          'gemini mcp add \\',
          `  -e NOWTASK_API_URL=${this.apiUrl} \\`,
          `  -e NOWTASK_API_KEY=${key} \\`,
          `  nowtask node ${server}`,
        ].join('\n');
      case 'vscode':
        return this.json({ type: 'stdio', command: 'node', args: [server], env }, 'servers');
      case 'chatgpt':
        return [
          'docker run -d --name nowtask-mcp \\',
          '  -p 8765:8765 \\',
          '  -e NOWTASK_MCP_TRANSPORT=http \\',
          '  -e NOWTASK_MCP_HTTP_HOST=0.0.0.0 \\',
          '  -e NOWTASK_MCP_ALLOWED_HOSTS=mcp.example.com \\',
          `  -e NOWTASK_API_URL=${this.apiUrl} \\`,
          `  -e NOWTASK_API_KEY=${key} \\`,
          '  nowtask-mcp',
        ].join('\n');
      case 'docker':
        return this.json({
          command: 'docker',
          args: [
            'run',
            '-i',
            '--rm',
            '-e',
            'NOWTASK_MCP_TRANSPORT=stdio',
            '-e',
            `NOWTASK_API_URL=${this.apiUrl}`,
            '-e',
            `NOWTASK_API_KEY=${key}`,
            'nowtask-mcp',
          ],
        });
      default:
        return this.json({ command: 'node', args: [server], env });
    }
  }

  private json(server: Record<string, unknown>, root = 'mcpServers'): string {
    return JSON.stringify({ [root]: { nowtask: server } }, null, 2);
  }

  private async copy(text: string, messageKey: string): Promise<void> {
    try {
      await navigator.clipboard.writeText(text);
      this.toast.success(this.t(messageKey));
    } catch {
      this.toast.error(this.t('task.linkCopyFailed'));
    }
  }
}
