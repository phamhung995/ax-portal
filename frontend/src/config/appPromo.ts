/**
 * AX Desktop installer download URL.
 *
 * Relative path — served by nginx `location /downloads/` (alias
 * /var/www/downloads/) on BOTH dev and prod, so the same build works
 * everywhere. The download attribute only works same-origin, hence the
 * relative URL instead of an absolute prod URL.
 */
export const AX_DESKTOP_DOWNLOAD_URL = '/downloads/AX_Desktop-Setup-1.0.22.exe'
