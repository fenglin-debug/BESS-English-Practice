package com.essvocab.app.domain.fsrs

import com.essvocab.app.data.local.ReviewCardEntity
import com.essvocab.app.data.local.ReviewLogEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

enum class FsrsRating(val value: Int, val labelZh: String) {
    AGAIN(1, "忘记"),
    HARD(2, "困难"),
    GOOD(3, "记得"),
    EASY(4, "简单"),
}

data class FsrsScheduleOutcome(
    val updatedCard: ReviewCardEntity,
    val reviewLog: ReviewLogEntity,
    val intervalMillis: Long,
    val intervalLabel: String,
)

/**
 * Pure-Kotlin implementation of the open-spaced-repetition FSRS-5 algorithm.
 * Reference: https://github.com/open-spaced-repetition/fsrs4anki/wiki/The-Algorithm
 */
@Singleton
class FsrsScheduler @Inject constructor() {

    private val weights = DEFAULT_FSRS5_WEIGHTS

    fun previewAll(
        wordId: String,
        currentCard: ReviewCardEntity?,
        nowMillis: Long,
        desiredRetention: Double = 0.90,
    ): Map<FsrsRating, FsrsScheduleOutcome> {
        return FsrsRating.entries.associateWith { rating ->
            review(wordId, currentCard, rating, nowMillis, desiredRetention)
        }
    }

