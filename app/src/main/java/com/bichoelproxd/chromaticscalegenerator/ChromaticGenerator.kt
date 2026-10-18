package com.bichoelproxd.chromaticscalegenerator

import android.content.Context
import java.io.File

/**
 * Port de chromatic_gen.py (GeneratorGUI.generate_chromatic) a Android.
 *
 * El programa de PC llamaba a Praat con `parselmouth.praat.call(...)`. Aqui los mismos comandos
 * de Praat estan en el script `assets/chromatic_scale.praat` y se ejecutan con el motor de Praat
 * compilado para Android ([PraatBridge]). Esta clase prepara los parametros y lanza el script.
 */
object ChromaticGenerator {

    /** Parametros de la interfaz (los mismos controles que la version de PC). */
    data class Params(
        /** Indice de la nota inicial: 0 = C, 1 = C#, ... 11 = B. */
        val startNote: Int,
        /** Indice de la octava inicial: 0 = octava 2, 1 = octava 3, 2 = octava 4. */
        val startOctaveIndex: Int,
        /** Rango en semitonos. */
        val semitones: Int,
        /** Separacion entre muestras, en segundos. */
        val gapSeconds: Double,
        /** Ajustar el tono de cada muestra. */
        val pitched: Boolean,
        /** Guardar cada muestra con su tono en pitched_samples/. */
        val dumpSamples: Boolean,
    )

    private const val SCRIPT_ASSET = "chromatic_scale.praat"

    /** Escapa un texto para usarlo como cadena de Praat ("" dentro de comillas). */
    private fun praatString(value: String) = "\"" + value.replace("\"", "\"\"") + "\""

    /** Praat siempre usa punto decimal, sin importar el idioma del telefono. */
    private fun praatNumber(value: Double) = java.lang.Double.toString(value)

    fun buildScript(context: Context, params: Params, sampleDir: File, outputDir: File): String {
        val body = context.assets.open(SCRIPT_ASSET).use { it.readBytes().toString(Charsets.UTF_8) }
        val startKey = params.startNote + 12 * params.startOctaveIndex
        return buildString {
            append("sampleDir$ = ").append(praatString(sampleDir.absolutePath)).append('\n')
            append("outputDir$ = ").append(praatString(outputDir.absolutePath)).append('\n')
            append("startKey = ").append(startKey).append('\n')
            append("semitones = ").append(params.semitones).append('\n')
            append("gapSeconds = ").append(praatNumber(params.gapSeconds)).append('\n')
            append("pitched = ").append(if (params.pitched) 1 else 0).append('\n')
            append("dumpSamples = ").append(if (params.dumpSamples) 1 else 0).append('\n')
            append(body)
        }
    }

    /**
     * Genera la escala cromatica. [sampleDir] contiene 1.wav, 2.wav, ... y los resultados se
     * escriben en [outputDir] (chromatic.wav y pitched_samples/pitched_N.wav).
     *
     * [onProgress] recibe (hechas, total). Debe llamarse desde un hilo de fondo.
     */
    fun generate(
        context: Context,
        params: Params,
        sampleDir: File,
        outputDir: File,
        onProgress: (done: Int, total: Int) -> Unit,
    ) {
        val script = buildScript(context, params, sampleDir, outputDir)
        val lineBuffer = StringBuilder()
        PraatBridge.runScript(context, script, object : PraatBridge.InfoListener {
            override fun onInfo(text: String) {
                // La salida puede llegar partida en trozos: se junta por lineas.
                lineBuffer.append(text)
                while (true) {
                    val newline = lineBuffer.indexOf("\n")
                    if (newline < 0) break
                    val line = lineBuffer.substring(0, newline).trim()
                    lineBuffer.delete(0, newline + 1)
                    parseProgress(line)?.let { (done, total) -> onProgress(done, total) }
                }
            }
        })
    }

    private fun parseProgress(line: String): Pair<Int, Int>? {
        if (!line.startsWith("PROGRESS ")) return null
        val parts = line.removePrefix("PROGRESS ").trim().split(' ')
        if (parts.size != 2) return null
        val done = parts[0].toDoubleOrNull()?.toInt() ?: return null
        val total = parts[1].toDoubleOrNull()?.toInt() ?: return null
        return done to total
    }
}
