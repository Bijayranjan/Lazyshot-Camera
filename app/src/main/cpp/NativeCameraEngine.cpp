#include <android/log.h>
#include <android/native_window_jni.h>
#include <camera/NdkCameraDevice.h>
#include <camera/NdkCameraError.h>
#include <camera/NdkCameraManager.h>
#include <camera/NdkCameraMetadata.h>
#include <jni.h>
#include <media/NdkImage.h>
#include <media/NdkImageReader.h>
#include <string>

#define LOG_TAG "NativeCameraEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Image Formats
#define AIMAGE_FORMAT_YCBCR_P010 0x36
#define AIMAGE_FORMAT_RAW16 0x20

static AImageReader *rawReader = nullptr;
static AImageReader *yuvReader = nullptr;

void OnImageAvailable(void *context, AImageReader *reader) {
  AImage *image = nullptr;
  media_status_t status = AImageReader_acquireNextImage(reader, &image);

  if (status != AMEDIA_OK || image == nullptr) {
    LOGE("Failed to acquire image");
    return;
  }

  int format = 0;
  AImage_getFormat(image, &format);

  int64_t timestamp = 0;
  AImage_getTimestamp(image, &timestamp);

  LOGI("Image acquired! Format: 0x%x, Timestamp: %lld", format, timestamp);

  // TODO: Process image buffer here (e.g., access planes)
  // AImage_getPlaneData(image, ...);

  AImage_delete(image);
}

extern "C" JNIEXPORT jobject JNICALL
Java_com_example_lazyshot_CameraRepository_createNativeImageReader(
    JNIEnv *env, jobject /* this */, jint width, jint height, jint format) {

  AImageReader *reader = nullptr;
  media_status_t status = AImageReader_new(width, height, format, 4, &reader);

  if (status != AMEDIA_OK) {
    LOGE("Failed to create AImageReader");
    return nullptr;
  }

  AImageReader_ImageListener listener;
  listener.context = nullptr;
  listener.onImageAvailable = OnImageAvailable;

  status = AImageReader_setImageListener(reader, &listener);
  if (status != AMEDIA_OK) {
    LOGE("Failed to set image listener");
    return nullptr;
  }

  if (format == AIMAGE_FORMAT_RAW16) {
    rawReader = reader;
  } else if (format == AIMAGE_FORMAT_YCBCR_P010) {
    yuvReader = reader;
  }

  ANativeWindow *window = nullptr;
  status = AImageReader_getWindow(reader, &window);
  if (status != AMEDIA_OK) {
    LOGE("Failed to get window from reader");
    return nullptr;
  }

  return ANativeWindow_toSurface(env, window);
}
