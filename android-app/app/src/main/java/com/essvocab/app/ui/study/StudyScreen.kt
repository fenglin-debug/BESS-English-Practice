package com.essvocab.app.ui.study

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.SlowMotionVideo
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.essvocab.app.data.local.WordWithReview
import com.essvocab.app.domain.audio.TtsAvailability
import com.essvocab.app.domain.fsrs.FsrsRating
import com.essvocab.app.domain.fsrs.FsrsScheduleOutcome
import com.essvocab.app.domain.shanbay.ShanbayMechanics
import com.essvocab.app.ui.theme.GoogleBlue
import com.essvocab.app.ui.theme.GoogleBlueOnSoft
import com.essvocab.app.ui.theme.GoogleBlueSoft
import com.essvocab.app.ui.theme.GoogleFourOnSoftColors
import com.essvocab.app.ui.theme.GoogleFourSoftColors
import com.essvocab.app.ui.theme.GoogleGreen
import com.essvocab.app.ui.theme.GoogleGreenOnSoft
import com.essvocab.app.ui.theme.GoogleGreenSoft
import com.essvocab.app.ui.theme.GoogleRed
import com.essvocab.app.ui.theme.GoogleRedOnSoft
import com.essvocab.app.ui.theme.GoogleRedSoft
import com.essvocab.app.ui.theme.GoogleYellowOnSoft
import com.essvocab.app.ui.theme.GoogleYellowSoft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyScreen(
    onBack: () -> Unit,
    viewModel: StudyViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.feedbackMessage) {
        state.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissFeedbackMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text(state.sessionTitle, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "返回",
                        )
                    }
                },
                actions = {
                    if (state.canUndo) {
                        IconButton(onClick = viewModel::onUndoLastReview) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.Undo,
                                contentDescription = "撤回上一词",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (state.stage != StudyStage.COMPLETED && state.currentWord != null) {
                        IconButton(onClick = viewModel::onToggleFavorite) {
                            Icon(
                                imageVector = if (state.isFavorite) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                contentDescription = if (state.isFavorite) "移出生词本" else "加入生词本",
                                tint = if (state.isFavorite) GoogleYellowOnSoft else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        TextButton(
                            onClick = viewModel::onSlashTooEasy,
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCut,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("斩", fontWeight = FontWeight.Bold)
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

                state.stage == StudyStage.COMPLETED || state.currentWord == null -> {
                    ShanbaySessionCompleteView(
                        state = state,
                        onCheckIn = viewModel::onCompleteCheckIn,
                        onExtraRound = viewModel::onStartExtraRound,
                        onBack = onBack,
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
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(999.dp)),
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                shape = RoundedCornerShape(999.dp),
                            ) {
                                Text(
                                    text = "剩余 ${state.remainingCount}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                )
                            }
                        }

                        if (state.ttsAvailability == TtsAvailability.MISSING_ENGLISH_VOICE &&
                            wordItem.word.audioUrl.isNullOrBlank()
                        ) {
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                color = GoogleYellowSoft,
                                contentColor = GoogleYellowOnSoft,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = "系统缺少英文离线语音包，请在系统设置 -> 语音转文字中下载英文语音，即可启用离线朗读。",
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(10.dp),
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        ShanbayStudyCard(
                            item = wordItem,
                            stage = state.stage,
                            hintLevel = state.hintLevel,
                            hintText = state.hintText,
                            initialVerdict = state.initialVerdict,
                            ratingPreviews = state.ratingPreviews,
                            onSpeakNormal = { viewModel.speakCurrent(slowMode = false) },
                            onSpeakSlow = { viewModel.speakCurrent(slowMode = true) },
                            onSpeakExample = viewModel::speakExample,
                            modifier = Modifier.weight(1f),
                        )

                        Spacer(Modifier.height(14.dp))

                        ShanbayBottomActionBar(
                            stage = state.stage,
                            hintLevel = state.hintLevel,
                            maxHintLevel = state.maxHintLevel,
                            initialVerdict = state.initialVerdict,
                            ratingPreviews = state.ratingPreviews,
                            onKnown = viewModel::onChooseKnown,
                            onVague = viewModel::onChooseVague,
                            onConfirmHintRecalled = viewModel::onConfirmHintRecalled,
                            onUnknown = viewModel::onChooseUnknown,
                            onUndoKnown = viewModel::onUndoKnownToUnknown,
                            onProceedNext = viewModel::onProceedNext,
                        )

                        Spacer(Modifier.height(10.dp))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ShanbayStudyCard(
    item: WordWithReview,
    stage: StudyStage,
    hintLevel: Int,
    hintText: String,
    initialVerdict: InitialVerdict,
    ratingPreviews: Map<FsrsRating, FsrsScheduleOutcome>,
    onSpeakNormal: () -> Unit,
    onSpeakSlow: () -> Unit,
    onSpeakExample: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mastery = ShanbayMechanics.masteryLevel(item.card)
    val masteryLabel = ShanbayMechanics.masteryLabel(mastery)
    val targetRating = when (initialVerdict) {
        InitialVerdict.KNOWN -> FsrsRating.GOOD
        InitialVerdict.VAGUE -> FsrsRating.HARD
        InitialVerdict.UNKNOWN, InitialVerdict.NONE -> FsrsRating.AGAIN
    }
    val nextIntervalLabel = ratingPreviews[targetRating]?.intervalLabel ?: "-"

    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
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
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                    )
                }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(999.dp),
                ) {
                    Text(
                        text = masteryLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            Spacer(Modifier.height(22.dp))

            val abbrs = item.word.abbreviationsList()
            if (abbrs.isNotEmpty() && (stage == StudyStage.DETAIL || hintLevel > 0)) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    abbrs.forEachIndexed { idx, abbr ->
                        val bg = GoogleFourSoftColors[idx % GoogleFourSoftColors.size]
                        val fg = GoogleFourOnSoftColors[idx % GoogleFourOnSoftColors.size]
                        Surface(
                            color = bg,
                            shape = RoundedCornerShape(10.dp),
                        ) {
                            Text(
                                text = abbr,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = fg,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            Text(
                text = item.word.en,
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 27.sp, lineHeight = 35.sp),
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
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

            Spacer(Modifier.height(18.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
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

            AnimatedVisibility(visible = stage == StudyStage.RECOGNIZE && hintLevel > 0 && hintText.isNotBlank()) {
                Surface(
                    color = GoogleYellowSoft,
                    contentColor = GoogleYellowOnSoft,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = if (!item.word.exampleEn.isNullOrBlank()) "记忆提示 · 语境线索" else "记忆提示",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            if (!item.word.exampleEn.isNullOrBlank()) {
                                TextButton(
                                    onClick = onSpeakExample,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                        contentDescription = "听例句",
                                        tint = GoogleYellowOnSoft,
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "听例句",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = GoogleYellowOnSoft,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                        Text(
                            text = hintText,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            if (stage == StudyStage.DETAIL) {
                Spacer(Modifier.height(22.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(20.dp))

                Text(
                    text = item.word.zh,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(16.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ShanbayMasteryBars(level = mastery)
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(999.dp),
                    ) {
                        Text(
                            text = "下次复习：$nextIntervalLabel",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }

                if (!item.word.exampleEn.isNullOrBlank()) {
                    Spacer(Modifier.height(16.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = "工程例句",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                TextButton(
                                    onClick = onSpeakExample,
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                        contentDescription = "朗读例句",
                                        modifier = Modifier.size(15.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        text = "朗读例句",
                                        style = MaterialTheme.typography.labelMedium,
                                    )
                                }
                            }
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
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.60f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = "工程师注解 · 实战辨析",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = item.word.note,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            } else if (hintLevel == 0) {
                Spacer(Modifier.height(24.dp))
                Text(
                    text = "请先回想该储能术语的中文含义",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ShanbayMasteryBars(level: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (i in 1..5) {
            val active = i <= level
            Box(
                modifier = Modifier
                    .width(12.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        if (active) GoogleGreen else MaterialTheme.colorScheme.outlineVariant,
                    ),
            )
        }
    }
}

@Composable
private fun ShanbayBottomActionBar(
    stage: StudyStage,
    hintLevel: Int,
    maxHintLevel: Int,
    initialVerdict: InitialVerdict,
    ratingPreviews: Map<FsrsRating, FsrsScheduleOutcome>,
    onKnown: () -> Unit,
    onVague: () -> Unit,
    onConfirmHintRecalled: () -> Unit,
    onUnknown: () -> Unit,
    onUndoKnown: () -> Unit,
    onProceedNext: () -> Unit,
) {
    if (stage == StudyStage.RECOGNIZE) {
        if (hintLevel == 0) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ShanbayChoicePill(
                    title = "认识",
                    subtitle = ratingPreviews[FsrsRating.GOOD]?.intervalLabel ?: "",
                    containerColor = GoogleGreenSoft,
                    contentColor = GoogleGreenOnSoft,
                    onClick = onKnown,
                    modifier = Modifier.weight(1f),
                )
                ShanbayChoicePill(
                    title = "模糊",
                    subtitle = "先看提示",
                    containerColor = GoogleYellowSoft,
                    contentColor = GoogleYellowOnSoft,
                    onClick = onVague,
                    modifier = Modifier.weight(1f),
                )
                ShanbayChoicePill(
                    title = "不认识",
                    subtitle = "稍后复测",
                    containerColor = GoogleRedSoft,
                    contentColor = GoogleRedOnSoft,
                    onClick = onUnknown,
                    modifier = Modifier.weight(1f),
                )
            }
        } else if (hintLevel < maxHintLevel) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ShanbayChoicePill(
                    title = "想起来了",
                    subtitle = ratingPreviews[FsrsRating.HARD]?.intervalLabel ?: "",
                    containerColor = GoogleGreenSoft,
                    contentColor = GoogleGreenOnSoft,
                    onClick = onConfirmHintRecalled,
                    modifier = Modifier.weight(1f),
                )
                ShanbayChoicePill(
                    title = "首字提示",
                    subtitle = "再给提示",
                    containerColor = GoogleYellowSoft,
                    contentColor = GoogleYellowOnSoft,
                    onClick = onVague,
                    modifier = Modifier.weight(1f),
                )
                ShanbayChoicePill(
                    title = "不认识",
                    subtitle = "查看释义",
                    containerColor = GoogleRedSoft,
                    contentColor = GoogleRedOnSoft,
                    onClick = onUnknown,
                    modifier = Modifier.weight(1f),
                )
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ShanbayChoicePill(
                    title = "想起来了",
                    subtitle = ratingPreviews[FsrsRating.HARD]?.intervalLabel ?: "",
                    containerColor = GoogleGreenSoft,
                    contentColor = GoogleGreenOnSoft,
                    onClick = onConfirmHintRecalled,
                    modifier = Modifier.weight(1f),
                )
                ShanbayChoicePill(
                    title = "还是不认识",
                    subtitle = "查看释义",
                    containerColor = GoogleRedSoft,
                    contentColor = GoogleRedOnSoft,
                    onClick = onUnknown,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    } else if (stage == StudyStage.DETAIL) {
        when (initialVerdict) {
            InitialVerdict.KNOWN -> {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    OutlinedButton(
                        onClick = onUndoKnown,
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, GoogleRed.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = GoogleRed),
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                    ) {
                        Text("记错了", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = onProceedNext,
                        shape = RoundedCornerShape(999.dp),
                        modifier = Modifier
                            .weight(1.6f)
                            .height(52.dp),
                    ) {
                        Text("下一个", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            InitialVerdict.VAGUE -> {
                Button(
                    onClick = onProceedNext,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("继续学习（稍后再次巩固）", style = MaterialTheme.typography.titleMedium)
                }
            }

            InitialVerdict.UNKNOWN,
            InitialVerdict.NONE,
            -> {
                Button(
                    onClick = onProceedNext,
                    shape = RoundedCornerShape(999.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("记住这个释义，稍后复测", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun ShanbayChoicePill(
    title: String,
    subtitle: String,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
        ),
        shape = RoundedCornerShape(18.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 10.dp),
        modifier = modifier.height(58.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun ShanbaySessionCompleteView(
    state: StudyUiState,
    onCheckIn: () -> Unit,
    onExtraRound: () -> Unit,
    onBack: () -> Unit,
) {
    val accuracy = if (state.completedCount > 0) {
        state.goodOrEasyCount * 100 / state.completedCount
    } else {
        100
    }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(if (state.streakSummary.isCheckedInToday) GoogleGreenSoft else GoogleBlueSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (state.streakSummary.isCheckedInToday) Icons.Rounded.CheckCircle else Icons.Rounded.AutoAwesome,
                contentDescription = null,
                tint = if (state.streakSummary.isCheckedInToday) GoogleGreen else GoogleBlue,
                modifier = Modifier.size(38.dp),
            )
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = if (state.completedCount > 0) "本组学习已完成" else "今日学习任务已完成",
            style = MaterialTheme.typography.headlineMedium,
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = if (state.completedCount > 0) {
                "本轮完成 ${state.completedCount} 词评估（含斩词 ${state.slashedCount} 词），熟悉率 $accuracy%。"
            } else {
                "当前词书今日待新学与待复习词汇已全部清零，建议打个卡或加练一组巩固记忆。"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )

        Spacer(Modifier.height(16.dp))

        Surface(
            color = GoogleYellowSoft,
            contentColor = GoogleYellowOnSoft,
            shape = RoundedCornerShape(999.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "已连续打卡 ${state.streakSummary.currentStreak} 天 · 累计 ${state.streakSummary.totalCheckInDays} 天",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Button(
            onClick = onCheckIn,
            enabled = !state.streakSummary.isCheckedInToday,
            colors = ButtonDefaults.buttonColors(
                containerColor = if (state.streakSummary.isCheckedInToday) GoogleGreenSoft else GoogleBlue,
                contentColor = if (state.streakSummary.isCheckedInToday) GoogleGreenOnSoft else Color.White,
                disabledContainerColor = GoogleGreenSoft,
                disabledContentColor = GoogleGreenOnSoft,
            ),
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (state.streakSummary.isCheckedInToday) "今日已打卡 ✓" else "完成今日打卡",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        Spacer(Modifier.height(10.dp))

        FilledTonalButton(
            onClick = onExtraRound,
            shape = RoundedCornerShape(999.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Text("再来一组巩固 (10 词)")
        }

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = onBack) {
            Text("返回首页")
        }
    }
}
