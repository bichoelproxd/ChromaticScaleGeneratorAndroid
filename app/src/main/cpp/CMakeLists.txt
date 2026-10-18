# Compila el motor de Praat (codigo fuente de Parselmouth) para Android junto con el puente JNI.
#
# El codigo fuente de Parselmouth/Praat se descarga con scripts/fetch_praat.sh a
# third_party/Parselmouth. Aqui se reutilizan sus propios CMakeLists de Praat (los mismos que
# usa Parselmouth en PC, con NO_GUI NO_AUDIO NO_GRAPHICS NO_NETWORK) y solo se anaden los
# ajustes para Android.
#
# Opciones:
#   -DPARSELMOUTH_DIR=...     carpeta del codigo fuente (por defecto third_party/Parselmouth)
#   -DCHROMATIC_HOST_TEST=ON  compila para PC (sin JNI) un programa de prueba, ver native-test/
cmake_minimum_required(VERSION 3.18)
project(chromaticpraat LANGUAGES C CXX)

option(CHROMATIC_HOST_TEST "Compilar en PC el programa de prueba en lugar de la libreria JNI" OFF)

set(PARSELMOUTH_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../third_party/Parselmouth" CACHE PATH
	"Carpeta con el codigo fuente de Parselmouth (descargado con scripts/fetch_praat.sh)")
get_filename_component(PARSELMOUTH_DIR "${PARSELMOUTH_DIR}" ABSOLUTE)

if (NOT EXISTS "${PARSELMOUTH_DIR}/praat/CMakeLists.txt" OR NOT EXISTS "${PARSELMOUTH_DIR}/extern/fmt/CMakeLists.txt")
	message(FATAL_ERROR
		"No se encontro el codigo fuente de Parselmouth/Praat en:\n  ${PARSELMOUTH_DIR}\n"
		"Ejecuta primero:  scripts/fetch_praat.sh")
endif ()

if (NOT CMAKE_BUILD_TYPE)
	set(CMAKE_BUILD_TYPE Release CACHE STRING "Tipo de compilacion" FORCE)
endif ()

set(CMAKE_POSITION_INDEPENDENT_CODE ON)

# --- Praat y fmt (reutilizando los CMakeLists de Parselmouth) ---------------------------------
add_subdirectory("${PARSELMOUTH_DIR}/extern" "${CMAKE_BINARY_DIR}/extern")
add_subdirectory("${PARSELMOUTH_DIR}/praat" "${CMAKE_BINARY_DIR}/praat")

set_target_properties(praat fmt PROPERTIES
	POSITION_INDEPENDENT_CODE TRUE
	C_VISIBILITY_PRESET hidden
	CXX_VISIBILITY_PRESET hidden
)
# El codigo de Praat y de Parselmouth incluye sus cabeceras como <praat/sys/praat.h>
target_include_directories(praat SYSTEM INTERFACE "${PARSELMOUTH_DIR}")

# --- Ajustes para Android ---------------------------------------------------------------------
if (ANDROID)
	# El CMakeLists de Praat define `linux` solo si CMAKE_SYSTEM_NAME es "Linux"; Android es Linux
	# (bionic), y el compilador ya define `linux`/`__linux__`, pero lo dejamos explicito.
	target_compile_definitions(praat PUBLIC linux)
	# Praat no usa la salida de log de Android; sus avisos de compilacion no son utiles aqui.
	target_compile_options(praat PRIVATE -Wno-everything)
	target_compile_options(fmt PRIVATE -Wno-everything)
endif ()

# --- Libreria JNI o programa de prueba --------------------------------------------------------
if (CHROMATIC_HOST_TEST)
	add_executable(chromatic_host_test "${CMAKE_CURRENT_LIST_DIR}/../../../../native-test/host_test.cpp" praat_core.cpp)
	target_include_directories(chromatic_host_test PRIVATE "${CMAKE_CURRENT_LIST_DIR}")
	target_link_libraries(chromatic_host_test PRIVATE praat)
	set_target_properties(chromatic_host_test PROPERTIES CXX_STANDARD 17 CXX_STANDARD_REQUIRED TRUE CXX_EXTENSIONS FALSE)
else ()
	add_library(chromaticpraat SHARED jni_bridge.cpp praat_core.cpp)
	target_link_libraries(chromaticpraat PRIVATE praat log)
	set_target_properties(chromaticpraat PROPERTIES CXX_STANDARD 17 CXX_STANDARD_REQUIRED TRUE CXX_EXTENSIONS FALSE)
	# (La alineacion para paginas de 16 KB de Android 15+ la activa app/build.gradle.kts con
	#  -DANDROID_SUPPORT_FLEXIBLE_PAGE_SIZES=ON.)
endif ()
