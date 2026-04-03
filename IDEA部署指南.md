
# IDEA部署指南（中文环境）

## 前置条件

在开始之前，请确保您已安装以下软件：

1. **IntelliJ IDEA**（推荐使用 Ultimate 版本，Community 版本也可以）
2. **Java JDK 17** 或更高版本
3. **MySQL 8.4** 或更高版本
4. **Node.js 16** 或更高版本（用于前端开发）
5. **Git**（可选，用于版本控制）

---

## 第一步：准备Java环境

### 1.1 检查Java是否已安装

打开IDEA，点击顶部菜单栏：
- **文件** → **项目结构**（快捷键：`Ctrl+Alt+Shift+S`）

在左侧导航栏选择 **SDKs**：
- 如果已经看到有 Java 17 的 SDK，说明已安装
- 如果没有，点击 **+** 号 → **添加SDK** → **下载JDK**

### 1.2 下载并配置Java 17

1. 在 **下载JDK** 对话框中：
   - 版本：选择 `17`
   - 供应商：选择 `Eclipse Temurin` 或 `AdoptOpenJDK`
   - 点击 **下载**

2. 下载完成后，会自动添加到SDK列表中

---

## 第二步：导入后端项目

### 2.1 打开项目

1. 打开 IntelliJ IDEA
2. 在欢迎界面点击 **打开**（Open）
3. 浏览到项目目录：`E:\ClaudeCodeProject\price-management-system\backend`
4. 点击 **确定**（OK）

### 2.2 等待Maven项目加载

IDEA会自动识别这是一个Maven项目，并开始下载依赖：

1. 观察右下角的进度条
2. 等待所有依赖下载完成（可能需要几分钟）
3. 确保右下角没有红色报错

### 2.3 Maven刷新（如果需要）

如果依赖下载有问题，可以手动刷新：

1. 点击右侧的 **Maven** 工具窗口
2. 点击顶部的 **刷新** 图标（两个旋转箭头）
3. 或右键点击项目根目录 → **Maven** → **重新加载项目**

---

## 第三步：配置数据库

### 3.1 创建数据库和用户

**方式一：使用MySQL命令行**

```bash
# 登录MySQL
mysql -u root -p

# 创建数据库
CREATE DATABASE IF NOT EXISTS price_management DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

# （可选）创建专用数据库用户
CREATE USER IF NOT EXISTS 'pricemanagement'@'localhost' IDENTIFIED BY '123abc';
GRANT ALL PRIVILEGES ON price_management.* TO 'pricemanagement'@'localhost';
FLUSH PRIVILEGES;

EXIT;
```

**方式二：使用MySQL Workbench**

1. 打开MySQL Workbench
2. 使用root用户连接到本地MySQL服务器
3. 点击 **创建新的SQL查询**
4. 执行上述SQL语句

### 3.2 执行数据库初始化脚本

1. 在MySQL Workbench中，选择 `price_management` 数据库
2. 打开文件：`E:\ClaudeCodeProject\price-management-system\backend\src\main\resources\init.sql`
3. 点击 **执行**（闪电图标）按钮
4. 确认所有表创建和数据插入成功

> 注意：`init.sql` 包含完整的表结构创建和数据初始化，推荐使用此脚本一步完成初始化。

### 3.4 验证数据是否正确

在MySQL Workbench中执行以下查询验证：

```sql
USE price_management;

-- 查看产品分类（应该有5条记录）
SELECT * FROM product_category;

-- 查看产品数据（应该有20条记录）
SELECT * FROM product;

-- 查看用户数据（应该有3条记录）
SELECT username, role, status FROM sys_user;
```

### 3.5 配置应用连接数据库

在IDEA中打开配置文件：`E:\ClaudeCodeProject\price-management-system\backend\src\main\resources\application.yml`

确认以下配置（根据您的实际情况）：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/price_management?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true
    username: root  # 或您创建的专用用户
    password: 123abc  # 修改为您的实际密码
    driver-class-name: com.mysql.cj.jdbc.Driver

  jpa:
    hibernate:
      ddl-auto: update  # 首次运行可以改为 create-drop，之后改回 update
    show-sql: true
    properties:
      hibernate:
        format_sql: true
        dialect: org.hibernate.dialect.MySQLDialect
