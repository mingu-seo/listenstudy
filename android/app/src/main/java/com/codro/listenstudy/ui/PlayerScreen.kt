package com.codro.listenstudy.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.codro.listenstudy.data.repository.LibraryItem
import com.codro.listenstudy.domain.player.PlaybackStatus
import com.codro.listenstudy.domain.tts.TtsEngineOption
import com.codro.listenstudy.domain.tts.TtsEngineSelection
import com.codro.listenstudy.domain.tts.TtsVoiceOption
import com.codro.listenstudy.domain.tts.TtsVoiceSelection
import com.codro.listenstudy.domain.tts.VoiceFilter
import com.codro.listenstudy.domain.tts.CloudPlaybackDiagnostics
import com.codro.listenstudy.domain.tts.CloudVoiceCatalog
import com.codro.listenstudy.domain.tts.PlaybackMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(viewModel: PlayerViewModel) {
    val state by viewModel.state.collectAsState()
    val ttsStatus by viewModel.ttsStatus.collectAsState()
    val voiceOptions by viewModel.voiceOptions.collectAsState()
    val selectedVoiceId by viewModel.selectedVoiceId.collectAsState()
    val engineOptions by viewModel.engineOptions.collectAsState()
    val selectedEnginePackageName by viewModel.selectedEnginePackageName.collectAsState()
    val documentTitle by viewModel.documentTitle.collectAsState()
    val playbackMode by viewModel.playbackMode.collectAsState()
    val cloudVoice by viewModel.cloudVoice.collectAsState()
    val hasCloudApiKey by viewModel.hasCloudApiKey.collectAsState()
    val cloudCacheStats by viewModel.cloudCacheStats.collectAsState()
    val libraryItems by viewModel.libraryItems.collectAsState()
    val showLibrary by viewModel.showLibrary.collectAsState()
    val cloudDiagnostics = CloudPlaybackDiagnostics.create(
        mode = playbackMode,
        voice = cloudVoice,
        cacheFileCount = cloudCacheStats.fileCount,
        cacheBytes = cloudCacheStats.totalBytes,
    )
    val listState = rememberLazyListState()
    val selectedVoiceLabel = TtsVoiceSelection.labelFor(voiceOptions, selectedVoiceId)
    val selectedEngineLabel = TtsEngineSelection.labelFor(engineOptions, selectedEnginePackageName)
    val progressPercent = PlayerUiFormatter.progressPercent(state.currentIndex, state.sentences.size)
    var controlsExpanded by rememberSaveable { mutableStateOf(false) }
    var voiceSheetVisible by rememberSaveable { mutableStateOf(false) }
    var cloudSheetVisible by rememberSaveable { mutableStateOf(false) }
    var voiceFilter by rememberSaveable { mutableStateOf(VoiceFilter.Recommended) }
    val filteredVoices = TtsVoiceSelection.filterVoices(voiceOptions, voiceFilter)
    val textFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.loadTextFile(uri)
    }

    if (showLibrary) {
        LibraryScreen(
            documents = libraryItems,
            onBackToPlayer = viewModel::openPlayer,
            onImport = { textFileLauncher.launch(arrayOf("text/plain", "text/*", "application/octet-stream")) },
            onOpen = viewModel::openDocument,
            onDelete = viewModel::deleteDocument,
        )
        return
    }

    LaunchedEffect(state.currentIndex) {
        if (state.sentences.isNotEmpty()) {
            listState.animateScrollToItem(state.currentIndex.coerceAtLeast(0))
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFFF6F7FB),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
        ) {
            PlayerHeader(
                documentTitle = documentTitle,
                progressPercent = progressPercent,
                currentCounter = PlayerUiFormatter.sentenceCounter(state.currentIndex, state.sentences.size),
                speedLabel = PlayerUiFormatter.speedLabel(state.speed),
                status = state.status,
                onOpenLibrary = viewModel::openLibrary,
                onOpenFile = { textFileLauncher.launch(arrayOf("text/plain", "text/*", "application/octet-stream")) },
            )

            Spacer(modifier = Modifier.height(12.dp))

            DocumentTextPanel(
                sentences = state.sentences,
                currentIndex = state.currentIndex,
                listState = listState,
                onSentenceClick = viewModel::jumpTo,
                modifier = Modifier.weight(1f),
            )

            Spacer(modifier = Modifier.height(8.dp))

            CompactDiagnostics(
                playbackMode = playbackMode,
                selectedEngineLabel = selectedEngineLabel,
                selectedVoiceLabel = selectedVoiceLabel,
                voiceCount = voiceOptions.size,
                cloudDiagnostics = cloudDiagnostics,
                ttsStatus = ttsStatus,
            )

            Spacer(modifier = Modifier.height(8.dp))

            BottomPlayerBar(
                status = state.status,
                speedLabel = PlayerUiFormatter.speedLabel(state.speed),
                progress = PlayerUiFormatter.progressFraction(state.currentIndex, state.sentences.size),
                expanded = controlsExpanded,
                onToggleExpanded = { controlsExpanded = !controlsExpanded },
                onPrevious = viewModel::previous,
                onPlayPause = {
                    if (state.status == PlaybackStatus.Playing) viewModel.pause() else viewModel.play()
                },
                onNext = viewModel::next,
                onSlower = viewModel::slower,
                onFaster = viewModel::faster,
                onChangeVoice = {
                    if (playbackMode == PlaybackMode.ON_DEVICE) voiceSheetVisible = true else cloudSheetVisible = true
                },
                onPreviewVoice = {
                    if (playbackMode == PlaybackMode.ON_DEVICE) viewModel.previewVoice() else viewModel.previewCloudVoice()
                },
                onCloudSettings = { cloudSheetVisible = true },
            )
        }
    }

    if (voiceSheetVisible) {
        VoicePickerSheet(
            engines = engineOptions,
            selectedEnginePackageName = selectedEnginePackageName,
            selectedEngineLabel = selectedEngineLabel,
            voices = filteredVoices,
            allVoiceCount = voiceOptions.size,
            selectedVoiceId = selectedVoiceId,
            selectedVoiceLabel = selectedVoiceLabel,
            selectedFilter = voiceFilter,
            onFilterChange = { voiceFilter = it },
            onDismiss = { voiceSheetVisible = false },
            onSelectEngine = { enginePackageName -> viewModel.selectEngine(enginePackageName) },
            onSelectVoice = { voiceId -> viewModel.selectVoice(voiceId) },
            onPreviewVoice = { voiceId -> viewModel.previewVoice(voiceId) },
            onOpenTtsSettings = viewModel::openTtsSettings,
        )
    }
    if (cloudSheetVisible) {
        CloudSettingsSheet(
            mode = playbackMode,
            selectedVoiceId = cloudVoice.id,
            hasKey = hasCloudApiKey,
            cacheFiles = cloudCacheStats.fileCount,
            cacheBytes = cloudCacheStats.totalBytes,
            ttsStatus = ttsStatus,
            onDismiss = { cloudSheetVisible = false },
            onSelectMode = viewModel::selectPlaybackMode,
            onSelectVoice = viewModel::selectCloudVoice,
            onSaveKey = viewModel::saveCloudApiKey,
            onDeleteKey = viewModel::deleteCloudApiKey,
            onClearCache = viewModel::clearCloudCache,
            onPreview = viewModel::previewCloudVoice,
        )
    }
}

