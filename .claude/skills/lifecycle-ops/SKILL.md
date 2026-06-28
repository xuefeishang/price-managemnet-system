---
name: lifecycle-ops
preamble-tier: 1
version: 1.3.1
description: |
  价格管理系统生产环境（10.7.5.175）的生命周期运维：完整关闭 + 启动。

  v1.3.1 进一步修正（基于 ops-check 体检 10/10 实测）：
  - ✅ MySQL 密码改回从 `.env` 读（ops-check 验证与容器 env 一致）
  - ✅ 容器清单修正为 13 个（mysql8 + 3 业务 + 9 Harbor）
  - ✅ 新增 mysql8 无 healthcheck 注释（依赖 restart policy）

  v1.3.0 实战修正（基于 2026-06-26 首次实战发现）：
  - 🔴 MySQL 是 Docker 容器 `mysql8`，**不是**系统服务
  - 🔴 MySQL 数据目录 `/data/mysql8/`（不是 `/var/lib/mysql`）
  - ✅ MySQL 启动方式：`docker start mysql8`（不是 systemctl）
  - ✅ binlog 已启用，PITR 备份路径修正
  - ✅ 备份根目录用 `/data/backup/lifecycle/`（不是 `/opt/backup`）

  v1.2.0 历史强化（在 v1.3.0 仍生效）：
  - mysqldump --master-data=2 + 容器内执行 + binlog 副本
  - dry-run 模式（LIFECYCLE_OPS_DRY_RUN=true）
  - JSON 报告 + rollback.sh 自动生成
  - 物理关机阶段 11
  - 容器日志归档（最后 200 行）
  - iptables 校验
  - Harbor 健康检查 ≥ 8 组件

  使用场景：
  - "优雅关闭生产环境"
  - "关闭服务器维护"
  - "重启价格系统"
  - "一键启动"
  - "演练关闭流程"（dry-run）

  与其他 skill 的边界：
  - deploy：首次/重建镜像部署
  - ops-check：只读体检
  - lifecycle-ops（本 skill）：完整启停，**不重建**
triggers:
  - 关闭生产环境
  - 优雅关闭
  - 关闭服务器
  - 整机关机
  - 停机维护
  - 重启服务
  - 一键启动
  - 重启价格系统
  - 启动生产环境
  - 演练关闭流程
allowed-tools:
  - Bash
  - Read
  - Write
  - AskUserQuestion
---

# 价格管理系统生命周期运维 Skill（v1.3.1）

生产环境（10.7.5.175）的**完整关闭 + 启动**全流程编排。

> **v1.3.1 修正**：基于 ops-check 体检（10/10）实测，修正容器清单和密码来源。
> **v1.3.0 重大修正**：基于 2026-06-26 首次实战发现，修正 MySQL 是 Docker 容器的假设。
> 之前的 v1.2.0 基于文档假设编写，**实战发现 MySQL 实际是 Docker 容器，不是系统服务**。
> 本版本已适配真实环境。

---

## v1.3.0 → v1.3.1 进一步修正（基于 ops-check 体检实测）

| 修正项 | v1.3.0（过度修正）| v1.3.1（修正）| 实测证据 |
|--------|-----------------|---------------|---------|
| **MySQL 密码来源** | `docker inspect` 读 env | **改回从 `.env` 读** | ops-check 实测两者一致 |
| **容器清单数量** | 14 个 | **13 个** | 实测 docker ps 输出 |
| **mysql8 健康检查** | 未提及 | **新增注释**：无 healthcheck，依赖 restart policy | ops-check 显示 mysql8 无 `(healthy)` 标记 |

---

## v1.2.0 → v1.3.0 实战修正清单

| 修正项 | v1.2.0（错误）| v1.3.0（修正）| 实战证据 |
|--------|--------------|---------------|---------|
| **MySQL 运行方式** | systemctl 服务 | **Docker 容器 `mysql8`** | `docker ps` 显示 mysql8，ps aux 无 systemd |
| **MySQL 关闭命令** | `systemctl stop mysql` | **`docker stop mysql8`** | 无 systemd unit |
| **MySQL 启动命令** | `systemctl start mysql` | **`docker start mysql8`** | 同上 |
| **MySQL 密码来源** | `grep .env` | **`docker inspect mysql8`** 读 env | 容器 env 含 `MYSQL_ROOT_PASSWORD=Root@2026` |
| **MySQL 数据目录** | `/var/lib/mysql/` | **`/data/mysql8/`** | bind 挂载 `/data/mysql8:/var/lib/mysql` |
| **binlog 路径** | `/var/lib/mysql/binlog.*` | **`/data/mysql8/binlog.*`** | binlog.000005/000006 在 /data/mysql8 |
| **备份根目录** | `/opt/backup/` | **`/data/backup/lifecycle/`** | `/opt` 79% 已用，`/data` 393G 仅 1% |
| **备份目录创建** | `mkdir /opt/backup/{mysql,redis,...}` | **`mkdir /data/backup/lifecycle/{mysql,redis,...}`** | 同上 |
| **MySQL 服务名检测** | `systemctl is-active mysql mysqld mariadb` | **`docker inspect mysql8 --format='{{.State.Status}}'`** | 不存在 systemd unit |

---

## 真实环境服务全景（v1.3.0）

