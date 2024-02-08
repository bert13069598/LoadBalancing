project(nanodetncnn)

cmake_minimum_required(VERSION 3.22.1)

set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/opencv-mobile-4.8.0-android/sdk/native/jni)
find_package(OpenCV REQUIRED core imgproc)

set(ncnn_DIR ${CMAKE_SOURCE_DIR}/ncnn-20230816-android/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

add_library(ncnntotal SHARED ncnn.cpp yolov8.cpp nanodet.cpp)
target_link_libraries(ncnntotal ncnn ${OpenCV_LIBS} mediandk)