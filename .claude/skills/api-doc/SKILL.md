---
name: api-doc
description: 生成 Spring Boot REST API 文档
disable-model-invocation: true
---

# API 文档生成

为 Spring Boot Controller 生成 OpenAPI 风格的 API 文档。

## 使用方式

```
/api-doc <Controller名称或路径>
```

## 执行步骤

1. 读取指定的 Controller 文件
2. 分析所有 `@RequestMapping`、`@GetMapping`、`@PostMapping` 等注解
3. 提取请求路径、方法、参数、响应类型
4. 生成 OpenAPI 3.0 格式文档
5. 输出到 `docs/dev/api/` 目录

## 文档格式

```yaml
openapi: 3.0.0
info:
  title: 价格管理系统 API
  version: 1.0.0
paths:
  /api/products:
    get:
      summary: 获取产品列表
      parameters:
        - name: page
          in: query
          schema:
            type: integer
      responses:
        '200':
          description: 成功
```

## 参考文件

- `backend/src/main/java/com/pricemanagement/controller/` — 所有控制器
- `docs/dev/design/api-design.md` — 现有 API 设计（v2.0）
- `docs/dev/api/internal.md` — 内部 API 端点（v2.0）
- `docs/dev/api/external.md` — 外部 API 端点（v2.0）
