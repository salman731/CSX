package com.megix

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.network.CloudflareKiller

class RogmoviesProvider : VegaMoviesProvider() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://rogmovies.fun"
    override var name = "Rogmovies"
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
        "$mainUrl/category/web-series/netflix/page/%d/" to "Netflix",
        "$mainUrl/category/web-series/disney-plus-hotstar/page/%d/" to "Disney Plus Hotstar",
        "$mainUrl/category/web-series/amazon-prime-video/page/%d/" to "Amazon Prime",
        "$mainUrl/category/web-series/mx-original/page/%d/" to "MX Original",
        "$mainUrl/category/web-series/jio-studios/page/%d/" to "Jio Cinema",
        "$mainUrl/category/web-series/sonyliv/page/%d/" to "Sony Liv",
        "$mainUrl/category/web-series/zee5-originals/page/%d/" to "Zee5",
        "$mainUrl/category/web-series/alt-balaji-web-series/page/%d/" to "ALT Balaji",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data.format(page), interceptor = cfInterceptor).document
        val home = document.select("a.blog-img").mapNotNull {
            it.toSearchResult()
        }
        return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.attr("title").replace("Download ", "")
        val href = this.attr("href")
        val posterUrl = this.selectFirst("img")?.attr("data-src").toString()
        val quality = if(title.contains("HDCAMRip", ignoreCase = true) || title.contains("CAMRip", ignoreCase = true)) {
            SearchQuality.CamRip
        }
        else {
            null
        }

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = quality
        }
    }
    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        for (i in 1..25) {
            val document = app.get("$mainUrl/page/$i/?s=$query", interceptor = cfInterceptor).document
            val results = document.select("a.blog-img").mapNotNull { it.toSearchResult() }
            if (results.isEmpty()) {
                break
            }
            searchResponse.addAll(results)
        }
        return searchResponse
    }

}
