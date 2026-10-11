package com.siteledger.app.core.common

import java.util.Locale

/** "82.3%" — one decimal place, US formatting so the decimal separator is stable. */
fun formatPercent(value: Double): String = String.format(Locale.US, "%.1f%%", value)
