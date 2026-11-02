#include <jni.h>
#include <atomic>
#include <string>
#include <vector>
#include <cmath>
#include <algorithm>
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
extern "C" JNIEXPORT jbyteArray JNICALL Java_ro_scribe_lectures_Whisper_decode(JNIEnv * env, jclass, jlong handle, jfloatArray input, jstring language, jint threads, jboolean beam) {
    auto * context = reinterpret_cast<whisper_context *>(handle);
    if (!context) { fail(env, "Speech model is not loaded"); return nullptr; }
    const int count = env->GetArrayLength(input);
    if (count < 1 || count > 16000 * 31) { fail(env, "Invalid audio window"); return nullptr; }
    std::vector<float> pcm(count); env->GetFloatArrayRegion(input, 0, count, pcm.data());
    if (cancelled.load()) { fail(env, "cancelled"); return nullptr; }
    const char * lang = env->GetStringUTFChars(language, nullptr);
    std::string resolved(lang);
    env->ReleaseStringUTFChars(language, lang);
    float language_probability = 1.0f;
    bool automatic = resolved == "auto";
    if (automatic) {
        if (whisper_pcm_to_mel(context, pcm.data(), count, threads) != 0) {
            fail(env, "Speech recognition failed"); return nullptr;
        }
        std::vector<float> probabilities(whisper_lang_max_id() + 1);
        int id = whisper_lang_auto_detect(context, 0, threads, probabilities.data());
        if (id < 0) { fail(env, "Speech recognition failed"); return nullptr; }
        resolved = whisper_lang_str(id);
        language_probability = probabilities[id];
        if (cancelled.load()) { fail(env, "cancelled"); return nullptr; }
    } else if (whisper_lang_id(resolved.c_str()) < 0) {
        fail(env, "Invalid speech language"); return nullptr;
    }
    auto params = whisper_full_default_params(beam ? WHISPER_SAMPLING_BEAM_SEARCH : WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads;
    params.translate = false; params.no_context = true;
    params.print_realtime = false; params.print_progress = false;
    params.print_timestamps = false; params.print_special = false;
    params.language = resolved.c_str(); params.initial_prompt = nullptr;
    params.beam_search.beam_size = 5;
    params.temperature = 0.0f; params.temperature_inc = 0.0f;
    params.suppress_blank = true; params.suppress_nst = true;
    params.abort_callback = abort_decode;
    params.abort_callback_user_data = nullptr;
    // Auto detection already computed the mel input; reuse it for recognition.
    int result = whisper_full(context, params, automatic ? nullptr : pcm.data(), automatic ? 0 : count);
    if (result != 0 || cancelled.load()) { fail(env, cancelled.load() ? "cancelled" : "Speech recognition failed"); return nullptr; }
    std::string json = "{\"language\":\"" + resolved + "\",\"language_probability\":" + std::to_string(language_probability) + ",\"segments\":[";
    double log_probability = 0.0;
    int token_count = 0;
    for (int i = 0; i < whisper_full_n_segments(context); ++i) {
        if (i) json += ',';
        json += "{\"start\":" + std::to_string(whisper_full_get_segment_t0(context, i) * 10);
        json += ",\"end\":" + std::to_string(whisper_full_get_segment_t1(context, i) * 10);
        json += ",\"text\":\"" + escape(whisper_full_get_segment_text(context, i)) + "\"}";
        for (int j = 0; j < whisper_full_n_tokens(context, i); ++j) {
            if (whisper_full_get_token_id(context, i, j) >= whisper_token_eot(context)) continue;
            log_probability += std::log(std::max(1e-8f, whisper_full_get_token_p(context, i, j)));
            ++token_count;
        }
    }
    json += "],\"average_log_probability\":" + std::to_string(token_count ? log_probability / token_count : -10.0) + "}";
    jbyteArray output = env->NewByteArray(static_cast<jsize>(json.size()));
    if (output) env->SetByteArrayRegion(output, 0, static_cast<jsize>(json.size()), reinterpret_cast<const jbyte *>(json.data()));
    return output;
}
