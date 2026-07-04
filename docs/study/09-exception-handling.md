# 09. 全局异常处理：让 500 错误也能优雅返回

> 一个不处理异常的后台 = 动不动给前端返回一坨 HTML 错误页。这一章讲怎么用 `@RestControllerAdvice` 统一处理。

---

## 一、问题：默认的错误响应很丑

如果不处理异常，Spring Boot 默认返回的是这样：

```html
<html>
  <head><title>Error</title></head>
  <body>
    <h1>Whitelabel Error Page</h1>
    <p>This application has no explicit mapping for /error...</p>
    ...
  </body>
</html>
```

前端拿到这个会懵——"这不是 JSON 啊，error.message 在哪？"

**正确做法**：所有异常都返回统一的 JSON 格式。

## 二、统一响应格式

本项目用 `Result<T>` 包装所有响应：

```json
{
  "code": 200,
  "message": "操作成功",
  "data": { "id": 1, "name": "铜精粉" },
  "timestamp": "2026-06-28 21:00:00"
}
```

错误时：

```json
{
  "code": 400,
  "message": "产品名称不能为空",
  "data": null,
  "timestamp": "2026-06-28 21:00:00"
}
```

**Result 类的典型实现**（在 `com.pricemanagement.util` 或 `dto` 包下）：

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Result<T> {
    private int code;
    private String message;
    private T data;
    private LocalDateTime timestamp;

    public static <T> Result<T> success(T data) {
        return new Result<>(200, "操作成功", data, LocalDateTime.now());
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> fail(String message) {
        return new Result<>(500, message, null, LocalDateTime.now());
    }

    public static <T> Result<T> fail(int code, String message) {
        return new Result<>(code, message, null, LocalDateTime.now());
    }
}
```

## 三、自定义业务异常

```java
// 业务异常基类
public class BusinessException extends RuntimeException {
    private final int code;

    public BusinessException(String message) {
        super(message);
        this.code = 400;
    }

    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    public int getCode() {
        return code;
    }
}

// 找不到资源
public class ResourceNotFoundException extends BusinessException {
    public ResourceNotFoundException(String message) {
        super(404, message);
    }
}

// 权限不足
public class ForbiddenException extends BusinessException {
    public ForbiddenException(String message) {
        super(403, message);
    }
}
```

**Service 里抛出**：

```java
@Service
public class ProductService {

    public ProductDTO getById(Long id) {
        return productRepository.findById(id)
            .map(this::toDTO)
            .orElseThrow(() -> new ResourceNotFoundException("产品不存在: " + id));
    }

    public ProductDTO create(ProductDTO dto) {
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException("产品名称不能为空");
        }
        // ... 保存逻辑
    }
}
```

## 四、@RestControllerAdvice 全局处理

这是本节**最核心**的内容。

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 1. 业务异常
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        return Result.fail(e.getCode(), e.getMessage());
    }

    /**
     * 2. 参数校验失败（@Valid 失败时抛）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidation(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        log.warn("参数校验失败: {}", message);
        return Result.fail(400, message);
    }

    /**
     * 3. 请求体格式错误
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<Void> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体格式错误: {}", e.getMessage());
        return Result.fail(400, "请求体格式错误");
    }

    /**
     * 4. 404 接口不存在
     */
    @ExceptionHandler(NoHandlerFoundException.class)
    public Result<Void> handleNotFound(NoHandlerFoundException e) {
        log.warn("接口不存在: {}", e.getRequestURL());
        return Result.fail(404, "接口不存在: " + e.getRequestURL());
    }

    /**
     * 5. 405 方法不允许
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public Result<Void> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("方法不允许: {}", e.getMessage());
        return Result.fail(405, "HTTP 方法不允许: " + e.getMethod());
    }

    /**
     * 6. 数据不存在（Repository 返回空）
     */
    @ExceptionHandler(EmptyResultDataAccessException.class)
    public Result<Void> handleEmpty(EmptyResultDataAccessException e) {
        log.warn("数据不存在: {}", e.getMessage());
        return Result.fail(404, "数据不存在");
    }

    /**
     * 7. 数据库唯一约束冲突
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public Result<Void> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("数据完整性冲突: {}", e.getMessage());
        return Result.fail(409, "数据冲突（唯一约束或外键）");
    }

    /**
     * 8. 兜底：所有其他异常
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleAll(Exception e) {
        log.error("系统异常", e);   // 记录完整堆栈
        return Result.fail(500, "系统异常，请联系管理员");
    }
}
```

## 五、注解详解

| 注解 | 作用 |
|------|------|
| `@RestControllerAdvice` | 全局处理 Controller 抛出的异常（= `@ControllerAdvice` + `@ResponseBody`） |
| `@ExceptionHandler(XxxException.class)` | 处理指定类型的异常 |
| `@ControllerAdvice(basePackages = "...")` | 只处理指定包的 Controller |

**方法签名**：

```java
@ExceptionHandler(BusinessException.class)
public Result<Void> handleBusiness(BusinessException e) {
    // 参数 e：抛出的异常对象
    // 返回值：变成响应体
}
```

可以返回任何类型：String、自定义对象、`ResponseEntity<T>` 等。

## 六、错误码规范

本项目建议的错误码体系：

| 范围 | 含义 | 例子 |
|------|------|------|
| 200 | 成功 | 200 |
| 400 | 客户端错误（参数错） | 400 |
| 401 | 未登录 | 401 |
| 403 | 无权限 | 403 |
| 404 | 资源不存在 | 404 |
| 405 | 方法不允许 | 405 |
| 409 | 冲突（重复） | 409 |
| 429 | 限流 | 429 |
| 500 | 服务器错误 | 500 |

