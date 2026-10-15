# Prometheus 快速上手

## 环境搭建

### 安装方式

**Docker（推荐）：**

项目已提供 docker-compose 配置文件，位于项目根目录。

**启动：**
```bash
cd prometheus-demo
docker-compose up -d
```

**按模块学习：**

`prometheus.yml` 已包含所有模块的配置，用注释标记。学习对应模块时，取消注释即可：

- **M1**：默认配置（监控自身），直接启动
- **M3**：取消对应 Exporter 的注释（如 Node Exporter、Windows Exporter）
- **M4**：取消 `rule_files` 和 `alerting` 的注释
- **M5**：取消服务发现配置的注释

修改配置后，重新加载：
```bash
docker-compose restart
```

**停止：**
```bash
docker-compose down
```

**停止并删除数据：**
```bash
docker-compose down -v
```

### 版本选择

**推荐版本：3.13.0 LTS（长期支持版）**

- 支持到 2027年7月31日
- 稳定性高，适合生产环境
- 包含最新特性和安全修复

### 安装验证

启动后访问 http://localhost:9090，能看到 Prometheus Web UI 即安装成功。

验证指标数据：
```bash
# 浏览器访问或 curl
curl http://localhost:9090/metrics
```

预期输出：大量 `# HELP` 和 `# TYPE` 开头的指标数据。

### 常见坑

**Docker 注意事项：**
- prometheus.yml 配置文件已在项目根目录，与 docker-compose.yml 同级
- 数据持久化已配置 volume，容器重启数据不会丢失
- Windows 用户需确保 Docker Desktop 已启动

**通用配置：**
- 配置文件使用 YAML 格式，注意缩进（空格，不能用 Tab）
- 默认端口：9090（Web UI 和 API）
- 数据存储：默认本地存储，可配置远程存储

---

## M1：核心概念与架构

### 概念解释

**Prometheus 是什么：**
开源的系统监控和告警工具包，最初由 SoundCloud 开发，现为 CNCF 项目。

**核心特点：**
- **多维数据模型**：基于时间序列数据，每个指标有多个标签（label）
- **Pull 模式**：主动从目标拉取指标数据（也支持 Push Gateway 推送模式）
- **服务发现**：自动发现监控目标（Kubernetes、Consul、文件等）
- **强大的查询语言**：PromQL 支持丰富的聚合和计算
- **告警机制**：基于 PromQL 表达式定义告警规则

**架构组件：**
1. **Prometheus Server**：核心组件，负责数据采集、存储和查询
2. **Exporters**：将第三方系统的指标转换为 Prometheus 格式
3. **Push Gateway**：支持短生命周期任务推送指标
4. **Alertmanager**：处理告警（去重、分组、路由、发送）
5. **Web UI**：内置的查询和可视化界面

### 核心语法/配置

**prometheus.yml 核心配置项：**

```yaml
global:
  scrape_interval: 15s        # 采集间隔
  evaluation_interval: 15s    # 规则评估间隔

scrape_configs:
  - job_name: "prometheus"    # 监控自身
    static_configs:
      - targets: ["localhost:9090"]
```

### 示例

**监控自身配置：**

当前 `prometheus.yml` 已包含此配置，直接启动即可。

启动后访问 http://localhost:9090/targets 查看采集目标状态。

### 常见陷阱/最佳实践

**陷阱：**
- `scrape_interval` 设置过小（如 1s）会导致性能问题
- 忘记配置 `evaluation_interval` 导致告警规则不生效
- 标签命名不规范（应使用 snake_case）

**最佳实践：**
- 采集间隔建议 15s-60s
- 使用标签区分环境（env=prod/dev）和实例
- 定期清理过期数据（配置 `storage.tsdb.retention.time`）

---

## M2：PromQL 查询语言

### 概念解释

**PromQL（Prometheus Query Language）** 是 Prometheus 的查询语言，用于：
- 查询时间序列数据
- 数据聚合和计算
- 定义告警规则
- 在 Grafana 中创建面板

**核心概念：**
- **即时查询**：返回某个时间点的值
- **范围查询**：返回一段时间内的数据
- **向量**：一组时间序列（瞬时向量 / 范围向量）
- **标量**：单个数值
- **字符串**：临时值（不常用）

