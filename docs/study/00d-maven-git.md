# 00d. Maven 与 Git 入门：每个后台开发都要会

> 本项目用 Maven 管理依赖、用 Git 管理代码。这一章讲清 **pom.xml 在干嘛、git 命令怎么用**。

---

## 第一部分：Maven 入门

## 一、Maven 是什么？

**Maven = Java 的"包管理器 + 构建工具"**。

类比 Node.js 生态：

| Node.js | Java |
|---------|------|
| `package.json` | `pom.xml` |
| `npm install` | `mvn install` |
| `npm run dev` | `mvn spring-boot:run` |
| `node_modules` | `~/.m2/repository` |

Maven 解决 3 件事：

1. **下载依赖**：自动从仓库下载 Spring、JPA、Hibernate 等 jar 包
2. **构建项目**：编译、测试、打包
3. **规范结构**：约定每个目录放什么代码

## 二、本项目的 pom.xml 速读

打开 `backend/pom.xml`：

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="...">
    <modelVersion>4.0.0</modelVersion>

    <!-- 1. 父项目：继承 Spring Boot 的默认配置 -->
    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>4.0.6</version>
    </parent>

    <!-- 2. 项目坐标：唯一的"身份证" -->
    <groupId>com.pricemanagement</groupId>
    <artifactId>price-management-backend</artifactId>
    <version>1.0.0</version>

    <!-- 3. 属性：版本号、编译参数 -->
    <properties>
        <java.version>25</java.version>
        <lombok.version>1.18.46</lombok.version>
    </properties>

    <!-- 4. 依赖：本项目用到的 jar 包 -->
    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <!-- ... 更多依赖 ... -->
    </dependencies>

    <!-- 5. 构建插件 -->
    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
            </plugin>
        </plugins>
    </build>
</project>
```

### 2.1 GAV 坐标

每个 jar 包都有 3 个标识：

| 字段 | 含义 | 例子 |
|------|------|------|
| `groupId` | 组织（反向域名） | `org.springframework.boot` |
| `artifactId` | 项目名 | `spring-boot-starter-web` |
| `version` | 版本号 | `4.0.6` |

三个合起来定位一个唯一的 jar 包：`org.springframework.boot:spring-boot-starter-web:4.0.6`。

### 2.2 Starter 是什么？

**Starter = 一组相关依赖的"套餐"**。

| Starter | 包含什么 |
|---------|----------|
| `spring-boot-starter-web` | Spring MVC + Jackson + Tomcat |
| `spring-boot-starter-data-jpa` | Hibernate + JPA + JDBC |
| `spring-boot-starter-security` | Spring Security |
| `spring-boot-starter-data-redis` | Lettuce + Redis 工具 |
| `spring-boot-starter-validation` | Hibernate Validator |
| `spring-boot-starter-test` | JUnit + Mockito + AssertJ |

**记忆规则**：`spring-boot-starter-{功能}`。

### 2.3 依赖范围（scope）

```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>    <!-- 只在测试时使用 -->
</dependency>
```

| scope | 含义 |
|-------|------|
| `compile`（默认） | 主代码、测试、打包都包含 |
| `runtime` | 运行时需要，编译时不需要（如 MySQL JDBC 驱动） |
| `test` | 只在测试时使用（如 H2、JUnit） |
| `provided` | 编译需要，运行由环境提供（如 Lombok、Servlet API） |

## 三、Maven 常用命令

打开命令行，进入 `backend/` 目录：

```bash
# 1. 编译（生成 target/classes）
mvn compile

# 2. 运行测试
mvn test

# 3. 打包（生成 target/*.jar）
mvn package

# 4. 清理（删掉 target 目录）
mvn clean

# 5. 清理 + 打包（常用）
mvn clean package

# 6. 跳过测试打包
mvn clean package -DskipTests

# 7. 下载所有依赖（首次）
mvn dependency:resolve

# 8. 看依赖树（排查冲突）
mvn dependency:tree

# 9. 启动应用（开发模式）
mvn spring-boot:run

