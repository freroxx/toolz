/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.OtpType
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.exception.AuthErrorCode
import io.github.jan.supabase.auth.exception.AuthRestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.postgrest.postgrest
import com.frerox.toolz.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WhisperAuthManager @Inject constructor(
    private val supabase: SupabaseClient,
    // P2: shared hardened transport for the delete-account edge function.
    private val edgeFunctions: EdgeFunctionClient,
) {
    sealed interface EmailRegistrationResult {
        data object SignedIn : EmailRegistrationResult
        data class VerificationRequired(val email: String) : EmailRegistrationResult
    }
    val sessionStatus: Flow<SessionStatus>
        get() = supabase.auth.sessionStatus
    val isAuthenticated: Flow<Boolean?>
        get() = sessionStatus.map { status ->
            when (status) {
                is SessionStatus.Initializing -> null
                is SessionStatus.Authenticated -> true
                else -> false
            }
        }
    val isInitializing: Flow<Boolean>
        get() = sessionStatus.map { it is SessionStatus.Initializing }
    val currentUserId: String?
        get() = supabase.auth.currentUserOrNull()?.id
    val currentUserEmail: String?
        get() = supabase.auth.currentUserOrNull()?.email
    val currentUserMetadata: Map<String, Any?>
        get() = supabase.auth.currentUserOrNull()?.userMetadata ?: emptyMap()
    val isReady: Boolean
        get() = supabase.auth.sessionStatus.value !is SessionStatus.Initializing
    val isAnonymousTokenUser: Boolean
        get() = supabase.auth.currentUserOrNull()?.email?.endsWith("@whisper.toolz.app") == true

    val isCurrentEmailVerified: Boolean
        get() = supabase.auth.currentUserOrNull()?.emailConfirmedAt != null

    /**
     * Last legacy-migration prompt data seen this session (null when none).
     * Set when a pre-P1-15 legacy derivation opens an account; the UI reads it
     * for the "Legacy account found" warning banner (st_Whisper_Legacy_*).
     */
    @Volatile var pendingLegacyMigration: LegacyTokenLogin.MigrationNeeded? = null
        private set

    suspend fun deleteAccount(password: String? = null): Result<Unit> = runCatching {
        val user = supabase.auth.currentUserOrNull() ?: error("Not authenticated")
        val email = user.email ?: error("No account associated")
        val isTokenUser = email.endsWith("@whisper.toolz.app")

        if (!isTokenUser) {
            require(!password.isNullOrBlank()) { "Password is required to delete account." }
            // Re-authenticate to verify password before deletion
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
        }

        // Ask the edge function (service role, JWT-verified) to permanently delete the
        // GoTrue user. The caller wipes local data after this returns.
        // P0-4 FIX: Also send X-Whisper-Password so the edge function can independently
        // verify re-auth. A stolen JWT alone can no longer delete the account.
        supabase.auth.currentSessionOrNull()?.accessToken
            ?: error("Sign in before deleting your account.")
        // P2: transport moved to the shared EdgeFunctionClient; headers, body and the
        // exact error string are preserved verbatim.
        val response = edgeFunctions.execute(
            EdgeFunctionClient.Request(
                function = "whisper-delete-account",
                jsonBody = buildJsonObject {
                    if (!isTokenUser && !password.isNullOrBlank()) {
                        put("whisperPassword", password)
                    }
                }.toString(),
                authMode = EdgeFunctionClient.AuthMode.USER,
                extraHeaders = buildMap {
                    // V2-FIX W-A1/P0-4: password rides the body AND the compat header
                    // during the backend transition window.
                    if (!isTokenUser && !password.isNullOrBlank()) {
                        put("X-Whisper-Password", password)
                    }
                    // Fresh confirmation nonce (timestamp) to prevent replay beyond 5 min.
                    put("X-Whisper-Confirm-Ts", System.currentTimeMillis().toString())
                },
                connectTimeoutMs = 10_000,
                readTimeoutMs = 30_000,
            ),
        )
        if (!response.is2xx) {
            error(response.errorText().ifBlank { "HTTP ${response.code}" })
        }

        // M-5 FIX (reviewwhisper.md): ALL server-side data cleanup now happens INSIDE
        // whisper-delete-account BEFORE the GoTrue user is deleted. The old client-side
        // postgrest deletes ran AFTER user deletion, relying on RLS matching a JWT sub
        // that no longer corresponds to a live user — fragile against session revocation
        // tightening, and it silently no-op'd if Supabase changed that behavior.
        //
        // FK-cascaded tables (whisper_upload_quota, whisper_deleted_tombstones,
        // whisper_discover_quota) clean themselves automatically.

        // Sign out
        supabase.auth.signOut()
    }

    /**
     * Sybil gate for account creation (20261005): asks `whisper-signup-gate`
     * whether this IP may create another account (5/day). Fail-open on
     * transport errors (edge not deployed yet, offline) so a broken gate
     * never bricks signup; fail-closed ONLY on an explicit 429, which
     * means the quota service is healthy and the caller is over budget.
     */
    private suspend fun checkSignupAllowed(): Result<Unit> = runCatching {
        val response = runCatching {
            edgeFunctions.execute(
                EdgeFunctionClient.Request(
                    function = "whisper-signup-gate",
                    jsonBody = "{}",
                    authMode = EdgeFunctionClient.AuthMode.ANON,
                    extraHeaders = emptyMap(),
                    connectTimeoutMs = 10_000,
                    readTimeoutMs = 15_000,
                ),
            )
        }.getOrElse { return@runCatching }
        if (response.code == 429) {
            error("Too many signups from your network right now. Please try again tomorrow.")
        }
    }

    /**
     * One-shot server migration for legacy token accounts: the edge moves the
     * profile handle to a fresh current-scheme user and disables the weak
     * legacy credential. Returns true when the caller should retry the
     * current-scheme login immediately. Any transport/backend failure returns
     * false so callers fall back to the manual migration prompt.
     */
    private suspend fun tryServerLegacyMigrate(cleanToken: String): Result<Boolean> = runCatching {
        val response = edgeFunctions.execute(
            EdgeFunctionClient.Request(
                function = "whisper-legacy-migrate",
                jsonBody = buildJsonObject { put("token", cleanToken) }.toString(),
                authMode = EdgeFunctionClient.AuthMode.ANON,
                extraHeaders = emptyMap(),
                connectTimeoutMs = 10_000,
                readTimeoutMs = 30_000,
            ),
        )
        if (!response.is2xx) return@runCatching false
        val obj = runCatching { Json.parseToJsonElement(response.body).jsonObject }.getOrNull()
            ?: return@runCatching false
        obj["migrated"]?.jsonPrimitive?.booleanOrNull == true
    }

    suspend fun registerWithUsername(username: String, password: String, displayName: String): Result<Unit> = runCatching {
        val cleanUsername = username.trim().lowercase()
        val cleanDisplayName = displayName.trim()
        val virtualEmail = "$cleanUsername@u.whisper.local"
        
        require(password.length >= MIN_PASSWORD_LENGTH) { "Password must be at least $MIN_PASSWORD_LENGTH characters." }
        require(USERNAME_PATTERN.matches(cleanUsername)) { "Username must be 3-20 lowercase letters, numbers, or underscores." }
        require(cleanDisplayName.length in 1..60) { "Display name must be 1-60 characters." }
        checkSignupAllowed().getOrThrow()
        
        supabase.auth.signUpWith(Email) {
            this.email = virtualEmail
            this.password = password
            this.data = buildJsonObject {
                put("username", cleanUsername)
                put("display_name", cleanDisplayName)
            }
        }
        
        // With email confirmation OFF in Supabase, this signs in immediately
        if (supabase.auth.currentSessionOrNull() == null) {
            val firstAttempt = runCatching {
                supabase.auth.signInWith(Email) {
                    this.email = virtualEmail
                    this.password = password
                }
            }
            if (firstAttempt.isFailure) {
                val firstError = firstAttempt.exceptionOrNull()
                // A transient failure right after signup leaves the account created but
                // no session: retry the sign-in once so a hiccup doesn't strand the user.
                if (firstError != null && !isInvalidCredentials(firstError)) {
                    val retry = loginWithUsername(username, password)
                    if (retry.isFailure) {
                        val retryError = retry.exceptionOrNull()
                        if (retryError != null && isInvalidCredentials(retryError)) {
                            error("Your account was created, but the automatic sign-in failed. Please sign in with your username and password.")
                        }
                        throw retryError ?: firstError
                    }
                } else {
                    throw firstError ?: Exception("Sign-in failed after registration")
                }
            }
        }
    }

    suspend fun loginWithUsername(username: String, password: String): Result<Unit> = runCatching {
        val cleanUsername = username.trim().lowercase()
        val virtualEmail = "$cleanUsername@u.whisper.local"
        
        supabase.auth.signInWith(Email) {
            this.email = virtualEmail
            this.password = password
        }
    }

    suspend fun refreshUser(): Result<Unit> = runCatching {
        supabase.auth.retrieveUserForCurrentSession(updateSession = true)
    }

    // Removed email verification and password reset as they require real emails

    fun generateAnonToken(): WhisperAnonToken {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        val token = bytes.toHexString()
        // P1-15 FIX: Use full 64-char SHA256 hex (256-bit) for new accounts; 32 was 128-bit truncation.
        // Login retains fallback to 32 for pre-fix accounts.
        return WhisperAnonToken(token = token, virtualEmail = sha256(token) + "@whisper.toolz.app")
    }

    suspend fun registerWithToken(anonToken: WhisperAnonToken, username: String, displayName: String): Result<Unit> = runCatching {
        val cleanToken = normalizeToken(anonToken.token)
        require(isValidToken(cleanToken)) { "Token must be a valid 64-character hex string." }
        checkSignupAllowed().getOrThrow()
        val virtualEmail = sha256(cleanToken) + "@whisper.toolz.app"
        val virtualPassword = sha256("pwd_" + cleanToken)
        val cleanUsername = username.trim().lowercase()
        val cleanDisplayName = displayName.trim()
        supabase.auth.signUpWith(Email) {
            this.email = virtualEmail
            this.password = virtualPassword
            this.data = buildJsonObject {
                put("username", cleanUsername)
                put("display_name", cleanDisplayName)
            }
        }
        // Ensure session is started immediately
        if (supabase.auth.currentSessionOrNull() == null) {
            supabase.auth.signInWith(Email) {
                this.email = virtualEmail
                this.password = virtualPassword
            }
        }
    }

    /**
     * Outcome of probing the pre-P1-15 legacy token derivations.
     */
    sealed interface LegacyTokenLogin {
        /** No legacy derivation matched this token. */
        data object NotFound : LegacyTokenLogin
        /**
         * A legacy derivation opened the account. The legacy session is already
         * signed out again — the same token under the current scheme is a
         * DIFFERENT GoTrue user with no data, so the UI must prompt for a
         * username and migrate (create a current-scheme account for the same
         * token, then copy the profile) instead of signing in. Never locks out:
         * legacy holders always land here, with a warning banner, during the
         * 30-day sunset window. When (and only when) the `whisper-legacy-migrate`
         * edge function lands, it should consult `whisper_is_legacy_disabled()`
         * (20261005 SQL) before retiring the legacy GoTrue identity via the Auth
         * admin API — SQL alone cannot delete GoTrue users.
         */
        data class MigrationNeeded(
            val username: String?,
            val displayName: String?,
        ) : LegacyTokenLogin
    }

    /**
     * Typed failure surfaced through [loginWithToken] when a legacy derivation
     * opens the account. The UI detects `it is LegacyMigrationNeededException`
     * to show the "Legacy account found — tap Migrate" banner
     * (st_Whisper_Legacy_*) instead of "token not recognized".
     */
    class LegacyMigrationNeededException(
        val username: String?,
        val displayName: String?,
    ) : IllegalArgumentException(
        "Legacy account found - tap Migrate to move to the new secure credential.",
    )

    /**
     * Signs in an anonymous-token user with the single current
     * (virtualEmail, virtualPassword) derivation:
     * `SHA-256(token)@whisper.toolz.app` / `SHA-256("pwd_" + token)`.
     *
     * 20261005-P2: when the current derivation misses with invalid-credentials,
     * the three pre-P1-15 legacy derivations are probed ONCE each (see
     * [migrateLegacyTokenAccounts]) before failing. A legacy hit signs the
     * legacy session straight back out and fails with
     * [LegacyMigrationNeededException] carrying the legacy profile's
     * username/displayName so the UI can prompt migration — the Result<Unit>
     * shape is unchanged so existing callers keep compiling.
     */
    suspend fun loginWithToken(rawToken: String): Result<Unit> = runCatching {
        val cleanToken = normalizeToken(rawToken)
        require(isValidToken(cleanToken)) { "That token doesn't look right. Check for missing or extra characters." }
        val fullHash = sha256(cleanToken)
        val virtualEmail = fullHash + "@whisper.toolz.app"
        val virtualPassword = sha256("pwd_" + cleanToken)
        val attempt = runCatching {
            supabase.auth.signInWith(Email) {
                this.email = virtualEmail
                this.password = virtualPassword
            }
        }
        val ex = attempt.exceptionOrNull()
        if (ex != null) {
            if (!isInvalidCredentials(ex)) throw ex
            when (val legacy = migrateLegacyTokenAccounts(cleanToken).getOrThrow()) {
                is LegacyTokenLogin.MigrationNeeded -> {
                    // Server migration first: one call moves the handle and
                    // disables the weak credential, then the current-scheme
                    // login below just works. Undeployed/unreachable edge
                    // falls back to the manual prompt.
                    if (tryServerLegacyMigrate(cleanToken).getOrNull() == true) {
                        supabase.auth.signInWith(Email) {
                            this.email = virtualEmail
                            this.password = virtualPassword
                        }
                    } else {
                        throw LegacyMigrationNeededException(legacy.username, legacy.displayName)
                    }
                }
                LegacyTokenLogin.NotFound ->
                    throw IllegalArgumentException(
                        "Invalid login credentials. " + legacyLoginHint(),
                        ex,
                    )
            }
        }
    }

    /**
     * 20261005-P2 REAL implementation (replaces the Phase 1B fail-closed stub):
     * tries the current scheme first (1 attempt), then the 3 legacy candidates
     * ONCE each with [LEGACY_ATTEMPT_PACING_MS] pacing between probes.
     *
     * Legacy candidates (best-effort historical reconstruction of the
     * truncated 128-bit email era): truncated-email + SHA-512/SHA-256 password
     * variants. Each candidate costs exactly one GoTrue attempt, keeping login
     * fast and clear of per-identity rate limits.
     *
     * On a legacy hit: reads the legacy profile's username/displayName
     * (best-effort), immediately signs the legacy session back out, records
     * [pendingLegacyMigration], and returns [LegacyTokenLogin.MigrationNeeded]
     * so the UI can prompt "Legacy account found — tap Migrate to move to the
     * new secure credential" with a username choice. Non-credential errors
     * (network, provider_disabled, …) abort immediately — only
     * invalid-credentials advances to the next candidate.
     */
    suspend fun migrateLegacyTokenAccounts(rawToken: String): Result<LegacyTokenLogin> = runCatching {
        val cleanToken = normalizeToken(rawToken)
        require(isValidToken(cleanToken)) { "That token doesn't look right. Check for missing or extra characters." }
        val fullHash = sha256(cleanToken)
        val truncatedEmail = fullHash.take(32) + "@whisper.toolz.app"
        val candidates = listOf(
            // L1: truncated email + current password derivation.
            truncatedEmail to sha256("pwd_" + cleanToken),
            // L2: truncated email + SHA-512 password variant.
            truncatedEmail to sha512Hex("pwd_" + cleanToken),
            // L3: truncated email + raw-token-hash password variant.
            truncatedEmail to sha256(cleanToken),
        )
        for ((index, candidate) in candidates.withIndex()) {
            if (index > 0) delay(LEGACY_ATTEMPT_PACING_MS)
            val (email, password) = candidate
            val probe = runCatching {
                supabase.auth.signInWith(Email) {
                    this.email = email
                    this.password = password
                }
            }
            if (probe.isSuccess) {
                val (username, displayName) = readLegacyProfileInfo()
                // Never linger in the legacy identity: sign straight back out.
                runCatching { supabase.auth.signOut() }
                val needed = LegacyTokenLogin.MigrationNeeded(username, displayName)
                pendingLegacyMigration = needed
                return@runCatching needed
            }
            val probeError = probe.exceptionOrNull()
            if (probeError != null && !isInvalidCredentials(probeError)) throw probeError
        }
        LegacyTokenLogin.NotFound
    }

    /**
     * Best-effort read of the just-opened legacy account's profile handle.
     * Prefers the profiles row (same normalization the app uses everywhere);
     * falls back to the GoTrue user_metadata stamped at registration.
     */
    private suspend fun readLegacyProfileInfo(): Pair<String?, String?> {
        val uid = supabase.auth.currentUserOrNull()?.id
        if (uid != null) {
            runCatching {
                supabase.postgrest.from("profiles")
                    .select { filter { eq("id", uid) } }
                    .decodeSingleOrNull<WhisperProfile>()
            }.getOrNull()?.let { return it.username to it.displayName }
        }
        val metadata = supabase.auth.currentUserOrNull()?.userMetadata
        val username = metadata?.get("username")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it != "null" && it.isNotBlank() }
        val displayName = metadata?.get("display_name")?.jsonPrimitive?.contentOrNull
            ?.takeIf { it != "null" && it.isNotBlank() }
        return username to displayName
    }

    /**
     * Preserved Phase 1B hint text for the pure not-found path: the account
     * needs a one-time recovery/migration, not silent multi-candidate probing
     * beyond the single legacy pass above.
     */
    private fun legacyLoginHint(): String =
        "Legacy token accounts require one-time migration - contact support / use recovery."

    fun normalizeToken(raw: String): String = raw.trim().replace(Regex("[^0-9a-fA-F]"), "").lowercase()
    fun isValidToken(token: String): Boolean = token.length == 64 && token.all { it in '0'..'9' || it in 'a'..'f' }

    // V2-FIX W-A7: prefer structural detection — AuthErrorCode.InvalidCredentials / error strings
    // over broad statusCode 400. GoTrue returns error "invalid_credentials" (not "invalid_grant")
    // for wrong password, and status 400 alone would misclassify provider_disabled etc. as invalid creds.
    fun isInvalidCredentials(throwable: Throwable): Boolean {
        if (throwable is AuthRestException) {
            if (throwable.errorCode == AuthErrorCode.InvalidCredentials) return true
            if (throwable.error == "invalid_grant" || throwable.error == "invalid_credentials") return true
        }
        if (throwable is RestException) {
            if (throwable.error == "invalid_grant" || throwable.error == "invalid_credentials") return true
        }
        val msg = throwable.message.orEmpty()
        return msg.contains("Invalid login credentials", ignoreCase = true) ||
            msg.contains("invalid_credentials", ignoreCase = true) ||
            msg.contains("invalid_grant", ignoreCase = true)
    }

    suspend fun signOut(): Result<Unit> = runCatching {
        supabase.auth.signOut()
    }

    private fun sha256(input: String): String = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8)).toHexString()
    private fun sha512Hex(input: String): String = MessageDigest.getInstance("SHA-512").digest(input.toByteArray(Charsets.UTF_8)).toHexString()
    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        const val MIN_PASSWORD_LENGTH = 10
        // 20261005-P2: pacing between legacy-derivation probes — one 500ms beat
        // per extra GoTrue attempt keeps the 3-candidate legacy pass from
        // hammering auth under flaky networks. Current-scheme login stays single-attempt.
        const val LEGACY_ATTEMPT_PACING_MS = 500L
        // Phase 1B: single-attempt token login — no candidate pacing delay.
        val USERNAME_PATTERN = Regex("^[a-z0-9](?:[a-z0-9_]{1,18}[a-z0-9])?$")
    }
}
