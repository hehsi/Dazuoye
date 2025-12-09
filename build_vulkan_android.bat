@echo off
REM ============================================
REM  llama.cpp Vulkan Android 编译脚本
REM  用于编译支持 GPU 加速的 llama.cpp 库
REM ============================================

setlocal enabledelayedexpansion

REM 设置路径
set ANDROID_SDK=D:\Andriod_sdk
set NDK_VERSION=29.0.14206865
set CMAKE_VERSION=3.22.1
set ANDROID_NDK=%ANDROID_SDK%\ndk\%NDK_VERSION%
set CMAKE_PATH=%ANDROID_SDK%\cmake\%CMAKE_VERSION%\bin
set NINJA_PATH=%CMAKE_PATH%

REM 添加到 PATH
set PATH=%CMAKE_PATH%;%NINJA_PATH%;%PATH%

REM 项目路径
set PROJECT_DIR=%~dp0
set LLAMA_DIR=%PROJECT_DIR%llama.cpp
set BUILD_DIR=%LLAMA_DIR%\build-android-vulkan
set OUTPUT_DIR=%PROJECT_DIR%app\src\main\jniLibs\arm64-v8a

echo ============================================
echo  llama.cpp Vulkan Android Build Script
echo ============================================
echo.
echo NDK Path: %ANDROID_NDK%
echo CMake Path: %CMAKE_PATH%
echo Build Dir: %BUILD_DIR%
echo Output Dir: %OUTPUT_DIR%
echo.

REM 检查必要组件
if not exist "%ANDROID_NDK%" (
    echo ERROR: NDK not found at %ANDROID_NDK%
    echo Please install Android NDK first.
    pause
    exit /b 1
)

if not exist "%CMAKE_PATH%\cmake.exe" (
    echo ERROR: CMake not found at %CMAKE_PATH%
    echo Please install CMake via Android SDK Manager.
    pause
    exit /b 1
)

REM 检查 MinGW/GCC (用于编译主机工具)
where gcc >nul 2>&1
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo WARNING: GCC not found in PATH!
    echo.
    echo To compile Vulkan shaders, you need a host compiler.
    echo Please install MinGW-w64:
    echo   1. Download from: https://github.com/niXman/mingw-builds-binaries/releases
    echo   2. Or use winget: winget install -e --id MinGW.get
    echo   3. Add MinGW\bin to your PATH
    echo.
    echo After installing, run this script again.
    pause
    exit /b 1
)

echo GCC found:
gcc --version 2>&1 | findstr /n "^" | findstr "^1:"
echo.

REM 检查 llama.cpp 源码
if not exist "%LLAMA_DIR%" (
    echo llama.cpp not found, cloning...
    git clone --depth 1 https://github.com/ggerganov/llama.cpp.git "%LLAMA_DIR%"
    if %ERRORLEVEL% NEQ 0 (
        echo ERROR: Failed to clone llama.cpp
        pause
        exit /b 1
    )
)

REM 进入 llama.cpp 目录
cd /d "%LLAMA_DIR%"

REM 创建构建目录
if exist "%BUILD_DIR%" (
    echo Cleaning previous build...
    rmdir /s /q "%BUILD_DIR%"
)
mkdir "%BUILD_DIR%"
cd /d "%BUILD_DIR%"

echo.
echo [1/3] Running CMake configuration...
echo.

REM 检查并下载 Vulkan 头文件
if not exist "%LLAMA_DIR%\Vulkan-Hpp" (
    echo Downloading Vulkan C++ headers...
    git clone --depth 1 https://github.com/KhronosGroup/Vulkan-Hpp.git "%LLAMA_DIR%\Vulkan-Hpp"
)
if not exist "%LLAMA_DIR%\Vulkan-Headers" (
    echo Downloading Vulkan headers...
    git clone --depth 1 https://github.com/KhronosGroup/Vulkan-Headers.git "%LLAMA_DIR%\Vulkan-Headers"
)

REM 设置 Vulkan 头文件路径
set VULKAN_HPP_PATH=%LLAMA_DIR%\Vulkan-Hpp
set VULKAN_HEADERS_PATH=%LLAMA_DIR%\Vulkan-Headers\include

REM 运行 CMake 配置
cmake .. ^
    -G "Ninja" ^
    -DCMAKE_MAKE_PROGRAM="%NINJA_PATH%\ninja.exe" ^
    -DCMAKE_TOOLCHAIN_FILE=%ANDROID_NDK%\build\cmake\android.toolchain.cmake ^
    -DANDROID_ABI=arm64-v8a ^
    -DANDROID_PLATFORM=android-26 ^
    -DANDROID_STL=c++_shared ^
    -DCMAKE_BUILD_TYPE=Release ^
    -DGGML_VULKAN=ON ^
    -DGGML_VULKAN_CHECK_RESULTS=OFF ^
    -DLLAMA_CURL=OFF ^
    -DLLAMA_BUILD_TESTS=OFF ^
    -DLLAMA_BUILD_EXAMPLES=OFF ^
    -DLLAMA_BUILD_SERVER=OFF ^
    "-DCMAKE_CXX_FLAGS=-I%VULKAN_HPP_PATH% -I%VULKAN_HEADERS_PATH%"

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: CMake configuration failed!
    pause
    exit /b 1
)

echo.
echo [2/3] Building...
echo.

REM 编译
cmake --build . --config Release -j %NUMBER_OF_PROCESSORS%

if %ERRORLEVEL% NEQ 0 (
    echo.
    echo ERROR: Build failed!
    pause
    exit /b 1
)

echo.
echo [3/3] Copying libraries to project...
echo.

REM 备份旧库
if not exist "%OUTPUT_DIR%\backup" mkdir "%OUTPUT_DIR%\backup"
for %%f in ("%OUTPUT_DIR%\*.so") do (
    if exist "%%f" (
        echo Backing up: %%~nxf
        copy /y "%%f" "%OUTPUT_DIR%\backup\" >nul
    )
)

REM 查找并复制新库
echo Searching for built libraries...

REM 主要库文件
for %%d in (. bin lib src ggml\src) do (
    for %%f in (libllama.so libggml.so libggml-base.so libggml-cpu.so libggml-vulkan.so) do (
        if exist "%%d\%%f" (
            echo Found: %%d\%%f
            copy /y "%%d\%%f" "%OUTPUT_DIR%\" >nul
            echo   Copied: %%f
        )
    )
)

REM 检查 Vulkan 库是否成功复制
if exist "%OUTPUT_DIR%\libggml-vulkan.so" (
    echo.
    echo SUCCESS: Vulkan backend library copied!
) else (
    echo.
    echo WARNING: libggml-vulkan.so not found in build output!
    echo GPU acceleration may not work.
)

echo.
echo ============================================
echo  Build completed!
echo ============================================
echo.
echo Libraries in %OUTPUT_DIR%:
dir /b "%OUTPUT_DIR%\*.so" 2>nul
echo.
echo Next steps:
echo   1. Open project in Android Studio
echo   2. Clean and rebuild the project
echo   3. Test on a device with Vulkan support
echo.
echo The app will automatically use GPU if available,
echo and silently fallback to CPU if not.
echo.

pause
