# 00. 环境搭建：从零把项目跑起来

> **小白的第一步**：不是学理论，而是把项目在本地跑起来，看到"Started successfully"。

---

## 一、本项目用到的工具一览

| 工具 | 版本 | 作用 | 必需 |
|------|------|------|------|
| **JDK** | 25 | Java 运行环境，编译运行 Java 代码 | ✅ 必需 |
| **Maven** | 3.9+ | 项目构建工具，下载依赖、编译、打包 | ✅ 必需 |
| **IDEA** | Community 2024.3+ | Java 集成开发环境（写代码） | ✅ 必需 |
| **MySQL** | 8.0+ | 关系数据库 | ✅ 必需 |
| **Redis** | 7.0+ | 缓存数据库 | ✅ 必需 |
| **Node.js** | 20+ | 前端运行环境 | ⚠️ 只学后端可暂不装 |
| **Git** | 2.40+ | 版本控制 | ✅ 必需 |
| **Docker Desktop** | 最新 | 容器化（部署用） | ⚠️ 可选 |
| **Postman / Apifox** | 最新 | 接口调试 | ⚠️ 推荐 |

> **Windows / macOS / Linux 全平台适用**，本教程以 **Windows 11** 为例，其他系统命令大同小异。

## 二、安装 JDK 25

### 2.1 为什么是 JDK 25？

本项目用 **Spring Boot 4.0.6**，要求 **Java 17+**，我们用最新的 LTS **Java 25**。

### 2.2 下载

官方地址：https://www.oracle.com/java/technologies/downloads/

或国内镜像（推荐，速度快）：
- 阿里云：https://mirrors.aliyun.com/AdoptOpenJDK/
- 华为云：https://repo.huaweicloud.com/java/jdk/

**下载**：选择 `Windows x64 Installer`（约 200MB）。

### 2.3 安装

```
双击 .exe → 下一步 → 下一步 → 安装完成
```

**关键**：记下安装路径，默认是 `C:\Program Files\Java\jdk-25\`。

### 2.4 配置环境变量

**Windows 设置 → 系统 → 关于 → 高级系统设置 → 环境变量**。

#### 1. 新建 JAVA_HOME

```
变量名：JAVA_HOME
变量值：C:\Program Files\Java\jdk-25   （你的安装路径）
```

#### 2. 编辑 Path，添加两条

```
%JAVA_HOME%\bin
%JAVA_HOME%\lib
```

### 2.5 验证

打开新的命令行窗口（PowerShell 或 CMD），输入：

```bash
java -version
```

期望看到：

```
openjdk version "25" ...
Java(TM) SE Runtime Environment ...
```

再输入：

```bash
javac -version
javac 25
```

**如果提示"不是内部命令"**：说明 PATH 没生效，重启电脑或重新打开命令行。

## 三、安装 Maven

### 3.1 为什么需要 Maven？

Maven 是 Java 的"包管理器+构建工具"。Spring Boot 项目用它来：
- 自动下载所有依赖（Spring、JPA、Hibernate…）
- 编译 Java 代码
- 打包成 jar 包
- 运行单元测试

### 3.2 下载

官网：https://maven.apache.org/download.cgi

下载 `apache-maven-3.9.x-bin.zip`（约 10MB）。

### 3.3 解压

解压到 `D:\dev\apache-maven-3.9.9\`（**路径不要有中文或空格**）。

### 3.4 配置环境变量

新建系统变量：

```
变量名：MAVEN_HOME
变量值：D:\dev\apache-maven-3.9.9
```

Path 添加：

```
%MAVEN_HOME%\bin
```

### 3.5 配置阿里云镜像（重要！）

默认 Maven 从国外中央仓库下载依赖，**慢到怀疑人生**。改成国内镜像。

打开 `D:\dev\apache-maven-3.9.9\conf\settings.xml`，找到 `<mirrors>` 节点，替换为：

```xml
<mirrors>
  <mirror>
    <id>aliyun</id>
    <name>Aliyun Maven Mirror</name>
    <url>https://maven.aliyun.com/repository/public</url>
    <mirrorOf>central</mirrorOf>
  </mirror>
