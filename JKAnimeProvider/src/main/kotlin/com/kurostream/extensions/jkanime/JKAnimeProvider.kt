package com.kurostream.extensions.jkanime

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.util.*
import kotlin.collections.ArrayList
import java.util.Collections

class JKAnimeProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Especial")) TvType.OVA
            else if (t.contains("Pelicula")) TvType.AnimeMovie
            else TvType.Anime
        }
    }

    override var mainUrl = "https://jkanime.net"
    override var name = "JKAnime"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.OVA,
        TvType.Anime,
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val urls = listOf(
            Pair(
                "$mainUrl/directorio/?filtro=fecha&tipo=TV&estado=1&fecha=none&temporada=none&orden=desc",
                "En emisión"
            ),
            Pair(
                "$mainUrl/directorio/animes/",
                "Animes"
            ),
            Pair(
                "$mainUrl/directorio/peliculas/",
                "Películas"
            ),
        )

        val items = ArrayList<HomePageList>()
        val isHorizontal = true
        
        val latestEps = app.get(mainUrl).document.select(".listadoanime-home a.bloqq").mapNotNull {
            val title = it.selectFirst("h5")?.text() ?: return@mapNotNull null
            val dubstat = if (title.contains("Latino") || title.contains("Castellano"))
                DubStatus.Dubbed else DubStatus.Subbed
            val poster =
                it.selectFirst(".anime__sidebar__comment__item__pic img")?.attr("src") ?: ""
            val epRegex = Regex("/(\\d+)/|/especial/|/ova/")
            val url = it.attr("href").replace(epRegex, "")
            val epNum =
                it.selectFirst("h6")?.text()?.replace("Episodio ", "")?.toIntOrNull()
            newAnimeSearchResponse(title, url) {
                this.posterUrl = poster
                addDubStatus(dubstat, epNum)
            }
        }
        
        items.add(HomePageList("Últimos episodios", latestEps, isHorizontal))

        for (item in urls) {
            val (url, listName) = item
            val soup = app.get(url).document
            val home = soup.select(".g-0").mapNotNull {
                val title = it.selectFirst("h5 a")?.text() ?: return@mapNotNull null
                val poster = it.selectFirst("img")?.attr("src") ?: ""
                newAnimeSearchResponse(title, fixUrl(it.selectFirst("a")?.attr("href") ?: "")) {
                    this.posterUrl = fixUrl(poster)
                    addDubStatus(if (title.contains("Latino") || title.contains("Castellano")) DubStatus.Dubbed else DubStatus.Subbed)
                }
            }
            items.add(HomePageList(listName, home))
        }

        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val urls = listOf(
            "$mainUrl/buscar/$query/1/",
            "$mainUrl/buscar/$query/2/",
            "$mainUrl/buscar/$query/3/"
        )
        val search = ArrayList<SearchResponse>()
        urls.forEach { ss ->
            val doc = app.get(ss).document
            doc.select("div.row div.anime__item").forEach {
                val title = it.selectFirst(".title")?.text() ?: return@forEach
                val href = it.selectFirst("a")?.attr("href") ?: return@forEach
                val img = it.selectFirst(".set-bg")?.attr("data-setbg") ?: ""
                val isDub = title.contains("Latino") || title.contains("Castellano")
                search.add(
                    newAnimeSearchResponse(title, href) {
                        this.posterUrl = fixUrl(img)
                        addDubStatus(isDub, !isDub)
                    })
            }
        }
        return search
    }

    data class EpsInfo (
        @JsonProperty("number" ) var number : String? = null,
        @JsonProperty("title"  ) var title  : String? = null,
        @JsonProperty("image"  ) var image  : String? = null
    )
    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url, timeout = 120).document
        val poster = doc.selectFirst(".set-bg")?.attr("data-setbg")
        val title = doc.selectFirst(".anime__details__title > h3")?.text() ?: ""
        val description = doc.selectFirst(".anime__details__text > p")?.text()
        val genres = doc.select("div.col-lg-6:nth-child(1) > ul:nth-child(1) > li:nth-child(2) > a")
            .map { it.text() }
        val status = when (doc.selectFirst("span.enemision")?.text()) {
            "En emisión" -> ShowStatus.Ongoing
            "En emision" -> ShowStatus.Ongoing
            "Concluido" -> ShowStatus.Completed
            else -> null
        }
        val type = doc.selectFirst("div.col-lg-6.col-md-6 ul li[rel=tipo]")?.text() ?: ""
        val animeID = doc.selectFirst("div.ml-2")?.attr("data-anime")?.toIntOrNull()
        val episodes = ArrayList<Episode>()
        
        if (animeID != null) {
            val pags = doc.select("a.numbers").map { it.attr("href").substringAfter("#pag") }
            pags.forEach { pagnum ->
                val res = app.get("$mainUrl/ajax/pagination_episodes/$animeID/$pagnum/").text
                val json = parseJson<ArrayList<EpsInfo>>(res)
                json.forEach { info ->
                    val imagetest = !info.image.isNullOrBlank()
                    val image = if (imagetest) "https://cdn.jkdesu.com/assets/images/animes/video/image_thumb/${info.image}" else null
                    val link = "${url.removeSuffix("/")}/${info.number}"
                    val ep = newEpisode(link) {
                        this.posterUrl = image
                        this.episode = info.number?.toIntOrNull()
                    }
                    episodes.add(ep)
                }
            }
        }

        return newAnimeLoadResponse(title, url, getType(type)) {
            this.posterUrl = poster
            addEpisodes(DubStatus.Subbed, episodes)
            showStatus = status
            this.plot = description
            this.tags = genres
        }
    }

    data class Nozomi(
        @JsonProperty("file") val file: String?
    )

    private suspend fun streamClean(
        name: String,
        url: String,
        referer: String,
        quality: String?,
        callback: (ExtractorLink) -> Unit,
        m3u8: Boolean
    ): Boolean {
        callback(
            newExtractorLink(
                name,
                name,
                url,
                null
            ) {
                this.referer = referer
                this.quality = getQualityFromName(quality)
                // isM3u8 is usually set by extractor or not directly exposed as var in some versions
            }
        )
        return true
    }


    private fun fetchjkanime(text: String?): List<String> {
        if (text.isNullOrEmpty()) {
            return listOf()
        }
        val linkRegex =
            Regex("""(iframe.*class.*width)""")
        return linkRegex.findAll(text).map { it.value.trim().removeSurrounding("\"").replace(Regex("(iframe(.class|.src=\")|=\"player_conte\".*src=\"|\".scrolling|\".width)"),"") }.toList()
    }



    data class ServersEncoded (
            @JsonProperty("remote" ) val remote : String,
    )
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val capturedLinks = Collections.synchronizedList(ArrayList<ExtractorLink>())
        
        val doc = app.get(data).document
        val scriptElements = doc.select("script")
        
        for (script in scriptElements) {
            val scriptData = script.data()
            if (scriptData.contains(Regex("slug|remote"))) {
                val serversRegex = Regex("\\[\\{.*?\"remote\".*?\"\\}\\]")
                val servers = serversRegex.findAll(scriptData).map { it.value }.toList().firstOrNull()
                if (servers != null) {
                    val serJson = parseJson<ArrayList<ServersEncoded>>(servers)
                    for (item in serJson) {
                        val encodedurl = item.remote
                        val urlDecoded = base64Decode(encodedurl)
                        loadExtractor(urlDecoded, mainUrl, subtitleCallback) { l -> capturedLinks.add(l) }
                    }
                }
            }


            if (scriptData.contains("var video = []")) {
                val videos = scriptData.replace("\\/", "/")
                for (videoLink in fetchjkanime(videos)) {
                    val link = videoLink.replace("$mainUrl/jkfembed.php?u=", "https://embedsito.com/v/")
                        .replace("$mainUrl/jkokru.php?u=", "http://ok.ru/videoembed/")
                        .replace("$mainUrl/jkvmixdrop.php?u=", "https://mixdrop.co/e/")
                        .replace("$mainUrl/jk.php?u=", "$mainUrl/")
                        .replace("/jkfembed.php?u=","https://embedsito.com/v/")
                        .replace("/jkokru.php?u=", "http://ok.ru/videoembed/")
                        .replace("/jkvmixdrop.php?u=", "https://mixdrop.co/e/")
                        .replace("/jk.php?u=", "$mainUrl/")
                        .replace("/um2.php?","$mainUrl/um2.php?")
                        .replace("/um.php?","$mainUrl/um.php?")
                        .replace("=\"player_conte\" src=", "")
                        
                    for (links in fetchUrls(link)) {
                        loadExtractor(links, data, subtitleCallback) { l -> capturedLinks.add(l) }
                        if (links.contains("um2.php")) {
                            val innerDoc = app.get(links, referer = data).document
                            val gsplaykey = innerDoc.select("form input[value]").attr("value")
                            for (loc in app.post(
                                "$mainUrl/gsplay/redirect_post.php",
                                headers = mapOf(
                                    "Host" to "jkanime.net",
                                    "User-Agent" to USER_AGENT,
                                    "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                                    "Accept-Language" to "en-US,en;q=0.5",
                                    "Referer" to links,
                                    "Content-Type" to "application/x-www-form-urlencoded",
                                    "Origin" to "https://jkanime.net",
                                    "DNT" to "1",
                                    "Connection" to "keep-alive",
                                    "Upgrade-Insecure-Requests" to "1",
                                    "Sec-Fetch-Dest" to "iframe",
                                    "Sec-Fetch-Mode" to "navigate",
                                    "Sec-Fetch-Site" to "same-origin",
                                    "TE" to "trailers",
                                    "Pragma" to "no-cache",
                                    "Cache-Control" to "no-cache",
                                ),
                                data = mapOf(Pair("data", gsplaykey)),
                                allowRedirects = false
                            ).okhttpResponse.headers.values("location")) {
                                val postkey = loc.replace("/gsplay/player.html#", "")
                                val nozomitext = app.post(
                                    "$mainUrl/gsplay/api.php",
                                    headers = mapOf(
                                        "Host" to "jkanime.net",
                                        "User-Agent" to USER_AGENT,
                                        "Accept" to "application/json, text/javascript, */*; q=0.01",
                                        "Accept-Language" to "en-US,en;q=0.5",
                                        "Content-Type" to "application/x-www-form-urlencoded; charset=UTF-8",
                                        "X-Requested-With" to "XMLHttpRequest",
                                        "Origin" to "https://jkanime.net",
                                        "DNT" to "1",
                                        "Connection" to "keep-alive",
                                        "Sec-Fetch-Dest" to "empty",
                                        "Sec-Fetch-Mode" to "cors",
                                        "Sec-Fetch-Site" to "same-origin",
                                    ),
                                    data = mapOf(Pair("v", postkey)),
                                    allowRedirects = false
                                ).text
                                val json = parseJson<Nozomi>(nozomitext)
                                if (json.file != null) {
                                    val nozomiurl = json.file
                                    val nozominame = "Nozomi"
                                    streamClean(
                                        nozominame,
                                        nozomiurl,
                                        "",
                                        null,
                                        { l -> capturedLinks.add(l) },
                                        nozomiurl.contains(".m3u8")
                                    )
                                }
                            }

                        }
                        if (links.contains("um.php")) {
                            val desutext = app.get(links, referer = data).text
                            val desuRegex = Regex("((https:|http:)//.*\\.m3u8)")
                            val file = desuRegex.find(desutext)?.value
                            if (file != null) {
                                val namedesu = "Desu"
                                for (desurl in generateM3u8(
                                    namedesu,
                                    file,
                                    mainUrl,
                                )) {
                                    streamClean(
                                        namedesu,
                                        desurl.url,
                                        mainUrl,
                                        desurl.quality.toString(),
                                        { l -> capturedLinks.add(l) },
                                        true
                                    )
                                }
                            }
                        }

                        if (links.contains("jkmedia")) {
                            for (xtremeurl in app.get(
                                links,
                                referer = data,
                                allowRedirects = false
                            ).okhttpResponse.headers.values("location")) {
                                val namex = "Xtreme S"
                                streamClean(
                                    namex,
                                    xtremeurl,
                                    "",
                                    null,
                                    { l -> capturedLinks.add(l) },
                                    xtremeurl.contains(".m3u8")
                                )
                            }
                        }

                    }
                }
            }
        }
        
        // Strict Filtering
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
}
