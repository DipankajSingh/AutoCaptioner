#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"

// Deleted sanitizeForJni since we now pass raw byte arrays instead of Strings.

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
            if (clazz) {
                onProgress = env->GetMethodID(clazz, "onProgress", "(I)V");
                if (env->ExceptionCheck()) { env->ExceptionClear(); }
                env->DeleteLocalRef(clazz);
            }
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

struct SegmentCallbackContext {
    JavaVM* jvm;
    jobject listener;
    jmethodID onSegment;

    SegmentCallbackContext(JNIEnv* env, jobject lst) : jvm(nullptr), listener(nullptr), onSegment(nullptr) {
        env->GetJavaVM(&jvm);
        if (lst) {
            listener = env->NewGlobalRef(lst);
            jclass clazz = env->GetObjectClass(lst);
            if (clazz) {
                onSegment = env->GetMethodID(clazz, "onSegment", "([BJJ)V");
                if (env->ExceptionCheck()) { env->ExceptionClear(); }
                env->DeleteLocalRef(clazz);
            }
        }
    }

    ~SegmentCallbackContext() {
        if (listener && jvm) {
            JNIEnv* env = nullptr;
            jint rc = jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
            if (rc == JNI_OK) {
                env->DeleteGlobalRef(listener);
            } else if (rc == JNI_EDETACHED) {
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
        if (cb->jvm->AttachCurrentThread(&env, nullptr) == JNI_OK) {
            env->CallVoidMethod(cb->listener, cb->onProgress, (jint)progress);
            env->ExceptionClear();
            cb->jvm->DetachCurrentThread();
        }
    }
}

static void android_whisper_new_segment_callback(struct whisper_context * /*ctx*/, struct whisper_state * state, int n_new, void * user_data) {
    if (!user_data) return;
    SegmentCallbackContext* cb = static_cast<SegmentCallbackContext*>(user_data);
    if (!cb->listener || !cb->onSegment) return;

    JNIEnv * env = nullptr;
    bool did_attach = false;
    jint rc = cb->jvm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (rc == JNI_EDETACHED) {
        if (cb->jvm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        did_attach = true;
    } else if (rc != JNI_OK) {
        return;
    }

    int total = whisper_full_n_segments_from_state(state);
    for (int i = total - n_new; i < total; i++) {
        const char * text = whisper_full_get_segment_text_from_state(state, i);
        if (text == nullptr) continue;

        int64_t t0 = whisper_full_get_segment_t0_from_state(state, i);
        int64_t t1 = whisper_full_get_segment_t1_from_state(state, i);

        int len = strlen(text);
        jbyteArray jtext = env->NewByteArray(len);
        if (jtext != nullptr) {
            env->SetByteArrayRegion(jtext, 0, len, reinterpret_cast<const jbyte*>(text));
            env->CallVoidMethod(cb->listener, cb->onSegment, jtext, (jlong)(t0 * 10), (jlong)(t1 * 10));
            env->ExceptionClear();
            env->DeleteLocalRef(jtext);
        }
    }

    if (did_attach) {
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
JNIEXPORT jbyteArray JNICALL
Java_com_dipdev_aiautocaptioner_core_whisper_WhisperEngine_transcribe(
        JNIEnv * env, jobject, jlong handle, jfloatArray audio_data, jstring lang_str, jboolean translate_to_english, jint n_threads, jobject listener) {

    whisper_context * ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx == nullptr) {
        LOGE("transcribe called with null handle!");
        return nullptr;
    }

    const char * lang = nullptr;
    if (lang_str != nullptr) {
        lang = env->GetStringUTFChars(lang_str, nullptr);
    }
    
    jsize n_samples   = env->GetArrayLength(audio_data);
    jfloat * samples  = env->GetFloatArrayElements(audio_data, nullptr);
    if (samples == nullptr) {
        if (lang != nullptr) env->ReleaseStringUTFChars(lang_str, lang);
        return nullptr;
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads        = (int) n_threads;
    params.language         = lang;
    params.translate        = translate_to_english == JNI_TRUE;
    params.print_special    = false;
    params.print_progress   = false;
    params.print_realtime   = false;
    params.print_timestamps = false;

    ProgressCallbackContext* cb_ctx = nullptr;
    try {
        if (listener != nullptr) {
            cb_ctx = new ProgressCallbackContext(env, listener);
            params.progress_callback = android_whisper_progress_callback;
            params.progress_callback_user_data = cb_ctx;
        }
    } catch (...) {
        LOGE("Failed to allocate ProgressCallbackContext");
    }

    int result = whisper_full(ctx, params, samples, (int) n_samples);

    if (cb_ctx != nullptr) {
        delete cb_ctx;
    }

    env->ReleaseFloatArrayElements(audio_data, samples, JNI_ABORT);
    if (lang != nullptr) {
        env->ReleaseStringUTFChars(lang_str, lang);
    }

    if (result != 0) {
        LOGE("Transcription failed with code: %d", result);
        return nullptr;
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
    
    int len = text.size();
    jbyteArray jBytes = env->NewByteArray(len);
    if (jBytes != nullptr) {
        env->SetByteArrayRegion(jBytes, 0, len, reinterpret_cast<const jbyte*>(text.data()));
    }
    return jBytes;
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
JNIEXPORT jbyteArray JNICALL
Java_com_dipdev_aiautocaptioner_core_whisper_WhisperEngine_transcribeWithTimestamps(
        JNIEnv * env, jobject, jlong handle, jfloatArray audio_data, jstring lang_str, jboolean translate_to_english, jint n_threads, jobject listener, jobject segmentListener) {

    whisper_context * ctx = reinterpret_cast<whisper_context *>(handle);
    if (ctx == nullptr) {
        LOGE("transcribeWithTimestamps called with null handle!");
        return nullptr;
    }

    const char * lang = nullptr;
    if (lang_str != nullptr) {
        lang = env->GetStringUTFChars(lang_str, nullptr);
    }
    jsize n_samples   = env->GetArrayLength(audio_data);
    jfloat * samples  = env->GetFloatArrayElements(audio_data, nullptr);
    if (samples == nullptr) {
        if (lang != nullptr) env->ReleaseStringUTFChars(lang_str, lang);
        return nullptr;
    }

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads        = (int) n_threads;
    params.language         = lang;
    params.translate        = translate_to_english == JNI_TRUE;
    params.print_special    = false;
    params.print_progress   = false;
    params.print_realtime   = false;
    params.print_timestamps = true;
    params.token_timestamps = true;
    params.max_len          = 1;

    ProgressCallbackContext* cb_ctx = nullptr;
    SegmentCallbackContext* seg_ctx = nullptr;
    try {
        if (listener != nullptr) {
            cb_ctx = new ProgressCallbackContext(env, listener);
            params.progress_callback = android_whisper_progress_callback;
            params.progress_callback_user_data = cb_ctx;
        }

        if (segmentListener != nullptr) {
            seg_ctx = new SegmentCallbackContext(env, segmentListener);
            params.new_segment_callback = android_whisper_new_segment_callback;
            params.new_segment_callback_user_data = seg_ctx;
        }
    } catch (...) {
        LOGE("Failed to allocate callback contexts");
    }

    int result = whisper_full(ctx, params, samples, (int) n_samples);

    if (cb_ctx != nullptr) {
        delete cb_ctx;
    }
    if (seg_ctx != nullptr) {
        delete seg_ctx;
    }

    env->ReleaseFloatArrayElements(audio_data, samples, JNI_ABORT);
    if (lang != nullptr) {
        env->ReleaseStringUTFChars(lang_str, lang);
    }

    if (result != 0) {
        LOGE("Transcription failed: %d", result);
        return nullptr;
    }

    int n_segments = whisper_full_n_segments(ctx);

    std::string text_result;
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

        text_result += word + "\t" + std::to_string(start_ms) + "\t" + std::to_string(end_ms) + "\t" + std::to_string(confidence) + "\n";
    }

    LOGI("Total segments: %d", n_segments);

    int len = text_result.size();
    jbyteArray jBytes = env->NewByteArray(len);
    if (jBytes != nullptr) {
        env->SetByteArrayRegion(jBytes, 0, len, reinterpret_cast<const jbyte*>(text_result.data()));
    }
    return jBytes;
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
