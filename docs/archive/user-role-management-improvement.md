# 用户管理与角色管理功能改进方案

## 实施状态

| Phase | 功能 | 状态 |
|-------|------|------|
| Phase 1 | 验证码功能 | ✅ 已完成 |
| Phase 2 | 工号登录 | ✅ 已完成 |
| Phase 3 | 角色权限体系 | ✅ 已完成 |

---

## Context

当前系统用户管理功能存在以下问题：
1. 用户登录仅支持用户名密码方式，无法满足微信等第三方登录需求
2. 登录无验证码保护，存在安全风险
3. 员工使用普通用户名登录，不符合公司内部系统工号管理规范
4. 角色管理简单（仅3个固定角色），无法满足精细化权限控制需求

**需求目标：**
- 支持工号登录（6位数字）+ 用户名密码登录 + 微信登录
- 登录增加4位数字随机验证码
- 后台可新增、修改、禁用用户
- 支持精细化角色权限管理

---

## 一、数据库设计

### 1.1 用户表改进 (sys_user)

新增字段：

```sql
-- 用户表扩展字段
ALTER TABLE sys_user ADD COLUMN employee_id VARCHAR(6) UNIQUE COMMENT '工号（6位数字）' AFTER username;
ALTER TABLE sys_user ADD COLUMN login_type VARCHAR(20) DEFAULT 'PASSWORD' COMMENT '登录方式：PASSWORD密码, WECHAT微信, BOTH双方式' AFTER role;
ALTER TABLE sys_user ADD COLUMN wechat_openid VARCHAR(100) UNIQUE COMMENT '微信OpenID' AFTER phone;
ALTER TABLE sys_user ADD COLUMN wechat_unionid VARCHAR(100) COMMENT '微信UnionID' AFTER wechat_openid;
ALTER TABLE sys_user ADD COLUMN wechat_nickname VARCHAR(100) COMMENT '微信昵称' AFTER wechat_unionid;
ALTER TABLE sys_user ADD COLUMN wechat_avatar VARCHAR(500) COMMENT '微信头像URL' AFTER wechat_nickname;
ALTER TABLE sys_user ADD COLUMN last_login_time DATETIME COMMENT '最后登录时间' AFTER updated_time;
ALTER TABLE sys_user ADD COLUMN last_login_ip VARCHAR(50) COMMENT '最后登录IP' AFTER last_login_time;
ALTER TABLE sys_user ADD COLUMN login_count INT DEFAULT 0 COMMENT '登录次数' AFTER last_login_ip;
ALTER TABLE sys_user ADD COLUMN password_updated_time DATETIME COMMENT '密码更新时间' AFTER password;
ALTER TABLE sys_user ADD COLUMN is_locked BOOLEAN DEFAULT FALSE COMMENT '是否锁定' AFTER status;
ALTER TABLE sys_user ADD COLUMN locked_time DATETIME COMMENT '锁定时间' AFTER is_locked;
ALTER TABLE sys_user ADD COLUMN department VARCHAR(100) COMMENT '部门' AFTER nickname;

-- 索引
ALTER TABLE sys_user ADD INDEX idx_employee_id (employee_id);
ALTER TABLE sys_user ADD INDEX idx_wechat_openid (wechat_openid);
ALTER TABLE sys_user ADD INDEX idx_login_type (login_type);
```

### 1.2 验证码表 (sys_captcha)

```sql
-- 验证码表
CREATE TABLE IF NOT EXISTS sys_captcha (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '验证码ID',
    captcha_key VARCHAR(100) NOT NULL UNIQUE COMMENT '验证码Key（UUID）',
    captcha_code VARCHAR(4) NOT NULL COMMENT '验证码（4位数字）',
    captcha_image VARCHAR(500) COMMENT '验证码图片Base64',
    ip_address VARCHAR(50) COMMENT '请求IP',
    expire_time DATETIME NOT NULL COMMENT '过期时间',
    used BOOLEAN DEFAULT FALSE COMMENT '是否已使用',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    INDEX idx_captcha_key (captcha_key),
    INDEX idx_captcha_expire (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='验证码表';
```

### 1.3 角色表改进 (sys_role)

将角色从枚举改为独立表，支持动态角色管理：

