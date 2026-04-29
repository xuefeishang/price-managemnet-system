package com.pricemanagement.constants;

import com.pricemanagement.entity.User;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 系统级常量定义
 * 统一管理各处硬编码的魔法字符串
 */
public final class SystemConstants {

    private SystemConstants() {
        // 防止实例化
    }

    // ==================== 审批相关 ====================

    /**
     * 待审批标记（写入 remark 字段，用于标识数据处于待审批状态）
     */
    public static final String PENDING_APPROVAL = "PENDING_APPROVAL";

    /**
     * 审批动作：创建
     */
    public static final String ACTION_CREATE = "CREATE";

    /**
     * 审批动作：更新
     */
    public static final String ACTION_UPDATE = "UPDATE";

    // ==================== 安全相关 ====================

    /**
     * Spring Security 角色前缀
     */
    public static final String ROLE_PREFIX = "ROLE_";

    /**
     * HTTP Authorization 请求头
     */
    public static final String AUTH_HEADER = "Authorization";

    /**
     * Bearer Token 前缀
     */
    public static final String BEARER_PREFIX = "Bearer ";

    // ==================== 默认值 ====================

    /**
     * 默认币种
     */
    public static final String DEFAULT_CURRENCY = "CNY";

    /**
     * 默认角色（未指定角色时的回退值）
     */
    public static final String DEFAULT_ROLE = "VIEWER";

    // ==================== 公开路径（无需认证） ====================

    public static final String[] PUBLIC_PATHS = {
            "/api/auth/login",
            "/api/auth/register",
            "/api/menus/tree",
            "/api/menus/visible"
    };

    // ==================== 角色辅助方法 ====================

    /**
     * 将角色枚举列表序列化为 JSON 字符串
     * 例如：toJsonRoles(User.Role.ADMIN, User.Role.EDITOR) → "[\"ADMIN\",\"EDITOR\"]"
     */
    public static String toJsonRoles(User.Role... roles) {
        List<String> roleNames = Arrays.stream(roles)
                .map(Enum::name)
                .collect(Collectors.toList());
        // 手动构建 JSON 避免引入 ObjectMapper 依赖
        return roleNames.stream()
                .map(name -> "\"" + name + "\"")
                .collect(Collectors.joining(",", "[", "]"));
    }
}
