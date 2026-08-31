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
class BingEngine @Inject constructor(private val fetcher: JsoupFetcher): SearchEngine{
    override val id=EngineId.BING; override val displayName="Bing"
    override fun buildSearchUrl(request: SearchRequest): List<String>{
        val enc=java.net.URLEncoder.encode(request.query,"UTF-8")
        val safe=SafeSearchMapper.queryParam(id, request.safeSearch)
        val offset=if(request.offset>0) "&first=${request.offset}" else ""
        return listOf("https://www.bing.com/search?q=$enc$offset$safe")
    }
    override fun parse(html:String, baseUrl:String, request:SearchRequest): List<SearchResult> = parseDocument(org.jsoup.Jsoup.parse(html, baseUrl), request)
    fun parseDocument(doc: Document, request: SearchRequest): List<SearchResult>{
        val results=mutableListOf<SearchResult>()
        doc.select("li.b_algo, div.b_algo").forEachIndexed{ rank, el->
            val titleEl=el.select("h2 a, h3 a").firstOrNull() ?: return@forEachIndexed
            val snippetEl=el.select("p, div.b_caption p").firstOrNull()
            val cleanUrl=titleEl.attr("href").takeIf{it.startsWith("http")} ?: return@forEachIndexed
            val (date, cleanSnippet)=fetcher.extractDateFromSnippet(snippetEl?.text()?.trim()?:"")
            val breadcrumb=el.select(".b_attribution cite, cite").firstOrNull()?.text()?.trim()?.ifBlank{null}
            results+=SearchResult(titleEl.text().trim().ifBlank{ fetcher.safeHost(cleanUrl)}, cleanSnippet, cleanUrl, breadcrumb ?: fetcher.safeHost(cleanUrl),"Bing", date, breadcrumb, null, rank)
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
