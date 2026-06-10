package com.pricemanagement.config;

import com.pricemanagement.config.properties.SecurityProperties;
import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.entity.SysDict;
import com.pricemanagement.entity.User;
import com.pricemanagement.repository.SysDictRepository;
import com.pricemanagement.repository.ScheduledTaskRepository;
import com.pricemanagement.repository.UserRepository;
import com.pricemanagement.service.MenuItemService;
import com.pricemanagement.service.PriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MenuItemService menuItemService;
    private final SysDictRepository sysDictRepository;
    private final ScheduledTaskRepository scheduledTaskRepository;
    private final PriceService priceService;
    private final SecurityProperties securityProperties;

    @Override
    public void run(String... args) {
        initUsers();
        initDicts();
        initMenus();
        cleanupDuplicatePrices();
    }

    private void initUsers() {
        String defaultPassword = securityProperties.getDefaultUserPassword();
        if (defaultPassword == null || defaultPassword.isEmpty()) {
            log.warn("Default user password not configured, skipping user initialization. Set security.default-user-password environment variable.");
            return;
        }

        initUser("admin", defaultPassword, User.Role.ADMIN, "管理员", "admin@pricemanagement.com", "13800138000");
        initUser("editor", defaultPassword, User.Role.EDITOR, "编辑员", "editor@pricemanagement.com", "13800138001");
        initUser("viewer", defaultPassword, User.Role.VIEWER, "查看员", "viewer@pricemanagement.com", "13800138002");
    }

    private void initUser(String username, String password, User.Role role, String nickname, String email, String phone) {
        if (!userRepository.existsByUsername(username)) {
            User user = new User();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(password));
            user.setRole(role);
            user.setStatus(CommonStatus.ACTIVE);
            user.setNickname(nickname);
            user.setEmail(email);
            user.setPhone(phone);
            userRepository.save(user);
            log.info("Created default user: {}", username);
        } else if (securityProperties.isResetPasswordOnStartup()) {
            // 用户已存在时，可选重置密码确保默认凭据可用
            userRepository.findByUsername(username).ifPresent(user -> {
                user.setPassword(passwordEncoder.encode(password));
                user.setRole(role);
                user.setStatus(CommonStatus.ACTIVE);
                userRepository.save(user);
                log.info("Reset password for default user: {}", username);
            });
        }
    }

    private void initMenus() {
        try {
            log.info("Starting menu initialization...");
            menuItemService.initializeDefaultMenus();
            log.info("Menu initialization completed");
        } catch (Exception e) {
            log.error("Menu initialization failed: {}", e.getMessage(), e);
        }
    }

    private void initDicts() {
        try {
            log.info("Starting dict initialization...");
            List<SysDict> dicts = new ArrayList<>();

            // 币种 currency
            dicts.add(createDict("currency", "CNY", "人民币", "¥", 1, "中国人民币"));
            dicts.add(createDict("currency", "USD", "美元", "$", 2, "美国美元"));
            dicts.add(createDict("currency", "EUR", "欧元", "€", 3, "欧元"));

            // 通用状态 common_status
            dicts.add(createDict("common_status", "ACTIVE", "启用", "#52c41a", 1, "启用状态"));
            dicts.add(createDict("common_status", "INACTIVE", "停用", "#ff4d4f", 2, "停用状态"));

            // 用户角色 user_role
            dicts.add(createDict("user_role", "ADMIN", "管理员", "shield", 1, "系统管理员"));
            dicts.add(createDict("user_role", "EDITOR", "编辑者", "edit", 2, "内容编辑者"));
            dicts.add(createDict("user_role", "VIEWER", "查看者", "eye", 3, "只读查看者"));

            // 审批状态 approval_status
            dicts.add(createDict("approval_status", "PENDING", "待审批", "#faad14", 1, null));
            dicts.add(createDict("approval_status", "APPROVED", "已通过", "#52c41a", 2, null));
            dicts.add(createDict("approval_status", "REJECTED", "已拒绝", "#ff4d4f", 3, null));
            dicts.add(createDict("approval_status", "CANCELLED", "已撤回", "#999999", 4, null));

            // 工作流类型 workflow_type
            dicts.add(createDict("workflow_type", "PRICE_CHANGE", "价格变更", null, 1, null));
            dicts.add(createDict("workflow_type", "PRODUCT_CREATE", "产品创建", null, 2, null));
            dicts.add(createDict("workflow_type", "PRICE_PUBLISH", "价格发布审批", null, 3, "未来价格发布审批预留"));

            // 节点类型 node_type
            dicts.add(createDict("node_type", "APPROVER", "审批", null, 1, null));
            dicts.add(createDict("node_type", "NOTIFIER", "知会", null, 2, null));

            // 业务类型 business_type
            dicts.add(createDict("business_type", "PRICE", "价格", null, 1, null));
            dicts.add(createDict("business_type", "PRODUCT", "产品", null, 2, null));

            // 审批操作 approval_action
            dicts.add(createDict("approval_action", "APPROVE", "通过", null, 1, null));
            dicts.add(createDict("approval_action", "REJECT", "拒绝", null, 2, null));

            // 变更类型 change_type
            dicts.add(createDict("change_type", "CREATE", "新建", null, 1, null));
            dicts.add(createDict("change_type", "UPDATE", "更新", null, 2, null));
            dicts.add(createDict("change_type", "DELETE", "删除", null, 3, null));

            // 计量单位 unit
            dicts.add(createDict("unit", "元/吨", "元/吨", null, 1, null));
            dicts.add(createDict("unit", "万元/吨", "万元/吨", null, 2, null));
            dicts.add(createDict("unit", "元/克", "元/克", null, 3, null));
            dicts.add(createDict("unit", "元/千克", "元/千克", null, 4, null));
            dicts.add(createDict("unit", "元/吨度", "元/吨度", null, 5, null));

            // 操作类型 operation_type
            dicts.add(createDict("operation_type", "LOGIN", "登录", "login", 1, null));
            dicts.add(createDict("operation_type", "LOGOUT", "登出", "logout", 2, null));
            dicts.add(createDict("operation_type", "CREATE", "新建", "create", 3, null));
            dicts.add(createDict("operation_type", "UPDATE", "更新", "update", 4, null));
            dicts.add(createDict("operation_type", "DELETE", "删除", "delete", 5, null));
            dicts.add(createDict("operation_type", "EXPORT", "导出", "export", 6, null));
            dicts.add(createDict("operation_type", "IMPORT", "导入", "import", 7, null));
            dicts.add(createDict("operation_type", "VIEW", "查看", "view", 8, null));
            dicts.add(createDict("operation_type", "QUERY", "查询", "query", 9, null));
            dicts.add(createDict("operation_type", "OTHER", "其他", "other", 10, null));

            // 操作模块 operation_module
            dicts.add(createDict("operation_module", "USER", "用户管理", "user", 1, null));
            dicts.add(createDict("operation_module", "PRODUCT", "产品管理", "product", 2, null));
            dicts.add(createDict("operation_module", "PRICE", "价格管理", "price", 3, null));
            dicts.add(createDict("operation_module", "DICT", "字典管理", "dict", 4, null));
            dicts.add(createDict("operation_module", "MENU", "菜单管理", "menu", 5, null));
            dicts.add(createDict("operation_module", "SYSTEM", "系统管理", "system", 6, null));
            dicts.add(createDict("operation_module", "价格维护", "价格维护", "price", 7, "价格草稿保存与发布"));
            dicts.add(createDict("operation_module", "定时任务", "定时任务", "schedule", 8, "通用定时任务配置"));
            dicts.add(createDict("operation_module", "通知中心", "通知中心", "notification", 9, "通知消息阅读与投递"));

            // 菜单图标 menu_icon
            dicts.add(createDict("menu_icon", "home", "首页", "home", 1, null));
            dicts.add(createDict("menu_icon", "product", "产品", "box", 2, null));
            dicts.add(createDict("menu_icon", "price", "价格", "dollar-sign", 3, null));
            dicts.add(createDict("menu_icon", "import", "导入", "upload", 4, null));
            dicts.add(createDict("menu_icon", "user", "用户", "users", 5, null));
            dicts.add(createDict("menu_icon", "dict", "字典", "book", 6, null));
            dicts.add(createDict("menu_icon", "menu", "菜单", "menu", 7, null));
            dicts.add(createDict("menu_icon", "log", "日志", "file-text", 8, null));

            // 同步状态 sync_status
            dicts.add(createDict("sync_status", "SUCCESS", "成功", "#52c41a", 1, null));
            dicts.add(createDict("sync_status", "PARTIAL_SUCCESS", "部分成功", "#faad14", 2, null));
            dicts.add(createDict("sync_status", "FAILED", "失败", "#ff4d4f", 3, null));
            dicts.add(createDict("sync_status", "PROCESSING", "处理中", "#1890ff", 4, null));

            // 产地 origin（dictKey=产地编码, dictValue=产地名称）
            dicts.add(createDict("origin", "BJ", "北京", null, 1, "北京产地"));
            dicts.add(createDict("origin", "SH", "上海", null, 2, "上海产地"));
            dicts.add(createDict("origin", "GZ", "广州", null, 3, "广州产地"));
            dicts.add(createDict("origin", "SZ", "深圳", null, 4, "深圳产地"));
            dicts.add(createDict("origin", "TJ", "天津", null, 5, "天津产地"));
            dicts.add(createDict("origin", "WH", "武汉", null, 6, "武汉产地"));

            // 客户 customer（dictKey=客户编码, dictValue=客户名称, extraValue=联系信息JSON）
            dicts.add(createDict("customer", "CUST001", "华东钢铁集团", "{\"contact\":\"张经理\",\"phone\":\"021-55550001\"}", 1, "核心客户"));
            dicts.add(createDict("customer", "CUST002", "南方矿业公司", "{\"contact\":\"李总\",\"phone\":\"020-55550002\"}", 2, "长期合作"));
            dicts.add(createDict("customer", "CUST003", "北方金属制品厂", "{\"contact\":\"王工\",\"phone\":\"010-55550003\"}", 3, null));
            dicts.add(createDict("customer", "CUST004", "西部资源开发有限公司", "{\"contact\":\"赵经理\",\"phone\":\"028-55550004\"}", 4, null));

            // 样式主题 theme（dictKey=主题标识, dictValue=主题名称, extraValue=主题配置JSON）
            dicts.add(createDict("theme", "theme_red_green", "红涨绿跌", "{\"priceRise\":\"#EF4444\",\"priceFall\":\"#10B981\",\"priceFlat\":\"#9CA3AF\",\"chartPrimary\":\"#0D6E6E\",\"chartBudget\":\"#F59E0B\",\"chartColors\":\"#0D6E6E,#10B981,#F59E0B,#EF4444,#8B5CF6,#EC4899,#6366F1,#14B8A6,#64748B\"}", 1, "传统配色，涨价显示红色，跌价显示绿色"));
            dicts.add(createDict("theme", "theme_green_red", "绿涨红跌", "{\"priceRise\":\"#10B981\",\"priceFall\":\"#EF4444\",\"priceFlat\":\"#9CA3AF\",\"chartPrimary\":\"#0D6E6E\",\"chartBudget\":\"#F59E0B\",\"chartColors\":\"#0D6E6E,#10B981,#F59E0B,#EF4444,#8B5CF6,#EC4899,#6366F1,#14B8A6,#64748B\"}", 2, "美股风格配色，涨价显示绿色，跌价显示红色"));
            dicts.add(createDict("theme", "theme_blue_orange", "蓝涨橙跌", "{\"priceRise\":\"#3B82F6\",\"priceFall\":\"#F97316\",\"priceFlat\":\"#9CA3AF\",\"chartPrimary\":\"#0D6E6E\",\"chartBudget\":\"#F59E0B\",\"chartColors\":\"#0D6E6E,#3B82F6,#F97316,#8B5CF6,#EC4899,#6366F1,#14B8A6,#64748B,#10B981\"}", 3, "商务风格配色"));
            dicts.add(createDict("theme", "theme_purple_gold", "紫涨金跌", "{\"priceRise\":\"#8B5CF6\",\"priceFall\":\"#EAB308\",\"priceFlat\":\"#9CA3AF\",\"chartPrimary\":\"#8B5CF6\",\"chartBudget\":\"#F59E0B\",\"chartColors\":\"#8B5CF6,#EAB308,#0D6E6E,#EC4899,#6366F1,#14B8A6,#64748B,#10B981,#F59E0B\"}", 4, "高贵风格配色"));

            // 样式配置 style（dictKey=配置项, dictValue=配置项名称, extraValue=配置值）
            dicts.add(createDict("style", "price_rise_color", "涨价颜色", "#EF4444", 1, null));
            dicts.add(createDict("style", "price_fall_color", "跌价颜色", "#10B981", 2, null));
            dicts.add(createDict("style", "price_flat_color", "平价颜色", "#9CA3AF", 3, null));
            dicts.add(createDict("style", "chart_primary_color", "图表主色", "#0D6E6E", 4, null));
            dicts.add(createDict("style", "chart_budget_color", "预算线颜色", "#F59E0B", 5, null));
            dicts.add(createDict("style", "chart_colors", "图表配色", "#0D6E6E,#10B981,#F59E0B,#EF4444,#8B5CF6,#EC4899,#6366F1,#14B8A6,#64748B", 6, null));
            dicts.add(createDict("style", "heading_font", "标题字体", "Newsreader", 7, null));
            dicts.add(createDict("style", "body_font", "正文字体", "Inter", 8, null));
            dicts.add(createDict("style", "number_font", "数字字体", "JetBrains Mono", 9, null));
            dicts.add(createDict("style", "logo_url", "Logo地址", "/api/static/logo.png", 10, null));
            dicts.add(createDict("style", "active_theme", "当前主题", "theme_red_green", 11, null));

            // 首页布局配置 home_layout
            dicts.add(createDict("home_layout", "card_columns", "产品卡片列数", "4", 1, "首页重点产品卡片每行显示数量"));
            dicts.add(createDict("home_layout", "featured_product_count", "重点产品数量", "4", 2, "首页重点产品区最多显示4个产品"));
            dicts.add(createDict("home_layout", "product_list_mode", "产品列表模式", "table", 3, "首页产品列表展示模式：table/cards/auto"));
            dicts.add(createDict("home_layout", "product_table_page_size", "产品表每页条数", "10", 4, "首页产品表默认分页大小"));

            // 首页小组件配置 home_widget
            dicts.add(createDict("home_widget", "summary_stats", "经营摘要", "{\"enabled\":true,\"order\":1}", 1, "顶部摘要统计区"));
            dicts.add(createDict("home_widget", "core_metrics", "核心指标", "{\"enabled\":true,\"order\":2,\"maxCards\":8}", 2, "核心指标卡片区"));
            dicts.add(createDict("home_widget", "trend_chart", "重点走势", "{\"enabled\":true,\"order\":3,\"defaultDays\":30}", 3, "重点关注指标关联的小折线图区"));
            dicts.add(createDict("home_widget", "product_list", "产品列表", "{\"enabled\":true,\"order\":4}", 4, "产品行情列表区"));
            dicts.add(createDict("home_widget", "risk_alerts", "风险预警", "{\"enabled\":true,\"order\":5}", 5, "风险预警提示区"));

            // 价格预警规则 price_alert
            dicts.add(createDict("price_alert", "single_day_rise", "单日涨幅>5%", "{\"type\":\"percentage\",\"threshold\":5,\"direction\":\"up\",\"severity\":\"warning\"}", 1, "单日涨幅超过5%预警"));
            dicts.add(createDict("price_alert", "single_day_fall", "单日跌幅>5%", "{\"type\":\"percentage\",\"threshold\":5,\"direction\":\"down\",\"severity\":\"warning\"}", 2, "单日跌幅超过5%预警"));
            dicts.add(createDict("price_alert", "consecutive_rise", "连续上涨3日", "{\"type\":\"consecutive\",\"days\":3,\"direction\":\"up\",\"severity\":\"info\"}", 3, "连续3日上涨预警"));
            dicts.add(createDict("price_alert", "consecutive_fall", "连续下跌3日", "{\"type\":\"consecutive\",\"days\":3,\"direction\":\"down\",\"severity\":\"info\"}", 4, "连续3日下跌预警"));
            dicts.add(createDict("price_alert", "price_high", "价格高于预算10%", "{\"type\":\"budget_diff\",\"threshold\":10,\"severity\":\"warning\"}", 5, "价格高于预算预警"));
            dicts.add(createDict("price_alert", "price_low", "价格低于预算10%", "{\"type\":\"budget_diff\",\"threshold\":-10,\"severity\":\"warning\"}", 6, "价格低于预算预警"));

            // 图表时间范围 chart_range
            dicts.add(createDict("chart_range", "7d", "7日", "7", 1, "7天趋势"));
            dicts.add(createDict("chart_range", "30d", "30日", "30", 2, "30天趋势"));
            dicts.add(createDict("chart_range", "90d", "90日", "90", 3, "90天趋势"));
            dicts.add(createDict("chart_range", "1y", "年度", "365", 4, "年度趋势"));

            // 个人中心偏好与安全记录
            dicts.add(createDict("profile_table_density", "COMPACT", "紧凑", null, 1, "个人表格密度"));
            dicts.add(createDict("profile_table_density", "DEFAULT", "默认", null, 2, "个人表格密度"));
            dicts.add(createDict("profile_table_density", "COMFORTABLE", "宽松", null, 3, "个人表格密度"));
            dicts.add(createDict("profile_theme_mode", "SYSTEM", "跟随系统", null, 1, "个人主题模式"));
            dicts.add(createDict("profile_theme_mode", "LIGHT", "浅色", null, 2, "个人主题模式"));
            dicts.add(createDict("profile_theme_mode", "DARK", "深色", null, 3, "个人主题模式"));
            dicts.add(createDict("login_result", "SUCCESS", "成功", "#52c41a", 1, "登录结果"));
            dicts.add(createDict("login_result", "FAILED", "失败", "#ff4d4f", 2, "登录结果"));

            // 价格发布与通知
            dicts.add(createDict("price_draft_status", "DRAFT", "草稿", "#64748B", 1, "价格草稿状态"));
            dicts.add(createDict("price_draft_status", "PENDING_APPROVAL", "待审批", "#F59E0B", 2, "未来审批预留状态"));
            dicts.add(createDict("price_draft_status", "APPROVED", "已通过", "#10B981", 3, "未来审批预留状态"));
            dicts.add(createDict("price_draft_status", "REJECTED", "已拒绝", "#EF4444", 4, "未来审批预留状态"));
            dicts.add(createDict("price_draft_status", "PUBLISHING", "发布中", "#3B82F6", 5, "价格草稿状态"));
            dicts.add(createDict("price_draft_status", "PUBLISHED", "已发布", "#10B981", 6, "价格草稿状态"));
            dicts.add(createDict("price_draft_status", "CANCELLED", "已取消", "#9CA3AF", 7, "价格草稿状态"));
            dicts.add(createDict("price_publish_type", "MANUAL", "手动发布", null, 1, "发布类型"));
            dicts.add(createDict("price_publish_type", "SCHEDULED", "定时发布", null, 2, "发布类型"));
            dicts.add(createDict("price_publish_status", "SUCCESS", "成功", "#10B981", 1, "发布结果"));
            dicts.add(createDict("price_publish_status", "FAILED", "失败", "#EF4444", 2, "发布结果"));
            dicts.add(createDict("price_publish_status", "PARTIAL", "部分成功", "#F59E0B", 3, "发布结果"));
            dicts.add(createDict("notification_type", "PRICE_PUBLISHED", "价格已发布", null, 1, "通知类型"));
            dicts.add(createDict("notification_type", "APPROVAL_PENDING", "审批待处理", null, 2, "通知类型"));
            dicts.add(createDict("notification_type", "APPROVAL_FINISHED", "审批完成", null, 3, "通知类型"));
            dicts.add(createDict("notification_type", "TASK_FAILED", "任务失败", null, 4, "通知类型"));
            dicts.add(createDict("notification_type", "API_LIMIT_WARNING", "API告警", null, 5, "通知类型"));
            dicts.add(createDict("notification_type", "IMPORT_EXPORT_FINISHED", "导入导出完成", null, 6, "通知类型"));
            dicts.add(createDict("notification_type", "SYSTEM_NOTICE", "系统公告", null, 7, "通知类型"));
            dicts.add(createDict("notification_channel", "IN_APP", "站内通知", null, 1, "通知渠道"));
            dicts.add(createDict("notification_channel", "APP_PUSH", "App推送", null, 2, "通知渠道"));
            dicts.add(createDict("notification_channel", "MINI_PROGRAM", "小程序订阅消息", null, 3, "通知渠道"));
            dicts.add(createDict("notification_channel", "WEBHOOK", "Webhook", null, 4, "通知渠道"));
            dicts.add(createDict("notification_channel", "WECHAT_WORK", "企业微信", null, 5, "通知渠道"));
            dicts.add(createDict("notification_mini_program_page", "pages/notifications/index", "消息通知", null, 1, "小程序通知跳转页"));
            dicts.add(createDict("notification_mini_program_page", "pages/home/index", "首页", null, 2, "小程序通知跳转页"));
            dicts.add(createDict("notification_mini_program_page", "pages/history/index", "历史价格", null, 3, "小程序通知跳转页"));
            dicts.add(createDict("notification_mini_program_page", "pages/products/list", "产品列表", null, 4, "小程序通知跳转页"));
            dicts.add(createDict("notification_mini_program_page", "pages/profile/index", "个人中心", null, 5, "小程序通知跳转页"));
            dicts.add(createDict("notification_read_status", "UNREAD", "未读", "#F59E0B", 1, "阅读状态"));
            dicts.add(createDict("notification_read_status", "READ", "已读", "#10B981", 2, "阅读状态"));
            dicts.add(createDict("notification_priority", "LOW", "低", "#64748B", 1, "通知优先级"));
            dicts.add(createDict("notification_priority", "NORMAL", "普通", "#0D6E6E", 2, "通知优先级"));
            dicts.add(createDict("notification_priority", "HIGH", "高", "#F59E0B", 3, "通知优先级"));
            dicts.add(createDict("notification_priority", "URGENT", "紧急", "#EF4444", 4, "通知优先级"));
            dicts.add(createDict("notification_link_type", "PRICE_QUERY", "价格查询", null, 1, "通知跳转类型"));
            dicts.add(createDict("notification_link_type", "APPROVAL_DETAIL", "审批详情", null, 2, "通知跳转类型"));
            dicts.add(createDict("notification_link_type", "TASK_LOG", "任务日志", null, 3, "通知跳转类型"));
            dicts.add(createDict("notification_link_type", "SYSTEM_NOTICE", "系统通知", null, 4, "通知跳转类型"));
            dicts.add(createDict("notification_business_type", "PRICE", "价格", null, 1, "通知业务类型"));
            dicts.add(createDict("notification_business_type", "APPROVAL", "审批", null, 2, "通知业务类型"));
            dicts.add(createDict("notification_business_type", "TASK", "任务", null, 3, "通知业务类型"));
            dicts.add(createDict("notification_business_type", "SYSTEM", "系统", null, 4, "通知业务类型"));
            dicts.add(createDict("notification_business_type", "SECURITY", "安全", null, 5, "通知业务类型"));
            dicts.add(createDict("notification_delivery_status", "PENDING", "待投递", "#64748B", 1, "投递状态"));
            dicts.add(createDict("notification_delivery_status", "SUCCESS", "成功", "#10B981", 2, "投递状态"));
            dicts.add(createDict("notification_delivery_status", "FAILED", "失败", "#EF4444", 3, "投递状态"));
            dicts.add(createDict("notification_delivery_status", "SKIPPED", "已跳过", "#9CA3AF", 4, "投递状态"));
            dicts.add(createDict("notification_outbox_status", "PENDING", "待处理", "#64748B", 1, "Outbox状态"));
            dicts.add(createDict("notification_outbox_status", "PROCESSING", "处理中", "#3B82F6", 2, "Outbox状态"));
            dicts.add(createDict("notification_outbox_status", "SUCCESS", "成功", "#10B981", 3, "Outbox状态"));
            dicts.add(createDict("notification_outbox_status", "FAILED", "失败", "#EF4444", 4, "Outbox状态"));
            dicts.add(createDict("notification_provider_health_status", "OK", "正常", "#10B981", 1, "Provider健康状态"));
            dicts.add(createDict("notification_provider_health_status", "DEGRADED", "降级", "#F59E0B", 2, "Provider健康状态"));
            dicts.add(createDict("notification_provider_health_status", "DOWN", "异常", "#EF4444", 3, "Provider健康状态"));
            dicts.add(createDict("notification_provider_health_status", "NOT_CONFIGURED", "未配置", "#9CA3AF", 4, "Provider健康状态"));
            dicts.add(createDict("notification_mini_subscription_status", "UNKNOWN", "未知", "#9CA3AF", 1, "小程序订阅授权状态"));
            dicts.add(createDict("notification_mini_subscription_status", "ACCEPT", "已授权", "#10B981", 2, "小程序订阅授权状态"));
            dicts.add(createDict("notification_mini_subscription_status", "REJECT", "已拒绝", "#EF4444", 3, "小程序订阅授权状态"));
            dicts.add(createDict("notification_mini_subscription_status", "BAN", "已禁用", "#64748B", 4, "小程序订阅授权状态"));
            dicts.add(createDict("notification_frequency_rule", "TASK_FAILED", "任务失败聚合频控", "{\"enabled\":true,\"windowMinutes\":30,\"maxCount\":5}", 1, "任务失败消息在时间窗内超过阈值后聚合"));
            dicts.add(createDict("notification_frequency_rule", "API_LIMIT_WARNING", "API告警聚合频控", "{\"enabled\":true,\"windowMinutes\":30,\"maxCount\":5}", 2, "API告警消息在时间窗内超过阈值后聚合"));
            dicts.add(createDict("notification_frequency_rule", "IMPORT_EXPORT_FINISHED", "导入导出完成聚合频控", "{\"enabled\":true,\"windowMinutes\":60,\"maxCount\":10}", 3, "导入导出完成消息在时间窗内超过阈值后聚合"));
            dicts.add(createDict("system_notice_status", "DRAFT", "草稿", "#64748B", 1, "系统公告状态"));
            dicts.add(createDict("system_notice_status", "SCHEDULED", "待发布", "#3B82F6", 2, "系统公告状态"));
            dicts.add(createDict("system_notice_status", "PUBLISHED", "已发布", "#10B981", 3, "系统公告状态"));
            dicts.add(createDict("system_notice_status", "CANCELLED", "已撤回", "#9CA3AF", 4, "系统公告状态"));
            dicts.add(createDict("system_notice_status", "EXPIRED", "已过期", "#F59E0B", 5, "系统公告状态"));
            dicts.add(createDict("scheduled_task_type", "PRICE_PUBLISH", "价格自动发布", null, 1, "定时任务类型"));
            dicts.add(createDict("scheduled_task_type", "NOTIFICATION_RETRY", "通知重试", null, 2, "定时任务类型"));
            dicts.add(createDict("scheduled_task_type", "DATA_CLEANUP", "数据清理", null, 3, "定时任务类型"));
            dicts.add(createDict("scheduled_task_trigger_type", "SCHEDULED", "自动触发", null, 1, "任务触发方式"));
            dicts.add(createDict("scheduled_task_trigger_type", "MANUAL_TEST", "手动测试", null, 2, "任务触发方式"));
            dicts.add(createDict("scheduled_task_trigger_type", "MANUAL_RUN", "手动执行", null, 3, "任务触发方式"));
            dicts.add(createDict("scheduled_task_run_status", "RUNNING", "执行中", "#3B82F6", 1, "任务执行状态"));
            dicts.add(createDict("scheduled_task_run_status", "SUCCESS", "成功", "#10B981", 2, "任务执行状态"));
            dicts.add(createDict("scheduled_task_run_status", "FAILED", "失败", "#EF4444", 3, "任务执行状态"));
            dicts.add(createDict("scheduled_task_run_status", "SKIPPED", "已跳过", "#9CA3AF", 4, "任务执行状态"));

            // 保存到数据库（跳过已存在的）
            int created = 0;
            for (SysDict dict : dicts) {
                if (!sysDictRepository.existsByCategoryAndDictKey(dict.getCategory(), dict.getDictKey())) {
                    sysDictRepository.save(dict);
                    created++;
                }
            }
            log.info("Dict initialization completed, created {} new items (total {} defined)", created, dicts.size());
            initScheduledTasks();
        } catch (Exception e) {
            log.error("Dict initialization failed: {}", e.getMessage(), e);
        }
    }

    private void initScheduledTasks() {
        scheduledTaskRepository.findByTaskCode("PRICE_AUTO_PUBLISH").orElseGet(() -> {
            com.pricemanagement.entity.ScheduledTask task = new com.pricemanagement.entity.ScheduledTask();
            task.setTaskCode("PRICE_AUTO_PUBLISH");
            task.setTaskName("价格自动发布");
            task.setTaskType("PRICE_PUBLISH");
            task.setCronExpression("0 0 9 * * ?");
            task.setTimezone("Asia/Shanghai");
            task.setEnabled(false);
            task.setConfigJson("{\"dateOffsetDays\":-1,\"publishOnlyCompleteDraft\":false,\"notifyChannels\":[\"IN_APP\"],\"recipientRoles\":[\"ADMIN\",\"EDITOR\",\"VIEWER\"],\"systemUserId\":0,\"skipIfNoDraft\":true}");
            task.setRemark("默认停用，需管理员确认后启用");
            log.info("Created default scheduled task: PRICE_AUTO_PUBLISH");
            return scheduledTaskRepository.save(task);
        });
    }

    private SysDict createDict(String category, String dictKey, String dictValue, String extraValue, int sortOrder, String remark) {
        SysDict dict = new SysDict();
        dict.setCategory(category);
        dict.setDictKey(dictKey);
        dict.setDictValue(dictValue);
        dict.setExtraValue(extraValue);
        dict.setSortOrder(sortOrder);
        dict.setStatus(CommonStatus.ACTIVE);
        dict.setRemark(remark);
        return dict;
    }

    private void cleanupDuplicatePrices() {
        try {
            log.info("Starting duplicate price cleanup...");
            int deleted = priceService.cleanupDuplicatePrices();
            log.info("Duplicate price cleanup completed, removed {} records", deleted);
        } catch (Exception e) {
            log.error("Duplicate price cleanup failed: {}", e.getMessage(), e);
        }
    }
}
