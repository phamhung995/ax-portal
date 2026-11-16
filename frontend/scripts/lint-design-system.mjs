// Design System v2.0 (Linear) lint gate.
//
// The v2.0 replacement was done by text search, and every pass missed one
// notation (hex, then rgb(), then old rgb triples, then JS colour arrays).
// This gate is the thorough fix: it fails the build-time lint when any
// remnant of the old neon/glass/bokeh system reappears in frontend/src,
// so a regression is caught by the machine instead of by eye.
//
// Run:  npm run lint:design-system   (also runs inside `npm run lint`)
// Exit 0 = clean, exit 1 = violations listed with file:line.
import { readdirSync, readFileSync, statSync } from 'node:fs'
import { join, relative } from 'node:path'

const SRC = new URL('../src', import.meta.url).pathname
const EXTENSIONS = new Set(['.vue', '.css', '.js', '.ts'])
// Test fixtures deliberately feed arbitrary hex values to the colour picker.
const SKIP_FILES = new Set(['components.test.js'])

// [label, regex] — every pattern is one notation the old system used.
const BANNED = [
  ['old hex #67e8f9 (cyan)', /#67e8f9/i],
  ['old hex #100d2b (purple canvas)', /#100d2b/i],
  ['old hex #090626 (banner purple)', /#090626/i],
  ['old hex #a58aef (violet)', /#a58aef/i],
  ['old hex #9ce8d0 (mint)', /#9ce8d0/i],
  ['old hex #ffacc4 (rose)', /#ffacc4/i],
  ['old hex #f3d28c (amber)', /#f3d28c/i],
  ['old hex #38bdf8 (sky)', /#38bdf8/i],
  ['old hex #f472b6 (pink)', /#f472b6/i],
  ['old hex #facc15 (yellow)', /#facc15/i],
  ['old hex #12121a (dark fallback)', /#12121a/i],
  ['old hex #f87171 (red fallback)', /#f87171/i],
  ['old hex #fbbf24 (amber fallback)', /#fbbf24/i],
  ['old hex #f28bb0 (pink chart)', /#f28bb0/i],
  ['old hex #f2c879 (amber chart)', /#f2c879/i],
  ['old rgb 103 232 249 (cyan)', /103\s+232\s+249/],
  ['old rgb 133 234 255 (light cyan)', /133\s+234\s+255/],
  ['old rgb 16 13 43 (purple)', /16\s+13\s+43/],
  ['old rgb 156 232 208 (mint)', /156\s+232\s+208/],
  ['old rgb 243 210 140 (amber)', /243\s+210\s+140/],
  ['old rgb 255 172 196 (rose)', /255\s+172\s+196/],
  ['old rgb 165 138 239 (violet)', /165\s+138\s+239/],
  ['old rgb 15 32 62 (dark blue)', /15\s+32\s+62/],
  ['old rgb 5 8 26 (dark navy)', /5\s+8\s+26/],
  ['old rgb 3 5 23 (dark navy)', /3\s+5\s+23/],
  ['gradient (Linear uses flat fills)', /linear-gradient|radial-gradient/],
  ['backdrop blur (v2.0 is flat)', /backdrop-filter:\s*blur/],
  ['glow shadow', /box-shadow:[^;]*0 0 (6|8|14|16)px/],
  ['bokeh', /\bbokeh\b/i],
  ['plexus / ambient orb', /ambient-(orb|plexus|node)|plexus-glow/],
]

function walk(dir, out = []) {
  for (const entry of readdirSync(dir)) {
    const path = join(dir, entry)
    if (statSync(path).isDirectory()) walk(path, out)
    else if (EXTENSIONS.has(entry.slice(entry.lastIndexOf('.')))) out.push(path)
  }
  return out
}

let violations = 0
for (const file of walk(SRC)) {
  if (SKIP_FILES.has(file.split('/').pop())) continue
  const lines = readFileSync(file, 'utf8').split('\n')
  lines.forEach((line, i) => {
    for (const [label, re] of BANNED) {
      if (re.test(line)) {
        console.error(`${relative(process.cwd(), file)}:${i + 1}: ${label}`)
        violations++
      }
    }
  })
}

if (violations > 0) {
  console.error(`\nlint:design-system: ${violations} violation(s) — old design system remnants found.`)
  process.exit(1)
}
console.log('lint:design-system: clean — no old design system remnants in frontend/src.')
