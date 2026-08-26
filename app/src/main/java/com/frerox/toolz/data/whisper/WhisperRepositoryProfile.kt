/*
 * Copyright (C) 2026 Toolz Contributors
 * GPL-3.0 License
 */

package com.frerox.toolz.data.whisper

/**
 * P4b PROFILE DOMAIN of [WhisperRepository]: profiles, username availability,
 * profile updates, presence stamps and avatars — extracted as extension functions.
 * Physical move only: bodies are byte-identical; all call sites unchanged.
 *
 * Repository members widened to internal for this split: db, crypto, store,
 * encryptedImageHost, avatarLoader, runCatchingCE, profileCache(+Ts/TTL),
 * cachedProfile/cacheProfile, updateProfileRowWithFreshness.
 */
import io.github.jan.supabase.auth.auth
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant

suspend fun WhisperRepository.getMyProfile(forceRefresh: Boolean = false): Result<WhisperProfile> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) error("User not authenticated")

        if (!forceRefresh) {
            cachedProfile(currentId)?.let { cached ->
                if (cached.publicKey != null) return Result.success(cached)
            }
        }

        val existing = db.from("profiles")
            .select { filter { eq("id", currentId) } }
            .decodeList<WhisperProfile>()
            .firstOrNull()

        val pubKey = crypto.getPublicKeyBase64()

        val profile = if (existing == null) {
            val user = supabase.auth.currentUserOrNull()
            val metadata = user?.userMetadata
            // V2-FIX (reviewwhisper.md) L-18: jsonPrimitive.contentOrNull instead of
            // removeSurrounding("\"") — correctly handles JSON null, escapes and
            // values that legitimately contain quote characters.
            val metaUsername = metadata?.get("username")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it != "null" && it.isNotBlank() }
            val metaDisplayName = metadata?.get("display_name")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it != "null" && it.isNotBlank() }
            val initialUsername = metaUsername ?: "user_${currentId.take(8)}"
            val insertData = WhisperProfileInsert(
                id = currentId,
                username = initialUsername,
                displayName = metaDisplayName,
                isPrivate = false,
                publicKey = pubKey
            )
            db.from("profiles").insert(insertData) { defaultToNull = false }
            WhisperProfile(id = currentId, username = initialUsername, displayName = metaDisplayName, isPrivate = false, publicKey = pubKey)
        } else {
            val user = supabase.auth.currentUserOrNull()
            val metadata = user?.userMetadata
            // V2-FIX (reviewwhisper.md) L-18: safe JSON access instead of quote stripping.
            val metaDisplayName = metadata?.get("display_name")?.jsonPrimitive?.contentOrNull
                ?.takeIf { it != "null" && it.isNotBlank() }

            // Check if username is an ugly 64-char token string and normalize it.
            // V2-FIX (reviewwhisper.md) L-17: only rename when the account looks untouched —
            // require hex length >= 32 AND no display_name set, so a real personalized
            // account can never lose its chosen username over a coincidental hex string.
            val isUglyHexUsername = existing.username.length >= 32 &&
                existing.username.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' } &&
                existing.displayName.isNullOrBlank()

            val cleanUsername = if (isUglyHexUsername) {
                android.util.Log.i("WhisperRepo", "Self-healing raw-token username for ${currentId.take(8)}…: renaming to anon_${existing.username.take(6)}")
                "anon_${existing.username.take(6)}"
            } else null
            
            val needsDisplayName = existing.displayName.isNullOrBlank() && !metaDisplayName.isNullOrBlank()
            // Self-heal only when the server has NO key (fresh row after reinstall/clear
            // data). Reinstalling with a DIFFERENT key now flows through the key-change
            // review flow instead of silently clobbering the server's key.
            val needsKey = pubKey != null && existing.publicKey.isNullOrBlank()
            // V4-FIX (field report): reinstall divergence — uninstalling wipes the
            // AndroidKeyStore identity, but this profile row keeps the PREVIOUS
            // install's public key. Every contact then encrypts to a key this device
            // can no longer open ("[Encrypted message]" both directions) and the two
            // fingerprints disagree. Whisper is single-device per account, so when no
            // staged rotation is in flight we republish THIS device's key; contacts
            // receive it as a fresh server update and auto-pin within the freshness
            // window, restoring readability automatically.
            val reinstallDivergence = pubKey != null &&
                !existing.publicKey.isNullOrBlank() &&
                existing.publicKey != pubKey &&
                !crypto.hasStagedAliases()
            ProtocolDiagnostics.log(
                "keyHeal: divergence=$reinstallDivergence hasStaged=${crypto.hasStagedAliases()}"
            )
            if (reinstallDivergence || (!existing.publicKey.isNullOrBlank() && pubKey != null)) {
                // V4-FIX diagnostics: field reports said the heal "did nothing". Log the
                // full decision inputs so any silent blocker is visible in logcat.
                android.util.Log.w(
                    "WhisperRepo",
                    "KeyHeal check: serverKey=${existing.publicKey?.take(12)}… localKey=${pubKey?.take(12)}… " +
                        "equal=${existing.publicKey == pubKey} hasStaged=${crypto.hasStagedAliases()} " +
                        "-> divergence=$reinstallDivergence"
                )
            }

            if (needsKey || reinstallDivergence || cleanUsername != null || needsDisplayName) {
                // Build the body without nulls: a null field would be serialized as
                // "field": null and NULL the server column (e.g. username) on self-heal.
                val body = buildJsonObject {
                    if (needsKey || reinstallDivergence) put("public_key", pubKey!!)
                    cleanUsername?.let { put("username", it) }
                    if (needsDisplayName) put("display_name", metaDisplayName!!)
                    // V5.1: republishing a key MUST look fresh to contacts, or they
                    // classify CHANGED and the manual verify dance returns.
                    if (needsKey || reinstallDivergence) {
                        put("updated_at", java.time.Instant.now().toString())
                    }
                }
                // V6-R6 (#4): a FAILED divergence-republish used to vanish inside the
                // outer runCatching — server kept the stale key, every contact sealed
                // to it, and the reinstall dance returned with zero diagnostic trail.
                runCatching { updateProfileRowWithFreshness(currentId, body) }
                    .onFailure { healError ->
                        ProtocolDiagnostics.increment("heal.republishFailed")
                        ProtocolDiagnostics.log("heal: divergence-republish FAILED: ${healError.message}")
                        throw healError
                    }
                existing.copy(
                    publicKey = if (needsKey || reinstallDivergence) pubKey else existing.publicKey,
                    username = cleanUsername ?: existing.username,
                    displayName = if (needsDisplayName) metaDisplayName else existing.displayName
                )
            } else existing
        }
        cacheProfile(currentId, profile)
        profile
    }

    suspend fun WhisperRepository.getProfile(userId: String, forceRefresh: Boolean = false): Result<WhisperProfile> = runCatching {
        if (!forceRefresh) {
            cachedProfile(userId)?.let { cached ->
                if (!cached.publicKey.isNullOrBlank() || userId == myId) {
                    cachePeerKey(userId, cached.publicKey)
                    return Result.success(cached)
                }
            }
        }
        val p = db.from("profiles")
            .select { filter { eq("id", userId) } }
            .decodeSingle<WhisperProfile>()
        cacheProfile(userId, p)
        cachePeerKey(userId, p.publicKey)
        p
    }

    suspend fun WhisperRepository.searchProfiles(query: String): Result<List<WhisperProfile>> = runCatching {
        val q = query.trim()
        // Gate trivial queries: a single-char ilike '%x%' scan per debounce wastes DB
        // cycles and produces noisy result churn while typing.
        if (q.length < 2) return@runCatching emptyList()
        // M-13 FIX (reviewwhisper.md): escape the backslash FIRST — a literal "\" in the
        // query would otherwise neutralize the %/_ escapes that follow it.
        val escaped = q.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        db.from("profiles")
            .select {
                filter {
                    or {
                        ilike("username", "%$escaped%")
                        ilike("display_name", "%$escaped%")
                    }
                    neq("id", myId)
                    eq("hide_from_discover", false)
                    eq("is_private", false)
                }
                limit(30)
            }
            .decodeList()
    }

    suspend fun WhisperRepository.checkUsernameAvailable(username: String): Result<Boolean> = runCatching {
        val results = db.from("profiles")
            .select { filter { eq("username", username.trim().lowercase()) } }
            .decodeList<WhisperProfile>()
        results.isEmpty()
    }

    suspend fun WhisperRepository.updateProfile(update: WhisperProfileUpdate): Result<Unit> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) error("User not authenticated")
        val pubKey = crypto.getPublicKeyBase64()
        // Build the body explicitly so unset fields (especially public_key) are OMITTED:
        // supabase-kt serializes nulls by default, and "public_key": null would NULL the
        // server's column. The local key is only pushed when explicitly requested or when
        // the server row has none to begin with — never overwritten with a mismatch.
        val body = buildJsonObject {
            update.username?.let { put("username", it) }
            // Empty strings must reach the server to clear a field; null means "don't touch".
            if (update.displayName != null) put("display_name", update.displayName)
            if (update.bio != null) put("bio", update.bio)
            if (update.avatarUrl != null) put("avatar_url", update.avatarUrl)
            update.isPrivate?.let { put("is_private", it) }
            update.isHiddenFromDiscover?.let { put("hide_from_discover", it) }
            update.lastSeenAt?.let { put("last_seen_at", it) }
            when {
                update.publicKey != null -> put("public_key", update.publicKey!!)
                pubKey != null -> {
                    // V2-FIX (reviewwhisper.md) L-13: CE-safe runCatching around suspend call.
                    val serverKeyResult = runCatchingCE {
                        db.from("profiles").select { filter { eq("id", currentId) } }
                            .decodeSingleOrNull<WhisperProfile>()?.publicKey
                    }
                    if (serverKeyResult.isSuccess && serverKeyResult.getOrNull().isNullOrBlank()) {
                        put("public_key", pubKey)
                        // V5.1: a first publish is also a key event — keep it fresh.
                        put("updated_at", java.time.Instant.now().toString())
                    }
                }
            }
        }
        updateProfileRowWithFreshness(currentId, body)
        profileCache.remove(currentId); profileCacheTs.remove(currentId)
    }

    suspend fun WhisperRepository.updateLastSeen(): Result<Unit> = updateLastSeenInternal(backdateMinutes = 0)

    /**
     * V6-R6: instant OFFLINE — backdates last_seen beyond the 120s presence window so
     * partners see "offline / online Xm ago" the moment the app leaves foreground,
     * without waiting for the window to age out. No schema change needed.
     */
    suspend fun WhisperRepository.goOfflineInstantly(): Result<Unit> = updateLastSeenInternal(backdateMinutes = 3)

    internal suspend fun WhisperRepository.updateLastSeenInternal(backdateMinutes: Long): Result<Unit> = runCatching {
        val currentId = myId
        if (currentId.isBlank()) return@runCatching
        val now = java.time.OffsetDateTime.now().minusMinutes(backdateMinutes).toString()
        val body = buildJsonObject { put("last_seen_at", now) }
        db.from("profiles").update(body) {
            filter { eq("id", currentId) }
        }
    }

    suspend fun WhisperRepository.uploadAvatar(imageBytes: ByteArray, mimeType: String): Result<String> = runCatching {
        // V6-R7 AVATARS: Supabase storage → ImgBB (encrypted). Storage bloat was the
        // driver; posture stays ciphertext-only on third-party hosts via the owner-key
        // derived codec. mimeType param kept for call-site compatibility — output is
        // always a ≤256px JPEG regardless of input format.
        val ownPub = crypto.getPublicKeyBase64()
            ?: error("Encryption unavailable: device identity key not ready.")
        val jpeg = prepareAvatarBytes(imageBytes)
            ?: error("Could not process this image as an avatar.")
        val sealed = WhisperAvatarCodec.seal(jpeg, ownPub)

        // V6-R7b FIX (double-wrap): the host client already applies the WZ1/PNG
        // transport wrap. Pre-wrapping here produced PNG(WZ1(PNG(WZ1(sealed))))
        // on ImgBB; the loader unwraps only once and hands PNG bytes to the
        // codec which then fails closed. Pass the sealed payload and let the
        // host wrap exactly once (same layering as chat attachments).
        val (url, attachmentId) = encryptedImageHost.upload(
            cipherBytes = sealed,
            name = "avatar_${myId.take(8)}_${System.currentTimeMillis()}",
            expirationSeconds = null,
        ).getOrThrow()

        // Deletion handle rides in a fragment (never sent to any server).
        val finalUrl = attachmentId?.let { "$url#att=${java.net.URLEncoder.encode(it, "UTF-8")}" } ?: url

        // V6-R7c FIX (not-applied): prime the avatar loader so the new picture renders
        // instantly from cache without waiting for a network download/decrypt round-trip.
        // Clean key is the bare ImgBB URL without fragment/bust query.
        runCatching {
            val cleanKey = finalUrl.substringBefore("#").substringBefore("?")
            avatarLoader.prime(cleanKey, sealed)
        }

        // V6-R7d FIX (not-applied after reinstall/rotation): the sealing key is
        // derived from the owner's public key. If the device's current key
        // (ownPub) differs from the key stored in the profile row, sealing with
        // ownPub but leaving the row's public_key stale makes the avatar
        // undecryptable for everyone (including self on next fetch which uses the
        // row's key). Publish the current key atomically with the avatar URL
        // when they differ so viewers derive the same key that was used to seal.
        val serverPub = runCatching {
            db.from("profiles").select { filter { eq("id", myId) } }
                .decodeSingleOrNull<WhisperProfile>()?.publicKey
        }.getOrNull()
        // Publish the current key whenever the row's key is missing or stale so
        // the sealing key and the row's key stay in sync; otherwise keep the
        // avatar-only update to avoid unnecessary key churn.
        val avatarUpdate = if (serverPub.isNullOrBlank() || serverPub != ownPub) {
            WhisperProfileUpdate(avatarUrl = finalUrl, publicKey = ownPub)
        } else {
            WhisperProfileUpdate(avatarUrl = finalUrl)
        }
        updateProfile(avatarUpdate).getOrThrow()
        profileCache.remove(myId); profileCacheTs.remove(myId)
        finalUrl
    }

    /** Center-crop square + downscale to [WhisperRepository.AVATAR_SIDE_PX] JPEG for avatar uploads. */
    internal suspend fun WhisperRepository.prepareAvatarBytes(raw: ByteArray): ByteArray? =
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            runCatching {
                val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
                android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, bounds)
                if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@runCatching null
                val side = WhisperRepository.AVATAR_SIDE_PX
                var sample = 1
                while (bounds.outWidth / (sample * 2) >= side && bounds.outHeight / (sample * 2) >= side) sample *= 2
                val decodeOpts = android.graphics.BitmapFactory.Options().apply { inSampleSize = sample }
                val src = android.graphics.BitmapFactory.decodeByteArray(raw, 0, raw.size, decodeOpts)
                    ?: return@runCatching null
                val crop = centerCropSquare(src, side)
                val out = java.io.ByteArrayOutputStream().also { it.buffered() }
                crop.compress(android.graphics.Bitmap.CompressFormat.JPEG, 85, out)
                if (crop !== src) crop.recycle()
                src.recycle()
                out.toByteArray()
            }.getOrNull()
        }

    internal fun WhisperRepository.centerCropSquare(src: android.graphics.Bitmap, sidePx: Int): android.graphics.Bitmap {
        val w = src.width; val h = src.height
        val edge = minOf(w, h)
        val x = (w - edge) / 2
        val y = (h - edge) / 2
        val cropped = android.graphics.Bitmap.createBitmap(src, x, y, edge, edge)
        return if (edge > sidePx) {
            android.graphics.Bitmap.createScaledBitmap(cropped, sidePx, sidePx, true)
        } else cropped
    }

    suspend fun WhisperRepository.deleteAvatar(): Result<Unit> = runCatchingCE {
        // V2-FIX (reviewwhisper.md) R-M7: honor the delete-before-null ordering rationale —
        // only null avatar_url after the blob deletion succeeded OR failed because the
        // object is already gone (404/400 treated as success). On a genuine network
        // failure the reference is kept so a retry remains possible.
        val current = db.from("profiles").select { filter { eq("id", myId) } }
            .decodeSingleOrNull<WhisperProfile>()
        current?.avatarUrl?.let { url ->
            // V6-R7 AVATARS: ImgBB-hosted avatars (encrypted, fragment carries the
            // deletion handle) route through the shared host client; legacy Supabase
            // Storage URLs keep the original path-parsing flow.
            if (url.contains("i.ibb.co")) {
                val cleanUrl = url.substringBefore("#att=")
                val attId = url.substringAfter("#att=", "").takeIf { it.isNotBlank() }
                    ?.let { runCatching { java.net.URLDecoder.decode(it, "UTF-8") }.getOrNull() }
                val blobResult = runCatchingCE { encryptedImageHost.delete(cleanUrl, attId) }
                blobResult.exceptionOrNull()?.let { e ->
                    android.util.Log.w("WhisperRepo", "deleteAvatar: imgbb delete failed (orphan tolerated): ${e.message}")
                }
            } else {
            // Robust object-path extraction from the public URL:
            //   <origin>/storage/v1/object/public/whisper-avatars/<userId>/avatar.ext?...
            // Parse via URL segments instead of substringAfter so encoded chars,
            // repeated substrings, or query strings can never corrupt the path.
            val path = runCatching {
                val parsed = java.net.URL(url)
                val segments = parsed.path.split("/").filter { it.isNotBlank() }
                val bucketIdx = segments.indexOf("whisper-avatars")
                if (bucketIdx >= 0 && bucketIdx < segments.lastIndex) {
                    segments.drop(bucketIdx + 1).joinToString("/") { java.net.URLDecoder.decode(it, "UTF-8") }
                } else ""
            }.getOrDefault("")
            if (path.isNotBlank()) {
                val blobResult = runCatchingCE { store.from("whisper-avatars").delete(path) }
                blobResult.exceptionOrNull()?.let { e ->
                    val restStatus = (e as? io.github.jan.supabase.exceptions.RestException)?.statusCode
                    val msg = e.message.orEmpty().lowercase()
                    val objectMissing = restStatus == 404 || restStatus == 400 ||
                        msg.contains("404") || msg.contains("not found")
                    if (!objectMissing) throw e
                    android.util.Log.w("WhisperRepo", "deleteAvatar: blob already gone ($path); clearing reference anyway")
                }
            }
            }
        }
        db.from("profiles").update(mapOf("avatar_url" to null as String?)) {
            filter { eq("id", myId) }
        }
        profileCache.remove(myId); profileCacheTs.remove(myId)
    }