</mirrors>
```

### 3.6 配置本地仓库路径（可选）

在 `settings.xml` 里找到 `<localRepository>`，改成一个不依赖 C 盘的路径：

```xml
<localRepository>D:\dev\maven-repo</localRepository>
```

### 3.7 验证

```bash
mvn -version
```

期望看到：

```
Apache Maven 3.9.9 ...
Java version: 25 ...
```

## 四、安装 IDEA Community

### 4.1 下载

官网：https://www.jetbrains.com/idea/download/

**选 Community 版（免费）**——Ultimate 版收费但学生可申请。

### 4.2 安装

双击 `.exe` → 下一步到完成。建议勾选：
- ✅ Add "Open Folder as Project"
- ✅ Add launcher action
- ✅ .java 文件关联

### 4.3 首次配置

1. 启动 IDEA
2. 选 "Do not import settings"
3. 选择主题（深色/浅色）
4. **Install Plugins**：
   - **Lombok**（必须）
   - **Maven Helper**（推荐）
   - **.ignore**（推荐）
   - **MyBatisX / JPA Buddy**（可选）
5. 重启 IDEA

### 4.4 配置 Maven 路径

`File → Settings → Build, Execution, Deployment → Build Tools → Maven`：

- Maven home path: `D:\dev\apache-maven-3.9.9`
- User settings file: 勾选 Override，指向 `D:\dev\apache-maven-3.9.9\conf\settings.xml`
- Local repository: 自动识别

## 五、安装 MySQL 8

### 5.1 两种方式选一种

| 方式 | 优点 | 缺点 |
|------|------|------|
| **官方 MSI 安装包** | 直观、生产一致 | 安装稍复杂，约 400MB |
| **Docker 一键** | 干净、好卸载、跨平台 | 需要先装 Docker |

**推荐小白**：Docker 方式。

### 方式 A：Docker 方式（推荐）

```bash
# 创建数据目录
mkdir -p D:/dev/mysql-data

# 启动容器
docker run -d ^
  --name mysql8 ^
  -p 3306:3306 ^
  -e MYSQL_ROOT_PASSWORD=YourStrongPassw0rd! ^
  -v D:/dev/mysql-data:/var/lib/mysql ^
  mysql:8.0
```

> **PowerShell 用 `^`，CMD 用 `^`，Git Bash 用 `\`**——选你顺手的。

### 方式 B：官方 MSI 安装包

1. 下载：https://dev.mysql.com/downloads/installer/
2. 选 `mysql-installer-community-8.0.x.msi`
3. 安装类型选 `Developer Default`
4. 设置 root 密码（**一定要记住！**）
5. 其余下一步即可

### 5.2 验证

```bash
mysql -u root -p
# 输入密码
```

看到 `mysql>` 提示符就成功了。

### 5.3 创建本项目数据库

```sql
CREATE DATABASE price_management
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

-- 创建专用用户（生产推荐，小白可跳过）
-- CREATE USER 'price_app'@'localhost' IDENTIFIED BY 'YourAppPass';
-- GRANT ALL ON price_management.* TO 'price_app'@'localhost';
```

### 5.4 验证 Flyway 能执行

数据库一旦创建好，**不用手动建表**。项目启动时 Flyway 会自动执行 `backend/src/main/resources/db/migration/` 下的 SQL。

## 六、安装 Redis

### 6.1 Docker 方式（推荐）

```bash
docker run -d ^
  --name redis7 ^
  -p 6379:6379 ^
  redis:7-alpine