```
┌────────────────────────────────────────────────────────────────────┐
│  外网访问                                                          │
│  https://price.jlmining.com:443  /  :32080  /  http://...:32801  │
└────────────────────────────────┬───────────────────────────────────┘
                                 │
                                 ▼
┌────────────────────────────────────────────────────────────────────┐
│  price-management-frontend  (Docker, bridge)                       │
│    ports: 80 / 443 / 32080 / 32801                                 │
│    compose: /opt/price-management-system/docker-compose.yml        │
└────────────────────────────────┬───────────────────────────────────┘
                                 │ host.docker.internal:8080
                                 ▼
┌────────────────────────────────────────────────────────────────────┐
│  price-management-backend  (Docker, host network)                  │
│    port: 8080                                                      │
│    compose: /opt/price-management-system/docker-compose.yml        │
└──────────────┬─────────────────────────────────┬───────────────────┘
               │                                 │
               ▼                                 ▼
┌────────────────────────────┐    ┌──────────────────────────────────┐
│ mysql8 (Docker 独立容器)   │    │ price-management-redis (Docker)  │
│ image: mysql:8.0.36        │    │ compose: 同业务系统               │
│ port: 3306                 │    │ port: 6379                       │
│ 启动方式: docker run       │    │ AOF 持久化                       │
│ 数据: /data/mysql8/        │    │ 数据: /data/Redis/                │
│ 密码: 容器env              │    └──────────────────────────────────┘
│ (无 compose 文件!)         │
└────────────────────────────┘

   独立项目（Harbor 镜像仓库，自带 PostgreSQL）：
┌────────────────────────────────────────────────────────────────────┐
│  /data/harbor/docker-compose.yml  (独立 compose)                   │
│    10 个组件：harbor-{core,db,jobservice,portal,log}              │
│    + nginx (goharbor) + redis-photon + registry + registryctl     │
│    port: 8082                                                      │
└────────────────────────────────────────────────────────────────────┘
```

### 容器清单（v1.3.1 修正，共 **13 个**，ops-check 实测）

| # | 容器名 | 镜像 | Compose 位置 | 启动方式 | 健康检查 |
|---|--------|------|--------------|---------|---------|
| 1 | **mysql8** | mysql:8.0.36 | **无 compose** | docker run（命令已丢失，需重建）| ⚠️ 无 |
| 2 | price-management-frontend | 自建 | /opt/price-management-system | compose | — |
| 3 | price-management-backend | 自建 | /opt/price-management-system | compose | ✅ (healthy) |
| 4 | price-management-redis | redis:7-alpine | /opt/price-management-system | compose | ✅ (healthy) |
| 5 | harbor-core | goharbor/harbor-core:v2.15.1 | /data/harbor | compose | ✅ (healthy) |
| 6 | harbor-db | goharbor/harbor-db:v2.15.1 | /data/harbor | compose | ✅ (healthy) |
| 7 | harbor-jobservice | goharbor/harbor-jobservice:v2.15.1 | /data/harbor | compose | ✅ (healthy) |
| 8 | harbor-portal | goharbor/harbor-portal:v2.15.1 | /data/harbor | compose | ✅ (healthy) |
| 9 | harbor-log | goharbor/harbor-log:v2.15.1 | /data/harbor | compose | ✅ (healthy) |
| 10 | nginx (Harbor) | goharbor/nginx-photon:v2.15.1 | /data/harbor | compose | ✅ (healthy) |
| 11 | redis (Harbor) | goharbor/redis-photon | /data/harbor | compose | ✅ (healthy) |
| 12 | registry | goharbor/registry-photon | /data/harbor | compose | ✅ (healthy) |
| 13 | registryctl | goharbor/harbor-registryctl | /data/harbor | compose | ✅ (healthy) |

> ⚠️ **mysql8 没有 healthcheck**：依赖 Docker `restart: unless-stopped` 自动重启。
> 如果 mysql8 内部进程崩了但容器未退出（如连接池耗尽），不会被自动发现。
> **建议**：本次关闭前用 `docker inspect mysql8 > /data/backup/lifecycle/mysql8-config-backup.json` 备份完整容器配置（含 env、卷、健康检查等），便于未来重建。

---

## 全局变量与工具函数（v1.3.0 修正）

