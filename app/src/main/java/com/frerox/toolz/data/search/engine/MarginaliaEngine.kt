/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine
import com.frerox.toolz.data.search.SearchResult
import com.frerox.toolz.data.search.pagination.OffsetBasedPagination
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class MarginaliaEngine @Inject constructor(private val fetcher: JsoupFetcher): SearchEngine{
    override val id=EngineId.MARGINALIA; override val displayName="Marginalia"
    override fun buildSearchUrl(request: SearchRequest): List<String>{
        val enc=java.net.URLEncoder.encode(request.query,"UTF-8")
        val page=request.offset/10+1
        return listOf("https://search.marginalia.nu/search?query=$enc&page=$page")
    }
    override fun parse(html:String, baseUrl:String, request:SearchRequest): List<SearchResult>{
        val doc=org.jsoup.Jsoup.parse(html, baseUrl)
        val results=mutableListOf<SearchResult>()
        doc.select(".search-result, article").forEachIndexed{ rank, el->
            val linkEl=el.select("a[href]").firstOrNull() ?: return@forEachIndexed
            val url=linkEl.attr("href").let{ if(it.startsWith("/")) "https://search.marginalia.nu$it" else it }.takeIf{ it.startsWith("http")} ?: return@forEachIndexed
            val a=el.select("a[href] h2, h2 a").firstOrNull() ?: linkEl
            val title=a.text().trim().ifBlank{ fetcher.safeHost(url)}
            val snippet=el.select("p, .description").firstOrNull()?.text()?.trim()?:""
            val (date, clean)=fetcher.extractDateFromSnippet(snippet)
            results+=SearchResult(title, clean, url, fetcher.safeHost(url),"Marginalia", date, null, null, rank)
        }
        return results
    }
    override suspend fun search(request: SearchRequest): SearchResponse{
        for(url in buildSearchUrl(request)){
            val doc=fetcher.fetch(url) ?: continue
            val p=parse(doc.outerHtml(), url, request)
            if(p.isNotEmpty()) return SearchResponse(p, OffsetBasedPagination.nextOffset(request.offset, request.pageSize, p.size, true), true, id)
        }
        return SearchResponse(emptyList(),null,false,id)
    }
}
