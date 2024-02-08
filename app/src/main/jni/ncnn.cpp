#include <android/asset_manager_jni.h>
#include <android/native_window_jni.h>
#include <android/native_window.h>

#include <android/log.h>

#include <jni.h>

#include <string>
#include <vector>

#include <platform.h>
#include <benchmark.h>

#include "nanodet.h"
#include "yolov8.h"
#include "nanodet_tstl.h"

#include <opencv2/core/core.hpp>
#include <opencv2/imgproc/imgproc.hpp>

#include "cpu.h"

#if __ARM_NEON
#include <arm_neon.h>
#endif // __ARM_NEON

static int draw_unsupported(cv::Mat &rgb) {
    const char text[] = "unsupported";

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 1.0, 1, &baseLine);

    int y = (rgb.rows - label_size.height) / 2;
    int x = (rgb.cols - label_size.width) / 2;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y),
                                cv::Size(label_size.width, label_size.height + baseLine)),
                  cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 1.0, cv::Scalar(0, 0, 0));

    return 0;
}

static int draw_fps(cv::Mat &rgb) {
    // resolve moving average
    float avg_fps = 0.f;
    {
        static double t0 = 0.f;
        static float fps_history[10] = {0.f};

        double t1 = ncnn::get_current_time();
        if (t0 == 0.f) {
            t0 = t1;
            return 0;
        }

        float fps = 1000.f / (t1 - t0);
        t0 = t1;

        for (int i = 9; i >= 1; i--) {
            fps_history[i] = fps_history[i - 1];
        }
        fps_history[0] = fps;

        if (fps_history[9] == 0.f) {
            return 0;
        }

        for (int i = 0; i < 10; i++) {
            avg_fps += fps_history[i];
        }
        avg_fps /= 10.f;
    }

    char text[32];
    sprintf(text, "FPS=%.2f", avg_fps);

    int baseLine = 0;
    cv::Size label_size = cv::getTextSize(text, cv::FONT_HERSHEY_SIMPLEX, 0.5, 1, &baseLine);

    int y = 0;
    int x = rgb.cols - label_size.width;

    cv::rectangle(rgb, cv::Rect(cv::Point(x, y),
                                cv::Size(label_size.width, label_size.height + baseLine)),
                  cv::Scalar(255, 255, 255), -1);

    cv::putText(rgb, text, cv::Point(x, y + label_size.height),
                cv::FONT_HERSHEY_SIMPLEX, 0.5, cv::Scalar(0, 0, 0));

    return 0;
}

