/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.search.engine
import com.frerox.toolz.data.search.SearchResult
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class MetaMerger @Inject constructor(){
    fun merge(resultsByEngine: Map<String, List<SearchResult>>): List<SearchResult>{
        val urlToAppearances = mutableMapOf<String, MutableList<Pair<String, Int>>>()
        for((eng,list) in resultsByEngine){ list.forEachIndexed{ rank,r-> val canonical=when(eng.uppercase()){"DUCKDUCKGO"->"DuckDuckGo";"BRAVE"->"Brave";"BING"->"Bing";"YAHOO"->"Yahoo"; else->eng.lowercase().replaceFirstChar{it.uppercase()}}; urlToAppearances.getOrPut(r.url){ mutableListOf() }.add(canonical to rank) } }
        data class Scored(val result: SearchResult, val score: Double)
        return resultsByEngine.values.flatten().distinctBy{ canonicalUrl(it.url)}.map{ r->
            val appearances=urlToAppearances[r.url] ?: emptyList()
            val engineNames=appearances.map{ it.first}.distinct()
            val rankScore=appearances.sumOf{ (_,rank)-> 1.0/(rank+1)}
            val consensus=if(appearances.size>=2) 1.5 else 1.0
            val snippet=if(r.snippet.isNotBlank()) 1.1 else 1.0
            val freshness=if(r.date!=null) 1.05 else 1.0
            Scored(r.copy(engines=engineNames), rankScore*consensus*snippet*freshness)
        }.sortedByDescending{ it.score}.map{ it.result}
    }
    fun canonicalUrl(url: String): String = try{ var u=url.trim().removeSuffix("/").lowercase(); u=u.replace(Regex("[?&]utm_[^&]+"),"").replace("?&","?").trimEnd('?','&'); u } catch(_:Exception){ url }
}
