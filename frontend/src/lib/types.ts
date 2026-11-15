// Shared domain types used by multiple features.

/** One organization assignment of a user (Employed=Y org row). Hierarchy: Division > Center > Group > Part. */
export interface OrgRef {
  part: string | null
  group: string | null
  center: string | null
  division: string | null
  position: string | null
}

/** "Division > Center > Group > Part" (null levels skipped) for one org row. */
export function orgRowLabel(o: OrgRef): string {
  return [o.division, o.center, o.group, o.part]
    .filter((v) => v != null && v !== '')
    .join(' > ')
}

/** Multiple org rows joined by "; " (empty rows skipped). */
export function orgsLabel(orgs: OrgRef[] | null | undefined): string {
  if (!orgs?.length) return '—'
  return orgs.map(orgRowLabel).filter(Boolean).join('; ') || '—'
}
