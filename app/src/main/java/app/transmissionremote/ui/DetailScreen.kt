package app.transmissionremote.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import app.transmissionremote.data.St
import app.transmissionremote.data.TFile
import app.transmissionremote.data.TorrentDetail
import app.transmissionremote.util.formatBytes
import app.transmissionremote.util.formatDate
import app.transmissionremote.util.formatDuration
import app.transmissionremote.util.formatEta
import app.transmissionremote.util.formatPercent
import app.transmissionremote.util.formatRatio
import app.transmissionremote.util.formatSpeed
import org.json.JSONArray
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(vm: AppViewModel, sid: String, tid: Int, onBack: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    LaunchedEffect(sid, tid) {
        owner.repeatOnLifecycle(Lifecycle.State.STARTED) { vm.watchDetail(sid, tid) }
    }
    val d by vm.detail.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    MessageEffect(vm, snackbar)

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var removeDlg by remember { mutableStateOf(false) }
    val titles = listOf("Инфо", "Файлы", "Пиры", "Трекеры", "Настройки")

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(d?.base?.name ?: "…", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Назад") }
                },
                actions = {
                    d?.let { det ->
                        val paused = det.base.status == St.STOPPED
                        IconButton(onClick = {
                            vm.run(sid) { if (paused) it.start(listOf(tid)) else it.stop(listOf(tid)) }
                        }) { Icon(if (paused) Icons.Default.PlayArrow else Icons.Default.Pause, "Старт/пауза") }
                        IconButton(onClick = { removeDlg = true }) { Icon(Icons.Default.Delete, "Удалить") }
                    }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
                titles.forEachIndexed { i, title ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                }
            }
            val det = d
            if (det == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
                when (tab) {
                    0 -> InfoTab(det, sid, vm)
                    1 -> FilesTab(det, sid, vm)
                    2 -> PeersTab(det)
                    3 -> TrackersTab(det, sid, vm)
                    else -> SettingsTab(det, sid, vm)
                }
            }
        }
    }

    if (removeDlg) {
        var deleteData by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { removeDlg = false },
            title = { Text("Удалить торрент?") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = deleteData, onCheckedChange = { deleteData = it })
                    Text("Удалить также скачанные файлы")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    removeDlg = false
                    vm.run(sid) { it.remove(listOf(tid), deleteData) }
                    onBack()
                }) { Text("Удалить") }
            },
            dismissButton = { TextButton(onClick = { removeDlg = false }) { Text("Отмена") } },
        )
    }
}

// ---------------------------------------------------------------- Инфо

