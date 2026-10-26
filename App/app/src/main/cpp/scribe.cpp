#include <jni.h>
#include <atomic>
#include <string>
#include <vector>
#include "whisper.h"

namespace {
std::atomic<bool> cancelled{false};
bool abort_decode(void *) { return cancelled.load(); }
std::string escape(const char * text) {
    std::string out;
    for (const unsigned char * p = reinterpret_cast<const unsigned char *>(text); *p; ++p) {
        switch (*p) {
            case '"': out += "\\\""; break;
            case '\\': out += "\\\\"; break;
            case '\n': out += "\\n"; break;
            case '\r': out += "\\r"; break;
            case '\t': out += "\\t"; break;
            default: if (*p >= 0x20) out += static_cast<char>(*p);
        }
    }
    return out;
}
void fail(JNIEnv * env, const char * message) { env->ThrowNew(env->FindClass("java/io/IOException"), message); }
}

extern "C" JNIEXPORT jlong JNICALL Java_ro_scribe_lectures_Whisper_open(JNIEnv * env, jclass, jstring path) {
    const char * file = env->GetStringUTFChars(path, nullptr);
    auto params = whisper_context_default_params(); params.use_gpu = false;
    auto * context = whisper_init_from_file_with_params(file, params);
    env->ReleaseStringUTFChars(path, file);
    if (!context) fail(env, "Could not load the speech model");
    return reinterpret_cast<jlong>(context);
}
extern "C" JNIEXPORT void JNICALL Java_ro_scribe_lectures_Whisper_close(JNIEnv *, jclass, jlong handle) { whisper_free(reinterpret_cast<whisper_context *>(handle)); }
extern "C" JNIEXPORT void JNICALL Java_ro_scribe_lectures_Whisper_cancel(JNIEnv *, jclass) { cancelled.store(true); }
extern "C" JNIEXPORT void JNICALL Java_ro_scribe_lectures_Whisper_resetCancel(JNIEnv *, jclass) { cancelled.store(false); }
extern "C" JNIEXPORT jbyteArray JNICALL Java_ro_scribe_lectures_Whisper_decode(JNIEnv * env, jclass, jlong handle, jfloatArray input, jstring language, jstring prompt, jint threads) {
    auto * context = reinterpret_cast<whisper_context *>(handle);
    if (!context) { fail(env, "Speech model is not loaded"); return nullptr; }
    const int count = env->GetArrayLength(input);
    if (count < 1 || count > 16000 * 31) { fail(env, "Invalid audio window"); return nullptr; }
    std::vector<float> pcm(count); env->GetFloatArrayRegion(input, 0, count, pcm.data());
    const char * lang = env->GetStringUTFChars(language, nullptr);
    const char * previous = env->GetStringUTFChars(prompt, nullptr);
    auto params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads;
    params.translate = false; params.no_context = true;
    params.print_realtime = false; params.print_progress = false;
    params.print_timestamps = false; params.print_special = false;
    params.language = lang; params.initial_prompt = previous;
    params.temperature = 0.0f; params.temperature_inc = 0.0f;
    params.suppress_blank = true; params.suppress_nst = true;
    params.abort_callback = abort_decode;
    params.abort_callback_user_data = nullptr;
    int result = whisper_full(context, params, pcm.data(), count);
    env->ReleaseStringUTFChars(language, lang); env->ReleaseStringUTFChars(prompt, previous);
    if (result != 0 || cancelled.load()) { fail(env, cancelled.load() ? "cancelled" : "Speech recognition failed"); return nullptr; }
    std::string json = "[";
    for (int i = 0; i < whisper_full_n_segments(context); ++i) {
        if (i) json += ',';
        json += "{\"start\":" + std::to_string(whisper_full_get_segment_t0(context, i) * 10);
        json += ",\"end\":" + std::to_string(whisper_full_get_segment_t1(context, i) * 10);
        json += ",\"text\":\"" + escape(whisper_full_get_segment_text(context, i)) + "\"}";
    }
    json += ']';
    jbyteArray output = env->NewByteArray(static_cast<jsize>(json.size()));
    if (output) env->SetByteArrayRegion(output, 0, static_cast<jsize>(json.size()), reinterpret_cast<const jbyte *>(json.data()));
    return output;
}
