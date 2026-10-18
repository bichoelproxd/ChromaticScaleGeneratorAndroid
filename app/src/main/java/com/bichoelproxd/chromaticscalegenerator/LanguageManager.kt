package com.bichoelproxd.chromaticscalegenerator

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.util.Locale

/**
 * Idioma de la app. Se elige la primera vez que se abre y se puede cambiar despues.
 * Idiomas: ingles, espanol, portugues (Portugal) y portugues (Brasil).
 */
object LanguageManager {

    class Language(
        /** Etiqueta de idioma BCP 47 que usa Android (en, es, pt-PT, pt-BR). */
        val tag: String,
        /** Nombre del idioma escrito en ese mismo idioma. */
        val nativeName: String,
    )

    val LANGUAGES = listOf(
        Language("en", "English"),
        Language("es", "Español"),
        Language("pt-PT", "Português (Portugal)"),
        Language("pt-BR", "Português (Brasil)"),
    )

    private const val PREFS = "app_prefs"
    private const val KEY_LANGUAGE = "language_tag"

    /** Idioma guardado, o null si todavia no se eligio ninguno. */
    fun savedTag(context: Context): String? =
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_LANGUAGE, null)
                ?.takeIf { tag -> LANGUAGES.any { it.tag == tag } }
        } catch (_: Exception) {
            null
        }

    /** Aplica el idioma guardado (se llama al arrancar el proceso). */
    fun applySaved(context: Context) {
        savedTag(context)?.let { setLocale(it) }
    }

    /** Guarda y aplica el idioma. AppCompat vuelve a crear la pantalla con las cadenas nuevas. */
    fun choose(context: Context, tag: String) {
        try {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_LANGUAGE, tag).apply()
        } catch (_: Exception) {
            // Si no se puede guardar, el idioma igual se aplica en esta sesion.
        }
        setLocale(tag)
    }

    private fun setLocale(tag: String) {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }

    /** Idioma de la lista que mejor coincide con el del telefono (para preseleccionarlo la primera vez). */
    fun suggestedTag(): String {
        val device = Locale.getDefault()
        return when {
            device.language == "es" -> "es"
            device.language == "pt" && device.country.equals("BR", ignoreCase = true) -> "pt-BR"
            device.language == "pt" -> "pt-PT"
            else -> "en"
        }
    }

    fun nativeNameOf(tag: String?): String =
        LANGUAGES.firstOrNull { it.tag == tag }?.nativeName ?: LANGUAGES.first().nativeName
}