**业务错误码扩展**：

```java
public class ErrorCode {
    public static final int SUCCESS = 200;
    public static final int BAD_REQUEST = 400;
    public static final int UNAUTHORIZED = 401;
    public static final int FORBIDDEN = 403;
    public static final int NOT_FOUND = 404;

    // 业务错误码从 1000 开始
    public static final int PRODUCT_NOT_FOUND = 1001;
    public static final int PRODUCT_NAME_DUPLICATE = 1002;
    public static final int PRICE_INVALID = 2001;
    public static final int APPROVAL_NEEDED = 3001;
}
```

## 七、本项目完整流程示例

**完整调用链**：

```
前端调用
  POST /api/products  {"name": "", "price": -100}
         ↓
Controller
  @PostMapping
  public Result<ProductDTO> create(@Valid @RequestBody ProductDTO dto) {
      ↑ @Valid 触发校验
        ↓
      dto.name 为空 → MethodArgumentNotValidException
        ↓
      Spring 调用 @ExceptionHandler
        ↓
GlobalExceptionHandler.handleValidation()
        ↓
      返回 Result.fail(400, "name: 产品名称不能为空")
        ↓
前端收到
  {"code": 400, "message": "name: 产品名称不能为空", ...}
```

## 八、Spring Security 异常的单独处理

Spring Security 抛的异常（如未登录、Token 过期）不进 Controller，所以 `@RestControllerAdvice` 抓不到，要单独配。

```java
@Component
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {
    @Override
    public void commence(HttpServletRequest req, HttpServletResponse res,
                         AuthenticationException e) throws IOException {
        res.setStatus(401);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("""
            {"code": 401, "message": "未登录或 Token 过期"}
            """);
    }
}

@Component
public class JwtAccessDeniedHandler implements AccessDeniedHandler {
    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res,
                       AccessDeniedException e) throws IOException {
        res.setStatus(403);
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("""
            {"code": 403, "message": "无权限访问"}
            """);
    }
}
```

注册到 SecurityConfig：

```java
http
    .exceptionHandling(ex -> ex
        .authenticationEntryPoint(jwtAuthenticationEntryPoint)
        .accessDeniedHandler(jwtAccessDeniedHandler)
    );
```

## 九、错误响应要不要暴露堆栈？

**绝对不要**。

```java
// ❌ 错误：暴露堆栈给前端
return Result.fail(500, "系统异常: " + e.getMessage() + "\n" + e.toString());

// ✅ 正确：服务端记录完整堆栈，给前端友好提示
log.error("系统异常", e);  // 服务端日志
return Result.fail(500, "系统异常，请联系管理员");  // 客户端提示
```

**为什么？**

- 暴露堆栈会泄漏代码结构，给攻击者提供线索
- 堆栈里可能有 SQL 语句、文件路径、密钥
- 前端用户不需要看堆栈

## 十、动手试试

### 实验 1：故意抛个异常

新建 `TestController.java`：

```java
@RestController
@RequestMapping("/api/test")
public class TestController {

    @GetMapping("/ok")
    public Result<String> ok() {
        return Result.success("正常");
    }

    @GetMapping("/biz-error")
    public Result<String> bizError() {
        throw new BusinessException("业务异常测试");
    }

    @GetMapping("/null-error")
    public Result<String> nullError() {
        String s = null;
        return Result.success(s.toUpperCase());  // NPE
    }

    @GetMapping("/not-found")
    public Result<String> notFound() {
        throw new ResourceNotFoundException("资源不存在");
    }
}
```

分别访问 4 个接口，看返回什么。

### 实验 2：参数校验

```java
@Data
public class UserDTO {
    @NotBlank(message = "用户名不能为空")
    private String username;

    @Email(message = "邮箱格式不正确")
    private String email;

    @Min(value = 18, message = "年龄必须 >= 18")
    private Integer age;
}

@PostMapping("/user")
public Result<UserDTO> create(@Valid @RequestBody UserDTO dto) {
    return Result.success(dto);
}
```

POST 一个 `{"age": 10}` 进去，看返回的错误格式。

### 实验 3：自定义异常

```java
@PostMapping("/user")
public Result<UserDTO> create(@RequestBody UserDTO dto) {
    if (userRepository.existsByUsername(dto.getUsername())) {
        throw new BusinessException(1001, "用户名已存在");
    }
    return Result.success(userRepository.save(dto));
}
```

## 十一、常见疑问

**Q：`@RestControllerAdvice` 和 `@ControllerAdvice` 区别？**
A：`@RestControllerAdvice` = `@ControllerAdvice` + `@ResponseBody`。返回值直接序列化为 JSON，不走视图。

**Q：异常处理的优先级？**
A：Spring 找**最匹配**的 `@ExceptionHandler`。`Exception` 是兜底，会捕获所有。

**Q：怎么处理异步任务的异常？**
A：`@Async` 方法抛的异常不进 `@RestControllerAdvice`。要单独用 `AsyncUncaughtExceptionHandler` 配置。

**Q：要不要返回完整的堆栈给前端？**
A：不要。堆栈只写日志，给前端友好提示。

**Q：异常要不要被事务回滚？**
A：`@Transactional` 默认对 `RuntimeException` 回滚，对 `Exception` 不回滚（要手动配置 `rollbackFor`）。

```java
@Transactional(rollbackFor = Exception.class)  // 所有异常都回滚
```

---

下一步：[10 日志框架](10-logging.md) →

回头补课：
- [06 Spring Security 与 JWT](06-security-jwt.md)
- [04 项目分层架构](04-layered-architecture.md)