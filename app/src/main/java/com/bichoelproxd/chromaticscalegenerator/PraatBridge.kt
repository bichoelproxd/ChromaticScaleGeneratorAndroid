package com.bichoelproxd.chromaticscalegenerator

import android.content.Context

/**
 * Motor de Praat (el mismo codigo fuente que usa Parselmouth) compilado para Android con el NDK.
 *
 * Es el equivalente en Android de `parselmouth.praat.run(...)`: ejecuta un script de Praat y
 * entrega su salida. Las funciones nativas estan en app/src/main/cpp/jni_bridge.cpp.
 *
 * Las funciones `external` son publicas a proposito: si fueran `internal`, Kotlin les cambiaria
 * el nombre y JNI no las encontraria.
 */
object PraatBridge {

    /** Recibe cada trozo de salida del script (`appendInfoLine`, etc.) apenas se produce. */
    interface InfoListener {
        fun onInfo(text: String)
    }

    init {
        System.loadLibrary("chromaticpraat")
    }

    @JvmStatic
    external fun nativeInit(homeDir: String): String

    @JvmStatic
    external fun nativeRunScript(script: String, listener: InfoListener?): String

    @Volatile
    private var praatVersion: String? = null

    /** Inicializa Praat la primera vez y devuelve su version. Seguro de llamar varias veces. */
    @Synchronized
    fun ensureInitialized(context: Context): String {
        praatVersion?.let { return it }
        val home = context.filesDir.absolutePath
        val version = nativeInit(home)
        praatVersion = version
        return version
    }

    /** Ejecuta un script de Praat. Lanza [RuntimeException] con el mensaje de Praat si falla. */
    @Synchronized
    fun runScript(context: Context, script: String, listener: InfoListener?): String {
        ensureInitialized(context)
        return nativeRunScript(script, listener)
    }
}