    fun review(
        wordId: String,
        currentCard: ReviewCardEntity?,
        rating: FsrsRating,
        nowMillis: Long,
        desiredRetention: Double = 0.90,
    ): FsrsScheduleOutcome {
        val card = currentCard ?: ReviewCardEntity(wordId = wordId, dueAtMillis = nowMillis)
        val retention = desiredRetention.coerceIn(0.80, 0.97)
        val elapsedDays = if (card.lastReviewAtMillis != null && card.lastReviewAtMillis > 0L) {
            max(0, ((nowMillis - card.lastReviewAtMillis) / MILLIS_PER_DAY).toInt())
        } else {
            0
        }

        val stateBefore = card.state
        val nextCard: ReviewCardEntity
        val intervalMillis: Long

        when (card.state) {
            ReviewCardEntity.CARD_STATE_NEW -> {
                val initS = initStability(rating)
                val initD = initDifficulty(rating)
                when (rating) {
                    FsrsRating.AGAIN -> {
                        intervalMillis = 1 * MILLIS_PER_MINUTE
                        nextCard = card.copy(
                            state = ReviewCardEntity.CARD_STATE_LEARNING,
                            stability = initS,
                            difficulty = initD,
                            elapsedDays = 0,
                            scheduledDays = 0,
                            reps = card.reps + 1,
                            lapses = card.lapses,
                            dueAtMillis = nowMillis + intervalMillis,
                            lastReviewAtMillis = nowMillis,
                        )
                    }

                    FsrsRating.HARD -> {
                        intervalMillis = 10 * MILLIS_PER_MINUTE
                        nextCard = card.copy(
                            state = ReviewCardEntity.CARD_STATE_LEARNING,
                            stability = initS,
                            difficulty = initD,
                            elapsedDays = 0,
                            scheduledDays = 0,
                            reps = card.reps + 1,
                            lapses = card.lapses,
                            dueAtMillis = nowMillis + intervalMillis,
                            lastReviewAtMillis = nowMillis,
                        )
                    }

                    FsrsRating.GOOD -> {
                        val days = nextInterval(initS, retention)
                        intervalMillis = days * MILLIS_PER_DAY
                        nextCard = card.copy(
                            state = ReviewCardEntity.CARD_STATE_REVIEW,
                            stability = initS,
                            difficulty = initD,
                            elapsedDays = 0,
                            scheduledDays = days,
                            reps = card.reps + 1,
                            lapses = card.lapses,
                            dueAtMillis = nowMillis + intervalMillis,
                            lastReviewAtMillis = nowMillis,
                        )
                    }

                    FsrsRating.EASY -> {
                        val goodDays = nextInterval(initStability(FsrsRating.GOOD), retention)
                        val days = max(nextInterval(initS, retention), goodDays + 1)
                        intervalMillis = days * MILLIS_PER_DAY
                        nextCard = card.copy(
                            state = ReviewCardEntity.CARD_STATE_REVIEW,
                            stability = initS,
                            difficulty = initD,
                            elapsedDays = 0,
                            scheduledDays = days,
                            reps = card.reps + 1,
                            lapses = card.lapses,
                            dueAtMillis = nowMillis + intervalMillis,
                            lastReviewAtMillis = nowMillis,
                        )
                    }
                }
            }

            ReviewCardEntity.CARD_STATE_LEARNING,
            ReviewCardEntity.CARD_STATE_RELEARNING,
            -> {
                val safeS = max(card.stability, 0.1)
                val safeD = card.difficulty.coerceIn(1.0, 10.0)
                val newS = shortTermStability(safeS, rating)
                val newD = nextDifficulty(safeD, rating)

                when (rating) {
                    FsrsRating.AGAIN -> {
                        intervalMillis = 1 * MILLIS_PER_MINUTE
                        nextCard = card.copy(
                            state = card.state,
                            stability = newS,
                            difficulty = newD,
                            elapsedDays = elapsedDays,
                            scheduledDays = 0,
                            reps = card.reps + 1,
                            dueAtMillis = nowMillis + intervalMillis,
                            lastReviewAtMillis = nowMillis,
                        )
                    }

                    FsrsRating.HARD -> {
                        intervalMillis = 10 * MILLIS_PER_MINUTE
                        nextCard = card.copy(
                            state = card.state,
                            stability = newS,
                            difficulty = newD,
                            elapsedDays = elapsedDays,
                            scheduledDays = 0,
                            reps = card.reps + 1,
                            dueAtMillis = nowMillis + intervalMillis,
                            lastReviewAtMillis = nowMillis,
                        )
                    }

                    FsrsRating.GOOD -> {
                        val days = nextInterval(newS, retention)
                        intervalMillis = days * MILLIS_PER_DAY
                        nextCard = card.copy(
                            state = ReviewCardEntity.CARD_STATE_REVIEW,
                            stability = newS,
                            difficulty = newD,
                            elapsedDays = elapsedDays,
                            scheduledDays = days,
                            reps = card.reps + 1,
                            dueAtMillis = nowMillis + intervalMillis,
                            lastReviewAtMillis = nowMillis,
                        )
                    }

                    FsrsRating.EASY -> {
                        val goodDays = nextInterval(shortTermStability(safeS, FsrsRating.GOOD), retention)
                        val days = max(nextInterval(newS, retention), goodDays + 1)
                        intervalMillis = days * MILLIS_PER_DAY
                        nextCard = card.copy(
                            state = ReviewCardEntity.CARD_STATE_REVIEW,
                            stability = newS,
                            difficulty = newD,
                            elapsedDays = elapsedDays,
                            scheduledDays = days,
                            reps = card.reps + 1,
                            dueAtMillis = nowMillis + intervalMillis,
                            lastReviewAtMillis = nowMillis,
                        )
                    }
                }
            }

            else -> {
                // CARD_STATE_REVIEW
                val safeS = max(card.stability, 0.1)
                val safeD = card.difficulty.coerceIn(1.0, 10.0)
                val retrievability = forgettingCurve(elapsedDays.toDouble(), safeS)
                val newD = nextDifficulty(safeD, rating)

                when (rating) {
                    FsrsRating.AGAIN -> {
                        val forgetS = nextForgetStability(safeD, safeS, retrievability)
                        intervalMillis = 5 * MILLIS_PER_MINUTE
                        nextCard = card.copy(
                            state = ReviewCardEntity.CARD_STATE_RELEARNING,
                            stability = forgetS,
                            difficulty = newD,
                            elapsedDays = elapsedDays,
                            scheduledDays = 0,
                            reps = card.reps + 1,
                            lapses = card.lapses + 1,
                            dueAtMillis = nowMillis + intervalMillis,
                            lastReviewAtMillis = nowMillis,
                        )
                    }

                    FsrsRating.HARD, FsrsRating.GOOD, FsrsRating.EASY -> {
                        val hardS = nextRecallStability(safeD, safeS, retrievability, FsrsRating.HARD)
                        val goodS = nextRecallStability(safeD, safeS, retrievability, FsrsRating.GOOD)
                        val easyS = nextRecallStability(safeD, safeS, retrievability, FsrsRating.EASY)

                        var hardDays = nextInterval(hardS, retention)
                        var goodDays = nextInterval(goodS, retention)
                        var easyDays = nextInterval(easyS, retention)

                        hardDays = min(hardDays, goodDays)
                        goodDays = max(goodDays, hardDays + 1)
                        easyDays = max(easyDays, goodDays + 1)

                        val (chosenS, chosenDays) = when (rating) {
                            FsrsRating.HARD -> hardS to hardDays
                            FsrsRating.GOOD -> goodS to goodDays
                            FsrsRating.EASY -> easyS to easyDays
                            else -> goodS to goodDays
                        }

                        intervalMillis = chosenDays * MILLIS_PER_DAY
                        nextCard = card.copy(
                            state = ReviewCardEntity.CARD_STATE_REVIEW,
                            stability = chosenS,
                            difficulty = newD,
                            elapsedDays = elapsedDays,
                            scheduledDays = chosenDays,
                            reps = card.reps + 1,
                            dueAtMillis = nowMillis + intervalMillis,
                            lastReviewAtMillis = nowMillis,
                        )
                    }
                }
            }
        }

        val log = ReviewLogEntity(
            wordId = wordId,
            rating = rating.value,
            stateBefore = stateBefore,
            stabilityAfter = nextCard.stability,
            difficultyAfter = nextCard.difficulty,
            scheduledDays = nextCard.scheduledDays,
            reviewedAtMillis = nowMillis,
        )

        return FsrsScheduleOutcome(
            updatedCard = nextCard,
            reviewLog = log,
            intervalMillis = intervalMillis,
            intervalLabel = formatInterval(intervalMillis),
        )
    }