jobject MatToBitmap(JNIEnv *env, cv::Mat src) {
    jclass bitmapConfigClass = env->FindClass("android/graphics/Bitmap$Config");
    jfieldID argb8888FieldID = env->GetStaticFieldID(bitmapConfigClass, "ARGB_8888",
                                                     "Landroid/graphics/Bitmap$Config;");
    jobject argb8888Config = env->GetStaticObjectField(bitmapConfigClass, argb8888FieldID);

    jclass bitmapClass = env->FindClass("android/graphics/Bitmap");
    jmethodID createBitmapMethodID = env->GetStaticMethodID(bitmapClass, "createBitmap",
                                                            "(IILandroid/graphics/Bitmap$Config;)Landroid/graphics/Bitmap;");

    jobject bitmap = env->CallStaticObjectMethod(bitmapClass, createBitmapMethodID, src.cols,
                                                 src.rows, argb8888Config);

    AndroidBitmapInfo bitmapInfo;
    void *pixels = 0;
    if (AndroidBitmap_getInfo(env, bitmap, &bitmapInfo) < 0) {
        return NULL;
    }

    if (AndroidBitmap_lockPixels(env, bitmap, &pixels) < 0) {
        return NULL;
    }

    cv::Mat dst(bitmapInfo.height, bitmapInfo.width, CV_8UC4, pixels);

    if (src.type() == CV_8UC3) {
        cv::cvtColor(src, dst, cv::COLOR_RGB2RGBA);
    } else if (src.type() == CV_8UC1) {
        cv::cvtColor(src, dst, cv::COLOR_GRAY2RGBA);
    } else {
        src.copyTo(dst);
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return bitmap;
}

static NanoDet *g_nanodet = 0;
static Yolov8 *g_yolo = 0;
static NanoDet2 *g_nanodet2 = 0;
static ncnn::Mutex lock;

extern "C" {

JNIEXPORT jint
JNI_OnLoad(JavaVM * vm , void *reserved ) {
__android_log_print(ANDROID_LOG_DEBUG, "ncnn" , "JNI_OnLoad" ) ;

return JNI_VERSION_1_4;
}

JNIEXPORT void JNI_OnUnload(JavaVM * vm, void * reserved) {
    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "JNI_OnUnload");

    {
        ncnn::MutexLockGuard g(lock);

        delete g_nanodet;
        delete g_yolo;
        delete g_nanodet2;
        g_nanodet = 0;
        g_yolo = 0;
        g_nanodet2 = 0;
    }
}

JNIEXPORT jboolean

JNICALL
Java_com_example_demoproject_1master_NCNN_loadModel(JNIEnv *env,
                                                             jobject thiz,
                                                             jobject assetManager,
                                                             jint cpugpu) {
    AAssetManager *mgr = AAssetManager_fromJava(env, assetManager);

    __android_log_print(ANDROID_LOG_DEBUG, "ncnn", "loadModel %p", mgr);

    bool use_gpu = (int) cpugpu == 1;

    // reload
    {
        ncnn::MutexLockGuard g(lock);

        if (!g_nanodet && !g_yolo && !g_nanodet2){
            g_yolo = new Yolov8;
            g_nanodet = new NanoDet;
            g_nanodet2 = new NanoDet2;
            g_yolo->load(mgr, use_gpu);
            g_nanodet->load(mgr, use_gpu);
            g_nanodet2->load(mgr, use_gpu);
            }
    }

    return JNI_TRUE;
}

JNIEXPORT jboolean

JNICALL
Java_com_example_demoproject_1master_NCNN_homogeneousComputing(JNIEnv *env,
                  jobject thiz,
                  jobject imageView,
                  jobject bitmap,
                  jbooleanArray opt) {

    AndroidBitmapInfo info;
    void *bitmapPixels;
    if (AndroidBitmap_getInfo(env,
                              bitmap, &info) < 0 ||
                              info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
                              AndroidBitmap_lockPixels(env, bitmap, &bitmapPixels) < 0)
        return JNI_FALSE;

// Create a cv::Mat from the bitmap data
    int width = info.width;
    int height = info.height;
    cv::Mat rgba(height, width, CV_8UC4, bitmapPixels);
    cv::Mat rgb;
    cv::cvtColor(rgba, rgb, cv::COLOR_RGBA2RGB);

    ncnn::MutexLockGuard g(lock);

// Prediction
    jsize size = env->GetArrayLength(opt);
    std::vector<uint8_t> option(size);
    env->GetBooleanArrayRegion(opt, 0, size, reinterpret_cast<jboolean *>(&option[0]));

    if (g_yolo && g_nanodet) {
        if (option[0]) {
            cv::Mat mask = cv::Mat::zeros(cv::Size(rgb.cols, rgb.rows), CV_8UC1);
            g_yolo->detect(rgb, mask);
            g_yolo->draw(rgb, mask);
        }
        if (option[1]) {
            std::vector<NanoDetObject> objects;
            g_nanodet->detect(rgb, objects);
            g_nanodet->draw(rgb, objects);
        }
        if (option[2]) {
            std::vector<NanoDet2Object> objects;
            g_nanodet2->detect(rgb, objects);
            g_nanodet2->draw(rgb, objects);
        }

// 이미지 뷰 업데이트 JNI 호출
        jclass imageViewClass = env->GetObjectClass(imageView);
        jmethodID setImageBitmapMethod = env->GetMethodID(imageViewClass,
                                                          "setImageBitmap",
                                                          "(Landroid/graphics/Bitmap;)V");

//        draw_fps(rgb);

// 자바로 반환, imageView 나타내기
        jobject jbitmap = MatToBitmap(env, rgb);
        env->CallVoidMethod(imageView, setImageBitmapMethod, jbitmap);
    } else {
        draw_unsupported(rgb);
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}


JNIEXPORT jboolean

JNICALL
Java_com_example_demoproject_1master_NCNN_heterogeneousComputing(JNIEnv *env,
                                                    jobject thiz,
                                                    jobject image_view,
                                                    jobject bitmap,
                                                    jstring datadet,
                                                    jobject dataseg) {
    AndroidBitmapInfo info;
    void *bitmapPixels;
    if (AndroidBitmap_getInfo(env,
                              bitmap, &info) < 0 ||
                              info.format != ANDROID_BITMAP_FORMAT_RGBA_8888 ||
                              AndroidBitmap_lockPixels(env, bitmap, &bitmapPixels) < 0)
        return JNI_FALSE;


    // Create a cv::Mat from the bitmap data
    int width = info.width;
    int height = info.height;
    cv::Mat rgba(height, width, CV_8UC4, bitmapPixels);
    cv::Mat rgb;
    cv::cvtColor(rgba, rgb, cv::COLOR_RGBA2RGB);

    ncnn::MutexLockGuard g(lock);

    if (g_nanodet && g_yolo) {
        // detection
        if (datadet != nullptr) {
            const char *cstr = env->GetStringUTFChars(datadet, nullptr);

            // 문자열을 Object로 반환
            if (cstr != nullptr && cstr != " ") {
                std::string bboxString(cstr); // C 스타일 문자열을 C++의 std::string으로 복사
                env->ReleaseStringUTFChars(datadet, cstr); // 메모리 릴리스

                std::vector<NanoDetObject> objects;
                std::istringstream iss(bboxString);
                std::string token;
                while (std::getline(iss, token, '/')) {
                    NanoDetObject obj;
                    sscanf(token.c_str(), "%d %f %f %f %f %f",
                           &obj.label,
                           &obj.prob,
                           &obj.rect.x,
                           &obj.rect.y,
                           &obj.rect.width,
                           &obj.rect.height);

                    objects.push_back(obj);
                }
                g_nanodet->draw(rgb, objects);
                objects.clear();
            }
        }

        // segmentation
        if (dataseg != nullptr) {
            void *maskPixels;
            if (AndroidBitmap_lockPixels(env, dataseg, &maskPixels) < 0)
                return JNI_FALSE;

            cv::Mat rgba_(height, width, CV_8UC4, maskPixels);
            cv::Mat mask;
            cv::cvtColor(rgba_, mask, cv::COLOR_RGBA2GRAY);

            g_yolo->draw(rgb, mask);
            rgba_.release();
            mask.release();
        }

        draw_fps(rgb);

        // 자바로 반환환
        jclass imageViewClass = env->GetObjectClass(image_view);
        jmethodID setImageBitmapMethod = env->GetMethodID(imageViewClass,
                                                          "setImageBitmap",
                                                          "(Landroid/graphics/Bitmap;)V");
        jobject jbitmap = MatToBitmap(env, rgb);
        env->CallVoidMethod(image_view, setImageBitmapMethod, jbitmap);
    } else {
        draw_unsupported(rgb);
    }

    AndroidBitmap_unlockPixels(env, bitmap);
    return JNI_TRUE;
}
}