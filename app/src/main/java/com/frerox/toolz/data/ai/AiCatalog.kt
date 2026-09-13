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

package com.frerox.toolz.data.ai

import com.squareup.moshi.JsonClass

/**
 * Server-driven AI model catalog (`GET https://toolz-app.vercel.app/api/models`).
 *
 * Field names intentionally match the TypeScript `ProviderCatalog` in
 * `toolz-website/api/models.ts` 1:1. All fields have safe defaults so a
 * newer server payload with extra/unknown fields still parses (Moshi
 * ignores unknown fields by default) and a partial payload never crashes
 * a getter — [AiSettingsHelper] falls back to its bundled tables.
 */
@JsonClass(generateAdapter = true)
data class AiCatalogResponse(
    val version: Int = 0,
    val updatedAt: String = "",
    val providers: List<AiCatalogProvider> = emptyList(),
    val disclaimerText: String = "",
    val apiKeySuggestion: String = "",
)

@JsonClass(generateAdapter = true)
data class AiCatalogProvider(
    val id: String = "",
    val displayName: String = "",
    val chatCompletionUrl: String? = null,
    val modelsUrl: String? = null,
    val openAiCompatible: Boolean = false,
    val recommendedModel: String = "",
    val fallbackModels: List<String> = emptyList(),
    val models: List<String> = emptyList(),
    val freeModels: List<String> = emptyList(),
    val retiredModels: Map<String, String> = emptyMap(),
    val visionSubstrings: List<String> = emptyList(),
    val supportsFiles: Boolean = false,
    val filesSubstrings: List<String>? = null,
    val keyPlaceholder: String = "",
    val keyUrl: String = "",
    val keyPrefix: String? = null,
    val description: String = "",
    val tutorial: List<String> = emptyList(),
)