# 10. 强制更新依赖
mvn clean install -U
```

## 四、Maven 生命周期

Maven 有 3 套独立的生命周期：

```
clean 生命周期：
  pre-clean → clean → post-clean
         ↓
default 生命周期（最常用）：
  validate → compile → test → package → verify → install → deploy
         ↓
site 生命周期：
  pre-site → site → post-site
```

**关键阶段**：

| 阶段 | 干啥 |
|------|------|
| `validate` | 检查项目结构 |
| `compile` | 编译主代码 |
| `test` | 运行单元测试 |
| `package` | 打包成 jar |
| `verify` | 运行集成测试 |
| `install` | 装到本地仓库（~/.m2/repository） |
| `deploy` | 部署到远程仓库 |

**记忆**：`compile → test → package → install` 是最常用的四个。

## 五、本地仓库与镜像

### 5.1 本地仓库

下载的 jar 包存在 `~/.m2/repository/`（Windows 是 `C:\Users\你的用户名\.m2\repository`）。

首次构建时，Maven 会：
1. 从配置的镜像下载 jar 到本地
2. 后续构建直接用本地缓存

### 5.2 配置阿里云镜像

打开 `D:\dev\apache-maven-3.9.9\conf\settings.xml`：

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

### 5.3 私有仓库（Nexus）

公司项目常用 Nexus 私服管理内部 jar 包，配置：

```xml
<mirror>
    <id>nexus</id>
    <url>http://nexus.internal.com/repository/maven-public/</url>
    <mirrorOf>*</mirrorOf>
</mirror>
```

## 六、依赖冲突怎么办？

### 6.1 怎么看依赖树

```bash
mvn dependency:tree > tree.txt
```

输出会像：

```
[INFO] +- org.springframework.boot:spring-boot-starter-web:jar:4.0.6
[INFO] |  +- org.springframework:spring-web:jar:7.0.5
[INFO] |  |  \- commons-io:commons-io:jar:2.18.0
[INFO] |  \- com.fasterxml.jackson.core:jackson-databind:jar:2.21.1
```

### 6.2 排除传递依赖

如果两个 jar 包版本冲突：

```xml
<dependency>
    <groupId>A</groupId>
    <artifactId>a</artifactId>
    <exclusions>
        <exclusion>
            <groupId>B</groupId>
            <artifactId>b</artifactId>
        </exclusion>
    </exclusions>
</dependency>
```

### 6.3 强制版本

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>commons-io</groupId>
            <artifactId>commons-io</artifactId>
            <version>2.18.0</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

### 6.4 用 Maven Helper 插件

IDEA 装 **Maven Helper** 插件后：

1. 右键 `pom.xml` → `Analyze`
2. 可以看到依赖冲突的红色高亮
3. 一键排除冲突依赖

## 七、动手试试

### 实验 1：跑一次构建

```bash
cd backend
mvn clean compile
```

观察输出，看：
- 编译了几个类？
- 第一次跑会下载多少依赖？

### 实验 2：看依赖树

```bash
cd backend
mvn dependency:tree | head -50
```

找出 spring-boot-starter-web 依赖了哪些 jar。

### 实验 3：手动指定版本

在 pom.xml 里给 `lombok` 加一个旧版本：

```xml
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <version>1.18.20</version>  <!-- 故意写旧 -->
</dependency>
```

观察 IDE 是否有警告。

---

# 第二部分：Git 基础

## 八、Git 是什么？

**Git = 分布式版本控制系统**——记录文件的每次修改，可以随时回退、对比、合并。

**类比**：

| 没有 Git | 有 Git |
|---------|--------|
| 写代码 → 改 → 备份 v1.zip → 改 → 备份 v2.zip ... | 写代码 → 改 → git 记录 → 随时回退 |
| 同事改完发 U 盘给你 | git pull / push |

**三个核心概念**：

```
工作区（Working Directory）   ← 你看到的文件
   ↓ git add
暂存区（Staging Area）       ← 准备提交的内容
   ↓ git commit
