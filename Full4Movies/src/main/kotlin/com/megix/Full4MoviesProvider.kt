package com.megix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.network.CloudflareKiller


class Full4MoviesProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://www.full4movie.wine"
    override var name = "Full4Movies"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    private val cfInterceptor = CloudflareKiller()
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "$mainUrl/page/%d/" to "Home",
        "$mainUrl/category/web-series/page/%d/" to "Web Series",
        "$mainUrl/category/south-indian-hindi-dubbed-movies/page/%d/" to "South Hindi Dubbed",
        "$mainUrl/category/bollywood-movies-download/page/%d/" to "Bollywood Movies",
        "$mainUrl/category/hollywood-movies/page/%d/" to "Hollywood Movies",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data.format(page), interceptor = cfInterceptor).document
        val home = document.select("div.article-content-col").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("img")?.attr("title") ?: return null
        val href = fixUrl(this.selectFirst("a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..6) {
            val document = app.get("$mainUrl/page/$i/?s=$query").document

            val results = document.select("div.article-content-col").mapNotNull { it.toSearchResult() }

            searchResponse.addAll(results)

            if (results.isEmpty()) break
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document
        val title = document.selectFirst("h1.title")?.text() ?: return null
        val posterUrl = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))


        val tvType =  if(document.selectFirst("meta[property=article:section]")?.attr("content") == "Web series") TvType.TvSeries else TvType.Movie

        return if (tvType == TvType.TvSeries) {

            val regex = Regex("""<a[^>]*href="([^"]*)"[^>]*>(?:WCH|Watch)<\/a>""")
            val urls = regex.findAll(document.html()).map { it.groupValues[1] }.toList()

            val episodes = urls.mapNotNull {
                Episode(it, "Episode ${urls.indexOf(it) + 1}")
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = posterUrl
            }
        }
        else {
            val movieUrl = Regex("""<a\s+class="myButton"\s+href="([^"]+)".*?>Watch Online 1<\/a>""").find(document.html())?.groupValues?.get(1) ?: ""
            return newMovieLoadResponse(title, url, TvType.Movie, movieUrl) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val link = if (data.contains("full4u")) {
            val document = app.get(data).document
            val href = document.select("iframe").attr("src")
            when {
                href.contains("watchx.top") -> href.replace("watchx.top", "boltx.stream")
                href.contains("bestx.stream") -> href.replace("bestx.stream", "boltx.stream")
                href.contains("chillx.top") -> href.replace("chillx.top", "boltx.stream")
                else -> href
            }
        } else {
            when {
                data.contains("watchx.top") -> data.replace("watchx.top", "boltx.stream")
                data.contains("bestx.stream") -> data.replace("bestx.stream", "boltx.stream")
                data.contains("chillx.top") -> data.replace("chillx.top", "boltx.stream")
                else -> data
            }
        }

        loadExtractor(link, subtitleCallback, callback)


        return true
    }

}