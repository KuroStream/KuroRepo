package com.kurostream.extensions.animeflv

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import java.util.*

data class SearchObject(
    @JsonProperty("id") val id: String? = null,
    @JsonProperty("title") val title: String? = null,
    @JsonProperty("type") val type: String? = null,
    @JsonProperty("last_id") val lastId: String? = null,
    @JsonProperty("slug") val slug: String? = null
)

data class EpsInfo (
    @JsonProperty("number" ) var number : String? = null,
    @JsonProperty("title"  ) var title  : String? = null,
    @JsonProperty("image"  ) var image  : String? = null
)

data class MainServers(
        @JsonProperty("SUB")
        val sub: List<Sub>? = null,
)

data class Sub(
        val code: String? = null,
)

class AnimeflvProvider : MainAPI() {
    companion object {
        fun getType(t: String): TvType {
            return if (t.contains("OVA") || t.contains("Especial")) TvType.OVA
            else if (t.contains("Película")) TvType.AnimeMovie
            else TvType.Anime
        }

        fun getDubStatus(title: String): DubStatus {
            return if (title.contains("Latino") || title.contains("Castellano"))
                DubStatus.Dubbed
            else DubStatus.Subbed
        }
    }

    private val mapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .build()

    override var mainUrl = "https://www3.animeflv.net"
    override var name = "Animeflv.net"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val hasQuickSearch = true
    override val supportedTypes = setOf(
        TvType.AnimeMovie,
        TvType.OVA,
        TvType.Anime,
    )

