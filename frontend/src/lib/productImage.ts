// Seed catalog photos are Amazon thumbnails such as `…/images/I/81k57a1xhuL._AC_UL320_.jpg`,
// capped at ~320px: fine for grids and cards, blurry once zoomed. The `._…_` block before the
// extension is a resize/quality modifier; without it the CDN serves the full-resolution original.

const AMAZON_IMAGE_HOST = 'm.media-amazon.com'
const AMAZON_IMAGE_PATH = '/images/I/'
// `<id>.<modifiers>.<ext>` — the id itself never contains a dot.
const MODIFIED_FILE = /^([^.]+)\..+\.([A-Za-z0-9]+)$/

// Full-resolution version of a product image URL. Anything that isn't a modified Amazon image
// (seller uploads, relative paths, already-original URLs) is returned unchanged.
export function fullSizeImageUrl(url: string): string {
  let parsed: URL
  try {
    parsed = new URL(url)
  } catch {
    return url
  }
  if (parsed.hostname !== AMAZON_IMAGE_HOST || !parsed.pathname.startsWith(AMAZON_IMAGE_PATH)) return url

  const slash = parsed.pathname.lastIndexOf('/')
  const match = MODIFIED_FILE.exec(parsed.pathname.slice(slash + 1))
  if (!match) return url

  parsed.pathname = `${parsed.pathname.slice(0, slash + 1)}${match[1]}.${match[2]}`
  return parsed.toString()
}
