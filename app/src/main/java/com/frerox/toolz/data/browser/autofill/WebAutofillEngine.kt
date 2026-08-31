/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */
package com.frerox.toolz.data.browser.autofill
object WebAutofillEngine{
    const val DETECTION_JS = """(function(){ function isVisible(el){ if(!el) return false; var s=window.getComputedStyle(el); return s.display!=='none' && s.visibility!=='hidden' && el.offsetWidth>0 && el.offsetHeight>0; } var inputs=Array.from(document.querySelectorAll('input')); var hasPass=inputs.some(el=> (el.type==='password' || el.name.toLowerCase().includes('pass') || el.id.toLowerCase().includes('pass') || (el.getAttribute('autocomplete')||'').includes('password')) && isVisible(el) && !el.disabled); var hasUser=inputs.some(el=> (el.type==='email' || el.type==='text' || el.type==='tel') && (el.name.toLowerCase().includes('user') || el.name.toLowerCase().includes('login') || el.name==='identifier' || el.id.toLowerCase().includes('user') || el.id.toLowerCase().includes('login') || (el.getAttribute('autocomplete')||'').includes('username') || (el.getAttribute('autocomplete')||'').includes('email')) && isVisible(el) && !el.disabled); var isSearch=!!document.querySelector('input[name="q"], input[name="s"], input[id*="search"]'); if(!window.autofillObserverSet){ const obs=new MutationObserver(()=>{ var newP=Array.from(document.querySelectorAll('input[type="password"]')).some(isVisible); if(newP && window.AndroidAutofill) window.AndroidAutofill.onAuthFieldsDetected();}); try{ obs.observe(document.body,{childList:true, subtree:true}); window.autofillObserverSet=true;}catch(e){} } return (hasPass || (hasUser && !isSearch)); })();"""
}
