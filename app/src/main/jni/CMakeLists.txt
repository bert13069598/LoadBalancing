project(nanodetncnn)

cmake_minimum_required(VERSION 3.22.1)

set(OpenCV_DIR ${CMAKE_SOURCE_DIR}/opencv-mobile-4.5.1-android/sdk/native/jni)
find_package(OpenCV REQUIRED core imgproc)

set(ncnn_DIR ${CMAKE_SOURCE_DIR}/ncnn-20220420-android/${ANDROID_ABI}/lib/cmake/ncnn)
find_package(ncnn REQUIRED)

# det - nanodet
add_library(nanodetncnn SHARED nanodetncnn.cpp nanodet.cpp)
target_link_libraries(nanodetncnn ncnn ${OpenCV_LIBS} mediandk)

# seg - yolov8
#add_library(ncnnyolov8 SHARED yolov8ncnn.cpp yolov8.cpp)
#target_link_libraries(ncnnyolov8 ncnn ${OpenCV_LIBS} mediandk)