```

**重要提示：**
- 首次运行可以将 `ddl-auto` 改为 `create-drop`，但运行后建议改回 `update`
- 如果您使用单独创建的用户，请修改 `username` 和 `password`

---

## 第四步：启动后端应用

### 4.1 找到主启动类

在IDEA左侧的项目视图中：
1. 展开 `src` → `main` → `java` → `com.pricemanagement`
2. 找到 `PriceManagementApplication.java` 文件

### 4.2 运行应用

**方式一：右键运行**
1. 右键点击 `PriceManagementApplication.java`
2. 选择 **运行 'PriceManagementApplication.main()'**

**方式二：使用运行按钮**
1. 点击 `PriceManagementApplication.java` 文件
2. 点击代码行号旁边的绿色三角形图标
3. 或使用快捷键 `Shift+F10`

### 4.3 查看控制台输出

观察IDEA底部的 **控制台**（Console）标签页：

**成功启动的标志：**
```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/
 :: Spring Boot ::                (v3.2.0)

... 省略其他日志 ...

Started PriceManagementApplication in X.XXX seconds
```

### 4.4 测试后端API

**方式一：使用浏览器**
打开浏览器访问：`http://localhost:8080/actuator/health`
（如果没有配置actuator，可以跳过此步）

**方式二：使用Postman或其他API工具**
测试登录接口：
- URL：`http://localhost:8080/api/auth/login`
- 方法：POST
- Body（JSON）：
```json
{
  "username": "admin",
  "password": "admin123"
}
```

---

## 第五步：配置IDEA的数据库工具（可选但推荐）

### 5.1 打开Database工具窗口

1. 点击IDEA右侧的 **Database** 工具窗口
2. 如果没有看到，点击顶部菜单 **视图** → **工具窗口** → **Database**

### 5.2 添加MySQL数据源

1. 点击 **Database** 窗口左上角的 **+** 号
2. 选择 **数据源** → **MySQL**

### 5.3 配置连接信息

在弹出的配置对话框中填写：
- **主机**：`localhost`
- **端口**：`3306`
- **用户**：`root`（或您创建的用户）
- **密码**：`123abc`
- **数据库**：`price_management`

### 5.4 测试并保存连接

1. 点击 **测试连接**（Test Connection）
2. 如果显示绿色对勾，说明连接成功
3. 点击 **确定** 保存

### 5.5 使用Database工具操作数据库

- 展开数据源可以查看所有表
- 双击表名可以查看数据
- 可以直接在IDEA中执行SQL查询
- 右键表可以进行导入/导出操作

---

## 第六步：启动前端应用

### 6.1 打开前端项目

**方式一：在同一IDEA窗口中打开**
1. 点击IDEA顶部的 **文件** → **打开**
2. 选择 `E:\ClaudeCodeProject\price-management-system\frontend`
3. 选择 **在新窗口中打开**（推荐）或 **附加到当前项目**

**方式二：使用其他编辑器**
也可以使用 VS Code 或其他编辑器打开前端项目

### 6.2 安装前端依赖

1. 在IDEA中打开 **终端**（Terminal）窗口
2. 确认当前目录是 `frontend`
3. 运行以下命令：
```bash
npm install
```

**注意：**
- 如果安装很慢，可以使用国内镜像：
  ```bash
  npm install --registry=https://registry.npmmirror.com
  ```
- 首次安装可能需要几分钟，请耐心等待

### 6.3 启动前端开发服务器

安装依赖完成后，在终端运行：
```bash
npm run dev
```

**成功启动标志：**
```
  VITE v5.x.x  ready in XXX ms

  ➜  Local:   http://localhost:5173/
  ➜  Network: use --host to expose
```

### 6.4 访问系统

1. 浏览器会自动打开 `http://localhost:5173`
2. 如果没有自动打开，手动在浏览器中访问
3. 使用默认用户登录：
   - 用户名：`admin`
   - 密码：`admin123`

---

## 第七步：调试功能

### 7.1 后端调试

#### 设置断点
1. 在代码行号左侧点击，会出现红色圆点（断点）
2. 或在代码行使用快捷键 `Ctrl+F8`

#### 以调试模式运行
1. 右键点击 `PriceManagementApplication.java`
2. 选择 **调试 'PriceManagementApplication.main()'**
3. 或使用快捷键 `Shift+F9`

#### 调试控制
- **F8**：步过（Step Over）
- **F7**：步入（Step Into）
- **Shift+F8**：步出（Step Out）
- **F9**：继续运行到下一个断点
- **Ctrl+F8**：取消断点

