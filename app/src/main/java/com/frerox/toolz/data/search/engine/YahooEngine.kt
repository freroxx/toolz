/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine
import com.frerox.toolz.data.search.SearchResult
import com.frerox.toolz.data.search.pagination.OffsetBasedPagination
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class YahooEngine @Inject constructor(private val fetcher: JsoupFetcher): SearchEngine{
    override val id=EngineId.YAHOO; override val displayName="Yahoo"
    override fun buildSearchUrl(request: SearchRequest): List<String>{
        val enc=java.net.URLEncoder.encode(request.query,"UTF-8")
        val offset=if(request.offset>0) "&b=${request.offset+1}" else ""
        return listOf("https://search.yahoo.com/search?p=$enc$offset")
    }
    override fun parse(html:String, baseUrl:String, request:SearchRequest): List<SearchResult>{
        val doc=org.jsoup.Jsoup.parse(html, baseUrl)
        val results=mutableListOf<SearchResult>()
        var rank=0
        for(h3 in doc.select("h3")){
            val a = h3.parent()?.takeIf{ it.tagName().equals("a", true)} ?: h3.select("a").firstOrNull() ?: continue
            val raw=a.attr("href")
            if(raw.isBlank() || !raw.startsWith("http")) continue
            val cleanUrl=if(raw.contains("RU=")){
                try{ java.net.URLDecoder.decode(raw.substringAfter("RU=").substringBefore("/RK=").substringBefore("&"), StandardCharsets.UTF_8.name()) } catch(_:Exception){raw}
            } else raw
            if(cleanUrl.contains("yahoo.com/search")) continue
            val title=h3.text().trim().ifBlank{continue}
            val parent=h3.parents().firstOrNull{ it.tagName().equals("li", true) || it.hasClass("algo") }
            val desc=parent?.select(".compText, .fz-m, p")?.firstOrNull()?.text()?.trim()?:""
            val (date, snippet)=fetcher.extractDateFromSnippet(desc)
            results+=SearchResult(title, snippet, cleanUrl, fetcher.safeHost(cleanUrl),"Yahoo", date, null, null, rank++)
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
