/*
 * Copyright (C) 2026 Toolz Contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.frerox.toolz.data.update

/**
 * Pure identity rules for a downloaded APK. The installer never opens a
 * file until it matches the installed package, is actually newer and was
 * signed with the same certificate(s) as the running app.
 *
 * Kept Android-free so the rules are unit-tested directly.
 */
object ApkIdentity {

    enum class Reason {
        NotAnApk,
        WrongPackage,
        NotNewer,
        SignerUnavailable,
        SignerMismatch,
    }

    /**
     * @return null when [apk] is a valid update, otherwise the first
     *         failed rule.
     */
    fun evaluate(
        installedPackage: String?,
        installedVersionCode: Long,
        installedSigners: Set<String>,
        apkPackage: String?,
        apkVersionCode: Long,
        apkSigners: Set<String>,
    ): Reason? {
        if (installedPackage.isNullOrBlank() || apkPackage.isNullOrBlank()) {
            return Reason.NotAnApk
        }
        if (apkPackage != installedPackage) return Reason.WrongPackage
        if (apkVersionCode <= installedVersionCode) return Reason.NotNewer
        if (installedSigners.isEmpty() || apkSigners.isEmpty()) {
            return Reason.SignerUnavailable
        }
        if (installedSigners.intersect(apkSigners).isEmpty()) return Reason.SignerMismatch
        return null
    }
}
