package com.pricemanagement.config;

import com.pricemanagement.constants.CommonStatus;
import com.pricemanagement.entity.SysDict;
import com.pricemanagement.entity.User;
import com.pricemanagement.repository.SysDictRepository;
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
    private final PriceService priceService;

    @Override
    public void run(String... args) {
        initUsers();
        initDicts();
        initMenus();
        cleanupDuplicatePrices();
    }

    private void initUsers() {
        initUser("admin", "admin123", User.Role.ADMIN, "管理员", "admin@pricemanagement.com", "13800138000");
        initUser("editor", "admin123", User.Role.EDITOR, "编辑员", "editor@pricemanagement.com", "13800138001");
        initUser("viewer", "admin123", User.Role.VIEWER, "查看员", "viewer@pricemanagement.com", "13800138002");
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
        } else {
            // 用户已存在时，重置密码确保默认凭据可用
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
            dicts.add(createDict("operation_type", "QUERY", "查询", "query", 8, null));
            dicts.add(createDict("operation_type", "OTHER", "其他", "other", 9, null));

            // 操作模块 operation_module
            dicts.add(createDict("operation_module", "USER", "用户管理", "user", 1, null));
            dicts.add(createDict("operation_module", "PRODUCT", "产品管理", "product", 2, null));
            dicts.add(createDict("operation_module", "PRICE", "价格管理", "price", 3, null));
            dicts.add(createDict("operation_module", "DICT", "字典管理", "dict", 4, null));
            dicts.add(createDict("operation_module", "MENU", "菜单管理", "menu", 5, null));
            dicts.add(createDict("operation_module", "SYSTEM", "系统管理", "system", 6, null));

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

            // 保存到数据库（跳过已存在的）
            int created = 0;
            for (SysDict dict : dicts) {
                if (!sysDictRepository.existsByCategoryAndDictKey(dict.getCategory(), dict.getDictKey())) {
                    sysDictRepository.save(dict);
                    created++;
                }
            }
            log.info("Dict initialization completed, created {} new items (total {} defined)", created, dicts.size());
        } catch (Exception e) {
            log.error("Dict initialization failed: {}", e.getMessage(), e);
        }
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