
import http from '@/utils/http'
import type { ApiResponse } from '@/types'

// 下载导入模板（暂未实现）
export const downloadTemplate = async (): Promise<void> => {
  // 简单提示用户，模板功能暂未实现
  console.warn('模板下载功能暂未实现，请使用导出功能获取示例文件')
  // 这里可以直接跳转到导出功能，让用户先导出一个示例文件作为模板
  await exportProducts()
}

// 导入产品价格
export const importProducts = async (formData: FormData): Promise<ApiResponse<string>> => {
  return await http.post('/api/import/products', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

// 导出产品价格
export const exportProducts = async (): Promise<void> => {
  try {
    const response = await http.get('/api/import/products', {
      responseType: 'blob'
    })
    const url = window.URL.createObjectURL(new Blob([response.data]))
    const a = document.createElement('a')
    a.href = url
    const filename = `产品价格_${new Date().toISOString().slice(0, 10)}.xlsx`
    a.download = filename
    a.click()
    window.URL.revokeObjectURL(url)
  } catch (error) {
    console.error('导出失败:', error)
    throw error
  }
}
