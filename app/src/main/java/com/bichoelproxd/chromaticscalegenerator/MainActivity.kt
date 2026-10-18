package com.bichoelproxd.chromaticscalegenerator

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.bichoelproxd.chromaticscalegenerator.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File

/** Pantalla principal: los mismos controles que la ventana de PC (app_ui.py). */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private var treeUri: Uri? = null

    @Volatile
    private var busy = false

    private val pickFolder = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onFolderPicked(uri)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Con edge-to-edge (Android 15+) el contenido no debe quedar debajo de las barras del sistema.
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.startNoteChoice.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, NOTES
        )
        binding.startOctaveChoice.adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_dropdown_item, OCTAVES
        )

        // Idioma: se pregunta la primera vez que se abre la app y se puede cambiar con el boton.
        val currentLanguage = LanguageManager.savedTag(this) ?: LanguageManager.suggestedTag()
        binding.languageButton.text =
            getString(R.string.language) + ": " + LanguageManager.nativeNameOf(currentLanguage)
        binding.languageButton.setOnClickListener { showLanguageDialog(cancelable = true) }
        if (LanguageManager.savedTag(this) == null) {
            showLanguageDialog(cancelable = false)
        }

        binding.dirPicker.setOnClickListener { pickFolder.launch(treeUri) }
        binding.chromGenerate.setOnClickListener { generate() }
        binding.pitchedCheck.setOnCheckedChangeListener { _, checked ->
            // Igual que en PC: las muestras con tono solo se guardan si "Pitched" esta activado.
            binding.samplesCheck.isEnabled = checked
        }

        // Recordar la carpeta elegida la ultima vez
        getPreferences(MODE_PRIVATE).getString(PREF_TREE_URI, null)?.let { saved ->
            val uri = Uri.parse(saved)
            val stillGranted = contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission && it.isWritePermission
            }
            if (stillGranted) showFolder(uri)
        }

        // Inicializar Praat en segundo plano para que el primer "Generar" no tenga que esperar.
        praatThread("praat-init") {
            try {
                val version = PraatBridge.ensureInitialized(applicationContext)
                runOnUiThread { setStatus(getString(R.string.status_praat_ready, version)) }
            } catch (e: Throwable) {
                runOnUiThread { setStatus(getString(R.string.error_prefix, e.message ?: e.toString())) }
            }
        }
    }

    /** Lista de idiomas. AppCompat vuelve a crear la pantalla ya traducida al elegir uno. */
    private fun showLanguageDialog(cancelable: Boolean) {
        val current = LanguageManager.savedTag(this) ?: LanguageManager.suggestedTag()
        val names = LanguageManager.LANGUAGES.map { it.nativeName }.toTypedArray()
        val checked = LanguageManager.LANGUAGES.indexOfFirst { it.tag == current }.coerceAtLeast(0)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.choose_language)
            .setSingleChoiceItems(names, checked) { dialog, which ->
                dialog.dismiss()
                LanguageManager.choose(this, LanguageManager.LANGUAGES[which].tag)
            }
            .setCancelable(cancelable)
            .show()
    }

    private fun onFolderPicked(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Si no se puede recordar el permiso, la carpeta funciona igual en esta sesion.
        }
        getPreferences(MODE_PRIVATE).edit().putString(PREF_TREE_URI, uri.toString()).apply()
        showFolder(uri)
    }

    private fun showFolder(uri: Uri) {
        treeUri = uri
        binding.dirPath.text = try {
            SampleFolder(this, uri).displayName
        } catch (_: Exception) {
            uri.toString()
        }
    }

    private fun setStatus(text: String) {
        binding.statusText.text = text
    }

    private fun setBusy(value: Boolean) {
        busy = value
        binding.chromGenerate.isEnabled = !value
        binding.dirPicker.isEnabled = !value
        binding.languageButton.isEnabled = !value
        binding.progressBar.visibility = if (value) View.VISIBLE else View.INVISIBLE
        if (value) {
            binding.progressBar.isIndeterminate = true
        }
    }

    private fun generate() {
        if (busy) return

        val uri = treeUri
        if (uri == null) {
            setStatus(getString(R.string.error_no_folder))
            return
        }
        val semitones = binding.rangeInput.text.toString().trim().toIntOrNull()
        if (semitones == null || semitones < 1) {
            setStatus(getString(R.string.error_bad_range))
            return
        }
        // Aceptar coma o punto como separador decimal
        val gap = binding.gapInput.text.toString().trim().replace(',', '.').toDoubleOrNull()
        if (gap == null || gap < 0.0 || gap.isNaN() || gap.isInfinite()) {
            setStatus(getString(R.string.error_bad_gap))
            return
        }

        val params = ChromaticGenerator.Params(
            startNote = binding.startNoteChoice.selectedItemPosition.coerceAtLeast(0),
            startOctaveIndex = binding.startOctaveChoice.selectedItemPosition.coerceAtLeast(0),
            semitones = semitones,
            gapSeconds = gap,
            pitched = binding.pitchedCheck.isChecked,
            dumpSamples = binding.samplesCheck.isChecked && binding.pitchedCheck.isChecked,
        )

        setBusy(true)
        setStatus(getString(R.string.status_copying))

        praatThread("chromatic-generate") {
            val workRoot = File(cacheDir, "work")
            try {
                val folder = SampleFolder(applicationContext, uri)
                if (!folder.canAccess()) {
                    throw IllegalStateException(getString(R.string.error_folder_access))
                }

                val sampleDir = File(workRoot, "samples")
                val outputDir = File(workRoot, "output")
                outputDir.deleteRecursively()
                outputDir.mkdirs()

                if (folder.copySamplesTo(sampleDir) == 0) {
                    throw IllegalStateException(getString(R.string.error_no_samples))
                }

                ChromaticGenerator.generate(applicationContext, params, sampleDir, outputDir) { done, total ->
                    runOnUiThread {
                        if (total > 0) {
                            binding.progressBar.isIndeterminate = false
                            binding.progressBar.max = total
                            binding.progressBar.progress = done
                        }
                        setStatus(getString(R.string.status_working, done, total))
                    }
                }

                runOnUiThread { setStatus(getString(R.string.status_saving)) }
                folder.copyResultsFrom(outputDir)

                val doneText = if (params.dumpSamples) R.string.status_done_pitched else R.string.status_done
                runOnUiThread {
                    setStatus(getString(doneText))
                    setBusy(false)
                }
            } catch (e: Throwable) {
                val message = e.message ?: e.toString()
                runOnUiThread {
                    setStatus(getString(R.string.error_prefix, message))
                    setBusy(false)
                }
            } finally {
                workRoot.deleteRecursively()
            }
        }
    }

    /**
     * Praat usa recursion profunda al interpretar scripts; el hilo por defecto de Android tiene
     * poca pila, asi que Praat se ejecuta siempre en hilos con 16 MB de pila.
     */
    private fun praatThread(name: String, block: () -> Unit) {
        Thread(null, { block() }, name, PRAAT_STACK_BYTES).start()
    }

    companion object {
        private const val PRAAT_STACK_BYTES = 16L * 1024 * 1024
        private const val PREF_TREE_URI = "tree_uri"
        private val NOTES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
        private val OCTAVES = listOf("2", "3", "4")
    }
}