```bash
# ============================================================
#  全局配置
# ============================================================
set -euo pipefail

# 远程服务器（CLAUDE.md §production_access_rule）
PROD_HOST="root@10.7.5.175"
PROD_DEPLOY_DIR="/opt/price-management-system"     # 业务系统
PROD_HARBOR_DIR="/data/harbor"                     # Harbor 独立 compose
PROD_DATA_ROOT="/data"                              # 数据盘（393G，1% 已用）

# ⚠️ MySQL 是独立 Docker 容器，无 compose
MYSQL_CONTAINER="mysql8"                            # v1.3.0 修正
MYSQL_IMAGE="mysql:8.0.36"                          # 镜像版本
MYSQL_DATA_DIR="/data/mysql8"                       # v1.3.0 修正
MYSQL_PORT="3306"

# ⚠️ 备份根目录用 /data/backup/lifecycle/（v1.3.0 修正）
PROD_BACKUP_ROOT="/data/backup/lifecycle"

# 时间戳
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
DATE_ONLY=$(date +%Y%m%d)

# 备份子目录
BACKUP_MYSQL_DIR="${PROD_BACKUP_ROOT}/mysql"
BACKUP_REDIS_DIR="${PROD_BACKUP_ROOT}/redis"
BACKUP_DATA_DIR="${PROD_BACKUP_ROOT}/data"
BACKUP_LOG_DIR="${PROD_BACKUP_ROOT}/logs"
BACKUP_REPORT_DIR="${PROD_BACKUP_ROOT}/reports"

# 报告文件
REPORT_FILE="${BACKUP_REPORT_DIR}/lifecycle_${TIMESTAMP}.json"
ROLLBACK_SCRIPT="${BACKUP_REPORT_DIR}/rollback_${TIMESTAMP}.sh"

# dry-run 模式
DRY_RUN="${LIFECYCLE_OPS_DRY_RUN:-false}"

# ============================================================
#  工具函数
# ============================================================
phase_start() {
  local phase_name="$1"
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo "▶ 阶段：${phase_name}"
  echo "  开始时间：$(date '+%Y-%m-%d %H:%M:%S')"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  PHASE_START_TIME=$(date +%s)
}

phase_end() {
  local phase_name="$1"
  local status="${2:-success}"
  local phase_end_time=$(date +%s)
  local duration=$((phase_end_time - PHASE_START_TIME))
  echo "✅ 阶段完成：${phase_name}（耗时 ${duration}s，状态 ${status}）"
}

ssh_run() {
  if [ "${DRY_RUN}" = "true" ]; then
    echo "  [DRY-RUN] ssh ${PROD_HOST} \"$*\""
  else
    ssh -o ConnectTimeout=10 -o BatchMode=yes ${PROD_HOST} "$@"
  fi
}

# v1.3.1: MySQL 密码从 .env 读（ops-check 实测与容器 env 一致，更通用）
get_mysql_password() {
  ssh_run "grep '^DB_PASSWORD=' ${PROD_DEPLOY_DIR}/.env | cut -d'=' -f2-"
}

# v1.3.0: MySQL 容器状态
mysql_status() {
  ssh_run "docker inspect ${MYSQL_CONTAINER} --format='{{.State.Status}}' 2>/dev/null || echo missing"
}

# v1.3.0: MySQL 端口监听
mysql_port_listening() {
  ssh_run "ss -tln | grep -q ':${MYSQL_PORT} ' && echo yes || echo no"
}
```

---

## 完整关闭流程（v1.3.0）

### 总览

| 阶段 | 步骤 | 耗时 | v1.3.0 关键修正 |
|------|------|------|---------------|
| 0 | 预检查 + 确认 | 10-20s | MySQL 状态用 `docker inspect` |
| 1 | MySQL 备份 | 30-60s | 容器内 mysqldump + binlog from /data/mysql8 |
| 2 | Redis 备份 + 日志归档 | 10-30s | — |
| 3 | 关键目录打包 | 10-30s | 备份到 /data/backup/lifecycle/data |
| 4 | 停 frontend | 5-10s | — |
| 5 | 停 backend | 30-60s | — |
| 6 | 停 Redis | 5-10s | — |
| 7 | **停 MySQL** | **10-60s** | `docker stop mysql8`（**不是 systemctl**）|
| 8 | 停 Harbor | 30-90s | 健康检查 ≥ 8 |
| 9 | 验证 + JSON 报告 + 回滚脚本 | 5-10s | — |
| 10 | 清理过期备份 | 1s | — |
| 11 | 物理关机（可选）| 5s | — |

**总耗时**：约 3-5 分钟。

---

### 阶段 0：预检查 + 确认（v1.3.0 修正）

```bash
phase_start "预检查与用户确认"

# 0.1 SSH 连通性
ssh_run "echo ok" || { echo "❌ SSH 不可达"; exit 1; }

# 0.2 ⚠️ MySQL 状态检查（v1.3.0：docker inspect，不用 systemctl）
MYSQL_STATE=$(mysql_status)
if [ "${MYSQL_STATE}" != "running" ]; then
  echo "❌ MySQL 容器未运行（状态：${MYSQL_STATE}）"
  exit 1
fi
echo "✅ MySQL 容器运行中（${MYSQL_CONTAINER}）"

# 0.3 业务容器状态
ssh_run "docker ps --format '{{.Names}}\t{{.Status}}' \
  | grep -E 'price-management-(frontend|backend|redis)' \
  | awk -F'\t' '{print \$1, \$2}'" || { echo "❌ 业务容器异常"; exit 1; }

# 0.4 Harbor 容器状态
ssh_run "cd ${PROD_HARBOR_DIR} && docker compose ps 2>/dev/null | grep -cE '\(healthy\)'" \
  | while read count; do
    if [ "${count}" -lt 8 ]; then
      echo "⚠️ Harbor healthy 组件仅 ${count}（< 8）"
    else
      echo "✅ Harbor healthy: ${count} 组件"
    fi
  done

# 0.5 磁盘空间预检
DB_SIZE=$(ssh_run "du -sb ${MYSQL_DATA_DIR} 2>/dev/null | awk '{print \$1}'" || echo "0")
BACKUP_DISK_FREE=$(ssh_run "df --output=avail /data | tail -1" || echo "0")
NEEDED=$((DB_SIZE * 2))
if [ "${BACKUP_DISK_FREE}" -lt "${NEEDED}" ]; then
  echo "❌ /data 备份空间不足（需要 ${NEEDED} bytes，可用 ${BACKUP_DISK_FREE}）"
  exit 1
fi
echo "✅ /data 空间充足（可用 ${BACKUP_DISK_FREE} bytes）"

# 0.6 创建备份目录（v1.3.0：用 /data/backup/lifecycle）
ssh_run "mkdir -p ${BACKUP_MYSQL_DIR} ${BACKUP_REDIS_DIR} ${BACKUP_DATA_DIR} ${BACKUP_LOG_DIR} ${BACKUP_REPORT_DIR}"
ssh_run "chmod 700 ${PROD_BACKUP_ROOT}/{mysql,redis,data,logs,reports}"
ssh_run "chown root:root ${PROD_BACKUP_ROOT}/{mysql,redis,data,logs,reports}"

# 0.7 询问用户确认
# AskUserQuestion: 是否关闭整台服务器？

phase_end "预检查与用户确认"
```

