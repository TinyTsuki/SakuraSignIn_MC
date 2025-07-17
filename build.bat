@echo off
chcp 65001 > nul
setlocal enabledelayedexpansion

:: 配置常量
set EXCLUDED_BRANCHES=main
set INCLUDED_BRANCHES=
set JDK_PROPERTIES_FILE=jdks.properties
set GRADLE_USER_HOME=F:\Data\Gradle

:: 如果白名单不为空，优先使用白名单
if not "%INCLUDED_BRANCHES%" == "" (
    for %%b in (%INCLUDED_BRANCHES%) do (
        call :build_branch %%b
    )
) else (
    :: 获取所有远程分支并排除指定的分支
    for /f "tokens=*" %%b in ('git branch -r ^| findstr /v "%EXCLUDED_BRANCHES%"') do (
        set "branch=%%b"
        set "branch=!branch:origin/=!"
        call :build_branch !branch!
    )
)

git checkout main > nul 2>&1


goto :eof

:: 构建指定分支
:build_branch
set "branch_name=%1"
echo ===================================================================
echo Building branch: !branch_name!


:: 切换到目标分支
git checkout !branch_name! > nul 2>&1
if errorlevel 1 (
    echo Error: Failed to switch to branch !branch_name!
    exit /b 1
)

:: 拉取最新的远程代码
git pull origin !branch_name! > nul 2>&1
if errorlevel 1 (
    echo Error: Failed to pull remote code for branch !branch_name!
    exit /b 1
)

:: 读取 build.gradle 中的 Java 版本
set "java_version="
for /f "tokens=3 delims== " %%v in ('findstr /r /c:"def javaVer *=" build.gradle') do (
    set "java_version=%%v"
)

:: 清理版本号
set "java_version=!java_version:.=.!"
set "java_version=!java_version:"=!"
set "java_version=!java_version: =!"

echo Detected Java version: !java_version!

:: 从 jdks.properties 获取jdk路径
set "jdk_path="
for /f "tokens=1,2 delims==" %%j in (%JDK_PROPERTIES_FILE%) do (
    if /i "%%j"=="jdk!java_version!" set "jdk_path=%%k"
)

if not defined jdk_path (
    echo Error: Could not find JDK path for Java version !java_version!
    goto :eof
)

:: 设置 JAVA_HOME 并更新 PATH
set "JAVA_HOME=!jdk_path!"
set "PATH=!JAVA_HOME!\bin;!PATH!"

:: 执行构建
call gradlew.bat clean build

if errorlevel 1 (
    echo Error: Build failed for branch !branch_name!
    exit /b 1
)

echo Build completed for branch !branch_name!


:: 清理状态
goto :eof