#### 查看变量值
- 使用IDEA底部的 **调试器**（Debugger）工具窗口
- 可以查看当前作用域内的所有变量值
- 使用 **计算表达式**（Evaluate Expression）功能计算任意表达式

### 7.2 前端调试

#### 使用浏览器开发者工具
1. 在浏览器中按 `F12` 打开开发者工具
2. **元素**（Elements）：查看和修改HTML/CSS
3. **控制台**（Console）：查看日志和运行JavaScript
4. **网络**（Network）：查看网络请求
5. **源代码**（Sources）：设置断点和调试

---

## 常见问题排查

### 问题1：Java版本不匹配

**症状**：运行时报错，提示Java版本问题

**解决方法**：
1. **文件** → **项目结构** → **项目**
2. 确保 **SDK** 选择的是 Java 17
3. 确保 **语言级别**（Language level）是 `17 - Sealed types, pattern matching for switch`
4. **文件** → **设置** → **构建、执行、部署** → **编译器** → **Java编译器**
5. 确保 **模块字节码版本**（Project bytecode version）是 17

### 问题2：Maven依赖下载失败

**症状**：右下角Maven下载进度条不动或报错

**解决方法**：
1. 检查网络连接
2. 配置国内Maven镜像：
   - 编辑 `~/.m2/settings.xml`（Windows通常在 `C:\Users\用户名\.m2\settings.xml`）
   - 添加阿里云镜像：
     ```xml
     <mirrors>
       <mirror>
         <id>aliyun</id>
         <mirrorOf>central</mirrorOf>
         <name>Aliyun Maven</name>
         <url>https://maven.aliyun.com/repository/public</url>
       </mirror>
     </mirrors>
     ```
3. 在IDEA中刷新Maven项目

### 问题3：数据库连接失败

**症状**：启动时报错，提示无法连接数据库

**检查清单**：
1. MySQL服务是否已启动？
2. 数据库名称、用户名、密码是否正确？
3. 端口3306是否被占用？
4. `application.yml` 中的连接URL是否正确？
5. 是否添加了 `allowPublicKeyRetrieval=true` 参数？

### 问题4：前端端口被占用

**症状**：运行 `npm run dev` 时报错，提示5173端口已被占用

**解决方法**：
1. 修改前端配置文件 `vite.config.ts`：
   ```typescript
   export default defineConfig({
     server: {
       port: 5174  // 改为其他端口
     }
   })
   ```
2. 或找到占用端口的进程并结束它

### 问题5：前端无法访问后端API

**症状**：前端请求API时报错，或返回404

**检查清单**：
1. 后端服务是否已启动？
2. 后端是否运行在8080端口？
3. 前端API请求的URL是否正确？
4. CORS配置是否正确？

---

## 常用快捷键（中文IDEA）