### 核心语法/命令

**基础查询：**

```promql
# 查询所有指标
{__name__=~".+"}

# 按名称查询
http_requests_total

# 按标签过滤
http_requests_total{method="GET"}
http_requests_total{method="GET", status="200"}

# 标签正则匹配
http_requests_total{method=~"GET|POST"}
http_requests_total{method!~"DELETE"}

# 范围向量（查询最近5分钟的数据）
http_requests_total[5m]
```

**聚合操作：**

```promql
# sum：求和
sum(http_requests_total)
sum by (method) (http_requests_total)
sum without (instance) (http_requests_total)

# avg：平均值
avg(node_cpu_seconds_total{mode="idle"})

# rate：计算每秒增长率（最常用）
rate(http_requests_total[5m])

# increase：计算增长量
increase(http_requests_total[1h])

# histogram_quantile：计算分位数
histogram_quantile(0.95, rate(http_request_duration_seconds_bucket[5m]))
```

### 示例

**查询 CPU 使用率：**
```promql
100 - (avg by (instance) (rate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)
```

**查询内存使用率：**
```promql
(1 - node_memory_available_bytes / node_memory_total_bytes) * 100
```

**查询 QPS（每秒请求数）：**
```promql
sum(rate(http_requests_total[5m]))
```

### 常见陷阱/最佳实践

**陷阱：**
- 直接使用计数器（counter）而不使用 `rate()` 或 `increase()`
- 范围向量时间窗口设置过短（小于 2 倍采集间隔）
- 忽略标签导致查询结果包含不需要的数据

**最佳实践：**
- 计数器类型必须使用 `rate()` 或 `increase()`
- 范围向量时间窗口至少是采集间隔的 2-4 倍
- 使用 `by` 或 `without` 明确聚合维度

> 📂 对应代码：`02-promql-examples.promql`

---

## M3：Exporter 与数据采集

### 概念解释

**Exporter** 是将第三方系统的指标转换为 Prometheus 格式的桥接组件。

**常见 Exporter：**
- **Node Exporter**：Linux 系统指标（CPU、内存、磁盘、网络），端口 9100
- **Windows Exporter**：Windows 系统指标，端口 9182
- **MySQL Exporter**：MySQL 数据库指标，端口 9104
- **Redis Exporter**：Redis 指标，端口 9121
- **Blackbox Exporter**：黑盒监控（HTTP、TCP、ICMP），端口 9115

**工作原理：**
1. Exporter 启动后暴露 `/metrics` 端点
2. Prometheus 定期拉取该端点获取指标数据
3. 指标数据以文本格式返回（Prometheus 文本格式）

### 示例

**Node Exporter（已在 docker-compose 中配置）：**

`docker-compose.yml` 已包含 Node Exporter 服务，启动时会自动运行：

```bash
docker-compose up -d
```

在 `prometheus.yml` 中取消 Node Exporter 配置的注释：

```yaml
scrape_configs:
  - job_name: "node"
    static_configs:
      - targets: ["node-exporter:9100"]
        labels:
          env: "dev"
          role: "system-monitor"
```

修改后执行 `docker-compose restart`。

**验证：**

访问 http://localhost:9090/targets，确认 `node` 任务状态为 UP。

在 Prometheus UI 中查询：
```promql
# CPU 使用率
100 - (avg by (instance) (rate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)

# 内存使用率
(1 - node_memory_available_bytes / node_memory_total_bytes) * 100
```

**其他 Exporter 配置方式类似：**

核心都是：启动 Exporter → 暴露 `/metrics` 端点 → Prometheus 配置 `scrape_configs` 拉取。

如果是 Docker 部署，使用容器名作为主机名（如 `node-exporter:9100`）；如果是宿主机部署，使用 `host.docker.internal:端口`。

### 常见陷阱/最佳实践

**陷阱：**
- Exporter 和 Prometheus 版本不兼容
- 忘记配置防火墙放行 Exporter 端口
- 采集间隔过短导致 Exporter 负载过高

