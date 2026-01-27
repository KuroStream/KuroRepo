package com.kurostream.extensions

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addDuration
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class SeriesflixProvider : MainAPI() {
    override var mainUrl = "https://seriesflix.fit"
    override var name = "Seriesflix"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val urls = listOf(
            Pair("$mainUrl/series-online/", "Series"),
            Pair("$mainUrl/genero/accion/", "Acción"),
            Pair("$mainUrl/genero/ciencia-ficcion/", "Ciencia ficción"),
        )
        for (item in urls) {
            val (url, name) = item
            val soup = app.get(url).document
            val home = soup.select("article.TPost.B").map {
                val title = it.selectFirst("h2.title")!!.text()
                val link = it.selectFirst("a")!!.attr("href")
                val img = it.selectFirst("img")!!.attr("data-src").replace("//tmdbcdn2.online","https://tmdbcdn2.online").replace(".webp",".jpg")
                newTvSeriesSearchResponse(title, link, TvType.Movie) {
                    this.posterUrl = img
                }
            }

            items.add(HomePageList(name, home))
        }
        if (items.size <= 0) throw ErrorLoadingException()
        return newHomePageResponse(items)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val doc = app.get(url).document
        return doc.select("article.TPost.B").map {
            val href = it.selectFirst("a")!!.attr("href")
            val poster = it.selectFirst("figure img")!!.attr("src")
            val name = it.selectFirst("h2.title")!!.text()
            val isMovie = href.contains("/movies/")
            if (isMovie) {
                newMovieSearchResponse(name, href, TvType.Movie) {
                    this.posterUrl = poster
                }
            } else {
                newTvSeriesSearchResponse(name, href, TvType.TvSeries) {
                    this.posterUrl = poster
                }
            }
        }.toList()
    }


    override suspend fun load(url: String): LoadResponse {
        val type = if (url.contains("/movies/")) TvType.Movie else TvType.TvSeries

        val document = app.get(url).document

        val title = document.selectFirst("h1.Title")!!.text()
        val descRegex = Regex("(Recuerda.*Seriesflix.)")
        val descipt = document.selectFirst("div.Description > p")!!.text().replace(descRegex, "")
        
        // Temporarily disabled due to build failure on deprecated toRatingInt()
        /* val ratingInt = try {
            @Suppress("DEPRECATION")
            document.selectFirst("div.Vote > div.post-ratings > span")?.text()?.toRatingInt()
        } catch (e: Exception) {
            null
        } */
        
        val year = document.selectFirst("span.Date")?.text()
        
        val duration = try {
            document.selectFirst("span.Time")!!.text()
        } catch (e: Exception) {
            null
        }
        val postercss = document.selectFirst("head").toString()
        val posterRegex =
            Regex("(\"og:image\" content=\"https://seriesflix.video/wp-content/uploads/(\\d+)/(\\d+)/?.*.jpg)")
        val poster = try {
            posterRegex.findAll(postercss).map {
                it.value.replace("\"og:image\" content=\"", "")
            }.toList().first()
        } catch (e: Exception) {
            document.select(".TPostBg").attr("src")
        }

        if (type == TvType.TvSeries) {
            val list = ArrayList<Pair<Int, String>>()

            document.select("main > section.SeasonBx > div > div.Title > a").forEach { element ->
                val season = element.selectFirst("> span")?.text()?.toIntOrNull()
                val href = element.attr("href")
                if (season != null && season > 0 && !href.isNullOrBlank()) {
                    list.add(Pair(season, fixUrl(href)))
                }
            }
            if (list.isEmpty()) throw ErrorLoadingException("No Seasons Found")

            val episodeList = ArrayList<Episode>()

            for (item in list) {
                val (seasonInt, seasonUrl) = item
                val seasonDocument = app.get(seasonUrl).document
                val episodes = seasonDocument.select("table > tbody > tr")
                if (episodes.isNotEmpty()) {
                    episodes.forEach { episode ->
                        val epNum = episode.selectFirst("> td > span.Num")?.text()?.toIntOrNull()
                        val epthumb = episode.selectFirst("img")?.attr("src")
                        val aName = episode.selectFirst("> td.MvTbTtl > a")
                        val name = aName!!.text()
                        val href = aName.attr("href")
                        episodeList.add(
                            newEpisode(href) {
                                this.name = name
                                this.season = seasonInt
                                this.episode = epNum
                                this.posterUrl = fixUrlNull(epthumb)
                            }
                        )
                    }
                }
            }
            return newTvSeriesLoadResponse(title, url, type, episodeList) {
                this.posterUrl = fixUrlNull(poster)
                this.year = year?.toIntOrNull()
                this.plot = descipt
            }
        } else {
            return newMovieLoadResponse(title, url, type, url) {
                posterUrl = fixUrlNull(poster)
                this.year = year?.toIntOrNull()
                this.plot = descipt
                addDuration(duration)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val buttons = app.get(data).document.select("li div.Button.sgty")
        for (it in buttons) {
            val encodedlink = it.attr("data-url")
            val decodelink = base64Decode(encodedlink)
            if (decodelink.isNotBlank()) {
                loadExtractor(decodelink, data, subtitleCallback, callback)
            }
        }

        return true
    }
}
