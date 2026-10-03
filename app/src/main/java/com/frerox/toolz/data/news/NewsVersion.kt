package com.frerox.toolz.data.news

object NewsVersion {
    private fun parts(v: String): List<Int> {
        val core = v.trim().substringBefore("-").substringBefore("+")
        return core.split(".").map { it.toIntOrNull() ?: 0 }
    }

    fun compare(a: String, b: String): Int {
        val pa = parts(a)
        val pb = parts(b)
        for (i in 0 until maxOf(pa.size, pb.size, 3)) {
            val x = pa.getOrElse(i) { 0 }
            val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    fun isEligible(
        appVersion: String,
        minAppVersion: String?,
        maxAppVersion: String?,
        onlyVersions: List<String>,
        excludedVersions: List<String>
    ): Boolean {
        val av = appVersion.trim()
        if (minAppVersion != null && compare(av, minAppVersion) < 0) return false
        if (maxAppVersion != null && compare(av, maxAppVersion) > 0) return false
        if (onlyVersions.isNotEmpty() && av !in onlyVersions) return false
        if (av in excludedVersions) return false
        return true
    }
}
