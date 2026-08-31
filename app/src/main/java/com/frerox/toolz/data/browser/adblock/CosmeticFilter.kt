/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.browser.adblock
object CosmeticFilter{
    const val JS = """(function(){ if(window.__adblockInjected) return; window.__adblockInjected=true; var adSelectors=['ins.adsbygoogle','iframe[id^="google_ads_iframe"]','div[id*="google_ads_"]','.ad-container','.ad-wrapper','.ad-banner','.OUTBRAIN','.taboola','[id*="taboola-"]','.revcontent','.mgid','#onetrust-banner-sdk','#CybotCookiebotDialog']; function hideAds(){ adSelectors.forEach(function(sel){ try{ document.querySelectorAll(sel).forEach(function(el){ var tag=el.tagName?el.tagName.toLowerCase():''; if(tag==='body'||tag==='html'||tag==='main'||tag==='article'||tag==='header'||tag==='nav') return; if(el.style.display!=='none') el.style.setProperty('display','none','important');});}catch(e){}});} hideAds(); var observer=new MutationObserver(function(){ hideAds();}); if(document.documentElement) observer.observe(document.documentElement,{childList:true, subtree:true}); })();"""
}
