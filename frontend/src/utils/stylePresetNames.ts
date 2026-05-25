import type { StylePreset } from '@/types/theme'

export const buildStylePresetNameMap = (...presetGroups: StylePreset[][]): Record<string, string> => {
  return presetGroups.flat().reduce<Record<string, string>>((names, preset) => {
    names[preset.key] = preset.name
    return names
  }, {})
}

export const resolveStylePresetName = (
  presets: StylePreset[],
  key: string | null | undefined,
  fallbackLabel: string
): string => {
  if (!key) {
    return presets.find(preset => preset.isDefault)?.name || fallbackLabel
  }
  return presets.find(preset => preset.key === key)?.name || key
}
