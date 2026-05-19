import { defineConfig, presetUno, presetAttributify } from 'unocss'

export default defineConfig({
  // ========== 预设 ==========
  presets: [
    presetUno(),
    presetAttributify(),
  ],

  // ========== Vant 共存配置 ==========
  // scoped 模式，样式仅作用于当前组件
  mode: 'vue-scoped',

  // 排除 Vant 相关文件和类名
  exclude: [
    /node_modules\/vant/,
    /van-/,
  ],

  // 禁用默认重置，避免覆盖 Vant 样式
  preflights: {
    getCSS: () => '',
  },

  // ========== 主题配置 ==========
  theme: {
    // 字体大小 - 绑定到 CSS 变量，支持动态配置
    fontSize: {
      'xs': 'var(--font-size-xs)',
      'sm': 'var(--font-size-sm)',
      'base': 'var(--font-size-base)',
      'lg': 'var(--font-size-lg)',
      'xl': 'var(--font-size-xl)',
      '2xl': 'var(--font-size-2xl)',
      '3xl': 'var(--font-size-3xl)',
    },

    // 颜色 - 复用现有 CSS 变量
    colors: {
      primary: 'var(--primary-color)',
      'primary-light': 'var(--primary-light)',
      'primary-dark': 'var(--primary-dark)',
      secondary: 'var(--secondary-color)',
      success: 'var(--success-color)',
      warning: 'var(--warning-color)',
      error: 'var(--error-color)',
      info: 'var(--info-color)',
    },

    // 间距
    spacing: {
      'xs': 'var(--spacing-xs)',
      'sm': 'var(--spacing-sm)',
      'md': 'var(--spacing-md)',
      'lg': 'var(--spacing-lg)',
      'xl': 'var(--spacing-xl)',
      '2xl': 'var(--spacing-2xl)',
      'page': 'var(--spacing-lg)',
      'section': 'var(--spacing-md)',
      'item': 'var(--spacing-sm)',
    },

    // 阴影层级
    boxShadow: {
      'card': 'var(--shadow-sm)',
      'card-hover': 'var(--shadow-md)',
      'modal': 'var(--shadow-lg)',
    },

    // 圆角
    borderRadius: {
      'sm': 'var(--radius-sm)',
      'DEFAULT': 'var(--radius)',
      'md': 'var(--radius-md)',
      'lg': 'var(--radius-lg)',
      'xl': 'var(--radius-xl)',
    },
  },

  // ========== 快捷方式（语义化） ==========
  shortcuts: {
    // ========== 页面级布局（组件化） ==========
    'page': 'min-h-screen bg-gray-50',
    'page-pc': 'page flex flex-col gap-lg',
    'page-mobile': 'page flex flex-col',

    // ========== 区块级布局 ==========
    'section': 'bg-white rounded-lg shadow-sm p-lg',
    'section-sm': 'bg-white rounded-lg shadow-sm p-md',
    'section-flat': 'bg-white rounded-lg shadow-sm',

    // ========== 头部布局 ==========
    'header-row': 'flex items-center justify-between gap-md',
    'header-title': 'text-2xl font-semibold text-gray-900',
    'header-actions': 'flex items-center gap-sm',

    // ========== 筛选栏 ==========
    'filter': 'bg-white rounded-lg shadow-sm p-sm md:p-md flex items-center gap-md flex-wrap',
    'filter-left': 'flex items-center gap-sm flex-wrap flex-1',
    'filter-right': 'flex items-center gap-sm',

    // ========== 表格 ==========
    'table': 'bg-white rounded-lg shadow-sm overflow-hidden',
    'table-head': 'bg-gray-50 border-b border-gray-200 px-lg py-md',
    'table-body': 'divide-y divide-gray-100',
    'table-row': 'px-lg py-md hover:bg-gray-50 transition-colors',
    'table-cell': 'text-sm text-gray-800',
    'table-wrapper': 'bg-white rounded-lg shadow-sm overflow-x-auto',
    'table-row-min': 'min-w-[800px]',
    'table-col-sm': 'w-[8%]',
    'table-col-md': 'w-[15%]',
    'table-col-lg': 'w-[20%]',
    'table-col-xl': 'w-[25%]',

    // ========== 卡片网格 ==========
    'grid-2': 'grid grid-cols-1 md:grid-cols-2 gap-lg',
    'grid-3': 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-lg',
    'grid-4': 'grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-lg',
    'grid-stats': 'grid grid-cols-2 md:grid-cols-4 gap-md',

    // ========== 表单 ==========
    'form': 'flex flex-col gap-md',
    'form-row': 'flex gap-md flex-wrap',
    'form-item': 'flex flex-col gap-xs',
    'form-label': 'text-sm font-medium text-gray-700',
    'form-input': 'px-md py-sm border border-gray-300 rounded-lg focus:border-primary focus:outline-none',

    // ========== 状态指示 ==========
    'status-badge': 'inline-flex items-center px-sm py-xs rounded-full text-xs font-medium',
    'status-active': 'status-badge bg-success/10 text-success',
    'status-inactive': 'status-badge bg-gray-100 text-gray-400',
    'status-warning': 'status-badge bg-warning/10 text-warning',
    'status-error': 'status-badge bg-error/10 text-error',

    // ========== 交互状态 ==========
    'hoverable': 'transition-all hover:bg-gray-50',
    'clickable': 'cursor-pointer transition-all hover:opacity-80 active:opacity-60',
    'focusable': 'focus:outline-none focus:ring-2 focus:ring-primary/50',

    // ========== 空状态 ==========
    'empty': 'flex flex-col items-center justify-center py-2xl text-gray-400',
    'empty-icon': 'w-12 h-12 text-gray-300 mb-lg',
    'empty-text': 'text-sm text-gray-400',

    // 字体大小语义化别名
    'text-caption': 'text-xs',
    'text-body-sm': 'text-sm',
    'text-body': 'text-base',
    'text-subtitle': 'text-lg',
    'text-title': 'text-xl',
    'text-heading': 'text-2xl',
    'text-hero': 'text-3xl',

    // 布局
    'flex-center': 'flex items-center justify-center',
    'flex-between': 'flex items-center justify-between',
    'flex-col-center': 'flex flex-col items-center',

    // 卡片
    'card': 'bg-white rounded-lg shadow-md p-lg',
    'card-hover': 'card transition-all hover:shadow-lg hover:-translate-y-0.5',

    // 按钮（自定义按钮，不影响 Vant）
    'btn': 'px-4 py-2 rounded-lg font-medium transition-all cursor-pointer',
    'btn-primary': 'btn bg-primary text-white hover:opacity-90',
    'btn-outline': 'btn border-2 border-primary text-primary bg-transparent hover:bg-primary hover:text-white',
    'btn-sm': 'px-3 py-1.5 text-sm rounded',
    'btn-lg': 'px-6 py-3 text-lg rounded-lg',

    // 输入框（自定义输入框，不影响 Vant）
    'input-field': 'px-4 py-2 border border-gray-300 rounded-lg focus:border-primary focus:outline-none transition-colors',
  },

  // ========== 自定义规则 ==========
  rules: [
    // 安全区域
    ['safe-area-inset-bottom', { 'padding-bottom': 'env(safe-area-inset-bottom)' }],
    ['safe-area-inset-top', { 'padding-top': 'env(safe-area-inset-top)' }],

    // 文字截断
    ['truncate-2', {
      'display': '-webkit-box',
      '-webkit-line-clamp': '2',
      '-webkit-box-orient': 'vertical',
      'overflow': 'hidden',
    }],
    ['truncate-3', {
      'display': '-webkit-box',
      '-webkit-line-clamp': '3',
      '-webkit-box-orient': 'vertical',
      'overflow': 'hidden',
    }],
  ],

  // ========== 响应式断点 ==========
  breakpoints: {
    sm: '640px',
    md: '768px',
    lg: '1024px',
    xl: '1280px',
  },
})
