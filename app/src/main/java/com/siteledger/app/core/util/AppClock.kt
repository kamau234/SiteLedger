package com.siteledger.app.core.util

import java.time.LocalDate
import java.time.ZoneId

interface AppClock {
    fun nowMillis(): Long
    fun today(): LocalDate
}

class SystemAppClock(private val zone: ZoneId = ZoneId.systemDefault()) : AppClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
    override fun today(): LocalDate = LocalDate.now(zone)
}
