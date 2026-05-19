/**
 * 页面布局迁移脚本
 * 自动扫描并替换页面根容器样式，迁移到 PageContainer 组件
 *
 * 使用方法：npx ts-node scripts/migrate-page-layout.ts
 */

import fs from 'fs'
import path from 'path'

const VIEWS_DIR = path.resolve(__dirname, '../src/views')

const MIGRATION_RULES = [
  // 1. 替换根容器类名
  {
    from: /class="(\w+-page)"(\s*)>/g,
    to: (match: string, p1: string, p2: string) => {
      // 保留特殊页面（login）
      if (p1 === 'login-page') return match
      return `class="page-container"${p2}>`
    }
  },

  // 2. 删除硬编码背景色
  {
    from: /background-color:\s*(#FAFAFA|#F5F5F5);?\n?/g,
    to: ''
  },

  // 3. 删除 min-height: 100vh
  {
    from: /min-height:\s*100vh;?\n?/g,
    to: ''
  },

  // 4. 删除冗余 @import
  {
    from: /@import url\('https:\/\/fonts\.googleapis\.com\/css2\?family=Inter[^']+'\);?\n?/g,
    to: ''
  },

  // 5. 替换 flex 布局为 UnoCSS 类
  {
    from: /\.(\w+-page)\s*\{\s*display:\s*flex;\s*flex-direction:\s*column;\s*gap:\s*var\(--spacing-lg\);\s*\}/g,
    to: ''
  },

  // 6. 替换表格 overflow: hidden 为 overflow-x: auto
  {
    from: /\.dict-table\s*\{\s*[^}]*overflow:\s*hidden/g,
    to: '.dict-table { overflow-x: auto'
  },
]

function migrateFile(filePath: string): boolean {
  let content = fs.readFileSync(filePath, 'utf-8')
  let changed = false

  for (const rule of MIGRATION_RULES) {
    const newContent = content.replace(rule.from, rule.to)
    if (newContent !== content) {
      content = newContent
      changed = true
    }
  }

  if (changed) {
    // 添加 PageContainer import（如果使用了 page-container）
    if (content.includes('class="page-container"')) {
      const scriptSetupMatch = content.match(/<script setup lang="ts">/)
      if (scriptSetupMatch) {
        // 检查是否已有 PageContainer import
        if (!content.includes('PageContainer')) {
          content = content.replace(
            '<script setup lang="ts">',
            '<script setup lang="ts">\nimport PageContainer from \'@/components/PageContainer.vue\''
          )
        }
      }
    }

    fs.writeFileSync(filePath, content)
    return true
  }

  return false
}

function main() {
  // 检查目录是否存在
  if (!fs.existsSync(VIEWS_DIR)) {
    console.error(`Views directory not found: ${VIEWS_DIR}`)
    process.exit(1)
  }

  const files = fs.readdirSync(VIEWS_DIR)
    .filter(f => f.endsWith('.vue'))

  let migrated = 0
  for (const file of files) {
    const filePath = path.join(VIEWS_DIR, file)
    if (migrateFile(filePath)) {
      console.log(`✓ Migrated: ${file}`)
      migrated++
    } else {
      console.log(`○ Skipped: ${file}`)
    }
  }

  console.log(`\n迁移完成: ${migrated}/${files.length} 个文件`)
}

main()