### 导航和编辑
- **Ctrl + N**：查找类
- **Ctrl + Shift + N**：查找文件
- **Ctrl + F**：当前文件中查找
- **Ctrl + Shift + F**：全项目查找
- **Ctrl + Alt + L**：格式化代码
- **Ctrl + /**：行注释
- **Ctrl + Shift + /**：块注释

### 运行和调试
- **Shift + F10**：运行
- **Shift + F9**：调试
- **F8**：步过
- **F7**：步入
- **Shift + F8**：步出
- **F9**：继续
- **Ctrl + F8**：切换断点

### 项目和视图
- **Alt + 1**：项目视图
- **Alt + 4**：运行窗口
- **Alt + 5**：调试窗口
- **Alt + 9**：版本控制
- **Ctrl + Alt + Shift + S**：项目结构
- **Ctrl + Alt + S**：设置

---

## 下一步

现在您已经成功在IDEA中部署了项目！接下来可以：

1. **探索系统功能**：使用admin用户登录，体验各个功能模块
2. **修改代码**：尝试修改一些代码，观察效果
3. **编写测试**：为您的代码添加单元测试
4. **提交代码**：如果使用Git，可以提交您的更改
5. **阅读文档**：查看其他项目文档了解更多细节

---

## 生产环境部署

### 生产环境要求

#### 硬件配置
- **CPU**: 4核及以上
- **内存**: 8GB及以上
- **硬盘**: 100GB及以上（建议使用 SSD）
- **网络**: 100Mbps及以上

#### 软件配置
- **操作系统**: CentOS 7.6+ 或 Ubuntu 18.04+
- **Java**: OpenJDK 17+
- **MySQL**: 8.0+
- **Nginx**: 1.16+（用于部署前端）

### 后端部署

#### 1. 打包项目
```bash
cd backend
mvn clean package -DskipTests
```

打包成功后，会在 `target` 目录下生成 `price-management-backend-1.0.0.jar` 文件。

#### 2. 部署到服务器

**方式一：直接运行 JAR 包**
```bash
# 创建部署目录
mkdir -p /opt/pricemanagement/backend

# 上传 JAR 包到服务器
scp target/price-management-backend-1.0.0.jar user@server:/opt/pricemanagement/backend/

# 配置应用（创建 application.yml）
cat > /opt/pricemanagement/backend/application.yml <<EOF
server:
  port: 8080

spring:
  application:
    name: price-management-system
  datasource:
    url: jdbc:mysql://localhost:3306/price_management?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai
    username: pricemanagement
    password: your_strong_password
    driver-class-name: com.mysql.cj.jdbc.Driver
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false

jwt:
  secret: your_jwt_secret_key
  expiration: 86400000

logging:
  level:
    com.pricemanagement: info
EOF

# 运行应用
cd /opt/pricemanagement/backend
nohup java -jar price-management-backend-1.0.0.jar --spring.config.location=./application.yml > app.log 2>&1 &
```

**方式二：使用 Systemd 服务**

创建服务文件 `/etc/systemd/system/pricemanagement-backend.service`：
```ini
[Unit]
Description=Price Management System Backend
After=syslog.target network.target

[Service]
Type=simple
User=pricemanagement
WorkingDirectory=/opt/pricemanagement/backend
ExecStart=/usr/bin/java -jar /opt/pricemanagement/backend/price-management-backend-1.0.0.jar
Restart=always
RestartSec=30

[Install]
WantedBy=multi-user.target
```

启用并启动服务：
```bash
systemctl daemon-reload
systemctl enable pricemanagement-backend.service
systemctl start pricemanagement-backend.service
systemctl status pricemanagement-backend.service
```

### 前端部署

#### 1. 打包项目
```bash
cd frontend
npm run build
```

打包成功后，会在 `dist` 目录下生成生产环境的文件。

#### 2. 部署到 Nginx

创建配置文件 `/etc/nginx/conf.d/pricemanagement.conf`：
```nginx
server {
    listen 80;
    server_name your.domain.com;
    root /var/www/pricemanagement/frontend;
    index index.html;

    # 静态资源缓存
    location ~* \.(js|css|png|jpg|jpeg|gif|ico|svg|woff|woff2|ttf|eot)$ {
        expires 30d;
        add_header Cache-Control "public, immutable";
    }

    # 前端路由重定向
    location / {
        try_files $uri $uri/ /index.html;
    }

    # API 接口代理
    location /api {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        client_max_body_size 10M;
    }
}
```

部署静态文件：
```bash
mkdir -p /var/www/pricemanagement/frontend
scp -r frontend/dist/* user@server:/var/www/pricemanagement/frontend/
nginx -t && nginx -s reload
```

### 数据库初始化（生产环境）

```bash
mysql -u root -p
CREATE DATABASE IF NOT EXISTS price_management DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
EXIT;
mysql -u root -p price_management < /path/to/backend/src/main/resources/init.sql
```

> 注意：用户数据由 Spring Boot 应用启动时的 DataInitializer 自动初始化，密码统一为 admin123。

### 安全配置

1. **数据库安全**：使用强密码，限制数据库用户访问IP，定期备份
2. **应用安全**：使用 HTTPS，定期更新依赖库，启用日志审计
3. **服务器安全**：配置防火墙，禁用不必要的服务，定期更新系统

### 版本升级

```bash
# 后端升级
systemctl stop pricemanagement-backend.service
cp -r /opt/pricemanagement/backend /opt/pricemanagement/backend.backup
scp target/price-management-backend-1.0.1.jar user@server:/opt/pricemanagement/backend/
systemctl start pricemanagement-backend.service

# 前端升级
scp -r frontend/dist/* user@server:/var/www/pricemanagement/frontend/
nginx -s reload
```

---

祝开发顺利！如有问题，请查看其他项目文档或联系开发团队。
