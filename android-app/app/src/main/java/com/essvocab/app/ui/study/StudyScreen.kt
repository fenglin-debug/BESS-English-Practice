package com.essvocab.app.ui.study

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.essvocab.app.data.local.ReviewCardEntity
import com.essvocab.app.data.local.WordWithReview
import com.essvocab.app.domain.audio.TtsAvailability
import com.essvocab.app.domain.fsrs.FsrsRating
import com.essvocab.app.domain.fsrs.FsrsScheduleOutcome
import com.essvocab.app.ui.theme.EnergyAmber
import com.essvocab.app.ui.theme.EnergyBlue
import com.essvocab.app.ui.theme.EnergyRed
import com.essvocab.app.ui.theme.GridEmerald

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    onBack: () -> Unit,
    viewModel: StudyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.packTitle, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    if (!state.sessionFinished && !state.isLoading) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(999.dp),
                            modifier = Modifier.padding(end = 12.dp),
                        ) {
                            Text(
                                text = "剩余 ${state.remainingCount}",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when {
                state.isLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                state.sessionFinished || state.currentWord == null -> {
                    StudySessionCompleteView(
                        completedCount = state.completedCount,
                        goodOrEasyCount = state.goodOrEasyCount,
                        onBack = onBack,
                        onReload = viewModel::loadSession,
                    )
                }

                else -> {
                    val wordItem = state.currentWord!!
                    val total = (state.completedCount + state.remainingCount).coerceAtLeast(1)
                    val progress = state.completedCount.toFloat() / total.toFloat()

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp),
                        )

                        if (state.ttsAvailability == TtsAvailability.MISSING_ENGLISH_VOICE) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "系统缺少英文离线语音包，请在系统设置 -> 语音转文字中下载英文语音，以启用离线朗读。",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        AnimatedContent(
                            targetState = wordItem.word.id to state.isRevealed,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            modifier = Modifier.weight(1f),
                            label = "flashcard_transition",
                        ) { (_, revealed) ->
                            FlashcardBody(
                                item = wordItem,
                                isRevealed = revealed,
                                onSpeakNormal = { viewModel.speakCurrent(slowMode = false) },
                                onSpeakSlow = { viewModel.speakCurrent(slowMode = true) },
                                onCardTap = {
                                    if (!revealed) viewModel.revealAnswer()
                                },
                            )
                        }

                        Spacer(Modifier.height(16.dp))

                        if (!state.isRevealed) {
                            Button(
                                onClick = viewModel::revealAnswer,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp),
                                shape = RoundedCornerShape(16.dp),
                            ) {
                                Text(
                                    text = "显示中文术语与解析",
                                    style = MaterialTheme.typography.titleMedium,
                                )
                            }
                        } else {
                            FsrsRatingBar(
                                previews = state.ratingPreviews,
                                onRate = viewModel::submitRating,
                            )
                        }

                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlashcardBody(
    item: WordWithReview,
    isRevealed: Boolean,
    onSpeakNormal: () -> Unit,
    onSpeakSlow: () -> Unit,
    onCardTap: () -> Unit,
) {
    val cardStateLabel = when (item.card?.state) {
        null, ReviewCardEntity.CARD_STATE_NEW -> "新词"
        ReviewCardEntity.CARD_STATE_LEARNING -> "学习中"
        ReviewCardEntity.CARD_STATE_RELEARNING -> "重学巩固"
        else -> "间隔复习"
    }

    Card(
        onClick = onCardTap,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = item.packName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = cardStateLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            val abbrs = item.word.abbreviationsList()
            if (abbrs.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    abbrs.forEach { abbr ->
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = RoundedCornerShape(8.dp),
                        ) {
                            Text(
                                text = abbr,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Text(
                text = item.word.en,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 26.sp, lineHeight = 34.sp),
                textAlign = TextAlign.Center,
            )

            if (item.word.ipa.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "/ ${item.word.ipa} /",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(20.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = onSpeakNormal,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = "朗读",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("朗读")
                }

                OutlinedButton(
                    onClick = onSpeakSlow,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SlowMotionVideo,
                        contentDescription = "慢速跟读",
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("慢速 0.7x")
                }
            }

            if (isRevealed) {
                Spacer(Modifier.height(26.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
                Spacer(Modifier.height(22.dp))

                Text(
                    text = item.word.zh,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )

                if (!item.word.exampleEn.isNullOrBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = item.word.exampleEn,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            if (!item.word.exampleZh.isNullOrBlank()) {
                                Text(
                                    text = item.word.exampleZh,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (!item.word.note.isNullOrBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = item.word.note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                Spacer(Modifier.height(28.dp))
                Text(
                    text = "先在脑中回忆中文释义，再轻触卡片翻面",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                )
            }
        }
    }
}

@Composable
private fun FsrsRatingBar(
    previews: Map<FsrsRating, FsrsScheduleOutcome>,
    onRate: (FsrsRating) -> Unit,
) {
    val ratingColors = mapOf(
        FsrsRating.AGAIN to EnergyRed,
        FsrsRating.HARD to EnergyAmber,
        FsrsRating.GOOD to GridEmerald,
        FsrsRating.EASY to EnergyBlue,
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FsrsRating.entries.forEach { rating ->
            val accentColor = ratingColors[rating] ?: MaterialTheme.colorScheme.primary
            val intervalText = previews[rating]?.intervalLabel ?: "-"
            Button(
                onClick = { onRate(rating) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = accentColor.copy(alpha = 0.14f),
                    contentColor = accentColor,
                ),
                shape = RoundedCornerShape(14.dp),
                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = rating.labelZh,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = intervalText,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun StudySessionCompleteView(
    completedCount: Int,
    goodOrEasyCount: Int,
    onBack: () -> Unit,
    onReload: () -> Unit,
) {
    val accuracy = if (completedCount > 0) (goodOrEasyCount * 100 / completedCount) else 100
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = if (completedCount > 0) "本轮学习已完成！" else "当前暂无待复习卡片",
            style = MaterialTheme.typography.headlineMedium,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (completedCount > 0) {
                "共完成 $completedCount 次记忆评估，熟悉率 $accuracy%。FSRS 已为你安排下一次最佳复习时间。"
            } else {
                "所有已到期的储能词汇均已完成复习，你可以在设置中调高每日新词量，或稍后再来巩固。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("返回首页")
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = onReload,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text("检查下一批待学卡片")
        }
    }
}
