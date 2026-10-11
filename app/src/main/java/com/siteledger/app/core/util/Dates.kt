package com.siteledger.app.core.util

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object Dates {

    private val display = DateTimeFormatter.ofPattern("d MMM yyyy")
    private val displayWithTime = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")

    fun format(date: LocalDate?): String = date?.format(display) ?: "—"

    fun formatMillisUtc(epochMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String =
        Instant.ofEpochMilli(epochMillis).atZone(zone).format(displayWithTime)

    fun toIso(date: LocalDate): String = date.toString()

    fun parseIso(text: String?): LocalDate? {
        if (text.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(text) }.getOrNull()
    }

    fun parseIsoInstantToMillis(text: String?): Long? {
        if (text.isNullOrBlank()) return null
        return runCatching { java.time.OffsetDateTime.parse(text).toInstant().toEpochMilli() }
            .recoverCatching { Instant.parse(text).toEpochMilli() }
            .getOrNull()
    }

    fun millisToIsoUtc(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()
}
