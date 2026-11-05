/* Android PTY bridge for a SIMH-DVK executable stored in nativeLibraryDir. */
#define _GNU_SOURCE
#include <jni.h>
#include <pty.h>
#include <errno.h>
#include <signal.h>
#include <poll.h>
#include <sys/wait.h>
#include <unistd.h>
#include <stdlib.h>
#include <stdint.h>

typedef struct { int fd; pid_t pid; } DvkPty;
static DvkPty* ptr(jlong h) { return (DvkPty*)(uintptr_t)h; }

JNIEXPORT jlong JNICALL
Java_org_demos_dvk_MainActivity_nativeSpawn(JNIEnv* env, jobject thiz, jstring executable, jstring dir) {
    (void)thiz;
    if (!executable || !dir) return 0;
    const char* exe = (*env)->GetStringUTFChars(env, executable, 0);
    const char* cwd = (*env)->GetStringUTFChars(env, dir, 0);
    if (!exe || !cwd) {
        if (exe) (*env)->ReleaseStringUTFChars(env, executable, exe);
        if (cwd) (*env)->ReleaseStringUTFChars(env, dir, cwd);
        return 0;
    }
    int master = -1;
    struct winsize size = { .ws_row = 32, .ws_col = 80, .ws_xpixel = 0, .ws_ypixel = 0 };
    pid_t pid = forkpty(&master, NULL, NULL, &size);
    if (pid == 0) {
        if (chdir(cwd) != 0) _exit(126);
        execl(exe, exe, "boot.ini", (char*)NULL);
        _exit(127);
    }
    (*env)->ReleaseStringUTFChars(env, executable, exe);
    (*env)->ReleaseStringUTFChars(env, dir, cwd);
    if (pid < 0 || master < 0) return 0;
    DvkPty* p = (DvkPty*)calloc(1, sizeof(DvkPty));
    if (!p) { close(master); kill(pid, SIGTERM); return 0; }
    p->fd = master; p->pid = pid;
    return (jlong)(uintptr_t)p;
}

JNIEXPORT jint JNICALL
Java_org_demos_dvk_MainActivity_nativeRead(JNIEnv* env, jobject thiz, jlong handle, jbyteArray bytes) {
    (void)thiz;
    DvkPty* p = ptr(handle);
    if (!p || !bytes) return -1;
    jsize n = (*env)->GetArrayLength(env, bytes);
    if (n <= 0) return 0;
    if (n > 4096) n = 4096;
    struct pollfd ready = { .fd = p->fd, .events = POLLIN | POLLHUP };
    int rc = poll(&ready, 1, 130);
    if (rc == 0 || (rc < 0 && errno == EINTR)) return 0;
    if (rc < 0) return -1;
    char buffer[4096];
    ssize_t count = read(p->fd, buffer, (size_t)n);
    if (count <= 0) return -1;
    (*env)->SetByteArrayRegion(env, bytes, 0, (jsize)count, (jbyte*)buffer);
    return (jint)count;
}

JNIEXPORT jint JNICALL
Java_org_demos_dvk_MainActivity_nativeWrite(JNIEnv* env, jobject thiz, jlong handle, jbyteArray bytes) {
    (void)thiz;
    DvkPty* p = ptr(handle);
    if (!p || !bytes) return -1;
    jsize n = (*env)->GetArrayLength(env, bytes);
    if (n <= 0 || n > 4096) return 0;
    jbyte buffer[4096];
    (*env)->GetByteArrayRegion(env, bytes, 0, n, buffer);
    ssize_t count = write(p->fd, buffer, (size_t)n);
    return count < 0 ? -1 : (jint)count;
}

JNIEXPORT void JNICALL
Java_org_demos_dvk_MainActivity_nativeRequestStop(JNIEnv* env, jobject thiz, jlong handle) {
    (void)env; (void)thiz;
    DvkPty* p = ptr(handle);
    if (p) kill(p->pid, SIGTERM);
}

JNIEXPORT void JNICALL
Java_org_demos_dvk_MainActivity_nativeClose(JNIEnv* env, jobject thiz, jlong handle) {
    (void)env; (void)thiz;
    DvkPty* p = ptr(handle);
    if (!p) return;
    close(p->fd);
    int status;
    if (waitpid(p->pid, &status, WNOHANG) == 0) kill(p->pid, SIGTERM);
    free(p);
}
