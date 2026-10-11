# Milvus 向量数据库快速上手

## 环境搭建

### 安装方式

Milvus 有三种部署模式，学习阶段用 **Standalone（单机模式）** 即可：

| 模式 | 适用场景 | 组件数 |
|------|---------|--------|
| Milvus Lite | 本地开发/测试，嵌入式运行，无需 Docker | 0（Python import 即用） |
| Standalone | 学习、开发、小规模生产 | 3（etcd + MinIO + Milvus） |
| Cluster | 大规模生产 | 5+（增加 Proxy、Query Node、Data Node、Index Node 等） |

**推荐方式：Docker Compose 部署 Standalone**

#### 前置依赖

- Docker Desktop（Windows/macOS）或 Docker Engine（Linux）
- 内存 >= 8GB

#### 安装步骤

项目已内置官方 docker-compose 配置文件（Milvus 3.0.2），位于 `01-env-setup/` 目录。

```bash
# 进入配置目录
cd 01-env-setup

# 启动（不含 Attu）
docker compose up -d

# 或启动含 Attu 可视化管理的版本
docker compose -f docker-compose-attu.yml up -d

# 查看状态
docker compose ps
```

预期输出：三个容器（etcd、minio、milvus-standalone）状态均为 Up。

### 版本选择

**推荐 Milvus 3.0.x**（当前最新稳定版本），原因：
- 3.0.x 是 2026 年最新版本，性能更优，功能更完善
- 支持全文检索、多向量字段、稀疏向量等高级特性
- Java SDK 和 Spring AI Milvus VectorStore 完全兼容

### 安装验证

```bash
# 检查三个容器是否全部 Running
docker compose ps
# 预期输出：三个容器（etcd、minio、milvus-standalone）状态均为 Up

# 检查 Milvus 端口是否监听（默认 19530）
# Windows PowerShell:
netstat -ano | findstr 19530
# Linux/macOS:
# ss -tlnp | grep 19530

# 通过 Attu Web UI 验证（如果部署了 Attu）
# 浏览器访问 http://localhost:8000
```

### 平台差异/常见坑

- **Windows**：Docker Desktop 需要开启 WSL2 后端；确保在 BIOS 中启用了虚拟化
- **内存不足**：MinIO 容器启动较慢（约 30s），Milvus 容器会等 MinIO 就绪后才启动，如果看到 Milvus 容器反复重启，先检查内存是否 >= 8GB
- **端口冲突**：Milvus 默认使用 19530（gRPC）、9091（metrics）、8080（Attu），确认这些端口未被占用
- **数据持久化**：docker-compose 默认将数据挂载到 `./volumes/` 目录，删除容器不会丢数据；要彻底清理需要 `docker compose down -v` 并删除 volumes 目录

---

## M1:核心概念与选型

### 概念解释

**Milvus 是什么**：专为 AI 应用设计的开源向量数据库，核心解决"高维向量的高效相似性搜索"问题。在 RAG、推荐系统、以图搜图等场景中，Milvus 负责存储 Embedding 向量并快速找到最相似的结果。

**核心概念对照表**（和关系型数据库类比）：

| Milvus 概念 | 关系型数据库类比 | 说明 |
|-------------|----------------|------|
| Collection | Table | 存储向量的表，有固定的 Schema |
| Field | Column | 字段，支持标量字段（INT/VARCHAR）和向量字段（FLOAT_VECTOR） |
| Partition | 表的分区 | Collection 内的数据分区，加速查询（可选） |
| Segment | 数据段 | Milvus 内部自动管理的数据分片，用户不直接操作 |
| Index | 索引 | 向量的 ANN 索引，加速相似性搜索 |
| Entity/Row | Row | 一行数据，包含标量字段 + 向量字段 |

**向量搜索的本质**：将文本/图片等非结构化数据通过 Embedding 模型转成高维浮点向量，存入 Milvus，查询时对 query 也做 Embedding，然后在向量空间中找距离最近（最相似）的 Top-K 结果。