```sql
-- 角色表（替代原有枚举角色）
CREATE TABLE IF NOT EXISTS sys_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '角色ID',
    role_code VARCHAR(50) NOT NULL UNIQUE COMMENT '角色编码',
    role_name VARCHAR(100) NOT NULL COMMENT '角色名称',
    description VARCHAR(500) COMMENT '角色描述',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE/INACTIVE',
    is_system BOOLEAN DEFAULT FALSE COMMENT '是否系统内置角色（不可删除）',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_role_code (role_code),
    INDEX idx_role_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统角色表';

-- 用户角色关联表（支持多角色）
CREATE TABLE IF NOT EXISTS sys_user_role (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '关联ID',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    FOREIGN KEY (user_id) REFERENCES sys_user(id) ON DELETE CASCADE ON UPDATE CASCADE,
    FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE ON UPDATE CASCADE,
    UNIQUE KEY uk_user_role (user_id, role_id),
    INDEX idx_user_role_user (user_id),
    INDEX idx_user_role_role (role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户角色关联表';
```

### 1.4 权限表 (sys_permission)

```sql
-- 权限表
CREATE TABLE IF NOT EXISTS sys_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '权限ID',
    permission_code VARCHAR(100) NOT NULL UNIQUE COMMENT '权限编码',
    permission_name VARCHAR(100) NOT NULL COMMENT '权限名称',
    permission_type VARCHAR(20) NOT NULL COMMENT '权限类型：MENU菜单, BUTTON按钮, API接口',
    parent_id BIGINT COMMENT '父权限ID',
    resource_url VARCHAR(200) COMMENT '资源路径',
    icon VARCHAR(50) COMMENT '图标',
    sort_order INT DEFAULT 0 COMMENT '排序',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    updated_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_permission_code (permission_code),
    INDEX idx_permission_type (permission_type),
    INDEX idx_permission_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统权限表';

-- 角色权限关联表
CREATE TABLE IF NOT EXISTS sys_role_permission (
    id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '关联ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    created_time DATETIME DEFAULT CURRENT_TIMESTAMP NOT NULL COMMENT '创建时间',
    FOREIGN KEY (role_id) REFERENCES sys_role(id) ON DELETE CASCADE ON UPDATE CASCADE,
    FOREIGN KEY (permission_id) REFERENCES sys_permission(id) ON DELETE CASCADE ON UPDATE CASCADE,
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    INDEX idx_role_permission_role (role_id),
    INDEX idx_role_permission_permission (permission_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色权限关联表';
```

### 1.5 初始化数据

```sql
-- 初始化角色数据
INSERT INTO sys_role (role_code, role_name, description, sort_order, status, is_system) VALUES
('ADMIN', '系统管理员', '拥有所有权限', 1, 'ACTIVE', TRUE),
('EDITOR', '数据编辑', '可编辑产品价格数据', 2, 'ACTIVE', TRUE),
('VIEWER', '数据查看', '仅可查看数据', 3, 'ACTIVE', TRUE),
('DEPT_MANAGER', '部门经理', '部门级管理权限', 4, 'ACTIVE', FALSE);

-- 初始化权限数据（菜单权限）
INSERT INTO sys_permission (permission_code, permission_name, permission_type, parent_id, resource_url, sort_order, status) VALUES
-- 首页
('home:view', '首页查看', 'MENU', NULL, '/home', 1, 'ACTIVE'),
-- 产品管理
('product:view', '产品列表查看', 'MENU', NULL, '/products', 10, 'ACTIVE'),
('product:create', '产品创建', 'BUTTON', NULL, NULL, 11, 'ACTIVE'),
('product:edit', '产品编辑', 'BUTTON', NULL, NULL, 12, 'ACTIVE'),
('product:delete', '产品删除', 'BUTTON', NULL, NULL, 13, 'ACTIVE'),
('price:view', '价格查看', 'MENU', NULL, '/price-maintenance', 20, 'ACTIVE'),
('price:edit', '价格编辑', 'BUTTON', NULL, NULL, 21, 'ACTIVE'),
-- 基础运维
('category:view', '分类管理查看', 'MENU', NULL, '/categories', 30, 'ACTIVE'),
('category:edit', '分类编辑', 'BUTTON', NULL, NULL, 31, 'ACTIVE'),
('origin:view', '产地管理查看', 'MENU', NULL, '/origins', 40, 'ACTIVE'),
('origin:edit', '产地编辑', 'BUTTON', NULL, NULL, 41, 'ACTIVE'),
('customer:view', '客户管理查看', 'MENU', NULL, '/customers', 50, 'ACTIVE'),
('customer:edit', '客户编辑', 'BUTTON', NULL, NULL, 51, 'ACTIVE'),
-- 系统管理（仅管理员）
('user:view', '用户管理查看', 'MENU', NULL, '/users', 100, 'ACTIVE'),
('user:create', '用户创建', 'BUTTON', NULL, NULL, 101, 'ACTIVE'),
('user:edit', '用户编辑', 'BUTTON', NULL, NULL, 102, 'ACTIVE'),
('user:delete', '用户删除', 'BUTTON', NULL, NULL, 103, 'ACTIVE'),
('role:view', '角色管理查看', 'MENU', NULL, '/roles', 110, 'ACTIVE'),
('role:edit', '角色编辑', 'BUTTON', NULL, NULL, 111, 'ACTIVE'),
('log:view', '日志查看', 'MENU', NULL, '/operation-log', 120, 'ACTIVE');

-- 初始化角色权限关联（管理员拥有所有权限）
-- EDITOR拥有产品、价格、分类、产地、客户相关权限
-- VIEWER仅拥有查看权限

-- 迁移现有用户到新角色体系
INSERT INTO sys_user_role (user_id, role_id)
SELECT u.id, r.id FROM sys_user u, sys_role r
WHERE u.role = r.role_code AND r.is_system = TRUE;
```