---

### 阶段 1：MySQL 备份（v1.3.0 重大修正 ⭐⭐⭐）

> **核心变化**：mysqldump 在 **容器内**执行，binlog 从 **bind 挂载目录** `/data/mysql8/` 备份。

```bash
phase_start "MySQL 备份（容器内执行）"

# 1.0 获取 MySQL 密码（v1.3.1：从 .env 读，ops-check 验证与容器 env 一致）
DB_PWD=$(get_mysql_password)
if [ -z "${DB_PWD}" ]; then
  echo "❌ 无法从 .env 读取 DB_PASSWORD"; exit 1
fi
echo "✅ MySQL 密码已从 .env 读取"

# 1.1 mysqldump 在容器内执行（v1.3.0：容器内 dump，避免网络传输）
BACKUP_FILE="${BACKUP_MYSQL_DIR}/price_management_${TIMESTAMP}.sql.gz"

ssh_run "docker exec -e MYSQL_PWD='${DB_PWD}' ${MYSQL_CONTAINER} \
  mysqldump \
  --user=root \
  --single-transaction \
  --lock-tables=false \
  --master-data=2 \
  --add-drop-database \
  --column-statistics=0 \
  --quick \
  --routines \
  --triggers \
  --events \
  --default-character-set=utf8mb4 \
  price_management | gzip > ${BACKUP_FILE}"

# 1.2 验证备份完整性
BACKUP_SIZE=$(ssh_run "stat -c %s ${BACKUP_FILE}")
echo "备份大小：$(numfmt --to=iec ${BACKUP_SIZE})"
ssh_run "gunzip -t ${BACKUP_FILE} && echo '✅ SQL 备份完整'"

# 1.3 验证核心表
ssh_run "gunzip -c ${BACKUP_FILE} \
  | grep -cE 'CREATE TABLE.*\`(sys_user|product|price_history|flyway_schema_history|security_event)\`'" \
  | while read count; do
    if [ "${count}" -lt 5 ]; then
      echo "⚠️ 核心表不全（仅 ${count} 个）"
    else
      echo "✅ 核心表齐全（${count} 个）"
    fi
  done

# 1.4 备份当前 binlog（v1.3.0：从 /data/mysql8 读，**不是** /var/lib/mysql）
ssh_run "mkdir -p ${BACKUP_MYSQL_DIR}/binlog_${TIMESTAMP}"
ssh_run "cp -u ${MYSQL_DATA_DIR}/binlog.* ${BACKUP_MYSQL_DIR}/binlog_${TIMESTAMP}/ 2>/dev/null || true"
ssh_run "ls -lh ${BACKUP_MYSQL_DIR}/binlog_${TIMESTAMP}/"

# 1.5 权限
ssh_run "chmod 600 ${BACKUP_FILE} ${BACKUP_MYSQL_DIR}/binlog_${TIMESTAMP}/*"
ssh_run "chown root:root ${BACKUP_FILE}"

phase_end "MySQL 备份"
```

**PITR 恢复示例**（v1.3.0 路径修正）：

```bash
# 1. 启动 mysql8
docker start mysql8

# 2. 全量恢复
gunzip -c /data/backup/lifecycle/mysql/price_management_20260626.sql.gz \
  | docker exec -i -e MYSQL_PWD='Root@2026' mysql8 \
    mysql --user=root price_management

# 3. binlog 应用到指定时间点（注意：binlog 已拷到备份目录）
docker exec -i mysql8 mysqlbinlog --stop-datetime="2026-06-26 14:35:00" \
  /data/backup/lifecycle/mysql/binlog_20260626/binlog.000006 \
  | docker exec -i -e MYSQL_PWD='Root@2026' mysql8 \
    mysql --user=root
```

---

### 阶段 2：Redis 备份 + 容器日志归档

```bash
phase_start "Redis 备份与日志归档"

REDIS_PWD=$(ssh_run "grep '^REDIS_PASSWORD=' ${PROD_DEPLOY_DIR}/.env | cut -d'=' -f2-" || true)

# 2.1 触发 BGSAVE
ssh_run "docker exec price-management-redis \
  redis-cli -a '${REDIS_PWD}' --no-auth-warning BGSAVE"

# 2.2 等待 BGSAVE 完成
PREVIOUS=$(ssh_run "docker exec price-management-redis \
  redis-cli -a '${REDIS_PWD}' --no-auth-warning LASTSAVE")
for i in {1..30}; do
  CURRENT=$(ssh_run "docker exec price-management-redis \
    redis-cli -a '${REDIS_PWD}' --no-auth-warning LASTSAVE")
  if [ "${CURRENT}" != "${PREVIOUS}" ]; then
    echo "✅ BGSAVE 完成（耗时 ${i}s）"
    break
  fi
  sleep 1
done

# 2.3 拷贝 RDB
RDB_FILE="${BACKUP_REDIS_DIR}/dump_${TIMESTAMP}.rdb"
ssh_run "docker cp price-management-redis:/data/dump.rdb ${RDB_FILE}"

# 2.4 容器日志归档（最后 200 行）
LOG_BACKUP_DIR="${BACKUP_LOG_DIR}/${TIMESTAMP}"
ssh_run "mkdir -p ${LOG_BACKUP_DIR}"
ssh_run "docker logs price-management-backend --tail 200 > ${LOG_BACKUP_DIR}/backend_final.log 2>&1"
ssh_run "docker logs price-management-frontend --tail 200 > ${LOG_BACKUP_DIR}/frontend_final.log 2>&1"
ssh_run "docker logs price-management-redis --tail 100 > ${LOG_BACKUP_DIR}/redis_final.log 2>&1"
ssh_run "docker logs ${MYSQL_CONTAINER} --tail 100 > ${LOG_BACKUP_DIR}/mysql_final.log 2>&1"

# 2.5 权限
ssh_run "chmod 600 ${RDB_FILE} && chown root:root ${RDB_FILE}"

phase_end "Redis 备份与日志归档"
```