### 同类技术选型对比表

| 维度 | Milvus | Qdrant | Weaviate | pgvector |
|------|--------|--------|----------|----------|
| 定位 | 专用向量数据库，云原生分布式 | Rust 编写的高性能向量数据库 | AI-native 向量数据库，内置模块化 | PostgreSQL 扩展，关系型+向量 |
| 吞吐量 | 十万级 QPS（集群模式） | 万级 QPS（单机性能极强） | 万级 QPS | 千级 QPS（受限于 PG） |
| 数据规模 | 百亿级向量 | 十亿级向量 | 十亿级向量 | 千万级向量 |
| 部署复杂度 | 中等（需 etcd + MinIO） | 低（单二进制） | 中等 | 低（PG 扩展） |
| 混合搜索 | 支持（标量过滤 + 向量搜索） | 支持 | 支持 | 支持（SQL WHERE + 向量） |
| 全文检索 | 2.5.x 原生支持 | 1.9+ 支持 | 内置 | 通过 PG tsvector |
| 多语言 SDK | 官方支持 Java/Python/Go/Node | 官方支持 Java/Python/Go/Rust | 官方支持 Java/Python/Go | 通过 JDBC 直接用 |
| REST API | 支持 | 支持 | 支持 | 不支持（只有 SQL） |
| 适用场景 | 大规模 RAG、推荐、搜索 | 中小规模高性能场景 | 需要内置 NLP 管道的场景 | 已有 PG 基础设施、数据量小 |
| 不适用 | 小数据量（杀鸡用牛刀） | 超大规模分布式 | 纯向量存储（过重） | 大规模向量（性能瓶颈） |
| 学习曲线 | 中等 | 低 | 较高 | 低（会 SQL 即可） |

**选型结论**：
- **数据量大、需要分布式** → Milvus（综合最强，生态完善）
- **追求单机极致性能、数据量适中** → Qdrant
- **已有 PostgreSQL、数据量千万以内** → pgvector（最少额外组件）
- **需要内置 NLP 能力（向量化、分块）** → Weaviate

### 生产高频功能 → 学习模块清单

| 模块 | 为什么必学 | 核心要点 |
|------|-----------|---------|
| M1: 核心概念与选型 | 理解 Milvus 在 AI 技术栈中的位置和选型依据 | Collection/Field/Index 概念、与关系型数据库类比 |
| M2: Schema 设计与数据操作 | 每次接入 Milvus 的第一步就是设计 Schema 和写入数据 | 字段类型选择、主键设计、Insert/Upsert/Delete |
| M3: 索引与向量搜索 | 没有索引就没有可用的搜索性能，这是 Milvus 的核心 | HNSW/IVF 索引、COSINE/L2 度量、nprobe/ef 调参 |
| M4: 高级搜索 | 生产环境几乎都用混合搜索（向量+标量过滤） | 标量过滤表达式、混合检索 RRF、Range Search |
| M5: Spring AI 集成 | Java 生态做 RAG 应用的标准方式 | VectorStore 抽象、自动 Embedding、RAG 管道 |
| M6: 运维与调优 | 上线必备：监控、性能调优、一致性等级 | Attu 管理、Compaction、分区策略、一致性等级 |

### 可跳过的功能

| 功能 | 为什么可以跳过 |
|------|--------------|
| Milvus Cluster 模式 | 学习阶段不需要，Standalone 足够；集群部署是运维阶段的事 |
| 多向量字段（Multi-Vector） | 高级特性，单向量字段覆盖 90% 的 RAG 场景 |
| 稀疏向量（Sparse Vector） | 主要用于混合检索中的 BM25 分支，初学阶段用稠密向量即可 |
| 全文检索（Full Text Search） | 2.5.x 新特性，生产中使用率还不高，先用外部 ES 做全文检索更成熟 |
| RBAC 权限管理 | 生产环境需要，但不影响理解核心功能 |
| Data Import/Export（bulk load） | 数据迁移场景才用，日常开发用不到 |

