/**
 * 日期格式化
 * @param date Date对象或日期字符串
 * @param format 格式字符串，默认 'YYYY-MM-DD HH:mm:ss'
 * @returns 格式化后的日期字符串
 */
export const formatDate = (
  date: Date | string | number | null | undefined,
  format: string = 'YYYY-MM-DD HH:mm:ss'
): string => {
  if (!date) return '-'

  const d = new Date(date)
  if (isNaN(d.getTime())) return '-'

  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  const hours = String(d.getHours()).padStart(2, '0')
  const minutes = String(d.getMinutes()).padStart(2, '0')
  const seconds = String(d.getSeconds()).padStart(2, '0')

  return format
    .replace('YYYY', String(year))
    .replace('MM', month)
    .replace('DD', day)
    .replace('HH', hours)
    .replace('mm', minutes)
    .replace('ss', seconds)
}

/**
 * 相对时间格式化（如：刚刚、5分钟前、2小时前）
 * @param date 日期
 * @returns 相对时间字符串
 */
export const formatRelativeTime = (
  date: Date | string | number | null | undefined
): string => {
  if (!date) return '-'

  const d = new Date(date)
  if (isNaN(d.getTime())) return '-'

  const now = Date.now()
  const diff = now - d.getTime()

  const seconds = Math.floor(diff / 1000)
  const minutes = Math.floor(seconds / 60)
  const hours = Math.floor(minutes / 60)
  const days = Math.floor(hours / 24)

  if (seconds < 60) return '刚刚'
  if (minutes < 60) return `${minutes}分钟前`
  if (hours < 24) return `${hours}小时前`
  if (days < 7) return `${days}天前`

  return formatDate(d, 'YYYY-MM-DD')
}

/**
 * 金额格式化
 * @param amount 金额数值
 * @param decimals 小数位数，默认2位
 * @param prefix 前缀符号，默认 '¥'
 * @returns 格式化后的金额字符串
 */
export const formatCurrency = (
  amount: number | string | null | undefined,
  decimals: number = 2,
  prefix: string = '¥'
): string => {
  if (amount === null || amount === undefined || amount === '') return '-'

  const num = typeof amount === 'string' ? parseFloat(amount) : amount
  if (isNaN(num)) return '-'

  return `${prefix}${num.toFixed(decimals).replace(/\B(?=(\d{3})+(?!\d))/g, ',')}`
}

/**
 * 数字格式化（添加千分位）
 * @param num 数字
 * @returns 格式化后的字符串
 */
export const formatNumber = (
  num: number | string | null | undefined
): string => {
  if (num === null || num === undefined || num === '') return '-'

  const n = typeof num === 'string' ? parseFloat(num) : num
  if (isNaN(n)) return '-'

  return n.toString().replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

/**
 * 文件大小格式化
 * @param bytes 字节数
 * @returns 格式化后的文件大小字符串
 */
export const formatFileSize = (bytes: number): string => {
  if (bytes === 0) return '0 B'

  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  const k = 1024
  const i = Math.floor(Math.log(bytes) / Math.log(k))

  return `${(bytes / Math.pow(k, i)).toFixed(2)} ${units[i]}`
}

/**
 * 截断文本并添加省略号
 * @param text 原始文本
 * @param maxLength 最大长度
 * @returns 截断后的文本
 */
export const truncateText = (
  text: string | null | undefined,
  maxLength: number = 50
): string => {
  if (!text) return '-'
  if (text.length <= maxLength) return text
  return text.substring(0, maxLength) + '...'
}

/**
 * 手机号格式化（显示为 138****8888）
 * @param phone 手机号
 * @returns 格式化后的手机号
 */
export const formatPhone = (phone: string | null | undefined): string => {
  if (!phone) return '-'
  if (phone.length !== 11) return phone
  return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}

/**
 * 邮箱格式化（显示为 t***@example.com）
 * @param email 邮箱
 * @returns 格式化后的邮箱
 */
export const formatEmail = (email: string | null | undefined): string => {
  if (!email) return '-'
  const parts = email.split('@')
  if (parts.length !== 2) return email
  if (parts[0].length <= 3) return email
  return parts[0].substring(0, 3) + '***@' + parts[1]
}
