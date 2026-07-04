# 00c. Java 语法入门：从 Hello World 到看懂项目代码

> **完全没学过 Java 的同学必看**。学完这一章，再去看本项目的 Controller / Service 不再害怕。

---

## 一、Java 是什么？为什么要学？

**Java 是后端开发的主流语言**——银行、运营商、政企系统、电商后端 90% 都在用。

| 语言 | 主要用途 |
|------|---------|
| Java | 后台、Android、大数据 |
| Python | AI、数据分析、脚本 |
| JavaScript | 前端、Node.js 后端 |
| Go | 云原生、微服务 |
| C# | Windows 桌面、游戏 |

Java 的特点：
- **啰嗦但严谨**：每个变量都要声明类型，编译器帮你抓错
- **跨平台**：一次编译，到处运行（Windows、Linux、macOS）
- **生态完善**：Spring 全家桶，企业级开发首选

## 二、第一个 Java 程序

### 2.1 Hello World

新建文件 `Hello.java`：

```java
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello, World!");
    }
}
```

**逐行解释**：

| 代码 | 含义 |
|------|------|
| `public class Hello` | 定义一个**类**，名字叫 Hello |
| `public static void main(String[] args)` | **程序入口**，JVM 从这里开始执行 |
| `System.out.println("...")` | 打印一行到控制台 |
| `{ }` | **代码块**的边界 |
| `;` | 每条语句以分号结尾 |

### 2.2 编译和运行

```bash
javac Hello.java    # 编译，生成 Hello.class
java Hello          # 运行
```

输出：

```
Hello, World!
```

**编译** = 把 `.java` 源码翻译成 `.class` 字节码。
**运行** = JVM 加载字节码，解释执行。

### 2.3 包和 import

```java
package com.pricemanagement.study;   // 本文件所在的"目录"

import java.util.List;               // 引用 JDK 自带的 List
import java.util.ArrayList;

import com.pricemanagement.entity.Product;   // 引用项目里的 Product 类
```

**包名 = 目录路径**。比如 `com.pricemanagement.study` 表示文件在 `com/pricemanagement/study/` 目录下。

**import** = "我要用别的包的类，先声明一下"。

## 三、变量与基本类型

### 3.1 基本类型（8 种）

```java
// 整数
byte  b = 127;                  // -128 ~ 127
short s = 30000;                // -32768 ~ 32767
int   i = 2000000000;           // 最常用，约 ±21 亿
long  l = 9000000000000000000L; // 更大，结尾加 L

// 小数
float  f = 3.14f;               // 结尾加 f
double d = 3.1415926;           // 最常用

// 字符和布尔
char c = 'A';                   // 单引号，一个字符
boolean flag = true;            // 只有 true / false
```

### 3.2 引用类型（"高级类型"）

```java
String name = "铜精粉";         // 字符串，双引号
String empty = "";              // 空字符串
String nothing = null;          // null 表示"没有对象"
```

**基本类型 vs 引用类型**：

```
基本类型：值直接存在变量里，变量就是"小盒子"
引用类型：变量是"指针"，指向真正的对象
```

### 3.3 变量的命名规范

```java
// ✅ 正确
int age = 18;
String userName = "admin";
BigDecimal totalPrice = new BigDecimal("100.00");

// ❌ 错误（编译报错）
int 1age = 18;            // 不能以数字开头
String class = "A";       // 不能用关键字
int 我的年龄 = 18;          // 不推荐用中文（虽然合法）
```

**驼峰命名**：第一个单词首字母小写，后续单词首字母大写（`userName`、`productPrice`）。
**类名大驼峰**：每个单词首字母都大写（`ProductController`、`UserService`）。

### 3.4 final 常量

```java
final double PI = 3.14159;       // 常量，不能改
final String DEFAULT_NAME = "未知"; 

// 本项目常见
private final ProductRepository productRepository;   // final 字段 = 不可变引用
```

## 四、运算符

### 4.1 算术运算符

```java
int a = 10, b = 3;

System.out.println(a + b);   // 13
System.out.println(a - b);   // 7
System.out.println(a * b);   // 30
System.out.println(a / b);   // 3（整数除法取整）
System.out.println(a % b);   // 1（取余）

// 自增自减
int x = 5;
x++;      // x = 6
x--;      // x = 5
```

### 4.2 比较运算符

