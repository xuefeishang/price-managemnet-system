/**
 * 分类视觉预览状态管理
 * 分类主数据来自 product_category，视觉配置保存到 category_visual_config 字典。
 */

import { ref, reactive, computed } from 'vue'
import { getCategories } from '@/api/categories'
import { createDict, getDicts, updateDict } from '@/api/dict'
import { refreshDictCache } from '@/composables/useDict'
import { clearCategoryVisualCache } from '@/composables/useCategoryVisual'
import { useTheme } from '@/composables/useTheme'
import {
  CATEGORY_VISUAL_COMBOS,
  CATEGORY_VISUAL_PRESETS,
  CATEGORY_VISUAL_PRESET_GROUPS,
  type CategoryVisualCombo,
  type CategoryVisualPresetGroup,
  type CategoryVisualPreset,
  buildCategoryVisualConfigFromPreset,
  getCategoryVisualPreset,
  getPresetByCombo,
  getRecommendedCategoryVisualPresets,
  getRecommendedPresetGroup
} from '@/constants/categoryVisualPresets'
import { checkCategoryVisualColors } from '@/utils/categoryVisualColor'
import type { CategoryVisualConfig, ProductCategory, SysDict } from '@/types'

export interface CategoryInfo {
  id: number
  name: string
  code: string
  sortOrder?: number
}

export type CategoryVisualComboScope = 'unconfigured' | 'all'

export interface CategoryVisualComboSummary {
  updated: number
  skipped: number
  fallback: number
}

export interface CategoryVisualCustomComboMapping {
  categoryId?: number
  categoryCode?: string
  presetId?: string
  presetVersion?: number
  customized?: boolean
  config: Partial<CategoryVisualConfig>
}

export interface CategoryVisualCustomCombo {
  version: number
  name: string
  description: string
  updatedAt: string
  mappings: CategoryVisualCustomComboMapping[]
}

export interface CategoryVisualComboPreviewItem {
  category: CategoryInfo
  presetName: string
  icon: string
  primaryColor: string
  surfaceColor?: string
  borderColor: string
  fallback: boolean
  skipped: boolean
  reason?: string
}

const CUSTOM_COMBO_CATEGORY = 'category_visual_custom_combo'
const CUSTOM_COMBO_KEY = 'my_combo'
const CUSTOM_COMBO_ID = 'custom_my_combo'

export const CATEGORY_PRESET_COLORS = CATEGORY_VISUAL_PRESETS.reduce<Record<string, CategoryVisualConfig>>((map, preset) => {
  map[preset.id] = buildCategoryVisualConfigFromPreset({ id: 0, code: preset.id.toUpperCase() }, preset)
  return map
}, {})

const categories = ref<CategoryInfo[]>([])
const selectedCategory = ref<CategoryInfo | null>(null)
const visualConfigs = reactive<Record<number, CategoryVisualConfig>>({})
const visualDictIds = reactive<Record<number, number | undefined>>({})
const loading = ref(false)
const saving = ref(false)
const isLoaded = ref(false)
const hasUnsavedChanges = ref(false)
const dirtyCategoryIds = new Set<number>()
const activePresetGroup = ref<CategoryVisualPresetGroup>('ore_metal')
const lastComboSummary = ref<CategoryVisualComboSummary | null>(null)
const selectedComboId = ref(CATEGORY_VISUAL_COMBOS[0]?.id || '')
const customCombo = ref<CategoryVisualCustomCombo | null>(null)
const customComboDictId = ref<number | undefined>()

const normalizeConfig = (category: CategoryInfo, config?: Partial<CategoryVisualConfig>): CategoryVisualConfig => {
  if (config?.presetId) {
    return {
      ...buildCategoryVisualConfigFromPreset(category, getCategoryVisualPreset(config.presetId)),
      ...config,
      categoryId: category.id,
      categoryCode: category.code,
      iconType: config.iconType || 'builtin'
    }
  }

  if (config?.primaryColor) {
    return {
      ...buildCategoryVisualConfigFromPreset(category, getRecommendedCategoryVisualPresets(category.name, category.code)[0]),
      ...config,
      categoryId: category.id,
      categoryCode: category.code,
      customized: true,
      iconType: config.iconType || 'builtin'
    }
  }

  return buildCategoryVisualConfigFromPreset(category, getRecommendedCategoryVisualPresets(category.name, category.code)[0])
}