---

### 阶段 3：关键目录打包（v1.3.0 路径修正）

```bash
phase_start "关键目录打包"

# 3.1 后端日志
ssh_run "tar czf ${BACKUP_DATA_DIR}/logs_${TIMESTAMP}.tar.gz \
  -C ${PROD_DEPLOY_DIR} logs/ 2>/dev/null || true"

# 3.2 产品 logo
ssh_run "tar czf ${BACKUP_DATA_DIR}/logos_${TIMESTAMP}.tar.gz \
  -C /var/lib/docker/volumes/price-management-system_logo-data/_data . \
  2>/dev/null || true"

# 3.3 Redis AOF/RDB（v1.3.0：路径在 /data/Redis/）
ssh_run "tar czf ${BACKUP_DATA_DIR}/redis-data_${TIMESTAMP}.tar.gz \
  -C ${PROD_DEPLOY_DIR}/redis-data . 2>/dev/null || true"

# 3.4 MySQL 数据目录（轻量元数据备份，不含 ibdata1 等大文件）
ssh_run "tar czf ${BACKUP_DATA_DIR}/mysql-meta_${TIMESTAMP}.tar.gz \
  -C ${MYSQL_DATA_DIR} \
  --exclude='*.ibd' --exclude='ibdata1' --exclude='#ib_*' \
  binlog.index auto.cnf mysql.ibd 2>/dev/null || true"

# 3.5 权限
ssh_run "chmod 600 ${BACKUP_DATA_DIR}/*_${TIMESTAMP}.tar.gz && chown root:root ${BACKUP_DATA_DIR}/*_${TIMESTAMP}.tar.gz"
ssh_run "ls -lh ${BACKUP_DATA_DIR}/*_${TIMESTAMP}.tar.gz"

phase_end "关键目录打包"
```

---

### 阶段 4：停 frontend

```bash
phase_start "停止 frontend"
ssh_run "cd ${PROD_DEPLOY_DIR} && docker compose stop frontend"
ssh_run "docker ps -a --format '{{.Names}}\t{{.Status}}' | grep price-management-frontend"
phase_end "停止 frontend"
```

---

### 阶段 5：停 backend

```bash
phase_start "停止 backend（含 graceful shutdown）"
ssh_run "cd ${PROD_DEPLOY_DIR} && docker compose stop backend"

for i in {1..60}; do
  STATUS=$(ssh_run "docker inspect price-management-backend --format='{{.State.Status}}' 2>/dev/null")
  if [ "${STATUS}" = "exited" ]; then
    echo "✅ Backend 已退出（耗时 ${i}s）"
    break
  fi
  sleep 1
done
phase_end "停止 backend"
```

---

### 阶段 6：停 Redis

```bash
phase_start "停止 Redis"
ssh_run "cd ${PROD_DEPLOY_DIR} && docker compose stop redis"
sleep 5
phase_end "停止 Redis"
```

---

### 阶段 7：停 MySQL（v1.3.0 重大修正 ⭐⭐⭐）

> **核心变化**：用 `docker stop` 而不是 `systemctl stop`。

```bash
phase_start "停止 MySQL（docker stop mysql8）"

# 7.1 MySQL flush（容器内执行）
DB_PWD=$(get_mysql_password)
ssh_run "docker exec -e MYSQL_PWD='${DB_PWD}' ${MYSQL_CONTAINER} \
  mysqladmin -uroot --protocol=socket \
  flush-hosts flush-logs flush-privileges flush-status flush-tables flush-threads 2>&1 | tail -5"
echo "✅ MySQL flush 完成"

# 7.2 docker stop（默认给 10s SIGTERM，MySQL 会优雅退出）
echo "停止 MySQL 容器（docker stop 默认 10s SIGTERM）..."
ssh_run "docker stop ${MYSQL_CONTAINER}"

# 7.3 验证
MYSQL_STATE=$(mysql_status)
if [ "${MYSQL_STATE}" = "exited" ]; then
  echo "✅ MySQL 容器已退出"
else
  echo "⚠️ MySQL 容器状态：${MYSQL_STATE}"
fi

# 7.4 端口释放
ssh_run "ss -tln | grep ':${MYSQL_PORT} ' || echo '✅ 3306 已释放'"

# 7.5 数据完整性（binlog 应已刷盘）
ssh_run "ls -lh ${MYSQL_DATA_DIR}/ibdata1 ${MYSQL_DATA_DIR}/binlog.* 2>/dev/null"

# 7.6 应急：强制 kill
# docker kill mysql8（仅在 hang 时用，可能丢数据）

phase_end "停止 MySQL"
```