---

## M2:Schema 设计与数据操作

### 概念解释

Milvus 的 Schema 定义了一个 Collection 的结构，必须包含：
- **至少一个主键字段**（INT64 或 VARCHAR）
- **至少一个向量字段**（FLOAT_VECTOR 或 SPARSE_FLOAT_VECTOR）
- **零个或多个标量字段**（BOOL、INT8/16/32/64、FLOAT、DOUBLE、VARCHAR、JSON、ARRAY）

**为什么 Schema 设计重要**：Milvus 是 Schema-Fixed 的，Collection 创建后不能修改字段结构（只能加字段），所以设计阶段要想清楚。

### 核心语法/命令

**字段类型速查**：

| 类型 | 用途 | 示例 |
|------|------|------|
| INT64 / VARCHAR | 主键 | 文档 ID |
| FLOAT_VECTOR | 稠密向量（最常用） | Embedding 向量（768/1024/1536 维） |
| SPARSE_FLOAT_VECTOR | 稀疏向量 | BM25 特征、SPLADE 向量 |
| VARCHAR | 文本 | 原始文档内容 |
| JSON | 结构化元数据 | 标签、来源、时间戳 |
| INT32/INT64 | 数值标量 | 分类 ID、评分 |

**距离度量方式**：

| 度量 | 公式特点 | 适用场景 |
|------|---------|---------|
| COSINE | 余弦相似度，归一化后的内积 | 文本 Embedding（最常用） |
| L2 | 欧氏距离 | 图像特征 |
| IP | 内积 | 归一化后的向量等价 COSINE |

### 示例

📂 **完整 curl 示例**：[02-schema-crud.md](02-schema-crud.md)

包含：创建 Collection、插入数据、查询、Upsert、删除

### 常见陷阱/最佳实践

- **向量维度必须一致**：同一个 FLOAT_VECTOR 字段，所有向量的维度必须相同（如都用 768 维），插入时维度不匹配会报错
- **主键选择**：推荐用 VARCHAR 类型主键（如业务 ID），不要用自增 INT64（不利于 upsert 和去重）
- **文本冗余存储**：建议把原始文本也存进 Collection（VARCHAR 字段），搜索时直接返回原文，避免回查业务库
- **动态 Schema**：Milvus 2.5.x 支持 `$meta` 动态字段（JSON），适合元数据结构不固定的场景，但性能略低于固定字段

### 实践 Q&A

> 以下是动手操作中产生的疑问和踩坑记录。

<details>
<summary><b>Q: 创建 Collection 的字段只有 FloatVector 和 VarChar 两种吗？</b></summary>

**A:** 不是，Milvus 支持多种字段类型：

- **主键类型**：Int64、VarChar
- **向量类型**：FloatVector（稠密向量）、BinaryVector、Float16Vector、SparseFloatVector（稀疏向量）
- **标量类型**：Bool、Int8/16/32/64、Float、Double、VarChar、JSON、Array

生产环境常用组合：VarChar 主键 + 几个标量字段（用于过滤）+ FloatVector 向量字段。

</details>

<details>
<summary><b>Q: 向量字段的维度有哪些可选？</b></summary>

**A:** 维度不是固定的，可以自定义任意值。维度取决于你用的 Embedding 模型：

| 维度 | 来源 |
|------|------|
| 768 | BERT、很多中文 Embedding 模型 |
| 1024 | 部分开源模型 |
| 1536 | OpenAI text-embedding-3-small |
| 3072 | OpenAI text-embedding-3-large |

学习测试时可以随便设（如 8、128），生产环境必须和模型对齐。

</details>

<details>
<summary><b>Q: 插入数据时报错 `[]float32 size 8 doesn't equal to vector dimension 768`？</b></summary>

