// JNI bridge to whisper.cpp (spec 0010 V1/V2).
//
// - initContext(modelPath)         -> whisper_init_from_file, returns opaque context
// - transcribe(ctx, audio, threads)-> whisper_full with language="" (auto-detect) +
//                                     translate=true (output is ALWAYS English)
// - getDetectedLanguage(ctx)       -> detected source language (ISO code)
// - freeContext(ctx)               -> whisper_free
//
// Kotlin native name: com.agentai.app.whisper.WhisperLib.Companion.*
// (matches the whisper.cpp Android sample naming convention).

#include <jni.h>
#include <stdlib.h>
#include <string.h>

#include "whisper.h"

static char *concat_segments(struct whisper_context *ctx, jsize max_text) {
    const int n = whisper_full_n_segments(ctx);
    if (n <= 0) {
        return strdup("");
    }
    size_t cap = max_text > 0 ? (size_t)max_text : 4096;
    char *out = (char *)malloc(cap);
    if (!out) return NULL;
    out[0] = '\0';
    size_t used = 0;
    for (int i = 0; i < n; i++) {
        const char *seg = whisper_full_get_segment_text(ctx, i);
        if (!seg) continue;
        size_t len = strlen(seg);
        if (used + len + 1 >= cap) {
            cap = (used + len + 1) * 2;
            char *grown = (char *)realloc(out, cap);
            if (!grown) {
                free(out);
                return NULL;
            }
            out = grown;
        }
        memcpy(out + used, seg, len);
        used += len;
        out[used++] = ' ';
    }
    out[used] = '\0';
    return out;
}

JNIEXPORT jlong JNICALL
Java_com_agentai_app_whisper_WhisperLib_00024Companion_initContext(
    JNIEnv *env, jobject thiz, jstring model_path) {
    (void)thiz;
    const char *path = (*env)->GetStringUTFChars(env, model_path, NULL);
    struct whisper_context *ctx = path ? whisper_init_from_file(path) : NULL;
    if (path) (*env)->ReleaseStringUTFChars(env, model_path, path);
    return (jlong)ctx;
}

JNIEXPORT jstring JNICALL
Java_com_agentai_app_whisper_WhisperLib_00024Companion_transcribe(
    JNIEnv *env, jobject thiz, jlong context_ptr, jfloatArray audio_data, jint num_threads) {
    (void)thiz;
    struct whisper_context *ctx = (struct whisper_context *)context_ptr;
    if (!ctx || !audio_data) {
        return (*env)->NewStringUTF(env, "");
    }
    jfloat *audio_arr = (*env)->GetFloatArrayElements(env, audio_data, NULL);
    const jsize audio_len = (*env)->GetArrayLength(env, audio_data);
    if (!audio_arr || audio_len <= 0) {
        return (*env)->NewStringUTF(env, "");
    }

    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = true;     // output English regardless of source language
    params.language = "";        // empty = auto-detect source language
    params.n_threads = num_threads > 0 ? num_threads : 4;
    params.offset_ms = 0;
    params.no_context = true;
    params.single_segment = false;

    jstring result = NULL;
    if (whisper_full(ctx, params, audio_arr, audio_len) == 0) {
        char *text = concat_segments(ctx, 0);
        result = (*env)->NewStringUTF(env, text ? text : "");
        if (text) free(text);
    } else {
        result = (*env)->NewStringUTF(env, "");
    }
    (*env)->ReleaseFloatArrayElements(env, audio_data, audio_arr, JNI_ABORT);
    return result;
}

JNIEXPORT jstring JNICALL
Java_com_agentai_app_whisper_WhisperLib_00024Companion_getDetectedLanguage(
    JNIEnv *env, jobject thiz, jlong context_ptr) {
    (void)thiz;
    struct whisper_context *ctx = (struct whisper_context *)context_ptr;
    if (!ctx) return (*env)->NewStringUTF(env, "");
    const int lang_id = whisper_full_lang_id(ctx);
    const char *lang = lang_id >= 0 ? whisper_lang_str(lang_id) : "";
    return (*env)->NewStringUTF(env, lang ? lang : "");
}

JNIEXPORT void JNICALL
Java_com_agentai_app_whisper_WhisperLib_00024Companion_freeContext(
    JNIEnv *env, jobject thiz, jlong context_ptr) {
    (void)env;
    (void)thiz;
    struct whisper_context *ctx = (struct whisper_context *)context_ptr;
    if (ctx) whisper_free(ctx);
}