const parseVisualDicts = (dicts: SysDict[]) => {
  const byCode = new Map<string, SysDict>()
  const byId = new Map<number, SysDict>()

  dicts.forEach(dict => {
    try {
      const config = JSON.parse(dict.extraValue || '{}') as CategoryVisualConfig
      if (config.categoryCode) byCode.set(config.categoryCode, dict)
      if (config.categoryId) byId.set(config.categoryId, dict)
      if (!config.categoryCode && dict.dictKey) byCode.set(dict.dictKey, dict)
    } catch {
      if (dict.dictKey) byCode.set(dict.dictKey, dict)
    }
  })

  return { byCode, byId }
}

const loadCategories = async (force = false): Promise<void> => {
  if ((isLoaded.value && !force) || loading.value) return
  loading.value = true

  try {
    const [categoryRes, visualDictRes, customComboRes] = await Promise.all([
      getCategories('ACTIVE'),
      getDicts('category_visual_config'),
      getDicts(CUSTOM_COMBO_CATEGORY)
    ])

    const activeCategories = (categoryRes.data || [])
      .filter((category: ProductCategory) => category.status === 'ACTIVE')
      .sort((a, b) => (a.sortOrder || 0) - (b.sortOrder || 0))
      .map(category => ({
        id: category.id,
        name: category.name,
        code: category.code,
        sortOrder: category.sortOrder
      }))

    const dictMaps = parseVisualDicts(visualDictRes.data || [])
    const customDict = (customComboRes.data || []).find(dict => dict.dictKey === CUSTOM_COMBO_KEY)
    customComboDictId.value = customDict?.id
    customCombo.value = null
    if (customDict?.extraValue) {
      try {
        customCombo.value = JSON.parse(customDict.extraValue) as CategoryVisualCustomCombo
      } catch {
        customCombo.value = null
      }
    }

    categories.value = activeCategories
    Object.keys(visualConfigs).forEach(key => {
      delete visualConfigs[Number(key)]
    })
    Object.keys(visualDictIds).forEach(key => {
      delete visualDictIds[Number(key)]
    })

    activeCategories.forEach(category => {
      const dict = dictMaps.byId.get(category.id) || dictMaps.byCode.get(category.code)
      let parsed: Partial<CategoryVisualConfig> | undefined

      if (dict?.extraValue) {
        try {
          parsed = JSON.parse(dict.extraValue) as CategoryVisualConfig
        } catch {
          parsed = undefined
        }
      }

      visualConfigs[category.id] = normalizeConfig(category, parsed)
      visualDictIds[category.id] = dict?.id
    })

    if (!selectedCategory.value || !activeCategories.some(category => category.id === selectedCategory.value?.id)) {
      selectedCategory.value = activeCategories[0] || null
    }

    if (selectedCategory.value) {
      activePresetGroup.value = getRecommendedPresetGroup(selectedCategory.value.name, selectedCategory.value.code)
    }

    isLoaded.value = true
    hasUnsavedChanges.value = false
    dirtyCategoryIds.clear()
    lastComboSummary.value = null
  } catch (error) {
    console.error('Failed to load categories:', error)
  } finally {
    loading.value = false
  }
}

const discardChanges = async (): Promise<void> => {
  isLoaded.value = false
  hasUnsavedChanges.value = false
  dirtyCategoryIds.clear()
  lastComboSummary.value = null
  await loadCategories(true)
}

const selectCategory = (category: CategoryInfo): void => {
  selectedCategory.value = category
  activePresetGroup.value = getRecommendedPresetGroup(category.name, category.code)
}

