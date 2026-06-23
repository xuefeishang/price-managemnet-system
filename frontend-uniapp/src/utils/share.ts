export const MINIAPP_SHARE_TITLE = '矿产品价格管理系统'
export const MINIAPP_SHARE_PATH = '/pages/home/index'
export const MINIAPP_SHARE_IMAGE = '/static/share/app-share.png'

declare const wx: {
  showShareMenu?: (options: UniNamespace.ShowShareMenuOptions) => void
} | undefined

type ShareMenuItems = NonNullable<UniNamespace.ShowShareMenuOptions['menus']>

const normalizeShareTitle = (title?: string) => title?.trim() || MINIAPP_SHARE_TITLE

export const getMiniappEntryShareMessage = (title?: string) => ({
  title: normalizeShareTitle(title),
  path: MINIAPP_SHARE_PATH,
  imageUrl: MINIAPP_SHARE_IMAGE
})

export const getMiniappEntryTimelineShare = (title?: string) => ({
  title: normalizeShareTitle(title),
  query: '',
  imageUrl: MINIAPP_SHARE_IMAGE
})

export const showMiniappEntryShareMenu = (
  menus: ShareMenuItems = ['shareAppMessage', 'shareTimeline'] as ShareMenuItems
) => {
  // #ifdef MP-WEIXIN
  const options = {
    withShareTicket: false,
    menus
  } as UniNamespace.ShowShareMenuOptions

  if (typeof wx !== 'undefined' && typeof wx.showShareMenu === 'function') {
    wx.showShareMenu(options)
    return
  }

  uni.showShareMenu(options as UniNamespace.ShowShareMenuOptions)
  // #endif
}
