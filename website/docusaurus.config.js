// @ts-check
import { themes } from 'prism-react-renderer'

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'Eventus',
  tagline: 'Routes each UI event of a screen to its own event handler',
  favicon: 'img/logo.svg',
  url: 'https://mradzinski.github.io',
  baseUrl: '/Eventus/',
  organizationName: 'mradzinski',
  projectName: 'Eventus',
  // Every page is a folder with an index file, which any plain file server and GitHub Pages serve alike.
  trailingSlash: true,
  onBrokenLinks: 'throw',
  onBrokenAnchors: 'throw',

  // The pages are plain Markdown written for GitHub, so they are not read as MDX.
  markdown: {
    format: 'detect',
    hooks: { onBrokenMarkdownLinks: 'throw' },
  },

  i18n: { defaultLocale: 'en', locales: ['en'] },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
          routeBasePath: '/',
          sidebarPath: './sidebars.js',
        },
        blog: false,
        theme: { customCss: './src/css/custom.css' },
      }),
    ],
  ],

  themes: [
    [
      '@easyops-cn/docusaurus-search-local',
      {
        hashed: true,
        indexBlog: false,
        docsRouteBasePath: '/',
      },
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      colorMode: { respectPrefersColorScheme: true },
      navbar: {
        title: 'Eventus',
        logo: { alt: 'Eventus', src: 'img/logo.svg', srcDark: 'img/logo-ring.svg' },
        items: [
          {
            href: 'pathname:///Eventus/api/',
            label: 'API reference',
            position: 'left',
            target: '_self',
          },
          {
            href: 'https://central.sonatype.com/artifact/io.github.mradzinski/eventus',
            label: 'Maven Central',
            position: 'right',
          },
          {
            href: 'https://github.com/mradzinski/Eventus',
            label: 'GitHub',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'light',
        copyright: 'Eventus is released under the Apache License 2.0.',
      },
      prism: {
        theme: themes.github,
        darkTheme: themes.dracula,
        additionalLanguages: ['kotlin', 'groovy', 'toml', 'bash', 'java'],
      },
    }),
}

export default config
