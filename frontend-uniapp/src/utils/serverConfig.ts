/**
 * 服务器地址配置 - 智能内外网切换
 *
 * 统一端口架构：
 * - PC 端和小程序共用 32080 端口
 * - 内网地址：http://10.7.5.175:32080
 * - 外网地址：http://101.254.159.153:32080
 *
 * 功能：
 * 1. 支持内网/外网双地址配置
 * 2. 自动检测网络连通性
 * 3. 内网优先，自动降级到外网
 * 4. 无感切换，用户无需手动操作
 */

const STORAGE_KEY = 'api_server_config'
const NETWORK_MODE_KEY = 'api_network_mode'

// 内网地址（公司内部访问）
const INTERNAL_BASE_URL = 'http://10.7.5.175:32080'
// 外网地址（外部访问）
const EXTERNAL_BASE_URL = 'http://101.254.159.153:32080'
// 开发环境地址（本地调试）
const DEV_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'

// 网络模式
export type NetworkMode = 'internal' | 'external' | 'auto' | 'dev'

export interface ServerConfig {
  ip: string
  port: string
}

// ============ 基础工具函数 ============

const normalizeBaseUrl = (url: string) => url.replace(/\/$/, '')

const parseBaseUrl = (url: string): ServerConfig => {
  const normalizedUrl = normalizeBaseUrl(url)
  const matched = normalizedUrl.match(/^https?:\/\/([^:/]+)(?::(\d+))?/)

  return {
    ip: matched?.[1] || '127.0.0.1',
    port: matched?.[2] || '32080'
  }
}

const buildBaseUrl = (config: ServerConfig) => `http://${config.ip.trim()}:${config.port.trim()}`

// ============ 配置获取函数 ============

/**
 * 获取当前网络模式
 */
export const getNetworkMode = (): NetworkMode => {
  // 开发环境强制使用开发地址
  if (import.meta.env.DEV) {
    return 'dev'
  }

  const storedMode = uni.getStorageSync(NETWORK_MODE_KEY) as NetworkMode
  return storedMode || 'auto'
}

/**
 * 设置网络模式
 */
export const setNetworkMode = (mode: NetworkMode) => {
  uni.setStorageSync(NETWORK_MODE_KEY, mode)
  // 清除缓存的服务器配置，下次请求时会重新检测
  uni.removeStorageSync(STORAGE_KEY)
}

/**
 * 根据网络模式获取对应的基础 URL
 */
const getBaseUrlByMode = (mode: NetworkMode): string => {
  switch (mode) {
    case 'internal':
      return INTERNAL_BASE_URL
    case 'external':
      return EXTERNAL_BASE_URL
    case 'dev':
      return DEV_BASE_URL
    case 'auto':
      // 自动模式默认先尝试内网
      return INTERNAL_BASE_URL
    default:
      return EXTERNAL_BASE_URL
  }
}

/**
 * 获取默认服务器配置
 */
export const getDefaultServerConfig = (): ServerConfig => {
  const mode = getNetworkMode()
  return parseBaseUrl(getBaseUrlByMode(mode))
}

/**
 * 获取当前服务器配置
 */
export const getServerConfig = (): ServerConfig => {
  const storedConfig = uni.getStorageSync(STORAGE_KEY)
  if (storedConfig?.ip && storedConfig?.port) {
    return storedConfig
  }

  return getDefaultServerConfig()
}

/**
 * 获取 API 基础地址
 */
export const getApiBaseUrl = (): string => {
  return normalizeBaseUrl(buildBaseUrl(getServerConfig()))
}

/**
 * 保存服务器配置
 */
export const saveServerConfig = (config: ServerConfig) => {
  const nextConfig = {
    ip: config.ip.trim(),
    port: config.port.trim()
  }
  uni.setStorageSync(STORAGE_KEY, nextConfig)
  return nextConfig
}

/**
 * 验证服务器配置是否有效
 */
export const isValidServerConfig = (config: ServerConfig): boolean => {
  const portNumber = Number(config.port)
  return Boolean(config.ip.trim()) && Number.isInteger(portNumber) && portNumber > 0 && portNumber <= 65535
}

// ============ 网络检测功能 ============

// 检测结果缓存时间（毫秒）
const DETECT_CACHE_TTL = 30000 // 30秒
const DETECT_CACHE_KEY = 'api_network_detect_cache'

interface DetectCache {
  internalReachable: boolean
  externalReachable: boolean
  timestamp: number
}

/**
 * 检测指定地址是否可达
 * @param baseUrl 基础地址
 * @param timeout 超时时间（毫秒）
 */
const checkNetworkReachable = async (baseUrl: string, timeout: number = 5000): Promise<boolean> => {
  return new Promise((resolve) => {
    const testUrl = `${normalizeBaseUrl(baseUrl)}/api/auth/captcha`

    uni.request({
      url: testUrl,
      method: 'GET',
      timeout,
      success: (res) => {
        resolve(res.statusCode === 200)
      },
      fail: () => {
        resolve(false)
      }
    })
  })
}

/**
 * 获取缓存的检测结果
 */
