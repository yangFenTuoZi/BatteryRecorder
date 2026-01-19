#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <fcntl.h>
#include <unistd.h>
#include <errno.h>
#include <android/log.h>

#define TAG "DataWriterJNI"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// JNI: Open a file for writing
JNIEXPORT jint JNICALL
Java_yangfentuozi_batteryrecorder_server_Native_nativeOpenFile(
        JNIEnv *env, jclass clazz __attribute__((unused)),
        jstring path_jstr, jint mode) {
    const char *path = (*env)->GetStringUTFChars(env, path_jstr, NULL);
    if (!path) {
        return -1;
    }

    int flags;
    if (mode == 0) {
        flags = O_WRONLY | O_CREAT | O_APPEND;
    } else {
        flags = O_WRONLY | O_CREAT | O_TRUNC;
    }

    int fd = open(path, flags, 0644);
    if (fd < 0) {
        LOGE("Failed to open file: %s, error: %s", path, strerror(errno));
    }

    (*env)->ReleaseStringUTFChars(env, path_jstr, path);
    return fd;
}

// JNI: Write data to file descriptor
JNIEXPORT jint JNICALL
Java_yangfentuozi_batteryrecorder_server_Native_nativeWriteFile(
        JNIEnv *env, jclass clazz __attribute__((unused)),
        jint fd, jbyteArray data_jarr, jint offset, jint length) {
    if (fd < 0 || !data_jarr) {
        return -1;
    }

    jbyte *data = (*env)->GetByteArrayElements(env, data_jarr, NULL);
    if (!data) {
        return -1;
    }

    ssize_t written = write(fd, data + offset, length);
    (*env)->ReleaseByteArrayElements(env, data_jarr, data, JNI_ABORT);

    return (jint)written;
}

// JNI: Flush file descriptor (fsync)
JNIEXPORT jint JNICALL
Java_yangfentuozi_batteryrecorder_server_Native_nativeSyncFile(
        JNIEnv *env __attribute__((unused)), jclass clazz __attribute__((unused)),
        jint fd) {
    if (fd < 0) {
        return -1;
    }
    return fsync(fd);
}

// JNI: Close file descriptor
JNIEXPORT jint JNICALL
Java_yangfentuozi_batteryrecorder_server_Native_nativeCloseFile(
        JNIEnv *env __attribute__((unused)), jclass clazz __attribute__((unused)),
        jint fd) {
    if (fd < 0) {
        return -1;
    }
    return close(fd);
}

