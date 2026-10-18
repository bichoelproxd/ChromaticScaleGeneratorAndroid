package com.bichoelproxd.chromaticscalegenerator

import android.app.Application

class ChromaticApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // Aplicar el idioma elegido antes de que se cree ninguna pantalla
        LanguageManager.applySaved(this)
    }
}
