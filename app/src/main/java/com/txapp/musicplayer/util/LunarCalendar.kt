package com.txapp.musicplayer.util

import java.util.Calendar

/**
 * Robust Solar to Vietnamese Lunar Calendar Lookup for Tet Holidays (2025-2040).
 * Precision-focused for User Requests.
 */
object LunarCalendar {

    data class LunarDate(val day: Int, val month: Int, val year: Int)

    // Solar dates of the 1st Day of Lunar New Year (Tết) for UTC+7
    private val tetSolarDates = mapOf(
        2025 to Pair(1, 29),
        2026 to Pair(2, 17),
        2027 to Pair(2, 6),
        2028 to Pair(1, 26),
        2029 to Pair(2, 13),
        2030 to Pair(2, 3),
        2031 to Pair(1, 23),
        2032 to Pair(2, 11),
        2033 to Pair(1, 31),
        2034 to Pair(2, 19),
        2035 to Pair(2, 8),
        2036 to Pair(1, 28),
        2037 to Pair(2, 15),
        2038 to Pair(2, 4),
        2039 to Pair(1, 24),
        2040 to Pair(2, 12)
    )

    /**
     * Helper to get Lunar Date only if within Tet range (approx -4 days to +5 days).
     * Returns null if not in Tet range.
     */
    fun getStrictTetRangeDate(solarDay: Int, solarMonth: Int, solarYear: Int): LunarDate? {
        val tetDate = tetSolarDates[solarYear] ?: return null
        
        val currentCal = Calendar.getInstance().apply { set(solarYear, solarMonth - 1, solarDay) }
        val tetCal = Calendar.getInstance().apply { set(solarYear, tetDate.first - 1, tetDate.second) }
        
        val diffDays = ((currentCal.timeInMillis - tetCal.timeInMillis) / (1000 * 60 * 60 * 24)).toInt()
        
        return when (diffDays) {
            0 -> LunarDate(1, 1, solarYear) // Mùng 1
            1 -> LunarDate(2, 1, solarYear) // Mùng 2
            2 -> LunarDate(3, 1, solarYear) // Mùng 3
            3 -> LunarDate(4, 1, solarYear) // Mùng 4
            -1 -> LunarDate(30, 12, solarYear - 1) // Tat Nien
            -2 -> LunarDate(29, 12, solarYear - 1)
            -3 -> LunarDate(28, 12, solarYear - 1)
            -4 -> LunarDate(27, 12, solarYear - 1)
            else -> null
        }
    }

    fun getLunarDate(solarDay: Int, solarMonth: Int, solarYear: Int): LunarDate {
        // Fallback to Solar Date as requested by User if not in Tet range
        return getStrictTetRangeDate(solarDay, solarMonth, solarYear) 
            ?: LunarDate(solarDay, solarMonth, solarYear)
    }

    /**
     * Specifically checks if today is Giao Thua (The moment before Mùng 1).
     * Since Giao Thua is 00:00 of Mùng 1, scheduling at midnight of Mùng 1 is correct.
     */
}
