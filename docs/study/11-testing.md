# 11. 单元测试：让代码不会"改一处坏一片"

> 不写测试 = 改完代码心里没底。本项目用 JUnit 5 + Mockito + AssertJ + H2 内存数据库，覆盖率 60%+。

---

## 一、为什么写测试？

**真实场景**：

```
产品经理：这里有个 Bug，帮我修
开发 A：  我改了 ProductService 的查询
开发 B：  我改了 Repository 的方法
开发 C：  我把 DTO 加了个字段
        ─── 一周后 ───
测试：    "产品价格不对"
运维：    "用户说下单失败"
开发：    "刚才还能用啊！"
        ↓
        没测试，谁改的都不知道
```

**有测试的世界**：

```
测试通过 = 这段代码能用
测试失败 = 这次改动破坏了某功能
CI 流水线 = 每次 push 自动跑所有测试
```

**测试金字塔**：

```
        /\
       /  \        E2E 测试（少）
      / UI \       模拟用户操作整个系统
     /______\
    /        \     集成测试（中）
   / 集成测试  \    测试多个模块协作
  /____________\
 /              \   单元测试（多）
/   单元测试     \  测试单个方法/类
/__________________\
```

**本项目重点：单元测试 + 集成测试**。

## 二、本项目测试技术栈

| 工具 | 版本 | 作用 |
|------|------|------|
| **JUnit 5** | (随 Boot) | 测试框架 |
| **Mockito** | (随 Boot) | 模拟（Mock）对象 |
| **AssertJ** | 3.27.7 | 流式断言 |
| **H2** | (test scope) | 内存数据库，跑测试用 |
| **@SpringBootTest** | (随 Boot) | 启动整个 Spring 上下文 |
| **@DataJpaTest** | (随 Boot) | 只测 JPA 层 |

## 三、第一个单元测试

### 3.1 经典三段式

```java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SimpleTest {

    @Test
    void testAddition() {
        // Given（准备）
        int a = 2, b = 3;

        // When（执行）
        int result = a + b;

        // Then（断言）
        assertEquals(5, result);
    }
}
```

### 3.2 测试命名约定

```java
// 三种常见命名法：

// 1. 方法名_期望结果
@Test
void add_TwoPositiveNumbers_ReturnsSum() { ... }

// 2. should_期望行为_when_条件
@Test
void should_ReturnSum_When_AddingTwoNumbers() { ... }

// 3. given_条件_when_动作_then_结果（BDD 风格）
@Test
void given_TwoNumbers_When_Added_Then_ReturnsSum() { ... }
```

## 四、AssertJ 流式断言（强烈推荐）

AssertJ 比 JUnit 自带的断言**好写、好读、好排查**。

```java
import static org.assertj.core.api.Assertions.*;

@Test
void testProduct() {
    Product p = new Product("铜精粉", new BigDecimal("5800"));

    // 基础断言
    assertThat(p.getName()).isEqualTo("铜精粉");
    assertThat(p.getPrice()).isGreaterThan(new BigDecimal("5000"));
    assertThat(p.getPrice()).isBetween(new BigDecimal("1000"), new BigDecimal("10000"));

    // 字符串断言
    assertThat(p.getName())
        .startsWith("铜")
        .endsWith("精粉")
        .hasSize(3);

    // 集合断言
    assertThat(products)
        .hasSize(5)
        .extracting(Product::getName)
        .contains("铜精粉", "铅精粉")
        .doesNotContain("废品");

    // 异常断言
    assertThatThrownBy(() -> productService.getById(999L))
        .isInstanceOf(ResourceNotFoundException.class)
        .hasMessageContaining("999");

    // 对象断言
    assertThat(product)
        .usingRecursiveComparison()
        .isEqualTo(expectedProduct);
}
```

**AssertJ 优势**：

```java
// JUnit 风格：失败信息不直观
assertEquals(5, list.size());

// AssertJ 风格：失败时打印实际值
assertThat(list).hasSize(5);
// 失败输出：Expected size: 5 but was: 3 in: [a, b, c]
```

## 五、Mockito：模拟依赖

### 5.1 为什么要 Mock？

测试 `ProductService.getById(id)`，但 Service 依赖 Repository、Cache、SecurityContext……

```
ProductService.getById(id)
  ├─ ProductRepository.findById(id)    ← 真去查数据库？
  ├─ RedisCache.get("product:" + id)   ← 真去连 Redis？
  └─ SecurityContext.getCurrentUser()  ← 真有 Spring 上下文？
```

**单元测试只想测业务逻辑**，不想启动整个 Spring。这时候用 **Mock 模拟**这些依赖。

### 5.2 基本用法