**最佳实践：**
- 使用官方维护的 Exporter
- Exporter 端口标准化（Node: 9100, MySQL: 9104, Windows: 9182）
- 生产环境使用 systemd/Windows Service 管理 Exporter
- 监控 Exporter 自身指标（`up` 指标）

### 实践 Q&A

> 以下是动手操作中产生的疑问和踩坑记录。

<details>
<summary><b>Q: Exporter 是什么？为什么要用它？</b></summary>

**A:** Exporter 是一个独立进程，作用是采集目标系统的指标，暴露为 Prometheus 格式的 `/metrics` 端点。比如 Node Exporter 采集 Linux 系统指标，MySQL Exporter 采集数据库指标。Prometheus 本身只认识自己的格式，其他系统需要通过 Exporter 转换。

</details>

<details>
<summary><b>Q: Docker 环境下，Prometheus 容器怎么访问宿主机上的 Exporter？</b></summary>

**A:** 容器内的 `localhost` 指向容器自身，不是宿主机。解决方案：
- **推荐**：把 Exporter 也放到 Docker 里，用容器名通信（如 `node-exporter:9100`）
- **备选**：用 `host.docker.internal:端口` 访问宿主机（macOS/Windows Docker Desktop 支持）

本项目采用推荐方案，Node Exporter 和 Prometheus 都在 Docker 中运行。

</details>

<details>
<summary><b>Q: 启动 Docker 后，为什么过一会儿才有数据？</b></summary>

**A:** 正常行为。`scrape_interval: 15s` 表示 Prometheus 每 15 秒采集一次。启动后需要等待 15-30 秒完成第一轮采集。可以在 http://localhost:9090/targets 页面查看 Last scrape 时间戳确认。

</details>

<details>
<summary><b>Q: 为什么选择 Node Exporter 而不是 Windows Exporter？</b></summary>

**A:** 学习场景下，Docker 环境全部容器化更简单：Node Exporter 有官方 Docker 镜像，和 Prometheus 在同一个 Docker 网络里直接用容器名通信。Windows Exporter 只能装在宿主机上，需要处理跨网络访问的问题（`host.docker.internal`）。生产环境中，监控 Linux 服务器用 Node Exporter，监控 Windows 服务器用 Windows Exporter，按实际系统选择。

</details>

---

## M4：告警规则与 Alertmanager

### 概念解释

**告警系统组成：**
1. **告警规则**：在 Prometheus 中定义，基于 PromQL 表达式
2. **Alertmanager**：独立组件，负责告警处理（去重、分组、静默、路由、发送）

**告警流程：**
1. Prometheus 定期评估告警规则
2. 规则触发时，生成告警并发送给 Alertmanager
3. Alertmanager 处理告警（去重、分组、路由）
4. 发送到目标渠道（邮件、Slack、企业微信等）

### 核心配置参数

**告警规则文件（`04-alert-rules.yml`）：**

```yaml
groups:                          # 告警规则组（必须）
  - name: node_alerts            # 规则组名称
    rules:                       # 规则列表
      - alert: InstanceDown      # 告警名称（必须）
        expr: up == 0            # PromQL 表达式（必须），为 true 时触发告警
        for: 1m                  # 持续时间，条件满足多久后才真正触发（可选）
        labels:                  # 告警标签（可选）
          severity: critical     # 自定义标签，用于路由和分类
        annotations:             # 告警注解（可选）
          summary: "实例宕机"     # 告警摘要，支持模板变量
          description: "..."     # 告警详情，支持模板变量
```

**关键参数说明：**

- **alert**：告警名称，唯一标识一个告警规则
- **expr**：PromQL 表达式，返回 1（触发）或 0（不触发）。比如 `up == 0` 表示目标宕机
- **for**：持续时间。条件满足后进入 `pending` 状态，持续指定时间后才变为 `firing`。避免瞬时波动触发告警
- **labels**：自定义标签，会附加到告警上。常用于 `severity`（严重级别：warning/critical）
- **annotations**：告警描述信息，支持模板变量：
  - `{{ $labels.instance }}`：触发告警的实例地址
  - `{{ $value }}`：表达式的计算结果

