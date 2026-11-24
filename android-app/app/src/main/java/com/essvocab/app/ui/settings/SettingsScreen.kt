package com.essvocab.app.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.essvocab.app.data.preferences.AppSettings
import com.essvocab.app.data.preferences.TtsAccent
import com.essvocab.app.domain.audio.TtsAvailability
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var advancedExpanded by rememberSaveable { mutableStateOf(false) }

    if (state.showConsentDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConsentDialog,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.VerifiedUser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = { Text("开启自动联网更新") },
            text = {
                Text(
                    text = "本安装包不含内置语料。同意后，应用将自动联网为您拉取最新储能英语词库，并在后续启动时自动静默检查更新。",
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                Button(onClick = viewModel::confirmConsentAndContinue) {
                    Text("同意并立即同步")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConsentDialog) {
                    Text("取消")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("学习与同步设置") })
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item(key = "auto_update") {
                val versionLabel = if (state.settings.datasetVersion.isNotBlank()) {
                    "当前词库版本：v${state.settings.datasetVersion}"
                } else {
                    "当前尚未同步词库"
                }
                val timeLabel = if (state.settings.lastSyncAtMillis > 0L) {
                    " · 上次检查 " + SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                        .format(Date(state.settings.lastSyncAtMillis))
                } else {
                    ""
                }

                SectionCard(title = "云端词库自动更新") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("启动时自动检查并更新词库", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                text = "开启后自动静默增量同步最新术语，不丢失任何学习进度",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.settings.onlineSyncConsentGranted,
                            onCheckedChange = viewModel::setOnlineSyncConsent,
                        )
                    }

                    Text(
                        text = versionLabel + timeLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    OutlinedButton(
                        onClick = viewModel::checkUpdateNow,
                        enabled = !state.isBusy,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.isBusy) "正在检查更新..." else "立即检查更新")
                    }

                    state.statusMessage?.let { msg ->
                        Surface(
                            color = if (state.statusIsError) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.primaryContainer
                            },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (state.statusIsError) {
                                    MaterialTheme.colorScheme.onErrorContainer
                                } else {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                },
                                modifier = Modifier.padding(10.dp),
                            )
                        }
                    }
                }
            }

            item(key = "fsrs_settings") {
                SectionCard(title = "FSRS 间隔复习计划") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("每日新词上限", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            text = "${state.settings.dailyNewLimit} 词/天",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    Slider(
                        value = state.settings.dailyNewLimit.toFloat(),
                        onValueChange = { viewModel.updateDailyNewLimit((it / 5f).roundToInt() * 5) },
                        valueRange = 5f..60f,
                        steps = 10,
                    )

                    Text("目标记忆保持率（越高复习越密）", style = MaterialTheme.typography.bodyLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            0.85 to "85% 轻松",
                            0.90 to "90% 推荐",
                            0.95 to "95% 强化",
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = kotlin.math.abs(state.settings.desiredRetention - value) < 0.01,
                                onClick = { viewModel.updateDesiredRetention(value) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }

            item(key = "audio_settings") {
                SectionCard(title = "语音朗读 (离线 TTS / 音频)") {
                    val ttsStatusLabel = when (state.ttsAvailability) {
                        TtsAvailability.READY -> "离线英文 TTS 就绪"
                        TtsAvailability.INITIALIZING -> "TTS 初始化中..."
                        TtsAvailability.MISSING_ENGLISH_VOICE -> "缺少系统英文语音包（请到系统设置下载）"
                        TtsAvailability.UNAVAILABLE -> "当前设备未启用 TTS 引擎"
                    }
                    Text(
                        text = "引擎状态：$ttsStatusLabel",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("切换卡片自动朗读", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "进入下一张闪卡时自动播放英文发音",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.settings.autoPlayAudio,
                            onCheckedChange = viewModel::updateAutoPlayAudio,
                        )
                    }

                    Text("口音偏好", style = MaterialTheme.typography.bodyLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TtsAccent.entries.forEach { accent ->
                            FilterChip(
                                selected = state.settings.ttsAccent == accent,
                                onClick = { viewModel.updateTtsAccent(accent) },
                                label = { Text(accent.label) },
                            )
                        }
                    }

                    Text("默认语速", style = MaterialTheme.typography.bodyLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            0.75f to "0.75x 慢速",
                            1.0f to "1.0x 标准",
                            1.15f to "1.15x 稍快",
                        ).forEach { (rate, label) ->
                            FilterChip(
                                selected = kotlin.math.abs(state.settings.ttsSpeechRate - rate) < 0.05f,
                                onClick = { viewModel.updateTtsSpeechRate(rate) },
                                label = { Text(label) },
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = viewModel::testPronunciation,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text("试听：Battery energy storage system")
                    }
                }
            }

            item(key = "advanced_source") {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { advancedExpanded = !advancedExpanded },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "高级设置（自定义词库源）",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = "默认已自动接入官方云端词库，一般无需修改",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Icon(
                                imageVector = if (advancedExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = if (advancedExpanded) "收起" else "展开",
                            )
                        }

                        AnimatedVisibility(visible = advancedExpanded) {
                            Column(
                                modifier = Modifier.padding(top = 14.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))

                                OutlinedTextField(
                                    value = state.urlInput,
                                    onValueChange = viewModel::onUrlInputChange,
                                    label = { Text("Manifest URL") },
                                    singleLine = false,
                                    maxLines = 3,
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                )

                                FlowRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    AssistChip(
                                        onClick = { viewModel.applyPresetUrl(AppSettings.DEFAULT_MANIFEST_URL) },
                                        label = { Text("恢复官方 CDN 默认源") },
                                    )
                                    AssistChip(
                                        onClick = { viewModel.applyPresetUrl(AppSettings.GITHUB_RAW_MANIFEST_URL) },
                                        label = { Text("GitHub Raw 备用源") },
                                    )
                                    AssistChip(
                                        onClick = { viewModel.applyPresetUrl(AppSettings.EMULATOR_LOCAL_URL) },
                                        label = { Text("模拟器本地测试源") },
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = viewModel::testConnection,
                                        enabled = !state.isBusy,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("测试连接")
                                    }
                                    Button(
                                        onClick = { viewModel.saveUrlAndSync(forceFullCheck = false) },
                                        enabled = !state.isBusy,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("保存并同步")
                                    }
                                }

                                OutlinedButton(
                                    onClick = { viewModel.saveUrlAndSync(forceFullCheck = true) },
                                    enabled = !state.isBusy,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("强制全量重新拉取词库")
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            content()
        }
    }
}
