package com.siteledger.app.core.common

import kotlinx.serialization.json.Json

/** Shared JSON configuration for outbox payloads and Supabase DTOs. */
val AppJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
