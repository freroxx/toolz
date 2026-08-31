/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.browser.autofill
import org.json.JSONObject
object AutofillJsBridge{
    fun fillJs(user:String, pass:String):String{
        val u=JSONObject.quote(user); val p=JSONObject.quote(pass)
        return """(function(u,p){ function fill(selectors, value){ for(var i=0;i<selectors.length;i++){ var els=document.querySelectorAll(selectors[i]); for(var j=0;j<els.length;j++){ var el=els[j]; if(el && el.offsetParent!==null && !el.disabled){ el.focus(); el.value=value; el.dispatchEvent(new Event('input',{bubbles:true})); el.dispatchEvent(new Event('change',{bubbles:true})); return true; } } } return false;} fill(['input[type="email"]','input[name*="email"]','input[name="identifier"]','input[name*="user"]','input[name*="login"]','input[id*="user"]','input[id*="email"]','input[autocomplete*="username"]','input[autocomplete*="email"]','input[type="text"]'], u); fill(['input[type="password"]','input[name*="pass"]','input[autocomplete*="password"]','input[autocomplete*="current-password"]','input[autocomplete*="new-password"]'], p); })($u,$p);"""
    }
}
