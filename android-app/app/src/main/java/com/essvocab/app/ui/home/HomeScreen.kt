package com.essvocab.app.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.essvocab.app.domain.shanbay.StreakSummary
import com.essvocab.app.ui.theme.GoogleBlue
import com.essvocab.app.ui.theme.GoogleBlueOnSoft
import com.essvocab.app.ui.theme.GoogleBlueSoft
import com.essvocab.app.ui.theme.GoogleFourColors
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
fun HomeScreen(
    onStartBookStudy: () -> Unit,
    onStartExtraStudy: () -> Unit,
    onSwitchBook: () -> Unit,
    onOpenDictionarySearch: () -> Unit,
    onOpenNotebook: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    if (state.showConsentDialog) {
        OnlineSyncConsentDialog(
            onConfirm = { viewModel.confirmConsentAndSync() },
            onDismiss = viewModel::declineConsentDialog,
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        GoogleFourDotMark()
                        Text(
                            text = "储能英语实战",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                actions = {
                    if (state.isSyncing) {
                        Box(
                            modifier = Modifier.padding(end = 16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.5.dp,
                            )
                        }
                    } else if (state.totalActiveWords > 0) {
                        IconButton(onClick = { viewModel.requestSync(forceFullCheck = false) }) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "检查词库更新",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            state.syncBannerMessage?.let { msg ->
                item(key = "sync_banner") {
                    SyncMessageBanner(
                        message = msg,
                        isError = state.syncBannerIsError,
                        onDismiss = viewModel::dismissBanner,
                    )
                }
            }

            if (state.totalActiveWords == 0) {
                item(key = "empty_state") {
                    if (state.isSyncing) {
                        InitialSyncProgressCard()
                    } else {
                        EmptyVocabOnboardingCard(
                            consentGranted = state.settings.onlineSyncConsentGranted,
                            onConsentAndSync = { viewModel.confirmConsentAndSync(forceFullCheck = true) },
                            onRetrySync = { viewModel.requestSync(forceFullCheck = true) },
                        )
                    }
                }
            } else {
                item(key = "streak_row") {
                    StreakStatusPillRow(
                        streak = state.streakSummary,
                        version = state.settings.datasetVersion,
                    )
                }

                item(key = "active_book") {
                    ActiveVocabBookCard(
                        bookName = state.selectedBookName,
                        mastered = state.bookMasteredWords,
                        total = state.bookTotalWords,
                        estimatedDays = state.estimatedDaysLeft,
                        onSwitchBook = onSwitchBook,
                    )
                }

                item(key = "daily_task") {
                    ShanbayDailyTaskCard(
                        newRemaining = state.todayNewRemaining,
                        reviewRemaining = state.todayReviewRemaining,
                        dailyReviewLimit = state.settings.dailyReviewLimit,
                        overdueBacklogCount = state.overdueBacklogCount,
                        onStartBookStudy = onStartBookStudy,
                        onStartExtraStudy = onStartExtraStudy,
                    )
                }

                item(key = "quick_actions") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        QuickShortcutCard(
                            title = "生词本",
                            subtitle = "${state.notebookCount} 个重点词",
                            icon = Icons.Rounded.Bookmarks,
                            iconBg = GoogleRedSoft,
                            iconTint = GoogleRedOnSoft,
                            onClick = onOpenNotebook,
                            modifier = Modifier.weight(1f),
                        )
                        QuickShortcutCard(
                            title = "词典快查",
                            subtitle = "${state.totalActiveWords} 条储能术语",
                            icon = Icons.Rounded.Search,
                            iconBg = GoogleBlueSoft,
                            iconTint = GoogleBlueOnSoft,
                            onClick = onOpenDictionarySearch,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }

                item(key = "checkin_calendar") {
                    SevenDayCheckInStrip(streak = state.streakSummary)
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun GoogleFourDotMark() {
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        GoogleFourColors.forEach { color ->
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

@Composable
private fun StreakStatusPillRow(
    streak: StreakSummary,
    version: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            color = GoogleYellowSoft,
            contentColor = GoogleYellowOnSoft,
            shape = RoundedCornerShape(999.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.LocalFireDepartment,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "连续打卡 ${streak.currentStreak} 天",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        Surface(
            color = if (streak.isCheckedInToday) GoogleGreenSoft else GoogleBlueSoft,
            contentColor = if (streak.isCheckedInToday) GoogleGreenOnSoft else GoogleBlueOnSoft,
            shape = RoundedCornerShape(999.dp),
        ) {
            Text(
                text = if (streak.isCheckedInToday) {
                    "今日已打卡 ✓"
                } else if (version.isNotBlank()) {
                    "词库 v$version"
                } else {
                    "今日待打卡"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun ActiveVocabBookCard(
    bookName: String,
    mastered: Int,
    total: Int,
    estimatedDays: Int,
    onSwitchBook: () -> Unit,
) {
    val progress = if (total > 0) mastered.toFloat() / total.toFloat() else 0f
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(GoogleBlueSoft),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoStories,
                            contentDescription = null,
                            tint = GoogleBlue,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Column {
                        Text(
                            text = "在背词书",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = bookName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }

                FilledTonalButton(
                    onClick = onSwitchBook,
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SwapHoriz,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("换书", style = MaterialTheme.typography.labelMedium)
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = GoogleGreen,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "已掌握 $mastered / $total 词",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = if (estimatedDays == 0) "本书已全部掌握" else "预计还需 $estimatedDays 天",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ShanbayDailyTaskCard(
    newRemaining: Int,
    reviewRemaining: Int,
    dailyReviewLimit: Int,
    overdueBacklogCount: Int,
    onStartBookStudy: () -> Unit,
    onStartExtraStudy: () -> Unit,
) {
    val hasDailyTasks = (newRemaining + reviewRemaining) > 0
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                text = "今日学习任务",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = newRemaining.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        color = GoogleBlue,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "今日新学",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                VerticalDivider(
                    modifier = Modifier.height(48.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = reviewRemaining.toString(),
                        style = MaterialTheme.typography.displaySmall,
                        color = GoogleGreen,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "今日复习",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (overdueBacklogCount > 0) {
                Surface(
                    color = GoogleYellowSoft,
                    contentColor = GoogleYellowOnSoft,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = "已按每日上限 $dailyReviewLimit 词分流，另有 $overdueBacklogCount 词顺延复习",
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }

            Button(
                onClick = if (hasDailyTasks) onStartBookStudy else onStartExtraStudy,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (hasDailyTasks) "开始学习" else "今日已完成 · 再来一组巩固",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun QuickShortcutCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconBg: Color,
    iconTint: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SevenDayCheckInStrip(streak: StreakSummary) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "近 7 日打卡足迹",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = "累计 ${streak.totalCheckInDays} 天",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                streak.last7Days.forEach { day ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        day.checkedIn -> GoogleGreenSoft
                                        day.isToday -> GoogleBlueSoft
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (day.checkedIn) {
                                Icon(
                                    imageVector = Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = GoogleGreen,
                                    modifier = Modifier.size(18.dp),
                                )
                            } else {
                                Text(
                                    text = day.date.dayOfMonth.toString(),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (day.isToday) GoogleBlue else MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal,
                                )
                            }
                        }
                        Text(
                            text = if (day.isToday) "今" else day.weekdayShort,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (day.isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun OnlineSyncConsentDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.VerifiedUser,
                contentDescription = null,
                tint = GoogleBlue,
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = "欢迎使用储能英语实战",
                textAlign = TextAlign.Center,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "为保持安装包轻巧并持续同步最新储能行业术语，本应用不内置固定词库语料。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "点击同意后，应用将自动联网为您下载最新储能专业词汇并缓存在本地；后续每次启动会自动静默增量更新，无网络时也可正常离线复习。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "您的学习记录仅保存在本地设备，随时可在「我的」中关闭自动联网更新。",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("同意并自动更新")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("暂不联网")
            }
        },
    )
}

@Composable
private fun InitialSyncProgressCard() {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 38.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(44.dp),
                strokeWidth = 3.5.dp,
            )
            Text(
                text = "正在自动获取最新储能词库...",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "首次同步完成后将缓存在本地，后续离线也能流畅学习与跟读。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun EmptyVocabOnboardingCard(
    consentGranted: Boolean,
    onConsentAndSync: () -> Unit,
    onRetrySync: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(GoogleBlueSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CloudDownload,
                    contentDescription = null,
                    tint = GoogleBlue,
                    modifier = Modifier.size(30.dp),
                )
            }
            Text(
                text = if (consentGranted) "词库同步未完成" else "开启储能英语实战之旅",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Text(
                text = if (consentGranted) {
                    "请检查手机网络连接后点击重试，同步完成后即可离线学习全部储能专业词汇。"
                } else {
                    "本安装包不含内置语料。点击下方按钮同意后，应用将自动为您联网更新最新储能专业词库，无需手动填写任何配置。"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Button(
                onClick = if (consentGranted) onRetrySync else onConsentAndSync,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Icon(Icons.Rounded.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (consentGranted) "重新自动获取词库" else "同意联网并自动更新词库",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun SyncMessageBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit,
) {
    val containerColor = if (isError) GoogleRedSoft else GoogleGreenSoft
    val contentColor = if (isError) GoogleRedOnSoft else GoogleGreenOnSoft
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "关闭提示",
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}
