package com.essvocab.app.domain.shanbay

import com.essvocab.app.data.local.ReviewCardEntity
import java.time.DayOfWeek
import java.time.LocalDate

data class DayCheckInStatus(
    val date: LocalDate,
    val weekdayShort: String,
    val checkedIn: Boolean,
    val isToday: Boolean,
)

data class StreakSummary(
    val currentStreak: Int,
    val totalCheckInDays: Int,
    val isCheckedInToday: Boolean,
    val last7Days: List<DayCheckInStatus>,
)

object ShanbayMechanics {

    /**
     * Computes Shanbay-style 0..5 mastery level from FSRS stability and bypass flag.
     */
    fun masteryLevel(card: ReviewCardEntity?): Int {
        if (card == null) return 0
        if (card.isMasteredBypass) return 5
        if (card.reps == 0) return 0
        return when {
            card.stability < 1.5 -> 1
            card.stability < 4.0 -> 2
            card.stability < 7.0 -> 3
            card.stability < 21.0 -> 4
            else -> 5
        }
    }

    fun masteryLabel(level: Int): String = when (level.coerceIn(0, 5)) {
        0 -> "新词"
        1 -> "初识"
        2 -> "熟悉"
        3 -> "巩固"
        4 -> "牢记"
        else -> "已掌握"
    }

    /**
     * Progressive hint shown when the user taps "模糊" in Shanbay Stage 1.
     * Reveals category, abbreviation badges, and masked first-char Chinese hint
     * without giving away the full Chinese answer.
     */
    fun buildProgressiveHint(
        zh: String,
        packName: String,
        abbreviations: List<String>,
    ): String {
        val parts = mutableListOf<String>()
        if (packName.isNotBlank()) {
            parts.add("所属专题：$packName")
        }
        if (abbreviations.isNotEmpty()) {
            parts.add("常见缩写：${abbreviations.joinToString(" / ")}")
        }
        val cleanZh = zh.trim()
        if (cleanZh.length >= 2) {
            val firstChar = cleanZh.first()
            val maskedTail = List(minOf(cleanZh.length - 1, 6)) { "_" }.joinToString(" ")
            val suffix = if (cleanZh.length > 7) " …" else ""
            parts.add("中文首字提示：$firstChar $maskedTail$suffix")
        } else if (cleanZh.isNotEmpty()) {
            parts.add("中文提示：共 ${cleanZh.length} 个字")
        }
        return parts.joinToString("\n")
    }

    /**
     * Computes continuous streak & 7-day calendar strip from ISO date keys (yyyy-MM-dd).
     */
    fun calculateStreak(
        dateKeysDesc: List<String>,
        today: LocalDate = LocalDate.now(),
    ): StreakSummary {
        val parsedDates = dateKeysDesc.mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()
        val isCheckedInToday = today in parsedDates

        var streak = 0
        var cursor = if (isCheckedInToday) today else today.minusDays(1)
        while (cursor in parsedDates) {
            streak++
            cursor = cursor.minusDays(1)
        }

        val last7Days = (6 downTo 0).map { offset ->
            val date = today.minusDays(offset.toLong())
            DayCheckInStatus(
                date = date,
                weekdayShort = weekdayLabel(date.dayOfWeek),
                checkedIn = date in parsedDates,
                isToday = offset == 0,
            )
        }

        return StreakSummary(
            currentStreak = streak,
            totalCheckInDays = parsedDates.size,
            isCheckedInToday = isCheckedInToday,
            last7Days = last7Days,
        )
    }

    private fun weekdayLabel(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
        DayOfWeek.MONDAY -> "一"
        DayOfWeek.TUESDAY -> "二"
        DayOfWeek.WEDNESDAY -> "三"
        DayOfWeek.THURSDAY -> "四"
        DayOfWeek.FRIDAY -> "五"
        DayOfWeek.SATURDAY -> "六"
        DayOfWeek.SUNDAY -> "日"
    }
}