**注意**：`docker stop` 默认 10 秒 SIGTERM 后升级为 SIGKILL。
- MySQL 收到 SIGTERM 会触发 InnoDB 刷盘
- 如果 10s 内未完成（数据量大），会被强制 kill
- 如果担心，可以加 `-t 60`（给 60s）

```bash
# 更保守的停止（60s 宽限）
ssh_run "docker stop -t 60 ${MYSQL_CONTAINER}"
```

---

### 阶段 8：停 Harbor

```bash
phase_start "停止 Harbor"

ssh_run "cd ${PROD_HARBOR_DIR} && docker compose stop"

for i in $(seq 1 90); do
  UP=$(ssh_run "cd ${PROD_HARBOR_DIR} && docker compose ps -q 2>/dev/null \
    | xargs docker inspect --format='{{.State.Status}}' 2>/dev/null \
    | grep -c '^running$'")
  if [ "${UP}" = "0" ]; then
    echo "✅ Harbor 全部停止（耗时 ${i}s）"
    break
  fi
  sleep 1
done

phase_end "停止 Harbor"
```

---

### 阶段 9：验证 + 报告

```bash
phase_start "验证与生成报告"

# 9.1 容器状态
ssh_run "docker ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.ExitCode}}' \
  | grep -E '${MYSQL_CONTAINER}|price-management|harbor-'"

# 9.2 端口释放
PORT_COUNT=$(ssh_run "ss -tln \
  | grep -E ':80\s|:443\s|:32080\s|:32801\s|:8080\s|:6379\s|:8082\s|:${MYSQL_PORT}\s' \
  | wc -l")
[ "${PORT_COUNT}" = "0" ] && echo "✅ 所有端口已释放" || echo "⚠️ 仍有 ${PORT_COUNT} 端口"

# 9.3 MySQL 容器状态
MYSQL_STATE=$(mysql_status)
[ "${MYSQL_STATE}" = "exited" ] && echo "✅ MySQL 已停止" || echo "⚠️ MySQL: ${MYSQL_STATE}"

# 9.4 生成 JSON 报告
JSON_CONTENT=$(cat <<EOF
{
  "version": "1.3.0",
  "timestamp": "${TIMESTAMP}",
  "operation": "graceful-shutdown",
  "target": "${PROD_HOST}",
  "mysql_deployment": "docker_container",
  "mysql_container": "${MYSQL_CONTAINER}",
  "backup_root": "${PROD_BACKUP_ROOT}",
  "backup_files": {
    "mysql_dump": "${BACKUP_MYSQL_DIR}/price_management_${TIMESTAMP}.sql.gz",
    "binlog_dir": "${BACKUP_MYSQL_DIR}/binlog_${TIMESTAMP}/",
    "redis_dump": "${BACKUP_REDIS_DIR}/dump_${TIMESTAMP}.rdb",
    "logs_tar": "${BACKUP_DATA_DIR}/logs_${TIMESTAMP}.tar.gz",
    "logos_tar": "${BACKUP_DATA_DIR}/logos_${TIMESTAMP}.tar.gz",
    "redis_data_tar": "${BACKUP_DATA_DIR}/redis-data_${TIMESTAMP}.tar.gz",
    "mysql_meta_tar": "${BACKUP_DATA_DIR}/mysql-meta_${TIMESTAMP}.tar.gz",
    "container_logs": "${BACKUP_LOG_DIR}/${TIMESTAMP}/"
  },
  "containers_stopped": [
    "${MYSQL_CONTAINER}",
    "price-management-frontend",
    "price-management-backend",
    "price-management-redis",
    "harbor-core",
    "harbor-db",
    "harbor-jobservice",
    "harbor-portal",
    "harbor-log",
    "harbor-redis",
    "harbor-registry",
    "harbor-registryctl",
    "goharbor-nginx"
  ],
  "ports_released": [80, 443, 32080, 32801, 8080, 6379, 8082, 3306]
}
EOF
)

ssh_run "cat > ${REPORT_FILE} <<< '${JSON_CONTENT}' && chmod 600 ${REPORT_FILE}"

# 9.5 生成回滚脚本（v1.3.0：docker start mysql8）
ROLLBACK_CONTENT=$(cat <<'ROLLBACK_EOF'
#!/bin/bash
# ============================================================
#  回滚脚本（启动失败时使用）
# ============================================================
set -euo pipefail
PROD_HOST="root@10.7.5.175"
BACKUP_TS="__TIMESTAMP__"
MYSQL_CONTAINER="mysql8"

echo "⚠️ 即将恢复 MySQL 数据到关闭前状态"
read -p "确认执行？(yes/no): " CONFIRM
[ "${CONFIRM}" = "yes" ] || exit 1

# 1. 启动 MySQL
ssh ${PROD_HOST} "docker start ${MYSQL_CONTAINER}"

# 2. 等待就绪
for i in {1..60}; do
  if ssh ${PROD_HOST} "ss -tln | grep -q ':3306 '"; then
    echo "✅ MySQL 已就绪"
    break
  fi
  sleep 1
done

# 3. 恢复 MySQL 数据
ssh ${PROD_HOST} "gunzip -c /data/backup/lifecycle/mysql/price_management_${BACKUP_TS}.sql.gz \
  | docker exec -i -e MYSQL_PWD=\$(docker inspect ${MYSQL_CONTAINER} \
      --format='{{range .Config.Env}}{{println .}}{{end}}' | grep MYSQL_ROOT_PASSWORD= | cut -d'=' -f2) \
    ${MYSQL_CONTAINER} mysql --user=root price_management"

# 4. 启动业务系统
ssh ${PROD_HOST} "cd /opt/price-management-system && docker compose up -d"

# 5. 启动 Harbor
ssh ${PROD_HOST} "cd /data/harbor && docker compose up -d"

echo "✅ 回滚完成，请验证业务"
ROLLBACK_EOF
)

ssh_run "cat > ${ROLLBACK_SCRIPT} <<< '${ROLLBACK_CONTENT//__TIMESTAMP__/${TIMESTAMP}}' && chmod 700 ${ROLLBACK_SCRIPT}"

phase_end "验证与生成报告"
```