---

## 二、后端实现

### 2.1 验证码服务

**新增文件：**
- `entity/Captcha.java` - 验证码实体
- `repository/CaptchaRepository.java` - 验证码Repository
- `service/CaptchaService.java` - 验证码服务
- `dto/CaptchaResponse.java` - 验证码响应DTO

**核心逻辑：**
```java
// CaptchaService.java
public CaptchaResponse generateCaptcha(String ipAddress) {
    // 生成4位数字验证码
    String code = String.format("%04d", new Random().nextInt(10000));
    // 生成UUID作为key
    String key = UUID.randomUUID().toString();
    // 生成图片（可选，简单场景可仅返回数字）
    String image = generateCaptchaImage(code);
    // 设置过期时间（5分钟）
    LocalDateTime expireTime = LocalDateTime.now().plusMinutes(5);
    // 保存到数据库
    Captcha captcha = new Captcha();
    captcha.setCaptchaKey(key);
    captcha.setCaptchaCode(code);
    captcha.setCaptchaImage(image);
    captcha.setIpAddress(ipAddress);
    captcha.setExpireTime(expireTime);
    captchaRepository.save(captcha);
    // 返回
    return new CaptchaResponse(key, image);
}

public boolean validateCaptcha(String key, String code) {
    Optional<Captcha> captchaOpt = captchaRepository.findByCaptchaKey(key);
    if (captchaOpt.isEmpty()) return false;
    Captcha captcha = captchaOpt.get();
    // 检查是否过期
    if (captcha.getExpireTime().isBefore(LocalDateTime.now())) return false;
    // 检查是否已使用
    if (captcha.getUsed()) return false;
    // 检查验证码是否匹配
    if (!captcha.getCaptchaCode().equals(code)) return false;
    // 标记已使用
    captcha.setUsed(true);
    captchaRepository.save(captcha);
    return true;
}
```

### 2.2 登录接口改进

**AuthController.java 改进：**

```java
@PostMapping("/login")
public Result<?> login(@Validated @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
    // 1. 验证验证码
    if (!captchaService.validateCaptcha(request.getCaptchaKey(), request.getCaptchaCode())) {
        return Result.error(400, "验证码错误或已过期");
    }

    // 2. 根据登录类型查找用户
    User user = null;
    if (request.getLoginType() == LoginType.EMPLOYEE_ID) {
        // 工号登录（6位数字）
        if (!request.getUsername().matches("\\d{6}")) {
            return Result.error(400, "工号格式错误，应为6位数字");
        }
        user = userRepository.findByEmployeeId(request.getUsername());
    } else {
        // 用户名登录
        user = userRepository.findByUsername(request.getUsername());
    }

    // 3. 验证用户状态、密码等
    // ... 现有逻辑

    // 4. 更新登录信息
    user.setLastLoginTime(LocalDateTime.now());
    user.setLastLoginIp(httpRequest.getRemoteAddr());
    user.setLoginCount(user.getLoginCount() + 1);
    userRepository.save(user);

    // 5. 返回Token
    // ...
}

@PostMapping("/wechat-login")
public Result<?> wechatLogin(@RequestBody WechatLoginRequest request) {
    // 1. 调用微信API获取openid
    WechatUserInfo wechatInfo = wechatService.getUserInfo(request.getCode());

    // 2. 查找或创建用户
    Optional<User> userOpt = userRepository.findByWechatOpenid(wechatInfo.getOpenid());
    User user;
    if (userOpt.isEmpty()) {
        // 新用户，需要绑定工号
        return Result.error(400, "请先绑定工号");
    } else {
        user = userOpt.get();
    }

    // 3. 验证状态，返回Token
    // ...
}

@PostMapping("/bind-wechat")
public Result<?> bindWechat(@RequestBody BindWechatRequest request) {
    // 绑定微信到已有工号账户
    User user = userRepository.findByEmployeeId(request.getEmployeeId());
    if (user == null) return Result.error(404, "工号不存在");

    WechatUserInfo wechatInfo = wechatService.getUserInfo(request.getWechatCode());
    user.setWechatOpenid(wechatInfo.getOpenid());
    user.setWechatUnionid(wechatInfo.getUnionid());
    user.setWechatNickname(wechatInfo.getNickname());
    user.setWechatAvatar(wechatInfo.getHeadimgurl());
    user.setLoginType(LoginType.BOTH);
    userRepository.save(user);

    return Result.success("绑定成功");
}
```

