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
class BraveEngine @Inject constructor(private val fetcher: JsoupFetcher): SearchEngine{
    override val id=EngineId.BRAVE; override val displayName="Brave"
    override fun buildSearchUrl(request: SearchRequest): List<String>{
        val enc=java.net.URLEncoder.encode(request.query,"UTF-8")
        val safe=SafeSearchMapper.queryParam(id, request.safeSearch)
        val offset=if(request.offset>0) "&offset=${request.offset}" else ""
        return listOf("https://search.brave.com/search?q=$enc&source=web$safe$offset")
    }
    override fun parse(html:String, baseUrl:String, request:SearchRequest): List<SearchResult> = parseDocument(org.jsoup.Jsoup.parse(html, baseUrl), request)
    fun parseDocument(doc: Document, request: SearchRequest): List<SearchResult>{
        val results=mutableListOf<SearchResult>()
        val snippets=doc.select("div.snippet[data-type=web]")
        snippets.forEachIndexed{ rank, el->
            val linkEl=el.select("a[href]").firstOrNull{ it.attr("href").startsWith("http") && !it.attr("href").contains("search.brave.com")} ?: return@forEachIndexed
            val cleanUrl=linkEl.attr("href")
            val titleEl=linkEl.select("[class*=title]").firstOrNull()
            val title=(titleEl?.text() ?: linkEl.attr("title").ifBlank{ linkEl.text() }).trim()
            if(title.isBlank()) return@forEachIndexed
            val snippetEl=el.select(".generic-snippet, [class*=description]").firstOrNull()
            val (date, cleanSnippet)=fetcher.extractDateFromSnippet(snippetEl?.text()?.trim()?:"")
            val displayUrl=el.select("[class*=snippet-url]").firstOrNull()?.text()?.trim()?.ifBlank{null} ?: fetcher.safeHost(cleanUrl)
            results+=SearchResult(title, cleanSnippet, cleanUrl, displayUrl,"Brave", date, null, null, rank)
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
