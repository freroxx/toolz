package com.frerox.toolz.data.ai

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class AiConfig(
    val name: String,
    val provider: String,
    val model: String,
    val apiKey: String,
    val iconRes: String = "AUTO" // "AUTO", "GEMINI", "CHATGPT", "GROQ", "CLAUDE", "BOT", "SPARKLE", "BRAIN"
)
