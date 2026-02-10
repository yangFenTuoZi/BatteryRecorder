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

JNIEXPORT jint JNICALL
Java_yangfentuozi_batteryrecorder_server_recorder_Native_nativeInit(JNIEnv *env __attribute__((unused)), jclass clazz __attribute__((unused))) {
    return init_file_cache();
}

JNIEXPORT jlong JNICALL
Java_yangfentuozi_batteryrecorder_server_recorder_Native_nativeGetVoltage(JNIEnv *env __attribute__((unused)), jclass clazz __attribute__((unused))) {
    if (!g_cache.initialized || !g_cache.voltage_fp) {
        return 0;
    }
    return read_long(g_cache.voltage_fp);
}

JNIEXPORT jlong JNICALL
Java_yangfentuozi_batteryrecorder_server_recorder_Native_nativeGetCurrent(JNIEnv *env __attribute__((unused)), jclass clazz __attribute__((unused))) {
    if (!g_cache.initialized || !g_cache.current_fp) {
        return 0;
    }
    return read_long(g_cache.current_fp);
}

JNIEXPORT jint JNICALL
Java_yangfentuozi_batteryrecorder_server_recorder_Native_nativeGetCapacity(JNIEnv *env __attribute__((unused)), jclass clazz __attribute__((unused))) {
    if (!g_cache.initialized || !g_cache.capacity_fp) {
        return 0;
    }
    return read_int(g_cache.capacity_fp);
}

JNIEXPORT jint JNICALL
Java_yangfentuozi_batteryrecorder_server_recorder_Native_nativeGetStatus(JNIEnv *env, jclass clazz __attribute__((unused))) {
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