---

### 阶段 10：清理过期备份

```bash
phase_start "清理过期备份"

ssh_run "
  find ${BACKUP_MYSQL_DIR} -name '*.sql.gz' -mtime +7 -delete
  find ${BACKUP_MYSQL_DIR} -type d -name 'binlog_*' -mtime +7 -exec rm -rf {} + 2>/dev/null
  find ${BACKUP_REDIS_DIR} -name '*.rdb' -mtime +7 -delete
  find ${BACKUP_DATA_DIR} -name '*.tar.gz' -mtime +7 -delete
  find ${BACKUP_LOG_DIR} -type d -mtime +7 -exec rm -rf {} + 2>/dev/null
  find ${BACKUP_REPORT_DIR} -name '*.json' -mtime +30 -delete
  find ${BACKUP_REPORT_DIR} -name 'rollback_*.sh' -mtime +7 -delete

  echo '✅ 过期备份已清理'
  echo '当前 /data/backup/lifecycle 占用：' \$(du -sh ${PROD_BACKUP_ROOT} | cut -f1)
"

phase_end "清理过期备份"
```

---

### 阶段 11：物理关机（可选）

```bash
phase_start "物理关机选项"

# AskUserQuestion:
# A) poweroff -h now
# B) reboot
# C) 不做任何事

# 根据选择执行
# ssh_run "sync && sleep 3 && systemctl poweroff"

phase_end "物理关机选项"
```

---

## 完整启动流程（v1.3.0）

### 阶段 0：环境检查（v1.3.0 修正）

```bash
phase_start "环境检查"

# 0.1 Docker daemon
ssh_run "docker info > /dev/null 2>&1 && echo '✅ Docker 在线' || echo '❌ Docker 离线'"

# 0.2 /data 空间
ssh_run "df -h /data 2>/dev/null | tail -1"

# 0.3 MySQL 数据目录（v1.3.0：用 /data/mysql8）
ssh_run "test -d ${MYSQL_DATA_DIR} && test -f ${MYSQL_DATA_DIR}/ibdata1 \
  && echo '✅ MySQL 数据目录完整' || echo '❌ MySQL 数据目录缺失'"

# 0.4 部署目录
ssh_run "test -d ${PROD_DEPLOY_DIR} && test -f ${PROD_DEPLOY_DIR}/docker-compose.yml \
  && echo '✅ 部署目录完整' || echo '❌ 部署目录缺失'"

# 0.5 Harbor 目录
ssh_run "test -f ${PROD_HARBOR_DIR}/docker-compose.yml \
  && echo '✅ Harbor compose 存在' || echo '❌ Harbor compose 缺失'"

# 0.6 iptables
PHASE1=$(ssh_run "grep -c 'SECURITY-HARDENING-PHASE1' /etc/iptables/rules.v4 2>/dev/null || echo 0")
[ "${PHASE1}" -ge 7 ] && echo "✅ iptables 规则：${PHASE1}" \
  || echo "⚠️ iptables 规则不足（${PHASE1} < 7）"

phase_end "环境检查"
```

---

### 阶段 1：启动 MySQL（v1.3.0 重大修正 ⭐⭐⭐）

> **核心变化**：用 `docker start mysql8` 而不是 `systemctl start mysql`。

```bash
phase_start "启动 MySQL（docker start）"

# 1.1 启动
ssh_run "docker start ${MYSQL_CONTAINER}"

# 1.2 等待端口监听（最多 60s）
for i in {1..60}; do
  if [ "$(mysql_port_listening)" = "yes" ]; then
    echo "✅ MySQL 端口监听（耗时 ${i}s）"
    break
  fi
  sleep 1
done

# 1.3 等待完全就绪（最多 30s）
sleep 3
for i in {1..30}; do
  DB_PWD=$(get_mysql_password)
  if ssh_run "docker exec -e MYSQL_PWD='${DB_PWD}' ${MYSQL_CONTAINER} \
    mysql --user=root -e 'SELECT 1' price_management" >/dev/null 2>&1; then
    echo "✅ MySQL 可连接（耗时 ${i}s）"
    break
  fi
  sleep 1
done

# 1.4 验证 Flyway 表
DB_PWD=$(get_mysql_password)
ssh_run "docker exec -e MYSQL_PWD='${DB_PWD}' ${MYSQL_CONTAINER} \
  mysql --user=root -e 'SHOW TABLES FROM price_management' \
  | grep -q flyway_schema_history && echo '✅ Flyway 表存在'"

phase_end "启动 MySQL"
```

---

### 阶段 2：启动 Harbor

```bash
phase_start "启动 Harbor"

ssh_run "cd ${PROD_HARBOR_DIR} && docker compose up -d"

for i in $(seq 1 120); do
  HEALTHY=$(ssh_run "cd ${PROD_HARBOR_DIR} && docker compose ps 2>/dev/null \
    | grep -cE '\(healthy\)'")
  if [ "${HEALTHY}" -ge 8 ]; then
    echo "✅ Harbor 就绪（${HEALTHY} healthy，耗时 ${i}s）"
    break
  fi
  sleep 1
done

phase_end "启动 Harbor"
```

