const GITHUB = 'https://github.com/arthurr0/nowtask.app';
const CONTACT = 'mailto:biuro@minecodes.pl';

export const LANDING_LINKS = {
  github: GITHUB,
  issues: `${GITHUB}/issues`,
  discussions: `${GITHUB}/discussions`,
  deployGuide: `${GITHUB}#running`,
  agentGuide: `${GITHUB}/blob/master/docs/ai-agents.md`,
  mcpServer: `${GITHUB}/tree/master/mcp`,
  contact: CONTACT,
  repoLabel: 'github.com/arthurr0/nowtask.app',
  contactLabel: 'biuro@minecodes.pl',
} as const;

export const LANDING_COPY = {
  brand: 'nowtask',

  nav: {
    links: [
      { href: '#features', label: 'Features' },
      { href: '#compare', label: 'Comparison' },
      { href: '#automations', label: 'Automations' },
      { href: '#ai-agents', label: 'AI agents' },
      { href: '#pricing', label: 'Pricing' },
      { href: '#open-source', label: 'Open source' },
      { href: '#faq', label: 'FAQ' },
    ],
    signIn: 'Sign in',
    start: 'Start for free',
    menu: 'Menu',
    close: 'Close menu',
  },

  hero: {
    badge: 'Open source, with a hosted instance ready to use',
    title: 'Describe the work once, let the rules keep it on track',
    body: 'Board, list, timeline and calendar run on the same data. Statuses and fields follow your process, automations move tasks, assign people and watch the deadlines, and a built-in MCP server puts the same API in the hands of your AI agents.',
    ctaPrimary: 'Start for free',
    ctaSecondary: 'See the code on GitHub',
    note: 'Free for individuals and teams of up to 25 people. No card required.',
    feedTitle: 'Happening now',
    feed: [
      {
        icon: 'bolt',
        text: 'Rule assigned NT-142 to Marta R. for review',
        time: 'now',
        rule: true,
      },
      {
        icon: 'board',
        text: 'NT-131 moved from In progress to Review',
        time: '2 min',
        rule: false,
      },
      {
        icon: 'agent',
        text: 'agent:Team assistant commented on NT-138',
        time: '9 min',
        rule: false,
      },
      { icon: 'calendar', text: 'NT-147 due date moved to Friday', time: '14 min', rule: false },
    ],
  },

  strip: [
    { icon: 'layers', value: '4 views', label: 'over the same set of tasks' },
    { icon: 'agent', value: '31 tools', label: 'in the MCP server for AI agents' },
    { icon: 'globe', value: '3 languages', label: 'Polish, English and German' },
    { icon: 'shield', value: '1 command', label: 'to run it on your own server' },
  ],

  compare: {
    eyebrow: 'Comparison',
    title: 'How nowtask stands next to the tools you are using today',
    body: 'Views, rules, agent access and export are all here from the first day, with no plan to upgrade for each of them separately. This is what comes included, and what usually costs extra somewhere else.',
    featureHeading: 'What you get',
    columns: ['nowtask', 'Jira', 'Trello', 'Asana', 'Notion'],
    rows: [
      {
        label: 'Board, list, timeline and calendar over one set of tasks',
        values: ['yes', 'yes', 'partial', 'partial', 'yes'],
      },
      {
        label: 'Automation rules with nested conditions',
        values: ['yes', 'yes', 'partial', 'partial', 'partial'],
      },
      {
        label: 'Run log and audit trail without an upgrade',
        values: ['yes', 'partial', 'partial', 'partial', 'partial'],
      },
      {
        label: 'MCP server for AI agents at no extra cost',
        values: ['yes', 'partial', 'no', 'partial', 'partial'],
      },
      {
        label: 'Permissions down to a single field',
        values: ['yes', 'partial', 'no', 'partial', 'no'],
      },
      {
        label: 'Self-hosting the same product you see here',
        values: ['yes', 'partial', 'no', 'no', 'no'],
      },
      {
        label: 'Public source code',
        values: ['yes', 'no', 'no', 'no', 'no'],
      },
      {
        label: 'Free for a team of up to 25 people',
        values: ['yes', 'partial', 'partial', 'partial', 'partial'],
      },
      {
        label: 'Full export of tasks, comments and configuration',
        values: ['yes', 'yes', 'partial', 'partial', 'yes'],
      },
    ],
    legend: [
      { state: 'yes', label: 'Included' },
      { state: 'partial', label: 'Limited, paid or through an add-on' },
      { state: 'no', label: 'Not available' },
    ],
    footnote:
      'Based on the public plans and documentation of each tool, checked in August 2026. Every vendor changes their offer, so it is worth confirming before you decide. Jira, Trello, Asana and Notion belong to their owners.',
    cta: 'See what the plans cost',
  },

  features: {
    eyebrow: 'Features',
    title: 'Everything a team needs, without bolting on plugins',
    body: 'One product instead of five tools taped together by integrations that break at the worst moment.',
    items: [
      {
        icon: 'board',
        art: 'views',
        title: 'Four views, one set of data',
        body: 'Board, list, timeline and calendar show the same tasks. A change in one place shows up everywhere, with no syncing and no copying.',
      },
      {
        icon: 'sliders',
        art: 'fields',
        title: 'Statuses and fields that match your process',
        body: 'Add your own columns, fields and labels. WIP limits, status categories and grouping are yours to set, no ticket to IT required.',
      },
      {
        icon: 'bolt',
        art: 'rules',
        title: 'Automations with real conditions',
        body: 'Rules with nested conditions move tasks, assign people and set due dates. Every run lands in the log, so you can see what happened and why.',
      },
      {
        icon: 'lock',
        art: 'permissions',
        title: 'Permissions down to a single field',
        body: 'Custom roles with a permission matrix and hiding selected fields from guests. Sensitive data stays where it belongs.',
      },
      {
        icon: 'sun',
        art: 'theme',
        title: 'An interface you can tune',
        body: 'Light and dark theme, five accents, three densities and a radius setting. It has to feel right for eight hours a day, not just in a screenshot.',
      },
      {
        icon: 'archive',
        art: 'data',
        title: 'Your data stays yours',
        body: 'Export any time, a public API and the full source code. If you ever want to leave, the file format will not hold you back.',
      },
    ],
    agents: {
      icon: 'agent',
      badge: 'Built in',
      title: 'Open to AI agents, not just to people',
      body: 'An MCP server hands Claude, Cursor or your own agent 31 tools over the same API the interface uses. Scopes and the audit log keep it in check.',
      cta: 'See how agents work here',
    },
  },

  rules: {
    eyebrow: 'Automations',
    title: 'Describe the process once, then just refine it',
    body: 'A rule is a trigger, conditions and actions. Conditions nest, so a single rule also covers the exception that never fits a plain if.',
    points: [
      'Triggers: status, field or due date changes, or a new comment',
      'Actions: assign, move, set a field, add a label, notify',
      'A run log that records why the rule fired',
    ],
    name: 'Review for urgent work',
    active: 'Active',
    when: 'When',
    whenValue: 'status changes to Review',
    if: 'If',
    ifValues: ['priority is High', 'due date is within 2 days'],
    ifJoin: 'or',
    then: 'Then',
    thenValues: ['assign Marta R. as reviewer', 'set the due date to tomorrow'],
    logTitle: 'Recent runs',
    log: [
      { time: '14:38', label: 'NT-131 assigned to Marta R.', reason: 'priority is High' },
      { time: '11:04', label: 'NT-138 due date set to tomorrow', reason: 'due in 2 days' },
      { time: '09:12', label: 'NT-142 skipped', reason: 'no condition matched' },
    ],
  },

  agents: {
    eyebrow: 'AI agents',
    title: 'Give an agent the board, not a copy of the board',
    body: 'nowtask ships with an MCP server. An agent connects to it, authenticates with an API key you issue, and works on the same tasks the team sees, with no export, no sync and no second source of truth.',
    points: [
      'One key per agent, with the scopes you pick and a date it expires',
      'Every write shows up in task history as agent:<key name> and in the audit log',
      'Admin stays out of reach: no agent issues keys, changes roles or reads the audit log',
    ],
    transportTitle: 'Transports',
    transports: ['stdio', 'Streamable HTTP'],
    clientsTitle: 'Tested with',
    clients: ['Claude Desktop', 'Claude Code', 'Cursor'],
    configTitle: 'Client configuration',
    configFile: 'claude_desktop_config.json',
    config: `"nowtask": {
  "command": "node",
  "args": ["mcp/dist/index.js"],
  "env": {
    "NOWTASK_API_URL": "https://nowtask.app",
    "NOWTASK_API_KEY": "nt_fbe17d9e_…"
  }
}`,
    toolsTitle: '31 tools, grouped by what they touch',
    toolsBody:
      'Each tool declares the scope it needs. A key without that scope gets a refusal from the API, not a silent skip.',
    toolGroups: [
      {
        icon: 'search',
        title: 'Reading',
        scope: 'tasks:read',
        body: 'Search with filters, task details, comments, history, timeline and workspace configuration.',
      },
      {
        icon: 'pencil',
        title: 'Writing',
        scope: 'tasks:write',
        body: 'Create and update tasks, move statuses, comment, set labels, relations and custom fields.',
      },
      {
        icon: 'layers',
        title: 'Subtasks and bulk work',
        scope: 'tasks:write',
        body: 'Subtasks end to end, plus bulk assign and bulk status for up to 100 tasks in one call.',
      },
      {
        icon: 'bolt',
        title: 'Automations and metrics',
        scope: 'rules:read',
        body: 'Read rules and their run log, run a rule on a task, toggle it, and pull dashboard metrics.',
      },
    ],
    demo: {
      client: 'Claude Desktop',
      server: 'nowtask · MCP',
      connected: 'connected',
      prompt: 'What has been sitting in review too long, and who has room to take it?',
      callsTitle: 'Tool calls',
      calls: [
        {
          tool: 'nowtask_tasks_search',
          args: '{ "statusId": "review", "sort": "dueDate" }',
          result: '4 tasks',
        },
        { tool: 'nowtask_metrics_overview', args: '{}', result: 'workload for 5 people' },
        {
          tool: 'nowtask_tasks_bulk_assign',
          args: '{ "keys": ["NT-131", "NT-138"], "assigneeId": "…" }',
          result: '2 tasks updated',
        },
      ],
      answer:
        'Four tasks have been in review for over three days. Marta has the lightest load this sprint, so NT-131 and NT-138 are now hers. The other two are waiting on a decision, so I left them alone.',
      scopesTitle: 'Key scopes',
      scopes: ['tasks:read', 'tasks:write', 'metrics:read'],
      trailTitle: 'What the team sees',
      trail: 'NT-131 · assigned to Marta R. · agent:Team assistant (nt_fbe17d9e)',
    },
    docs: 'AI agent guide',
    repo: 'MCP server source',
  },

  oss: {
    eyebrow: 'Open source',
    title: 'The code is public, the hosting is your call',
    body: 'The same code runs the instance you use here and the one you put on your own server. There is no stripped down community edition.',
    selfTitle: 'On your own server',
    selfBody:
      'Postgres, backend and frontend start with one command. No seat limit, no licence key, you upgrade when you want to.',
    command: 'docker compose up --build',
    commandOutput: [
      'postgres    ready to accept connections',
      'backend     started on port 8080',
      'frontend    serving on http://localhost',
    ],
    docs: 'Deployment guide',
    cloudTitle: 'On the official instance',
    cloudBody:
      'Sign up and get to work. We take care of backups, updates and uptime, and the data stays in the European Union.',
    cloudTags: ['nowtask.app', 'eu-central-1'],
    repo: 'Repository on GitHub',
  },

  pricing: {
    eyebrow: 'Pricing',
    title: 'Free up to 25 people, paid only once you really grow',
    body: 'No basics locked behind a paywall. Large organisations pay for scale, contracts and support, not for access to a board.',
    popular: 'Most chosen',
    tiers: [
      {
        name: 'Self-hosted',
        audience: 'For teams that keep the data at home',
        price: 'Free',
        priceNote: 'on your server',
        compactPrice: false,
        highlight: false,
        features: [
          'Full source code',
          'No limit on people or projects',
          'Docker compose with database and backend',
          'Upgrade whenever you decide to',
          'MCP server for AI agents included',
          'Community support on GitHub',
        ],
        cta: 'See it on GitHub',
        href: GITHUB,
      },
      {
        name: 'Hobby',
        audience: 'For one person and after-hours projects',
        price: 'Free',
        priceNote: 'forever',
        compactPrice: false,
        highlight: false,
        features: [
          'An account on the official instance',
          'Unlimited projects and tasks',
          'Every view and every automation',
          'Themes, languages and data export',
          'An API key for your own AI agent',
          'No payment card',
        ],
        cta: 'Create an account',
        href: null,
      },
      {
        name: 'Team',
        audience: 'For teams of up to 25 people',
        price: 'Free',
        priceNote: 'forever',
        compactPrice: false,
        highlight: true,
        features: [
          'Everything in Hobby',
          'Organisation, invites and roles',
          'Custom roles and field-level permissions',
          'Automation log and reports',
          'Agent keys with scopes and an audit log',
          'Saved views shared across the team',
        ],
        cta: 'Start for free',
        href: null,
      },
      {
        name: 'Company',
        audience: 'For organisations above 25 people',
        price: 'Custom quote',
        priceNote: 'based on the size of the organisation',
        compactPrice: true,
        highlight: false,
        features: [
          'Corporate sign-in through OIDC',
          'Contract, invoicing and an uptime guarantee',
          'A separate instance or a chosen data region',
          'Support with an agreed response time',
          'Agent access reviewed key by key',
          'Help migrating from your current tool',
        ],
        cta: 'Let us talk',
        href: CONTACT,
      },
    ],
  },

  faq: {
    title: 'Questions that come up most often',
    lead: 'Short answers to what people ask before they sign up or run their own instance.',
    more: 'Something else? Write to us',
    items: [
      {
        q: 'Are the free tiers really free?',
        a: 'Yes. A personal account and a team of up to 25 people cost nothing and need no card. There is no trial that quietly switches something off.',
      },
      {
        q: 'What happens when the team grows past 25 people?',
        a: 'We get in touch with a quote that fits the size of the organisation and the support you need. Nothing shuts down overnight, you get time to decide.',
      },
      {
        q: 'How does self-hosting differ from the official instance?',
        a: 'Not at all in code, it is the same project. The difference is who looks after the server, the backups and the updates.',
      },
      {
        q: 'Where is the data on the official instance kept?',
        a: 'On servers in the European Union. Companies can arrange a different region or an instance of their own.',
      },
      {
        q: 'Can an AI agent really work in nowtask?',
        a: 'Yes, through the MCP server that comes with the project. The agent gets 31 tools over the same API the interface uses: it searches, creates and moves tasks, comments, runs automations and reads metrics. It connects over stdio or Streamable HTTP, so Claude Desktop, Claude Code and Cursor work out of the box.',
      },
      {
        q: 'Is it safe to hand an agent a key to the board?',
        a: 'You decide what the key opens. Scopes are separate, so read-only is the default and deleting has to be granted on purpose. Admin endpoints are closed to keys entirely, every write lands in the audit log, and task history marks the change with the name of the key that made it.',
      },
      {
        q: 'Can I take my data with me?',
        a: 'Any time. The export covers tasks, comments and configuration, and the public API returns the same content programmatically.',
      },
      {
        q: 'Does nowtask work for sprint-based teams?',
        a: 'Yes. Statuses, fields and views fit sprints just as well as a simpler flow with a single in-progress column.',
      },
    ],
  },

  cta: {
    title: 'Move your first project over today',
    body: 'Create an account, invite the team and see what work looks like when nobody has to ask where a task stands.',
    primary: 'Create a free account',
    secondary: 'Talk about a rollout',
  },

  footer: {
    tagline: 'Now, not someday.',
    product: 'Product',
    community: 'Community',
    contact: 'Contact',
    issues: 'Report a bug',
    discussions: 'Discussions',
    rights: 'An open source project. The official instance is run by nowtask.',
  },
} as const;
