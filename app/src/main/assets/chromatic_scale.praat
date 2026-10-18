# Escala cromatica a partir de muestras numeradas (1.wav, 2.wav, ...).
#
# Es la version para Android de chromatic_gen.py (GeneratorGUI.generate_chromatic): son los mismos
# comandos de Praat que ese programa llamaba con parselmouth.praat.call(...), escritos como script
# de Praat y ejecutados por el motor de Praat compilado para Android.
#
# La app (ChromaticGenerator.kt) antepone estas variables antes de ejecutar el script:
#   sampleDir$   carpeta de trabajo con 1.wav, 2.wav, ... (copia local de la carpeta elegida)
#   outputDir$   carpeta donde se escriben chromatic.wav y pitched_samples/
#   startKey     nota inicial + 12 * octava inicial (indice, como en la interfaz de PC)
#   semitones    rango (cantidad de semitonos)
#   gapSeconds   separacion entre muestras, en segundos
#   pitched      1 = ajustar el tono de cada muestra, 0 = usar las muestras tal cual
#   dumpSamples  1 = guardar tambien cada muestra con su tono en pitched_samples/

samplingRate = 48000

# Cuantas muestras numeradas hay (1.wav, 2.wav, ...)
fileCount = 0
while fileReadable (sampleDir$ + "/" + string$ (fileCount + 1) + ".wav")
	fileCount += 1
endwhile
if fileCount = 0
	exitScript: "No se encontro 1.wav en la carpeta de muestras."
endif
if semitones < 1
	exitScript: "El rango debe ser al menos 1."
endif

# Silencio entre muestras
Create Sound from formula: "Gap", 1, 0, gapSeconds, samplingRate, "0"
gapSound = selected ("Sound")

for i from 0 to semitones - 1
	appendInfoLine: "PROGRESS ", i, " ", semitones

	# Muestra (i mod cantidad) + 1, remuestreada a 48 kHz y en mono
	sampleIndex = (i mod fileCount) + 1
	Read from file: sampleDir$ + "/" + string$ (sampleIndex) + ".wav"
	rawSound = selected ("Sound")
	Resample: samplingRate, 1
	resampledSound = selected ("Sound")
	removeObject: rawSound
	selectObject: resampledSound
	Convert to mono
	monoSound = selected ("Sound")
	removeObject: resampledSound

	if pitched
		# Cambiar el tono: el tier de tono se fija a la frecuencia de la nota (C1 = 32.703 Hz)
		selectObject: monoSound
		To Manipulation: 0.05, 60, 600
		manipulation = selected ("Manipulation")
		Extract pitch tier
		pitchTier = selected ("PitchTier")
		Formula: "32.703 * (2 ^ (" + string$ (i + startKey + 12) + "/12))"
		selectObject: pitchTier
		plusObject: manipulation
		Replace pitch tier
		selectObject: manipulation
		Get resynthesis (overlap-add)
		noteSound [i + 1] = selected ("Sound")
		removeObject: monoSound, manipulation, pitchTier
	else
		noteSound [i + 1] = monoSound
	endif

	# Un silencio despues de cada muestra. Se crea justo despues de la nota porque Concatenate
	# toma los sonidos en el orden de la lista de objetos.
	selectObject: gapSound
	Copy: "gapcopy"
	gapCopy [i + 1] = selected ("Sound")
endfor

# chromatic.wav = nota 1, silencio, nota 2, silencio, ...
selectObject: noteSound [1]
plusObject: gapCopy [1]
for i from 2 to semitones
	plusObject: noteSound [i]
	plusObject: gapCopy [i]
endfor
Concatenate
chromatic = selected ("Sound")
Save as WAV file: outputDir$ + "/chromatic.wav"
appendInfoLine: "SAVED chromatic.wav"
removeObject: chromatic

# Cada muestra con su tono, en pitched_samples/pitched_N.wav
if dumpSamples and pitched
	createFolder: outputDir$ + "/pitched_samples"
	for i from 1 to semitones
		selectObject: noteSound [i]
		Save as WAV file: outputDir$ + "/pitched_samples/pitched_" + string$ (i) + ".wav"
		appendInfoLine: "SAVED pitched_", i, ".wav"
	endfor
endif

appendInfoLine: "PROGRESS ", semitones, " ", semitones

select all
Remove