本地仓库（Local Repository）  ← 你的 .git 目录
   ↓ git push
远程仓库（Remote Repository） ← GitHub / GitLab / Gitee
```

## 九、安装与配置

### 9.1 安装

见 [00 环境搭建](00-prepare.md)，已经装过了。

### 9.2 配置身份

```bash
git config --global user.name "你的名字"
git config --global user.email "your@email.com"

# 默认分支名（避免每次都问）
git config --global init.defaultBranch main

# 中文文件名不乱码
git config --global core.quotepath false

# Windows 推荐：行尾用 CRLF
git config --global core.autocrlf true

# Linux/Mac：行尾用 LF
git config --global core.autocrlf input
```

### 9.3 配置 SSH（推荐）

```bash
# 生成 SSH 密钥
ssh-keygen -t eda25519 -C "your@email.com"
# 一路回车

# 复制公钥
cat ~/.ssh/id_ed25519.pub
# 粘贴到 GitHub / GitLab / Gitee 的 SSH 设置里
```

## 十、克隆本项目

```bash
# HTTPS（简单，但每次输密码）
git clone https://github.com/your-org/price-management-system.git

# SSH（推荐）
git clone git@github.com:your-org/price-management-system.git

# 进项目
cd price-management-system

# 看远程仓库
git remote -v
```

## 十一、常用命令

### 11.1 看状态

```bash
git status                   # 看改了哪些文件
git diff                     # 看具体改了啥
git log                      # 看提交历史
git log --oneline            # 一行一个
git log --graph --oneline   # 带分支图
```

### 11.2 提交修改

```bash
# 1. 把改动加入暂存区
git add 文件名               # 单个文件
git add .                    # 所有文件

# 2. 提交到本地仓库
git commit -m "说明这次改了什么"

# 3. 推送到远程
git push
```

### 11.3 拉取更新

```bash
git pull                     # 拉取并合并
git fetch                    # 只拉取，不合并
```

### 11.4 分支操作

```bash
# 看所有分支
git branch

# 新建分支
git branch feature/xxx
git checkout feature/xxx           # 切过去
git checkout -b feature/xxx        # 新建并切换

# 合并分支（先切回 main）
git checkout main
git merge feature/xxx

# 删除分支
git branch -d feature/xxx
```

### 11.5 撤销操作

```bash
# 撤销工作区修改（危险！）
git checkout -- 文件名

# 撤销暂存
git restore --staged 文件名

# 撤销最近一次提交（保留改动）
git reset --soft HEAD~1

# 撤销最近一次提交（丢弃改动，危险！）
git reset --hard HEAD~1

# 回到某个历史版本
git checkout commit-id
```

## 十二、本项目分支策略

**推荐流程**（Git Flow 简化版）：

```
master (主分支，永远可发布)
   │
   ├── develop (开发分支)
   │     │
   │     ├── feature/login (功能分支)
   │     ├── feature/export (功能分支)
   │     └── bugfix/xxx (修复分支)
   │
   └── release/v1.2.0 (发布分支)
```

### 12.1 本项目日常开发流程

```bash
# 1. 从 master 拉最新
git checkout master
git pull

# 2. 新建功能分支
git checkout -b feature/add-product-statistics

# 3. 写代码、commit
git add .
git commit -m "feat(product): 新增产品统计接口"

# 4. 推送到远程
git push -u origin feature/add-product-statistics

# 5. 在 GitHub/GitLab 上开 Pull Request / Merge Request

# 6. 合并后删除本地分支
git checkout master
git pull
git branch -d feature/add-product-statistics
```

### 12.2 提交信息规范（Conventional Commits）

本项目建议用 Conventional Commits 规范：

```bash
feat(product): 新增产品统计接口
fix(login): 修复验证码不刷新问题
docs: 更新 API 文档
refactor(service): 重构价格计算逻辑
test(product): 补充单元测试
chore: 升级 Spring Boot 到 4.0.6
```

**格式**：`<类型>(<范围>): <描述>`

| 类型 | 含义 |
|------|------|
| `feat` | 新功能 |
| `fix` | 修复 bug |
| `docs` | 文档 |
| `style` | 格式（不影响代码运行） |
| `refactor` | 重构 |
| `test` | 测试 |
| `chore` | 构建/工具 |

## 十三、.gitignore

不提交到 Git 的文件放 `.gitignore`：

```gitignore
# Java
target/
*.class
*.jar
*.war