```java
int a = 10, b = 20;

a == b    // 等于（基本类型比值）
a != b    // 不等于
a > b     // 大于
a < b     // 小于
a >= b    // 大于等于
a <= b    // 小于等于
```

⚠️ **字符串比较要用 `.equals()`**：

```java
String s1 = "hello";
String s2 = "hello";

// ❌ 错的（比的是内存地址）
if (s1 == s2) { ... }

// ✅ 对的（比的是内容）
if (s1.equals(s2)) { ... }
```

### 4.3 逻辑运算符

```java
boolean a = true, b = false;

a && b    // 与（AND），两个都 true 才 true
a || b    // 或（OR），一个 true 就 true
!a        // 非（NOT），取反
```

### 4.4 三元运算符

```java
int age = 20;
String type = age >= 18 ? "成人" : "未成年";
// 如果 age >= 18，type = "成人"，否则 type = "未成年"
```

## 五、控制流：让程序"会判断、能循环"

### 5.1 if / else

```java
int score = 85;

if (score >= 90) {
    System.out.println("优秀");
} else if (score >= 80) {
    System.out.println("良好");
} else if (score >= 60) {
    System.out.println("及格");
} else {
    System.out.println("不及格");
}
```

### 5.2 switch

```java
String status = "ACTIVE";

switch (status) {
    case "ACTIVE":
        System.out.println("启用");
        break;                          // 别忘了 break！
    case "DISABLED":
        System.out.println("停用");
        break;
    default:
        System.out.println("未知");
}
```

**Java 14+ 新写法（更简洁）**：

```java
String label = switch (status) {
    case "ACTIVE" -> "启用";
    case "DISABLED" -> "停用";
    default -> "未知";
};
```

### 5.3 for 循环

```java
// 传统写法
for (int i = 0; i < 5; i++) {
    System.out.println(i);  // 0, 1, 2, 3, 4
}

// 增强 for（遍历数组或集合）
int[] arr = {1, 2, 3, 4, 5};
for (int num : arr) {
    System.out.println(num);
}
```

### 5.4 while / do-while

```java
int i = 0;
while (i < 5) {
    System.out.println(i);
    i++;
}

int j = 0;
do {
    System.out.println(j);
    j++;
} while (j < 5);    // 至少执行一次
```

### 5.5 break 和 continue

```java
// break：跳出整个循环
for (int i = 0; i < 10; i++) {
    if (i == 5) break;
    System.out.println(i);  // 0~4
}

// continue：跳过本次，进入下一次
for (int i = 0; i < 10; i++) {
    if (i % 2 == 0) continue;   // 跳过偶数
    System.out.println(i);       // 1, 3, 5, 7, 9
}
```

## 六、数组和集合

### 6.1 数组

```java
// 声明 + 初始化
int[] scores = {90, 85, 78, 92};
String[] names = new String[3];     // 长度固定

// 访问
System.out.println(scores[0]);      // 90
scores[0] = 95;

// 遍历
for (int s : scores) {
    System.out.println(s);
}
```

⚠️ **数组长度固定**，改不了大小。一般用 `List` 更灵活。

### 6.2 List（列表）

```java
import java.util.List;
import java.util.ArrayList;

List<String> names = new ArrayList<>();
names.add("张三");
names.add("李四");
names.add("王五");

System.out.println(names.size());   // 3
System.out.println(names.get(0));   // "张三"
names.remove(0);
names.contains("李四");              // true

// 遍历
for (String name : names) {
    System.out.println(name);
}
```

**重点**：`<String>` 是"泛型"，表示这个 List 只能装 String。本项目代码里 `List<ProductDTO>` 就是"装 ProductDTO 的列表"。

### 6.3 Map（键值对）

```java
import java.util.Map;
import java.util.HashMap;

Map<String, Integer> ages = new HashMap<>();
ages.put("张三", 18);
ages.put("李四", 20);

System.out.println(ages.get("张三"));   // 18
System.out.println(ages.containsKey("王五")); // false
ages.remove("张三");

// 遍历
for (Map.Entry<String, Integer> entry : ages.entrySet()) {
    System.out.println(entry.getKey() + "=" + entry.getValue());
}
```

### 6.4 Set（不重复集合）

```java
import java.util.Set;
import java.util.HashSet;

Set<String> tags = new HashSet<>();
tags.add("铜");
tags.add("铜");     // 重复，不会加进去
tags.add("铅");

System.out.println(tags.size());   // 2
```