    private fun initStability(rating: FsrsRating): Double =
        max(weights[rating.value - 1], 0.1)

    private fun initDifficulty(rating: FsrsRating): Double =
        constrainDifficulty(weights[4] - exp(weights[5] * (rating.value - 1)) + 1.0)

    private fun nextDifficulty(d: Double, rating: FsrsRating): Double {
        val deltaD = -weights[6] * (rating.value - 3)
        val damped = d + deltaD * (10.0 - d) / 9.0
        val meanReverted = weights[7] * initDifficulty(FsrsRating.EASY) + (1.0 - weights[7]) * damped
        return constrainDifficulty(meanReverted)
    }

    private fun shortTermStability(s: Double, rating: FsrsRating): Double =
        max(s * exp(weights[17] * (rating.value - 3 + weights[18])), 0.1)

    private fun forgettingCurve(elapsedDays: Double, stability: Double): Double =
        (1.0 + FACTOR * elapsedDays / stability).pow(DECAY)

    private fun nextRecallStability(
        d: Double,
        s: Double,
        r: Double,
        rating: FsrsRating,
    ): Double {
        val hardPenalty = if (rating == FsrsRating.HARD) weights[15] else 1.0
        val easyBonus = if (rating == FsrsRating.EASY) weights[16] else 1.0
        val factor = exp(weights[8]) *
            (11.0 - d) *
            s.pow(-weights[9]) *
            (exp(weights[10] * (1.0 - r)) - 1.0) *
            hardPenalty *
            easyBonus + 1.0
        return max(s * factor, 0.1)
    }

    private fun nextForgetStability(d: Double, s: Double, r: Double): Double {
        val raw = weights[11] *
            d.pow(-weights[12]) *
            ((s + 1.0).pow(weights[13]) - 1.0) *
            exp(weights[14] * (1.0 - r))
        return max(min(raw, s), 0.1)
    }

    private fun nextInterval(stability: Double, desiredRetention: Double): Int {
        val rawInterval = (stability / FACTOR) * (desiredRetention.pow(1.0 / DECAY) - 1.0)
        return rawInterval.roundToInt().coerceIn(1, MAX_INTERVAL_DAYS)
    }

    private fun constrainDifficulty(d: Double): Double = d.coerceIn(1.0, 10.0)

    companion object {
        private const val DECAY = -0.5
        private const val FACTOR = 19.0 / 81.0
        private const val MAX_INTERVAL_DAYS = 36500
        const val MILLIS_PER_MINUTE = 60_000L
        const val MILLIS_PER_HOUR = 3_600_000L
        const val MILLIS_PER_DAY = 86_400_000L

        // Official FSRS-5 default parameters (19 weights)
        private val DEFAULT_FSRS5_WEIGHTS = doubleArrayOf(
            0.40255, 1.18385, 3.173, 15.69105,
            7.1949, 0.5345, 1.4604, 0.0046,
            1.54575, 0.1192, 1.01925, 1.9395,
            0.11, 0.29605, 2.2698, 0.2315,
            2.9898, 0.51655, 0.6621,
        )

        fun formatInterval(intervalMillis: Long): String {
            return when {
                intervalMillis < MILLIS_PER_HOUR -> {
                    val mins = max(1L, intervalMillis / MILLIS_PER_MINUTE)
                    "${mins}分钟"
                }

                intervalMillis < MILLIS_PER_DAY -> {
                    val hours = max(1L, intervalMillis / MILLIS_PER_HOUR)
                    "${hours}小时"
                }

                intervalMillis < 30 * MILLIS_PER_DAY -> {
                    val days = max(1L, intervalMillis / MILLIS_PER_DAY)
                    "${days}天"
                }

                intervalMillis < 365 * MILLIS_PER_DAY -> {
                    val months = intervalMillis.toDouble() / (30.0 * MILLIS_PER_DAY)
                    if (months >= 10.0) "${months.roundToInt()}个月" else String.format("%.1f个月", months)
                }

                else -> {
                    val years = intervalMillis.toDouble() / (365.0 * MILLIS_PER_DAY)
                    String.format("%.1f年", years)
                }
            }
        }
    }
}
