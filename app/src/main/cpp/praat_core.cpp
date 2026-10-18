// Basado en la inicializacion y en `parselmouth.praat.run` de Parselmouth
// (Copyright (C) 2018-2026 Yannick Jadoul, GPL-3.0-o-posterior), adaptado para no usar Python.
//
// This program is free software: you can redistribute it and/or modify it under the terms
// of the GNU General Public License as published by the Free Software Foundation, either
// version 3 of the License, or (at your option) any later version.

#include "praat_core.h"

#include <praat/sys/praat.h>
#include <praat/sys/praat_script.h>
#include <praat/sys/praat_version.h>
#include <praat/sys/praatP.h>

#include <cstdlib>
#include <unistd.h>

#define CHROMATIC_XSTR(s) CHROMATIC_STR(s)
#define CHROMATIC_STR(s) #s

// Igual que en Parselmouth: esta funcion debe estar fuera de un espacio de nombres anonimo
// para que INCLUDE_LIBRARY (que declara `extern void praat_uvafon_init()`) funcione.
static void initializePraatOnce() {
	static bool initialized = false;
	if (initialized)
		return;
	praatlib_init();
	INCLUDE_LIBRARY(praat_uvafon_init)
	initialized = true;
}

namespace chromatic {
namespace {

// Los "procs" de Melder son punteros a funcion simples, asi que el destino va en variables estaticas.
const std::function<void(const std::string &)> *g_onInfo = nullptr;
std::string *g_output = nullptr;

void infoProc(const char32 *message, size_t i) {
	std::string text = Melder_peek32to8(&message[i]);
	if (g_output)
		g_output->append(text);
	if (g_onInfo && *g_onInfo)
		(*g_onInfo)(text);
}

void crashProc(const char32 *message) {
	// Igual que Parselmouth: convertir un fallo fatal de Praat en una excepcion de C++.
	throw PraatError(std::string("Praat se detuvo con un error fatal: ") + Melder_peek32to8(message));
}

void warningProc(const char32 *message) {
	// Los avisos de Praat se anaden a la salida del script.
	std::string text = std::string("[aviso de Praat] ") + Melder_peek32to8(message) + "\n";
	if (g_output)
		g_output->append(text);
	if (g_onInfo && *g_onInfo)
		(*g_onInfo)(text);
}

void errorProc(const char32 *) {
	// Los errores se recogen con Melder_getError() al capturar MelderError; aqui no se hace nada
	// para que Praat no los escriba por stderr.
}

// Elimina los objetos que pudieran quedar en la lista de objetos de Praat
// (igual que el destructor de PraatEnvironment en Parselmouth).
void removeAllObjects() {
	try {
		PraatObjects objects = theCurrentPraatObjects;
		for (auto i = objects->n; i > 0; --i)
			praat_removeObject(i);
		praat_show();
	} catch (MelderError) {
		Melder_clearError();
	}
}

} // namespace

std::string initialize(const std::string &homeDir) {
	if (!homeDir.empty()) {
		setenv("HOME", homeDir.c_str(), 1);
		if (chdir(homeDir.c_str()) != 0) {
			// No es critico: Praat usara la carpeta actual que tenga el proceso.
		}
	}

	Melder_setCrashProc(crashProc);
	try {
		initializePraatOnce();
		Melder_setInformationProc(infoProc);
		Melder_setWarningProc(warningProc);
		Melder_setErrorProc(errorProc);
		praat_testPlatformAssumptions();
	} catch (MelderError) {
		std::string message = Melder_peek32to8(Melder_getError());
		Melder_clearError();
		throw PraatError("No se pudo inicializar Praat: " + message);
	}
	return CHROMATIC_XSTR(PRAAT_VERSION_STR);
}

std::string runScript(const std::string &scriptUtf8, const std::function<void(const std::string &)> &onInfo) {
	std::string output;
	g_onInfo = &onInfo;
	g_output = &output;

	auto finish = [&]() {
		g_onInfo = nullptr;
		g_output = nullptr;
		removeAllObjects();
	};

	try {
		praat_executeScriptFromText(Melder_peek8to32(scriptUtf8.c_str()));
	} catch (MelderError) {
		std::string message = Melder_peek32to8(Melder_getError());
		Melder_clearError();
		finish();
		while (!message.empty() && (message.back() == '\n' || message.back() == '\r'))
			message.pop_back();
		throw PraatError(message);
	} catch (...) {
		finish();
		throw;
	}

	finish();
	return output;
}

} // namespace chromatic
