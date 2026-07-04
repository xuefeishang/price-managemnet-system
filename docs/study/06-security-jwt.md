# 06. Spring Security 与 JWT

> 登录怎么实现？Token 是什么？怎么判断用户有没有权限？这一章讲清。

---

## 一、为什么需要"安全框架"？

一个后台系统至少要回答 4 个问题：

1. **你是谁？** —— 身份认证（Authentication）
2. **你能做什么？** —— 权限控制（Authorization）
3. **你的请求怎么防伪造？** —— CSRF 防护
4. **怎么防暴力破解？** —— 限流、验证码、密码策略

Spring Security 是 Java 生态最成熟的"一站式"安全框架，本项目用它完成以上所有事。

## 二、认证 vs 授权

| 概念 | 英文 | 回答的问题 | 本项目示例 |
|------|------|----------|-----------|
| 认证 | Authentication | 你是谁？ | 用户名密码正确，登录成功 |
| 授权 | Authorization | 你能干啥？ | EDITOR 不能删用户 |

**两件事分开**：
- 登录接口负责"认证"：校验用户名密码，返回 Token
- 其他接口先"认证"（看 Token 是否有效），再"授权"（看用户能不能访问）

## 三、本项目的认证方式：JWT

**JWT = JSON Web Token**，一种"无状态"的 Token 方案。

### 为什么不用传统的 Session？

| 方式 | 工作原理 | 问题 |
|------|---------|------|
| **Session** | 服务端存一份"用户状态"，浏览器靠 Cookie 里的 SessionId 找 | 多服务要共享 Session、跨域麻烦 |
| **JWT** | 服务端**不存**任何东西，Token 本身携带用户信息 | 一旦签发不好撤销 |

本项目用 JWT，因为：
- 前后端分离，跨域
- 后端不用存 Session，可水平扩展
- 小程序、APP 都能用

### JWT 长什么样？

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkFkbWluIiwicm9sZSI6IkFETUlOIiwiZXhwIjoxNzE5MDAwMDAwfQ.abc123signature
```

**三段式**：`header.payload.signature`，用 `.` 分隔。

- **header**：算法 + 类型，如 `{"alg":"HS256","typ":"JWT"}`
- **payload**：业务数据，如 `{"sub":"admin","role":"ADMIN","exp":1719000000}`
- **signature**：用密钥对前两段签名，防篡改

### 登录流程

```
┌──────────┐                              ┌──────────┐
│ 浏览器    │                              │ 后端     │
└────┬─────┘                              └────┬─────┘
     │  POST /api/auth/login                  │
     │  {username, password, captcha}         │
     │ ─────────────────────────────────────> │
     │                                        │ 1. 校验验证码
     │                                        │ 2. 查用户
     │                                        │ 3. 校验密码 (BCrypt)
     │                                        │ 4. 生成 JWT
     │  200 OK                                │
     │  {token: "eyJhbGc..."}                 │
     │ <───────────────────────────────────── │
     │                                        │
     │  GET /api/products                     │
     │  Authorization: Bearer eyJhbGc...      │
     │ ─────────────────────────────────────> │
     │                                        │ 1. 解析 Token
     │                                        │ 2. 验证签名
     │                                        │ 3. 加载用户
     │                                        │ 4. 检查权限
     │  200 OK                                │
     │  [{id:1, name:"铜精粉"}, ...]           │
     │ <───────────────────────────────────── │