@Composable
private fun LibraryScreen(
    documents: List<LibraryItem>,
    onBackToPlayer: () -> Unit,
    onImport: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    Surface(modifier = Modifier.fillMaxSize(), color = Color(0xFFF6F7FB)) {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("내 서재", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
                    Text("최근 학습한 자료부터 표시됩니다.", color = Color(0xFF6B7280))
                }
                TinyOutlinedButton("플레이어", onBackToPlayer)
                Spacer(modifier = Modifier.padding(horizontal = 2.dp))
                TinyButton("TXT 추가", onImport)
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (documents.isEmpty()) {
                Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), color = Color.White) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("저장된 학습 자료가 없습니다.", fontWeight = FontWeight.Bold)
                        Text("TXT 추가를 눌러 첫 자료를 가져오세요.", color = Color(0xFF6B7280))
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(documents, key = { it.id }) { document ->
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onOpen(document.id) },
                            shape = RoundedCornerShape(16.dp),
                            color = Color.White,
                            shadowElevation = 1.dp,
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Text(document.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(modifier = Modifier.height(5.dp))
                                LinearProgressIndicator(
                                    progress = { document.progressPercent / 100f },
                                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(99.dp)),
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "${document.sentenceCount}문장 · 재생 ${document.progressLabel} · ${document.progressPercent}%",
                                        modifier = Modifier.weight(1f),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color(0xFF6B7280),
                                    )
                                    TinyOutlinedButton(text = "삭제", onClick = { pendingDelete = document.id })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    pendingDelete?.let { id ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("학습 자료 삭제") },
            text = { Text("저장된 문장과 이어듣기 위치를 모두 삭제할까요?") },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { onDelete(id); pendingDelete = null }) { Text("삭제") }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { pendingDelete = null }) { Text("취소") }
            },
        )
    }
}