const getDetectCache = (): DetectCache | null => {
  const cache = uni.getStorageSync(DETECT_CACHE_KEY)
  if (!cache) return null

  const now = Date.now()
  if (now - cache.timestamp > DETECT_CACHE_TTL) {
    return null // 缓存过期
  }

  return cache
}

/**
 * 保存检测结果缓存
 */
const saveDetectCache = (internal: boolean, external: boolean) => {
  uni.setStorageSync(DETECT_CACHE_KEY, {
    internalReachable: internal,
    externalReachable: external,
    timestamp: Date.now()
  })
}

/**
 * 检测网络连通性（自动模式使用）
 * 并行检测内网和外网，返回最佳地址
 */
export const detectAndSelectBestNetwork = async (): Promise<{ baseUrl: string; mode: NetworkMode }> => {
  // 开发环境直接返回开发地址
  if (import.meta.env.DEV) {
    return { baseUrl: DEV_BASE_URL, mode: 'dev' }
  }

  // 检查缓存
  const cache = getDetectCache()
  if (cache) {
    if (cache.internalReachable) {
      saveServerConfig(parseBaseUrl(INTERNAL_BASE_URL))
      return { baseUrl: INTERNAL_BASE_URL, mode: 'internal' }
    } else if (cache.externalReachable) {
      saveServerConfig(parseBaseUrl(EXTERNAL_BASE_URL))
      return { baseUrl: EXTERNAL_BASE_URL, mode: 'external' }
    }
    // 两个都不可达，返回外网地址（用户可能还没打开服务器）
    return { baseUrl: EXTERNAL_BASE_URL, mode: 'external' }
  }

  // 并行检测内网和外网
  const [internalReachable, externalReachable] = await Promise.all([
    checkNetworkReachable(INTERNAL_BASE_URL),
    checkNetworkReachable(EXTERNAL_BASE_URL)
  ])

  // 保存检测结果缓存
  saveDetectCache(internalReachable, externalReachable)

  // 选择最佳网络
  if (internalReachable) {
    // 内网优先
    setNetworkMode('internal')
    saveServerConfig(parseBaseUrl(INTERNAL_BASE_URL))
    return { baseUrl: INTERNAL_BASE_URL, mode: 'internal' }
  } else if (externalReachable) {
    // 内网不可达，使用外网
    setNetworkMode('external')
    saveServerConfig(parseBaseUrl(EXTERNAL_BASE_URL))
    return { baseUrl: EXTERNAL_BASE_URL, mode: 'external' }
  } else {
    // 都不可达，默认外网（可能是服务器未启动）
    return { baseUrl: EXTERNAL_BASE_URL, mode: 'external' }
  }
}

/**
 * 初始化网络检测（App 启动时调用）
 */
export const initNetworkDetection = async (): Promise<string> => {
  const mode = getNetworkMode()

  // 如果用户手动设置了模式，直接使用
  if (mode === 'internal' || mode === 'external' || mode === 'dev') {
    const baseUrl = getBaseUrlByMode(mode)
    saveServerConfig(parseBaseUrl(baseUrl))
    return baseUrl
  }

  // 自动模式：检测并选择最佳网络
  const result = await detectAndSelectBestNetwork()
  return result.baseUrl
}

/**
 * 手动切换网络模式
 */
export const switchNetworkMode = async (mode: NetworkMode): Promise<string> => {
  setNetworkMode(mode)

  if (mode === 'auto') {
    // 自动模式需要重新检测
    uni.removeStorageSync(DETECT_CACHE_KEY)
    const result = await detectAndSelectBestNetwork()
    return result.baseUrl
  } else {
    const baseUrl = getBaseUrlByMode(mode)
    saveServerConfig(parseBaseUrl(baseUrl))
    return baseUrl
  }
}

/**
 * 获取网络模式显示名称
 * 注意：遵循项目规范，显示名称应从字典获取
 * 这里返回字典 key，由调用方通过 useDict 获取显示值
 */
export const getNetworkModeDictKey = (mode: NetworkMode): string => {
  const dictKeys: Record<NetworkMode, string> = {
    'internal': 'NETWORK_MODE_INTERNAL',
    'external': 'NETWORK_MODE_EXTERNAL',
    'auto': 'NETWORK_MODE_AUTO',
    'dev': 'NETWORK_MODE_DEV'
  }
  return dictKeys[mode] || 'NETWORK_MODE_AUTO'
}

/**
 * 获取所有可用的网络模式选项
 * 注意：返回字典 key，由调用方通过 useDict 获取显示值
 */
export const getNetworkModeOptions = (): Array<{ value: NetworkMode; dictKey: string }> => {
  return [
    { value: 'auto', dictKey: 'NETWORK_MODE_AUTO' },
    { value: 'internal', dictKey: 'NETWORK_MODE_INTERNAL' },
    { value: 'external', dictKey: 'NETWORK_MODE_EXTERNAL' }
  ]
}

/**
 * 获取内网地址
 */
export const getInternalBaseUrl = (): string => INTERNAL_BASE_URL

/**
 * 获取外网地址
 */
export const getExternalBaseUrl = (): string => EXTERNAL_BASE_URL

/**
 * 获取统一端口
 */
export const getServerPort = (): string => '32080'