### 6.5 本项目实际用法

```java
// 本项目代码常见
List<Product> products = productRepository.findAll();
Map<Long, String> productMap = products.stream()
    .collect(Collectors.toMap(Product::getId, Product::getName));
Set<String> categories = products.stream()
    .map(Product::getCategoryName)
    .collect(Collectors.toSet());
```

## 七、方法（函数）

```java
// 修饰符 返回类型 方法名(参数) { 方法体 }
public static int add(int a, int b) {
    return a + b;
}

// 调用
int sum = add(1, 2);  // 3
```

**修饰符含义**：

| 修饰符 | 含义 |
|--------|------|
| `public` | 公开的，任何地方都能调 |
| `private` | 私有的，只能在当前类调 |
| `protected` | 受保护的，子类或同包能调 |
| `static` | 静态的，属于类而不是对象 |
| `final` | 不可被重写 |

**本项目示例**：

```java
@Service
@RequiredArgsConstructor
public class ProductService {
    
    // 私有方法（工具方法）
    private ProductDTO toDTO(Product entity) {
        // ...
    }
    
    // 公开方法（业务方法）
    public List<ProductDTO> findAll() {
        return productRepository.findAll().stream()
            .map(this::toDTO)
            .toList();
    }
}
```

## 八、类与对象

### 8.1 什么是类？

**类 = 对象的模板/图纸**。

```java
public class Product {
    // 字段（属性）
    private Long id;
    private String name;
    private BigDecimal price;
    
    // 构造方法：new 时调用
    public Product() { }
    
    public Product(String name, BigDecimal price) {
        this.name = name;
        this.price = price;
    }
    
    // 方法（行为）
    public boolean isExpensive() {
        return price.compareTo(new BigDecimal("5000")) > 0;
    }
    
    // getter / setter
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
```

### 8.2 创建对象

```java
Product p1 = new Product();                  // 调用无参构造
Product p2 = new Product("铜精粉", new BigDecimal("5800"));  // 调用有参构造

p1.setName("铅精粉");     // 设置属性
String name = p1.getName(); // 获取属性

boolean expensive = p2.isExpensive();   // 调用方法
```

### 8.3 this 关键字

```java
public void setName(String name) {
    this.name = name;   // this.name 表示"当前对象的 name 字段"
                        // 右边的 name 是参数
}
```

### 8.4 构造方法重载

```java
public class Product {
    public Product() {}                                // 无参构造
    public Product(String name) { ... }                // 一个参数
    public Product(String name, BigDecimal price) { ... }  // 两个参数
}
```

## 九、字符串

字符串是 Java 里最常用的类型，但有点"特殊"。

```java
String s = "hello";

// 常用方法
s.length();                    // 5
s.toUpperCase();               // "HELLO"
s.toLowerCase();               // "hello"
s.substring(0, 3);             // "hel"（从 0 开始，到 3 结束，不含 3）
s.contains("el");              // true
s.startsWith("he");            // true
s.endsWith("lo");              // true
s.replace("l", "L");           // "heLLo"
s.split(",");                  // 按逗号拆成数组
s.trim();                      // 去首尾空格
s.isEmpty();                   // 是否空字符串 ""
s.isBlank();                   // 是否全空白（Java 11+）

// 拼接
String s2 = "hello, " + "world";   // 简单拼接
String s3 = String.format("Hi, %s! 你是第 %d 位", "Alice", 100);
String s4 = "Hi, " + name + "!";   // 性能差，循环里别用

// 文本块（Java 15+，本项目可能用到）
String json = """
        {
            "name": "铜精粉",
            "price": 5800
        }
        """;
```

⚠️ **`==` 和 `.equals()`**：

```java
String a = "hello";
String b = new String("hello");

a == b         // false（地址不同）
a.equals(b)    // true（内容相同）

// ⚠️ 字符串比较一定要用 .equals()
```

## 十、异常处理

**异常 = 程序运行时出错了**。

