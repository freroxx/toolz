/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine
import com.frerox.toolz.data.search.SearchResult
import com.frerox.toolz.data.search.safesearch.SafeSearchMapper
import com.frerox.toolz.data.search.pagination.OffsetBasedPagination
import org.jsoup.nodes.Document
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class DuckDuckGoEngine @Inject constructor(private val fetcher: JsoupFetcher): SearchEngine{
    override val id=EngineId.DUCKDUCKGO; override val displayName="DuckDuckGo"
    override fun buildSearchUrl(request: SearchRequest): List<String>{
        val enc=java.net.URLEncoder.encode(request.query,"UTF-8")
        val safe=SafeSearchMapper.queryParam(id, request.safeSearch)
        val region=if(request.region.isNotBlank()&&request.region!="wt-wt") "&kl=${request.region}" else ""
        val offset=OffsetBasedPagination.offsetParam(request.offset, id)
        return listOf("https://html.duckduckgo.com/html/?q=$enc$offset$safe$region","https://lite.duckduckgo.com/lite/?q=$enc$offset")
    }
    override fun parse(html:String, baseUrl:String, request:SearchRequest): List<SearchResult> = parseDocument(org.jsoup.Jsoup.parse(html, baseUrl), request)
    fun parseDocument(doc: Document, request: SearchRequest): List<SearchResult>{
        val results=mutableListOf<SearchResult>()
        val els=doc.select("#links .result").filter{ !it.hasClass("result--ad")&&!it.hasClass("result--more")&&it.select(".result__a").isNotEmpty() }
        els.forEachIndexed{ rank, el->
            val titleEl=el.select(".result__a").firstOrNull() ?: return@forEachIndexed
            val snippetEl=el.select(".result__snippet").firstOrNull()
            val urlEl=el.select(".result__url").firstOrNull()
            val cleanUrl=fetcher.cleanDuckDuckGoUrl(titleEl.attr("href"))
            if(cleanUrl.isBlank()||cleanUrl.startsWith("/")) return@forEachIndexed
            val (date, cleanSnippet)=fetcher.extractDateFromSnippet(snippetEl?.text()?.trim()?:"")
            results+=SearchResult(titleEl.text().trim(), cleanSnippet, cleanUrl, urlEl?.text()?.trim() ?: fetcher.safeHost(cleanUrl),"DuckDuckGo", date, urlEl?.text()?.trim(), null, rank)
        }
        return results
    }
    override suspend fun search(request: SearchRequest): SearchResponse{
        for(url in buildSearchUrl(request)){
            val doc=fetcher.fetch(url) ?: continue
            val parsed=parseDocument(doc, request)
            if(parsed.isNotEmpty()) return SearchResponse(parsed, OffsetBasedPagination.nextOffset(request.offset, request.pageSize, parsed.size, true), true, id)
        }
        return SearchResponse(emptyList(), null, false, id)
    }
}
