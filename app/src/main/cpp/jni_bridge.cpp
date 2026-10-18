// Capa JNI: expone el motor de Praat (praat_core) a Kotlin.
//
// This program is free software: you can redistribute it and/or modify it under the terms
// of the GNU General Public License as published by the Free Software Foundation, either
// version 3 of the License, or (at your option) any later version.

#include <jni.h>

#include <android/log.h>

#include <mutex>
#include <string>

#include "praat_core.h"

#define LOG_TAG "ChromaticPraat"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

namespace {

// Praat no es seguro entre hilos: todas las llamadas nativas se serializan.
std::mutex g_praatMutex;

void throwJava(JNIEnv *env, const char *className, const std::string &message) {
	jclass cls = env->FindClass(className);
	if (cls) {
		env->ThrowNew(cls, message.c_str());
		env->DeleteLocalRef(cls);
	}
}

// UTF-8 -> jstring. NewStringUTF espera "UTF-8 modificado", asi que se convierte a UTF-16 a mano
// (asi tambien funcionan bien los caracteres fuera del plano basico).
jstring toJString(JNIEnv *env, const std::string &utf8) {
	std::u16string utf16;
	utf16.reserve(utf8.size());
	size_t i = 0;
	const size_t n = utf8.size();
	while (i < n) {
		unsigned char c = static_cast<unsigned char>(utf8[i]);
		char32_t cp = 0xFFFD;
		size_t len = 1;
		if (c < 0x80) {
			cp = c;
		} else if ((c >> 5) == 0x6 && i + 1 < n) {
			cp = ((c & 0x1F) << 6) | (utf8[i + 1] & 0x3F);
			len = 2;
		} else if ((c >> 4) == 0xE && i + 2 < n) {
			cp = ((c & 0x0F) << 12) | ((utf8[i + 1] & 0x3F) << 6) | (utf8[i + 2] & 0x3F);
			len = 3;
		} else if ((c >> 3) == 0x1E && i + 3 < n) {
			cp = ((c & 0x07) << 18) | ((utf8[i + 1] & 0x3F) << 12) | ((utf8[i + 2] & 0x3F) << 6) | (utf8[i + 3] & 0x3F);
			len = 4;
		}
		if (cp >= 0x10000) {
			cp -= 0x10000;
			utf16.push_back(static_cast<char16_t>(0xD800 + (cp >> 10)));
			utf16.push_back(static_cast<char16_t>(0xDC00 + (cp & 0x3FF)));
		} else {
			utf16.push_back(static_cast<char16_t>(cp));
		}
		i += len;
	}
	return env->NewString(reinterpret_cast<const jchar *>(utf16.data()), static_cast<jsize>(utf16.size()));
}

std::string fromJString(JNIEnv *env, jstring s) {
	if (!s)
		return std::string();
	const jsize len = env->GetStringLength(s);
	const jchar *chars = env->GetStringChars(s, nullptr);
	std::string out;
	out.reserve(static_cast<size_t>(len));
	for (jsize i = 0; i < len; ++i) {
		char32_t cp = chars[i];
		if (cp >= 0xD800 && cp < 0xDC00 && i + 1 < len && chars[i + 1] >= 0xDC00 && chars[i + 1] < 0xE000) {
			cp = 0x10000 + ((cp - 0xD800) << 10) + (chars[i + 1] - 0xDC00);
			++i;
		}
		if (cp < 0x80) {
			out.push_back(static_cast<char>(cp));
		} else if (cp < 0x800) {
			out.push_back(static_cast<char>(0xC0 | (cp >> 6)));
			out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
		} else if (cp < 0x10000) {
			out.push_back(static_cast<char>(0xE0 | (cp >> 12)));
			out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
			out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
		} else {
			out.push_back(static_cast<char>(0xF0 | (cp >> 18)));
			out.push_back(static_cast<char>(0x80 | ((cp >> 12) & 0x3F)));
			out.push_back(static_cast<char>(0x80 | ((cp >> 6) & 0x3F)));
			out.push_back(static_cast<char>(0x80 | (cp & 0x3F)));
		}
	}
	env->ReleaseStringChars(s, chars);
	return out;
}

} // namespace

extern "C" {

// String PraatBridge.nativeInit(String homeDir)
JNIEXPORT jstring JNICALL
Java_com_bichoelproxd_chromaticscalegenerator_PraatBridge_nativeInit(JNIEnv *env, jclass, jstring homeDir) {
	std::lock_guard<std::mutex> lock(g_praatMutex);
	try {
		return toJString(env, chromatic::initialize(fromJString(env, homeDir)));
	} catch (const std::exception &e) {
		LOGE("nativeInit: %s", e.what());
		throwJava(env, "java/lang/IllegalStateException", e.what());
	}
	return nullptr;
}

// String PraatBridge.nativeRunScript(String script, PraatBridge.InfoListener listener)
JNIEXPORT jstring JNICALL
Java_com_bichoelproxd_chromaticscalegenerator_PraatBridge_nativeRunScript(JNIEnv *env, jclass, jstring script, jobject listener) {
	std::lock_guard<std::mutex> lock(g_praatMutex);

	jmethodID onInfo = nullptr;
	if (listener) {
		// Se busca el metodo en la interfaz (no en la clase concreta del objeto, que puede ser una lambda).
		jclass listenerInterface = env->FindClass("com/bichoelproxd/chromaticscalegenerator/PraatBridge$InfoListener");
		if (!listenerInterface)
			return nullptr; // FindClass ya dejo una excepcion pendiente
		onInfo = env->GetMethodID(listenerInterface, "onInfo", "(Ljava/lang/String;)V");
		env->DeleteLocalRef(listenerInterface);
		if (!onInfo)
			return nullptr; // GetMethodID ya dejo una excepcion pendiente
	}

	auto forward = [&](const std::string &text) {
		if (!listener || !onInfo || env->ExceptionCheck())
			return;
		jstring jtext = toJString(env, text);
		if (jtext) {
			env->CallVoidMethod(listener, onInfo, jtext);
			env->DeleteLocalRef(jtext);
		}
	};

	try {
		std::string output = chromatic::runScript(fromJString(env, script), forward);
		if (env->ExceptionCheck())
			return nullptr; // el listener lanzo una excepcion: se propaga a Kotlin
		return toJString(env, output);
	} catch (const chromatic::PraatError &e) {
		throwJava(env, "java/lang/RuntimeException", e.what());
	} catch (const std::exception &e) {
		LOGE("nativeRunScript: %s", e.what());
		throwJava(env, "java/lang/RuntimeException", e.what());
	}
	return nullptr;
}

} // extern "C"
