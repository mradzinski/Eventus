// Builds the site's pages from the repository's own Markdown, which stays the only source:
// README.md becomes the first page, CHANGELOG.md a page of its own, and every file in docs/ a page.
// What only makes sense on GitHub is taken out: the hand-made "Contents" lists (the site builds its
// own), the README's table of pages (the sidebar replaces it) and the links back to the README.
import { cpSync, existsSync, mkdirSync, readdirSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const here = dirname(fileURLToPath(import.meta.url))
const root = join(here, '..')
const source = join(root, 'docs')
const target = join(here, 'docs')
const repository = 'https://github.com/mradzinski/Eventus/blob/main/'
const siteLink = /\]\(https:\/\/mradzinski\.github\.io\/Eventus\/([a-z0-9-]+)\/(#[^)]*)?\)/g

/** Removes a second-level section, from its heading up to the next heading of the same level. */
function withoutSection(text, heading) {
  const start = text.indexOf(`\n## ${heading}\n`)

  if (start < 0) return text

  const next = text.indexOf('\n## ', start + 1)

  return text.slice(0, start) + (next < 0 ? '\n' : text.slice(next))
}

/** Points links that leave the docs folder at the first page or, for other files, at GitHub. */
function withSiteLinks(text) {
  return text
    .replace(/\]\(\.\.\/README\.md(#[^)]*)?\)/g, (_, anchor) => `](./index.md${anchor ?? ''})`)
    .replace(/\]\(\.\.\/CHANGELOG\.md\)/g, '](./changelog.md)')
    .replace(/\]\(\.\.\/([^)]+)\)/g, (_, path) => `](${repository}${path})`)
    .replace(/\]\(([^)/]+\.puml)\)/g, (_, file) => `](${repository}docs/${file})`)
}

/** What a GitHub alert becomes on the site: the kind of box, and its title when the text names none. */
const boxes = {
  NOTE: ['note', 'Note'],
  TIP: ['tip', 'Tip'],
  IMPORTANT: ['info', 'Important'],
  WARNING: ['warning', 'Warning'],
  CAUTION: ['danger', 'Caution'],
}

/**
 * Turns GitHub's alerts ("> [!TIP]" and the quoted lines after it) into the site's own boxes. An alert
 * that opens with "**Do:**" or "**Don't:**" becomes a box with that title, green or red.
 */
function withBoxes(text) {
  const lines = text.split('\n')
  const out = []

  for (let i = 0; i < lines.length; i++) {
    const start = lines[i].match(/^> \[!(NOTE|TIP|IMPORTANT|WARNING|CAUTION)\]\s*$/)

    if (!start) {
      out.push(lines[i])
      continue
    }

    const body = []

    while (i + 1 < lines.length && lines[i + 1].startsWith('>')) {
      i++
      body.push(lines[i].replace(/^> ?/, ''))
    }

    let [kind, title] = boxes[start[1]]
    const named = body[0]?.match(/^\*\*(Do|Don't):\*\* (.*)$/)

    if (named) {
      title = named[1]
      body[0] = named[2].charAt(0).toUpperCase() + named[2].slice(1)
    }

    out.push(`:::${kind}[${title}]`, '', ...body, '', ':::')
  }

  return out.join('\n')
}

function page(text) {
  const withoutBackLink = text.replace(/\n---\n\n\[Back to the README\]\([^)]*\)\s*$/, '\n')

  return withBoxes(withSiteLinks(withoutSection(withoutBackLink, 'Contents')))
}

function firstPage(text) {
  const body = withoutSection(withoutSection(text, 'Contents'), 'Documentation')
    // The README's link to this site would only point at the page it is on.
    .replace(/\n\*\*\[Read the documentation at [^\n]*\n/, '')
    .replace(/\]\(docs\/([^)]+)\)/g, '](./$1)')
    // The README links to this site's pages by their full address. Here they become links between
    // pages, so the build checks that each page and heading exists.
    .replace(siteLink, (_, name, anchor) => `](./${name}.md${anchor ?? ''})`)
    .replace(/\]\(CHANGELOG\.md\)/g, '](./changelog.md)')
    .replace(/\]\((AGENT-USAGE\.md|LICENSE|NOTICE)\)/g, `](${repository}$1)`)

  return `---\nslug: /\ntitle: Getting started\nhide_title: true\n---\n\n${body}`
}

rmSync(target, { recursive: true, force: true })
mkdirSync(target, { recursive: true })

for (const name of readdirSync(source)) {
  if (name.endsWith('.md')) {
    writeFileSync(join(target, name), page(readFileSync(join(source, name), 'utf8')))
  } else if (!name.endsWith('.puml') && !name.endsWith('.py')) {
    cpSync(join(source, name), join(target, name))
  }
}

writeFileSync(join(target, 'index.md'), firstPage(readFileSync(join(root, 'README.md'), 'utf8')))
const changelog = readFileSync(join(root, 'CHANGELOG.md'), 'utf8')
  .replace(/\]\((AGENT-USAGE\.md|LICENSE|NOTICE)\)/g, `](${repository}$1)`)
  .replace(/\]\(docs\/([^)]+)\)/g, '](./$1)')
  .replace(/\]\(README\.md(#[^)]*)?\)/g, (_, anchor) => `](./index.md${anchor ?? ''})`)

writeFileSync(join(target, 'changelog.md'), changelog)

// The API reference is built by Gradle (./gradlew :dokkaGenerate) and served beside the pages, at /api.
const reference = join(root, 'build', 'dokka', 'html')
const referenceTarget = join(here, 'static', 'api')

rmSync(referenceTarget, { recursive: true, force: true })

if (existsSync(reference)) {
  cpSync(reference, referenceTarget, { recursive: true })
} else {
  console.warn('No API reference found. Run ./gradlew :dokkaGenerate first, or the "API reference" link leads nowhere.')
}
