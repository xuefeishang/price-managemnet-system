UPDATE sys_style_preset
SET preset_description = CASE preset_key
    WHEN 'compact' THEN '高密度但保持可读'
    WHEN 'standard' THEN '通用场景'
    WHEN 'large' THEN '阅读友好'
    WHEN 'xlarge' THEN '演示/投影/无障碍'
    ELSE preset_description
  END,
  config_json = CASE preset_key
    WHEN 'compact' THEN '{"xs":"0.75rem","sm":"0.8125rem","base":"0.9375rem","lg":"1rem","xl":"1.125rem","2xl":"1.375rem","3xl":"1.75rem"}'
    WHEN 'standard' THEN '{"xs":"0.8125rem","sm":"0.875rem","base":"1rem","lg":"1.125rem","xl":"1.25rem","2xl":"1.5rem","3xl":"2rem"}'
    WHEN 'large' THEN '{"xs":"0.875rem","sm":"1rem","base":"1.125rem","lg":"1.25rem","xl":"1.5rem","2xl":"1.875rem","3xl":"2.375rem"}'
    WHEN 'xlarge' THEN '{"xs":"1rem","sm":"1.125rem","base":"1.25rem","lg":"1.5rem","xl":"1.75rem","2xl":"2.25rem","3xl":"2.75rem"}'
    ELSE config_json
  END,
  updated_time = NOW()
WHERE preset_type = 'font_preset'
  AND preset_key IN ('compact', 'standard', 'large', 'xlarge');

INSERT INTO sys_style_config (config_key, config_value, config_type, description, created_time, updated_time)
SELECT 'font_size_preset', 'standard', 'string', '字号预设', NOW(), NOW()
WHERE NOT EXISTS (
  SELECT 1 FROM sys_style_config WHERE config_key = 'font_size_preset'
);

UPDATE sys_style_config target
JOIN sys_style_config preset ON preset.config_key = 'font_size_preset'
SET target.config_value = CASE preset.config_value
    WHEN 'compact' THEN CASE target.config_key
      WHEN 'font_size_xs' THEN '0.75rem'
      WHEN 'font_size_sm' THEN '0.8125rem'
      WHEN 'font_size_base' THEN '0.9375rem'
      WHEN 'font_size_lg' THEN '1rem'
      WHEN 'font_size_xl' THEN '1.125rem'
      WHEN 'font_size_2xl' THEN '1.375rem'
      WHEN 'font_size_3xl' THEN '1.75rem'
      ELSE target.config_value
    END
    WHEN 'standard' THEN CASE target.config_key
      WHEN 'font_size_xs' THEN '0.8125rem'
      WHEN 'font_size_sm' THEN '0.875rem'
      WHEN 'font_size_base' THEN '1rem'
      WHEN 'font_size_lg' THEN '1.125rem'
      WHEN 'font_size_xl' THEN '1.25rem'
      WHEN 'font_size_2xl' THEN '1.5rem'
      WHEN 'font_size_3xl' THEN '2rem'
      ELSE target.config_value
    END
    WHEN 'large' THEN CASE target.config_key
      WHEN 'font_size_xs' THEN '0.875rem'
      WHEN 'font_size_sm' THEN '1rem'
      WHEN 'font_size_base' THEN '1.125rem'
      WHEN 'font_size_lg' THEN '1.25rem'
      WHEN 'font_size_xl' THEN '1.5rem'
      WHEN 'font_size_2xl' THEN '1.875rem'
      WHEN 'font_size_3xl' THEN '2.375rem'
      ELSE target.config_value
    END
    WHEN 'xlarge' THEN CASE target.config_key
      WHEN 'font_size_xs' THEN '1rem'
      WHEN 'font_size_sm' THEN '1.125rem'
      WHEN 'font_size_base' THEN '1.25rem'
      WHEN 'font_size_lg' THEN '1.5rem'
      WHEN 'font_size_xl' THEN '1.75rem'
      WHEN 'font_size_2xl' THEN '2.25rem'
      WHEN 'font_size_3xl' THEN '2.75rem'
      ELSE target.config_value
    END
    ELSE target.config_value
  END,
  target.updated_time = NOW()
WHERE target.config_key IN (
  'font_size_xs',
  'font_size_sm',
  'font_size_base',
  'font_size_lg',
  'font_size_xl',
  'font_size_2xl',
  'font_size_3xl'
)
  AND preset.config_value IN ('compact', 'standard', 'large', 'xlarge');
