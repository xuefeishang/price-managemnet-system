const STORAGE_KEY = 'api_server_config'
const DEFAULT_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://127.0.0.1:8080'

export interface ServerConfig {
  ip: string
  port: string
}

const normalizeBaseUrl = (url: string) => url.replace(/\/$/, '')

const parseBaseUrl = (url: string): ServerConfig => {
  const normalizedUrl = normalizeBaseUrl(url)
  const matched = normalizedUrl.match(/^https?:\/\/([^:/]+)(?::(\d+))?/)

  return {
    ip: matched?.[1] || '127.0.0.1',
    port: matched?.[2] || '8080'
  }
}

const buildBaseUrl = (config: ServerConfig) => `http://${config.ip.trim()}:${config.port.trim()}`

export const getDefaultServerConfig = () => parseBaseUrl(DEFAULT_BASE_URL)

export const getServerConfig = (): ServerConfig => {
  const storedConfig = uni.getStorageSync(STORAGE_KEY)
  if (storedConfig?.ip && storedConfig?.port) {
    return storedConfig
  }

  return getDefaultServerConfig()
}

export const getApiBaseUrl = () => normalizeBaseUrl(buildBaseUrl(getServerConfig()))

export const saveServerConfig = (config: ServerConfig) => {
  const nextConfig = {
    ip: config.ip.trim(),
    port: config.port.trim()
  }
  uni.setStorageSync(STORAGE_KEY, nextConfig)
  return nextConfig
}

export const isValidServerConfig = (config: ServerConfig) => {
  const portNumber = Number(config.port)
  return Boolean(config.ip.trim()) && Number.isInteger(portNumber) && portNumber > 0 && portNumber <= 65535
}
