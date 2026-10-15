## M5：服务发现与动态配置

### 为什么需要服务发现

M3 中我们给 Node Exporter 配置了静态目标：

```yaml
scrape_configs:
  - job_name: "node"
    static_configs:
      - targets: ["node-exporter:9100"]
```

这种方式在目标固定时没问题，但生产环境中会遇到问题：

- **容器环境**：Pod/容器重启后 IP 变化，每次都要改配置
- **弹性伸缩**：云服务器自动扩缩容，实例数量不固定
- **微服务架构**：服务实例频繁上下线，手动维护不现实

服务发现就是让 Prometheus **自动获取监控目标列表**，不再需要手动写死在配置文件里。

---

### 三种服务发现方式对比

| 方式 | 原理 | 适用场景 | 学习成本 |
|------|------|----------|----------|
| **文件服务发现** | 从 YAML/JSON 文件读取目标 | 目标由外部系统写入文件、多 Prometheus 共享配置 | 低 |
| **Consul 服务发现** | 从 Consul 注册中心查询健康实例 | 已有 Consul 集群、微服务注册/注销 | 中 |
| **Kubernetes 服务发现** | 通过 K8s API 自动发现 Pod/Service | Kubernetes 集群环境 | 高 |

---

### 方式一：文件服务发现（File-based SD）

**原理**：Prometheus 定期读取指定的 YAML/JSON 文件，自动加载其中的监控目标。修改文件后无需重启，`refresh_interval` 到期后自动生效。

**配置示例**（见 `prometheus.yml`）：

```yaml
scrape_configs:
  - job_name: "file_sd_nodes"
    file_sd_configs:
      - files:
          - "targets/nodes.yml"      # 目标文件路径，支持通配符
        refresh_interval: 1m          # 刷新间隔，建议 1-5 分钟
```

**目标文件格式**（见 `targets/nodes.yml`）：

```yaml
- targets:
    - "node1:9100"
    - "node2:9100"
  labels:
    env: "prod"
    role: "application-server"
```

**关键参数说明**：

| 参数 | 含义 | 建议值 |
|------|------|--------|
| `files` | 目标文件路径，支持 `*` 通配符 | 按环境/团队分文件，如 `targets/prod/*.yml` |
| `refresh_interval` | 文件刷新间隔 | 1-5 分钟，太短增加磁盘 IO，太长导致新目标上线延迟 |

**验证方式**：

1. 取消 `prometheus.yml` 中 file_sd 配置的注释
2. 执行 `docker-compose restart prometheus`
3. 访问 Prometheus UI → Status → Targets，查看 `file_sd_nodes` 任务
4. 修改 `targets/nodes.yml`，等待 1 分钟后刷新页面，观察目标变化

---

### 方式二：Consul 服务发现（Consul SD）

**原理**：Consul 是 HashiCorp 的服务注册与发现工具。服务启动时注册到 Consul，停止时自动注销。Prometheus 定期查询 Consul HTTP API，获取当前健康的服务实例列表。

**配置示例**（见 `prometheus.yml`，已注释）：

```yaml
scrape_configs:
  - job_name: "consul_services"
    consul_sd_configs:
      - server: "consul:8500"         # Consul 地址
        datacenter: "dc1"             # 数据中心
        services:                     # 要发现的服务名
          - "node-exporter"
          - "mysql-exporter"
        tags:                         # 按标签过滤
          - "prod"
    relabel_configs:                  # 标签重写（见下方说明）
      - source_labels: ["__meta_consul_service"]
        target_label: "job"
```

**关键参数说明**：

| 参数 | 含义 |
|------|------|
| `server` | Consul 地址，默认端口 8500 |
| `datacenter` | 数据中心名称，Consul 支持多数据中心 |
| `services` | 要发现的服务名列表，对应服务注册时的 name |
| `tags` | 按标签过滤实例，如只采集 prod 环境 |
| `token` | ACL Token，Consul 开启认证时需要 |

**自动生成的标签**（以 `__meta_` 开头，可通过 relabel_configs 转换）：

