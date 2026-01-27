rootProject.name = "KuroStreamPlugins"

// Definimos la ubicación real de cada carpeta para que el robot no se pierda
include("JKAnimeProvider")
project(":JKAnimeProvider").projectDir = file("JKAnimeProvider")

include("AnimeflvProvider")
project(":AnimeflvProvider").projectDir = file("AnimeflvProvider")
