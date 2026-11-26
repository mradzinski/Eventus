// @ts-check

/** @type {import('@docusaurus/plugin-content-docs').SidebarsConfig} */
const sidebars = {
  docs: [
    'index',
    {
      type: 'category',
      label: 'Concepts',
      collapsed: false,
      items: [
        'key-concepts',
        'where-eventus-sits-in-your-app',
        'structuring-the-events-of-a-feature',
        'what-eventus-does-not-do',
      ],
    },
    {
      type: 'category',
      label: 'Guides',
      collapsed: false,
      items: [
        'choosing-a-dispatch-strategy',
        'handling-failures',
        'emitting-an-effect',
        'choosing-between-an-effect-and-state',
        'updating-the-state-optimistically',
        'dispatching-an-event-from-a-handler',
        'debouncing',
        'keeping-work-running',
        'navigating',
        'reaching-another-screen',
        'choosing-the-coroutine-scope',
        'logging-events',
        'testing',
      ],
    },
    {
      type: 'category',
      label: 'Integrations',
      collapsed: false,
      items: [
        'dependency-injection',
        'checking-and-wiring-event-handlers-at-build-time',
        'using-the-compose-helpers',
      ],
    },
    {
      type: 'category',
      label: 'Reference',
      collapsed: false,
      items: [
        'project-layout',
        'class-diagram',
        'changelog',
        {
          type: 'link',
          label: 'API reference',
          href: 'pathname:///Eventus/api/',
        },
        {
          type: 'link',
          label: 'Guide for AI agents',
          href: 'https://github.com/mradzinski/Eventus/blob/main/AGENT-USAGE.md',
        },
      ],
    },
  ],
}

export default sidebars
