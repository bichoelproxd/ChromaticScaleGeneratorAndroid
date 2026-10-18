// Puente minimo entre C++ y el motor de Praat (el mismo codigo fuente que usa Parselmouth).
//
// Parselmouth es un envoltorio de Python (pybind11) alrededor de Praat. En Android no hay
// Python, asi que aqui se usa directamente el motor de Praat y se expone lo equivalente a
// `parselmouth.praat.run(...)`: ejecutar un script de Praat y capturar su salida.
//
// Este archivo no depende de JNI, para poder probarlo tambien en un PC (ver native-test/).
//
// This program is free software: you can redistribute it and/or modify it under the terms
// of the GNU General Public License as published by the Free Software Foundation, either
// version 3 of the License, or (at your option) any later version.
#ifndef CHROMATIC_PRAAT_CORE_H
#define CHROMATIC_PRAAT_CORE_H

#include <functional>
#include <stdexcept>
#include <string>

namespace chromatic {

// Error de Praat (mensaje ya convertido a UTF-8).
class PraatError : public std::runtime_error {
public:
	explicit PraatError(const std::string &message) : std::runtime_error(message) {}
};

// Inicializa Praat (una sola vez). `homeDir` se usa como HOME y como carpeta actual.
// Devuelve la version de Praat, p. ej. "6.4.16".
std::string initialize(const std::string &homeDir);

// Ejecuta un script de Praat (texto UTF-8). Cada trozo de salida (`appendInfoLine`, etc.)
// se entrega a `onInfo` apenas se produce. Devuelve toda la salida acumulada.
// Lanza PraatError si el script falla. No es reentrante: llamar desde un solo hilo a la vez.
std::string runScript(const std::string &scriptUtf8, const std::function<void(const std::string &)> &onInfo);

} // namespace chromatic

#endif // CHROMATIC_PRAAT_CORE_H