    override suspend fun getMainPage(page: Int, request : MainPageRequest): HomePageResponse {
        val urls = listOf(
            Pair("$mainUrl/browse?type[]=movie&order=updated", "Películas"),
            Pair("$mainUrl/browse?status[]=2&order=default", "Animes"),
            Pair("$mainUrl/browse?status[]=1&order=rating", "En emision"),
        )
        val items = ArrayList<HomePageList>()
        
        val latestDoc = app.get(mainUrl).document
        val latestEpsElements = latestDoc.select("main.Main ul.ListEpisodios li")
        val latestEps = ArrayList<SearchResponse>()
        for (it in latestEpsElements) {
            val title = it.selectFirst("strong.Title")?.text() ?: continue
            val poster = it.selectFirst("span img")?.attr("src") ?: continue
            val epRegex = Regex("(-(\\d+)\$)")
            val url = it.selectFirst("a")?.attr("href")?.replace(epRegex, "")
                ?.replace("ver/", "anime/") ?: continue
            val epNum =
                it.selectFirst("span.Capi")?.text()?.replace("Episodio ", "")?.toIntOrNull()
            latestEps.add(newAnimeSearchResponse(title, url) {
                this.posterUrl = fixUrl(poster)
                addDubStatus(getDubStatus(title), epNum)
            })
        }
        
        items.add(HomePageList("Últimos episodios", latestEps, true))

        for (item in urls) {
            val (url, listName) = item
            val doc = app.get(url).document
            val homeElements = doc.select("ul.ListAnimes li article")
            val home = ArrayList<SearchResponse>()
            for (it in homeElements) {
                val title = it.selectFirst("h3.Title")?.text() ?: continue
                val poster = it.selectFirst("figure img")?.attr("src") ?: continue
                val href = it.selectFirst("a")?.attr("href") ?: continue
                home.add(newAnimeSearchResponse(title, fixUrl(href)) {
                    this.posterUrl = fixUrl(poster)
                    addDubStatus(getDubStatus(title))
                })
            }

            items.add(HomePageList(listName, home))
        }
        
        return newHomePageResponse(items)
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> {
        val response = app.post(
            "https://www3.animeflv.net/api/animes/search",
            data = mapOf(Pair("value", query))
        ).text
        val searchObjects = mapper.readValue(response, Array<SearchObject>::class.java)
        val result = ArrayList<SearchResponse>()
        for (obj in searchObjects) {
            val title = obj.title ?: continue
            val slug = obj.slug ?: continue
            val id = obj.id ?: continue
            val href = "$mainUrl/anime/$slug"
            val image = "$mainUrl/uploads/animes/covers/$id.jpg"
            result.add(newAnimeSearchResponse(title, href) {
                this.posterUrl = fixUrl(image)
                addDubStatus(getDubStatus(title))
            })
        }
        return result
    }
    
    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/browse?q=$query").document
        val elements = doc.select("ul.ListAnimes article")
        val result = ArrayList<SearchResponse>()
        for (ll in elements) {
            val title = ll.selectFirst("h3")?.text() ?: ""
            val image = ll.selectFirst("figure img")?.attr("src") ?: ""
            val href = ll.selectFirst("a")?.attr("href") ?: ""
            result.add(newAnimeSearchResponse(title, href){
                this.posterUrl = image
                addDubStatus(getDubStatus(title))
            })
        }
        return result
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val epList = ArrayList<Episode>()
        val title = doc.selectFirst("h1.Title")?.text() ?: ""
        val poster = doc.selectFirst("div.AnimeCover div.Image figure img")?.attr("src") ?: ""
        val description = doc.selectFirst("div.Description p")?.text()
        val type = doc.selectFirst("span.Type")?.text() ?: ""
        val status = when (doc.selectFirst("p.AnmStts span")?.text()) {
            "En emision" -> ShowStatus.Ongoing
            "Finalizado" -> ShowStatus.Completed
            else -> null
        }
        
        val genreElements = doc.select("nav.Nvgnrs a")
        val genre = ArrayList<String>()
        for (it in genreElements) {
            genre.add(it.text().trim())
        }
        
        val scripts = doc.select("script")
        for (script in scripts) {
            val scriptData = script.data()
            if (scriptData.contains("var episodes = [")) {
                val dataStr = scriptData.substringAfter("var episodes = [").substringBefore("];")
                val parts = dataStr.split("],")
                for (part in parts) {
                    val epNum = part.removePrefix("[").substringBefore(",")
                    if (epNum.isNotBlank()) {
                        val link = url.replace("/anime/", "/ver/") + "-$epNum"
                        epList.add(
                            newEpisode(link) {
                                this.episode = epNum.toIntOrNull()
                            }
                        )
                    }
                }
            }
        }
        
        return newAnimeLoadResponse(title, url, getType(type)) {
            this.posterUrl = fixUrl(poster)
            addEpisodes(DubStatus.Subbed, epList.reversed())
            this.showStatus = status
            this.plot = description
            this.tags = genre
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val capturedLinks = Collections.synchronizedList(ArrayList<ExtractorLink>())

        val scriptElements = app.get(data).document.select("script")
        for (script in scriptElements) {
            val scriptData = script.data()
            if (scriptData.contains("var videos = {") || scriptData.contains("var anime_id =") || scriptData.contains("server")) {
                val serversRegex = Regex("var videos = (\\{\"SUB\":\\[\\{.*?\\}\\]\\});")
                val match = serversRegex.find(scriptData)
                val serversplain = match?.destructured?.component1() ?: ""
                if (serversplain.isNotBlank()) {
                    val json = mapper.readValue(serversplain, MainServers::class.java)
                    val subList = json.sub
                    if (subList != null) {
                        for (item in subList) {
                            val code = item.code ?: continue
                            loadExtractor(code, data, subtitleCallback) { link ->
                                capturedLinks.add(link)
                            }
                        }
                    }
                }
            }
        }
        
        val allowedLinks = ArrayList<ExtractorLink>()
        for (it in capturedLinks) {
            val lName = it.name
            if (!lName.contains("Castellano", true) && 
                !lName.contains("España", true) && 
                !lName.contains("Spain", true) &&
                !lName.contains("European Spanish", true) &&
                !lName.contains(" ES", true)) {
                allowedLinks.add(it)
            }
        }

        val hasLatino = allowedLinks.any { it.name.contains("Latino", true) || it.name.contains("LAT", true) }
        
        val finalLinks = if (hasLatino) {
             val filtered = ArrayList<ExtractorLink>()
             for (it in allowedLinks) {
                 if (it.name.contains("Latino", true) || it.name.contains("LAT", true)) filtered.add(it)
             }
             filtered
        } else {
            allowedLinks
        }
        
        for (link in finalLinks) {
            callback(link)
        }
        
        return true
    }
}