@Composable
private fun PlayerHeader(
    documentTitle: String,
    progressPercent: Int,
    currentCounter: String,
    speedLabel: String,
    status: PlaybackStatus,
    onOpenLibrary: () -> Unit,
    onOpenFile: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "ListenStudy",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF111827),
                )
                Text(
                    text = documentTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF374151),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TinyOutlinedButton(text = "서재", onClick = onOpenLibrary)
            Spacer(modifier = Modifier.padding(horizontal = 2.dp))
            OutlinedButton(
                onClick = onOpenFile,
                modifier = Modifier.height(34.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
            ) { Text("TXT 열기", style = MaterialTheme.typography.labelMedium) }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "학습 자료 · $currentCounter · $progressPercent% · $speedLabel",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6B7280),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (status == PlaybackStatus.Playing) "재생 중" else "대기 중",
                style = MaterialTheme.typography.labelLarge,
                color = if (status == PlaybackStatus.Playing) Color(0xFF16A34A) else Color(0xFFF59E0B),
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DocumentTextPanel(
    sentences: List<String>,
    currentIndex: Int,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onSentenceClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        if (sentences.isEmpty()) {
            Text(
                text = "읽을 텍스트가 없습니다.",
                modifier = Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF6B7280),
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                itemsIndexed(sentences) { index, sentence ->
                    DocumentSentenceLine(
                        sentence = sentence,
                        current = index == currentIndex,
                        onClick = { onSentenceClick(index) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DocumentSentenceLine(
    sentence: String,
    current: Boolean,
    onClick: () -> Unit,
) {
    Text(
        text = sentence,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (current) Color(0xFFFFF2CC) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = if (current) Color(0xFF111827) else Color(0xFF374151),
        fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
    )
}

@Composable
private fun CompactDiagnostics(
    playbackMode: PlaybackMode,
    selectedEngineLabel: String,
    selectedVoiceLabel: String,
    voiceCount: Int,
    cloudDiagnostics: CloudPlaybackDiagnostics,
    ttsStatus: String,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (playbackMode == PlaybackMode.ON_DEVICE) {
            Text(
                text = "엔진: $selectedEngineLabel",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "목소리: $selectedVoiceLabel (${voiceCount}개)",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Text(
                text = "엔진: ${cloudDiagnostics.engine}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF2563EB),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "목소리: ${cloudDiagnostics.voice}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = cloudDiagnostics.cache,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = "TTS 상태: $ttsStatus",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9CA3AF),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BottomPlayerBar(
    status: PlaybackStatus,
    speedLabel: String,
    progress: Float,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSlower: () -> Unit,
    onFaster: () -> Unit,
    onChangeVoice: () -> Unit,
    onPreviewVoice: () -> Unit,
    onCloudSettings: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        tonalElevation = 4.dp,
        shadowElevation = 6.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = Color(0xFF2563EB),
                trackColor = Color(0xFFE5E7EB),
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggleExpanded),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (status == PlaybackStatus.Playing) "재생 중" else "컨트롤",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF6B7280),
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "$speedLabel · ${if (expanded) "숨기기 ▼" else "열기 ▲"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF2563EB),
                    fontWeight = FontWeight.Bold,
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TinyOutlinedButton(
                            text = "이전",
                            onClick = onPrevious,
                            modifier = Modifier.weight(1f),
                        )
                        TinyButton(
                            text = if (status == PlaybackStatus.Playing) "일시정지" else "재생",
                            onClick = onPlayPause,
                            modifier = Modifier.weight(1.2f),
                        )
                        TinyOutlinedButton(
                            text = "다음",
                            onClick = onNext,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TinyOutlinedButton(text = "- 속도", onClick = onSlower, modifier = Modifier.weight(1f))
                        Text(
                            text = speedLabel,
                            modifier = Modifier.weight(0.7f),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF374151),
                            fontWeight = FontWeight.Bold,
                        )
                        TinyOutlinedButton(text = "+ 속도", onClick = onFaster, modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TinyOutlinedButton(text = "목소리", onClick = onChangeVoice, modifier = Modifier.weight(1f))
                        TinyOutlinedButton(text = "미리듣기", onClick = onPreviewVoice, modifier = Modifier.weight(1f))
                        TinyOutlinedButton(text = "클라우드", onClick = onCloudSettings, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VoicePickerSheet(
    engines: List<TtsEngineOption>,
    selectedEnginePackageName: String?,
    selectedEngineLabel: String,
    voices: List<TtsVoiceOption>,
    allVoiceCount: Int,
    selectedVoiceId: String?,
    selectedVoiceLabel: String,
    selectedFilter: VoiceFilter,
    onFilterChange: (VoiceFilter) -> Unit,
    onDismiss: () -> Unit,
    onSelectEngine: (String) -> Unit,
    onSelectVoice: (String) -> Unit,
    onPreviewVoice: (String) -> Unit,
    onOpenTtsSettings: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp, vertical = 8.dp),
        ) {
            Text(
                text = "목소리 선택",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF111827),
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "현재 엔진: $selectedEngineLabel",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "현재 목소리: $selectedVoiceLabel · 전체 ${allVoiceCount}개",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "TTS 엔진",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827),
            )
            Spacer(modifier = Modifier.height(6.dp))
            if (engines.isEmpty()) {
                Text(
                    text = "설치된 TTS 엔진 목록을 아직 읽는 중입니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(118.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    itemsIndexed(engines) { _, engine ->
                        EngineOptionRow(
                            engine = engine,
                            selected = engine.packageName == selectedEnginePackageName,
                            onSelectEngine = onSelectEngine,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "목소리",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827),
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                VoiceFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { onFilterChange(filter) },
                        label = { Text(filter.title, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (voices.isEmpty()) {
                Text(
                    text = "선택 가능한 목소리가 없습니다. 휴대폰 TTS 음성 데이터를 추가해 주세요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B7280),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.height(330.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(voices) { _, voice ->
                        VoiceOptionRow(
                            voice = voice,
                            selected = voice.id == selectedVoiceId,
                            onSelectVoice = onSelectVoice,
                            onPreviewVoice = onPreviewVoice,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "좋은 목소리가 안 보이면 휴대폰에 Google/Samsung 한국어 음성 데이터를 추가해 주세요.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280),
            )
            Spacer(modifier = Modifier.height(6.dp))
            OutlinedButton(
                onClick = onOpenTtsSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("휴대폰 TTS 설정 열기")
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun EngineOptionRow(
    engine: TtsEngineOption,
    selected: Boolean,
    onSelectEngine: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) Color(0xFFEFF6FF) else Color(0xFFF9FAFB),
        tonalElevation = if (selected) 2.dp else 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (selected) "✓ ${engine.label}" else engine.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF111827),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        if (engine.isDefault) append("휴대폰 기본 엔진 · ")
                        append(engine.discoverySource)
                        if (engine.packageName != TtsEngineSelection.SYSTEM_DEFAULT_ENGINE_ID) {
                            append(" · ")
                            append(engine.packageName)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B7280),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TinyOutlinedButton(
                text = if (selected) "선택됨" else "선택",
                onClick = { onSelectEngine(engine.packageName) },
            )
        }
    }
}

@Composable
private fun VoiceOptionRow(
    voice: TtsVoiceOption,
    selected: Boolean,
    onSelectVoice: (String) -> Unit,
    onPreviewVoice: (String) -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) Color(0xFFEFF6FF) else Color(0xFFF9FAFB),
        tonalElevation = if (selected) 2.dp else 0.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (selected) "✓ ${voice.label}" else voice.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = voice.description.ifBlank { if (voice.isOffline) "오프라인 가능" else "온라인 필요" },
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6B7280),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (voice.isRecommended) {
                    Text(
                        text = "추천",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFF2563EB),
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TinyOutlinedButton(
                    text = "미리듣기",
                    onClick = { onPreviewVoice(voice.id) },
                    modifier = Modifier.weight(1f),
                )
                TinyButton(
                    text = if (selected) "선택됨" else "선택",
                    onClick = { onSelectVoice(voice.id) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun TinyButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(34.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun TinyOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(34.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CloudSettingsSheet(
    mode: PlaybackMode,
    selectedVoiceId: String,
    hasKey: Boolean,
    cacheFiles: Int,
    cacheBytes: Long,
    ttsStatus: String,
    onDismiss: () -> Unit,
    onSelectMode: (PlaybackMode) -> Unit,
    onSelectVoice: (String) -> Unit,
    onSaveKey: (String) -> Unit,
    onDeleteKey: () -> Unit,
    onClearCache: () -> Unit,
    onPreview: () -> Unit,
) {
    var keyInput by rememberSaveable { mutableStateOf("") }
    val previewFeedback = PlayerUiFormatter.cloudPreviewFeedback(ttsStatus)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            item {
                Text("Google Cloud TTS 설정", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                Text("개인 테스트용 직접 API 키 방식입니다. 키 제한·과금·유출 책임을 확인하세요. 키는 APK에 포함되지 않고 앱 비공개 저장소에만 저장됩니다.", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB45309))
            }
            item {
                Text("재생 모드", fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    PlaybackMode.entries.forEach { option ->
                        FilterChip(selected = mode == option, onClick = { onSelectMode(option) }, label = { Text(option.label) })
                    }
                }
            }
            if (mode != PlaybackMode.ON_DEVICE) {
                item {
                    Text("한국어 음성", fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        CloudVoiceCatalog.forMode(mode).forEach { voice ->
                            FilterChip(selected = selectedVoiceId == voice.id, onClick = { onSelectVoice(voice.id) }, label = { Text(voice.id.takeLast(1)) })
                        }
                    }
                    Text(CloudVoiceCatalog.voices.firstOrNull { it.id == selectedVoiceId }?.label.orEmpty(), style = MaterialTheme.typography.bodySmall)
                }
            }
            item {
                Text("API 키: ${if (hasKey) "설정됨" else "설정되지 않음"}", fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (hasKey) "새 키로 교체" else "Google Cloud API 키") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TinyButton("저장/교체", { onSaveKey(keyInput); keyInput = "" }, Modifier.weight(1f))
                    TinyOutlinedButton("키 삭제", onDeleteKey, Modifier.weight(1f))
                }
            }
            item {
                Text("로컬 캐시: ${cacheFiles}개 · ${formatCacheBytes(cacheBytes)}", fontWeight = FontWeight.Bold)
                Text("캐시가 있으면 API 키와 네트워크 없이 재생합니다. 배속은 기기에서 적용되어 같은 MP3를 재사용합니다.", style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B7280))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TinyOutlinedButton("캐시 삭제", onClearCache, Modifier.weight(1f))
                    TinyButton("클라우드 미리듣기", onPreview, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (previewFeedback.inProgress) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(6.dp))
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    color = if (previewFeedback.isError) Color(0xFFFFE4E6) else Color(0xFFEFF6FF),
                ) {
                    Text(
                        text = previewFeedback.message,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (previewFeedback.isError) Color(0xFFBE123C) else Color(0xFF1D4ED8),
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

private fun formatCacheBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