**A:** 向量维度不匹配。Collection 定义的是 768 维，但插入的是 8 维向量。

解决：维度必须一致。要么重建 Collection 用 8 维（学习测试），要么用真实的 768 维向量（生产环境）。

</details>

<details>
<summary><b>Q: 查询时报错 `failed to query: collection not loaded`？</b></summary>

**A:** Milvus 查询/搜索前必须先把 Collection 加载到内存。

执行加载命令：
```bash
curl -X POST "http://localhost:19530/v2/vectordb/collections/load" \
  -H "Content-Type: application/json" \
  -d '{"collectionName": "test_docs"}'
```

顺序是：创建 Collection → 插入数据 → **创建索引** → **加载到内存** → 搜索/查询。

</details>

<details>
<summary><b>Q: 查看 Collection 信息显示 `indexes: []` 和 `index not found`？</b></summary>

**A:** 正常的。创建 Collection 后不会自动建索引，需要手动创建。

按 M3 步骤先创建 HNSW 索引，再 load Collection：
```bash
# 1. 创建索引
curl -X POST "http://localhost:19530/v2/vectordb/indexes/create" \
  -H "Content-Type: application/json" \
  -d '{
    "collectionName": "test_docs",
    "indexParams": [{
      "fieldName": "embedding",
      "indexType": "HNSW",
      "metricType": "COSINE",
      "params": {"M": 16, "efConstruction": 200}
    }]
  }'

# 2. 加载到内存
curl -X POST "http://localhost:19530/v2/vectordb/collections/load" \
  -H "Content-Type: application/json" \
  -d '{"collectionName": "test_docs"}'
```

</details>

---

## M3:索引与向量搜索

### 概念解释

**为什么需要索引**：向量搜索本质是在高维空间找最近邻，暴力搜索（FLAT）的时间复杂度是 O(n×d)，数据量大时不可接受。ANN（Approximate Nearest Neighbor）索引通过牺牲少量精度换取数量级的性能提升。

**四种主流索引对比**：

| 索引类型 | 原理 | 构建速度 | 搜索精度 | 内存占用 | 适用场景 |
|---------|------|---------|---------|---------|---------|
| FLAT | 暴力穷举 | 无需构建 | 100% | 低 | 数据量 < 100 万，精度要求极高 |
| IVF_FLAT | 倒排文件 + 暴力搜索 | 快 | 高（调大 nprobe） | 中 | 百万~千万级，通用场景 |
| IVF_SQ8 | 倒排文件 + 标量量化 | 快 | 中高 | 低（1/4） | 千万级，内存有限 |
| HNSW | 分层可导航小世界图 | 慢（耗内存） | 最高 | 高 | 对精度要求高、内存充足 |

**关键搜索参数**：

- **nprobe**（IVF 系列）：搜索时探测的聚类中心数量，越大精度越高、速度越慢
- **ef**（HNSW）：搜索时的候选列表大小，越大精度越高、速度越慢
- **topK**：返回最相似的 K 个结果

### 核心语法/命令

```
# 创建索引的核心参数
index_type: IVF_FLAT / IVF_SQ8 / HNSW / FLAT
metric_type: COSINE / L2 / IP
params:
  nlist: 1024        # IVF 系列的聚类中心数（建索引时）
  M: 16              # HNSW 的邻居数（建索引时）
  efConstruction: 200 # HNSW 的构建参数（建索引时）
```

### 示例

📂 **完整 curl 示例**：[03-index-search.md](03-index-search.md)

包含：创建 HNSW 索引、加载 Collection、向量搜索

### 常见陷阱/最佳实践

