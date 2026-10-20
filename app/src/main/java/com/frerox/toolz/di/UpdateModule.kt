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

import com.frerox.toolz.data.update.UpdateConstants
import com.frerox.toolz.data.update.UpdateService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import com.squareup.moshi.Moshi
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    @Provides
    @Singleton
    fun provideUpdateService(appClient: OkHttpClient, moshi: Moshi): UpdateService {
        // Update checks must never hang the UI or a worker. The shared app
        // client has long media timeouts, so derive a short-lived clone.
        val updateClient = appClient.newBuilder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .build()
        return Retrofit.Builder()
            .baseUrl(UpdateConstants.GITHUB_API_BASE_URL)
            .client(updateClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(UpdateService::class.java)
    }
}