@Composable
private fun InfoTab(d: TorrentDetail, sid: String, vm: AppViewModel) {
    val t = d.base
    val clipboard = LocalClipboardManager.current
    var moveDlg by remember { mutableStateOf(false) }
    val ids = listOf(t.id)

    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(onClick = { vm.run(sid) { it.verify(ids) } }, label = { Text("Проверить") }, leadingIcon = { Icon(Icons.Default.Refresh, null) })
            AssistChip(onClick = { vm.run(sid) { it.reannounce(ids) } }, label = { Text("Реанонс") })
            AssistChip(onClick = { vm.run(sid) { it.startNow(ids) } }, label = { Text("Старт сразу") })
            AssistChip(onClick = { moveDlg = true }, label = { Text("Переместить") }, leadingIcon = { Icon(Icons.Default.Folder, null) })
            AssistChip(onClick = { vm.run(sid) { it.queueMove("top", ids) } }, label = { Text("В начало очереди") })
            if (d.magnet.isNotBlank()) {
                AssistChip(
                    onClick = { clipboard.setText(AnnotatedString(d.magnet)) },
                    label = { Text("Magnet") },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, null) },
                )
            }
        }
        LinearProgressIndicator(
            progress = { t.percentDone.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        KeyValue("Статус", statusLabel(t))
        if (t.error != 0) KeyValue("Ошибка", t.errorString)
        KeyValue("Прогресс", formatPercent(t.percentDone))
        KeyValue("Размер", "${formatBytes(t.totalSize)} (нужно ${formatBytes(t.sizeWhenDone)})")
        KeyValue("Скачано / отдано", "${formatBytes(t.downloadedEver)} / ${formatBytes(t.uploadedEver)}")
        KeyValue("Рейтинг", formatRatio(t.uploadRatio))
        KeyValue("Скорость", "↓ ${formatSpeed(t.rateDownload)}  ↑ ${formatSpeed(t.rateUpload)}")
        if (t.status == St.DOWNLOAD) KeyValue("Осталось", formatEta(t.eta))
        KeyValue("Пиры", "${t.peersConnected} подключено")
        KeyValue("Папка", t.downloadDir)
        KeyValue("Хэш", t.hashString)
        KeyValue("Приватный", if (d.isPrivate) "да" else "нет")
        KeyValue("Добавлен", formatDate(t.addedDate))
        KeyValue("Завершён", formatDate(t.doneDate))
        KeyValue("Последняя активность", formatDate(d.activityDate))
        KeyValue("Создан", "${formatDate(d.dateCreated)}${if (d.creator.isNotBlank()) " · ${d.creator}" else ""}")
        KeyValue("Время загрузки / раздачи", "${formatDuration(d.secondsDownloading)} / ${formatDuration(d.secondsSeeding)}")
        KeyValue("Части", "${d.pieceCount} × ${formatBytes(d.pieceSize)}")
        KeyValue("Проверено / повреждено", "${formatBytes(d.haveValid)} / ${formatBytes(d.corruptEver)}")
        if (t.labels.isNotEmpty()) KeyValue("Метки", t.labels.joinToString(", "))
        if (d.comment.isNotBlank()) KeyValue("Комментарий", d.comment)
    }

    if (moveDlg) {
        TextInputDialog(
            title = "Переместить данные",
            label = "Новая папка на сервере",
            initial = t.downloadDir,
            confirm = "Переместить",
            onDismiss = { moveDlg = false },
            onConfirm = { loc ->
                moveDlg = false
                vm.run(sid, "Перемещение начато") { it.setLocation(ids, loc, true) }
            },
        )
    }
}

// ---------------------------------------------------------------- Файлы

private class Dir(val name: String, val path: String) {
    val dirs = LinkedHashMap<String, Dir>()
    val files = mutableListOf<TFile>()
    /** все файлы внутри папки, включая вложенные */
    val all = mutableListOf<TFile>()
}

private sealed class TreeRow {
    class Folder(val dir: Dir, val depth: Int) : TreeRow()
    class FileItem(val f: TFile, val depth: Int) : TreeRow()
}

private fun buildTree(files: List<TFile>): Dir {
    val root = Dir("", "")
    for (f in files) {
        val parts = f.name.split('/')
        var cur = root
        cur.all += f
        for (i in 0 until parts.size - 1) {
            val part = parts[i]
            val parent = cur
            cur = parent.dirs.getOrPut(part) { Dir(part, parent.path + part + "/") }
            cur.all += f
        }
        cur.files += f
    }
    return root
}

private fun flatten(d: Dir, depth: Int, collapsed: Set<String>, forceOpen: Boolean, out: MutableList<TreeRow>) {
    for (sub in d.dirs.values.sortedBy { it.name.lowercase() }) {
        out += TreeRow.Folder(sub, depth)
        if (forceOpen || sub.path !in collapsed) flatten(sub, depth + 1, collapsed, forceOpen, out)
    }
    for (f in d.files.sortedBy { it.name.lowercase() }) out += TreeRow.FileItem(f, depth)
}

private fun allDirPaths(d: Dir): List<String> = d.dirs.values.flatMap { listOf(it.path) + allDirPaths(it) }

