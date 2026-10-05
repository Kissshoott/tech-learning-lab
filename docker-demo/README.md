# Docker 快速上手

## 环境搭建与架构理解

### 安装

| 平台 | 安装方式 |
|------|---------|
| **Windows** | 下载 [Docker Desktop](https://www.docker.com/products/docker-desktop/) 安装包，安装时勾选 WSL2 后端 |
| **macOS** | `brew install --cask docker` 或下载 dmg 安装包 |
| **Linux (Ubuntu)** | `sudo apt install docker.io` 或用官方脚本 `curl -fsSL https://get.docker.com \| sh` |

安装验证：

```bash
docker --version        # 预期输出：Docker version 24.x.x 或更高
docker-compose --version # 预期输出：Docker Compose version v2.x.x
docker run hello-world   # 能跑通说明环境完全 OK
```

<br>

### 架构：客户端-守护进程

Docker 是 **C/S 架构**，两个进程：

```
docker CLI（客户端）  ──socket/pipe──>  Docker Daemon（守护进程）
     你敲命令                              真正干活的（管理镜像、容器、网络、卷）
```

- **Docker Desktop**：只是帮你启动 Daemon 的图形工具。Windows/macOS 需要它，因为容器本质是 Linux 进程，Desktop 内部跑了一个 Linux 虚拟机（WSL2）来承载 Daemon。

- **Docker Engine**：Linux 原生的 Daemon，生产服务器直接装这个，不需要桌面端。

**常见坑**：`docker ps` 报 `connection refused` → 99% 是 Docker Desktop 没启动，Daemon 没跑起来。

<br>

### Windows 特别注意：WSL2 集成

Docker Desktop 安装后，WSL2 默认未启用集成。如果想在 WSL 终端里也能用 docker 命令：
- Docker Desktop → Settings → Resources → WSL integration → 开启对应发行版

建议按 M1 → M6 顺序学习，M6 是综合实战，串联前面所有概念。

---

## M1: 镜像与容器基础操作

### 概念解释

- **镜像（Image）**：只读模板，包含运行应用所需的一切（代码、运行时、库、配置）。类比 Java 的 class 文件。

- **容器（Container）**：镜像的运行实例，有自己的生命周期（创建→运行→停止→删除）。类比 Java 的对象。

- **端口映射（-p）**：宿主机端口转发到容器内部端口，外部才能访问容器内服务。

- **Registry**：镜像仓库（Docker Hub 是默认的），`docker pull` 从这里下载镜像。

<br>

### 核心命令

**镜像操作：**

```bash
docker pull nginx:1.25          # 拉取镜像（不指定 tag 默认 latest）
docker images                    # 查看本地镜像列表
docker inspect nginx:1.25        # 查看镜像详细信息（层结构、环境变量等）
docker rmi nginx:1.25            # 删除镜像（先停掉引用该镜像的容器）
docker image prune -f            # 清理所有悬空镜像（没有 tag 的废弃镜像）
```

<br>

**容器生命周期：**

```bash
docker run nginx:1.25                              # 前台运行（Ctrl+C 停止）
docker run -d --name my-nginx -p 8080:80 nginx:1.25 # 后台运行 + 端口映射
docker ps                                           # 查看运行中的容器
docker ps -a                                        # 查看所有容器（含已停止的）
docker stop my-nginx                                # 停止
docker start my-nginx                               # 启动已停止的容器
docker restart my-nginx                             # 重启
docker rm my-nginx                                  # 删除（必须先停止）
```

<br>

**常用参数：**

| 参数 | 作用 | 示例 |
|------|------|------|
| `-d` | 后台运行 | `docker run -d nginx` |
| `-p` | 端口映射（宿主机:容器） | `-p 8080:80` |
| `--name` | 给容器命名 | `--name my-nginx` |
| `-e` | 设置环境变量 | `-e NGINX_PORT=80` |
| `-v` | 挂载卷或目录 | `-v my-data:/var/lib/mysql` |
| `--network` | 指定网络 | `--network my-net` |

<br>

**常用运维命令：**

```bash
docker logs -f my-nginx          # 实时跟踪日志（类似 tail -f）
docker exec -it my-nginx sh      # 进入容器内部（交互式 shell）
docker stats my-nginx            # 查看容器资源占用（CPU/内存/网络）
```

<br>

### 常见陷阱

- 容器内服务必须监听 `0.0.0.0` 而不是 `127.0.0.1`，否则外部无法访问

- 端口冲突：宿主机 8080 已被占用 → 改映射 `-p 8081:80`

- 删除容器必须先停止，或用 `docker rm -f`（慎用）

- 容器名重复会报错：`The container name "/my-nginx" is already in use`，先 `docker rm -f` 删旧的

<br>

---

### 实践 Q&A

> 以下是动手操作中产生的疑问和踩坑记录。

<details>
<summary><b>Q: 镜像的 tag 是什么？latest 是特殊版本吗？</b></summary>

`nginx:1.25` 中 `nginx` 是镜像名，`1.25` 是标签（tag）。

- **tag 只是版本号标识**，`latest` 不是特殊版本，只是默认 tag

- `docker pull nginx` 不指定 tag → 默认拉 `nginx:latest`

- DockerHub 上很多镜像标 latest，是因为维护者把最新构建推到了 latest tag

- **生产环境必须锁具体版本**（如 `nginx:1.25`、`mysql:8.0.35`），不要用 latest。原因：latest 随时会变，可能导致升级后不兼容

</details>

<details>
<summary><b>Q: docker image prune -f 和 -a -f 有什么区别？</b></summary>

```bash
# 悬空镜像：没有 tag 的废弃镜像（旧版本被新构建覆盖后的残留）
docker images --filter "dangling=true"
# 输出示例：REPOSITORY   TAG       IMAGE ID       CREATED        SIZE
#           <none>       <none>    abc123def456   2 hours ago    150MB

# 安全操作：只清理悬空镜像
docker image prune -f

# 危险操作：删除所有未被容器引用的镜像（包括你手动 pull 的！）
docker image prune -a -f
```

**区别**：`-f` 只删 `<none>:<none>` 的废弃镜像，`-a -f` 删所有没被容器使用的镜像。手滑跑 `-a -f` 会把你 pull 的 nginx、mysql 等镜像全删了，只能重新 `docker pull`。

</details>

<details>
<summary><b>Q: 容器创建后能改端口/环境变量吗？</b></summary>

不能。`-p`、`-e`、`--name`、`-v`、`--network` 这些参数**创建时写死，之后无法修改**。

想改只能删掉重建：

```bash
docker rm -f my-nginx
docker run -d --name my-nginx -p 9090:80 nginx:1.25  # 换个端口
```

这也是 **docker-compose 比 docker run 好用**的原因之一 — 改 yml 后 `docker-compose up -d` 自动帮你删旧建新。

</details>

> 📂 对应代码：`01-basic-commands.sh`

---

## M2: Dockerfile 编写与镜像构建

### Dockerfile 是什么

Dockerfile 是一个**无后缀的纯文本文件**，文件名就叫 `Dockerfile`（类似 `Makefile`、`Jenkinsfile`）。

- `docker build` 默认找当前目录下名为 `Dockerfile` 的文件（硬编码约定）

- 自定义文件名用 `-f` 参数：`docker build -f my-app.Dockerfile .`

- 首字母大写是约定，不是强制（`dockerfile` 小写也能用）

<br>

### 核心指令详解

| 指令 | 作用 | 关键点 |
|------|------|--------|
| **FROM** | 指定基础镜像 | 每个 Dockerfile 必须以此开头；尽量用 alpine 版本减小体积 |
| **WORKDIR** | 设置工作目录 | 后续所有命令在此目录执行，不存在则自动创建 |
| **COPY** | 复制文件到镜像 | 先复制 package.json/pom.xml 再 install，利用层缓存 |
| **RUN** | 构建时执行命令 | 每条 RUN 产生一层，尽量合并命令减少层数 |
| **CMD** | 容器启动时执行 | 用 exec 格式 `["cmd","arg"]`，避免 shell 格式的信号问题 |
| **ENTRYPOINT** | 容器入口命令 | 和 CMD 区别：ENTRYPOINT 不易被覆盖，适合固定入口 |
| **EXPOSE** | 声明监听端口 | 仅作文档说明，实际端口映射靠 `docker run -p` |
| **ENV** | 设置环境变量 | 运行时可通过 `-e` 覆盖 |

### 层缓存机制（重要）

Docker 镜像是**分层存储**，每层对应 Dockerfile 的一条指令。构建时 Docker 会检查每层是否有缓存：

- 如果该层指令没变且依赖的文件没变 → 使用缓存（秒过）

- 如果该层或上层有变化 → 从这一层开始重新构建

**最佳实践**：把不常变的指令放前面（如安装依赖），常变的放后面（如复制源码）。

<br>

### 多阶段构建

场景：编译环境和运行环境不同（如 Java 需要 Maven 编译但只需 JRE 运行）。

原理：用多个 FROM 定义多个阶段，最终镜像只包含最后一个阶段的内容。前面的阶段用于编译/测试，产物通过 `COPY --from=builder` 复制到最终阶段。

效果：镜像体积从 ~800MB → ~150MB。**生产环境务必使用多阶段构建。**

<br>

### .dockerignore

类似 .gitignore，排除不需要进入镜像的文件：

```
target/
.git/
node_modules/
*.md
.idea/
```

<br>

### 构建和运行示例

```bash
# ===== 第一步：构建镜像 =====
# docker build：从 Dockerfile 构建镜像（类比 mvn package）
#   -f 02-dockerfile   → 指定 Dockerfile 文件路径（不指定则默认找当前目录下的 Dockerfile）
#   -t node-app:1.0    → 给构建出的镜像打标签（格式：镜像名:版本号）
#   02-app             → 构建上下文（build context），即 COPY 指令的源文件目录
#                        Docker 只会把这个目录发给 Daemon，Dockerfile 中的 COPY 只能引用这里面的文件
#                        注意：这个参数是目录路径，不是 Dockerfile 路径，两者是独立的
docker build -f 02-dockerfile -t node-app:1.0 02-app

# ===== 第二步：运行容器 =====
# 从构建好的镜像启动容器（类比 java -jar）
docker run -d -p 3000:3000 --name my-app node-app:1.0

# 验证
curl http://localhost:3000   # → Hello from Docker!

# 清理
docker rm -f my-app
```

> 📂 对应代码：`02-dockerfile`（基础版）、`02-multi-stage.Dockerfile`（多阶段构建）、`02-app/`（配套 Node.js 应用）

---

### 实践 Q&A

> 以下是动手操作中产生的疑问和踩坑记录。

<details>
<summary><b>Q: docker run 的 -p、-e 等参数已经能配置端口和环境变量了，为什么还需要 Dockerfile？</b></summary>

**Dockerfile 是交付物，docker run 参数是运行时配置。**

- `docker run -p 8080:80 -e DB_HOST=xxx nginx` — 每次启动都要敲一遍这些参数，换个同事来部署可能漏掉某个参数
- Dockerfile — 把镜像构建逻辑固化下来，`docker build` 一次，任何人拿到镜像直接 `docker run` 就能跑

类比 Java：Dockerfile 相当于 `pom.xml` + 源码，构建出 jar 包（镜像）；`docker run` 相当于 `java -jar`，只负责配置运行时参数（端口、环境变量等）。

**分工原则**：镜像里该有什么（代码、依赖、基础配置）→ Dockerfile 决定；容器怎么跑（端口映射、挂载、网络）→ `docker run` 或 docker-compose 决定。

</details>

<details>
<summary><b>Q: docker pull/push 用的仓库是怎么来的？从来没配置过地址。</b></summary>

Docker 默认使用 **Docker Hub**（`docker.io`）作为公共镜像仓库。

```bash
docker pull nginx          # 等价于 docker pull docker.io/library/nginx:latest
docker pull mysql:8.0      # 等价于 docker pull docker.io/library/mysql:8.0
```

- **公共仓库**：Docker Hub（docker.io），类似 Maven 的中央仓库，个人和企业都能上传镜像
- **私有仓库**：生产环境通常自建（Harbor）或用云厂商的（阿里云 ACR、AWS ECR），存放业务镜像

镜像名格式：`[仓库地址/]组织名/镜像名:tag`
- `nginx` → 省略了 `docker.io/library/`，Docker 自动补全
- `myregistry.com/myapp:1.0` → 指定了私有仓库地址

</details>

<details>
<summary><b>Q: FROM 指令到底干了什么？为什么每个 Dockerfile 都要以 FROM 开头？</b></summary>

**FROM 的作用是选择一个"基础镜像"作为起点。**

Docker 镜像是分层存储的，`FROM node:22-alpine` 的意思是：以 `node:22-alpine` 这个镜像的所有层为基础，后续的指令（WORKDIR、COPY、RUN 等）在这个基础上叠加新的层。

- 基础镜像已经包含了 Node.js 运行时 + Alpine Linux 系统
- 你的 Dockerfile 只需要在此基础上添加应用代码和依赖

类比 Java：`FROM` 相当于选择一个预装了 JDK 的服务器环境，你只需要把 jar 包丢上去运行。

**为什么必须 FROM 开头**：因为镜像是分层叠加的，没有基础层就没法往上加东西。即使是最简单的 Dockerfile 也需要一个操作系统层来承载应用。

</details>

<details>
<summary><b>Q: 构建上下文（build context）是什么？为什么 docker build 最后要指定一个目录？</b></summary>

构建上下文是 `docker build` 命令**最后一个参数指定的目录**，它决定了 COPY 指令能访问哪些文件。

```bash
docker build -f 02-dockerfile -t node-app:1.0 02-app
#                                                  ^^^^^^ 这就是构建上下文
```

- Docker CLI 把 `02-app/` 目录下的**所有文件**打包发给 Docker Daemon
- Dockerfile 中的 `COPY` 只能引用这个目录里的文件（相对路径）
- Dockerfile 本身不需要在构建上下文里（`-f` 参数单独指定了路径）

**常见误区**：
- 构建上下文不是 WORKDIR，WORKDIR 是容器内的目录，构建上下文是宿主机的目录
- 不要把 `.git`、`node_modules` 等大目录放在构建上下文里，会拖慢构建速度（用 `.dockerignore` 排除）

</details>

<details>
<summary><b>Q: docker build 报错 "failed to fetch oauth token ... auth.docker.io" 怎么解决？</b></summary>

这是 Docker Hub 在国内无法直接访问导致的。`docker build` 需要从 Docker Hub 拉取 FROM 指定的基础镜像。

**解决方案一：先手动 pull 基础镜像**

```bash
# docker pull 走 Docker Daemon 的网络配置，可能能通
docker pull node:22-alpine

# pull 成功后，docker build 会直接使用本地缓存，不再访问 Docker Hub
docker build -f 02-dockerfile -t node-app:1.0 02-app
```

**解决方案二：配置镜像加速器**

Docker Desktop → Settings → Docker Engine，添加：

```json
{
  "registry-mirrors": ["https://docker.xuanyuan.me", "https://docker.registry.cyou"]
}
```

注意：镜像加速器只影响 `docker pull`，`docker build` 中 BuildKit 的网络路径可能不走加速器。所以方案一更稳定。

**解决方案三：配置代理**

Docker Desktop → Settings → Resources → Proxies，填入你的 HTTP 代理地址和端口。

</details>

---

## M3: 数据持久化

### 为什么需要数据持久化

容器的文件系统是**临时的** — 容器删除，数据就没了。数据库、日志、上传文件等需要持久化的数据，必须用外部存储。

<br>

### 两种方式

| 方式 | 语法 | 数据位置 | 适用场景 |
|------|------|---------|---------|
| **Volume（命名卷）** | `my-data:/var/lib/mysql` | Docker 管理的目录 | **生产推荐**，Docker 管理生命周期，跨平台 |
| **Bind Mount** | `./data:/var/lib/mysql` | 你指定的宿主机路径 | 开发调试用，可以直接看/改文件 |

<br>

### Volume 核心命令

```bash
docker volume ls                   # 查看所有卷
docker volume create my-data       # 创建卷
docker volume inspect my-data      # 查看卷详情（找到实际存储路径）
docker volume rm my-data           # 删除卷（数据不可恢复！）
docker volume prune -f             # 清理所有未被使用的卷
```

### 关键区别

- `docker-compose down` — 停止并删除容器，**但保留 Volume**

- `docker-compose down -v` — 停止并删除容器 **和 Volume**（数据全丢！）

生产中几乎不会用 `-v`，除非你确定要清空数据。

<br>

### 验证步骤

```bash
# 启动
docker-compose -f 03-persistence.yml up -d

# 连接 MySQL（localhost:3307，root/root123），插入数据
# CREATE TABLE test (id INT PRIMARY KEY, name VARCHAR(50));
# INSERT INTO test VALUES (1, 'hello');

# 停止容器（不加 -v，数据保留）
docker-compose -f 03-persistence.yml down

# 重新启动
docker-compose -f 03-persistence.yml up -d

# 再次连接，数据还在 → SELECT * FROM test;  →  (1, 'hello')

# 彻底清理
docker-compose -f 03-persistence.yml down -v
```

> 📂 对应代码：`03-persistence.yml`

---

### 实践 Q&A

> 以下是动手操作中产生的疑问和踩坑记录。

<details>
<summary><b>Q: docker-compose 只能用 yml 格式吗？</b></summary>

是的，只能用 YAML 格式（`.yml` 或 `.yaml`）。

- 默认文件名：`docker-compose.yml` 或 `docker-compose.yaml`（两个都行）
- 自定义文件名用 `-f` 参数：`docker-compose -f my-compose.yaml up -d`

早期版本支持 JSON，但 YAML 语法更简洁（不需要大括号、支持注释），现在几乎没人用 JSON。

</details>

<details>
<summary><b>Q: docker-compose.yml 里的 environment 参数（如 MYSQL_ROOT_PASSWORD）是每个容器特有的吗？</b></summary>

**分两部分理解**：

**通用部分（所有容器都一样）**：
```yaml
services:
  mysql:
    image: mysql:8.0        # 镜像名
    ports:                  # 端口映射
      - "3307:3306"
    volumes:                # 卷挂载
      - mysql-data:/var/lib/mysql
    networks:               # 网络
      - app-net
```
这些是 docker-compose 的语法，所有容器都这么写。

**特定部分（每个镜像不同）**：
```yaml
    environment:            # 环境变量
      MYSQL_ROOT_PASSWORD: root123    # ← MySQL 特有的
      MYSQL_DATABASE: demo            # ← MySQL 特有的
```
这些是镜像作者定义的，每个镜像需要的环境变量不同。

**怎么知道要配什么**：去 Docker Hub 搜镜像名，看 README 的 "Environment Variables" 部分。

</details>

<details>
<summary><b>Q: docker-compose up 等效于 docker run 吗？</b></summary>

**docker-compose 是 docker run 的批量编排工具**。

```bash
# docker-compose 一条命令
docker-compose -f 03-persistence.yml up -d

# 等价于手动执行多条 docker 命令
docker network create 03-persistence_default
docker volume create 03-persistence_mysql-data
docker run -d --name mysql \
  -p 3307:3306 \
  -e MYSQL_ROOT_PASSWORD=root123 \
  -v 03-persistence_mysql-data:/var/lib/mysql \
  --network 03-persistence_default \
  mysql:8.0
```

**核心区别**：

| 维度 | docker run | docker-compose |
|------|-----------|----------------|
| 容器数量 | 一次启动一个 | 一次启动多个 |
| 配置方式 | 命令行参数 | yml 文件 |
| 网络 | 手动创建和指定 | 自动创建，容器自动加入 |
| 依赖 | 手动控制启动顺序 | depends_on 自动处理 |
| 可复用性 | 命令难维护 | yml 文件可提交 Git |

</details>

<details>
<summary><b>Q: 03-persistence.yml 只启动一个容器，为什么用 docker-compose？</b></summary>

单容器场景下 docker-compose 优势确实不明显，用 `docker run` 也能搞定。

**为什么 M3 还是用 docker-compose**：
1. **为 M5 做铺垫** — M5 是多服务编排（MySQL + Redis + 应用），docker-compose 的优势在那里才明显
2. **卷的声明式管理** — yml 里 `volumes:` 声明的命名卷，`docker-compose down` 时自动管理（默认保留，加 `-v` 才删除）
3. **配置可复用** — 不用每次敲一长串参数，`docker-compose up -d` 一键启动

**实际建议**：
- 单容器临时测试 → `docker run` 够用
- 需要持久化、多容器、团队协作 → `docker-compose`

</details>

---

## M4: 网络与容器通信

### 网络模式

| 模式 | 说明 | 适用场景 |
|------|------|---------|
| **bridge**（默认） | 每个容器有独立 IP，同一网络内容器可互通 | 最常用的模式 |
| **host** | 容器直接用宿主机网络，无端口映射开销 | 对网络性能要求极高时 |
| **none** | 无网络，完全隔离 | 安全隔离场景 |

### 自定义网络的核心优势

默认的 bridge 网络有个坑：**容器之间不能用容器名通信**，只能用 IP。

自定义网络解决了这个问题 — Docker 内置 DNS，同一自定义网络中的容器可以直接用**容器名作为域名**互相访问。

```
默认 bridge：容器A 无法通过 "容器B" 访问，需要 --link（已废弃）
自定义网络：容器A 直接 http://容器B:端口 即可访问
```

<br>

### 端口映射原理

```
宿主机           容器
:8080  ──→  :80

外部请求 → 宿主机 8080 端口 → Docker 转发 → 容器 80 端口
```

`-p 8080:80` 的意思是：把宿主机的 8080 端口映射到容器的 80 端口。

<br>

### 常见陷阱

- 容器内服务监听 `0.0.0.0` 而不是 `127.0.0.1`，否则外部无法访问

- 端口冲突：宿主机 8080 已被占用 → 改映射 `-p 8081:80`

- 不同 docker-compose 项目的容器默认不在同一网络，无法互通

<br>

### 验证步骤

```bash
# 启动
docker-compose -f 04-networking.yml up -d

# 从 web 容器内通过容器名访问 api 容器
docker exec net-web wget -qO- http://api:9000
# → API response from container

# 关键点：web 容器内可以直接用 "api"（容器名）作为域名访问 api 容器

# 查看网络详情
docker network inspect 04-networking_app-net

# 清理
docker-compose -f 04-networking.yml down
```

> 📂 对应代码：`04-networking.yml`

---

## M5: docker-compose 多服务编排

### docker-compose 是什么

一个 YAML 文件定义多个容器服务 + 网络 + 卷，`docker-compose up` 一键全部启动。

**日常开发中使用频率最高的 Docker 功能** — 本地跑一个完整环境（应用 + 数据库 + 缓存）只需要一个命令。

<br>

### YAML 核心结构

```yaml
services:          # 服务定义（每个服务 = 一个容器）
  mysql:           # 服务名，也是容器内的 hostname
    image: mysql:8.0       # 使用的镜像
    container_name: xxx    # 容器名（可选，不指定则自动生成）
    ports:                 # 端口映射
      - "3307:3306"
    environment:           # 环境变量
      MYSQL_ROOT_PASSWORD: root123
    volumes:               # 卷挂载
      - mysql-data:/var/lib/mysql
    networks:              # 加入的网络
      - app-net
    healthcheck:           # 健康检查
      test: ["CMD", "mysqladmin", "ping"]
      interval: 10s
      retries: 5
    depends_on:            # 依赖关系
      mysql:
        condition: service_healthy  # 等健康检查通过

volumes:             # 声明命名卷
  mysql-data:

networks:            # 声明网络
  app-net:
    driver: bridge
```

### 服务参数详解

| 参数 | 作用 | 示例 | 备注 |
|------|------|------|------|
|  |  |  |  |
| **image** | 指定镜像（直接用现成的） | `image: mysql:8.0` | 和 `build` 二选一 |
| **build** | 指定 Dockerfile 路径（构建镜像） | `build: .` 或 `build: ./app` | 和 `image` 二选一 |
| **container_name** | 容器名 | `container_name: my-mysql` | 不指定则自动生成（`<项目名>_<服务名>_<序号>`） |
| **ports** | 端口映射（宿主机:容器） | `"3307:3306"` | 必须加引号，否则 YAML 解析成数字会丢前导零 |
| **environment** | 环境变量 | `MYSQL_ROOT_PASSWORD: root123` | 每个镜像需要的变量不同，查 Docker Hub 文档 |
| **depends_on** | 依赖关系 | 见下方详解 | 控制启动顺序 |
| **healthcheck** | 健康检查 | 见下方详解 | 配合 `depends_on` 使用 |
| **restart** | 重启策略 | `restart: always` | 常用值：`no`/`always`/`on-failure`/`unless-stopped` |
| **command** | 覆盖容器启动命令 | `command: python app.py` | 覆盖镜像默认的 CMD |
|  |  |  |  |
| **volumes** | 卷挂载 | `mysql-data:/var/lib/mysql` | 命名卷:`mysql-data:/path`，绑定挂载:`./data:/path` |
| **networks** | 加入的网络 | `app-net` | 同一网络的容器可以用服务名互相访问 |

#### image vs build

```yaml
services:
  mysql:
    image: mysql:8.0      # 直接用 Docker Hub 的镜像

  app:
    build: .              # 用当前目录的 Dockerfile 构建镜像
    # build 还可以指定 Dockerfile 路径和构建参数：
    # build:
    #   context: ./app           # 构建上下文
    #   dockerfile: Dockerfile   # Dockerfile 文件名（默认就是 Dockerfile）
    #   args:                    # 构建时参数（对应 Dockerfile 的 ARG）
    #     BUILD_ENV: prod
```

**选择原则**：
- 中间件（MySQL、Redis、Nginx 等）→ 用 `image`，官方镜像直接用
- 自己的应用 → 用 `build`，从源码构建镜像

#### 多阶段构建（05-full-demo/Dockerfile）

```dockerfile
# 阶段1：编译环境（带 Maven + JDK）
FROM maven:3.9-eclipse-temurin-17 AS builder    # AS 给阶段起别名
WORKDIR /build
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B                  # 编译出 target/app.jar

# 阶段2：运行环境（只有 JRE，没有 Maven）
FROM eclipse-temurin:17-jre-alpine              # 新的 FROM = 新阶段
WORKDIR /app
RUN addgroup -S appgroup && adduser -S appuser -G appgroup   # 创建非 root 用户
COPY --from=builder /build/target/app.jar app.jar            # 从阶段1复制产物
USER appuser                                    # 切换到非 root 用户
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

**核心概念**：
- 每个 `FROM` 开始一个新阶段，最终镜像只包含最后一个阶段
- `COPY --from=builder` 从前面的阶段复制编译产物
- 阶段1的 Maven、源码、编译中间产物全部丢弃，不会进入最终镜像

**为什么要多阶段**：

| 方案 | 镜像大小 | 包含内容 |
|------|---------|---------|
| 只用阶段1运行 | ~800MB | Maven + JDK + 源码 + jar |
| 多阶段构建 | ~150MB | 只有 JRE + jar |

类比：你不会把 IDE 和源码部署到生产服务器，Docker 也一样。

**安全用户**（第12-14行）：
- `addgroup -S appgroup && adduser -S appuser -G appgroup` — Alpine Linux 创建系统用户
- `USER appuser` — 后续命令和容器运行时都用这个用户
- 默认容器以 root 运行，切换到普通用户是生产安全规范

#### healthcheck 详解

```yaml
healthcheck:
  test: ["CMD", "mysqladmin", "ping", "-h", "localhost"]  # 执行的命令
  interval: 10s      # 每 10 秒检查一次
  timeout: 5s        # 超时 5 秒算失败
  retries: 5         # 连续失败 5 次标记为 unhealthy
  start_period: 30s  # 容器启动后 30 秒内不检查（给初始化时间）
```

`test` 的格式：
- `["CMD", "命令", "参数"]` — 直接执行命令
- `["CMD-SHELL", "shell 命令"]` — 用 shell 执行（可以用管道等）
- `"NONE"` — 禁用健康检查（继承自基础镜像时用来关闭）

### depends_on 的正确用法

`depends_on` 默认只等容器**启动**，不等服务**就绪**。MySQL 容器启动了但还没初始化完，app 连上去就会报错。

解决：配合 `healthcheck` + `condition: service_healthy`，等健康检查通过才算就绪。

<br>

### 环境变量注入

application.yml 中用 `${DB_HOST:mysql}` 语法：

- 优先读取环境变量 `DB_HOST`

- 如果没设置，使用默认值 `mysql`（docker-compose 中服务名就是 hostname）

这样同一份代码，本地开发用 docker-compose 的环境变量，部署到服务器可以覆盖成真实地址。

<br>

### 常用命令

```bash
docker-compose up -d          # 后台启动所有服务
docker-compose down           # 停止并删除容器（保留卷）
docker-compose down -v        # 停止并删除容器 + 卷（数据全丢！）
docker-compose ps             # 查看服务状态
docker-compose logs -f app    # 实时查看 app 服务日志
docker-compose restart app    # 重启单个服务
docker-compose exec app sh    # 进入 app 容器
docker-compose build --no-cache app  # 强制重新构建（不用缓存）
```

<br>

### 启动和验证（05-full-demo）

```bash
# 进入项目目录
cd 05-full-demo

# 首次启动（需要构建镜像，耗时较长）
docker-compose up --build -d

# 查看服务状态（mysql 和 redis 应显示 healthy）
docker-compose ps

# 验证应用（用自己的 HTTP 工具调用）
# GET  http://localhost:8080/api/users/health   → ok
# POST http://localhost:8080/api/users          → 创建用户
# GET  http://localhost:8080/api/users          → 查询用户列表

# 清理
docker-compose down
```

> 📂 对应代码：`05-full-demo/`（完整 Spring Boot + MySQL + Redis 三服务编排项目）

---

### 实践 Q&A

> 以下是动手操作中产生的疑问和踩坑记录。

<details>
<summary><b>Q: 05-full-demo 里为什么同时有 docker-compose.yml 和 Dockerfile？</b></summary>

两个文件分工不同，配合使用：

| 文件 | 作用 | 对应服务 |
|------|------|---------|
| `docker-compose.yml` | 编排多个服务（网络、依赖、环境变量） | mysql + redis + app |
| `Dockerfile` | 定义怎么构建 app 的镜像 | 只有 app 需要 |

docker-compose.yml 里：
```yaml
services:
  mysql:
    image: mysql:8.0      # 直接拉现成镜像，不需要 Dockerfile

  redis:
    image: redis:7-alpine # 同上

  app:
    build: .              # ← 告诉 docker-compose：用当前目录的 Dockerfile 构建镜像
```

**总结**：
- mysql/redis 是中间件 → 用 `image` 拉官方镜像
- app 是你自己的项目 → 用 `build` + Dockerfile 从源码构建
- docker-compose 负责"编排"，Dockerfile 负责"构建"，各司其职

</details>

---

## M6: 容器运维与调试

### 排障四步法

容器出问题时的标准排查流程：

```
1. docker ps -a          → 看状态（Up? Exited? Restarting?）
2. docker logs <name>    → 看错误日志（90%的问题在这步就能定位）
3. docker inspect <name> → 看详细配置和退出码
4. docker exec -it <name> sh → 进入容器内部排查
```

**退出码怎么找**：`docker inspect` 返回一大段 JSON，退出码在 `.State.ExitCode` 字段。

```bash
# 直接提取退出码
docker inspect --format='{{.State.ExitCode}}' <container_name>

# 查看完整状态（ExitCode、Status、OOMKilled、Error 等）
docker inspect --format='{{json .State}}' <container_name>
```

<br>

### 退出码含义

| 退出码 | 含义 | 常见原因 |
|--------|------|---------|
| **0** | 正常退出 | 应用主动退出 |
| **1** | 应用错误 | 代码异常、配置错误 |
| **126** | 权限不足 | 命令不可执行 |
| **127** | 命令不存在 | PATH 错误或镜像缺少工具 |
| **137** | OOM Kill | 内存超限被系统杀掉，需要调大内存限制 |
| **139** | 段错误 | C/C++ 层面的内存问题 |
| **143** | SIGTERM | 收到正常停止信号 |

### 日志最佳实践

- 应用输出到 stdout/stderr（不要用文件日志），Docker 自动收集

- 生产环境设置日志轮转，防止日志撑爆磁盘：

  ```json
  // daemon.json
  { "log-driver": "json-file", "log-opts": { "max-size": "10m", "max-file": "3" } }
  ```

<br>

### 资源限制

生产环境必须限制容器资源，防止单个容器吃光宿主机。

**单机模式**（docker-compose 常用）：

```yaml
services:
  app:
    mem_limit: 512M              # 内存上限，超过可能 OOM Kill（退出码 137）
    mem_reservation: 256M        # 内存预留，Docker 尽量保证这么多
    cpus: 1.0                    # CPU 上限（1.0 = 1 核）
```

**Swarm 模式**（集群部署）：

```yaml
services:
  app:
    deploy:
      resources:
        limits:
          cpus: "1.0"
          memory: 512M
        reservations:
          cpus: "0.5"
          memory: 256M
```

#### JVM 应用的内存配置

JVM 除了堆（heap），还有元空间、线程栈、直接内存等开销。容器限制不能全给堆：

```yaml
services:
  java-app:
    mem_limit: 512M
    mem_reservation: 256M
    environment:
      # 方式一：手动设置（堆 + JVM 开销 ≈ 容器限制）
      JAVA_OPTS: "-Xms128m -Xmx384m"    # 堆最大 384MB，留 ~128MB 给 JVM 自身

      # 方式二：JDK 10+ 自动感知容器限制
      JAVA_OPTS: "-XX:MaxRAMPercentage=75.0"   # 堆最多用容器内存的 75%
```

**经验公式**：`容器 mem_limit = JVM -Xmx + 128~200MB`


<br>

### 常用运维命令速查

```bash
# 日志
docker logs -f <container>              # 实时跟踪
docker logs --tail 100 <container>      # 最近100行
docker logs --since 2h <container>      # 最近2小时

# 进入容器
docker exec -it <container> sh          # 交互式 shell
docker exec <container> ls /app         # 执行单条命令

# 资源监控
docker stats                            # 查看所有容器的实时资源占用（CPU/内存/网络）
docker stats <container>                # 只看单个容器
docker top <container>                  # 查看容器内的进程列表

# 提取配置信息（inspect 看的是静态配置，不是实时数据）
docker inspect --format='{{.State.ExitCode}}' <container>   # 退出码
docker inspect --format='{{.NetworkSettings.IPAddress}}' <container>  # 容器 IP

# 文件复制
docker cp ./local.txt <container>:/app/ # 宿主机 → 容器
docker cp <container>:/app/file.txt ./  # 容器 → 宿主机

# 清理
docker image prune -f                   # 清理悬空镜像
docker system prune -a --volumes        # 清理所有未使用资源（慎用！）
```

> **inspect vs stats**：`docker stats` 看实时占用（CPU%、内存用量），`docker inspect` 看静态配置（ExitCode、IP、挂载路径等）。日常监控用 `stats`，排障查配置用 `inspect`。

> 📂 对应代码：`06-ops-commands.sh`

---

## 综合实战：一键启动完整环境

`05-full-demo/` 是一个完整的 Spring Boot + MySQL + Redis 项目，串联了 M1-M5 所有概念。

```bash
cd 05-full-demo

# 一键启动（首次会构建镜像 + 拉取 mysql/redis 镜像，需要几分钟）
docker-compose up --build -d

# 查看所有服务状态（都应显示 healthy 或 running）
docker-compose ps

# 验证：用 HTTP 工具调用 http://localhost:8080/api/users/health
# 清理
docker-compose down
```

---

## 安全提示

本项目是学习 demo，配置文件中的密码（如 `root123`）仅用于演示，**请勿在生产环境中使用**。

生产环境的密码管理方式：
- 使用环境变量或 `.env` 文件（加入 `.gitignore`，不提交到 Git）
- 使用 Docker Secrets（Swarm 模式）
- 使用专业的密钥管理服务（Vault、AWS Secrets Manager 等）