```java
import static org.mockito.Mockito.*;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;   // 模拟对象

    @InjectMocks
    private ProductService productService;          // 被测对象，自动注入 mock

    @Test
    void testGetById_Found() {
        // Given：模拟 Repository 返回
        Product p = new Product();
        p.setId(1L);
        p.setName("铜精粉");
        when(productRepository.findById(1L)).thenReturn(Optional.of(p));

        // When
        ProductDTO dto = productService.getById(1L);

        // Then
        assertThat(dto).isNotNull();
        assertThat(dto.getName()).isEqualTo("铜精粉");
        verify(productRepository).findById(1L);   // 验证被调用
    }

    @Test
    void testGetById_NotFound() {
        when(productRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getById(999L))
            .isInstanceOf(ResourceNotFoundException.class)
            .hasMessageContaining("999");

        verify(productRepository).findById(999L);
    }
}
```

### 5.3 常用 Mockito 方法

| 方法 | 作用 |
|------|------|
| `when(x).thenReturn(y)` | 调用 x 返回 y |
| `when(x).thenThrow(e)` | 调用 x 抛异常 |
| `verify(x).method()` | 验证 x.method() 被调用过 |
| `verify(x, times(n)).method()` | 验证被调用 n 次 |
| `verify(x, never()).method()` | 验证从未被调用 |
| `any()` / `anyLong()` | 任意参数 |
| `eq(value)` | 等于某值 |
| `mock(Class.class)` | 创建 mock 对象 |
| `@Mock` | 注解方式创建 mock |
| `@InjectMocks` | 自动注入 mock 到被测对象 |
| `@Spy` | 部分 mock（真实对象，部分方法可替换） |

### 5.4 模拟静态方法（Mockito 5+）

```java
// 模拟 UUID.randomUUID()
try (MockedStatic<UUID> mocked = mockStatic(UUID.class)) {
    mocked.when(UUID::randomUUID).thenReturn(...);
    // 测试代码
}
```

## 六、测试 Service 层（本项目核心）

```java
@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private RedisCacheService redisCacheService;

    @InjectMocks private ProductService productService;

    @Test
    void list_ShouldReturnActiveProducts() {
        // Given
        Product p1 = new Product(); p1.setId(1L); p1.setName("铜精粉");
        Product p2 = new Product(); p2.setId(2L); p2.setName("铅精粉");
        when(productRepository.findByStatus(ProductStatus.ACTIVE))
            .thenReturn(List.of(p1, p2));

        // When
        List<ProductDTO> result = productService.listActive();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(ProductDTO::getName)
            .containsExactly("铜精粉", "铅精粉");
        verify(redisCacheService).set(eq("products:active"), any(), any());
    }

    @Test
    void getById_WhenNotFound_ShouldThrow() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getById(99L))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void create_WhenNameEmpty_ShouldThrow() {
        ProductDTO dto = new ProductDTO();
        dto.setName("");

        assertThatThrownBy(() -> productService.create(dto))
            .isInstanceOf(BusinessException.class)
            .hasMessageContaining("产品名称");
        verify(productRepository, never()).save(any());
    }
}
```

## 七、测试 Controller 层

用 `@WebMvcTest` 只加载 Controller 层，模拟 Service。

```java
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ProductService productService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void list_ShouldReturnPage() throws Exception {
        ProductDTO dto = new ProductDTO();
        dto.setId(1L);
        dto.setName("铜精粉");

        Page<ProductDTO> page = new PageImpl<>(List.of(dto));
        when(productService.list(1, 20)).thenReturn(page);

        mockMvc.perform(get("/api/products?page=1&size=20"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200))
            .andExpect(jsonPath("$.data.content[0].name").value("铜精粉"))
            .andDo(print());
    }

    @Test
    void create_WithInvalidInput_ShouldReturn400() throws Exception {
        ProductDTO dto = new ProductDTO();
        // 故意不传 name

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(dto)))
            .andExpect(status().isOk())  // 本项目返回 200 + code=400
            .andExpect(jsonPath("$.code").value(400))
            .andExpect(jsonPath("$.message").value(containsString("name")));
    }
}
```

## 八、测试 Repository 层（用 H2 内存数据库）

```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = Replace.ANY)   // 用 H2 替换 MySQL
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void findByName_ShouldReturnProduct() {
        // Given
        Product p = new Product();
        p.setName("铜精粉");
        p.setStatus(ProductStatus.ACTIVE);
        entityManager.persist(p);
        entityManager.flush();

        // When
        Optional<Product> result = productRepository.findByName("铜精粉");

        // Then
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("铜精粉");
    }
}
```

## 九、测试工具类

```java
class BigDecimalUtilTest {

    @ParameterizedTest
    @CsvSource({
        "100, 0.1, 90",
        "100, 0.2, 80",
        "200, 0.5, 100"
    })
    void applyDiscount_ShouldCalculateCorrectly(
            String price, String rate, String expected) {
        BigDecimal result = BigDecimalUtil.applyDiscount(
            new BigDecimal(price), new BigDecimal(rate));
        assertThat(result).isEqualByComparingTo(new BigDecimal(expected));
    }
}
```