**Alertmanager 配置文件（`alertmanager.yml`）：**

```yaml
global:                          # 全局配置
  resolve_timeout: 5m            # 告警恢复超时时间

route:                           # 路由配置（必须）
  group_by: ['alertname']        # 按哪些标签分组
  group_wait: 30s                # 同组告警首次发送前的等待时间
  group_interval: 5m             # 同组告警的发送间隔
  repeat_interval: 4h            # 重复告警的发送间隔
  receiver: 'default-receiver'   # 默认接收器名称
  routes:                        # 子路由（可选，树状结构）
    - match:
        severity: critical
      receiver: 'critical-receiver'

receivers:                       # 接收器列表（必须）
  - name: 'default-receiver'     # 接收器名称（与 route 中引用对应）
    webhook_configs:             # 通知方式
      - url: 'http://...'
        send_resolved: true      # 告警恢复时是否发送通知
```

**关键参数说明：**

- **global.resolve_timeout**：告警恢复的超时时间，超过此时间未收到恢复通知则标记为已解决
- **route.group_by**：按标签分组。比如 `['alertname', 'instance']` 表示相同告警名+相同实例的告警合并为一条通知
- **route.group_wait**：新组告警首次发送前等待多久，用于聚合短时间内的告警
- **route.group_interval**：已发送告警组的新告警等待多久后再次发送
- **route.repeat_interval**：未恢复的告警多久后重复发送
- **route.receiver**：默认接收器名称，必须与 `receivers` 中的 `name` 对应
- **route.routes**：子路由，树状结构，匹配到第一个就停止。用于按标签路由到不同接收器
- **receivers**：接收器列表，每个接收器包含一种或多种通知方式（email、webhook、slack 等）
- **send_resolved**：告警恢复时是否发送恢复通知

### 示例

**完整实现步骤：飞书告警通知**

**步骤 1：启动 Alertmanager**

在 `docker-compose.yml` 中取消 Alertmanager 服务的注释：

```yaml
alertmanager:
  image: prom/alertmanager:v0.27.0
  container_name: alertmanager
  ports:
    - "9093:9093"
  volumes:
    - ./alertmanager.yml:/etc/alertmanager/alertmanager.yml
```

**步骤 2：配置告警规则**

在 `prometheus.yml` 中取消 `rule_files` 的注释：

```yaml
rule_files:
  - "04-alert-rules.yml"
```

**步骤 3：配置 Alertmanager 连接**

取消 `alerting` 的注释：

```yaml
alerting:
  alertmanagers:
    - static_configs:
        - targets: ["alertmanager:9093"]
```

修改后执行 `docker-compose up -d` 启动 Alertmanager。

**步骤 4：配置飞书机器人**

1. 打开飞书，进入测试群（或新建群）
2. 群设置 → 群机器人 → 添加机器人 → 自定义机器人
3. 给机器人起名字（如"Prometheus 告警"）
4. 复制 Webhook 地址（类似 `https://open.feishu.cn/open-apis/bot/v2/hook/xxx`）

**步骤 5：配置中转服务**

编辑 `04-feishu-webhook.py` 第 10 行，替换为你的飞书 Webhook：

```python
FEISHU_WEBHOOK = 'https://open.feishu.cn/open-apis/bot/v2/hook/你的地址'
```

**步骤 6：启动中转服务**

```bash
python -m pip install flask requests
python 04-feishu-webhook.py
```

看到 `Running on http://0.0.0.0:5001` 说明启动成功。

**步骤 7：验证服务正常**

浏览器访问 http://localhost:5001/health，应返回 `{"status": "ok"}`

**步骤 8：触发告警**

新打开 PowerShell 窗口，执行：

```bash
docker stop node-exporter
```

**步骤 9：查看飞书通知**

等待 1-2 分钟，飞书群收到告警通知卡片：
- 🔴 **InstanceDown** [CRITICAL] 触发
- 实例：node-exporter:9100
- 摘要：实例宕机

**步骤 10：恢复告警**

```bash
docker start node-exporter
```

等待 1-2 分钟，飞书群收到恢复通知：
- 🟢 **InstanceDown** [CRITICAL] 恢复

