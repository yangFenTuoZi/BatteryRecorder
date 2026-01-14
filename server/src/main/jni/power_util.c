#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>

#define TAG "PowerUtilJNI"
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

// Read a string from file
static int read_string(FILE *fp, char *buffer, size_t size) {
    rewind(fp);
    fflush(fp);

    if (!fgets(buffer, size, fp)) {
        return 0;
    }

    // Trim whitespace
    size_t len = strlen(buffer);
    while (len > 0 && (buffer[len - 1] == ' ' || buffer[len - 1] == '\n' ||
                       buffer[len - 1] == '\r' || buffer[len - 1] == '\t')) {
        buffer[--len] = '\0';
    }

    return 1;
}

JNIEXPORT jint JNICALL
Java_yangfentuozi_batteryrecorder_server_PowerUtil_nativeInit(JNIEnv *env __attribute__((unused)), jclass clazz __attribute__((unused))) {
    return init_file_cache();
}

JNIEXPORT jlong JNICALL
Java_yangfentuozi_batteryrecorder_server_PowerUtil_nativeGetVoltage(JNIEnv *env __attribute__((unused)), jclass clazz __attribute__((unused))) {
    if (!g_cache.initialized || !g_cache.voltage_fp) {
        return 0;
    }
    return read_long(g_cache.voltage_fp);
}

JNIEXPORT jlong JNICALL
Java_yangfentuozi_batteryrecorder_server_PowerUtil_nativeGetCurrent(JNIEnv *env __attribute__((unused)), jclass clazz __attribute__((unused))) {
    if (!g_cache.initialized || !g_cache.current_fp) {
        return 0;
    }
    return read_long(g_cache.current_fp);
}

JNIEXPORT jint JNICALL
Java_yangfentuozi_batteryrecorder_server_PowerUtil_nativeGetCapacity(JNIEnv *env __attribute__((unused)), jclass clazz __attribute__((unused))) {
    if (!g_cache.initialized || !g_cache.capacity_fp) {
        return 0;
    }
    return (jint)read_long(g_cache.capacity_fp);
}

JNIEXPORT jstring JNICALL
Java_yangfentuozi_batteryrecorder_server_PowerUtil_nativeGetStatus(JNIEnv *env, jclass clazz __attribute__((unused))) {
    char buffer[MAX_LINE_LENGTH];

    if (!g_cache.initialized || !g_cache.status_fp) {
        return (*env)->NewStringUTF(env, "Unknown");
    }

    if (!read_string(g_cache.status_fp, buffer, MAX_LINE_LENGTH)) {
        return (*env)->NewStringUTF(env, "Unknown");
    }

    return (*env)->NewStringUTF(env, buffer);
}
