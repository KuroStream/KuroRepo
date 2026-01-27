package com.kurostream.extensions

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.Collections

class SoloLatinoProvider : MainAPI() {
    override var mainUrl = "https://sololatino.net"
    override var name = "SoloLatino"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Anime,
        TvType.Cartoon,
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair("Peliculas", "$mainUrl/peliculas"),
            Pair("Series", "$mainUrl/series"),
            Pair("Animes", "$mainUrl/animes"),
            Pair("Cartoons", "$mainUrl/genre_series/toons"),
        )

        for (item in urls) {
            val (name, url) = item
            val tvType = when (name) {
                "Peliculas" -> TvType.Movie
                "Series" -> TvType.TvSeries
                "Animes" -> TvType.Anime
                "Cartoons" -> TvType.Cartoon
                else -> TvType.Others
            }
            val doc = app.get(url).document
            val home = doc.select("div.items article.item").map {
                val title = it.selectFirst("a div.data h3")?.text()
                val link = it.selectFirst("a")?.attr("href")
                val img = it.selectFirst("div.poster img.lazyload")?.attr("data-srcset")
                newTvSeriesSearchResponse(
                    title!!,
                    link!!,
                    tvType
                ) {
                    this.posterUrl = img
                }
            }
            items.add(HomePageList(name, home))
        }
        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val doc = app.get(url).document
        return doc.select("div.items article.item").map {
            val title = it.selectFirst("a div.data h3")?.text()
            val link = it.selectFirst("a")?.attr("href")
            val img = it.selectFirst("div.poster img.lazyload")?.attr("data-srcset")
            newTvSeriesSearchResponse(
                title!!,
                link!!,
                TvType.TvSeries
            ) {
                this.posterUrl = img
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        val tvType = if (url.contains("peliculas")) TvType.Movie else TvType.TvSeries
        val title = doc.selectFirst("div.data h1")?.text() ?: ""
        val poster = doc.selectFirst("div.poster img")!!.attr("src")
        val description = doc.selectFirst("div.wp-content")!!.text()
        val tags = doc.select("div.sgeneros a").map { it.text() }
        val episodes = if (tvType == TvType.TvSeries) {
            doc.select("div#seasons div.se-c").flatMap { season ->
                season.select("ul.episodios li").map {
                    val epurl = fixUrl(it.selectFirst("a")?.attr("href") ?: "")
                    val epTitle = it.selectFirst("div.episodiotitle div.epst")!!.text()
                    val seasonEpisodeNumber =
                        it.selectFirst("div.episodiotitle div.numerando")?.text()?.split("-")?.map { s ->
                            s.trim().toIntOrNull()
                        }
                    val realimg = it.selectFirst("div.imagen img")?.attr("src")
                    newEpisode(epurl) {
                        this.name = epTitle
                        this.season = seasonEpisodeNumber?.getOrNull(0)
                        this.episode = seasonEpisodeNumber?.getOrNull(1)
                        this.posterUrl = realimg
                    }
                }
            }
        } else listOf()

        return when (tvType) {
            TvType.TvSeries -> {
                newTvSeriesLoadResponse(
                    title,
                    url, tvType, episodes,
                ) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = poster
                    this.plot = description
                    this.tags = tags
                }
            }

            TvType.Movie -> {
                newMovieLoadResponse(title, url, tvType, url) {
                    this.posterUrl = poster
                    this.backgroundPosterUrl = poster
                    this.plot = description
                    this.tags = tags
                }
            }

            else -> null
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val capturedLinks = Collections.synchronizedList(ArrayList<ExtractorLink>())
        val regex = """(go_to_player|go_to_playerVast)\('(.*?)'""".toRegex()
        val frameUrl = app.get(data).document.selectFirst("iframe")?.attr("src")
        if (frameUrl != null) {
            val html = app.get(frameUrl).document.html()
            val matches = regex.findAll(html).map { it.groupValues.get(2) }.toList()
            for (item in matches) {
                loadExtractor(item, data, subtitleCallback) { l -> capturedLinks.add(l) }
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