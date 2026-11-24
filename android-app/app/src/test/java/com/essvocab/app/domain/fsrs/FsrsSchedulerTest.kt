package com.essvocab.app.domain.fsrs

import com.essvocab.app.data.local.ReviewCardEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FsrsSchedulerTest {

    private val scheduler = FsrsScheduler()
    private val baseTime = 1_700_000_000_000L

    @Test
    fun newCardRatingsProduceOrderedIntervals() {
        val previews = scheduler.previewAll(
            wordId = "ess_bess",
            currentCard = null,
            nowMillis = baseTime,
            desiredRetention = 0.90,
        )

        val again = previews.getValue(FsrsRating.AGAIN)
        val hard = previews.getValue(FsrsRating.HARD)
        val good = previews.getValue(FsrsRating.GOOD)
        val easy = previews.getValue(FsrsRating.EASY)

        assertEquals(ReviewCardEntity.CARD_STATE_LEARNING, again.updatedCard.state)
        assertEquals("1分钟", again.intervalLabel)

        assertEquals(ReviewCardEntity.CARD_STATE_LEARNING, hard.updatedCard.state)
        assertEquals("10分钟", hard.intervalLabel)

        assertEquals(ReviewCardEntity.CARD_STATE_REVIEW, good.updatedCard.state)
        assertTrue(good.updatedCard.scheduledDays >= 1)

        assertEquals(ReviewCardEntity.CARD_STATE_REVIEW, easy.updatedCard.state)
        assertTrue(easy.updatedCard.scheduledDays > good.updatedCard.scheduledDays)
    }

    @Test
    fun repeatedGoodReviewsIncreaseStabilityAndInterval() {
        var card: ReviewCardEntity? = null
        var now = baseTime
        var prevDays = 0

        repeat(4) {
            val outcome = scheduler.review(
                wordId = "ess_pcs",
                currentCard = card,
                rating = FsrsRating.GOOD,
                nowMillis = now,
                desiredRetention = 0.90,
            )
            assertTrue(
                "Expected interval to grow: prev=$prevDays, curr=${outcome.updatedCard.scheduledDays}",
                outcome.updatedCard.scheduledDays > prevDays,
            )
            prevDays = outcome.updatedCard.scheduledDays
            card = outcome.updatedCard
            now += outcome.intervalMillis
        }

        assertEquals(4, card!!.reps)
        assertTrue(card!!.stability > 10.0)
    }

    @Test
    fun lapseOnReviewCardIncrementsLapsesAndReducesStability() {
        val first = scheduler.review("ess_bms", null, FsrsRating.EASY, baseTime)
        val later = baseTime + first.intervalMillis
        val forgotten = scheduler.review("ess_bms", first.updatedCard, FsrsRating.AGAIN, later)

        assertEquals(ReviewCardEntity.CARD_STATE_RELEARNING, forgotten.updatedCard.state)
        assertEquals(1, forgotten.updatedCard.lapses)
        assertTrue(forgotten.updatedCard.stability < first.updatedCard.stability)
        assertEquals("5分钟", forgotten.intervalLabel)
    }

    @Test
    fun formatIntervalOutputsReadableChineseLabels() {
        assertEquals("1分钟", FsrsScheduler.formatInterval(FsrsScheduler.MILLIS_PER_MINUTE))
        assertEquals("2小时", FsrsScheduler.formatInterval(2 * FsrsScheduler.MILLIS_PER_HOUR))
        assertEquals("7天", FsrsScheduler.formatInterval(7 * FsrsScheduler.MILLIS_PER_DAY))
        assertEquals("2.0个月", FsrsScheduler.formatInterval(60 * FsrsScheduler.MILLIS_PER_DAY))
        assertEquals("1.0年", FsrsScheduler.formatInterval(365 * FsrsScheduler.MILLIS_PER_DAY))
    }
}
