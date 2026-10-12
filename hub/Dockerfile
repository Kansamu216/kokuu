# VoidkitHub 容器镜像 —— 最省事的一套跨平台部署方式。
#
# 构建（在 hub/ 目录下）：
#   docker build -t voidkithub:1.0 .
#
# 运行：
#   docker run -d --name voidkithub \
#     -p 9000:9000 \
#     -v voidkithub-data:/data \
#     --restart unless-stopped \
#     voidkithub:1.0
#
# 看账号密码（首次启动会打印）：
#   docker logs voidkithub | head -20
#
# 资源占用：镜像约 15MB，常驻内存 20MB 量级，1 核 256MB 的小机器足够。

# ---------- 构建阶段 ----------
FROM golang:1.22-alpine AS build
WORKDIR /src

# 纯 Go 无第三方依赖，只需要 go.mod
COPY go.mod ./
COPY *.go ./
COPY web ./web

RUN CGO_ENABLED=0 go build -trimpath -ldflags "-s -w" -o /out/voidkithub .

# ---------- 运行阶段 ----------
FROM alpine:3.20

# 用非 root 跑；--no-create-home 减小体积
RUN adduser -D -H -s /sbin/nologin voidkit

WORKDIR /app
COPY --from=build /out/voidkithub /app/voidkithub

# 数据目录（config.json / state.json / hub.log）挂出来
RUN mkdir -p /data && chown voidkit:voidkit /data
VOLUME ["/data"]

USER voidkit
EXPOSE 9000

# 0.0.0.0 才能在容器外访问
ENTRYPOINT ["/app/voidkithub", "-listen", "0.0.0.0:9000", "-data", "/data"]
