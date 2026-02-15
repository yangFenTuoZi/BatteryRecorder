#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#define TAG "PowerReaderJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

#define MAX_PATH_LENGTH 256
#define MAX_LINE_LENGTH 128

// File descriptor cache structure
typedef struct {
    FILE *voltage_fp;
    FILE *current_fp;
    FILE *capacity_fp;
    FILE *status_fp;
    int initialized;
} FileCache;

static FileCache g_cache = {NULL, NULL, NULL, NULL, 0};

// Open and cache file descriptors
static int init_file_cache() {
    if (g_cache.initialized) {
        return 1;
    }

    g_cache.voltage_fp = fopen("/sys/class/power_supply/battery/voltage_now", "r");
    if (!g_cache.voltage_fp) {
        LOGE("Failed to open voltage_now");
        return 0;
    }

    g_cache.current_fp = fopen("/sys/class/power_supply/battery/current_now", "r");
    if (!g_cache.current_fp) {
        LOGE("Failed to open current_now");
        fclose(g_cache.voltage_fp);
        g_cache.voltage_fp = NULL;
        return 0;
    }

    g_cache.capacity_fp = fopen("/sys/class/power_supply/battery/capacity", "r");
    if (!g_cache.capacity_fp) {
        LOGE("Failed to open capacity");
        fclose(g_cache.voltage_fp);
        g_cache.current_fp = NULL;
        fclose(g_cache.current_fp);
        return 0;
    }

    g_cache.status_fp = fopen("/sys/class/power_supply/battery/status", "r");
    if (!g_cache.status_fp) {
        LOGE("Failed to open status");
        fclose(g_cache.voltage_fp);
        g_cache.voltage_fp = NULL;
        fclose(g_cache.current_fp);
        g_cache.current_fp = NULL;
        fclose(g_cache.capacity_fp);
        g_cache.capacity_fp = NULL;
        return 0;
    }

    g_cache.initialized = 1;
    return 1;
}

// Read a long value from file
static long read_long(FILE *fp) {
    char buffer[MAX_LINE_LENGTH];
    rewind(fp);
    fflush(fp);

    if (!fgets(buffer, MAX_LINE_LENGTH, fp)) {
        return 0;
    }

    return atol(buffer);
}

// Read a int value from file
static int read_int(FILE *fp) {
    char buffer[MAX_LINE_LENGTH];
    rewind(fp);
    fflush(fp);

    if (!fgets(buffer, MAX_LINE_LENGTH, fp)) {
        return 0;
    }

    return atoi(buffer);
}

static jint native_init(JNIEnv *env __attribute__((unused)), jclass clazz __attribute__((unused))) {
    return init_file_cache();
}

static jlong native_get_voltage(JNIEnv *env __attribute__((unused)), jclass clazz __attribute__((unused))) {
    if (!g_cache.initialized || !g_cache.voltage_fp) {
        return 0;
    }
    return read_long(g_cache.voltage_fp);
}

static jlong native_get_current(JNIEnv *env __attribute__((unused)), jclass clazz __attribute__((unused))) {
    if (!g_cache.initialized || !g_cache.current_fp) {
        return 0;
    }
    return read_long(g_cache.current_fp);
}

static jint native_get_capacity(JNIEnv *env __attribute__((unused)), jclass clazz __attribute__((unused))) {
    if (!g_cache.initialized || !g_cache.capacity_fp) {
        return 0;
    }
    return read_int(g_cache.capacity_fp);
}

static jint native_get_status(JNIEnv *env __attribute__((unused)), jclass clazz __attribute__((unused))) {
    if (!g_cache.initialized || !g_cache.status_fp) {
        return 0;
    }

    FILE *fp = g_cache.status_fp;
    rewind(fp);
    fflush(fp);

    int ch = fgetc(fp);
    if (ch != EOF) {
        return ch;
    }

    return 0;
}

static int register_native_methods(JNIEnv *env) {
    const char *class_name = "yangfentuozi/batteryrecorder/server/recorder/Native";
    jclass clazz = (*env)->FindClass(env, class_name);
    if (!clazz) {
        LOGE("Failed to find class %s", class_name);
        return JNI_FALSE;
    }

    static const JNINativeMethod methods[] = {
            {"nativeInit", "()I", (void *) native_init},
            {"nativeGetVoltage", "()J", (void *) native_get_voltage},
            {"nativeGetCurrent", "()J", (void *) native_get_current},
            {"nativeGetCapacity", "()I", (void *) native_get_capacity},
            {"nativeGetStatus", "()I", (void *) native_get_status},
    };

    if ((*env)->RegisterNatives(env, clazz, methods, sizeof(methods) / sizeof(methods[0])) < 0) {
        LOGE("RegisterNatives failed for %s", class_name);
        return JNI_FALSE;
    }

    return JNI_TRUE;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved __attribute__((unused))) {
    JNIEnv *env = NULL;
    if ((*vm)->GetEnv(vm, (void **) &env, JNI_VERSION_1_6) != JNI_OK) {
        LOGE("GetEnv failed");
        return JNI_ERR;
    }

    if (!register_native_methods(env)) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}
