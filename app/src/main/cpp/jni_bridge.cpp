#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"

// ---------------------------------------------------------------------------
// sanitizeForJni — strip null bytes and replace invalid / unsupported
// Modified UTF-8 byte sequences so that NewStringUTF never crashes.
//
// JNI Modified UTF-8 differs from standard UTF-8 in two key ways:
//   1. Null bytes (U+0000) are encoded as 0xC0 0x80, not 0x00.
//      Raw 0x00 bytes inside a string are illegal and crash the JVM.
//   2. Codepoints above U+FFFF (4-byte UTF-8 sequences, lead byte 0xF0–0xF7)
//      are not supported.  They must be encoded as CESU-8 surrogate pairs,
//      but for simplicity we replace them with '?' here.
//
// Validation rules applied byte-by-byte:
//   0x01–0x7F  : valid single-byte
//   0xC2–0xDF  + one  continuation (0x80–0xBF) : valid 2-byte
//   0xE0–0xEF  + two  continuations             : valid 3-byte
//   0xF0–0xF7  (4-byte lead)                    : replace whole sequence with '?'
//   Anything else                                : replace byte with '?'
// ---------------------------------------------------------------------------
static std::string sanitizeForJni(const std::string& input) {
    std::string out;
    out.reserve(input.size());
    size_t i = 0;
    while (i < input.size()) {
        unsigned char c = static_cast<unsigned char>(input[i]);

        // Strip embedded nulls — illegal in Modified UTF-8
        if (c == 0x00) {
            ++i;
            continue;
        }

        // Single-byte ASCII (0x01–0x7F)
        if (c <= 0x7F) {
            out += static_cast<char>(c);
            ++i;
            continue;
        }

        // 4-byte sequence (0xF0–0xF7) — not supported in Modified UTF-8
        if (c >= 0xF0 && c <= 0xF7) {
            // Consume the lead byte plus up to 3 continuation bytes
            out += '?';
            ++i;
            while (i < input.size() &&
                   (static_cast<unsigned char>(input[i]) & 0xC0) == 0x80) {
                ++i;
            }
            continue;
        }

        // 3-byte sequence (0xE0–0xEF)
        if (c >= 0xE0 && c <= 0xEF) {
            if (i + 2 < input.size() &&
                (static_cast<unsigned char>(input[i+1]) & 0xC0) == 0x80 &&
                (static_cast<unsigned char>(input[i+2]) & 0xC0) == 0x80) {
                out += static_cast<char>(input[i]);
                out += static_cast<char>(input[i+1]);
                out += static_cast<char>(input[i+2]);
                i += 3;
            } else {
                out += '?';
                ++i;
            }
            continue;
        }

        // 2-byte sequence (0xC2–0xDF); 0xC0/0xC1 are overlong and illegal
        if (c >= 0xC2 && c <= 0xDF) {
            if (i + 1 < input.size() &&
                (static_cast<unsigned char>(input[i+1]) & 0xC0) == 0x80) {
                out += static_cast<char>(input[i]);
                out += static_cast<char>(input[i+1]);
                i += 2;
            } else {
                out += '?';
                ++i;
            }
            continue;
        }

        // Any other byte (continuation byte without lead, 0x80–0xBF, 0xC0, 0xC1, 0xF8+)
        out += '?';
        ++i;
    }
    return out;
}

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// NOTE: The static g_ctx global has been removed.
// The whisper_context pointer is now returned as a jlong (64-bit handle) by
// loadModel() and passed back into every subsequent JNI call by the Kotlin
// layer (WhisperEngine.kt).  This eliminates the static-global race condition
// that could cause segfaults during model switches or concurrent access.

struct ProgressCallbackContext {
    JavaVM* jvm;
    jobject listener;
    jmethodID onProgress;

    ProgressCallbackContext(JNIEnv* env, jobject lst) : jvm(nullptr), listener(nullptr), onProgress(nullptr) {
        env->GetJavaVM(&jvm);
        if (lst) {
            listener = env->NewGlobalRef(lst);
            jclass clazz = env->GetObjectClass(lst);
            onProgress = env->GetMethodID(clazz, "onProgress", "(I)V");
            env->DeleteLocalRef(clazz);
        }
    }

    ~ProgressCallbackContext() {
        if (listener && jvm) {
            JNIEnv* env = nullptr;
            jint rc = jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
            if (rc == JNI_OK) {
                env->DeleteGlobalRef(listener);
            } else if (rc == JNI_EDETACHED) {
                // Inference ran on a thread that got detached (e.g. after cancellation).
                // Temporarily attach so we can release the global ref — otherwise it leaks.
                if (jvm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
                    env->DeleteGlobalRef(listener);
                    jvm->DetachCurrentThread();
                }
            }
            listener = nullptr;
        }
    }
};

