#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static whisper_context * g_ctx = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_dipdev_aiautocaptioner_core_whisper_WhisperEngine_loadModel(
        JNIEnv * env, jobject, jstring model_path_str) {

    if (g_ctx != nullptr) {
        whisper_free(g_ctx);
        g_ctx = nullptr;
    }

    const char * model_path = env->GetStringUTFChars(model_path_str, nullptr);
    LOGI("Loading model from: %s", model_path);

    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;

    g_ctx = whisper_init_from_file_with_params(model_path, params);
    env->ReleaseStringUTFChars(model_path_str, model_path);

    if (g_ctx == nullptr) {
        LOGE("Failed to load model!");
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully");
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_dipdev_aiautocaptioner_core_whisper_WhisperEngine_transcribe(
        JNIEnv * env, jobject, jfloatArray audio_data, jstring lang_str, jint n_threads) {

    if (g_ctx == nullptr) {
        LOGE("Model not loaded!");
        return env->NewStringUTF("[Error: model not loaded]");
    }

    const char * lang = env->GetStringUTFChars(lang_str, nullptr);
    jsize n_samples = env->GetArrayLength(audio_data);
    jfloat * samples = env->GetFloatArrayElements(audio_data, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads        = (int) n_threads;
    params.language         = lang;
    params.translate        = false;
    params.print_special    = false;
    params.print_progress   = false;
    params.print_realtime   = false;
    params.print_timestamps = false;

    int result = whisper_full(g_ctx, params, samples, (int) n_samples);

    env->ReleaseFloatArrayElements(audio_data, samples, JNI_ABORT);
    env->ReleaseStringUTFChars(lang_str, lang);

    if (result != 0) {
        LOGE("Transcription failed with code: %d", result);
        return env->NewStringUTF("[Transcription failed]");
    }

    std::string text;
    int n_segments = whisper_full_n_segments(g_ctx);
    for (int i = 0; i < n_segments; i++) {
        const char * segment = whisper_full_get_segment_text(g_ctx, i);
        if (segment != nullptr) {
            text += segment;
        }
    }

    LOGI("Transcription done: %s", text.c_str());
    return env->NewStringUTF(text.c_str());
}

JNIEXPORT jobjectArray JNICALL
Java_com_dipdev_aiautocaptioner_core_whisper_WhisperEngine_transcribeWithTimestamps(
        JNIEnv * env, jobject, jfloatArray audio_data, jstring lang_str, jint n_threads) {

    if (g_ctx == nullptr) {
        LOGE("Model not loaded!");
        return nullptr;
    }

    const char * lang = env->GetStringUTFChars(lang_str, nullptr);
    jsize n_samples = env->GetArrayLength(audio_data);
    jfloat * samples = env->GetFloatArrayElements(audio_data, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads        = (int) n_threads;
    params.language         = lang;
    params.translate        = false;
    params.print_special    = false;
    params.print_progress   = false;
    params.print_realtime   = false;
    params.print_timestamps = true;
    params.token_timestamps = true;
    params.max_len          = 1;

    int result = whisper_full(g_ctx, params, samples, (int) n_samples);

    env->ReleaseFloatArrayElements(audio_data, samples, JNI_ABORT);
    env->ReleaseStringUTFChars(lang_str, lang);

    if (result != 0) {
        LOGE("Transcription failed: %d", result);
        return nullptr;
    }

    int n_segments = whisper_full_n_segments(g_ctx);

    // Collect valid entries first — filter out empty/special tokens
    std::vector<std::string> entries;
    for (int i = 0; i < n_segments; i++) {
        const char * text = whisper_full_get_segment_text(g_ctx, i);
        if (text == nullptr) continue;

        // Trim leading/trailing whitespace
        std::string word(text);
        size_t start = word.find_first_not_of(" \t\n\r");
        if (start == std::string::npos) continue; // all whitespace
        word = word.substr(start);
        size_t end = word.find_last_not_of(" \t\n\r");
        if (end != std::string::npos) word = word.substr(0, end + 1);

        // Skip special tokens like [_BEG_], [_TT_50], etc.
        if (!word.empty() && word.front() == '[' && word.back() == ']') continue;

        // Skip if still empty after trimming
        if (word.empty()) continue;

        long start_ms = whisper_full_get_segment_t0(g_ctx, i) * 10;
        long end_ms   = whisper_full_get_segment_t1(g_ctx, i) * 10;

        float confidence = 1.0f;
        if (whisper_full_n_tokens(g_ctx, i) > 0) {
            confidence = whisper_full_get_token_data(g_ctx, i, 0).p;
        }

        char entry[512];
        snprintf(entry, sizeof(entry), "%s|%ld|%ld|%.3f",
                 word.c_str(), start_ms, end_ms, confidence);
        entries.push_back(std::string(entry));
    }

    LOGI("Total segments: %d, valid words: %d", n_segments, (int)entries.size());

    // Build result array from valid entries only
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray resultArray = env->NewObjectArray(
            (jsize)entries.size(), stringClass, nullptr);

    for (int i = 0; i < (int)entries.size(); i++) {
        env->SetObjectArrayElement(resultArray, i,
                                   env->NewStringUTF(entries[i].c_str()));
    }

    return resultArray;
}

JNIEXPORT jboolean JNICALL
Java_com_dipdev_aiautocaptioner_core_whisper_WhisperEngine_isModelLoaded(JNIEnv *, jobject) {
    return g_ctx != nullptr ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_dipdev_aiautocaptioner_core_whisper_WhisperEngine_freeModel(JNIEnv *, jobject) {
if (g_ctx != nullptr) {
whisper_free(g_ctx);
g_ctx = nullptr;
LOGI("Model freed");
}
}

} // extern "C"



