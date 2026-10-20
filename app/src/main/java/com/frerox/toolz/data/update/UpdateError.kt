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

import androidx.annotation.StringRes
import com.frerox.toolz.R
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * Every failure the update flow can surface. [messageRes] points at plain,
 * translated copy for the updater screen; [detail] is diagnostics-only and
 * never shown to the user.
 */
sealed interface UpdateError {
    @get:StringRes
    val messageRes: Int
    val retryable: Boolean
    val detail: String? get() = null

    data object Offline : UpdateError {
        override val messageRes = R.string.st_Update_ErrorOffline
        override val retryable = true
    }

    data object Timeout : UpdateError {
        override val messageRes = R.string.st_Update_ErrorTimeout
        override val retryable = true
    }

    data class RateLimited(val retryAfterSeconds: Long? = null) : UpdateError {
        override val messageRes = R.string.st_Update_ErrorBusy
        override val retryable = true
    }

    data class Http(val code: Int) : UpdateError {
        override val messageRes = R.string.st_Update_ErrorServer
        override val detail = "HTTP $code"
        override val retryable = code >= 500 || code == 408 || code == 416
    }

    data object NoCompatibleBuild : UpdateError {
        override val messageRes = R.string.st_Update_ErrorNoBuild
        override val retryable = false
    }

    data class Integrity(override val detail: String? = null) : UpdateError {
        override val messageRes = R.string.st_Update_ErrorChecksum
        override val retryable = true
    }

    data class Verification(
        val reason: ApkIdentity.Reason? = null,
        @StringRes val overrideMessageRes: Int? = null,
        override val detail: String? = null,
    ) : UpdateError {
        override val messageRes: Int
            get() = overrideMessageRes ?: when (reason) {
                ApkIdentity.Reason.NotAnApk -> R.string.st_Update_ErrorNotApk
                ApkIdentity.Reason.WrongPackage -> R.string.st_Update_ErrorWrongPackage
                ApkIdentity.Reason.NotNewer -> R.string.st_Update_ErrorNotNewer
                ApkIdentity.Reason.SignerUnavailable -> R.string.st_Update_ErrorSignerUnavailable
                ApkIdentity.Reason.SignerMismatch -> R.string.st_Update_ErrorSignerMismatch
                null -> R.string.st_Update_ErrorInvalidFile
            }
        override val retryable = false
    }

    data object Storage : UpdateError {
        override val messageRes = R.string.st_Update_ErrorStorage
        override val retryable = false
    }

    data class InstallFailed(
        override val detail: String? = null,
        @StringRes val overrideMessageRes: Int? = null,
    ) : UpdateError {
        override val messageRes: Int
            get() = overrideMessageRes ?: R.string.st_Update_ErrorInstall
        override val retryable = true
    }

    data class Unknown(
        override val detail: String? = null,
        @StringRes val overrideMessageRes: Int? = null,
    ) : UpdateError {
        override val messageRes: Int
            get() = overrideMessageRes ?: R.string.st_Update_ErrorUnknown
        override val retryable = true
    }

    companion object {
        fun fromThrowable(t: Throwable): UpdateError = when (t) {
            is SocketTimeoutException -> Timeout
            is InterruptedIOException -> Timeout
            is UnknownHostException -> Offline
            is ConnectException -> Offline
            is NoRouteToHostException -> Offline
            is SSLException -> Offline
            is IOException -> Offline
            else -> Unknown(t.message)
        }

        /** Maps an HTTP failure to the right category. */
        fun fromHttp(code: Int): UpdateError = when (code) {
            403, 429 -> RateLimited()
            else -> Http(code)
        }
    }
}
