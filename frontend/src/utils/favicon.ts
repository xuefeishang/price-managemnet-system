const EMPTY_FAVICON = `data:image/svg+xml,${encodeURIComponent('<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 16 16"></svg>')}`
const FAVICON_SIZE = 64

const resolveAssetUrl = (url: string) => {
  if (!url) return ''
  if (url.startsWith('data:') || url.startsWith('http')) return url
  return `${window.location.origin}${url}`
}

const inferIconType = (url: string) => {
  const dataMime = url.match(/^data:([^;,]+)/)?.[1]
  if (dataMime) return dataMime
  if (url.endsWith('.svg')) return 'image/svg+xml'
  if (url.endsWith('.ico')) return 'image/x-icon'
  if (url.endsWith('.webp')) return 'image/webp'
  if (url.endsWith('.jpg') || url.endsWith('.jpeg')) return 'image/jpeg'
  return 'image/png'
}

const updateIconLink = (href: string, type: string) => {
  let link = document.querySelector<HTMLLinkElement>('link[rel~="icon"]')

  if (!link) {
    link = document.createElement('link')
    document.head.appendChild(link)
  }

  link.rel = 'icon'
  link.type = type
  link.href = href
}

const createFilledFavicon = (src: string) => new Promise<string>((resolve, reject) => {
  const image = new Image()
  image.crossOrigin = 'anonymous'
  image.onload = () => {
    const canvas = document.createElement('canvas')
    canvas.width = FAVICON_SIZE
    canvas.height = FAVICON_SIZE

    const context = canvas.getContext('2d')
    if (!context) {
      reject(new Error('Canvas is not supported'))
      return
    }

    context.clearRect(0, 0, FAVICON_SIZE, FAVICON_SIZE)

    const scale = Math.max(FAVICON_SIZE / image.naturalWidth, FAVICON_SIZE / image.naturalHeight)
    const width = image.naturalWidth * scale
    const height = image.naturalHeight * scale
    const x = (FAVICON_SIZE - width) / 2
    const y = (FAVICON_SIZE - height) / 2

    context.drawImage(image, x, y, width, height)
    resolve(canvas.toDataURL('image/png'))
  }
  image.onerror = reject
  image.src = src
})

export const updateFavicon = (logoUrl?: string) => {
  const href = resolveAssetUrl(logoUrl || '') || EMPTY_FAVICON
  updateIconLink(href, inferIconType(href))

  if (!logoUrl || href === EMPTY_FAVICON) return

  createFilledFavicon(href)
    .then(iconHref => updateIconLink(iconHref, 'image/png'))
    .catch(() => updateIconLink(href, inferIconType(href)))
}