**步骤 11：停止中转服务**

回到运行 Python 的窗口，按 `Ctrl+C` 停止服务。

**架构说明：**

```
Prometheus → Alertmanager → 04-feishu-webhook.py → 飞书群
                              ↓
                          Agent（可选）
```

- `04-feishu-webhook.py` 接收 Alertmanager 的 webhook
- 转换 JSON 格式为飞书卡片消息
- 同时支持转发给自定义 Agent（预留扩展）

**验证：**

- 访问 http://localhost:9090/alerts 查看告警规则状态
- 访问 http://localhost:9093 查看 Alertmanager 界面

>  对应代码：`04-alert-rules.yml`, `alertmanager.yml`, `04-feishu-webhook.py`

### 常见陷阱/最佳实践

**陷阱：**
- 告警阈值设置不合理（过高导致漏报，过低导致告警风暴）
- 忘记配置 `for` 持续时间，导致瞬时波动触发告警
- Alertmanager 未配置 `group_by`，导致告警轰炸

**最佳实践：**
- 告警阈值基于历史数据和 SLA 设定
- 使用 `for` 避免瞬时波动（至少 1-5 分钟）
- 合理配置 `group_by` 和 `group_interval` 减少告警噪音
- 区分 `warning` 和 `critical` 级别，路由到不同接收人

### 实践 Q&A

> 以下是动手操作中产生的疑问和踩坑记录。

<details>
<summary><b>Q: 告警规则只能通过配置文件实现吗？以前用的公司中台是通过网页添加的。</b></summary>

**A:** 原生 Prometheus 只能通过配置文件定义告警规则。公司中台的网页版实质是：网页界面 → 调用后端 API → 修改配置文件或数据库 → 触发 reload。底层还是配置文件，只是套了一层 UI。

</details>

<details>
<summary><b>Q: 修改告警规则后需要重启吗？</b></summary>

**A:** 不需要重启，支持热加载。前提是启动时加了 `--web.enable-lifecycle` 参数（本项目已配置）。

热加载命令：
- Prometheus：`curl -X POST http://localhost:9090/-/reload`
- Alertmanager：`curl -X POST http://localhost:9093/-/reload`

修改配置后执行 reload 即可生效，无需重启容器。

</details>

<details>
<summary><b>Q: Alertmanager 是和 Prometheus 独立部署的吗？它们怎么通信？</b></summary>

**A:** 是的，Alertmanager 是独立进程，和 Prometheus 分开部署。它们通过 HTTP 通信：
- Prometheus 评估告警规则后，通过 HTTP POST 发送告警到 Alertmanager 的 9093 端口
- 在 `prometheus.yml` 的 `alerting` 配置中指定 Alertmanager 地址（如 `alertmanager:9093`）
- Alertmanager 接收告警后，按配置的路由规则发送到通知渠道

</details>

<details>
<summary><b>Q: Alertmanager 支持哪些通知渠道？钉钉、飞书、QQ 是官方支持的吗？</b></summary>

**A:** 官方原生支持的通知渠道：
- 邮件（email）
- Slack
- PagerDuty
- OpsGenie
- VictorOps
- Pushover
- 企业微信（WeChat）
- Webhook（通用 HTTP 接口）

**钉钉/飞书**：不是官方直接支持，需要通过 webhook 中转。有两种方式：
1. 用开源中转工具（如 `prometheus-webhook-dingtalk`）
2. 自己写脚本接收 Alertmanager 的 webhook，转换格式后发送到钉钉/飞书

**QQ**：不支持，没有官方接口。

生产环境常用方案：
- 企业微信：原生支持，直接配置
- 钉钉/飞书：通过 webhook + 中转服务
- 邮件：原生支持，配置 SMTP

</details>

<details>
<summary><b>Q: 飞书群机器人能直接接收 Alertmanager 的 webhook 吗？</b></summary>

**A:** 不能直接接收，因为 JSON 格式不兼容：

**Alertmanager 发送的格式：**
```json
{
  "alerts": [...],
  "status": "firing",
  "commonLabels": {...}
}
```

