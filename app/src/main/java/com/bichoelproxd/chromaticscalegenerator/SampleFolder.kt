package com.bichoelproxd.chromaticscalegenerator

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.IOException

/**
 * Android no deja que Praat abra directamente las carpetas que elige el usuario (almacenamiento
 * con alcance). Por eso las muestras se copian a una carpeta privada de la app, se procesan alli
 * y los resultados se copian de vuelta a la carpeta elegida.
 */
class SampleFolder(private val context: Context, treeUri: Uri) {

    private val tree: DocumentFile = DocumentFile.fromTreeUri(context, treeUri)
        ?: throw IOException("invalid folder")

    val displayName: String get() = tree.name ?: tree.uri.toString()

    fun canAccess(): Boolean = tree.exists() && tree.isDirectory && tree.canRead() && tree.canWrite()

    /**
     * Copia a [destination] los archivos con nombre numerico (1.wav, 2.wav, ...), como los busca
     * la version de PC. Devuelve cuantos archivos copio.
     */
    fun copySamplesTo(destination: File): Int {
        destination.deleteRecursively()
        destination.mkdirs()
        var count = 0
        for (file in tree.listFiles()) {
            val name = file.name ?: continue
            if (!file.isFile || !SAMPLE_NAME.matches(name)) continue
            val target = File(destination, name.lowercase())
            context.contentResolver.openInputStream(file.uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: throw IOException("cannot read $name")
            count++
        }
        return count
    }

    /**
     * Copia chromatic.wav y pitched_samples/ desde [source] a la carpeta elegida,
     * reemplazando los archivos que ya existan.
     */
    fun copyResultsFrom(source: File) {
        val chromatic = File(source, "chromatic.wav")
        if (chromatic.isFile) writeFile(tree, "chromatic.wav", chromatic)

        val pitchedDir = File(source, "pitched_samples")
        if (pitchedDir.isDirectory) {
            val target = tree.findFile("pitched_samples")?.takeIf { it.isDirectory }
                ?: tree.createDirectory("pitched_samples")
                ?: throw IOException("cannot create pitched_samples")
            pitchedDir.listFiles()?.sortedBy { it.name }?.forEach { writeFile(target, it.name, it) }
        }
    }

    private fun writeFile(directory: DocumentFile, name: String, source: File) {
        val existing = directory.findFile(name)
        val destination = if (existing != null && existing.isFile) {
            existing
        } else {
            directory.createFile("audio/x-wav", name) ?: throw IOException("cannot create $name")
        }
        // "wt" vacia el archivo existente antes de escribir.
        context.contentResolver.openOutputStream(destination.uri, "wt")?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: throw IOException("cannot write $name")
    }

    companion object {
        private val SAMPLE_NAME = Regex("^[0-9]+\\.[wW][aA][vV]$")
    }
}
