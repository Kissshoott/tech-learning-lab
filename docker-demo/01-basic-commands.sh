#!/bin/bash
# ============================================================
# M1: 镜像与容器基础操作 - 可直接复制运行的命令示例
# ============================================================
# 前置条件：Docker Desktop 已安装并启动
# 验证：docker --version

# ------ 1. 镜像操作 ------

# 拉取镜像（不指定 tag 默认拉 latest）
# 生产环境务必锁具体版本，如 nginx:1.25，不要用 latest
docker pull nginx:1.25

# 查看本地镜像列表
docker images

# 查看悬空镜像（<none>:<none>，旧版本被覆盖后的残留）
docker images --filter "dangling=true"

# 查看镜像详细信息（层结构、环境变量等）
docker inspect nginx:1.25

# 删除镜像（先停掉引用该镜像的容器）
docker rmi nginx:1.25

# 安全清理：只删悬空镜像（<none>:<none>）
docker image prune -f

# 警告：以下命令删除所有未被容器使用的镜像（包括你手动 pull 的！）
# docker image prune -a -f


# ------ 2. 容器生命周期 ------

# 前台运行（Ctrl+C 停止，容器随之停止）
docker run nginx:1.25

# 后台运行 + 端口映射（宿主机8080 → 容器80）
docker run -d --name my-nginx -p 8080:80 nginx:1.25

# 验证：浏览器打开 http://localhost:8080 看到 Nginx 欢迎页

# 注意：容器参数（-p, -e, --name, -v）创建后不可修改
# 想改只能删掉重建：docker rm -f my-nginx && docker run -d ...

# 如果名字已存在会报错：
# Error: The container name "/my-nginx" is already in use
# 解决：先删除旧容器再创建
# docker rm -f my-nginx
# docker run -d --name my-nginx -p 8080:80 nginx:1.25

# 查看运行中的容器
docker ps

# 查看所有容器（含已停止的）
docker ps -a

# 停止容器
docker stop my-nginx

# 启动已停止的容器
docker start my-nginx

# 重启容器
docker restart my-nginx

# 删除容器（必须先停止）
docker rm my-nginx

# 强制删除运行中的容器（慎用）
# docker rm -f my-nginx


# ------ 3. 常用参数 ------

# -d：后台运行
# -p：端口映射（宿主机端口:容器端口）
# --name：给容器命名（不指定则随机生成）
# -e：设置环境变量
# -v：挂载卷或目录（宿主机路径:容器路径）
# --network：指定网络

# 带环境变量运行
docker run -d --name my-nginx \
  -p 8080:80 \
  -e NGINX_PORT=80 \
  nginx:1.25

# 查看容器日志（-f 实时跟踪，类似 tail -f）
docker logs -f my-nginx

# 进入容器内部（交互式 shell）
docker exec -it my-nginx sh

# 查看容器资源占用（CPU/内存/网络）
docker stats my-nginx

# 清理完毕后
docker rm -f my-nginx