static void android_whisper_progress_callback(struct whisper_context * /*ctx*/, struct whisper_state * /*state*/, int progress, void * user_data) {
    if (!user_data) return;
    ProgressCallbackContext* cb = static_cast<ProgressCallbackContext*>(user_data);
    if (!cb->listener || !cb->onProgress) return;

    JNIEnv * env = nullptr;
    jint rc = cb->jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (rc == JNI_OK) {
        env->CallVoidMethod(cb->listener, cb->onProgress, (jint)progress);
        env->ExceptionClear();
    } else if (rc == JNI_EDETACHED) {
        cb->jvm->AttachCurrentThread(&env, nullptr);
        env->CallVoidMethod(cb->listener, cb->onProgress, (jint)progress);
        env->ExceptionClear();
        cb->jvm->DetachCurrentThread();
    }
}

extern "C" {

// ---------------------------------------------------------------------------
// loadModel — initialises whisper and returns an opaque jlong handle.
// Returns 0L on failure so Kotlin can treat 0 as "not loaded".
// ---------------------------------------------------------------------------
JNIEXPORT jlong JNICALL
Java_com_dipdev_aiautocaptioner_core_whisper_WhisperEngine_loadModel(
        JNIEnv * env, jobject, jstring model_path_str) {

    const char * model_path = env->GetStringUTFChars(model_path_str, nullptr);
    LOGI("Loading model from: %s", model_path);

    whisper_context_params params = whisper_context_default_params();
    params.use_gpu    = false;
    params.flash_attn = true;  // ARM NEON-optimised attention — free speedup on arm64-v8a

    whisper_context * ctx = whisper_init_from_file_with_params(model_path, params);
    env->ReleaseStringUTFChars(model_path_str, model_path);

    if (ctx == nullptr) {
        LOGE("Failed to load model!");
        return 0L;
    }

    LOGI("Model loaded successfully");
    return reinterpret_cast<jlong>(ctx);
}

// ---------------------------------------------------------------------------
// transcribe — plain text transcription (no timestamps).
// ---------------------------------------------------------------------------
JNIEXPORT jstring JNICALL
Java_com_dipdev_aiautocaptioner_core_whisper_WhisperEngine_transcribe(
        JNIEnv * env, jobject, jlong handle, jfloatArray audio_data, jstring lang_str, jint n_threads, jobject listener) {

    whisper_context * ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx == nullptr) {
        LOGE("transcribe called with null handle!");
        return env->NewStringUTF("[Error: model not loaded]");
    }

    const char * lang = env->GetStringUTFChars(lang_str, nullptr);
    jsize n_samples   = env->GetArrayLength(audio_data);
    jfloat * samples  = env->GetFloatArrayElements(audio_data, nullptr);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads        = (int) n_threads;
    params.language         = lang;
    params.translate        = false;
    params.print_special    = false;
    params.print_progress   = false;
    params.print_realtime   = false;
    params.print_timestamps = false;

    ProgressCallbackContext* cb_ctx = nullptr;
    if (listener != nullptr) {
        cb_ctx = new ProgressCallbackContext(env, listener);
        params.progress_callback = android_whisper_progress_callback;
        params.progress_callback_user_data = cb_ctx;
    }

    int result = whisper_full(ctx, params, samples, (int) n_samples);

    if (cb_ctx != nullptr) {
        delete cb_ctx;
    }

    env->ReleaseFloatArrayElements(audio_data, samples, JNI_ABORT);
    env->ReleaseStringUTFChars(lang_str, lang);

    if (result != 0) {
        LOGE("Transcription failed with code: %d", result);
        return env->NewStringUTF("[Transcription failed]");
    }

    std::string text;
    int n_segments = whisper_full_n_segments(ctx);
    for (int i = 0; i < n_segments; i++) {
        const char * segment = whisper_full_get_segment_text(ctx, i);
        if (segment != nullptr) {
            text += segment;
        }
    }

    LOGI("Transcription done: %zu chars", text.size());
    std::string safeText = sanitizeForJni(text);
    return env->NewStringUTF(safeText.c_str());
}