```

## 四、Spring Security 过滤器链

Spring Security 是一堆**过滤器**（Filter）组成的链条，每个请求都会经过：

```
请求 → Filter1 → Filter2 → Filter3 → ... → Controller
响应 ← Filter1 ← Filter2 ← Filter3 ← ... ← Controller
```

本项目主要用到的过滤器（顺序）：

```
┌─────────────────────────────────────┐
│ 1. CORS 过滤器（跨域）              │
├─────────────────────────────────────┤
│ 2. CSRF 过滤器（前后端分离一般关掉）│
├─────────────────────────────────────┤
│ 3. JWT 认证过滤器（解析 Token）     │ ← 本项目核心
├─────────────────────────────────────┤
│ 4. UsernamePasswordAuthenticationFilter
│    （用户名密码登录）                │
├─────────────────────────────────────┤
│ 5. AuthorizationFilter              │
│    （检查权限）                      │
├─────────────────────────────────────┤
│ 6. ExceptionTranslationFilter       │
│    （捕获安全异常转 401/403）        │
└─────────────────────────────────────┘
```

**本项目 SecurityConfig**（简化版）：

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final CustomUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())              // 前后端分离不需要 CSRF
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()      // 登录接口放行
                .requestMatchers("/api/public/**").permitAll()    // 公开接口
                .anyRequest().authenticated()                    // 其他都要登录
            )
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> writeError(res, 401, "未登录"))
                .accessDeniedHandler((req, res, e) -> writeError(res, 403, "无权限"))
            )
            .addFilterBefore(jwtAuthenticationFilter,
                             UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config)
            throws Exception {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();   // BCrypt 算法
    }
}
```

## 五、JWT 过滤器核心实现

```java
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                     HttpServletResponse res,
                                     FilterChain chain) throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            chain.doFilter(req, res);    // 没 Token，放行（后面会被拦）
            return;
        }

        String token = header.substring(7);
        try {
            String username = jwtService.extractUsername(token);
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                if (jwtService.isTokenValid(token, userDetails)) {
                    UsernamePasswordAuthenticationToken auth =
                        new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities());
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        } catch (Exception e) {
            log.warn("JWT 解析失败: {}", e.getMessage());
        }

        chain.doFilter(req, res);
    }
}
```

**关键点**：

1. `OncePerRequestFilter` 保证每个请求只过一遍
2. 没 Token / Token 无效：**不报错，放行**，让后面的过滤器决定怎么办
3. Token 有效：把用户信息塞进 `SecurityContext`，后面直接拿来用

## 六、JWT 签发与校验

```java
@Service
public class JwtService {

    @Value("${security.jwt-secret}")
    private String secret;

    @Value("${security.jwt-expiration:86400000}")    // 默认 24 小时
    private long expiration;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generateToken(UserDetails user) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", user.getAuthorities().iterator().next().getAuthority());

        return Jwts.builder()
            .claims(claims)
            .subject(user.getUsername())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expiration))
            .signWith(getSigningKey())
            .compact();
    }

    public String extractUsername(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload()
            .getSubject();
    }

    public boolean isTokenValid(String token, UserDetails user) {
        String username = extractUsername(token);
        return username.equals(user.getUsername()) && !isExpired(token);
    }
}
```

**敏感配置从环境变量读**（本项目规范）：

```yaml
security:
  jwt-secret: ${JWT_SECRET}            # 必须从环境变量传入
  jwt-expiration: ${JWT_EXPIRATION:86400000}   # 默认 24h
```

生产环境**绝不能**用默认值。

## 七、权限控制

### 7.1 角色和权限

```java
public enum Role {
    ADMIN, EDITOR, VIEWER
}

// Spring Security 的 GrantedAuthority 用字符串
// "ROLE_ADMIN", "ROLE_EDITOR", "ROLE_VIEWER"
// 注意前缀 ROLE_
```

### 7.2 三种写法

```java
// 写法 1：URL 级别（SecurityConfig 里）
.requestMatchers("/api/admin/**").hasRole("ADMIN")
.requestMatchers("/api/users/**").hasAnyRole("ADMIN", "EDITOR")

// 写法 2：方法级别（用注解，更灵活）
@PreAuthorize("hasRole('ADMIN')")
public void deleteUser(Long id) { ... }

@PreAuthorize("hasAnyRole('ADMIN', 'EDITOR') and #userId == authentication.principal.id")
public void updateProfile(Long userId, ...) { ... }   // 只能改自己的

// 写法 3：编程式（最灵活）
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
boolean isAdmin = auth.getAuthorities().stream()
    .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
```