**飞书机器人期望的格式：**
```json
{
  "msg_type": "text",
  "content": {
    "text": "消息内容"
  }
}
```

直接填飞书 webhook URL 会收不到消息（格式不对，飞书会返回错误）。需要中转服务转换格式，项目提供的 `04-feishu-webhook.py` 就是这个作用。

</details>

---

## M5：服务发现与动态配置

### 为什么需要服务发现

M3 中我们给 Node Exporter 配置了静态目标（`targets: ["node-exporter:9100"]`），这种方式在目标固定时没问题，但生产环境中会遇到问题：

- **容器环境**：Pod/容器重启后 IP 变化，每次都要改配置
- **弹性伸缩**：云服务器自动扩缩容，实例数量不固定
- **微服务架构**：服务实例频繁上下线，手动维护不现实

**服务发现** 就是让 Prometheus 自动获取监控目标列表，不再需要手动写死在配置文件里。

### 三种服务发现方式

| 方式 | 原理 | 适用场景 | 学习成本 |
|------|------|----------|----------|
| **文件服务发现** | 从 YAML/JSON 文件读取目标，修改文件后自动加载（无需重启） | 目标由外部系统写入文件、多 Prometheus 共享配置 | 低 |
| **Consul 服务发现** | 从 Consul 注册中心查询健康实例，自动剔除不健康实例 | 已有 Consul 集群、微服务注册/注销 | 中 |
| **Kubernetes 服务发现** | 通过 K8s API 自动发现 Pod/Service，Pod 创建/销毁时自动调整 | Kubernetes 集群环境 | 高 |

**生产环境价值**：动态添加/删除监控目标，无需重启 Prometheus；适合容器化环境（Pod IP 频繁变化）；集中管理目标配置，多 Prometheus 实例共享。

### 学习建议

1. **掌握文件服务发现**：最简单、最实用，Docker 环境即可验证
2. **了解 Consul/K8s 配置格式**：知道参数含义，生产环境需要时能快速上手
3. **理解标签重写（relabel_configs）**：三种方式都会用到，是服务发现的核心技能

详细配置示例和参数说明请参考 `05-service-discovery/README.md`。

> 📂 对应代码：`05-service-discovery/`

---

## M6：综合实战 - Spring Boot 应用监控

### 场景描述

为一个 Spring Boot 应用搭建完整的监控体系，包括：
- 应用指标采集（JVM、HTTP、自定义业务指标）
- 系统指标采集（Node Exporter）
- 告警规则配置

**架构说明：**
- Prometheus 继续用 Docker 容器运行（和 M1-M5 一致）
- Spring Boot 应用在宿主机运行（`mvn spring-boot:run`）
- Prometheus 通过 `host.docker.internal:8080` 访问宿主机的 Spring Boot
- 采集配置在主 `prometheus.yml` 的 M6 部分（取消注释即可）
- 告警规则在主 `04-alert-rules.yml` 的 spring_boot_alerts 部分

### 快速开始

**1. 启动 Spring Boot 应用**

```bash
cd 06-spring-boot-demo
mvn spring-boot:run
```

应用启动后，访问以下端点：
- 应用首页：http://localhost:8080
- 健康检查：http://localhost:8080/actuator/health
- Prometheus 指标：http://localhost:8080/actuator/prometheus

**2. 测试订单接口**

```bash
# 创建订单
curl -X POST http://localhost:8080/api/orders -H "Content-Type: application/json" -d '{"item": "iPhone", "quantity": 1}'

# 查看订单统计
curl http://localhost:8080/api/orders/stats
```

**3. 启动 Prometheus 容器**

```bash
cd ..
docker-compose up -d
```

Prometheus 通过 `host.docker.internal` 访问宿主机的 Spring Boot 应用。

访问 Prometheus UI：http://localhost:9090

**4. 验证监控**

访问 http://localhost:9090/targets ，确认 `spring-boot-app` 状态为 UP。

访问 http://localhost:9090/rules ，确认规则已加载。

### 关键配置说明

**application.yml**

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,prometheus,metrics  # 暴露的端点
  metrics:
    tags:
      application: ${spring.application.name}  # 指标标签
      env: ${spring.profiles.active}
