cmake_minimum_required(VERSION 3.22.1)

project("haiyangapp")

# 设置 C++17 标准
set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# ============================================
# GPU 支持配置
# 当 libggml-vulkan.so 存在时自动启用
# ============================================
set(VULKAN_LIB_PATH "${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml-vulkan.so")
if(EXISTS ${VULKAN_LIB_PATH})
    set(GGML_VULKAN_AVAILABLE ON)
    message(STATUS "Vulkan backend found: ${VULKAN_LIB_PATH}")
    add_definitions(-DGGML_USE_VULKAN)
else()
    set(GGML_VULKAN_AVAILABLE OFF)
    message(STATUS "Vulkan backend not found, GPU acceleration disabled")
endif()

# 添加 llama.cpp 头文件路径
include_directories(${CMAKE_SOURCE_DIR}/../../../../../llama.cpp/include)
include_directories(${CMAKE_SOURCE_DIR}/../../../../../llama.cpp/ggml/include)

# 创建 JNI 包装库
add_library(
    llama-android
    SHARED
    llama_android.cpp
)

# 查找并链接 log 库
find_library(
    log-lib
    log
)

# 导入预编译的 llama.cpp 库
add_library(llama SHARED IMPORTED)
set_target_properties(llama PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libllama.so)

add_library(ggml SHARED IMPORTED)
set_target_properties(ggml PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml.so)

add_library(ggml-base SHARED IMPORTED)
set_target_properties(ggml-base PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml-base.so)

add_library(ggml-cpu SHARED IMPORTED)
set_target_properties(ggml-cpu PROPERTIES IMPORTED_LOCATION
    ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml-cpu.so)

# Vulkan 后端（条件编译）
if(GGML_VULKAN_AVAILABLE)
    add_library(ggml-vulkan SHARED IMPORTED)
    set_target_properties(ggml-vulkan PROPERTIES IMPORTED_LOCATION
        ${CMAKE_SOURCE_DIR}/../jniLibs/${ANDROID_ABI}/libggml-vulkan.so)
endif()

# 链接所有库
set(LINK_LIBS
    llama
    ggml
    ggml-base
    ggml-cpu
    ${log-lib}
)

# 如果 Vulkan 可用，添加到链接列表
if(GGML_VULKAN_AVAILABLE)
    list(APPEND LINK_LIBS ggml-vulkan)
endif()

target_link_libraries(
    llama-android
    ${LINK_LIBS}
)

# 打印配置摘要
message(STATUS "============================================")
message(STATUS "HaiyangAPP Native Build Configuration:")
message(STATUS "  ABI: ${ANDROID_ABI}")
message(STATUS "  Vulkan GPU: ${GGML_VULKAN_AVAILABLE}")
message(STATUS "============================================")