# IDE
.idea/
*.iml
.vscode/
.project
.classpath

# 配置
.env
application-local.yml

# 日志
*.log
logs/

# 系统
.DS_Store
Thumbs.db
```

本项目 `.gitignore` 已经写好了。

## 十四、实战场景

### 场景 1：拉代码发现冲突

```bash
git pull
# CONFLICT (content): Merge conflict in ProductService.java
```

**解决**：

```java
// Git 在冲突文件里加标记：
<<<<<<< HEAD
    // 你的代码
    return productRepository.findAll();
=======
    // 同事的代码
    return productRepository.findAllActive();
>>>>>>> origin/main
```

手动选择保留哪部分，删除标记，再 commit。

### 场景 2：提交错了想撤回

```bash
# 撤回最近一次 commit（保留改动）
git reset --soft HEAD~1

# 或者保留 commit 但撤回文件
git reset HEAD 文件名

# 已经 push 了：用 revert（生成反向 commit）
git revert HEAD
git push
```

### 场景 3：想回到某次 commit

```bash
# 看历史
git log --oneline
# a1b2c3d feat: 新增接口
# e4f5g6h fix: 修复 bug
# i7j8k9l init: 初始化项目

# 回到 i7j8k9l（init）
git checkout i7j8k9l

# 想从此处开个新分支
git checkout -b old-version
```

## 十五、推荐工具

| 工具 | 用途 |
|------|------|
| **IDEA 内置 Git** | 90% 操作可视化 |
| **GitLens** (VSCode) | 看代码谁写的、什么时候改的 |
| **SourceTree** | Git 图形化客户端 |
| **lazygit** | 命令行 TUI 工具 |
| **tig** | Linux 命令行 Git 工具 |

## 十六、动手试试

### 实验 1：第一次提交

```bash
# 在项目根目录
git status
git log --oneline

# 创建新分支
git checkout -b study/my-first-feature

# 随便改个 README
echo "# 学习笔记" >> docs/study/my-test.md
git add .
git commit -m "docs: 添加学习笔记"

# 推送
git push -u origin study/my-first-feature
```

### 实验 2：看历史

```bash
# 看项目里 product 相关文件的历史
git log --oneline -- backend/src/main/java/com/pricemanagement/entity/Product.java

# 看某次 commit 改了啥
git show commit-id
```

### 实验 3：制造冲突再解决

```bash
# 在两个分支改同一个文件
git checkout -b test1
echo "v1" > a.txt && git add . && git commit -m "v1"

git checkout master
git checkout -b test2
echo "v2" > a.txt && git add . && git commit -m "v2"

git checkout test1
git merge test2
# 出现冲突，手动解决
```

## 十七、常见错误

| 错误 | 原因 | 解决 |
|------|------|------|
| `fatal: not a git repository` | 当前目录不是 git 仓库 | `git init` 或 `cd` 进项目 |
| `Permission denied (publickey)` | SSH 密钥没配 | 见 9.3 |
| `Your branch is ahead of 'origin/main'` | 有本地 commit 没 push | `git push` |
| `CONFLICT (...) Merge conflict` | 合并冲突 | 手动解决 |
| `fatal: refusing to merge unrelated histories` | 两个不相关的仓库合并 | 加 `--allow-unrelated-histories` |
| `Updates were rejected` | 远程有更新你没拉 | 先 `git pull` 再 `git push` |

---

下一步：[01 宏观架构概览](01-architecture-overview.md) →

回头补课：
- [00 环境搭建](00-prepare.md)
- [00b MySQL 基础](00b-mysql-basics.md)
- [00c Java 语法入门](00c-java-syntax.md)