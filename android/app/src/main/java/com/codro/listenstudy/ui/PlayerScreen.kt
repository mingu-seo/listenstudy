package com.codro.listenstudy.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import com.codro.listenstudy.R
import com.codro.listenstudy.data.repository.LibraryItem
import com.codro.listenstudy.domain.player.PlaybackStatus
import com.codro.listenstudy.domain.reader.FlowingReaderTextPolicy
import com.codro.listenstudy.domain.tts.CloudErrorReport
import com.codro.listenstudy.domain.tts.CloudRecoveryAction
import com.codro.listenstudy.domain.tts.TtsEngineOption
import com.codro.listenstudy.domain.tts.TtsEngineSelection
import com.codro.listenstudy.domain.tts.TtsVoiceOption
import com.codro.listenstudy.domain.tts.TtsVoiceSelection
import com.codro.listenstudy.domain.tts.VoiceFilter
import com.codro.listenstudy.domain.tts.CloudVoiceCatalog
import com.codro.listenstudy.playback.CloudKeySaveResult
import com.codro.listenstudy.domain.tts.PlaybackMode
import com.codro.listenstudy.ui.theme.QuietReaderElevation
import com.codro.listenstudy.ui.theme.QuietReaderShapes
import com.codro.listenstudy.ui.theme.QuietReaderSizes
import com.codro.listenstudy.ui.theme.QuietReaderSpacing
import com.codro.listenstudy.ui.theme.extendedColors

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
    val cloudKeySaveResult by viewModel.cloudKeySaveResult.collectAsState()
    val cloudCacheStats by viewModel.cloudCacheStats.collectAsState()
    val cloudError by viewModel.cloudError.collectAsState()
    val libraryItems by viewModel.libraryItems.collectAsState()
    val selectedVoiceLabel = TtsVoiceSelection.labelFor(voiceOptions, selectedVoiceId)
    val selectedEngineLabel = TtsEngineSelection.labelFor(engineOptions, selectedEnginePackageName)

    var controlsExpanded by rememberSaveable { mutableStateOf(false) }
    var voiceSheetVisible by rememberSaveable { mutableStateOf(false) }
    var navigation by remember { mutableStateOf(AppNavigation.initial()) }
    var voiceFilter by rememberSaveable { mutableStateOf(VoiceFilter.Recommended) }
    val filteredVoices = TtsVoiceSelection.filterVoices(voiceOptions, voiceFilter)
    val textFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.loadTextFile(uri)
    }

    if (navigation.destination == AppDestination.Library) {
        BackHandler { navigation = navigation.navigate(AppNavigationEvent.Back) }
        LibraryScreen(
            documents = libraryItems,
            onBackToPlayer = {
                viewModel.openPlayer()
                navigation = navigation.navigate(AppNavigationEvent.OpenReader)
            },
            onOpenSettings = { navigation = navigation.navigate(AppNavigationEvent.OpenSettings) },
            onImport = { textFileLauncher.launch(arrayOf("text/plain", "text/*", "application/octet-stream")) },
            onOpen = { id ->
                viewModel.openDocument(id)
                navigation = navigation.navigate(AppNavigationEvent.OpenReader)
            },
            onDelete = viewModel::deleteDocument,
        )
        return
    }

    if (navigation.destination == AppDestination.Settings) {
        BackHandler { navigation = navigation.navigate(AppNavigationEvent.Back) }
        SettingsScreen(
            mode = playbackMode,
            selectedCloudVoiceId = cloudVoice.id,
            hasKey = hasCloudApiKey,
            cacheFiles = cloudCacheStats.fileCount,
            cacheBytes = cloudCacheStats.totalBytes,
            ttsStatus = ttsStatus,
            keySaveResult = cloudKeySaveResult,
            engines = engineOptions,
            selectedEnginePackageName = selectedEnginePackageName,
            selectedEngineLabel = selectedEngineLabel,
            voices = voiceOptions,
            selectedVoiceId = selectedVoiceId,
            selectedVoiceLabel = selectedVoiceLabel,
            onBack = { navigation = navigation.navigate(AppNavigationEvent.Back) },
            onSelectMode = viewModel::selectPlaybackMode,
            onSelectCloudVoice = viewModel::selectCloudVoice,
            onSaveKey = viewModel::saveCloudApiKey,
            onDeleteKey = viewModel::deleteCloudApiKey,
            onClearCache = viewModel::clearCloudCache,
            onCloudPreview = viewModel::previewCloudVoice,
            onSelectEngine = viewModel::selectEngine,
            onSelectVoice = viewModel::selectVoice,
            onPreviewVoice = viewModel::previewVoice,
            onOpenTtsSettings = viewModel::openTtsSettings,
            onOpenVoicePicker = { voiceSheetVisible = true },
        )
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
                onSelectEngine = viewModel::selectEngine,
                onSelectVoice = viewModel::selectVoice,
                onPreviewVoice = viewModel::previewVoice,
                onOpenTtsSettings = viewModel::openTtsSettings,
            )
        }
        return
    }

    val progressArgs = QuietReaderUiPolicy.progressFormatArgs(state.currentIndex, state.sentences.size)
    val progressDescription = if (progressArgs == null) {
        stringResource(R.string.progress_unavailable)
    } else {
        stringResource(
            R.string.progress_description,
            progressArgs.current,
            progressArgs.total,
            progressArgs.percent,
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(start = QuietReaderSpacing.lg, top = QuietReaderSpacing.sm, end = QuietReaderSpacing.lg, bottom = QuietReaderSpacing.sm),
        ) {
            PlayerHeader(
                documentTitle = documentTitle,
                progressDescription = progressDescription,
                speedLabel = PlayerUiFormatter.speedLabel(state.speed),
                status = state.status,
                onOpenLibrary = {
                    viewModel.openLibrary()
                    navigation = navigation.navigate(AppNavigationEvent.OpenLibrary)
                },
                onOpenSettings = { navigation = navigation.navigate(AppNavigationEvent.OpenSettings) },
            )

            Spacer(modifier = Modifier.height(QuietReaderSpacing.md))

            cloudError?.let { report ->
                CloudErrorPanel(
                    report = report,
                    onAction = { action ->
                        when (action) {
                            CloudRecoveryAction.Retry -> viewModel.retryCloudSentence()
                            CloudRecoveryAction.UseOnDeviceVoice -> viewModel.useOnDeviceVoiceForCurrentSentence()
                            CloudRecoveryAction.OpenCloudSettings -> {
                                viewModel.dismissCloudError()
                                navigation = navigation.navigate(AppNavigationEvent.OpenSettings)
                            }
                        }
                    },
                    onDismiss = viewModel::dismissCloudError,
                )
                Spacer(modifier = Modifier.height(QuietReaderSpacing.md))
            }

            DocumentTextPanel(
                sentences = state.sentences,
                currentIndex = state.currentIndex,
                onSentenceClick = viewModel::jumpTo,
                modifier = Modifier.weight(1f).align(Alignment.CenterHorizontally),
            )

            Spacer(modifier = Modifier.height(QuietReaderSpacing.sm))

            BottomPlayerBar(
                status = state.status,
                speedLabel = PlayerUiFormatter.speedLabel(state.speed),
                progress = QuietReaderUiPolicy.progressFraction(state.currentIndex, state.sentences.size),
                expanded = controlsExpanded,
                onToggleExpanded = { controlsExpanded = !controlsExpanded },
                onFirst = { viewModel.jumpTo(PlayerUiFormatter.FIRST_SENTENCE_INDEX) },
                onPrevious = viewModel::previous,
                onPlayPause = {
                    if (state.status == PlaybackStatus.Playing) viewModel.pause() else viewModel.play()
                },
                onNext = viewModel::next,
                onSlower = viewModel::slower,
                onFaster = viewModel::faster,
                onChangeVoice = {
                    if (playbackMode == PlaybackMode.ON_DEVICE) voiceSheetVisible = true
                    else navigation = navigation.navigate(AppNavigationEvent.OpenSettings)
                },
                onPreviewVoice = {
                    if (playbackMode == PlaybackMode.ON_DEVICE) viewModel.previewVoice() else viewModel.previewCloudVoice()
                },
                onCloudSettings = { navigation = navigation.navigate(AppNavigationEvent.OpenSettings) },
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
}

@Composable
private fun LibraryScreen(
    documents: List<LibraryItem>,
    onBackToPlayer: () -> Unit,
    onOpenSettings: () -> Unit,
    onImport: () -> Unit,
    onOpen: (String) -> Unit,
    onDelete: (String) -> Unit,
) {
    var pendingDelete by rememberSaveable { mutableStateOf<String?>(null) }
    var headerMenuExpanded by remember { mutableStateOf(false) }
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(QuietReaderSpacing.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.library_title), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text(stringResource(R.string.library_description), color = MaterialTheme.extendedColors.textSecondary)
                }
                LsButton(stringResource(R.string.import_txt), onImport)
                Box {
                    TextButton(onClick = { headerMenuExpanded = true }) { Text(stringResource(R.string.more_actions)) }
                    DropdownMenu(expanded = headerMenuExpanded, onDismissRequest = { headerMenuExpanded = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.reader)) }, onClick = { headerMenuExpanded = false; onBackToPlayer() })
                        DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, onClick = { headerMenuExpanded = false; onOpenSettings() })
                    }
                }
            }
            Spacer(modifier = Modifier.height(QuietReaderSpacing.lg))
            if (documents.isEmpty()) {
                Surface(modifier = Modifier.fillMaxWidth(), shape = QuietReaderShapes.medium, color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.padding(QuietReaderSpacing.xl)) {
                        Text(stringResource(R.string.library_empty_title), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.library_empty_description), color = MaterialTheme.extendedColors.textSecondary)
                    }
                }
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(QuietReaderSpacing.md)) {
                    items(documents, key = { it.id }) { document ->
                        var itemMenuExpanded by remember(document.id) { mutableStateOf(false) }
                        val documentMoreActionsLabel = stringResource(R.string.more_actions_for_document, document.title)
                        Surface(
                            modifier = Modifier.fillMaxWidth().clickable { onOpen(document.id) },
                            shape = QuietReaderShapes.medium,
                            color = MaterialTheme.colorScheme.surface,
                            border = androidx.compose.foundation.BorderStroke(QuietReaderElevation.low, MaterialTheme.extendedColors.outlineSubtle),
                        ) {
                            Column(modifier = Modifier.padding(QuietReaderSpacing.lg)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(document.title, modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Box {
                                        TextButton(
                                            onClick = { itemMenuExpanded = true },
                                            modifier = Modifier.semantics {
                                                contentDescription = documentMoreActionsLabel
                                            },
                                        ) { Text(stringResource(R.string.more_actions)) }
                                        DropdownMenu(expanded = itemMenuExpanded, onDismissRequest = { itemMenuExpanded = false }) {
                                            DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = { itemMenuExpanded = false; pendingDelete = document.id })
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(QuietReaderSpacing.xs))
                                LinearProgressIndicator(
                                    progress = { document.progressPercent / 100f },
                                    modifier = Modifier.fillMaxWidth().height(QuietReaderSizes.ProgressHeight).clip(QuietReaderShapes.dock)
                                        .semantics { progressBarRangeInfo = ProgressBarRangeInfo(document.progressPercent / 100f, 0f..1f) },
                                )
                                Spacer(modifier = Modifier.height(QuietReaderSpacing.sm))
                                Text(
                                    stringResource(R.string.library_item_summary, document.sentenceCount, document.progressLabel, document.progressPercent),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.extendedColors.textSecondary,
                                )
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
            title = { Text(stringResource(R.string.delete_document_title)) },
            text = { Text(stringResource(R.string.delete_document_message)) },
            confirmButton = { TextButton(onClick = { onDelete(id); pendingDelete = null }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
        )
    }
}

@Composable
private fun PlayerHeader(
    documentTitle: String,
    progressDescription: String,
    speedLabel: String,
    status: PlaybackStatus,
    onOpenLibrary: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = documentTitle,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.extendedColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LsOutlinedButton(text = stringResource(R.string.library), onClick = onOpenLibrary)
            Spacer(modifier = Modifier.padding(horizontal = QuietReaderSpacing.xxs))
            LsOutlinedButton(text = stringResource(R.string.settings), onClick = onOpenSettings)
        }
        Spacer(modifier = Modifier.height(QuietReaderSpacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "$progressDescription · $speedLabel",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.extendedColors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (status == PlaybackStatus.Playing) stringResource(R.string.playing) else stringResource(R.string.waiting),
                style = MaterialTheme.typography.labelLarge,
                color = if (status == PlaybackStatus.Playing) MaterialTheme.extendedColors.success else MaterialTheme.extendedColors.warning,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun DocumentTextPanel(
    sentences: List<String>,
    currentIndex: Int,
    onSentenceClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val layout = PlayerUiFormatter.documentTextLayout()
    val flowingText = remember(sentences) { FlowingReaderTextPolicy.compose(sentences) }
    val currentRange = flowingText.ranges.firstOrNull { it.sentenceIndex == currentIndex }
    val currentBackground = MaterialTheme.extendedColors.readingCurrent
    val currentForeground = MaterialTheme.extendedColors.onReadingCurrent
    val annotatedText = remember(flowingText, currentRange, currentBackground, currentForeground) {
        buildAnnotatedString {
            append(flowingText.text)
            currentRange?.let { range ->
                addStyle(
                    SpanStyle(
                        background = currentBackground,
                        color = currentForeground,
                        fontWeight = FontWeight.Medium,
                    ),
                    range.start,
                    range.endExclusive,
                )
            }
        }
    }
    val scrollState = rememberScrollState()
    var textLayoutResult by remember(flowingText.text) { mutableStateOf<TextLayoutResult?>(null) }
    var viewportHeight by remember { mutableStateOf(0) }

    LaunchedEffect(currentIndex, textLayoutResult, viewportHeight) {
        val range = currentRange ?: return@LaunchedEffect
        val result = textLayoutResult ?: return@LaunchedEffect
        if (range.start >= result.layoutInput.text.length || viewportHeight <= 0) return@LaunchedEffect
        val sentenceTop = result.getBoundingBox(range.start).top.toInt()
        val target = (sentenceTop - viewportHeight / 3).coerceIn(0, scrollState.maxValue)
        scrollState.animateScrollTo(target)
    }

    Surface(
        modifier = modifier.fillMaxWidth().widthIn(max = QuietReaderSizes.ReaderMaxWidth),
        shape = QuietReaderShapes.large,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = QuietReaderElevation.none,
        shadowElevation = QuietReaderElevation.low,
    ) {
        if (sentences.isEmpty()) {
            Text(
                text = stringResource(R.string.no_reading_text),
                modifier = Modifier.padding(QuietReaderSpacing.xl),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.extendedColors.textSecondary,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewportHeight = it.height }
                    .verticalScroll(scrollState),
            ) {
                Text(
                    text = annotatedText,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = QuietReaderSpacing.lg, vertical = QuietReaderSpacing.lg)
                        .pointerInput(flowingText, textLayoutResult) {
                            detectTapGestures { position ->
                                val characterOffset = textLayoutResult?.getOffsetForPosition(position)
                                    ?: return@detectTapGestures
                                flowingText.sentenceIndexAt(characterOffset)?.let(onSentenceClick)
                            }
                        },
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.extendedColors.textPrimary,
                    fontSize = layout.sentenceFontSizeSp.sp,
                    lineHeight = layout.sentenceLineHeightSp.sp,
                    onTextLayout = { textLayoutResult = it },
                )
            }
        }
    }
}

@Composable
private fun PlaybackPositionBar(progress: Float) {
    val safeProgress = progress.coerceIn(0f, 1f)
    val activeColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(QuietReaderSizes.ProgressThumbSize)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(safeProgress, 0f..1f) },
    ) {
        val thumbRadius = QuietReaderSizes.ProgressThumbSize.toPx() / 2f
        val startX = thumbRadius
        val endX = (size.width - thumbRadius).coerceAtLeast(startX)
        val centerY = size.height / 2f
        val markerX = startX + (endX - startX) * safeProgress
        val strokeWidth = QuietReaderSizes.ProgressHeight.toPx()

        drawLine(
            color = trackColor,
            start = Offset(startX, centerY),
            end = Offset(endX, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = activeColor,
            start = Offset(startX, centerY),
            end = Offset(markerX, centerY),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round,
        )
        drawCircle(color = activeColor, radius = thumbRadius, center = Offset(markerX, centerY))
    }
}

@Composable
private fun BottomPlayerBar(
    status: PlaybackStatus,
    speedLabel: String,
    progress: Float,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    onFirst: () -> Unit,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onSlower: () -> Unit,
    onFaster: () -> Unit,
    onChangeVoice: () -> Unit,
    onPreviewVoice: () -> Unit,
    onCloudSettings: () -> Unit,
) {
    val controlVisibility = PlayerUiFormatter.bottomPlayerControlVisibility(expanded)
    val expandLabel = if (expanded) stringResource(R.string.collapse_controls) else stringResource(R.string.expand_controls)
    val expansionState = if (expanded) stringResource(R.string.expanded) else stringResource(R.string.collapsed)
    val expansionAction = if (expanded) stringResource(R.string.hide_controls) else stringResource(R.string.show_controls)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        shape = QuietReaderShapes.dock,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = QuietReaderElevation.none,
        shadowElevation = QuietReaderElevation.high,
    ) {
        Column(modifier = Modifier.padding(horizontal = QuietReaderSpacing.md, vertical = QuietReaderSpacing.sm)) {
            PlaybackPositionBar(progress)
            Spacer(modifier = Modifier.height(QuietReaderSpacing.sm))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClickLabel = expandLabel,
                        onClick = onToggleExpanded,
                    )
                    .semantics { stateDescription = expansionState },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (status == PlaybackStatus.Playing) stringResource(R.string.playing) else stringResource(R.string.controls),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.extendedColors.textSecondary,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "$speedLabel · $expansionAction",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
            }

            if (controlVisibility.showPrimaryPlaybackControls) {
                Spacer(modifier = Modifier.height(QuietReaderSpacing.sm))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(QuietReaderSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayerUiFormatter.primaryPlaybackControls().forEach { control ->
                        when (control) {
                            PrimaryPlaybackControl.First -> LsOutlinedButton(
                                text = stringResource(R.string.first),
                                onClick = onFirst,
                                modifier = Modifier.weight(1f),
                            )
                            PrimaryPlaybackControl.Previous -> LsOutlinedButton(
                                text = stringResource(R.string.previous),
                                onClick = onPrevious,
                                modifier = Modifier.weight(1f),
                            )
                            PrimaryPlaybackControl.PlayPause -> LsButton(
                                text = if (status == PlaybackStatus.Playing) stringResource(R.string.pause) else stringResource(R.string.play),
                                onClick = onPlayPause,
                                modifier = Modifier.weight(1.2f).sizeIn(minHeight = QuietReaderSizes.PlayButton),
                            )
                            PrimaryPlaybackControl.Next -> LsOutlinedButton(
                                text = stringResource(R.string.next),
                                onClick = onNext,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            if (controlVisibility.showAdditionalControls) {
                Column {
                    Spacer(modifier = Modifier.height(QuietReaderSpacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(QuietReaderSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LsOutlinedButton(text = stringResource(R.string.slower), onClick = onSlower, modifier = Modifier.weight(1f))
                        Text(
                            text = speedLabel,
                            modifier = Modifier.weight(0.7f),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.extendedColors.textPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        LsOutlinedButton(text = stringResource(R.string.faster), onClick = onFaster, modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(QuietReaderSpacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(QuietReaderSpacing.sm),
                    ) {
                        LsOutlinedButton(text = stringResource(R.string.voice), onClick = onChangeVoice, modifier = Modifier.weight(1f))
                        LsOutlinedButton(text = stringResource(R.string.preview), onClick = onPreviewVoice, modifier = Modifier.weight(1f))
                        LsOutlinedButton(text = stringResource(R.string.cloud), onClick = onCloudSettings, modifier = Modifier.weight(1f))
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
    val loadingDescription = "${stringResource(R.string.status_loading)}. ${stringResource(R.string.tts_engines_loading)}"
    ModalBottomSheet(onDismissRequest = onDismiss) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(horizontal = QuietReaderSpacing.lg, vertical = QuietReaderSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(QuietReaderSpacing.sm),
        ) {
            item {
                Text(
                    text = stringResource(R.string.voice_picker_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.extendedColors.textPrimary,
                )
            }
            item {
                Text(
                    text = stringResource(R.string.current_engine, selectedEngineLabel),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.current_voice_count, selectedVoiceLabel, allVoiceCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            item {
                Text(stringResource(R.string.tts_engine), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            }
            if (engines.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.tts_engines_loading),
                        modifier = Modifier.semantics {
                            liveRegion = LiveRegionMode.Polite
                            contentDescription = loadingDescription
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.extendedColors.textSecondary,
                    )
                }
            } else {
                items(engines, key = { it.packageName }) { engine ->
                    EngineOptionRow(engine, engine.packageName == selectedEnginePackageName, onSelectEngine)
                }
            }
            item {
                Text(stringResource(R.string.voice), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(QuietReaderSpacing.sm)) {
                    VoiceFilter.entries.forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { onFilterChange(filter) },
                            label = { Text(filter.title, style = MaterialTheme.typography.labelMedium) },
                        )
                    }
                }
            }
            if (voices.isEmpty()) {
                item { Text(stringResource(R.string.no_voices), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.extendedColors.textSecondary) }
            } else {
                items(voices, key = { it.id }) { voice ->
                    VoiceOptionRow(voice, voice.id == selectedVoiceId, onSelectVoice, onPreviewVoice)
                }
            }
            item {
                Text(stringResource(R.string.voice_install_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textSecondary)
                Spacer(modifier = Modifier.height(QuietReaderSpacing.sm))
                OutlinedButton(onClick = onOpenTtsSettings, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.phone_tts_settings))
                }
                Spacer(modifier = Modifier.height(QuietReaderSpacing.md))
            }
        }
    }
}

@Composable
private fun EngineOptionRow(
    engine: TtsEngineOption,
    selected: Boolean,
    onSelectEngine: (String) -> Unit,
) {
    val selectionState = if (selected) stringResource(R.string.ls_selected) else stringResource(R.string.ls_not_selected)
    val selectLabel = stringResource(R.string.select_engine_a11y, engine.label)
    Surface(
        modifier = Modifier.fillMaxWidth().semantics {
            this.selected = selected
            stateDescription = selectionState
        }.clickable(onClickLabel = selectLabel) { onSelectEngine(engine.packageName) },
        shape = QuietReaderShapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = QuietReaderElevation.none,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = QuietReaderSpacing.md, vertical = QuietReaderSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (selected) "✓ ${engine.label}" else engine.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.extendedColors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (engine.isDefault) stringResource(R.string.default_phone_engine) else engine.discoverySource,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.extendedColors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            LsOutlinedButton(
                text = if (selected) stringResource(R.string.ls_selected) else stringResource(R.string.select),
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
    val selectionState = if (selected) stringResource(R.string.ls_selected) else stringResource(R.string.ls_not_selected)
    val selectLabel = stringResource(R.string.select_voice_a11y, voice.label)
    Surface(
        modifier = Modifier.fillMaxWidth().semantics {
            this.selected = selected
            stateDescription = selectionState
        }.clickable(onClickLabel = selectLabel) { onSelectVoice(voice.id) },
        shape = QuietReaderShapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = QuietReaderElevation.none,
    ) {
        Column(modifier = Modifier.padding(QuietReaderSpacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (selected) "✓ ${voice.label}" else voice.label,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.extendedColors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = voice.description.ifBlank { if (voice.isOffline) stringResource(R.string.offline_available) else stringResource(R.string.online_required) },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.extendedColors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (voice.isRecommended) {
                    Text(
                        text = stringResource(R.string.recommended),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(modifier = Modifier.height(QuietReaderSpacing.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(QuietReaderSpacing.sm)) {
                LsOutlinedButton(
                    text = stringResource(R.string.preview),
                    onClick = { onPreviewVoice(voice.id) },
                    modifier = Modifier.weight(1f),
                )
                LsButton(
                    text = if (selected) stringResource(R.string.ls_selected) else stringResource(R.string.select),
                    onClick = { onSelectVoice(voice.id) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Cloud failure recovery panel.
 *
 * A card rather than a toast: the failure needs a decision, and a toast would time out before a
 * TalkBack user finished hearing it. The buttons live in a [FlowRow] so long Korean labels wrap onto
 * their own line on a narrow screen instead of being clipped.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CloudErrorPanel(
    report: CloudErrorReport,
    onAction: (CloudRecoveryAction) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            // Assertive: the sentence the user asked for is not going to play, so it must interrupt
            // rather than wait for a gap in TalkBack's queue.
            .semantics { liveRegion = LiveRegionMode.Assertive },
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = QuietReaderShapes.medium,
        tonalElevation = QuietReaderElevation.low,
    ) {
        Column(modifier = Modifier.padding(QuietReaderSpacing.md)) {
            // Title first as a heading, then the explanation, then the actions: TalkBack reads them
            // in that order, which is also the order the user needs them in.
            Text(
                text = report.title,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(modifier = Modifier.height(QuietReaderSpacing.xs))
            Text(
                text = report.description,
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(modifier = Modifier.height(QuietReaderSpacing.md))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(QuietReaderSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(QuietReaderSpacing.sm),
            ) {
                // The policy orders actions by usefulness, so the first one is the primary button.
                report.actions.forEachIndexed { index, action ->
                    if (index == 0) {
                        LsButton(text = action.label, onClick = { onAction(action) })
                    } else {
                        LsOutlinedButton(text = action.label, onClick = { onAction(action) })
                    }
                }
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.sizeIn(
                        minWidth = QuietReaderSizes.MinTouchTarget,
                        minHeight = QuietReaderSizes.MinTouchTarget,
                    ),
                ) {
                    Text(text = stringResource(R.string.cloud_error_dismiss), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun LsButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = QuietReaderSizes.MinTouchTarget, minHeight = QuietReaderSizes.MinTouchTarget),
        contentPadding = PaddingValues(horizontal = QuietReaderSpacing.lg, vertical = QuietReaderSpacing.sm),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun LsOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.sizeIn(minWidth = QuietReaderSizes.MinTouchTarget, minHeight = QuietReaderSizes.MinTouchTarget),
        contentPadding = PaddingValues(horizontal = QuietReaderSpacing.md, vertical = QuietReaderSpacing.sm),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun SettingsScreen(
    mode: PlaybackMode,
    selectedCloudVoiceId: String,
    hasKey: Boolean,
    cacheFiles: Int,
    cacheBytes: Long,
    ttsStatus: String,
    keySaveResult: CloudKeySaveResult,
    engines: List<TtsEngineOption>,
    selectedEnginePackageName: String?,
    selectedEngineLabel: String,
    voices: List<TtsVoiceOption>,
    selectedVoiceId: String?,
    selectedVoiceLabel: String,
    onBack: () -> Unit,
    onSelectMode: (PlaybackMode) -> Unit,
    onSelectCloudVoice: (String) -> Unit,
    onSaveKey: (String) -> Long,
    onDeleteKey: () -> Unit,
    onClearCache: () -> Unit,
    onCloudPreview: () -> Unit,
    onSelectEngine: (String) -> Unit,
    onSelectVoice: (String) -> Unit,
    onPreviewVoice: (String) -> Unit,
    onOpenTtsSettings: () -> Unit,
    onOpenVoicePicker: () -> Unit,
) {
    val previewFeedback = PlayerUiFormatter.cloudPreviewFeedback(ttsStatus)
    val loadingStatus = stringResource(R.string.status_loading)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = QuietReaderSpacing.lg, vertical = QuietReaderSpacing.lg),
            verticalArrangement = Arrangement.spacedBy(QuietReaderSpacing.lg),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LsOutlinedButton(stringResource(R.string.back), onBack)
                    Spacer(modifier = Modifier.padding(horizontal = QuietReaderSpacing.xs))
                    Column {
                        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.ExtraBold)
                        Text(stringResource(R.string.settings_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textSecondary)
                    }
                }
            }
            item {
                Surface(modifier = Modifier.fillMaxWidth(), shape = QuietReaderShapes.medium, color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.padding(QuietReaderSpacing.lg), verticalArrangement = Arrangement.spacedBy(QuietReaderSpacing.sm)) {
                        Text(stringResource(R.string.playback_and_voice), fontWeight = FontWeight.Bold)
                        Column(verticalArrangement = Arrangement.spacedBy(QuietReaderSpacing.xs)) {
                            PlaybackMode.entries.forEach { option ->
                                FilterChip(
                                    selected = mode == option,
                                    onClick = { onSelectMode(option) },
                                    label = { Text(option.label) },
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                }
            }
            item {
                Surface(modifier = Modifier.fillMaxWidth(), shape = QuietReaderShapes.medium, color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.padding(QuietReaderSpacing.lg), verticalArrangement = Arrangement.spacedBy(QuietReaderSpacing.sm)) {
                        Text(stringResource(R.string.phone_tts), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.engine_value, selectedEngineLabel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(stringResource(R.string.voice_value, selectedVoiceLabel), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        LsButton(stringResource(R.string.choose_engine_and_voice), onOpenVoicePicker, Modifier.fillMaxWidth())
                        Row(horizontalArrangement = Arrangement.spacedBy(QuietReaderSpacing.sm)) {
                            LsOutlinedButton(stringResource(R.string.phone_settings), onOpenTtsSettings, Modifier.weight(1f))
                            LsOutlinedButton(stringResource(R.string.voice_preview), { selectedVoiceId?.let(onPreviewVoice) }, Modifier.weight(1f))
                        }
                    }
                }
            }
            item {
                Surface(modifier = Modifier.fillMaxWidth(), shape = QuietReaderShapes.medium, color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.padding(QuietReaderSpacing.lg), verticalArrangement = Arrangement.spacedBy(QuietReaderSpacing.sm)) {
                        Text(stringResource(R.string.google_cloud_advanced), fontWeight = FontWeight.Bold)
                        if (mode == PlaybackMode.ON_DEVICE) {
                            Text(
                                stringResource(R.string.cloud_inactive_description),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.extendedColors.textSecondary,
                            )
                            LsOutlinedButton(
                                stringResource(R.string.switch_to_standard),
                                { onSelectMode(PlaybackMode.GOOGLE_STANDARD) },
                                Modifier.fillMaxWidth(),
                            )
                            LsOutlinedButton(
                                stringResource(R.string.switch_to_wavenet),
                                { onSelectMode(PlaybackMode.GOOGLE_WAVENET) },
                                Modifier.fillMaxWidth(),
                            )
                        } else {
                            val availableCloudVoices = CloudVoiceCatalog.forMode(mode)
                            Text(
                                stringResource(R.string.cloud_active_description, mode.label),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.extendedColors.textSecondary,
                            )
                            Column(verticalArrangement = Arrangement.spacedBy(QuietReaderSpacing.xs)) {
                                availableCloudVoices.forEach { voice ->
                                    FilterChip(
                                        selected = selectedCloudVoiceId == voice.id,
                                        onClick = { onSelectCloudVoice(voice.id) },
                                        label = { Text(voice.label) },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                }
                            }
                            Text(
                                availableCloudVoices.firstOrNull { it.id == selectedCloudVoiceId }?.label.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                            )
                            LsOutlinedButton(stringResource(R.string.cloud_voice_preview), onCloudPreview, Modifier.fillMaxWidth())
                            CloudPreviewFeedbackPanel(previewFeedback, loadingStatus)
                        }
                        CloudSetupSection(
                            hasKey = hasKey,
                            cacheFiles = cacheFiles,
                            ttsStatus = ttsStatus,
                            keySaveResult = keySaveResult,
                            previewFeedback = previewFeedback,
                            loadingStatus = loadingStatus,
                            onSaveKey = onSaveKey,
                            onDeleteKey = onDeleteKey,
                            onCloudPreview = onCloudPreview,
                        )
                    }
                }
            }
            item {
                Surface(modifier = Modifier.fillMaxWidth(), shape = QuietReaderShapes.medium, color = MaterialTheme.colorScheme.surface) {
                    Column(modifier = Modifier.padding(QuietReaderSpacing.lg), verticalArrangement = Arrangement.spacedBy(QuietReaderSpacing.sm)) {
                        Text(stringResource(R.string.storage), fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.cache_summary, cacheFiles, formatCacheBytes(cacheBytes)))
                        Text(stringResource(R.string.cache_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textSecondary)
                        LsOutlinedButton(stringResource(R.string.clear_cache), onClearCache, Modifier.fillMaxWidth())
                        Text(stringResource(R.string.clear_cache_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textSecondary)
                    }
                }
            }
        }
    }
}

/**
 * Stepped BYOK setup for Google Cloud TTS. Renders a compact entry point (start setup when no key
 * is configured, or configured/replace/preview/delete when a key exists) and, once started, a
 * step-by-step wizard. The plaintext key and the transient consent live in [remember] only, so
 * neither is ever written to saved-instance state.
 */
@Composable
private fun CloudSetupSection(
    hasKey: Boolean,
    cacheFiles: Int,
    ttsStatus: String,
    keySaveResult: CloudKeySaveResult,
    previewFeedback: CloudPreviewFeedback,
    loadingStatus: String,
    onSaveKey: (String) -> Long,
    onDeleteKey: () -> Unit,
    onCloudPreview: () -> Unit,
) {
    val status = CloudSetupPolicy.statusModel(hasKey, cacheFiles)
    var wizard by remember { mutableStateOf<CloudSetupWizardState?>(null) }
    // The key itself is deliberately never in saveable state — only the id of the request awaiting
    // a verdict, which carries no secret and lets the final step reject a stale result.
    var pendingRequestId by rememberSaveable { mutableStateOf<Long?>(null) }
    var keyInput by remember { mutableStateOf("") }

    val active = wizard
    if (active == null) {
        CloudSetupEntry(
            status = status,
            onStart = { keyInput = ""; pendingRequestId = null; wizard = CloudSetupWizardState() },
            onPreview = onCloudPreview,
            onDeleteKey = onDeleteKey,
        )
    } else {
        CloudSetupWizard(
            state = active,
            hasKey = hasKey,
            keyInput = keyInput,
            ttsStatus = ttsStatus,
            keySaveResult = keySaveResult,
            pendingRequestId = pendingRequestId,
            previewFeedback = previewFeedback,
            loadingStatus = loadingStatus,
            onKeyInputChange = { keyInput = it },
            onConsentData = { wizard = active.acknowledgeDataTransfer(it) },
            onConsentCost = { wizard = active.acknowledgeBilling(it) },
            onNext = { wizard = active.next() },
            onPrev = { wizard = active.previous() },
            onClose = { keyInput = ""; pendingRequestId = null; wizard = null },
            onSave = {
                pendingRequestId = onSaveKey(keyInput)
                keyInput = ""
                wizard = active.goTo(CloudSetupStep.PreviewAndDone)
            },
            onPreview = onCloudPreview,
        )
    }
}

@Composable
private fun CloudSetupEntry(
    status: CloudSetupStatusModel,
    onStart: () -> Unit,
    onPreview: () -> Unit,
    onDeleteKey: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(QuietReaderSpacing.sm)) {
        if (status.configured) {
            Text(
                stringResource(R.string.cloud_setup_configured_badge),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.extendedColors.success,
            )
            Text(stringResource(R.string.cloud_setup_configured_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textSecondary)
            LsButton(
                text = if (status.cta == CloudSetupCta.ReplaceKey) stringResource(R.string.cloud_setup_replace) else stringResource(R.string.cloud_setup_start),
                onClick = onStart,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(QuietReaderSpacing.sm)) {
                if (status.canPreview) {
                    LsOutlinedButton(stringResource(R.string.cloud_voice_preview), onPreview, Modifier.weight(1f))
                }
                if (status.canDeleteKey) {
                    LsOutlinedButton(stringResource(R.string.delete_key), onDeleteKey, Modifier.weight(1f))
                }
            }
            Text(stringResource(R.string.cloud_setup_delete_notice), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textSecondary)
        } else {
            Text(stringResource(R.string.cloud_setup_entry_intro), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textSecondary)
            LsButton(stringResource(R.string.cloud_setup_start), onStart, Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun CloudSetupWizard(
    state: CloudSetupWizardState,
    hasKey: Boolean,
    keyInput: String,
    ttsStatus: String,
    keySaveResult: CloudKeySaveResult,
    pendingRequestId: Long?,
    previewFeedback: CloudPreviewFeedback,
    loadingStatus: String,
    onKeyInputChange: (String) -> Unit,
    onConsentData: (Boolean) -> Unit,
    onConsentCost: (Boolean) -> Unit,
    onNext: () -> Unit,
    onPrev: () -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
    onPreview: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(QuietReaderSpacing.sm)) {
        Text(
            stringResource(R.string.cloud_setup_step_indicator, CloudSetupPolicy.stepNumber(state.step), CloudSetupPolicy.stepCount),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        when (state.step) {
            CloudSetupStep.Intro -> {
                CloudSetupHeading(stringResource(R.string.cloud_setup_intro_title))
                Text(stringResource(R.string.cloud_setup_intro_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textSecondary)
            }
            CloudSetupStep.DataAndCostNotice -> {
                CloudSetupHeading(stringResource(R.string.cloud_setup_notice_title))
                Text(stringResource(R.string.cloud_setup_notice_data_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.warning)
                Text(stringResource(R.string.cloud_setup_notice_cost_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.warning)
                CloudSetupConsentRow(state.consent.textSentToGoogleAcknowledged, stringResource(R.string.cloud_setup_consent_data), onConsentData)
                CloudSetupConsentRow(state.consent.billingResponsibilityAcknowledged, stringResource(R.string.cloud_setup_consent_cost), onConsentCost)
            }
            CloudSetupStep.ConsoleRestrictions -> {
                CloudSetupHeading(stringResource(R.string.cloud_setup_restrictions_title))
                Text(stringResource(R.string.cloud_setup_restrictions_body), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textSecondary)
                CloudSetupBullet(stringResource(R.string.cloud_setup_restriction_api))
                CloudSetupBullet(stringResource(R.string.cloud_setup_restriction_app))
                CloudSetupBullet(stringResource(R.string.cloud_setup_restriction_package))
                CloudSetupBullet(stringResource(R.string.cloud_setup_restriction_sha))
            }
            CloudSetupStep.EnterAndSaveKey -> {
                CloudSetupHeading(stringResource(R.string.cloud_setup_enter_title))
                val canSave = CloudSetupPolicy.canSaveKey(state.consent, keyInput)
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = onKeyInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(if (hasKey) stringResource(R.string.replace_api_key) else stringResource(R.string.google_cloud_api_key)) },
                    visualTransformation = PasswordVisualTransformation(),
                    // A password keyboard keeps the key out of the IME's learned-word/autocorrect
                    // dictionary and off suggestion strips.
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        autoCorrectEnabled = false,
                    ),
                    singleLine = true,
                )
                Text(stringResource(R.string.cloud_setup_save_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textSecondary)
                if (!canSave) {
                    Text(
                        stringResource(R.string.cloud_setup_save_disabled_hint),
                        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.extendedColors.warning,
                    )
                }
                Button(
                    onClick = onSave,
                    enabled = canSave,
                    modifier = Modifier.fillMaxWidth().sizeIn(minHeight = QuietReaderSizes.MinTouchTarget),
                    contentPadding = PaddingValues(horizontal = QuietReaderSpacing.lg, vertical = QuietReaderSpacing.sm),
                ) {
                    Text(stringResource(R.string.cloud_setup_save), style = MaterialTheme.typography.labelMedium)
                }
            }
            CloudSetupStep.PreviewAndDone -> {
                // Never assume the save succeeded: the outcome counts only when the service's result
                // is stamped with this request's id, and a still-installed previous key is not proof
                // that a replacement worked.
                val result = CloudSetupPolicy.saveResultModel(pendingRequestId, keySaveResult, hasKey)
                val failed = result.outcome == CloudSaveOutcome.Failed
                CloudSetupHeading(stringResource(R.string.cloud_setup_result_title))
                Text(
                    text = when (result.outcome) {
                        CloudSaveOutcome.Succeeded -> stringResource(R.string.cloud_setup_result_success)
                        CloudSaveOutcome.Failed -> stringResource(R.string.cloud_setup_result_failed)
                        CloudSaveOutcome.Pending -> stringResource(R.string.cloud_setup_result_pending)
                    },
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (result.outcome == CloudSaveOutcome.Succeeded) {
                        MaterialTheme.extendedColors.textSecondary
                    } else {
                        MaterialTheme.extendedColors.warning
                    },
                )
                if (result.showExistingKeyCaveat) {
                    Text(
                        stringResource(R.string.cloud_setup_existing_key_caveat),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.extendedColors.warning,
                    )
                }
                if (result.canPreview) {
                    LsOutlinedButton(stringResource(R.string.cloud_voice_preview), onPreview, Modifier.fillMaxWidth())
                }
                // The service's actual message is always surfaced, for success and failure alike; a
                // failed key-save also styles it as an error even when the preview itself did not run.
                CloudPreviewFeedbackPanel(
                    feedback = previewFeedback,
                    loadingStatus = loadingStatus,
                    isError = failed || previewFeedback.isError,
                    message = "${stringResource(R.string.cloud_setup_service_status)}: $ttsStatus",
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(QuietReaderSpacing.sm)) {
            if (!CloudSetupPolicy.isFirstStep(state.step)) {
                LsOutlinedButton(stringResource(R.string.cloud_setup_prev), onPrev, Modifier.weight(1f))
            }
            when (state.step) {
                CloudSetupStep.EnterAndSaveKey -> LsOutlinedButton(stringResource(R.string.cloud_setup_close), onClose, Modifier.weight(1f))
                CloudSetupStep.PreviewAndDone -> LsButton(stringResource(R.string.cloud_setup_finish), onClose, Modifier.weight(1f))
                else -> {
                    LsOutlinedButton(stringResource(R.string.cloud_setup_close), onClose, Modifier.weight(1f))
                    LsButton(stringResource(R.string.cloud_setup_next), onNext, Modifier.weight(1f))
                }
            }
        }
    }
}

/**
 * Progress indicator plus feedback Surface for a cloud voice preview, rendered directly beneath the
 * `Cloud 음성 미리듣기` button so the outcome is found where the action was taken. Emits its children
 * as direct siblings of the surrounding [Column] so it inherits that column's spacing at full width.
 *
 * [isError] and [message] default to the [feedback] values, but the setup wizard overrides them to
 * fold a failed key-save into the error styling and to prefix the service status line.
 */
@Composable
private fun ColumnScope.CloudPreviewFeedbackPanel(
    feedback: CloudPreviewFeedback,
    loadingStatus: String,
    isError: Boolean = feedback.isError,
    message: String = feedback.message,
) {
    if (feedback.inProgress) {
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = loadingStatus
            },
        )
    }
    Surface(
        modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
        shape = QuietReaderShapes.small,
        color = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer,
    ) {
        Text(
            text = message,
            modifier = Modifier.padding(horizontal = QuietReaderSpacing.md, vertical = QuietReaderSpacing.sm),
            style = MaterialTheme.typography.bodySmall,
            color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}

@Composable
private fun CloudSetupHeading(text: String) {
    Text(
        text,
        // Bold + larger type reads as a heading visually; TalkBack needs it said out loud so
        // heading navigation can jump between setup steps.
        modifier = Modifier.semantics { heading() },
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.extendedColors.textPrimary,
    )
}

@Composable
private fun CloudSetupBullet(text: String) {
    Row {
        Text("•  ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textSecondary)
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.extendedColors.textPrimary)
    }
}

@Composable
private fun CloudSetupConsentRow(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = QuietReaderSizes.MinTouchTarget)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null)
        Text(
            label,
            modifier = Modifier.weight(1f).padding(start = QuietReaderSpacing.sm),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.extendedColors.textPrimary,
        )
    }
}

private fun formatCacheBytes(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024 -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