- **索引选择建议**：不确定就用 **HNSW**（精度最高），内存紧张用 **IVF_SQ8**
- **nprobe/ef 调优**：先设小值测速度，再逐步增大到精度满足需求，找到速度和精度的平衡点
- **搜索前必须建索引**：没有索引的 Collection 搜索会走暴力遍历，数据量大时超慢
- **COSINE vs L2**：如果你的 Embedding 模型输出已经归一化（如 OpenAI text-embedding-3-small），COSINE 和 IP 等价，用 COSINE 语义更清晰

### 实践 Q&A

> 以下是动手操作中产生的疑问和踩坑记录。

<details>
<summary><b>Q: 查看索引信息时报错 `missing required parameters: IndexName`？</b></summary>

**A:** describe index 接口需要 `indexName` 参数，不是 `fieldName`。

正确用法：
```bash
# 先列出所有索引
curl -X POST "http://localhost:19530/v2/vectordb/indexes/list" \
  -H "Content-Type: application/json" \
  -d '{"collectionName": "test_docs"}'

# 用索引名查看详情
curl -X POST "http://localhost:19530/v2/vectordb/indexes/describe" \
  -H "Content-Type: application/json" \
  -d '{
    "collectionName": "test_docs",
    "indexName": "embedding"
  }'
```

创建索引时如果不指定 `indexName`，默认使用字段名作为索引名。

</details>

<details>
<summary><b>Q: 为什么索引需要手动创建，而不是插入数据后自动建立？</b></summary>

**A:** 这样设计有几个原因：

1. **索引构建很耗资源**：HNSW 等索引构建需要大量 CPU 和内存。如果每次插入都自动更新索引，写入性能会很差。分离开来可以批量插入数据（快），然后一次性构建索引。

2. **索引类型和参数需要用户决定**：不同场景需要不同索引（HNSW/IVF_FLAT/IVF_SQ8），参数（M、efConstruction）也不一样，自动创建没法猜。

3. **生产环境的典型流程**：
   - 创建 Collection → 创建索引（指定参数）→ 批量插入数据 → 加载到内存 → 开始服务
   - 或者：创建 Collection → 批量插入数据 → 创建索引 → 加载到内存 → 开始服务

4. **避免增量更新的复杂性**：分布式环境下，每次插入都更新索引要保证一致性会很复杂，批量构建更简单高效。

**类比**：MySQL 的 B+Tree 索引会自动维护，但那是轻量级索引。向量索引（尤其是 HNSW 这种图结构）构建成本高得多，不适合自动增量维护。

</details>

---

## M4:高级搜索（混合搜索与过滤）

### 概念解释

生产中最常用的搜索模式是**混合搜索**：在向量相似性搜索的基础上，叠加标量字段的过滤条件。

例如："搜索与 query 最相似的文档，但只返回 category = 'tech' 且 score > 0.8 的结果"。

**Milvus 支持的搜索模式**：

| 搜索模式 | 说明 | 使用频率 |
|---------|------|---------|
| 向量搜索（ ANN Search） | 纯向量相似性搜索 | 高 |
| 标量过滤搜索（Filtered Search） | 向量搜索 + WHERE 条件 | **最高**（生产最常用） |
| 全文检索 | 基于 BM25 的文本搜索 | 中（2.5.x 新增） |
| 混合检索（Hybrid Search） | 多路搜索 + 融合排序（RRF/加权） | 中 |
| 范围搜索（Range Search） | 返回距离在指定范围内的结果 | 低 |
| 分组搜索（Grouping Search） | 按字段去重，每组只返回最佳 | 低 |

### 核心语法/命令

**标量过滤表达式语法**（类 SQL）：

```
# 等值
category == "tech"

# 范围
score > 0.8

# 逻辑组合
category == "tech" and publish_year >= 2024

# IN 查询
category in ["tech", "ai", "ml"]

# LIKE 模糊
title like "%向量数据库%"

# JSON 字段
meta["author"] == "张三"
```

### 示例

📂 **完整 curl 示例**：[04-advanced-search.md](04-advanced-search.md)

包含：纯向量搜索、等值过滤、范围过滤、IN 查询、组合条件