const currentVisualConfig = computed<CategoryVisualConfig | null>(() => {
  if (!selectedCategory.value) return null
  return visualConfigs[selectedCategory.value.id] || null
})

const currentPreset = computed<CategoryVisualPreset | null>(() => {
  const config = currentVisualConfig.value
  if (!config?.presetId) return null
  return getCategoryVisualPreset(config.presetId)
})

const currentComboPreviewItem = computed<CategoryVisualComboPreviewItem | null>(() => {
  if (!selectedCategory.value) return null
  const preview = selectedComboPreview.value.find(item => item.category.id === selectedCategory.value!.id)
  return preview || null
})

const recommendedPresets = computed<CategoryVisualPreset[]>(() => {
  const category = selectedCategory.value
  if (!category) return CATEGORY_VISUAL_PRESETS.slice(0, 3)
  return getRecommendedCategoryVisualPresets(category.name, category.code)
})

const groupedPresets = computed<Record<CategoryVisualPresetGroup, CategoryVisualPreset[]>>(() => {
  return CATEGORY_VISUAL_PRESET_GROUPS.reduce((map, group) => {
    map[group.key] = CATEGORY_VISUAL_PRESETS.filter(preset => preset.group === group.key)
    return map
  }, {} as Record<CategoryVisualPresetGroup, CategoryVisualPreset[]>)
})

const activeGroupPresets = computed<CategoryVisualPreset[]>(() => groupedPresets.value[activePresetGroup.value] || [])

const setActivePresetGroup = (group: CategoryVisualPresetGroup): void => {
  activePresetGroup.value = group
}

const selectCombo = (comboId: string): void => {
  selectedComboId.value = comboId
}

const updateVisualConfig = (patch: Partial<CategoryVisualConfig>, markCustomized = true): void => {
  if (!selectedCategory.value) return
  const current = visualConfigs[selectedCategory.value.id]
  if (current) {
    visualConfigs[selectedCategory.value.id] = {
      ...current,
      ...patch,
      categoryId: selectedCategory.value.id,
      categoryCode: selectedCategory.value.code,
      customized: markCustomized ? true : patch.customized ?? current.customized
    }
    hasUnsavedChanges.value = true
    dirtyCategoryIds.add(selectedCategory.value.id)
  }
}

const applyPreset = (preset: CategoryVisualPreset): void => {
  if (!selectedCategory.value) return
  visualConfigs[selectedCategory.value.id] = buildCategoryVisualConfigFromPreset(selectedCategory.value, preset)
  hasUnsavedChanges.value = true
  dirtyCategoryIds.add(selectedCategory.value.id)
}

const applyCombo = (combo: CategoryVisualCombo, scope: CategoryVisualComboScope = 'unconfigured'): CategoryVisualComboSummary => {
  let updated = 0
  let skipped = 0
  let fallback = 0

  categories.value.forEach((category, index) => {
    const current = visualConfigs[category.id]
    const hasStoredConfig = Boolean(visualDictIds[category.id])
    const shouldSkipByScope = scope === 'unconfigured' && hasStoredConfig
    const shouldSkipCustomized = Boolean(current?.customized)

    if (shouldSkipByScope || shouldSkipCustomized) {
      skipped += 1
      return
    }

    const preset = getPresetByCombo(combo, category.name, category.code, index)
    const text = `${category.name} ${category.code}`.toLowerCase()
    const matchedRule = combo.rules.find(rule =>
      rule.keywords.some(keyword => text.includes(keyword.toLowerCase()))
    )

    if (!matchedRule) fallback += 1
    visualConfigs[category.id] = buildCategoryVisualConfigFromPreset(category, preset)
    dirtyCategoryIds.add(category.id)
    updated += 1
  })

  if (updated > 0) {
    hasUnsavedChanges.value = true
  }

  lastComboSummary.value = { updated, skipped, fallback }
  return lastComboSummary.value
}

