# M2: Schema 设计与数据操作 - curl 示例

> **说明**：本示例使用 8 维向量方便测试。生产环境根据 Embedding 模型调整维度（如 OpenAI text-embedding-3-small 用 1536 维，BERT 用 768 维）。

## 创建 Collection（自定义 Schema）

```bash
curl --location 'http://localhost:19530/v2/vectordb/collections/create' \
--header 'Content-Type: application/json' \
--data '{
    "collectionName": "test_docs",
    "schema": {
      "autoID": false,
      "enableDynamicField": false,
      "fields": [
        {
          "fieldName": "id",
          "dataType": "VarChar",
          "isPrimary": true,
          "elementTypeParams": {
            "max_length": 64
          }
        },
        {
          "fieldName": "text",
          "dataType": "VarChar",
          "elementTypeParams": {
            "max_length": 2048
          }
        },
        {
          "fieldName": "category",
          "dataType": "VarChar",
          "elementTypeParams": {
            "max_length": 64
          }
        },
        {
          "fieldName": "embedding",
          "dataType": "FloatVector",
          "elementTypeParams": {
            "dim": 8
          }
        }
      ]
    }
  }'
```

**Schema 说明：**
- `autoID: false`：主键不自动生成，需手动指定
- `enableDynamicField: false`：不启用动态字段，只使用预定义字段
- `isPrimary: true`：标识主键字段
- `elementTypeParams`：字段参数配置
  - `max_length`：VarChar 类型最大长度
  - `dim`：FloatVector 向量维度

> **教学说明**：这个 Schema 设计适合语义搜索场景：
> - `id`：文档唯一标识（VarChar 主键）
> - `text`：文档内容（用于展示）
> - `category`：文档分类（用于过滤搜索）
> - `embedding`：文档向量（用于相似度计算）

## 插入数据

```bash
curl -X POST "http://localhost:19530/v2/vectordb/entities/insert" \
  -H "Content-Type: application/json" \
  -d '{
    "collectionName": "test_docs",
    "data": [
      {
        "id": "doc_1",
        "text": "Milvus 是专为 AI 应用设计的开源向量数据库",
        "category": "milvus",
        "embedding": [0.12, 0.34, 0.56, 0.78, 0.23, 0.45, 0.67, 0.89]
      },
      {
        "id": "doc_2",
        "text": "HNSW 索引通过分层图结构实现高效的近似最近邻搜索",
        "category": "index",
        "embedding": [0.56, 0.78, 0.90, 0.12, 0.34, 0.56, 0.78, 0.90]
      },
      {
        "id": "doc_3",
        "text": "Spring AI 提供了 VectorStore 抽象层，简化向量数据库集成",
        "category": "spring",
        "embedding": [0.11, 0.22, 0.33, 0.44, 0.55, 0.66, 0.77, 0.88]
      }
    ]
  }'
```

## 查询数据（按主键过滤）

```bash
curl -X POST "http://localhost:19530/v2/vectordb/entities/query" \
  -H "Content-Type: application/json" \
  -d '{
    "collectionName": "test_docs",
    "filter": "id == \"doc_1\"",
    "outputFields": ["id", "text", "category"]
  }'
```

## Upsert（更新或插入）

```bash
curl -X POST "http://localhost:19530/v2/vectordb/entities/upsert" \
  -H "Content-Type: application/json" \
  -d '{
    "collectionName": "test_docs",
    "data": [{
      "id": "doc_1",
      "text": "【已更新】Milvus 是最流行的开源向量数据库",
      "category": "milvus",
      "embedding": [0.12, 0.34, 0.56, 0.78, 0.23, 0.45, 0.67, 0.89]
    }]
  }'
```

## 删除数据

```bash
curl -X POST "http://localhost:19530/v2/vectordb/entities/delete" \
  -H "Content-Type: application/json" \
  -d '{
    "collectionName": "test_docs",
    "filter": "id == \"doc_2\""
  }'
```

## 查看 Collection 信息

```bash
curl -X POST "http://localhost:19530/v2/vectordb/collections/describe" \
  -H "Content-Type: application/json" \
  -d '{
    "collectionName": "test_docs"
  }'
```

## 删除 Collection

```bash
curl -X POST "http://localhost:19530/v2/vectordb/collections/drop" \
  -H "Content-Type: application/json" \
  -d '{
    "collectionName": "test_docs"
  }'
```