### 常见陷阱/最佳实践

- **过滤字段要建索引**：对标量过滤字段建索引（如 IVF_FLAT 的标量索引），否则先全量扫描再过滤，性能差
- **过滤顺序**：Milvus 默认先过滤再搜索（pre-filter），数据量大时过滤条件太宽松会退化为全量搜索
- **混合检索的 RRF**：多路搜索（如向量 + 全文）用 Reciprocal Rank Fusion（RRF）融合排序，比简单加权更稳定
- **topK 设置**：生产环境通常 topK = 5~20，太大反而引入噪声

---

## M5:Spring AI + Milvus 集成

### 概念解释

Spring AI 提供了 `VectorStore` 抽象层，Milvus 是其官方支持的实现之一。通过 Spring AI 集成，你不需要直接操作 Milvus SDK，而是用 Spring 的方式完成：

- **文档向量化**：自动调用 Embedding 模型将文本转成向量
- **存储**：自动将文档 + 向量写入 Milvus
- **检索**：自动将 query 向量化并在 Milvus 中搜索

**核心流程**（本 Demo 只做向量存储和检索，不涉及 LLM）：

```
文档 → Embedding Model → 向量 → Milvus 存储
                                    ↓
用户 Query → Embedding Model → Query 向量 → Milvus 搜索 → 返回匹配文档
```

> **说明**：如果需要 RAG（检索增强生成），在搜索后再把结果喂给 LLM 生成回答。那是应用层的事，Milvus 只负责向量存储和检索。

### 前置准备：申请 Embedding 模型 API Key

**本项目只需要 Embedding 模型**（文本转向量），不需要 Chat 模型。

**使用智谱AI**
- 控制台：https://open.bigmodel.cn/
- 模型：embedding-3（2048 维）
- base-url：`https://open.bigmodel.cn/api/paas/v4`

**申请步骤**：
1. 访问 https://open.bigmodel.cn/ 注册账号
2. 进入「控制台」→「API Key」→ 创建 API Key
3. 新用户有体验金，Embedding 调用成本很低
4. 推荐购买「Embedding-3 资源包」，比按量付费更划算，学习阶段够用

### 配置

```yaml
spring:
  ai:
    milvus:
      client:
        host: localhost
        port: 19530
      collection-name: spring_ai_rag
      embedding-dimension: 2048
    openai:
      api-key: 你的智谱apikey
      base-url: https://open.bigmodel.cn/api/paas/v4
      embedding:
        options:
          model: embedding-3
```

### API 接口

启动后可以用以下接口测试：

**1. 文档摄入**
```bash
curl -X POST http://localhost:8080/api/milvus/ingest
```
将 knowledge-base.txt 分块、向量化后存入 Milvus。

**2. 向量搜索**
```bash
curl "http://localhost:8080/api/milvus/search?query=Milvus是什么&topK=5"
```
返回最相似的 5 条文档。

**3. 带阈值的搜索**
```bash
curl "http://localhost:8080/api/milvus/search/threshold?query=Milvus&topK=5&threshold=0.7"
```
只返回相似度 >= 0.7 的结果。

### 常见陷阱/最佳实践

- **embedding-dimension 必须匹配**：配置中的维度必须和你使用的 Embedding 模型输出维度一致（如 text-embedding-3-small 是 1536 维，智谱 embedding-3 是 2048 维）
- **base-url 不要带 /embeddings**：Spring AI 会自动追加 `/embeddings` 路径。正确写法：`https://open.bigmodel.cn/api/paas/v4`，错误写法：`https://open.bigmodel.cn/api/paas/v4/embeddings`
- **API Key 不要提交到代码**：用环境变量 `${OPENAI_API_KEY}` 或本地配置文件，不要硬编码
- **Collection 必须手动创建**：生产环境禁止启动时自动建表，应该在部署阶段手动创建 Collection 并建好索引（参考 M2/M3 的 curl 命令）
- **文档分块**：存入 Milvus 前，文档需要分块（Chunk），通常 512~1024 token 一块，块之间可以有 10%~20% 重叠