const findCustomMapping = (category: CategoryInfo): CategoryVisualCustomComboMapping | undefined => {
  return customCombo.value?.mappings.find(mapping =>
    (mapping.categoryCode && mapping.categoryCode === category.code) ||
    (mapping.categoryId && mapping.categoryId === category.id)
  )
}

const getBuiltInComboPreview = (combo: CategoryVisualCombo, scope: CategoryVisualComboScope): CategoryVisualComboPreviewItem[] => {
  return categories.value.map((category, index) => {
    const current = visualConfigs[category.id]
    const hasStoredConfig = Boolean(visualDictIds[category.id])
    const skippedByScope = scope === 'unconfigured' && hasStoredConfig
    const skippedByCustom = Boolean(current?.customized)
    const text = `${category.name} ${category.code}`.toLowerCase()
    const matchedRule = combo.rules.find(rule =>
      rule.keywords.some(keyword => text.includes(keyword.toLowerCase()))
    )
    const preset = getPresetByCombo(combo, category.name, category.code, index)
    const skipped = skippedByScope || skippedByCustom
    // 预览始终显示组合效果，让用户看到「如果应用会是什么样」
    const previewConfig = buildCategoryVisualConfigFromPreset(category, preset)
    const presetName = preset.name

    return {
      category,
      presetName,
      icon: previewConfig.icon,
      primaryColor: previewConfig.primaryColor,
      surfaceColor: previewConfig.surfaceColor,
      borderColor: previewConfig.borderColor,
      fallback: !matchedRule,
      skipped,
      reason: skippedByCustom ? '已微调' : skippedByScope ? '已有配置' : undefined
    }
  })
}

const getCustomComboPreview = (scope: CategoryVisualComboScope): CategoryVisualComboPreviewItem[] => {
  return categories.value.map(category => {
    const current = visualConfigs[category.id]
    const mapping = findCustomMapping(category)
    const config = mapping ? normalizeConfig(category, mapping.config) : current
    const hasStoredConfig = Boolean(visualDictIds[category.id])
    const skippedByScope = scope === 'unconfigured' && hasStoredConfig
    const skippedByCustom = Boolean(current?.customized)
    const skippedByMissing = !mapping
    const preset = getCategoryVisualPreset(config?.presetId)

    return {
      category,
      presetName: mapping ? preset.name : '无匹配',
      icon: config?.icon || 'cube_ore',
      primaryColor: config?.primaryColor || '#2563EB',
      surfaceColor: config?.surfaceColor,
      borderColor: config?.borderColor || '#BFDBFE',
      fallback: false,
      skipped: skippedByScope || skippedByCustom || skippedByMissing,
      reason: skippedByMissing ? '自定义组合未包含' : skippedByCustom ? '已微调' : skippedByScope ? '已有配置' : undefined
    }
  })
}

const selectedComboPreview = computed<CategoryVisualComboPreviewItem[]>(() => {
  if (selectedComboId.value === CUSTOM_COMBO_ID) {
    return getCustomComboPreview('unconfigured')
  }

  const combo = CATEGORY_VISUAL_COMBOS.find(item => item.id === selectedComboId.value) || CATEGORY_VISUAL_COMBOS[0]
  return combo ? getBuiltInComboPreview(combo, 'unconfigured') : []
})

const previewSelectedCombo = (scope: CategoryVisualComboScope): CategoryVisualComboPreviewItem[] => {
  if (selectedComboId.value === CUSTOM_COMBO_ID) {
    return getCustomComboPreview(scope)
  }

  const combo = CATEGORY_VISUAL_COMBOS.find(item => item.id === selectedComboId.value) || CATEGORY_VISUAL_COMBOS[0]
  return combo ? getBuiltInComboPreview(combo, scope) : []
}

