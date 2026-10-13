@echo off
chcp 936 >nul
title VoidkitHub - 多服务器管理平台
cd /d "%~dp0"

echo ============================================================
echo   VoidkitHub  多服务器管理平台
echo.
echo   面板地址:  http://127.0.0.1:9000
echo   平台令牌:  下面日志里会打印（登录面板要用）
echo   数据目录:  .\hubdata  (config.json / state.json)
echo.
echo   第一次使用：打开面板 -> 服务器 -> 添加服务器
echo ============================================================
echo.

if not exist voidkithub.exe (
  echo 找不到 voidkithub.exe。先构建一次：
  echo.
  echo   set "GOROOT=%%USERPROFILE%%\.workbuddy\binaries\go\go"
  echo   "%%GOROOT%%\bin\go.exe" build -o voidkithub.exe .
  echo.
  echo 或者去 https://go.dev/dl/ 装个 Go 1.22+，然后在本目录执行：
  echo   go build -o voidkithub.exe .
  echo.
  pause
  exit /b 1
)

REM -listen 0.0.0.0:9000 表示允许从其它机器访问面板。
REM 只在本机用的话可以改成 127.0.0.1:9000 更安全。
REM 输出同时写进 hubdata\hub.log -- 窗口万一闪退，去那里看原因。
voidkithub.exe -listen 0.0.0.0:9000 -data .\hubdata

echo.
echo [Hub 已退出]
pause >nul