### 实践 Q&A

**Q1: API Key 在代码里怎么生效的？为什么不需要手动注入 EmbeddingModel？**

启动时 Spring AI 根据 yml 自动创建 `EmbeddingModel` Bean（用 api-key + base-url + model 配置），然后注入到 `MilvusVectorStore` 里。你代码里只需要注入 `VectorStore`，调用 `add()` 或 `similaritySearch()` 时，VectorStore 内部会自动调用 EmbeddingModel 把文本转向量，EmbeddingModel 再用 api-key 去调智谱API。整个过程对开发者透明，这就是 Spring AI 的设计思想——把 AI 模型调用封装成和数据库一样的基础设施。

**Q2: 搜索结果怎么返回相似度分数？**

Spring AI 的 Document 对象的 metadata 里包含 `distance` 字段（相似度分数）。在 Controller 里通过 `doc.getMetadata().get("distance")` 获取并返回给前端。

**Q3: initialize-schema: true 是什么意思？生产环境能用吗？**

这个配置让 Spring AI 在启动时自动创建 Collection（如果不存在）。教学环境方便测试，但**生产环境禁止使用**，应该在部署阶段手动创建 Collection 并建好索引（参考 M2/M3 的 curl 命令），避免启动时自动 DDL 带来的风险。

> 📂 对应代码：`05-spring-ai-rag/`

---

## M6:运维与调优

### 概念解释

生产环境中，Milvus 的运维重点关注：**监控**（Attu + Prometheus）、**性能调优**（索引参数、资源分配）、**数据管理**（分区、TTL、Compaction）。

### Attu 可视化管理

Attu 是 Milvus 官方 Web UI，提供：
- Collection/Partition 管理
- 数据浏览与搜索测试
- 索引状态查看
- 系统监控（QPS、延迟、节点状态）

部署方式见 `01-env-setup/docker-compose-attu.yml`。

### 性能调优要点

| 调优方向 | 具体措施 | 效果 |
|---------|---------|------|
| 索引选择 | 根据数据量和精度需求选择 HNSW / IVF_SQ8 | 搜索速度提升 10~100 倍 |
| 搜索参数 | 调整 nprobe / ef 平衡精度和速度 | 精度不变时速度提升 2~5 倍 |
| 分区策略 | 按业务维度（如租户、类别）分区 | 查询时跳过无关分区，速度提升 |
| 资源分配 | Query Node 增加内存、Index Node 增加 CPU | 支撑更大数据量和更高并发 |
| Compaction | 定期触发（Milvus 也会自动执行） | 清理删除数据，回收空间 |

### 一致性等级

Milvus 提供四种一致性等级，影响搜索时能否读到最新写入的数据：

| 等级 | 说明 | 适用场景 |
|------|------|---------|
| Strong | 强一致，写入立即可读 | 对实时性要求极高的场景 |
| Bounded | 有界延迟，毫秒级可见 | **默认推荐**，兼顾实时性和性能 |
| Session | 会话一致，同一客户端写入对自己立即可见 | 用户交互场景 |
| Eventually | 最终一致，延迟秒级 | 离线分析、批量处理 |

### 常见陷阱/最佳实践

- **不要用 Strong 一致性做默认值**：Strong 会显著降低搜索性能，绝大多数场景 Bounded 就够了
- **监控先行**：上线前配好 Prometheus + Grafana，关注 QPS、P99 延迟、内存使用率
- **数据清理**：定期 Compaction 清理已删除数据，否则搜索性能会逐渐下降
- **批量写入**：插入数据时用 batch insert（一次 1000~5000 条），不要逐条插入

> 📂 对应代码：`01-env-setup/docker-compose-attu.yml`