### 2.3 用户管理服务改进

**新增接口：**
- 工号生成（自动生成6位不重复工号）
- 用户禁用/启用
- 用户锁定/解锁
- 批量导入用户（Excel）
- 用户角色分配

### 2.4 角色管理服务

**新增文件：**
- `entity/Role.java` - 角色实体
- `entity/Permission.java` - 权限实体
- `entity/UserRole.java` - 用户角色关联
- `entity/RolePermission.java` - 角色权限关联
- `repository/RoleRepository.java`
- `repository/PermissionRepository.java`
- `service/RoleService.java`
- `controller/RoleController.java`

---

## 三、前端实现

### 3.1 登录页面改进

**Login.vue 改进：**
- 新增验证码输入框和图片显示
- 新增登录方式切换（工号/用户名）
- 新增微信登录按钮（可选）

**新增API：**
- `getCaptcha()` - 获取验证码
- `wechatLogin()` - 微信登录
- `bindWechat()` - 绑定微信

### 3.2 用户管理页面改进

**UserManagement.vue 改进：**
- 新增工号字段显示和输入
- 新增部门字段
- 新增登录方式字段
- 新增禁用/锁定状态切换
- 新增角色多选（替代单选）
- 新增批量导入功能

### 3.3 角色管理页面（新增）

**RoleManagement.vue：**
- 角色列表展示
- 新增/编辑角色
- 角色权限配置（树形选择）
- 角色状态管理

### 3.4 权限控制改进

**usePermission.ts 改进：**
- 从后端动态获取用户权限列表
- 支持细粒度权限检查（菜单、按钮、API）

---

## 四、实现步骤

### Phase 1：验证码功能（优先级高）

1. 创建验证码表和实体
2. 实现验证码生成和验证服务
3. 改造登录接口，增加验证码校验
4. 前端登录页增加验证码输入

### Phase 2：工号登录

1. 扩展用户表字段（employee_id等）
2. 实现工号生成逻辑
3. 改造登录接口支持工号登录
4. 前端登录页增加登录方式切换
5. 用户管理增加工号字段

### Phase 3：角色权限体系

1. 创建角色表、权限表及关联表
2. 迁移现有用户角色数据
3. 实现角色管理服务
4. 实现权限管理服务
5. 前端角色管理页面
6. 权限控制改造

### Phase 4：微信登录（可选）

1. 扩展用户表微信字段
2. 实现微信OAuth服务
3. 登录接口增加微信登录
4. 前端微信登录按钮

---

## 五、验证方式

1. **验证码测试：**
   - 验证码生成正确（4位数字）
   - 验证码过期机制有效（5分钟）
   - 验证码一次性使用
   - 登录时验证码校验生效

2. **工号登录测试：**
   - 工号格式校验（6位数字）
   - 工号唯一性校验
   - 工号登录成功
   - 工号与用户名双方式登录

3. **角色权限测试：**
   - 角色创建/编辑/删除
   - 权限分配生效
   - 用户多角色生效
   - 权限控制生效（菜单、按钮）

4. **用户管理测试：**
   - 用户创建（含工号）
   - 用户禁用/启用
   - 用户锁定/解锁
   - 批量导入

---

## 六、关键参考文件

**后端参考：**
- `backend/src/main/java/com/pricemanagement/entity/User.java` - 用户实体扩展
- `backend/src/main/java/com/pricemanagement/controller/AuthController.java` - 登录接口改造
- `backend/src/main/java/com/pricemanagement/controller/UserController.java` - 用户管理改造
- `backend/src/main/resources/init.sql` - 数据库脚本

**前端参考：**
- `frontend/src/views/Login.vue` - 登录页改造
- `frontend/src/views/UserManagement.vue` - 用户管理改造
- `frontend/src/composables/usePermission.ts` - 权限控制改造
- `frontend/src/api/users.ts` - 用户API扩展