```

| 参数 | 含义 |
|------|------|
| `endpoints.web.exposure.include` | 暴露的 Actuator 端点，必须包含 `prometheus` |
| `metrics.tags.application` | 给所有指标添加 `application` 标签，方便按应用过滤 |
| `metrics.tags.env` | 给所有指标添加 `env` 标签，区分环境 |

**pom.xml 关键依赖**

```xml
<!-- Actuator 提供监控端点 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Micrometer Prometheus Registry -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

| 依赖 | 作用 |
|------|------|
| `spring-boot-starter-actuator` | 提供 `/actuator/*` 端点（健康检查、指标、环境信息等） |
| `micrometer-registry-prometheus` | 将 Micrometer 指标转换为 Prometheus 格式，暴露 `/actuator/prometheus` |

### 指标分类

**自动采集的指标（无需写代码）**

引入 `micrometer-registry-prometheus` 后，Micrometer 自动注册以下指标：

| 指标类型 | 示例 | 来源 |
|---------|------|------|
| JVM 内存 | `jvm_memory_used_bytes` | JVM 运行时自动采集 |
| JVM 线程 | `jvm_threads_live_threads` | JVM 运行时自动采集 |
| GC | `jvm_gc_pause_seconds_count` | JVM GC 事件自动采集 |
| HTTP 请求 | `http_server_requests_seconds_count` | Spring MVC 拦截器自动采集 |
| 系统 CPU | `system_cpu_usage` | OS 层面自动采集 |

**自定义指标（需要写代码）**

项目演示了三种指标类型：

1. **Counter（计数器）**：`orders_total`（订单总数）、`orders_failed_total`（失败订单数）— 只增不减
2. **Timer（计时器）**：`orders_processing_time`（订单处理时间）— 记录耗时分布
3. **Gauge（仪表盘）**：`orders_pending`（成功创建的订单数）— 可增可减

**指标变化逻辑：**
- 创建订单：`orders_pending` +1，`orders_total` +1
- 订单失败：`orders_pending` -1，`orders_failed_total` +1
- 访问 `/api/orders/stats` 查看当前 `orders_pending` 值

**自定义指标代码示例（OrderMetrics.java）：**

```java
// Counter：订单总数
Counter orderCounter = Counter.builder("orders_total")
        .description("Total number of orders created")
        .tag("type", "new")
        .register(registry);

// Gauge：待处理订单数
AtomicInteger pendingOrders = new AtomicInteger(0);
registry.gauge("orders_pending", pendingOrders);

// Timer：订单处理时间
Timer orderProcessingTimer = Timer.builder("orders_processing_time")
        .description("Time taken to process an order")
        .register(registry);
```

### 常用 PromQL 查询

```promql
# 查看所有自定义指标
{__name__=~"orders_.*"}

# 订单创建速率（每秒）
rate(orders_total[1m])

# 订单处理时间 P95
histogram_quantile(0.95, rate(orders_processing_time_bucket[1m]))

# JVM 堆内存使用率
jvm_memory_used_bytes{area="heap"} / jvm_memory_max_bytes{area="heap"} * 100

# HTTP QPS
sum(rate(http_server_requests_seconds_count[1m]))

# HTTP 平均响应时间
sum(rate(http_server_requests_seconds_sum[1m])) / sum(rate(http_server_requests_seconds_count[1m]))

# HTTP 5xx 错误率
sum(rate(http_server_requests_seconds_count{status=~"5.."}[1m])) / sum(rate(http_server_requests_seconds_count[1m])) * 100

# JVM 线程数
jvm_threads_live_threads

# GC 次数
rate(jvm_gc_pause_seconds_count[1m])
```

### 学习总结

**M6 核心知识点**

1. **Spring Boot Actuator**：提供标准化的监控端点（`/actuator/health`、`/actuator/prometheus`）
2. **Micrometer**：Spring Boot 的指标门面，自动采集 JVM/HTTP/系统指标，支持自定义指标
3. **三种指标类型**：Counter（累加）、Timer（耗时分布）、Gauge（瞬时值）
4. **Prometheus 拉取模式**：Prometheus 定期 scrape `/actuator/prometheus` 端点，不是应用主动推送
5. **告警规则**：基于 PromQL 表达式 + `for` 持续时间触发

