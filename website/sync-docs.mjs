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

function page(text) {
  const withoutBackLink = text.replace(/\n---\n\n\[Back to the README\]\([^)]*\)\s*$/, '\n')

  return withSiteLinks(withoutSection(withoutBackLink, 'Contents'))
}

function firstPage(text) {
  const body = withoutSection(withoutSection(text, 'Contents'), 'Documentation')
    // The README's link to this site would only point at the page it is on.
    .replace(/\n\*\*\[Read the documentation at [^\n]*\n/, '')
    .replace(/\]\(docs\/([^)]+)\)/g, '](./$1)')
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
