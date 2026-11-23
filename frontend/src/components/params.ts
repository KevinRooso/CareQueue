// Helpers for reading numbers out of URL search params and route params.
// Pages keep filters and the page number in the URL so the back button and shared links work.

/** A positive integer id from a URL, or undefined if missing/invalid. */
export function parseId(value: string | null | undefined): number | undefined {
  if (!value || !/^\d+$/.test(value)) return undefined
  const id = Number(value)
  return Number.isSafeInteger(id) && id > 0 ? id : undefined
}

/**
 * The URL shows pages 1-based (`?page=2` is the second page) because people read it;
 * the API is zero-based. Returns the zero-based page.
 */
export function parsePage(value: string | null): number {
  const page = parseId(value)
  return page === undefined ? 0 : page - 1
}

/** Writes a zero-based page into search params (page 0 removes the param). */
export function withPage(params: URLSearchParams, page: number): URLSearchParams {
  const next = new URLSearchParams(params)
  if (page > 0) next.set('page', String(page + 1))
  else next.delete('page')
  return next
}