**生产环境注意事项**

| 配置项 | 测试值 | 生产建议 | 原因 |
|--------|--------|----------|------|
| `scrape_interval` | 5s | 15s-60s | 太短增加采集压力，太长告警延迟 |
| `evaluation_interval` | 5s | 15s-60s | 规则评估频率 |
| `for` 持续时间 | 10s-30s | 1m-5m | 避免瞬时抖动误报 |
| `rate()` 窗口 | 1m | 5m | 窗口太短数据波动大，太长告警延迟 |
| 指标标签 | 固定值 | 动态值 | 生产环境用 `${spring.application.name}` 等变量 |

### Q&A

<details>
<summary>Q：为什么 Prometheus 能查到 JVM 指标，但我没有写代码上报？</summary>

引入 `micrometer-registry-prometheus` 依赖后，Micrometer 自动配置了 JVM、HTTP、系统等指标的收集器。这些指标不是"主动上报"的，而是 Micrometer 自动采集后，Prometheus 来"拉取"的。自定义指标（如订单相关）才需要手动用 SDK 注册。
</details>

<details>
<summary>Q：`/actuator/prometheus` 端点返回的指标格式是什么样的？</summary>

```
# HELP orders_total Total number of orders created
# TYPE orders_total counter
orders_total{application="order-service",env="dev",type="new",} 15.0
# HELP orders_pending
# TYPE orders_pending gauge
orders_pending{application="order-service",env="dev",} 12.0
```

每行格式：`指标名{标签键="标签值",...} 数值`。`# HELP` 和 `# TYPE` 是元数据。
</details>

<details>
<summary>Q：Counter 和 Gauge 有什么区别？什么时候用哪个？</summary>

- **Counter**：只增不减，适合累计值（订单总数、请求总数、错误总数）。重启后归零。
- **Gauge**：可增可减，适合瞬时值（当前连接数、待处理订单数、内存使用量）。重启后丢失。

判断标准：如果指标只会增加（如订单总数），用 Counter；如果指标可能减少（如队列长度），用 Gauge。
</details>

<details>
<summary>Q：Prometheus 采集不到 Spring Boot 指标，怎么排查？</summary>

1. 检查应用是否启动：`curl http://localhost:8080/actuator/prometheus` 看是否有指标输出
2. 检查 Prometheus 配置：`targets` 是否是 `host.docker.internal:8080`（不是 `localhost:8080`）
3. 检查网络：Docker 容器内能否访问宿主机（Windows/Mac 默认支持 `host.docker.internal`）
4. 访问 http://localhost:9090/targets 查看错误信息
</details>

<details>
<summary>Q：告警规则里的 `for: 30s` 是什么意思？</summary>

`for` 表示条件持续满足多长时间后才触发告警。例如 `for: 30s` 表示 CPU 使用率 > 80% 持续 30 秒才触发告警，避免瞬时抖动误报。生产环境建议 1m-5m。
</details>

<details>
<summary>Q：`rate()` 函数的时间窗口（如 `rate(xxx[1m])`）和 `for` 持续时间有什么关系？</summary>

`rate()` 窗口应该 ≤ `for` 持续时间。如果窗口比 `for` 长，告警触发会更慢（需要等待窗口填满）。例如 `for: 30s` + `rate(xxx[1m])`，实际触发时间约 1 分钟（窗口长度），不是 30 秒。
</details>

> 📂 对应代码：`06-spring-boot-demo/`

---

## 附录：常用命令与工具

### promtool 工具

```bash
# 检查配置文件语法
promtool check config prometheus.yml

# 检查告警规则语法
promtool check rules 04-alert-rules.yml
```

### 常用 API

```bash
# 查询瞬时值
curl 'http://localhost:9090/api/v1/query?query=up'

# 查询目标
curl 'http://localhost:9090/api/v1/targets'

# 查询规则
curl 'http://localhost:9090/api/v1/rules'
```

### 热加载

```bash
# 修改配置后无需重启
curl -X POST http://localhost:9090/-/reload
```
