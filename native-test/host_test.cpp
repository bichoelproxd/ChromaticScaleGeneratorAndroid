// Programa de prueba para PC (Linux/macOS): ejecuta exactamente el mismo script de Praat
// (app/src/main/assets/chromatic_scale.praat) y el mismo puente (praat_core) que usa la app de
// Android, para comprobar el resultado sin un telefono. Ver native-test/README.md.
//
// Uso: chromatic_host_test <script.praat> <carpeta_muestras> <carpeta_salida>
//                          <nota_inicial 0-11> <octava_inicial 0-2> <rango> <gap_s> <pitched 0|1> <dump 0|1>
//
// This program is free software: you can redistribute it and/or modify it under the terms
// of the GNU General Public License as published by the Free Software Foundation, either
// version 3 of the License, or (at your option) any later version.

#include "praat_core.h"

#include <cstdio>
#include <cstdlib>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>

static std::string praatString(const std::string &s) {
	std::string out = "\"";
	for (char c : s) {
		if (c == '"')
			out += "\"\"";
		else
			out += c;
	}
	return out + "\"";
}

int main(int argc, char **argv) {
	if (argc != 10) {
		std::cerr << "Uso: " << argv[0]
		          << " <script.praat> <carpeta_muestras> <carpeta_salida> <nota 0-11> <octava 0-2> <rango> <gap_s> <pitched 0|1> <dump 0|1>\n";
		return 2;
	}
	std::ifstream in(argv[1], std::ios::binary);
	if (!in) {
		std::cerr << "No se pudo abrir " << argv[1] << "\n";
		return 2;
	}
	std::stringstream body;
	body << in.rdbuf();

	const int note = std::atoi(argv[4]);
	const int octave = std::atoi(argv[5]);
	const int startKey = note + 12 * octave;

	std::string script;
	script += "sampleDir$ = " + praatString(argv[2]) + "\n";
	script += "outputDir$ = " + praatString(argv[3]) + "\n";
	script += "startKey = " + std::to_string(startKey) + "\n";
	script += "semitones = " + std::string(argv[6]) + "\n";
	script += "gapSeconds = " + std::string(argv[7]) + "\n";
	script += "pitched = " + std::string(argv[8]) + "\n";
	script += "dumpSamples = " + std::string(argv[9]) + "\n";
	script += body.str();

	try {
		std::cout << "Praat " << chromatic::initialize("/tmp") << "\n";
		chromatic::runScript(script, [](const std::string &text) {
			std::cout << text;
			std::cout.flush();
		});
	} catch (const std::exception &e) {
		std::cerr << "ERROR: " << e.what() << "\n";
		return 1;
	}
	std::cout << "OK\n";
	return 0;
}