@Composable
private fun FilesTab(d: TorrentDetail, sid: String, vm: AppViewModel) {
    var filter by remember { mutableStateOf("") }
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 — со структурой папок, 1 — все файлы списком
    var sort by rememberSaveable { mutableIntStateOf(0) } // 0 размер ↓, 1 размер ↑, 2 имя, 3 прогресс
    var collapsed by remember { mutableStateOf(setOf<String>()) }
    var renaming by remember { mutableStateOf<TFile?>(null) }
    var renamingDir by remember { mutableStateOf<Dir?>(null) }
    val tid = d.base.id

    val files = remember(d.files, filter) {
        d.files.filter { filter.isBlank() || it.name.contains(filter, ignoreCase = true) }
    }
    val root = remember(files) { buildTree(files) }
    val rows = remember(root, collapsed, filter, mode) {
        val out = mutableListOf<TreeRow>()
        if (mode == 0) flatten(root, 0, collapsed, filter.isNotBlank(), out)
        out
    }
    val flat = remember(files, sort, mode) {
        if (mode != 1) {
            emptyList()
        } else {
            when (sort) {
                0 -> files.sortedByDescending { it.length }
                1 -> files.sortedBy { it.length }
                2 -> files.sortedBy { it.name.lowercase() }
                else -> files.sortedByDescending { if (it.length > 0) it.done.toDouble() / it.length else 1.0 }
            }
        }
    }

    fun setWanted(list: List<TFile>, wanted: Boolean) {
        if (list.isEmpty()) return
        val key = if (wanted) "files-wanted" else "files-unwanted"
        vm.run(sid) { it.torrentSet(listOf(tid), JSONObject().put(key, JSONArray(list.map { f -> f.index }))) }
    }

    fun setPriority(list: List<TFile>, p: Int) {
        if (list.isEmpty()) return
        val key = when (p) { 1 -> "priority-high"; -1 -> "priority-low"; else -> "priority-normal" }
        vm.run(sid) { it.torrentSet(listOf(tid), JSONObject().put(key, JSONArray(list.map { f -> f.index }))) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                label = { Text("Фильтр по имени") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { setWanted(files, true) }) { Text("Все") }
            TextButton(onClick = { setWanted(files, false) }) { Text("Ничего") }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = mode == 0,
                onClick = { mode = 0 },
                label = { Text("Папки") },
                leadingIcon = { Icon(Icons.Default.Folder, null) },
            )
            FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Все файлы") })
            if (mode == 0) {
                AssistChip(onClick = { collapsed = allDirPaths(root).toSet() }, label = { Text("Свернуть все") })
                AssistChip(onClick = { collapsed = emptySet() }, label = { Text("Развернуть все") })
            } else {
                listOf("Размер ↓", "Размер ↑", "Имя", "Прогресс").forEachIndexed { i, l ->
                    FilterChip(selected = sort == i, onClick = { sort = i }, label = { Text(l) })
                }
            }
        }
        Row(
            Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AssistChip(
                onClick = {
                    val todo = d.files.filter { it.wanted && it.done < it.length }.sortedBy { it.index }
                    if (todo.isNotEmpty()) {
                        val args = JSONObject()
                            .put("priority-high", JSONArray(todo.take(1).map { it.index }))
                            .put("priority-normal", JSONArray(todo.drop(1).take(1).map { it.index }))
                            .put("priority-low", JSONArray(todo.drop(2).map { it.index }))
                        vm.run(sid, "Файлы будут качаться по порядку") { it.torrentSet(listOf(tid), args) }
                    }
                },
                label = { Text("Качать по порядку") },
            )
            AssistChip(
                onClick = { setPriority(d.files, 0) },
                label = { Text("Сбросить приоритеты") },
            )
        }
        Text(
            "«По порядку»: первый незавершённый файл — высокий приоритет, второй — обычный, остальные — низкий. Когда файл докачается, нажмите снова.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
        val wantedFiles = d.files.filter { it.wanted }
        Text(
            "Выбрано ${wantedFiles.size} из ${d.files.size} · ${formatBytes(wantedFiles.sumOf { it.length })} из ${formatBytes(d.files.sumOf { it.length })}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
        )
        LazyColumn(Modifier.fillMaxSize()) {
            if (mode == 0) {
                items(
                    rows,
                    key = {
                        when (it) {
                            is TreeRow.Folder -> "d:" + it.dir.path
                            is TreeRow.FileItem -> "f:" + it.f.index
                        }
                    },
                ) { r ->
                    when (r) {
                        is TreeRow.Folder -> FolderRow(
                            dir = r.dir,
                            depth = r.depth,
                            expanded = filter.isNotBlank() || r.dir.path !in collapsed,
                            onToggle = {
                                collapsed = if (r.dir.path in collapsed) collapsed - r.dir.path else collapsed + r.dir.path
                            },
                            onWanted = { setWanted(r.dir.all, it) },
                            onPriority = { setPriority(r.dir.all, it) },
                            onRename = { renamingDir = r.dir },
                        )

                        is TreeRow.FileItem -> FileRow(
                            f = r.f,
                            depth = r.depth,
                            showPath = false,
                            onWanted = { setWanted(listOf(r.f), it) },
                            onPriority = { setPriority(listOf(r.f), it) },
                            onRename = { renaming = r.f },
                        )
                    }
                    HorizontalDivider()
                }
            } else {
                items(flat, key = { it.index }) { f ->
                    FileRow(
                        f = f,
                        depth = 0,
                        showPath = true,
                        onWanted = { setWanted(listOf(f), it) },
                        onPriority = { setPriority(listOf(f), it) },
                        onRename = { renaming = f },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    renaming?.let { f ->
        TextInputDialog(
            title = "Переименовать файл",
            label = "Новое имя",
            initial = f.name.substringAfterLast('/'),
            confirm = "Переименовать",
            onDismiss = { renaming = null },
            onConfirm = { newName ->
                renaming = null
                vm.run(sid, "Переименовано") { it.renamePath(tid, f.name, newName) }
            },
        )
    }
    renamingDir?.let { dir ->
        TextInputDialog(
            title = "Переименовать папку",
            label = "Новое имя",
            initial = dir.name,
            confirm = "Переименовать",
            onDismiss = { renamingDir = null },
            onConfirm = { newName ->
                renamingDir = null
                vm.run(sid, "Переименовано") { it.renamePath(tid, dir.path.trimEnd('/'), newName) }
            },
        )
    }
}

@Composable
private fun FolderRow(
    dir: Dir,
    depth: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    onWanted: (Boolean) -> Unit,
    onPriority: (Int) -> Unit,
    onRename: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val total = dir.all.sumOf { it.length }
    val done = dir.all.sumOf { it.done }
    val wantedN = dir.all.count { it.wanted }
    val state = when (wantedN) {
        0 -> ToggleableState.Off
        dir.all.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(start = (4 + depth * 16).dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(if (expanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight, null)
        TriStateCheckbox(state = state, onClick = { onWanted(state != ToggleableState.On) })
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Folder, null, Modifier.size(18.dp))
                Text(
                    dir.name,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            LinearProgressIndicator(
                progress = { if (total > 0) (done.toFloat() / total).coerceIn(0f, 1f) else 1f },
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            )
            Text(
                "${dir.all.size} файл(ов) · ${formatBytes(total)} · выбрано $wantedN",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, "Действия с папкой") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Приоритет папки: высокий") }, onClick = { menu = false; onPriority(1) })
                DropdownMenuItem(text = { Text("Приоритет папки: обычный") }, onClick = { menu = false; onPriority(0) })
                DropdownMenuItem(text = { Text("Приоритет папки: низкий") }, onClick = { menu = false; onPriority(-1) })
                DropdownMenuItem(text = { Text("Переименовать папку…") }, onClick = { menu = false; onRename() })
            }
        }
    }
}

@Composable
private fun FileRow(
    f: TFile,
    depth: Int,
    showPath: Boolean,
    onWanted: (Boolean) -> Unit,
    onPriority: (Int) -> Unit,
    onRename: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    val folder = f.name.substringBeforeLast('/', "")
    val name = f.name.substringAfterLast('/')
    val progress = if (f.length > 0) f.done.toFloat() / f.length else 1f
    Row(
        Modifier.fillMaxWidth().padding(start = (4 + depth * 16).dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = f.wanted, onCheckedChange = onWanted)
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            Text(name, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
            if (showPath && folder.isNotEmpty()) {
                Text(
                    folder,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
            Text(
                "${formatBytes(f.done)} / ${formatBytes(f.length)} · приоритет: ${when (f.priority) { 1 -> "высокий"; -1 -> "низкий"; else -> "обычный" }}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.Edit, "Действия") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Приоритет: высокий") }, onClick = { menu = false; onPriority(1) })
                DropdownMenuItem(text = { Text("Приоритет: обычный") }, onClick = { menu = false; onPriority(0) })
                DropdownMenuItem(text = { Text("Приоритет: низкий") }, onClick = { menu = false; onPriority(-1) })
                DropdownMenuItem(text = { Text("Переименовать…") }, onClick = { menu = false; onRename() })
            }
        }
    }
}

// ---------------------------------------------------------------- Пиры

@Composable
private fun PeersTab(d: TorrentDetail) {
    if (d.peers.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Нет подключённых пиров", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        return
    }
    LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(d.peers) { p ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                Column(Modifier.padding(12.dp).fillMaxWidth()) {
                    Text("${p.address}:${p.port}", style = MaterialTheme.typography.bodyLarge)
                    Text(p.client, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(progress = { p.progress.toFloat().coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                    Text(
                        "${formatPercent(p.progress)} · ↓ ${formatSpeed(p.rateDown)} ↑ ${formatSpeed(p.rateUp)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "[${p.flags}]${if (p.encrypted) " · шифрование" else ""}${if (p.utp) " · µTP" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Трекеры

@Composable
private fun TrackersTab(d: TorrentDetail, sid: String, vm: AppViewModel) {
    var addDlg by remember { mutableStateOf(false) }
    val tid = d.base.id
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { addDlg = true }) {
                Icon(Icons.Default.Add, null)
                Text("Добавить", Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = { vm.addTrackersFromSource(listOf("$sid|$tid")) }) { Text("Из источника") }
        }
        LazyColumn(contentPadding = PaddingValues(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(d.trackers, key = { it.id }) { tr ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
                    Row(Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(tr.host.ifBlank { tr.announce }, style = MaterialTheme.typography.bodyLarge)
                            Text(tr.announce, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(
                                (if (tr.result.isBlank()) "—" else tr.result) +
                                    (if (tr.seeders >= 0) " · сидов ${tr.seeders}, личей ${tr.leechers}" else ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (tr.ok || tr.result.isBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                            )
                            Text(
                                "Анонс: ${formatDate(tr.lastAnnounce)} · следующий: ${formatDate(tr.nextAnnounce)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = {
                            vm.run(sid, "Трекер удалён") { it.torrentSet(listOf(tid), JSONObject().put("trackerRemove", JSONArray(listOf(tr.id)))) }
                        }) { Icon(Icons.Default.Delete, "Удалить трекер") }
                    }
                }
            }
        }
    }
    if (addDlg) {
        TrackersDialog(onDismiss = { addDlg = false }) { urls ->
            addDlg = false
            vm.run(sid, "Трекеров добавлено: ${urls.size}") { it.addTrackers(listOf(tid), urls) }
        }
    }
}

// ---------------------------------------------------------------- Настройки торрента

@Composable
private fun SettingsTab(d: TorrentDetail, sid: String, vm: AppViewModel) {
    val key = d.base.hashString
    var dlOn by remember(key) { mutableStateOf(d.downloadLimited) }
    var dl by remember(key) { mutableStateOf(d.downloadLimit.toString()) }
    var ulOn by remember(key) { mutableStateOf(d.uploadLimited) }
    var ul by remember(key) { mutableStateOf(d.uploadLimit.toString()) }
    var honors by remember(key) { mutableStateOf(d.honorsSessionLimits) }
    var ratioMode by remember(key) { mutableStateOf(d.seedRatioMode) }
    var ratio by remember(key) { mutableStateOf(d.seedRatioLimit.toString()) }
    var idleMode by remember(key) { mutableStateOf(d.seedIdleMode) }
    var idle by remember(key) { mutableStateOf(d.seedIdleLimit.toString()) }
    var peerLimit by remember(key) { mutableStateOf(d.peerLimit.toString()) }
    var priority by remember(key) { mutableStateOf(d.base.bandwidthPriority) }
    var labels by remember(key) { mutableStateOf(d.base.labels.joinToString(", ")) }
    var seq by remember(key) { mutableStateOf(d.sequential) }

    val num = KeyboardOptions(keyboardType = KeyboardType.Number)
    val dec = KeyboardOptions(keyboardType = KeyboardType.Decimal)

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (d.seqKey != null) {
            SwitchRow("Последовательная загрузка (части по порядку)", seq) { seq = it }
        } else {
            Text(
                "Последовательная загрузка недоступна: нужен Transmission 4.1 или новее. Для файлов можно использовать «Качать по порядку» во вкладке «Файлы».",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DropdownField("Приоритет", listOf(-1 to "Низкий", 0 to "Обычный", 1 to "Высокий"), priority) { priority = it }

        SwitchRow("Ограничить загрузку", dlOn) { dlOn = it }
        if (dlOn) OutlinedTextField(dl, { dl = it }, label = { Text("Лимит загрузки, КБ/с") }, keyboardOptions = num, singleLine = true, modifier = Modifier.fillMaxWidth())
        SwitchRow("Ограничить отдачу", ulOn) { ulOn = it }
        if (ulOn) OutlinedTextField(ul, { ul = it }, label = { Text("Лимит отдачи, КБ/с") }, keyboardOptions = num, singleLine = true, modifier = Modifier.fillMaxWidth())
        SwitchRow("Учитывать общие лимиты сессии", honors) { honors = it }

        DropdownField("Лимит рейтинга", listOf(0 to "Как в настройках сервера", 1 to "Свой", 2 to "Без ограничения"), ratioMode) { ratioMode = it }
        if (ratioMode == 1) OutlinedTextField(ratio, { ratio = it }, label = { Text("Рейтинг") }, keyboardOptions = dec, singleLine = true, modifier = Modifier.fillMaxWidth())

        DropdownField("Простой при раздаче", listOf(0 to "Как в настройках сервера", 1 to "Свой", 2 to "Без ограничения"), idleMode) { idleMode = it }
        if (idleMode == 1) OutlinedTextField(idle, { idle = it }, label = { Text("Минут простоя") }, keyboardOptions = num, singleLine = true, modifier = Modifier.fillMaxWidth())

        OutlinedTextField(peerLimit, { peerLimit = it }, label = { Text("Макс. пиров") }, keyboardOptions = num, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(labels, { labels = it }, label = { Text("Метки (через запятую)") }, singleLine = true, modifier = Modifier.fillMaxWidth())

        Button(
            onClick = {
                val args = JSONObject()
                    .put("bandwidthPriority", priority)
                    .put("downloadLimited", dlOn)
                    .put("uploadLimited", ulOn)
                    .put("honorsSessionLimits", honors)
                    .put("seedRatioMode", ratioMode)
                    .put("seedIdleMode", idleMode)
                    .put("labels", JSONArray(labels.split(',').map { it.trim() }.filter { it.isNotEmpty() }))
                d.seqKey?.let { args.put(it, seq) }
                dl.trim().toIntOrNull()?.let { args.put("downloadLimit", it) }
                ul.trim().toIntOrNull()?.let { args.put("uploadLimit", it) }
                ratio.trim().replace(',', '.').toDoubleOrNull()?.let { args.put("seedRatioLimit", it) }
                idle.trim().toIntOrNull()?.let { args.put("seedIdleLimit", it) }
                peerLimit.trim().toIntOrNull()?.let { args.put("peer-limit", it) }
                vm.run(sid, "Сохранено") { it.torrentSet(listOf(d.base.id), args) }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Сохранить") }
    }
}

@Composable
private fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