**`@ParameterizedTest`**：参数化测试，同一段代码用多组数据跑。

## 十、集成测试（启动整个 Spring）

```java
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProductIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        productRepository.deleteAll();
        // 准备测试数据
    }

    @Test
    void fullFlow_CreateAndQuery() throws Exception {
        // 创建
        ProductDTO dto = new ProductDTO();
        dto.setName("测试产品");

        mockMvc.perform(post("/api/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(new ObjectMapper().writeValueAsString(dto)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(200));

        // 查询
        mockMvc.perform(get("/api/products"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content[0].name").value("测试产品"));
    }
}
```

## 十一、覆盖率

本项目配了 JaCoCo（Java Code Coverage）覆盖率工具。

查看 `pom.xml` 或 `build.gradle`：

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
</plugin>
```

跑覆盖率：

```bash
mvn clean test
# target/site/jacoco/index.html  ← 浏览器打开看
```

**目标覆盖率**：

| 层级 | 目标 |
|------|------|
| 工具类 | 90%+ |
| Service | 80%+ |
| Controller | 70%+ |
| Repository | 自动测（@DataJpaTest） |
| Config / Util | 视情况 |

**不要追求 100%**，性价比不高。

## 十二、TDD：测试驱动开发

**流程**：先写测试 → 看测试失败 → 写实现 → 看测试通过 → 重构。

```
1. 写测试（红的）
   @Test void getById_NotFound_Throws() { ... }

2. 跑测试 → 失败（因为方法还没写）

3. 写实现
   public ProductDTO getById(Long id) {
       return productRepository.findById(id)
           .map(this::toDTO)
           .orElseThrow(() -> new ResourceNotFoundException(...));
   }

4. 跑测试 → 通过（绿的）

5. 重构（保持绿色）
```

**TDD 的好处**：逼你先想清楚需求，避免写出不能测试的代码。

## 十三、动手试试

### 实验 1：写第一个测试

新建 `src/test/java/com/pricemanagement/study/CalculatorTest.java`：

```java
class Calculator {
    public int add(int a, int b) { return a + b; }
    public int divide(int a, int b) {
        if (b == 0) throw new IllegalArgumentException("除数不能为 0");
        return a / b;
    }
}

class CalculatorTest {
    private final Calculator calc = new Calculator();

    @Test
    void add_ShouldReturnSum() {
        assertThat(calc.add(2, 3)).isEqualTo(5);
    }

    @Test
    void divide_ByZero_ShouldThrow() {
        assertThatThrownBy(() -> calc.divide(10, 0))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

右键运行，看绿色通过 ✅。

### 实验 2：Mock 一个依赖

```java
@ExtendWith(MockitoExtension.class)
class MockTest {

    @Mock
    private List<String> mockedList;

    @Test
    void mockTest() {
        when(mockedList.size()).thenReturn(100);

        mockedList.add("hello");  // 不会真的 add

        assertThat(mockedList.size()).isEqualTo(100);
        verify(mockedList).add("hello");
    }
}
```

### 实验 3：参数化测试

```java
@ParameterizedTest
@ValueSource(ints = {1, 2, 3, 4, 5})
void test_isOdd(int number) {
    assertThat(number % 2).isNotEqualTo(0);
}
```

## 十四、常见错误

| 错误 | 原因 |
|------|------|
| `NullPointerException` in test | 没初始化字段，用 `@InjectMocks` 或 `@BeforeEach` |
| `MockitoException: Cannot mock final class` | Mockito 5 之前不能 mock final 类，加 `mockito-inline` |
| 测试之间互相影响 | 用了静态字段、`@BeforeEach` 没清理 |
| 测试慢 | 用了 `@SpringBootTest` 而不是 `@WebMvcTest` |
| 测试有随机性 | 用了 `new Date()` 或 `UUID`，注入 Clock 或固定值 |

## 十五、测试原则

1. **FIRST** 原则
   - **F**ast（快）
   - **I**ndependent（独立）
   - **R**epeatable（可重复）
   - **S**elf-validating（自验证）
   - **T**imely（及时）

2. **测试金字塔**：单元测试多，集成测试少，E2E 测试少

3. **每个测试只测一件事**：失败时一眼看出哪里坏了

4. **测试代码也是代码**：要可读、可维护

5. **覆盖率是参考不是目标**：核心逻辑覆盖到即可

---

下一步：[12 Docker 部署](12-docker-deploy.md) →

回头补课：
- [04 项目分层架构](04-layered-architecture.md)
- [05 JPA 与数据持久化](05-jpa-persistence.md)