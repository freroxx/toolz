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

package com.frerox.toolz.di

import android.content.Context
import com.frerox.toolz.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabaseClient(@ApplicationContext context: Context): SupabaseClient {
        // Fail-fast diagnostic: empty BuildConfig means local.properties was missing at build time
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_ANON_KEY.isBlank()) {
            android.util.Log.e("SupabaseModule", "SUPABASE_URL/KEY is blank — check local.properties and rebuild. Whisper will stay offline.")
        }
        // Use placeholder that fails fast with clear error if blank, to avoid obscure IllegalState at auth
        val url = BuildConfig.SUPABASE_URL.ifBlank { "https://invalid.supabase.co" }
        val key = BuildConfig.SUPABASE_ANON_KEY.ifBlank { "invalid-key" }
        return createSupabaseClient(
            supabaseUrl = url,
            supabaseKey = key,
        ) {
        install(Auth) {
            host = "login"
            scheme = "whisper-auth"
            // Persist the session encrypted with a Keystore key instead of plaintext.
            sessionManager = KeystoreSessionManager(context)
        }
        install(Postgrest)
        install(Realtime)
        install(Storage)
        }
    }
}
