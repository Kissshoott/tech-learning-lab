# M4: 高级搜索（混合搜索与过滤）- curl 示例

> **前置条件**：已完成 M2 的数据插入和 M3 的索引创建，Collection `test_docs` 已加载到内存。

## 纯向量搜索（无过滤，对比基准）

```bash
curl -X POST "http://localhost:19530/v2/vectordb/entities/search" \
  -H "Content-Type: application/json" \
  -d '{
    "collectionName": "test_docs",
    "data": [[0.12, 0.34, 0.56, 0.78, 0.23, 0.45, 0.67, 0.89]],
    "annsField": "embedding",
    "topK": 5,
    "outputFields": ["id", "text", "category"]
  }'
```

## 标量过滤搜索 — 等值过滤

只搜索 `category` 为 `milvus` 的数据：

```bash
curl -X POST "http://localhost:19530/v2/vectordb/entities/search" \
  -H "Content-Type: application/json" \
  -d '{
    "collectionName": "test_docs",
    "data": [[0.12, 0.34, 0.56, 0.78, 0.23, 0.45, 0.67, 0.89]],
    "annsField": "embedding",
    "topK": 5,
    "filter": "category == \"milvus\"",
    "outputFields": ["id", "text", "category"]
  }'
```

## 标量过滤搜索 — 范围过滤

```bash
curl -X POST "http://localhost:19530/v2/vectordb/entities/search" \
  -H "Content-Type: application/json" \
  -d '{
    "collectionName": "test_docs",
    "data": [[0.12, 0.34, 0.56, 0.78, 0.23, 0.45, 0.67, 0.89]],
    "annsField": "embedding",
    "topK": 5,
    "filter": "id >= \"doc_1\" and id <= \"doc_10\"",
    "outputFields": ["id", "text", "category"]
  }'
```

## 标量过滤搜索 — IN 查询

```bash
curl -X POST "http://localhost:19530/v2/vectordb/entities/search" \
  -H "Content-Type: application/json" \
  -d '{
    "collectionName": "test_docs",
    "data": [[0.12, 0.34, 0.56, 0.78, 0.23, 0.45, 0.67, 0.89]],
    "annsField": "embedding",
    "topK": 5,
    "filter": "category in [\"milvus\", \"spring\"]",
    "outputFields": ["id", "text", "category"]
  }'
```

## 标量过滤搜索 — 组合条件

```bash
curl -X POST "http://localhost:19530/v2/vectordb/entities/search" \
  -H "Content-Type: application/json" \
  -d '{
    "collectionName": "test_docs",
    "data": [[0.12, 0.34, 0.56, 0.78, 0.23, 0.45, 0.67, 0.89]],
    "annsField": "embedding",
    "topK": 5,
    "filter": "(category == \"milvus\" or category == \"spring\") and id != \"doc_0\"",
    "outputFields": ["id", "text", "category"]
  }'
```

## 过滤语法总结

| 操作 | 语法 | 示例 |
|------|------|------|
| 等值 | `==` | `category == "milvus"` |
| 不等 | `!=` | `category != "test"` |
| 比较 | `>`, `<`, `>=`, `<=` | `id >= "doc_1"` |
| IN | `in [...]` | `category in ["a", "b"]` |
| 逻辑与 | `and` | `id > "doc_1" and id < "doc_100"` |
| 逻辑或 | `or` | `category == "a" or category == "b"` |
| 逻辑非 | `not` | `not (category == "test")` |

> **注意**：VarChar 类型的主键在过滤时需要用引号包裹，如 `id == "doc_1"`。
