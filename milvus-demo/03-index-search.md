# M3: 索引与向量搜索 - curl 示例

> **前置条件**：已完成 M2 的 Collection 创建和数据插入。

## 创建 HNSW 索引

```bash
curl -X POST "http://localhost:19530/v2/vectordb/indexes/create" \
  -H "Content-Type: application/json" \
  -d '{
    "collectionName": "test_docs",
    "indexParams": [
      {
        "fieldName": "embedding",
        "indexType": "HNSW",
        "metricType": "COSINE",
        "params": {
          "M": 16,
          "efConstruction": 200
        }
      }
    ]
  }'
```

**索引参数说明：**
- `indexType: HNSW`：使用 HNSW 索引算法
- `metricType: COSINE`：余弦相似度度量
- `M: 16`：每个节点的最大连接数
- `efConstruction: 200`：构建索引时的搜索范围

## 加载 Collection 到内存（搜索前必须）

```bash
curl -X POST "http://localhost:19530/v2/vectordb/collections/load" \
  -H "Content-Type: application/json" \
  -d '{"collectionName": "test_docs"}'
```

## 向量搜索

```bash
curl -X POST "http://localhost:19530/v2/vectordb/entities/search" \
  -H "Content-Type: application/json" \
  -d '{
    "collectionName": "test_docs",
    "data": [[0.12, 0.34, 0.56, 0.78, 0.23, 0.45, 0.67, 0.89]],
    "annsField": "embedding",
    "topK": 3,
    "outputFields": ["id", "text", "category"],
    "searchParams": {
      "ef": 64
    }
  }'
```

**参数说明：**
- `data`：查询向量，维度必须与 Collection 一致（本示例 8 维）
- `annsField`：向量字段名（`embedding`）
- `topK`：返回最相似的 K 条结果
- `outputFields`：除向量外的输出字段
- `searchParams.ef`：搜索时的动态候选列表大小

## 查看索引信息

先列出 Collection 的所有索引：

```bash
curl -X POST "http://localhost:19530/v2/vectordb/indexes/list" \
  -H "Content-Type: application/json" \
  -d '{"collectionName": "test_docs"}'
```

再用索引名查看详情：

```bash
curl -X POST "http://localhost:19530/v2/vectordb/indexes/describe" \
  -H "Content-Type: application/json" \
  -d '{
    "collectionName": "test_docs",
    "indexName": "embedding"
  }'
```

> **说明**：创建索引时如果不指定 `indexName`，默认使用字段名作为索引名。

## 返回结果示例

```json
{
  "code": 0,
  "data": [
    {"id": "doc_1", "text": "Milvus 是向量数据库", "category": "milvus", "distance": 0.95},
    {"id": "doc_3", "text": "向量搜索的原理", "category": "search", "distance": 0.87}
  ]
}
```