```

### 6.2 Windows MSI 方式

GitHub 下载：https://github.com/microsoftarchive/redis/releases

下载 `Redis-x64-3.0.504.msi` 安装。

### 6.3 验证

```bash
redis-cli -h localhost -p 6379
> ping
PONG
```

## 七、安装 Git

### 7.1 下载

官网：https://git-scm.com/download/win

下载 64-bit Git for Windows Setup。

### 7.2 安装

全部下一步就行。建议勾选：
- ✅ Git Bash Here
- ✅ Git from the command line

### 7.3 配置

```bash
git config --global user.name "Your Name"
git config --global user.email "your@email.com"
git config --global init.defaultBranch main
```

## 八、拉取并导入项目

### 8.1 拉代码

```bash
cd D:/projects
git clone https://github.com/your-org/price-management-system.git
cd price-management-system
```

### 8.2 用 IDEA 打开

- `File → Open → 选择 price-management-system 文件夹`
- 等待 IDEA 索引和 Maven 同步（首次可能 5-10 分钟）

**右下角进度条**会显示：
- `Loading Maven changes` → Maven 下载依赖（首次较慢）
- `Indexing` → IDEA 解析代码

### 8.3 安装 IDEA 的 Lombok 插件

`File → Settings → Plugins → 搜索 Lombok → Install → 重启 IDEA`

## 九、配置环境变量

本项目用环境变量管理敏感配置。**绝不要把密码写死在 application.yml 里**。

### 9.1 创建 .env 文件（推荐）

在 `backend/` 下创建 `.env` 文件：

```properties
DB_HOST=localhost
DB_PORT=3306
DB_NAME=price_management
DB_USERNAME=root
DB_PASSWORD=YourStrongPassw0rd!

REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

JWT_SECRET=this-is-a-very-long-random-secret-key-at-least-32-chars-please-change-me
JWT_EXPIRATION=86400000

DEFAULT_USER_PASSWORD=Admin@123456
```

### 9.2 让 Spring Boot 读取 .env

**在 IDEA 里**：

`Run → Edit Configurations → 选择 PriceManagementApplication → Environment variables → 粘贴上面的环境变量`

或者更简单：直接用 IDE 的 `.env` 插件：
- 装插件 `EnvFile`
- `Run → Edit Configurations → EnvFile → 启用 + 选 backend/.env`

### 9.3 检查 application.yml

打开 `backend/src/main/resources/application.yml`，确认：

```yaml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:${DB_PORT:3306}/${DB_NAME:price_management}
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD}
```

`${DB_HOST:localhost}` 含义：**如果环境变量 `DB_HOST` 存在就用它，否则用 `localhost`**。

## 十、第一次启动

### 10.1 启动 MySQL 和 Redis

```bash
docker start mysql8 redis7
# 或者用 Docker Desktop 点 "Start"
```

### 10.2 启动后端

IDEA 找到 `PriceManagementApplication.java`，**右键 → Run**。

### 10.3 看启动日志

正常的话，最后几行是：

```
Started PriceManagementApplication in 8.234 seconds
Tomcat started on port 8080
Price Management System Backend Started Successfully!
```

**恭喜！你已经把项目跑起来了。**

### 10.4 验证接口

打开浏览器，访问：

```
http://localhost:8080/actuator/health
```

期望看到：

```json
{"status":"UP"}
```

或者调用登录接口：

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"Admin@123456"}'
```

期望看到 JWT Token 返回。

## 十一、可能遇到的问题

### Q1：端口 8080 被占用

```
Web server failed to start. Port 8080 was already in use.
```

**解决**：
1. 找到占用进程：`netstat -ano | findstr :8080`
2. 杀掉：`taskkill /PID <pid> /F`
3. 或者改端口：`application.yml` 里 `server.port: 8081`

### Q2：连不上 MySQL

```
Communications link failure
```

**检查**：
1. MySQL 是否启动？`docker ps` 或服务里看
2. 端口对吗？默认 3306
3. 用户名密码对吗？
4. 防火墙是否拦截？

### Q3：连不上 Redis

```
Unable to connect to Redis
```

**检查**：
1. Redis 是否启动？
2. 本项目**有意降级 Redis**，连不上也能启动，看 [07 Redis 缓存](07-redis-cache.md)

### Q4：Maven 下载依赖卡死

```
Resolving dependencies...
```

