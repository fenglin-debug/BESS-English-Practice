package com.essvocab.app.ui.home

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.essvocab.app.data.local.PackSummaryRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onStartStudy: (packId: String?) -> Unit,
    onOpenPackDictionary: (packId: String) -> Unit,
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
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(26.dp),
                        )
                        Spacer(Modifier.width(6.dp))
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
                    } else if (state.totalWords > 0) {
                        IconButton(onClick = { viewModel.requestSync(forceFullCheck = false) }) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "检查词库更新",
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
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
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

            if (state.totalWords == 0) {
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
                item(key = "hero_study") {
                    HeroStudyDashboardCard(
                        state = state,
                        onStartStudy = { onStartStudy(null) },
                    )
                }

                item(key = "section_packs") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "储能英语专题 (${state.packs.size})",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = "点击专题开始闪卡",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                items(state.packs, key = { it.id }) { pack ->
                    PackCategoryCard(
                        pack = pack,
                        onStudyPack = { onStartStudy(pack.id) },
                        onBrowsePack = { onOpenPackDictionary(pack.id) },
                    )
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
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
                tint = MaterialTheme.colorScheme.primary,
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
                    text = "您的学习记录仅保存在本地设备，随时可在「设置」中关闭自动联网更新。",
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
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 36.dp),
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
private fun SyncMessageBanner(
    message: String,
    isError: Boolean,
    onDismiss: () -> Unit,
) {
    val containerColor = if (isError) {
        MaterialTheme.colorScheme.errorContainer
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }
    val contentColor = if (isError) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(14.dp),
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

@Composable
private fun EmptyVocabOnboardingCard(
    consentGranted: Boolean,
    onConsentAndSync: () -> Unit,
    onRetrySync: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudDownload,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(48.dp),
            )
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
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
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
private fun HeroStudyDashboardCard(
    state: HomeUiState,
    onStartStudy: () -> Unit,
) {
    val syncTimeLabel = if (state.settings.lastSyncAtMillis > 0L) {
        SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(state.settings.lastSyncAtMillis))
    } else {
        "已缓存"
    }
    val statusBadge = when {
        state.isSyncing -> "正在静默检查更新..."
        state.settings.onlineSyncConsentGranted && state.settings.datasetVersion.isNotBlank() ->
            "自动同步已开启 · v${state.settings.datasetVersion} ($syncTimeLabel)"

        state.settings.datasetVersion.isNotBlank() ->
            "离线模式 · v${state.settings.datasetVersion}"

        else -> "离线缓存模式"
    }

    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "今日 FSRS 复习计划",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = statusBadge,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                MetricItem(label = "待复习", value = state.dueCount.toString())
                MetricItem(label = "未学新词", value = state.unseenCount.toString())
                MetricItem(label = "今日已练", value = state.todayReviewedCount.toString())
                MetricItem(label = "已掌握", value = "${state.masteredCount}/${state.totalWords}")
            }

            Button(
                onClick = onStartStudy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (state.dueCount > 0) {
                        "开始复习（待复习 ${state.dueCount} 词）"
                    } else {
                        "开始 FSRS 闪卡学习"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
}

@Composable
private fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.78f),
        )
    }
}

@Composable
private fun PackCategoryCard(
    pack: PackSummaryRow,
    onStudyPack: () -> Unit,
    onBrowsePack: () -> Unit,
) {
    val progress = if (pack.totalWords > 0) {
        pack.learnedWords.toFloat() / pack.totalWords.toFloat()
    } else {
        0f
    }
    Card(
        onClick = onStudyPack,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = pack.name,
                            style = MaterialTheme.typography.titleMedium,
                        )
                        if (pack.dueWords > 0) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                shape = RoundedCornerShape(999.dp),
                            ) {
                                Text(
                                    text = "待复习 ${pack.dueWords}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    if (pack.description.isNotBlank()) {
                        Text(
                            text = pack.description,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onBrowsePack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.MenuBook,
                        contentDescription = "查看词表",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

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
                Text(
                    text = "${pack.learnedWords}/${pack.totalWords}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