const applyCustomCombo = (scope: CategoryVisualComboScope = 'unconfigured'): CategoryVisualComboSummary => {
  let updated = 0
  let skipped = 0
  let fallback = 0

  categories.value.forEach(category => {
    const current = visualConfigs[category.id]
    const mapping = findCustomMapping(category)
    const hasStoredConfig = Boolean(visualDictIds[category.id])
    const shouldSkipByScope = scope === 'unconfigured' && hasStoredConfig
    const shouldSkipCustomized = Boolean(current?.customized)

    if (!mapping || shouldSkipByScope || shouldSkipCustomized) {
      skipped += 1
      return
    }

    visualConfigs[category.id] = normalizeConfig(category, mapping.config)
    dirtyCategoryIds.add(category.id)
    updated += 1
  })

  if (updated > 0) {
    hasUnsavedChanges.value = true
  }

  lastComboSummary.value = { updated, skipped, fallback }
  return lastComboSummary.value
}

const applySelectedCombo = (scope: CategoryVisualComboScope = 'unconfigured'): CategoryVisualComboSummary => {
  if (selectedComboId.value === CUSTOM_COMBO_ID) {
    return applyCustomCombo(scope)
  }

  const combo = CATEGORY_VISUAL_COMBOS.find(item => item.id === selectedComboId.value) || CATEGORY_VISUAL_COMBOS[0]
  return combo ? applyCombo(combo, scope) : { updated: 0, skipped: categories.value.length, fallback: 0 }
}

const saveCustomCombo = async (): Promise<void> => {
  const now = new Date().toISOString()
  const nextCombo: CategoryVisualCustomCombo = {
    version: 1,
    name: '我的组合',
    description: '由当前分类视觉保存',
    updatedAt: now,
    mappings: categories.value.map(category => {
      const config = visualConfigs[category.id]
      return {
        categoryId: category.id,
        categoryCode: category.code,
        presetId: config?.presetId,
        presetVersion: config?.presetVersion,
        customized: Boolean(config?.customized),
        config: config ? serializeVisualConfig(config) : {}
      }
    })
  }

  const payload = {
    category: CUSTOM_COMBO_CATEGORY,
    dictKey: CUSTOM_COMBO_KEY,
    dictValue: nextCombo.name,
    extraValue: JSON.stringify(nextCombo),
    sortOrder: 1,
    status: 'ACTIVE' as const,
    remark: '分类视觉自定义组合'
  }

  if (customComboDictId.value) {
    await updateDict(customComboDictId.value, payload)
  } else {
    const response = await createDict(payload)
    customComboDictId.value = response.data.id
  }

  customCombo.value = nextCombo
  selectedComboId.value = CUSTOM_COMBO_ID
  await refreshDictCache()
}

const resetCurrentPreset = (): void => {
  if (!selectedCategory.value) return
  const preset = currentPreset.value || recommendedPresets.value[0]
  visualConfigs[selectedCategory.value.id] = buildCategoryVisualConfigFromPreset(selectedCategory.value, preset)
  hasUnsavedChanges.value = true
  dirtyCategoryIds.add(selectedCategory.value.id)
}

const getValidationWarnings = (config: CategoryVisualConfig | null): string[] => {
  if (!config) return []
  const { themeConfig } = useTheme()
  return checkCategoryVisualColors(config, themeConfig.value.chartPrimaryColor || '#0D6E6E').warnings
}

const currentValidationWarnings = computed(() => getValidationWarnings(currentVisualConfig.value))

const ensureConfigCanSave = (config: CategoryVisualConfig) => {
  const warnings = getValidationWarnings(config)
  // 仅记录警告，不阻止保存
  if (warnings.length > 0) {
    console.warn('分类视觉配置警告：', warnings.join('；'))
  }
}

const serializeVisualConfig = (config: CategoryVisualConfig): Partial<CategoryVisualConfig> => {
  const base = {
    categoryId: config.categoryId,
    categoryCode: config.categoryCode,
    presetId: config.presetId,
    presetVersion: config.presetVersion,
    customized: Boolean(config.customized),
    icon: config.icon,
    iconType: config.iconType
  }

  if (!config.customized && config.presetId) {
    return base
  }

  return {
    ...base,
    primaryColor: config.primaryColor,
    secondaryColor: config.secondaryColor,
    textColor: config.textColor,
    borderColor: config.borderColor,
    surfaceColor: config.surfaceColor,
    chartLineColor: config.chartLineColor,
    chartAreaColor: config.chartAreaColor,
    glowColor: config.glowColor
  }
}