**本项目主要用方法注解** `@PreAuthorize`。

### 7.3 @PreAuthorize 表达式

| 表达式 | 含义 |
|--------|------|
| `hasRole('ADMIN')` | 有 ADMIN 角色 |
| `hasAnyRole('ADMIN', 'EDITOR')` | 任一角色 |
| `hasAuthority('product:create')` | 有某权限 |
| `isAuthenticated()` | 已登录 |
| `isAnonymous()` | 未登录 |
| `#id == authentication.principal.id` | 入参与当前用户匹配 |
| `permitAll()` | 放行 |
| `denyAll()` | 拒绝 |

## 八、密码安全

```java
// 加密
String hashed = new BCryptPasswordEncoder().encode("123456");
// $2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy

// 校验
boolean matches = new BCryptPasswordEncoder().matches("123456", hashed);  // true
```

**BCrypt 的特点**：
- 每次加密结果不同（自动加盐）
- 不可逆
- 可调成本因子（默认 10，越大越慢越安全）

**本项目的密码策略**（`SecurityProperties.passwordPolicy`）：

```yaml
security:
  password-policy:
    min-length: 8
    max-length: 32
    require-letter: true
    require-digit: true
    disallow-whitespace: true
```

## 九、其他安全措施

本项目作为一个**生产级系统**，还有很多安全细节：

| 措施 | 作用 |
|------|------|
| **验证码**（图形/滑块） | 防机器人登录、爆破 |
| **限流**（Redis + Lua） | 防止接口被刷爆 |
| **IP 黑名单** | 拦截恶意 IP |
| **CORS 白名单** | 限制跨域来源 |
| **CSRF 关闭**（前后端分离） | 避免误拦截 |
| **请求签名**（API Key） | 给第三方调用的接口 |
| **XSS 过滤** | 防止脚本注入 |
| **SQL 注入防护** | JPA 参数化查询自带 |
| **JNDI 防护** | 禁用远程查找 |
| **操作日志** | 出事后能追溯 |
| **审计字段** | 谁、什么时候、改了什么 |

详见 `docs/plan/security-hardening-2026-q2.md`。

## 十、动手试试

### 实验 1：跟踪一次登录

打开 `controller/AuthController.java`，找到 login 方法，按 F3 一路追到：

```
AuthController.login()
  → CaptchaService.verify()
  → UserDetailsService.loadUserByUsername()
  → PasswordEncoder.matches()
  → JwtService.generateToken()
  → RedisService.storeRefreshToken()
  → return Result.success(token)
```

### 实验 2：写一个需要权限的接口

```java
@PostMapping("/users")
@PreAuthorize("hasRole('ADMIN')")
public Result<UserDTO> create(@RequestBody UserDTO dto) {
    return Result.success(userService.create(dto));
}
```

用一个 VIEWER 账号调，看返回什么。

### 实验 3：手动生成一个 JWT

访问 https://jwt.io，把 username 填进去看 Payload，思考：

1. 为什么不存密码？
2. expiration 过期了怎么办？
3. 如果 secret 泄漏怎么办？

## 十一、常见错误

| 错误 | 原因 | 解决 |
|------|------|------|
| `401 Unauthorized` | 没登录 / Token 无效 | 检查 `Authorization` 头 |
| `403 Forbidden` | 登录了但没权限 | 看 `@PreAuthorize` 配置 |
| `JWT signature does not match` | secret 改了 / 多环境不一致 | 统一 secret |
| `JWT expired` | Token 过期 | 重新登录或 Refresh Token |
| `Invalid CORS` | 前端域名没在白名单 | 加到 `cors-allowed-origins` |

---

下一章：[07 Redis 缓存与性能](07-redis-cache.md) →

回头补课：[03 Spring Boot 与 IoC 核心](03-spring-boot.md)