// ---------------------------------------------------------------------------
// transcribeWithTimestamps — returns word|startMs|endMs|confidence entries.
//
// FIX: Each NewStringUTF() call consumes a JNI local reference slot.
// The JVM enforces a hard cap of 512 local refs per native call frame.
// On long videos this overflows and crashes the app.
// Fix: call DeleteLocalRef immediately after SetObjectArrayElement so the
// slot is reclaimed within the same loop iteration.
// ---------------------------------------------------------------------------
JNIEXPORT jobjectArray JNICALL
Java_com_dipdev_aiautocaptioner_core_whisper_WhisperEngine_transcribeWithTimestamps(
        JNIEnv * env, jobject, jlong handle, jfloatArray audio_data, jstring lang_str, jint n_threads, jobject listener) {

    whisper_context * ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx == nullptr) {
        LOGE("transcribeWithTimestamps called with null handle!");
        return nullptr;
    }

    const char * lang = env->GetStringUTFChars(lang_str, nullptr);
    jsize n_samples   = env->GetArrayLength(audio_data);
    jfloat * samples  = env->GetFloatArrayElements(audio_data, nullptr);

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

    ProgressCallbackContext* cb_ctx = nullptr;
    if (listener != nullptr) {
        cb_ctx = new ProgressCallbackContext(env, listener);
        params.progress_callback = android_whisper_progress_callback;
        params.progress_callback_user_data = cb_ctx;
    }

    int result = whisper_full(ctx, params, samples, (int) n_samples);

    if (cb_ctx != nullptr) {
        delete cb_ctx;
    }

    env->ReleaseFloatArrayElements(audio_data, samples, JNI_ABORT);
    env->ReleaseStringUTFChars(lang_str, lang);

    if (result != 0) {
        LOGE("Transcription failed: %d", result);
        return nullptr;
    }

    int n_segments = whisper_full_n_segments(ctx);

    // Collect valid entries first — filter out empty/special tokens
    std::vector<std::string> entries;
    for (int i = 0; i < n_segments; i++) {
        const char * text = whisper_full_get_segment_text(ctx, i);
        if (text == nullptr) continue;

        // Trim leading/trailing whitespace
        std::string word(text);
        size_t start = word.find_first_not_of(" \t\n\r");
        if (start == std::string::npos) continue;
        word = word.substr(start);
        size_t end = word.find_last_not_of(" \t\n\r");
        if (end != std::string::npos) word = word.substr(0, end + 1);

        // Skip special tokens like [_BEG_], [_TT_50], etc.
        if (!word.empty() && word.front() == '[' && word.back() == ']') continue;

        if (word.empty()) continue;

        long start_ms = whisper_full_get_segment_t0(ctx, i) * 10;
        long end_ms   = whisper_full_get_segment_t1(ctx, i) * 10;

        float confidence = 1.0f;
        if (whisper_full_n_tokens(ctx, i) > 0) {
            confidence = whisper_full_get_token_data(ctx, i, 0).p;
        }

        std::string entry = word + "|" + std::to_string(start_ms) + "|" + std::to_string(end_ms) + "|" + std::to_string(confidence);
        entries.push_back(entry);
    }

    LOGI("Total segments: %d, valid words: %d", n_segments, (int)entries.size());

    // Build the result array.
    // DeleteLocalRef on stringClass and on each jstring to stay well under
    // the 512-slot JNI local reference table limit.
    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray resultArray = env->NewObjectArray(
            (jsize)entries.size(), stringClass, nullptr);
    if (resultArray == nullptr) {
        LOGE("Failed to allocate string array");
        env->DeleteLocalRef(stringClass);
        return nullptr;
    }
    env->DeleteLocalRef(stringClass);  // no longer needed after NewObjectArray

    for (int i = 0; i < (int)entries.size(); i++) {
        std::string safeEntry = sanitizeForJni(entries[i]);
        jstring localStr = env->NewStringUTF(safeEntry.c_str());
        env->SetObjectArrayElement(resultArray, i, localStr);
        env->DeleteLocalRef(localStr);  // reclaim slot immediately — prevents overflow
    }

    return resultArray;
}

// ---------------------------------------------------------------------------
// isModelLoaded — handle-aware null check (no global state needed).
// ---------------------------------------------------------------------------
JNIEXPORT jboolean JNICALL
Java_com_dipdev_aiautocaptioner_core_whisper_WhisperEngine_isModelLoaded(
        JNIEnv *, jobject, jlong handle) {
    return handle != 0L ? JNI_TRUE : JNI_FALSE;
}

// ---------------------------------------------------------------------------
// freeModel — takes the handle, frees the context. Kotlin sets its own
// stored handle to 0L after this call so the pointer is never reused.
// ---------------------------------------------------------------------------
JNIEXPORT void JNICALL
Java_com_dipdev_aiautocaptioner_core_whisper_WhisperEngine_freeModel(
        JNIEnv *, jobject, jlong handle) {
    whisper_context * ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx != nullptr) {
        whisper_free(ctx);
        LOGI("Model freed");
    }
}

} // extern "C"
