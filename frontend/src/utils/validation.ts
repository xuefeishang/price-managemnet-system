/**
 * 表单验证规则工具
 */

// 必填验证
export const required = (message: string = '此字段必填') => ({
  required: true,
  message,
  trigger: ['blur', 'change']
})

// 邮箱验证
export const email = (message: string = '邮箱格式不正确') => ({
  type: 'email' as const,
  message,
  trigger: ['blur', 'change']
})

// 手机号验证
export const phone = (message: string = '手机号格式不正确') => ({
  pattern: /^1[3-9]\d{9}$/,
  message,
  trigger: ['blur', 'change']
})

// 密码验证（6-20位，包含字母和数字）
export const password = (message: string = '密码长度6-20位，需包含字母和数字') => ({
  pattern: /^(?=.*[A-Za-z])(?=.*\d)[A-Za-z\d@$!%*#?&]{6,20}$/,
  message,
  trigger: ['blur', 'change']
})

// 网址验证
export const url = (message: string = '网址格式不正确') => ({
  pattern: /^(https?:\/\/)?([\da-z.-]+)\.([a-z.]{2,6})([\/\w .-]*)*\/?$/,
  message,
  trigger: ['blur', 'change']
})

// 金额验证（正数，最多两位小数）
export const price = (message: string = '金额格式不正确') => ({
  pattern: /^(0|[1-9]\d*)(\.\d{1,2})?$/,
  message,
  trigger: ['blur', 'change']
})

// 数量验证（正整数）
export const quantity = (message: string = '数量必须为正整数') => ({
  pattern: /^[1-9]\d*$/,
  message,
  trigger: ['blur', 'change']
})

// 长度范围验证
export const lengthRange = (
  min: number,
  max: number,
  message?: string
) => ({
  min,
  max,
  message: message || `长度应在${min}-${max}个字符之间`,
  trigger: ['blur', 'change']
})

// 自定义正则验证
export const pattern = (
  regex: RegExp,
  message: string
) => ({
  pattern: regex,
  message,
  trigger: ['blur', 'change']
})

/**
 * 简单表单验证函数
 */
export const validators = {
  // 验证是否为空
  isEmpty(value: any): boolean {
    if (value === null || value === undefined) return true
    if (typeof value === 'string') return value.trim() === ''
    if (Array.isArray(value)) return value.length === 0
    return false
  },

  // 验证邮箱
  isEmail(email: string): boolean {
    return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)
  },

  // 验证手机号
  isPhone(phone: string): boolean {
    return /^1[3-9]\d{9}$/.test(phone)
  },

  // 验证URL
  isUrl(url: string): boolean {
    return /^(https?:\/\/)?([\da-z.-]+)\.([a-z.]{2,6})([\/\w .-]*)*\/?$/.test(url)
  },

  // 验证金额
  isPrice(price: string | number): boolean {
    return /^(0|[1-9]\d*)(\.\d{1,2})?$/.test(String(price))
  },

  // 验证正整数
  isPositiveInteger(num: string | number): boolean {
    return /^[1-9]\d*$/.test(String(num))
  },

  // 验证密码强度（返回0-4的强度等级）
  getPasswordStrength(password: string): number {
    if (!password) return 0

    let strength = 0
    if (password.length >= 6) strength++
    if (password.length >= 8) strength++
    if (/[a-z]/.test(password) && /[A-Z]/.test(password)) strength++
    if (/\d/.test(password)) strength++
    if (/[!@#$%^&*(),.?":{}|<>]/.test(password)) strength++

    return Math.min(strength, 4)
  },

  // 获取密码强度文本
  getPasswordStrengthText(password: string): { level: number; text: string; color: string } {
    const strength = this.getPasswordStrength(password)

    if (strength <= 1) return { level: 1, text: '弱', color: '#ff4d4f' }
    if (strength <= 2) return { level: 2, text: '中', color: '#faad14' }
    if (strength <= 3) return { level: 3, text: '强', color: '#52c41a' }
    return { level: 4, text: '非常强', color: '#1890ff' }
  }
}

export default validators