**解决**：
1. 检查 `settings.xml` 镜像是否生效
2. 删掉 `~/.m2/repository` 里没下完的包（带 `.lastUpdated` 后缀）
3. 重启 IDEA → `Maven → Reload`

### Q5：Lombok 报错

```
getXxx() 方法找不到
```

**解决**：
1. 安装 Lombok 插件
2. `Settings → Build → Compiler → Annotation Processors → 勾选 Enable`
3. 重启 IDEA

### Q6：JDK 版本不对

```
error: invalid target release: 25
```

**解决**：
- `File → Project Structure → Project SDK → 选 25`
- `File → Settings → Build → Compiler → Java Compiler → Project bytecode version → 25`

## 十二、可选：安装 Docker Desktop

后台开发迟早要用 Docker。下载地址：
https://www.docker.com/products/docker-desktop/

安装后启动它，右下角图标变绿就行。

**镜像加速**（重要，国内访问 Docker Hub 慢）：

`Settings → Docker Engine`，加上：

```json
{
  "registry-mirrors": [
    "https://docker.mirrors.ustc.edu.cn",
    "https://hub-mirror.c.163.com"
  ]
}
```

## 十三、推荐工具补充

| 工具 | 用途 | 下载 |
|------|------|------|
| **Apifox**（推荐） | 接口调试、文档、Mock | https://apifox.com |
| **Postman** | 老牌接口工具 | https://postman.com |
| **DBeaver** | 跨数据库客户端 | https://dbeaver.io |
| **Redis Desktop Manager** | 看 Redis 数据 | https://github.com/RedisInsight/RedisInsight |
| **Arthas** | 阿里开源 Java 诊断 | https://arthas.aliyun.com |

## 十四、动手试试

### 实验 1：Hello World

新建 `D:\test\hello.java`：

```java
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello, Price Management!");
    }
}
```

编译运行：

```bash
cd D:\test
javac Hello.java
java Hello
```

期望输出：`Hello, Price Management!`

### 实验 2：Maven Hello World

新建 `D:\test\mvn-demo\pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>
    <groupId>com.demo</groupId>
    <artifactId>hello</artifactId>
    <version>1.0.0</version>

    <properties>
        <maven.compiler.source>25</maven.compiler.source>
        <maven.compiler.target>25</maven.compiler.target>
    </properties>
</project>
```

新建 `src/main/java/Hello.java`：

```java
public class Hello {
    public static void main(String[] args) {
        System.out.println("Hello from Maven!");
    }
}
```

```bash
cd D:\test\mvn-demo
mvn compile
mvn exec:java -Dexec.mainClass="Hello"
```

### 实验 3：启动本项目 + 看日志

按上面的步骤启动 `PriceManagementApplication`，观察启动日志，记录：
- 应用启动用了多少秒？
- Tomcat 监听了什么端口？
- Flyway 执行了几个 SQL 脚本？
- 最后一行输出是什么？

---

## 十五、常见疑问

**Q：JDK 用 Oracle 还是 Adoptium（Eclipse Temurin）？**
A：都行。Adoptium 是开源的、免费的，下载更快；Oracle JDK 商用要付费（个人学习免费）。小白用哪个都行。

**Q：MySQL 一定要本地吗？**
A：不一定。本项目 application.yml 默认连 `${DB_HOST:localhost}`，可以指向任何机器。但**学习阶段建议本地**，避免网络问题。

**Q：Redis 一定要装吗？**
A：本项目支持 Redis 降级，**不装也能启动**。但装上能学到缓存。

**Q：为什么不用 VS Code？**
A：VSCode + Java 扩展也能写，但**IDEA 对 Spring Boot 的支持强太多**（自动补全、重构、Debug 体验都更好）。免费版 Community 完全够用。

**Q：内存要多大？**
A：开发机建议 **16GB RAM + SSD**。IDEA + MySQL + Redis 同时跑至少要 4GB 给 IDEA。

---

下一步：[00b MySQL 基础](00b-mysql-basics.md) →

回头补课：[01 宏观架构概览](01-architecture-overview.md)