package com.essvocab.app.domain.shanbay

import com.essvocab.app.data.local.ReviewCardEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ShanbayMechanicsTest {

    @Test
    fun masteryLevelMapsStabilityBoundariesAndBypassFlag() {
        assertEquals(0, ShanbayMechanics.masteryLevel(null))
        assertEquals(0, ShanbayMechanics.masteryLevel(ReviewCardEntity(wordId = "w1", reps = 0, stability = 5.0)))
        assertEquals(1, ShanbayMechanics.masteryLevel(ReviewCardEntity(wordId = "w1", reps = 1, stability = 0.8)))
        assertEquals(2, ShanbayMechanics.masteryLevel(ReviewCardEntity(wordId = "w1", reps = 2, stability = 2.5)))
        assertEquals(3, ShanbayMechanics.masteryLevel(ReviewCardEntity(wordId = "w1", reps = 3, stability = 5.0)))
        assertEquals(4, ShanbayMechanics.masteryLevel(ReviewCardEntity(wordId = "w1", reps = 4, stability = 12.0)))
        assertEquals(5, ShanbayMechanics.masteryLevel(ReviewCardEntity(wordId = "w1", reps = 5, stability = 25.0)))
        assertEquals(
            5,
            ShanbayMechanics.masteryLevel(
                ReviewCardEntity(wordId = "w1", reps = 0, stability = 0.0, isMasteredBypass = true),
            ),
        )
    }

    @Test
    fun buildProgressiveHintMasksFullAnswerWhileGivingClues() {
        val hint = ShanbayMechanics.buildProgressiveHint(
            zh = "电池储能系统",
            packName = "基础术语与系统",
            abbreviations = listOf("BESS"),
        )
        assertTrue(hint.contains("基础术语与系统"))
        assertTrue(hint.contains("BESS"))
        assertTrue(hint.contains("电 _ _ _ _ _"))
        assertFalse("Hint must never leak the full Chinese answer", hint.contains("电池储能系统"))
    }

    @Test
    fun buildProgressiveHintPrioritizesEnglishContextBeforeChineseClue() {
        assertEquals(2, ShanbayMechanics.maxHintLevel("The containerized BESS delivers 5 MWh."))
        assertEquals(1, ShanbayMechanics.maxHintLevel(null))
        assertEquals(1, ShanbayMechanics.maxHintLevel("   "))

        val level1 = ShanbayMechanics.buildProgressiveHint(
            zh = "电池储能系统",
            packName = "基础术语与系统",
            abbreviations = listOf("BESS"),
            exampleEn = "The containerized BESS delivers 5 MWh.",
            hintLevel = 1,
        )
        assertTrue(level1.contains("The containerized BESS delivers 5 MWh."))
        assertTrue(level1.contains("BESS"))
        assertFalse("Level 1 with example should not leak Chinese clue yet", level1.contains("中文首字提示"))
        assertFalse("Hint must never leak the full Chinese answer", level1.contains("电池储能系统"))

        val level2 = ShanbayMechanics.buildProgressiveHint(
            zh = "电池储能系统",
            packName = "基础术语与系统",
            abbreviations = listOf("BESS"),
            exampleEn = "The containerized BESS delivers 5 MWh.",
            hintLevel = 2,
        )
        assertTrue(level2.contains("The containerized BESS delivers 5 MWh."))
        assertTrue(level2.contains("电 _ _ _ _ _"))
        assertFalse("Hint must never leak the full Chinese answer", level2.contains("电池储能系统"))
    }

    @Test
    fun calculateStreakHandlesConsecutiveAndMissedDaysAcrossSevenDayWindow() {
        val today = LocalDate.of(2026, 11, 24)
        // Checked in today, yesterday, and 2 days ago -> streak = 3
        val streakWithToday = ShanbayMechanics.calculateStreak(
            dateKeysDesc = listOf("2026-11-24", "2026-11-23", "2026-11-22", "2026-11-19"),
            today = today,
        )
        assertEquals(3, streakWithToday.currentStreak)
        assertEquals(4, streakWithToday.totalCheckInDays)
        assertTrue(streakWithToday.isCheckedInToday)
        assertEquals(7, streakWithToday.last7Days.size)
        assertTrue(streakWithToday.last7Days.last().isToday)
        assertTrue(streakWithToday.last7Days.last().checkedIn)

        // Checked in yesterday only (not yet today) -> streak = 1
        val streakPendingToday = ShanbayMechanics.calculateStreak(
            dateKeysDesc = listOf("2026-11-23"),
            today = today,
        )
        assertEquals(1, streakPendingToday.currentStreak)
        assertFalse(streakPendingToday.isCheckedInToday)

        // Missed yesterday and today -> streak resets to 0
        val streakBroken = ShanbayMechanics.calculateStreak(
            dateKeysDesc = listOf("2026-11-22", "2026-11-21"),
            today = today,
        )
        assertEquals(0, streakBroken.currentStreak)
        assertEquals(2, streakBroken.totalCheckInDays)
    }
}