```java
// 1. try-catch 捕获异常
try {
    int result = 10 / 0;  // 除零异常
} catch (ArithmeticException e) {
    System.out.println("出错了：" + e.getMessage());
} finally {
    System.out.println("无论如何都执行");
}

// 2. 多重 catch
try {
    // 可能抛多种异常的代码
} catch (NullPointerException e) {
    // 处理空指针
} catch (IOException e) {
    // 处理 IO 异常
} catch (Exception e) {
    // 兜底处理其他异常
}

// 3. 抛出异常
public void delete(Long id) throws ResourceNotFoundException {
    if (!repository.existsById(id)) {
        throw new ResourceNotFoundException("找不到 id=" + id);
    }
    repository.deleteById(id);
}
```

**异常的层级**：

```
Throwable
  ├── Error（系统级错误，通常不处理，如 OutOfMemoryError）
  └── Exception
        ├── RuntimeException（运行时异常，不需要显式处理）
        │     ├── NullPointerException
        │     ├── IndexOutOfBoundsException
        │     ├── ArithmeticException
        │     └── IllegalArgumentException
        └── 其他（受检异常，必须处理，如 IOException、SQLException）
```

**本项目实践**：

```java
// 自定义业务异常
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}

// Service 里抛出
public ProductDTO getById(Long id) {
    return productRepository.findById(id)
        .map(this::toDTO)
        .orElseThrow(() -> new BusinessException("产品不存在: " + id));
}
```

**全局异常处理**（本项目用 `@RestControllerAdvice` 统一捕获）：

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusiness(BusinessException e) {
        return Result.fail(e.getMessage());
    }
    
    @ExceptionHandler(Exception.class)
    public Result<Void> handleAll(Exception e) {
        log.error("系统异常", e);
        return Result.fail("系统异常，请联系管理员");
    }
}
```

## 十一、动手试试

### 实验 1：Hello + 变量

新建 `D:\test\Demo.java`：

```java
public class Demo {
    public static void main(String[] args) {
        String name = "铜精粉";
        double price = 5800.00;
        int stock = 100;
        boolean active = true;

        System.out.println("产品：" + name);
        System.out.println("价格：" + price);
        System.out.println("库存：" + stock);
        System.out.println("上架：" + active);

        if (stock > 0) {
            System.out.println("有货");
        } else {
            System.out.println("缺货");
        }

        for (int i = 1; i <= 3; i++) {
            System.out.println("第 " + i + " 次打印");
        }
    }
}
```

```bash
javac Demo.java
java Demo
```

### 实验 2：写一个 Product 类

```java
public class Product {
    private Long id;
    private String name;
    private double price;

    public Product() {}

    public Product(String name, double price) {
        this.name = name;
        this.price = price;
    }

    public boolean isExpensive() {
        return price > 5000;
    }

    public void applyDiscount(double rate) {
        this.price = this.price * rate;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public double getPrice() { return price; }
    public void setPrice(double price) { this.price = price; }

    public static void main(String[] args) {
        Product p = new Product("铜精粉", 5800);
        System.out.println(p.getName() + " 贵吗？ " + p.isExpensive());

        p.applyDiscount(0.9);
        System.out.println("打折后：" + p.getPrice());
    }
}
```

### 实验 3：List + 循环

```java
import java.util.ArrayList;
import java.util.List;

public class ListDemo {
    public static void main(String[] args) {
        List<String> products = new ArrayList<>();
        products.add("铜精粉");
        products.add("铅精粉");
        products.add("锌精粉");

        for (String p : products) {
            System.out.println(p);
        }

        products.removeIf(p -> p.startsWith("铅"));   // 删掉"铅精粉"
        System.out.println("删除后剩 " + products.size() + " 个");
    }
}
```

### 实验 4：异常处理

```java
public class ExceptionDemo {
    public static void main(String[] args) {
        try {
            int[] arr = {1, 2, 3};
            System.out.println(arr[10]);    // 数组越界
        } catch (ArrayIndexOutOfBoundsException e) {
            System.out.println("数组越界了：" + e.getMessage());
        }

        String s = null;
        try {
            System.out.println(s.length());  // 空指针
        } catch (NullPointerException e) {
            System.out.println("空指针异常");
        }
    }
}
```

## 十二、本项目代码片段解读

### 12.1 看懂 ProductController

```java
@RestController                                  // (1) 这是个 REST 控制器
@RequestMapping("/api/products")                 // (2) URL 前缀
@RequiredArgsConstructor                         // (3) Lombok：生成构造方法
public class ProductController {                 // (4) 类名

    private final ProductService productService;// (5) 注入 Service