const saveVisualConfig = async (): Promise<void> => {
  if (!selectedCategory.value) return
  saving.value = true

  try {
    const category = selectedCategory.value
    const config = visualConfigs[category.id]
    if (!config) return
    ensureConfigCanSave(config)

    const payload = {
      category: 'category_visual_config',
      dictKey: category.code,
      dictValue: category.name,
      extraValue: JSON.stringify(serializeVisualConfig(config)),
      sortOrder: category.sortOrder || 0,
      status: 'ACTIVE' as const,
      remark: '分类视觉配置'
    }

    const dictId = visualDictIds[category.id]
    if (dictId) {
      await updateDict(dictId, payload)
    } else {
      const response = await createDict(payload)
      visualDictIds[category.id] = response.data.id
    }

    await refreshDictCache()
    clearCategoryVisualCache()
    dirtyCategoryIds.delete(category.id)
    hasUnsavedChanges.value = dirtyCategoryIds.size > 0
  } catch (error) {
    console.error('Failed to save visual config:', error)
    throw error
  } finally {
    saving.value = false
  }
}

const saveAll = async (): Promise<void> => {
  if (!hasUnsavedChanges.value) return
  saving.value = true

  try {
    const dirtyCategories = categories.value.filter(category => dirtyCategoryIds.has(category.id))
    const savePromises = dirtyCategories.map(async category => {
      const config = visualConfigs[category.id]
      if (!config) return
      ensureConfigCanSave(config)

      const payload = {
        category: 'category_visual_config',
        dictKey: category.code,
        dictValue: category.name,
        extraValue: JSON.stringify(serializeVisualConfig(config)),
        sortOrder: category.sortOrder || 0,
        status: 'ACTIVE' as const,
        remark: '分类视觉配置'
      }

      const dictId = visualDictIds[category.id]
      if (dictId) {
        await updateDict(dictId, payload)
      } else {
        const response = await createDict(payload)
        visualDictIds[category.id] = response.data.id
      }
    })

    await Promise.all(savePromises)
    await refreshDictCache()
    clearCategoryVisualCache()
    dirtyCategoryIds.clear()
    hasUnsavedChanges.value = false
  } catch (error) {
    console.error('Failed to save all category configs:', error)
    throw error
  } finally {
    saving.value = false
  }
}

export function useCategoryPreviewState() {
  return {
    categories: computed(() => categories.value),
    selectedCategory: computed(() => selectedCategory.value),
    visualConfigs: computed(() => visualConfigs),
    loading: computed(() => loading.value),
    saving: computed(() => saving.value),
    isLoaded: computed(() => isLoaded.value),
    hasUnsavedChanges: computed(() => hasUnsavedChanges.value),
    activePresetGroup: computed(() => activePresetGroup.value),
    lastComboSummary: computed(() => lastComboSummary.value),
    selectedComboId: computed(() => selectedComboId.value),
    customCombo: computed(() => customCombo.value),
    selectedComboPreview,
    customComboId: CUSTOM_COMBO_ID,
    presets: CATEGORY_VISUAL_PRESETS,
    presetGroups: CATEGORY_VISUAL_PRESET_GROUPS,
    combos: CATEGORY_VISUAL_COMBOS,

    currentVisualConfig,
    currentPreset,
    currentComboPreviewItem,
    recommendedPresets,
    groupedPresets,
    activeGroupPresets,
    currentValidationWarnings,

    loadCategories,
    selectCategory,
    setActivePresetGroup,
    selectCombo,
    previewSelectedCombo,
    updateVisualConfig,
    applyPreset,
    applyCombo,
    applySelectedCombo,
    saveCustomCombo,
    resetCurrentPreset,
    saveVisualConfig,
    saveAll,
    discardChanges
  }
}
