package com.essvocab.app.ui.dictionary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SecondaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.essvocab.app.data.local.WordWithReview
import com.essvocab.app.domain.shanbay.ShanbayMechanics
import com.essvocab.app.ui.study.ShanbayMasteryBars
import com.essvocab.app.ui.theme.GoogleBlue
import com.essvocab.app.ui.theme.GoogleBlueOnSoft
import com.essvocab.app.ui.theme.GoogleBlueSoft
import com.essvocab.app.ui.theme.GoogleFourOnSoftColors
import com.essvocab.app.ui.theme.GoogleFourSoftColors
import com.essvocab.app.ui.theme.GoogleGreen
import com.essvocab.app.ui.theme.GoogleGreenOnSoft
import com.essvocab.app.ui.theme.GoogleGreenSoft
import com.essvocab.app.ui.theme.GoogleRedOnSoft
import com.essvocab.app.ui.theme.GoogleRedSoft
import com.essvocab.app.ui.theme.GoogleYellowOnSoft

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DictionaryScreen(
    initialTab: Int = 0,
    requestNonce: Long = 0L,
    onStartPackStudy: (packId: String?) -> Unit,
    onStartNotebookStudy: () -> Unit,
    viewModel: DictionaryViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, 2)) }

    LaunchedEffect(initialTab, requestNonce) {
        if (requestNonce > 0L) {
            selectedTab = initialTab.coerceIn(0, 2)
        }
    }

    val tabTitles = listOf("选词书", "查术语", "生词本 (${state.notebookWords.size})")

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = { Text("储能词库中心", fontWeight = FontWeight.Bold) },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            SecondaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.background,
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = {
                            Text(
                                text = title,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Medium,
                            )
                        },
                    )
                }
            }

            when (selectedTab) {
                0 -> BooksSelectionTab(
                    state = state,
                    onSelectBook = viewModel::selectActiveBook,
                    onStudyBook = onStartPackStudy,
                )

                1 -> TermSearchTab(
                    state = state,
                    onQueryChange = viewModel::onQueryChange,
                    onSelectFilterPack = viewModel::selectFilterPack,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onRestoreMastered = viewModel::restoreFromMastered,
                    onSpeakWord = viewModel::speakWord,
                )

                else -> NotebookTab(
                    words = state.notebookWords,
                    onStartNotebookStudy = onStartNotebookStudy,
                    onToggleFavorite = viewModel::toggleFavorite,
                    onRestoreMastered = viewModel::restoreFromMastered,
                    onSpeakWord = viewModel::speakWord,
                )
            }
        }
    }
}