---

### 阶段 3：启动业务系统

```bash
phase_start "启动业务系统"
ssh_run "cd ${PROD_DEPLOY_DIR} && docker compose up -d"
sleep 10
phase_end "启动业务系统"
```

---

### 阶段 4：健康检查

```bash
phase_start "健康检查"

# 4.1 后端 healthy
for i in {1..90}; do
  HEALTHY=$(ssh_run "docker inspect price-management-backend \
    --format='{{.State.Health.Status}}' 2>/dev/null")
  if [ "${HEALTHY}" = "healthy" ]; then
    echo "✅ Backend healthy（耗时 ${i}s）"
    break
  fi
  sleep 1
done

# 4.2 端口
ssh_run "ss -tln \
  | grep -E ':80\s|:443\s|:32080\s|:32801\s|:8080\s|:6379\s|:3306\s|:8082\s'"

# 4.3 API
ssh_run "curl -s -o /dev/null -w 'captcha: %{http_code}\n' http://localhost:8080/api/auth/captcha"
ssh_run "curl -s -o /dev/null -w '32801: %{http_code}\n' http://localhost:32801/"
ssh_run "curl -s -k -o /dev/null -w '32080: %{http_code}\n' https://localhost:32080/"

# 4.4 MySQL 数据完整
DB_PWD=$(get_mysql_password)
ssh_run "docker exec -e MYSQL_PWD='${DB_PWD}' ${MYSQL_CONTAINER} \
  mysql --user=root -e 'SELECT COUNT(*) FROM sys_user; SELECT COUNT(*) FROM product' \
  price_management"

# 4.5 iptables 验证
PHASE1=$(ssh_run "iptables -L INPUT -n | grep -c 'SECURITY-HARDENING-PHASE1'" || echo 0)
[ "${PHASE1}" -ge 7 ] && echo "✅ iptables：${PHASE1}" || echo "⚠️ iptables 不足"

phase_end "健康检查"
```

---

## 轻量模式（仅重启业务容器，跳过 MySQL）

```bash
# 仅业务容器
ssh_run "cd ${PROD_DEPLOY_DIR} && docker compose stop frontend backend redis"
ssh_run "cd ${PROD_DEPLOY_DIR} && docker compose up -d"
```

---

## Dry-Run 模式

```bash
export LIFECYCLE_OPS_DRY_RUN=true
# ... 执行流程，所有命令只打印不执行
unset LIFECYCLE_OPS_DRY_RUN
```

---

## 故障排查（v1.3.0）

| 问题 | 解决方案 |
|------|---------|
| MySQL 容器无法启动 | `docker logs mysql8 --tail 50` 看错误 |
| mysql8 容器丢失 | **需要重建 docker run 命令**（compose 文件丢失）|
| binlog PITR 失败 | binlog 文件被截断，需要从备份恢复全量 |
| MySQL flush 失败 | 用 `mysqladmin -uroot -p ping` 测试连接 |
| 端口被占 | `ss -tlnp | grep :3306` 找占用进程 |

### ⚠️ mysql8 重建（容器丢失场景）

由于 mysql8 没有 compose 文件，需要重建启动命令：

```bash
# 参考启动命令（基于现有配置）
docker run -d \
  --name mysql8 \
  --restart unless-stopped \
  -p 3306:3306 \
  -v /data/mysql8:/var/lib/mysql \
  -e MYSQL_ROOT_PASSWORD='Root@2026' \
  -e TZ=Asia/Shanghai \
  mysql:8.0.36 \
  --character-set-server=utf8mb4 \
  --collation-server=utf8mb4_unicode_ci
```

**建议**：本次关闭前立即用 `docker inspect mysql8 > mysql8-config-backup.json` 备份完整容器配置！

---

## 版本历史

### v1.3.1（2026-06-26）—— ops-check 体检实测修正

**修正（基于 ops-check 体检 10/10）**：
- ✅ MySQL 密码源: docker inspect env → **改回 .env**（实测两者一致）
- ✅ 容器清单: 14 → **13 个**（实测 docker ps）
- ✅ 新增 mysql8 无 healthcheck 警告注释

### v1.3.0（2026-06-26）—— 实战修正版

**修正（基于首次实战）**：
- 🔴 MySQL: systemctl → **docker stop/start mysql8**
- 🔴 MySQL 数据目录: /var/lib/mysql → **/data/mysql8/**
- 🔴 binlog 路径: /var/lib/mysql/binlog.* → **/data/mysql8/binlog.\***
- 🔴 备份根: /opt/backup → **/data/backup/lifecycle/**
- 🔴 容器清单: 3 个 → **13 个**（v1.3.1 修正）
- ✅ 新增 mysql-meta 备份（binlog.index + auto.cnf）
- ✅ 新增 mysql8 容器配置备份建议
- ✅ 阶段 2 新增 mysql8 日志归档

### v1.2.0（2026-06-26）

- mysqldump 加 --master-data=2 + dry-run + JSON 报告 + rollback.sh

### v1.1.0（2026-06-26）

- 加入 MySQL 关闭和启动流程

### v1.0.0（2026-06-26）

- 初始版本

---

*Skill 版本: 1.3.1*
*最后更新: 2026-06-26 — ops-check 体检 10/10 后修正（密码源 + 容器数 + healthcheck 注释）*