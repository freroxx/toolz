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
class MojeekEngine @Inject constructor(private val fetcher: JsoupFetcher): SearchEngine{
    override val id=EngineId.MOJEEK; override val displayName="Mojeek"
    override fun buildSearchUrl(request: SearchRequest): List<String>{
        val enc=java.net.URLEncoder.encode(request.query,"UTF-8")
        val offset=if(request.offset>0) "&s=${request.offset}" else ""
        val safe=SafeSearchMapper.queryParam(id, request.safeSearch)
        return listOf("https://www.mojeek.com/search?q=$enc$offset$safe")
    }
    override fun parse(html:String, baseUrl:String, request:SearchRequest): List<SearchResult>{
        val doc=org.jsoup.Jsoup.parse(html, baseUrl)
        val results=mutableListOf<SearchResult>()
        doc.select(".ob, li.ob, .result").forEachIndexed{ rank, el->
            val titleEl=el.select("a.title, h2 a, a.obTitle").firstOrNull() ?: return@forEachIndexed
            val cleanUrl=titleEl.attr("href").takeIf{it.startsWith("http")} ?: return@forEachIndexed
            val snippetEl=el.select("p.s, .s, .desc").firstOrNull()
            val (date, cleanSnippet)=fetcher.extractDateFromSnippet(snippetEl?.text()?.trim()?:"")
            results+=SearchResult(titleEl.text().trim(), cleanSnippet, cleanUrl, fetcher.safeHost(cleanUrl),"Mojeek", date, null, null, rank)
        }
        return results
    }
    override suspend fun search(request: SearchRequest): SearchResponse{
        for(url in buildSearchUrl(request)){
            val doc=fetcher.fetch(url) ?: continue
            val parsed=parse(doc.outerHtml(), url, request)
            if(parsed.isNotEmpty()) return SearchResponse(parsed, OffsetBasedPagination.nextOffset(request.offset, request.pageSize, parsed.size, true), true, id)
        }
        return SearchResponse(emptyList(), null, false, id)
    }
}
