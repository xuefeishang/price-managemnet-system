# Redis 缓存部署方案

## Context

为了提升数据获取性能，计划引入 Redis 作为缓存层。当前需要缓存的主要数据：
- 字典数据（SysDict）- 频繁读取，很少修改
- 主题配置（StyleConfig）- 启动时加载，很少修改
- 用户权限信息

## 部署方案

### 方案：Docker 部署 Redis

**步骤1：安装 Docker Desktop**
- 下载地址：https://www.docker.com/products/docker-desktop
- 安装后启动，确保 Docker 服务运行

**步骤2：启动 Redis 容器**
```bash
docker run -d --name redis -p 6379:6379 redis:latest
```

**步骤3：验证 Redis 连接**
```bash
docker exec -it redis redis-cli ping
# 应返回 PONG
```

## 后端集成方案

### 引入依赖（pom.xml）
```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-cache</artifactId>
</dependency>
```

### 配置（application.yml）
```yaml
spring:
  redis:
    host: localhost
    port: 6379
  cache:
    type: redis
    redis:
      time-to-live: 3600000  # 缓存1小时
```

### 缓存策略

| 数据 | 缓存键 | TTL | 说明 |
|------|--------|-----|------|
| 字典数据 | dict:{category} | 1小时 | 按分类缓存 |
| 主题配置 | style:config | 1小时 | 全量配置 |
| 用户信息 | user:{id} | 30分钟 | 用户数据 |

### 待改造服务

1. **SysDictService** - 字典查询加入缓存
2. **StyleConfigService** - 主题配置加入缓存
3. **UserService** - 用户信息缓存（可选）

## 实现步骤

1. Docker 部署 Redis（用户执行）
2. 后端添加 Redis 依赖
3. 配置 Redis 连接和缓存
4. 改造 SysDictService - 添加缓存注解
5. 改造 StyleConfigService - 添加缓存注解
6. 测试验证

## 验证方式

1. 启动 Redis 容器
2. 启动后端应用
3. 首次访问字典/主题接口，观察日志
4. 再次访问，验证响应速度提升
5. 可通过 Redis CLI 查看缓存 keys：`docker exec -it redis redis-cli keys '*'`

## 参考文件

- `backend/src/main/java/com/pricemanagement/service/SysDictService.java` - 字典服务，需加入缓存
- `backend/src/main/java/com/pricemanagement/service/StyleConfigService.java` - 主题服务，需加入缓存
- `backend/pom.xml` - 添加依赖位置