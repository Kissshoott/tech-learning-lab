# ===== 多阶段构建 Dockerfile =====
# 场景：Java Spring Boot 应用
# stage1 用完整 Maven 镜像编译，stage2 只拷贝 jar 到精简 JRE 镜像
# 最终镜像从 ~800MB 缩减到 ~150MB

# ----- 阶段1：构建 -----
FROM maven:3.9-eclipse-temurin-17 AS builder
WORKDIR /build
# 先复制 pom.xml 下载依赖，利用层缓存
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn package -DskipTests -B

# ----- 阶段2：运行 -----
# 只用 JRE + Alpine，不包含编译工具
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
# 非 root 用户运行，生产安全基线
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
# 只从 stage1 拷贝产物 jar，不拷贝编译环境
COPY --from=builder /build/target/app.jar app.jar
USER appuser
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

# ===== 关键对比 =====
# 单阶段构建：最终镜像包含 Maven + JDK + 源码 + 编译产物 ≈ 800MB
# 多阶段构建：最终镜像只有 JRE + jar ≈ 150MB
# 生产环境务必使用多阶段构建
