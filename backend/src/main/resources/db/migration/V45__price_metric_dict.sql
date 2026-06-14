INSERT INTO sys_dict (
    category, dict_key, dict_value, extra_value, sort_order, status, remark, created_time, updated_time
)
VALUES
    ('price_metric_group', 'PRICE_STATUS', '价格现状', '当前价格及比较基准', 1, 'ACTIVE', '价格指标分组', NOW(), NOW()),
    ('price_metric_group', 'SHORT_TERM_BUDGET', '短期及预算偏差', '判断短期涨跌与预算偏离', 2, 'ACTIVE', '价格指标分组', NOW(), NOW()),
    ('price_metric_group', 'MONTHLY_TREND', '月度趋势', '环比与同比分析', 3, 'ACTIVE', '价格指标分组', NOW(), NOW()),
    ('price_metric', 'LATEST_PRICE', '最新价格', '{"group":"PRICE_STATUS","description":"当前系统中该产品最新一条有效价格记录对应的价格","rule":"按价格日期倒序取最新一条有效记录","note":"不是按录入时间取值"}', 1, 'ACTIVE', '价格指标', NOW(), NOW()),
    ('price_metric', 'LATEST_PRICE_DATE', '最新价格日期', '{"group":"PRICE_STATUS","description":"最新价格对应的业务日期","rule":"取最新价格记录中的价格日期","note":"用于说明当前价格具体对应哪一天"}', 2, 'ACTIVE', '价格指标', NOW(), NOW()),
    ('price_metric', 'PREVIOUS_EFFECTIVE_PRICE', '上期有效价格', '{"group":"PRICE_STATUS","description":"最新价格日期之前最近一个有效价格日对应的价格","rule":"向前查找最近一条有效价格记录","note":"自动跳过无价格记录日期"}', 3, 'ACTIVE', '价格指标', NOW(), NOW()),
    ('price_metric', 'PREVIOUS_PRICE_DATE', '上期价格日期', '{"group":"PRICE_STATUS","description":"上期有效价格对应的业务日期","rule":"取上期有效价格记录中的价格日期","note":"用于明确本次价格比较基准日期"}', 4, 'ACTIVE', '价格指标', NOW(), NOW()),
    ('price_metric', 'PREVIOUS_CHANGE_AMOUNT', '较上期差异额', '{"group":"SHORT_TERM_BUDGET","description":"最新价格与上期有效价格之间的差额","rule":"最新价格－上期有效价格","note":"大于0表示上涨，小于0表示下降"}', 5, 'ACTIVE', '价格指标', NOW(), NOW()),
    ('price_metric', 'PREVIOUS_CHANGE_PERCENT', '较上期差异率', '{"group":"SHORT_TERM_BUDGET","description":"最新价格相较上期有效价格的变动幅度","rule":"（最新价格－上期有效价格）÷上期有效价格×100%","note":"用于反映短期涨跌幅度"}', 6, 'ACTIVE', '价格指标', NOW(), NOW()),
    ('price_metric', 'BUDGET_PRICE', '预算价格', '{"group":"SHORT_TERM_BUDGET","description":"当前价格日期所属预算周期内对应产品的预算基准价格","rule":"取价格日期所属年度预算价格","note":"预算管理页是唯一维护入口"}', 7, 'ACTIVE', '价格指标', NOW(), NOW()),
    ('price_metric', 'BUDGET_CHANGE_AMOUNT', '较预算差异额', '{"group":"SHORT_TERM_BUDGET","description":"最新价格与预算价格之间的差额","rule":"最新价格－预算价格","note":"用于判断当前价格偏离预算的程度"}', 8, 'ACTIVE', '价格指标', NOW(), NOW()),
    ('price_metric', 'BUDGET_CHANGE_PERCENT', '较预算差异率', '{"group":"SHORT_TERM_BUDGET","description":"最新价格相较预算价格的偏离比例","rule":"（最新价格－预算价格）÷预算价格×100%","note":"用于衡量价格偏离预算的幅度"}', 9, 'ACTIVE', '价格指标', NOW(), NOW()),
    ('price_metric', 'CURRENT_MONTH_AVERAGE_PRICE', '本月累计平均价格', '{"group":"MONTHLY_TREND","description":"截至最新价格日期本月所有有效价格记录的平均值","rule":"本月有效价格之和÷本月有效价格记录数","note":"无价格记录日期不参与计算"}', 10, 'ACTIVE', '价格指标', NOW(), NOW()),
    ('price_metric', 'PREVIOUS_MONTH_AVERAGE_PRICE', '上月平均价格', '{"group":"MONTHLY_TREND","description":"上一个自然月内所有有效价格记录的平均值","rule":"上月有效价格之和÷上月有效价格记录数","note":"用于与本月累计平均价格进行环比分析"}', 11, 'ACTIVE', '价格指标', NOW(), NOW()),
    ('price_metric', 'MONTH_OVER_MONTH_PERCENT', '月均价环比', '{"group":"MONTHLY_TREND","description":"本月累计平均价格与上月平均价格相比的变化比例","rule":"（本月累计平均价格－上月平均价格）÷上月平均价格×100%","note":"反映月度价格变化趋势"}', 12, 'ACTIVE', '价格指标', NOW(), NOW()),
    ('price_metric', 'LAST_YEAR_SAME_PERIOD_AVERAGE_PRICE', '上年同期平均价格', '{"group":"MONTHLY_TREND","description":"上一年度同月同期的平均价格","rule":"上年同月截至对应日期的有效价格平均值","note":"按同期口径取值便于同比分析"}', 13, 'ACTIVE', '价格指标', NOW(), NOW()),
    ('price_metric', 'YEAR_OVER_YEAR_PERCENT', '月均价同比', '{"group":"MONTHLY_TREND","description":"本月累计平均价格相较上年同期平均价格的变化比例","rule":"（本月累计平均价格－上年同期平均价格）÷上年同期平均价格×100%","note":"反映价格年度变化趋势"}', 14, 'ACTIVE', '价格指标', NOW(), NOW())
ON DUPLICATE KEY UPDATE
    dict_value = VALUES(dict_value),
    extra_value = VALUES(extra_value),
    sort_order = VALUES(sort_order),
    status = VALUES(status),
    remark = VALUES(remark),
    updated_time = NOW();