| 标签 | 含义 |
|------|------|
| `__meta_consul_service` | 服务名（如 node-exporter） |
| `__meta_consul_node` | Consul 节点名 |
| `__meta_consul_tags` | 标签列表，逗号分隔 |
| `__meta_consul_address` | 实例 IP |

**适用场景**：

- 微服务架构，服务实例动态注册/注销
- 已有 Consul 作为服务注册中心
- 需要健康检查：Consul 自动剔除不健康的实例

**本项目说明**：学习阶段不部署 Consul，此配置仅供了解格式。生产环境中，如果已有 Consul 集群，只需将 `server` 改为实际地址即可。

---

### 方式三：Kubernetes 服务发现（Kubernetes SD）

**原理**：Prometheus 通过 Kubernetes API 自动发现集群中的资源（Pod、Service、Node 等）。当 Pod 创建/销毁时，Prometheus 自动调整采集目标，无需任何配置变更。

**配置示例**（见 `prometheus.yml`，已注释）：

```yaml
scrape_configs:
  - job_name: "kubernetes-pods"
    kubernetes_sd_configs:
      - role: pod                     # 发现角色：pod/service/endpoints/node/ingress
        namespaces:
          names:
            - "default"
            - "monitoring"
    relabel_configs:
      # 只采集标注了 prometheus.io/scrape=true 的 Pod
      - source_labels: ["__meta_kubernetes_pod_annotation_prometheus_io_scrape"]
        action: keep
        regex: true
```

**关键参数说明**：

| 参数 | 含义 |
|------|------|
| `role` | 发现角色，可选 `pod` / `service` / `endpoints` / `node` / `ingress` |
| `namespaces` | 限定发现的命名空间，不填则发现所有 namespace |

**常用注解约定**（需在 Pod/Service 上配置）：

| 注解 | 含义 | 示例 |
|------|------|------|
| `prometheus.io/scrape` | 是否采集该目标 | `"true"` |
| `prometheus.io/port` | 采集端口 | `"8080"` |
| `prometheus.io/path` | 采集路径 | `"/metrics"` |

**自动生成的标签**（以 pod role 为例）：

| 标签 | 含义 |
|------|------|
| `__meta_kubernetes_pod_name` | Pod 名称 |
| `__meta_kubernetes_namespace` | 命名空间 |
| `__meta_kubernetes_pod_ip` | Pod IP |
| `__meta_kubernetes_pod_annotation_*` | 所有注解 |
| `__meta_kubernetes_pod_label_*` | 所有标签 |

**适用场景**：

- Kubernetes 集群环境（几乎是 K8s 监控的标准方案）
- Pod IP 频繁变化，手动配置不现实
- 需要按 namespace、label、annotation 灵活筛选目标

**本项目说明**：学习阶段使用 Docker 环境，不部署 Kubernetes，此配置仅供了解格式。生产环境中，Prometheus 通常部署在 K8s 集群内，通过 ServiceAccount 访问 API。

---

### 标签重写（Relabeling）

三种服务发现方式都会生成大量以 `__meta_` 开头的元数据标签。这些标签不会直接附加到指标上，需要通过 `relabel_configs` 转换为正式标签。

**常用操作**：

```yaml
relabel_configs:
  # 保留：只保留匹配的目标
  - source_labels: ["__meta_consul_tags"]
    regex: ".*,prod,.*"
    action: keep

  # 替换：将元数据标签转为正式标签
  - source_labels: ["__meta_consul_service"]
    target_label: "job"

  # 删除：丢弃不需要的标签
  - regex: "__meta_.*"
    action: labeldrop
```

**注意事项**：

- `relabel_configs` 在采集前执行，修改的是目标标签
- `metric_relabel_configs` 在采集后执行，修改的是指标标签
- 标签重写规则过于复杂会影响性能，尽量保持简单

---

### 学习建议

1. **掌握文件服务发现**：这是最简单、最实用的方式，Docker 环境即可验证
2. **了解 Consul/K8s 配置格式**：知道参数含义，生产环境需要时能快速上手
3. **理解标签重写**：三种方式都会用到 relabel_configs，是服务发现的核心技能
