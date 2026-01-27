package com.kurostream.extensions.lacartoons

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document
import java.util.Collections

class LACartoonsProvider:MainAPI() {
    override var mainUrl = "https://www.lacartoons.com"
    override var name = "LACartoons"
    override var lang = "es"
    override val hasMainPage = true
    override val hasChromecastSupport = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Cartoon,
        TvType.TvSeries
    )

    private fun Document.toSearchResult():List<SearchResponse>{
        return this.select(".categorias .conjuntos-series a").mapNotNull {
            val title = it.selectFirst("p.nombre-serie")?.text() ?: return@mapNotNull null
            val href = fixUrl(it.attr("href"))
            val img = fixUrl(it.selectFirst("img")?.attr("src") ?: "")
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = img
            }
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val items = ArrayList<HomePageList>()
        val soup = app.get(mainUrl).document
        val home = soup.toSearchResult()
        items.add(HomePageList("Series", home))
        return newHomePageResponse(items)
    }
    override suspend fun search(query: String): List<SearchResponse> {
        val doc = app.get("$mainUrl/?utf8=✓&Titulo=$query").document
        return doc.toSearchResult()
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document

        val title = doc.selectFirst("h2.text-center")?.text() ?: ""
        val description = doc.selectFirst(".informacion-serie-seccion p:contains(Reseña)")?.text()?.substringAfter("Reseña:")?.trim()
        val poster = doc.selectFirst(".imagen-serie img")?.attr("src")
        val backposter = doc.selectFirst("img.fondo-serie-seccion")?.attr("src")
        val episodes = doc.select("ul.listas-de-episodion li").mapNotNull {
            val regexep = Regex("Capitulo.(\\d+)|Capitulo.(\\d+)\\-")
            val href = it.selectFirst("a")?.attr("href") ?: return@mapNotNull null
            val name = it.selectFirst("a")?.text()?.replace(regexep, "")?.replace("-","")
            val seasonnum = href.substringAfter("t=")
            val epnum = regexep.find(name.toString())?.destructured?.component1()
            newEpisode(fixUrl(href)) {
                this.name = name
                this.season = seasonnum.toIntOrNull()
                this.episode = epnum?.toIntOrNull()
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.Cartoon, episodes){
            this.posterUrl = fixUrl(poster ?: "")
            this.backgroundPosterUrl = fixUrl(backposter ?: "")
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val capturedLinks = Collections.synchronizedList(ArrayList<ExtractorLink>())
        
        val res = app.get(data).document
        val elements = res.select(".serie-video-informacion iframe")
        for (it in elements) {
            val link = it.attr("src")?.replace("https://short.ink/","https://abysscdn.com/?v=")
            if (link != null) {
                loadExtractor(link, data, subtitleCallback) { l -> capturedLinks.add(l) }
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
