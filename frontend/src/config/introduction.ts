/**
 * Introduction page (public/introduction.html) — shown on local, dev and
 * prod.
 *
 * Jenkins builds ONE artifact and deploys it to both dev and prod, so an
 * env var at build time cannot distinguish the two. Instead the button
 * visibility is decided at RUNTIME by hostname: the button appears only on
 * the hosts listed here.
 */
const INTRODUCTION_HOSTS = [
  'localhost',
  '127.0.0.1',
  'axd-portal.gafc.vn',
  'ax-portal.gafc.vn',
]

/** True when the current host may show the "Introduction" header button. */
export function isIntroductionEnabled(): boolean {
  return INTRODUCTION_HOSTS.includes(window.location.hostname)
}

/** The introduction page (served as a static file from /public). */
export const INTRODUCTION_PATH = '/introduction.html'