    @GetMapping                                  // (6) GET 请求
    public Result<Page<ProductDTO>> list(        // (7) 返回包装类型 Result
            @RequestParam(defaultValue = "1") int page,   // (8) 查询参数
            @RequestParam(defaultValue = "20") int size) {
        return Result.success(productService.list(page, size));  // (9) 调 Service
    }
}
```

**拆解**：

- **(1) `@RestController`**：Spring 注解，告诉框架这是 Controller
- **(2) `@RequestMapping`**：所有方法默认带 `/api/products` 前缀
- **(3) `@RequiredArgsConstructor`**：Lombok 注解，自动生成 `public ProductController(ProductService)` 构造方法
- **(4) 类名**：大驼峰命名
- **(5) `final` 字段**：通过构造方法注入，初始化后不能改
- **(6) `@GetMapping`**：处理 GET 请求
- **(7) `Result<Page<ProductDTO>>`**：泛型返回值，统一响应格式
- **(8) `@RequestParam`**：从 URL `?page=1&size=20` 取参数
- **(9) `Result.success(...)`**：把结果包装成统一响应

### 12.2 看懂 ProductService

```java
@Service                                         // (1) 这是个业务 Service
@RequiredArgsConstructor                         // (2) 生成构造方法
@Slf4j                                           // (3) Lombok：生成 log 字段
public class ProductService {                    // (4)

    private final ProductRepository productRepository;  // (5) 注入 Repository

    @Transactional(readOnly = true)              // (6) 只读事务
    public Page<ProductDTO> list(int page, int size) { // (7)
        log.info("查询产品 page={}, size={}", page, size);  // (8) 打日志
        PageRequest pageable = PageRequest.of(page - 1, size);  // (9)
        return productRepository.findAll(pageable)      // (10) 查库
                .map(this::toDTO);                      // (11) 转 DTO
    }

    private ProductDTO toDTO(Product entity) {    // (12) 转换方法
        // ...
    }
}
```

**拆解**：

- **(1) `@Service`**：Spring 注解，标记这是业务层
- **(2) `@RequiredArgsConstructor`**：构造注入
- **(3) `@Slf4j`**：自动生成 `private static final Logger log = LoggerFactory.getLogger(...)`
- **(4) 类名**：`XxxService` 是业务层的命名约定
- **(5) `final` 字段**：依赖 Repository
- **(6) `@Transactional`**：事务管理（读操作加 `readOnly = true` 性能更好）
- **(7) 方法签名**：`int page, int size` 入参，返回 `Page<ProductDTO>`
- **(8) `log.info`**：日志，`{}` 是占位符
- **(9) `PageRequest.of`**：分页请求（注意 page 从 0 开始）
- **(10) `productRepository.findAll`**：JPA 自动生成的分页查询
- **(11) `.map(this::toDTO)`**：把 Product 转为 ProductDTO
- **(12) 私有方法**：Entity → DTO 的转换逻辑

## 十三、推荐资源

| 资源 | 类型 | 适用 |
|------|------|------|
| [廖雪峰 Java 教程](https://liaoxuefeng.com) | 中文在线教程 | 零基础入门 |
| [菜鸟教程 Java](https://www.runoob.com/java/java-tutorial.html) | 中文教程 | 当手册查 |
| 《Head First Java》 | 书 | 图解入门，最轻松 |
| 《Java 核心技术》 | 书 | 系统学习 |
| [Baeldung](https://www.baeldung.com/) | 博客 | 进阶查询 |

## 十四、常见错误（IDE 里看到的红色提示）

| 错误 | 原因 | 解决 |
|------|------|------|
| `cannot find symbol` | 类名拼错、忘 import | 检查拼写、加 import |
| `';' expected` | 漏了分号 | 加 `;` |
| `unclosed string literal` | 字符串引号没闭合 | 检查 `"` |
| `incompatible types` | 类型不对（如 String 赋给 int） | 检查赋值 |
| `method does not override or implement a method from a supertype` | `@Override` 的方法签名和父类不一致 | 检查方法名、参数 |
| `non-static method cannot be referenced from a static context` | 静态方法调非静态方法 | new 一个对象再调 |
| `NullPointerException` | 空指针，对 null 调方法 | 加 null 判断 |

---

下一步：[02 Java 高级特性](02-java-advanced.md) →（继续深入：注解、泛型、Lombok、Stream API）

回头补课：[00 环境搭建](00-prepare.md)