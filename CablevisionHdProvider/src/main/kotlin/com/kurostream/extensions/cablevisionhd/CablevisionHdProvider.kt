package com.kurostream.extensions.cablevisionhd

import android.util.Base64
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.JsUnpacker
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.net.URL
import java.util.Collections

class CablevisionHdProvider : MainAPI() {

    override var mainUrl = "https://www.cablevisionhd.com"
    override var name = "CablevisionHd"
    override var lang = "es"

    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
            TvType.Live,
    )

    private fun decodeBase64UntilUnchanged(encodedString: String): String {
        var decodedString = encodedString
        var previousDecodedString = ""
        while (decodedString != previousDecodedString) {
            previousDecodedString = decodedString
            decodedString = try {
                val decodedBytes = Base64.decode(decodedString, Base64.DEFAULT)
                String(decodedBytes)
            } catch (e: IllegalArgumentException) {
                // If decoding fails (e.g., not valid base64), break the loop
                break
            }
        }

        return decodedString
    }

    val nowAllowed = setOf("Únete al chat", "Donar con Paypal", "Lizard Premium")

    val deportesCat = setOf(
            "TUDN",
            "WWE",
            "Afizzionados",
            "Gol Perú",
            "Gol TV",
            "TNT SPORTS",
            "Fox Sports Premium",
            "TYC Sports",
            "Movistar Deportes (Perú)",
            "Movistar La Liga",
            "Movistar Liga De Campeones",
            "Dazn F1",
            "Dazn La Liga",
            "Bein La Liga",
            "Bein Sports Extra",
            "Directv Sports",
            "Directv Sports 2",
            "Directv Sports Plus",
            "Espn Deportes",
            "Espn Extra",
            "Espn Premium",
            "Espn",
            "Espn 2",
            "Espn 3",
            "Espn 4",
            "Espn Mexico",
            "Espn 2 Mexico",
            "Espn 3 Mexico",
            "Fox Deportes",
            "Fox Sports",
            "Fox Sports 2",
            "Fox Sports 3",
            "Fox Sports Mexico",
            "Fox Sports 2 Mexico",
            "Fox Sports 3 Mexico",
            "Fox Sports Premium Mexico",
            "Fox Sports Premium",
            "F1 TV",
            "Golf Channel",
            "NBA TV",
            "MLB Network",
            "NFL Network",
    )

    val entretenimientoCat = setOf(
            "Telefe",
            "El Trece",
            "Televisión Pública",
            "Telemundo Puerto rico",
            "Univisión",
            "Univisión Tlnovelas",
            "Pasiones",
            "Caracol",
            "RCN",
            "Latina",
            "America TV",
            "Willax TV",
            "ATV",
            "Las Estrellas",
            "Tl Novelas",
            "Galavision",
            "Azteca 7",
            "Azteca Uno",
            "Canal 5",
            "Distrito Comedia",
            "Comedy Central",
            "A&E",
            "Lifetime",
            "E! Entertainment",
            "Discovery H&H",
            "Food Network",
            "HGTV",
            "TLC",
            "ID",
            "Discovery Channel",
            "Discovery World",
            "Discovery Theater",
            "Discovery Science",
            "Discovery Familia",
            "History",
            "History 2",
            "Animal Planet",
            "Nat Geo",
            "Nat Geo Mundo",
    )

    val noticiasCat = setOf(
            "Telemundo 51",
            "CNN en Español",
            "CNN Chile",
            "Fox News",
            "BBC World News",
            "RT en Español",
            "Telesur",
            "Mileno TV",
            "Foro TV",
            "N+",
            "C5N",
            "TN",
            "Crónica TV",
            "A24",
    )

    val peliculasCat = setOf(
            "Movistar Accion",
            "Movistar Drama",
            "Universal Channel",
            "TNT",
            "TNT Series",
            "Star Channel",
            "Star Action",
            "Star Series",
            "Cinemax",
            "Space",
            "Syfy",
            "Warner Channel",
            "Warner Channel (México)",
            "Cinecanal",
            "FX",
            "AXN",
            "AMC",
            "Studio Universal",
            "Multipremier",
            "Golden",
            "Golden Plus",
            "Golden Edge",
            "Golden Premier",
            "Golden Premier 2",
            "Sony",
            "DHE",
            "NEXT HD",
            "HBO",
            "HBO 2",
            "HBO Family",
            "HBO Plus",
            "HBO Signature",
            "HBO Mundi",
            "HBO Pop",
            "HBO Xtreme",
    )

    val infantilCat = setOf(
            "Cartoon Network",
            "Tooncast",
            "Cartoonito",
            "Disney Channel",
            "Disney JR",
            "Nick",
            "Nick Jr",
            "Discovery Kids",
            "Boomerang",
            "Nat Geo Kids",
    )

    val dos47Cat = setOf(
            "24/7",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
                Pair("Deportes", mainUrl),
                Pair("Entretenimiento", mainUrl),
                Pair("Noticias", mainUrl),
                Pair("Peliculas", mainUrl),
                Pair("Infantil", mainUrl),
                Pair("Educacion", mainUrl),
                Pair("24/7", mainUrl),
                Pair("Todos", mainUrl),
        )
        val doc = app.get(mainUrl).document
        val elements = doc.select("div.canal-item")
        
        urls.forEach { (name, url) ->
            val home = elements.filterNot { element ->
                val text = element.selectFirst("h4")?.text() ?: ""
                nowAllowed.any {
                    text.contains(it, ignoreCase = true)
                } || text.isBlank()
            }.filter {
                val text = it.selectFirst("h4")?.text()?.trim() ?: ""
                when (name) {
                    "Deportes" -> deportesCat.any { cat -> text.equals(cat, ignoreCase = true) }
                    "Entretenimiento" -> entretenimientoCat.any { cat -> text.equals(cat, ignoreCase = true) }
                    "Noticias" -> noticiasCat.any { cat -> text.equals(cat, ignoreCase = true) }
                    "Peliculas" -> peliculasCat.any { cat -> text.equals(cat, ignoreCase = true) }
                    "Infantil" -> infantilCat.any { cat -> text.equals(cat, ignoreCase = true) }
                    // Reusing entretenimientoCat for Educacion as it was mixed in original
                    "Educacion" -> entretenimientoCat.any { cat -> text.equals(cat, ignoreCase = true) } 
                    "24/7" -> text.contains("24/7", ignoreCase = true)
                    "Todos" -> true
                    else -> true
                }
            }.map {
                val title = it.selectFirst("h4")?.text() ?: ""
                val img = it.selectFirst("img")?.attr("src") ?: ""
                val link = it.selectFirst("a")?.attr("href") ?: ""
                newLiveSearchResponse(title, link, TvType.Live) {
                    this.posterUrl = fixUrl(img)
                }
            }
            if (home.isNotEmpty()) {
                items.add(HomePageList(name, home, true))
            }
        }

        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get(mainUrl).document
        return doc.select("div.canal-item").filterNot { element ->
            val text = element.selectFirst("h4")?.text() ?: ""
            nowAllowed.any {
                text.contains(it, ignoreCase = true)
            } || text.isBlank()
        }.filter { element ->
            element.selectFirst("h4")?.text()?.contains(query, ignoreCase = true) ?: false
        }.map {
            val title = it.selectFirst("h4")?.text() ?: ""
            val img = it.selectFirst("img")?.attr("src") ?: ""
            val link = it.selectFirst("a")?.attr("href") ?: ""
            newLiveSearchResponse(title, link, TvType.Live) {
                this.posterUrl = fixUrl(img)
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val poster = doc.selectFirst("div.card-body img")?.attr("src")?.replace(Regex("\\/p\\/w\\d+.*\\/"), "/p/original/") ?: ""
        val title = doc.selectFirst("div.block-title h2")?.text() ?: ""
        val desc = doc.selectFirst("div.card-body div.info")?.text() ?: ""

        return newMovieLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl = fixUrl(poster)
            this.plot = desc
        }
    }

    override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ): Boolean {
        val capturedLinks = Collections.synchronizedList(ArrayList<ExtractorLink>())
        val doc = app.get(data).document
        val btnElements = doc.select("a.btn.btn-md")

        for (it in btnElements) {
            val trembedlink = it.attr("href")
            if (trembedlink.contains("/stream")) {
                val tremrequest = app.get(trembedlink, headers = mapOf(
                        "Host" to "www.cablevisionhd.com",
                        "User-Agent" to USER_AGENT,
                        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                        "Accept-Language" to "en-US,en;q=0.5",
                        "Referer" to data,
                        "Alt-Used" to "www.cablevisionhd.com",
                        "Connection" to "keep-alive",
                        "Upgrade-Insecure-Requests" to "1",
                        "Sec-Fetch-Dest" to "iframe",
                        "Sec-Fetch-Mode" to "navigate",
                        "Sec-Fetch-Site" to "same-origin",
                )).document
                val trembedlink2 = tremrequest.selectFirst("iframe")?.attr("src") ?: ""
                val tremrequest2 = app.get(trembedlink2, headers = mapOf(
                        "User-Agent" to USER_AGENT,
                        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                        "Accept-Language" to "en-US,en;q=0.5",
                        "Referer" to mainUrl,
                        "Connection" to "keep-alive",
                        "Upgrade-Insecure-Requests" to "1",
                        "Sec-Fetch-Dest" to "iframe",
                        "Sec-Fetch-Mode" to "navigate",
                        "Sec-Fetch-Site" to "cross-site",
                )).document
                val scriptPacked = tremrequest2.select("script").find { s -> s.html().contains("function(p,a,c,k,e,d)") }?.html()
                val script = JsUnpacker(scriptPacked)
                if (script.detect()) {
                    val regex = """MARIOCSCryptOld\("(.*?)"\)""".toRegex()
                    val match = regex.find(script.unpack() ?: "")
                    val hash = match?.groupValues?.get(1) ?: ""
                    val extractedurl = decodeBase64UntilUnchanged(hash)
                    if (extractedurl.isNotBlank()) {
                        val finalName = it.text() ?: getHostUrl(extractedurl)
                        capturedLinks.add(
                            newExtractorLink(
                                    finalName,
                                    finalName,
                                    extractedurl,
                                    null
                            ) {
                                this.referer = "${getBaseUrl(extractedurl)}/"
                                this.quality = getQualityFromName("")
                            }
                        )
                    }
                }
            }
        }
        
        // Strict Filtering for Live TV
        val allowedLinks = capturedLinks.filter {
             !it.name.contains("Castellano", true) &&
             !it.name.contains("España", true) &&
             !it.name.contains("Spain", true)
        }
        
        val hasLatino = allowedLinks.any { it.name.contains("Latino", true) || it.name.contains("LAT", true) }
        
        val finalLinks = if (hasLatino) {
            allowedLinks.filter { it.name.contains("Latino", true) || it.name.contains("LAT", true) }
        } else {
            allowedLinks
        }
        
        finalLinks.forEach(callback)
        
        return true
    }

    fun getBaseUrl(urlString: String): String {
        return try {
            val url = URL(urlString)
            "${url.protocol}://${url.host}"
        } catch (e: Exception) {
            ""
        }
    }

    fun getHostUrl(urlString: String): String {
        return try {
            val url = URL(urlString)
            url.host
        } catch (e: Exception) {
            ""
        }
    }
}
