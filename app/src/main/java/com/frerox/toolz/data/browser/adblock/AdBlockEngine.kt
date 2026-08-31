/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.browser.adblock
import com.frerox.toolz.data.browser.AdBlockList
import javax.inject.Inject
import javax.inject.Singleton
@Singleton
class AdBlockEngine @Inject constructor(){
    fun isBlocked(url:String):Boolean = AdBlockList.isBlocked(url)
    fun totalCount():Int = AdBlockList.totalCount()
    fun updateCustomLists(blocked:Set<String>, allowed:Set<String>){ AdBlockList.updateCustomLists(blocked, allowed) }
    fun updateImportedList(rules:Set<String>){ AdBlockList.updateImportedList(rules) }
    fun refreshIndex(){ AdBlockList.refreshIndex() }
}