@Composable
private fun BooksSelectionTab(
    state: DictionaryUiState,
    onSelectBook: (String?) -> Unit,
    onStudyBook: (String?) -> Unit,
) {
    if (state.packs.isEmpty()) {
        EmptyStateMessage("尚未同步词库，请回到「学习」页授权联网拉取词库")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "full_book") {
            VocabBookItemCard(
                title = "储能行业全套词汇",
                description = "涵盖全部 8 大领域核心储能术语、缩写与音标",
                learned = state.totalMasteredWords,
                total = state.totalActiveWords,
                isActiveBook = state.activeBookPackId.isBlank(),
                badgeBg = GoogleBlueSoft,
                badgeTint = GoogleBlueOnSoft,
                onSetActive = { onSelectBook(null) },
                onStartStudy = { onStudyBook(null) },
            )
        }

        itemsIndexed(state.packs, key = { _, p -> p.id }) { index, pack ->
            val bg = GoogleFourSoftColors[index % GoogleFourSoftColors.size]
            val tint = GoogleFourOnSoftColors[index % GoogleFourOnSoftColors.size]
            VocabBookItemCard(
                title = pack.name,
                description = pack.description.ifBlank { "${pack.totalWords} 条行业术语" },
                learned = pack.learnedWords,
                total = pack.totalWords,
                isActiveBook = state.activeBookPackId == pack.id,
                badgeBg = bg,
                badgeTint = tint,
                onSetActive = { onSelectBook(pack.id) },
                onStartStudy = { onStudyBook(pack.id) },
            )
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun VocabBookItemCard(
    title: String,
    description: String,
    learned: Int,
    total: Int,
    isActiveBook: Boolean,
    badgeBg: Color,
    badgeTint: Color,
    onSetActive: () -> Unit,
    onStartStudy: () -> Unit,
) {
    val progress = if (total > 0) learned.toFloat() / total.toFloat() else 0f
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = if (isActiveBook) 1.5.dp else 1.dp,
            color = if (isActiveBook) GoogleBlue else MaterialTheme.colorScheme.outline,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(badgeBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoStories,
                        contentDescription = null,
                        tint = badgeTint,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        if (isActiveBook) {
                            Surface(
                                color = GoogleGreenSoft,
                                shape = RoundedCornerShape(999.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CheckCircle,
                                        contentDescription = null,
                                        tint = GoogleGreen,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Text(
                                        text = "在背",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = GoogleGreenOnSoft,
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
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
                    color = GoogleGreen,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text(
                    text = "$learned / $total 词",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (!isActiveBook) {
                    TextButton(onClick = onSetActive) {
                        Text("设为在背")
                    }
                    Spacer(Modifier.width(4.dp))
                }
                FilledTonalButton(
                    onClick = onStartStudy,
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("直接开练", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun TermSearchTab(
    state: DictionaryUiState,
    onQueryChange: (String) -> Unit,
    onSelectFilterPack: (String?) -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRestoreMastered: (String) -> Unit,
    onSpeakWord: (WordWithReview) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        TextField(
            value = state.query,
            onValueChange = onQueryChange,
            placeholder = { Text("搜索中英文或缩写，如 BESS、PCS、SOC、调频...") },
            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
            trailingIcon = {
                if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Rounded.Clear, contentDescription = "清空")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )

        if (state.packs.isNotEmpty()) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "all") {
                    FilterChip(
                        selected = state.selectedFilterPackId == null,
                        onClick = { onSelectFilterPack(null) },
                        label = { Text("全部 (${state.totalActiveWords})") },
                    )
                }
                items(state.packs, key = { it.id }) { pack ->
                    FilterChip(
                        selected = state.selectedFilterPackId == pack.id,
                        onClick = { onSelectFilterPack(pack.id) },
                        label = { Text(pack.name) },
                    )
                }
            }
        }

        if (state.words.isEmpty()) {
            EmptyStateMessage(
                if (state.packs.isEmpty()) "尚未拉取词库，请先在首页同步词库" else "未找到匹配的储能术语",
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.words, key = { it.word.id }) { item ->
                    DictionaryWordCard(
                        item = item,
                        onToggleFavorite = { onToggleFavorite(item.word.id) },
                        onRestoreMastered = { onRestoreMastered(item.word.id) },
                        onSpeak = { onSpeakWord(item) },
                    )
                }
                item { Spacer(Modifier.height(12.dp)) }
            }
        }
    }
}

@Composable
private fun NotebookTab(
    words: List<WordWithReview>,
    onStartNotebookStudy: () -> Unit,
    onToggleFavorite: (String) -> Unit,
    onRestoreMastered: (String) -> Unit,
    onSpeakWord: (WordWithReview) -> Unit,
) {
    if (words.isEmpty()) {
        EmptyStateMessage("生词本暂无词汇。\n学习时点击右上角星标，或答错的重点词会自动收录到这里。")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "notebook_cta") {
            Button(
                onClick = onStartNotebookStudy,
                shape = RoundedCornerShape(999.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "专项攻克生词本 (${words.size} 词)",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Spacer(Modifier.height(6.dp))
        }

        items(words, key = { it.word.id }) { item ->
            DictionaryWordCard(
                item = item,
                onToggleFavorite = { onToggleFavorite(item.word.id) },
                onRestoreMastered = { onRestoreMastered(item.word.id) },
                onSpeak = { onSpeakWord(item) },
            )
        }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun DictionaryWordCard(
    item: WordWithReview,
    onToggleFavorite: () -> Unit,
    onRestoreMastered: () -> Unit,
    onSpeak: () -> Unit,
) {
    val mastery = ShanbayMechanics.masteryLevel(item.card)
    val isFav = item.card?.isFavorite == true
    val isBypassed = item.card?.isMasteredBypass == true
    val lapses = item.card?.lapses ?: 0
    val hasDetails = !item.word.exampleEn.isNullOrBlank() || !item.word.note.isNullOrBlank()
    var expanded by rememberSaveable(item.word.id) { mutableStateOf(false) }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .let { mod ->
                    if (hasDetails) mod.clickable { expanded = !expanded } else mod
                }
                .padding(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = item.word.en,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (item.word.ipa.isNotBlank()) {
                        Text(
                            text = "/ ${item.word.ipa} /",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        text = item.word.zh,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ShanbayMasteryBars(level = mastery)
                        if (isBypassed) {
                            Surface(
                                onClick = onRestoreMastered,
                                color = GoogleGreenSoft,
                                shape = RoundedCornerShape(999.dp),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Restore,
                                        contentDescription = "恢复学习",
                                        tint = GoogleGreenOnSoft,
                                        modifier = Modifier.size(12.dp),
                                    )
                                    Text(
                                        text = "已斩 · 点击恢复",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = GoogleGreenOnSoft,
                                    )
                                }
                            }
                        } else if (lapses > 0) {
                            Surface(
                                color = GoogleRedSoft,
                                shape = RoundedCornerShape(999.dp),
                            ) {
                                Text(
                                    text = "错记 $lapses 次",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = GoogleRedOnSoft,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                )
                            }
                        }
                        if (hasDetails) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = if (expanded) "收起例句" else "例句注解",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Icon(
                                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }

                IconButton(onClick = onToggleFavorite) {
                    Icon(
                        imageVector = if (isFav) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                        contentDescription = if (isFav) "取消收藏" else "加入生词本",
                        tint = if (isFav) GoogleYellowOnSoft else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                IconButton(onClick = onSpeak) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                        contentDescription = "朗读发音",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            AnimatedVisibility(visible = expanded && hasDetails) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    if (!item.word.exampleEn.isNullOrBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
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
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                Text(
                                    text = "工程师注解",
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
                }
            }
        }
    }
}

@Composable
private fun EmptyStateMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
