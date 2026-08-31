/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine
import com.frerox.toolz.data.search.SearchResult
import com.frerox.toolz.data.search.safesearch.SafeSearchMapper
import com.frerox.toolz.data.search.pagination.OffsetBasedPagination
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class QwantEngine @Inject constructor(private val fetcher: JsoupFetcher): SearchEngine{
    override val id=EngineId.QWANT; override val displayName="Qwant"
    override fun buildSearchUrl(request: SearchRequest): List<String>{
        val enc=java.net.URLEncoder.encode(request.query,"UTF-8")
        val safe=SafeSearchMapper.queryParam(id, request.safeSearch)
        val offset=if(request.offset>0) "&offset=${request.offset}" else ""
        return listOf("https://www.qwant.com/?q=$enc&t=web$offset$safe")
    }
    override fun parse(html:String, baseUrl:String, request:SearchRequest): List<SearchResult>{
        val doc=org.jsoup.Jsoup.parse(html, baseUrl)
        val results=mutableListOf<SearchResult>()
        doc.select("[data-testid=result], .result, article").forEachIndexed{ rank, el->
            val linkEl=el.select("a[href]").firstOrNull() ?: return@forEachIndexed
            val cleanUrl=linkEl.attr("href").takeIf{it.startsWith("http")} ?: return@forEachIndexed
            if(cleanUrl.contains("qwant.com")) return@forEachIndexed
            val titleEl=el.select("a[href] h2, a[href] h3").firstOrNull() ?: linkEl
            val snippetEl=el.select("p, .result-snippet").firstOrNull()
            val (date, snippet)=fetcher.extractDateFromSnippet(snippetEl?.text()?.trim()?:"")
            results+=SearchResult(titleEl.text().trim().ifBlank{ fetcher.safeHost(cleanUrl)}, snippet, cleanUrl, fetcher.safeHost(cleanUrl),"Qwant", date, null, null, rank)
        }
        return results
    }
    override suspend fun search(request: SearchRequest): SearchResponse{
        for(url in buildSearchUrl(request)){
            val doc=fetcher.fetch(url) ?: continue
            val parsed=parse(doc.outerHtml(), url, request)
            if(parsed.isNotEmpty()) return SearchResponse(parsed, OffsetBasedPagination.nextOffset(request.offset, request.pageSize, parsed.size, true), true, id)
        }
        return SearchResponse(emptyList(),null,false,id)
    }
}
