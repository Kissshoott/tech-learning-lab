#!/bin/bash
# ============================================================
# M6: 容器运维与调试 - 常用命令速查
# ============================================================

# ------ 1. 日志查看 ------

# 查看容器日志
docker logs <container_name>

# 实时跟踪日志（类似 tail -f）
docker logs -f <container_name>

# 查看最近 100 行
docker logs --tail 100 <container_name>

# 查看指定时间之后的日志
docker logs --since 2h <container_name>

# 查看指定时间段的日志
docker logs --since "2024-01-01T00:00:00" --until "2024-01-01T12:00:00" <container_name>


# ------ 2. 进入容器 ------

# 进入运行中的容器（交互式 shell）
docker exec -it <container_name> sh
# 如果容器没有 sh，试 bash
docker exec -it <container_name> bash

# 在容器中执行单条命令（不进入交互模式）
docker exec <container_name> ls /app
docker exec <container_name> cat /etc/os-release

# 在容器中安装工具（临时调试用，容器重建后丢失）
docker exec -it <container_name> apk add curl


# ------ 3. 资源监控 ------

# 实时查看所有容器的 CPU/内存/网络使用
docker stats

# 查看单个容器
docker stats <container_name>

# 查看容器详细信息
docker inspect <container_name>

# 查看容器进程列表
docker top <container_name>


# ------ 4. 镜像清理 ------

# 清理悬空镜像（没有 tag 的废弃镜像）
docker image prune -f

# 清理所有未使用的镜像
docker image prune -a -f

# 清理所有未使用的资源（镜像+容器+网络+卷，慎用！）
docker system prune -a --volumes


# ------ 5. 容器复制文件 ------

# 宿主机 → 容器
docker cp ./local-file.txt <container_name>:/app/file.txt

# 容器 → 宿主机
docker cp <container_name>:/app/file.txt ./local-file.txt


# ------ 6. 排障流程 ------
# 容器启动失败时的排查步骤：
#
# 1. docker ps -a          → 看容器状态（Exited? Restarting?）
# 2. docker logs <name>    → 看错误日志
# 3. docker inspect <name> → 看详细配置和退出码
# 4. 退出码含义：
#    0   = 正常退出
#    1   = 应用错误
#    137 = 被 OOM Kill（内存超限）
#    139 = 段错误
#    143 = 收到 SIGTERM（正常停止）
