package com.fatih.stopmotion.ui.home

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.fatih.stopmotion.AppContainer
import com.fatih.stopmotion.data.ProjectManager
import com.fatih.stopmotion.data.ProjectNames
import com.fatih.stopmotion.data.ProjectSort
import com.fatih.stopmotion.data.ProjectSummary
import com.fatih.stopmotion.ui.theme.FatihColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(container: AppContainer, onOpenProject: (Long) -> Unit) {
    val context = LocalContext.current
    val vm: HomeViewModel = viewModel(factory = HomeViewModelFactory(container))
    val projects by vm.projects.collectAsStateWithLifecycle()
    val all by vm.allProjects.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val busy by vm.busy.collectAsStateWithLifecycle()
    val progress by vm.progress.collectAsStateWithLifecycle()
    val info by vm.info.collectAsStateWithLifecycle()
    val storage by vm.storage.collectAsStateWithLifecycle()

    var showNew by remember { mutableStateOf(false) }
    var showTutorial by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<ProjectSummary?>(null) }
    var toRename by remember { mutableStateOf<ProjectSummary?>(null) }
    var pendingBackup by remember { mutableStateOf<ProjectSummary?>(null) }

    LaunchedEffect(Unit) {
        vm.messages.collect { Toast.makeText(context, it, Toast.LENGTH_SHORT).show() }
    }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val target = pendingBackup
        pendingBackup = null
        if (uri != null && target != null) {
            vm.backup(target.project.id) { context.contentResolver.openOutputStream(uri) }
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.restore { context.contentResolver.openInputStream(uri) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(FatihColors.Black)
            .systemBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            FatihLogo()
            Spacer(Modifier.size(14.dp))
            Column(Modifier.weight(1f)) {
                Text("FATIH", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 4.sp)
                Text(
                    "STOP MOTION STUDIO",
                    fontSize = 11.sp,
                    letterSpacing = 2.sp,
                    color = FatihColors.TextSecondary
                )
            }
            IconButton(onClick = { vm.loadStorage(); showSettings = true }) {
                Icon(Icons.Filled.Settings, contentDescription = "Pengaturan", tint = FatihColors.TextSecondary)
            }
            IconButton(onClick = { showTutorial = true }) {
                Icon(Icons.Filled.HelpOutline, contentDescription = "Tutorial", tint = FatihColors.Blue)
            }
        }

        Spacer(Modifier.height(24.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { showNew = true },
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = FatihColors.ShutterRed, contentColor = FatihColors.OnAccent)
            ) {
                Icon(Icons.Filled.Add, null)
                Spacer(Modifier.size(8.dp))
                Text("Proyek Baru", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = {
                    restoreLauncher.launch(
                        arrayOf("application/zip", "application/x-zip-compressed", "application/octet-stream")
                    )
                },
                modifier = Modifier.weight(1f).height(54.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Filled.Unarchive, null, tint = FatihColors.Purple)
                Spacer(Modifier.size(8.dp))
                Text("Impor")
            }
        }

        Spacer(Modifier.height(20.dp))

        if (all.isNotEmpty()) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it.take(40) },
                placeholder = { Text("Cari proyek", color = FatihColors.TextSecondary) },
                leadingIcon = { Icon(Icons.Filled.Search, null, tint = FatihColors.TextSecondary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Proyek (${projects.size})",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                ProjectSort.entries.forEach { s ->
                    SortChip(s.label, sort == s) { vm.sort.value = s }
                    Spacer(Modifier.width(6.dp))
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (all.isEmpty()) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.Movie, null, tint = FatihColors.SurfaceHigh, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("Belum ada proyek", color = FatihColors.TextSecondary)
                }
            }
        } else if (projects.isEmpty()) {
            Box(Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                Text("Tidak ada proyek yang cocok", color = FatihColors.TextSecondary)
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(projects, key = { it.project.id }) { item ->
                    ProjectCard(
                        item = item,
                        onOpen = { vm.openProject(item.project.id, onOpenProject) },
                        onRename = { toRename = item },
                        onDuplicate = { vm.duplicate(item.project.id) },
                        onInfo = { vm.showInfo(item.project.id) },
                        onBackup = {
                            pendingBackup = item
                            backupLauncher.launch(ProjectNames.fileSafe(item.project.name) + "_cadangan.zip")
                        },
                        onDelete = { toDelete = item }
                    )
                }
            }
        }
    }

    if (showNew) {
        NameDialog(
            title = "Proyek Baru",
            initial = "",
            confirmLabel = "Buat",
            allowBlank = true,
            onDismiss = { showNew = false },
            onConfirm = { name ->
                showNew = false
                vm.createProject(name) { id -> onOpenProject(id) }
            }
        )
    }

    toRename?.let { item ->
        NameDialog(
            title = "Ganti Nama",
            initial = item.project.name,
            confirmLabel = "Simpan",
            allowBlank = false,
            onDismiss = { toRename = null },
            onConfirm = { name ->
                toRename = null
                vm.rename(item.project.id, name)
            }
        )
    }

    toDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            containerColor = FatihColors.SurfaceHigh,
            title = { Text("Hapus proyek?") },
            text = { Text("\"${item.project.name}\" beserta ${item.frameCount} frame akan dihapus permanen. Buat cadangan dulu bila perlu.") },
            confirmButton = {
                TextButton(onClick = { vm.deleteProject(item.project.id); toDelete = null }) {
                    Text("Hapus", color = FatihColors.ShutterRed)
                }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Batal") } }
        )
    }

    info?.let { i ->
        AlertDialog(
            onDismissRequest = { vm.clearInfo() },
            containerColor = FatihColors.SurfaceHigh,
            title = { Text(i.project.name) },
            text = { InfoBody(i) },
            confirmButton = { TextButton(onClick = { vm.clearInfo() }) { Text("Tutup") } }
        )
    }

    if (showSettings) {
        SettingsDialog(
            storage = storage,
            version = remember {
                runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
                    .getOrNull() ?: "-"
            },
            onClearExports = { vm.clearExports() },
            onDismiss = { showSettings = false }
        )
    }

    if (busy != null) {
        AlertDialog(
            onDismissRequest = {},
            containerColor = FatihColors.SurfaceHigh,
            title = { Text(busy ?: "") },
            text = {
                Column {
                    val p = progress
                    if (p == null) CircularProgressIndicator()
                    else LinearProgressIndicator(progress = { p }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("Mohon tunggu, jangan tutup aplikasi.", fontSize = 12.sp, color = FatihColors.TextSecondary)
                }
            },
            confirmButton = {}
        )
    }

    if (showTutorial) {
        AlertDialog(
            onDismissRequest = { showTutorial = false },
            containerColor = FatihColors.SurfaceHigh,
            title = { Text("Cara Singkat Penggunaan") },
            text = {
                Text(
                    "1. Tekan Proyek Baru lalu beri nama.\n\n" +
                        "2. Letakkan HP pada tripod atau sandaran yang stabil.\n\n" +
                        "3. Tekan tombol shutter merah untuk mengambil satu frame.\n\n" +
                        "4. Gerakkan objek sedikit saja, lalu ambil frame berikutnya. Ulangi.\n\n" +
                        "5. Semua frame tersimpan otomatis dan tetap ada walau aplikasi ditutup.\n\n" +
                        "6. Di daftar proyek, tekan titik tiga untuk ganti nama, duplikat, info, cadangan, atau hapus. " +
                        "Tombol Impor memulihkan proyek dari file cadangan.\n\n" +
                        "Tips: gunakan timer 3 detik agar HP tidak bergetar saat menekan shutter.",
                    fontSize = 14.sp
                )
            },
            confirmButton = { TextButton(onClick = { showTutorial = false }) { Text("Mengerti") } }
        )
    }
}

@Composable
private fun InfoBody(i: ProjectManager.Info) {
    val locale = Locale("id", "ID")
    val fmt = remember { SimpleDateFormat("dd MMM yyyy, HH:mm", locale) }
    val seconds = i.ticks / i.project.fps.coerceAtLeast(1).toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        InfoRow("Frame", "${i.frameCount} (${i.ticks} tick)")
        InfoRow("Durasi", String.format(Locale.US, "%.1f detik pada %d FPS", seconds, i.project.fps))
        InfoRow("Track audio", "${i.audioCount}")
        InfoRow("Ukuran", ProjectNames.formatBytes(i.bytes))
        InfoRow("Dibuat", fmt.format(Date(i.project.createdAt)))
        InfoRow("Diubah", fmt.format(Date(i.project.updatedAt)))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row {
        Text(label, fontSize = 13.sp, color = FatihColors.TextSecondary, modifier = Modifier.width(92.dp))
        Text(value, fontSize = 13.sp)
    }
}

@Composable
private fun SettingsDialog(
    storage: ProjectManager.StorageStats?,
    version: String,
    onClearExports: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FatihColors.SurfaceHigh,
        title = { Text("Pengaturan") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Penyimpanan", fontWeight = FontWeight.SemiBold, color = FatihColors.Blue)
                if (storage == null) CircularProgressIndicator(Modifier.size(20.dp)) else {
                    InfoRow("Dipakai proyek", ProjectNames.formatBytes(storage.projectsBytes))
                    InfoRow("Ruang kosong", ProjectNames.formatBytes(storage.freeBytes))
                }
                OutlinedButton(onClick = onClearExports) { Text("Bersihkan hasil ekspor") }
                Text(
                    "Hanya menghapus file MP4/GIF/ZIP hasil ekspor. Foto dan proyek tidak tersentuh.",
                    fontSize = 11.sp,
                    color = FatihColors.TextSecondary
                )
                Spacer(Modifier.height(4.dp))
                InfoRow("Versi", version)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Tutup") } }
    )
}

@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) FatihColors.Purple else FatihColors.SurfaceHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(label, fontSize = 12.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun FatihLogo() {
    Box(
        Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(FatihColors.Purple, FatihColors.Blue))),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Filled.Movie, contentDescription = null, tint = Color.White, modifier = Modifier.size(30.dp))
    }
}

@Composable
private fun ProjectCard(
    item: ProjectSummary,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onInfo: () -> Unit,
    onBackup: () -> Unit,
    onDelete: () -> Unit
) {
    val date = remember(item.project.updatedAt) {
        SimpleDateFormat("dd MMM yyyy, HH:mm", Locale("id", "ID")).format(Date(item.project.updatedAt))
    }
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(FatihColors.Surface)
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(width = 72.dp, height = 96.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(FatihColors.SurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            val thumb = item.project.thumbnailPath
            if (thumb != null && File(thumb).exists()) {
                AsyncImage(
                    model = File(thumb),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(Icons.Filled.Movie, null, tint = FatihColors.TextSecondary)
            }
        }
        Spacer(Modifier.size(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.project.name,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${item.frameCount} frame  •  ${item.project.fps} FPS",
                fontSize = 13.sp,
                color = FatihColors.Blue
            )
            Text(date, fontSize = 12.sp, color = FatihColors.TextSecondary)
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Menu proyek", tint = FatihColors.TextSecondary)
            }
            DropdownMenu(
                expanded = menu,
                onDismissRequest = { menu = false },
                modifier = Modifier.background(FatihColors.SurfaceHigh)
            ) {
                MenuItem("Buka", Icons.Filled.FolderOpen) { menu = false; onOpen() }
                MenuItem("Ganti nama", Icons.Filled.Edit) { menu = false; onRename() }
                MenuItem("Duplikat", Icons.Filled.ContentCopy) { menu = false; onDuplicate() }
                MenuItem("Info", Icons.Filled.Info) { menu = false; onInfo() }
                MenuItem("Cadangkan", Icons.Filled.Backup) { menu = false; onBackup() }
                MenuItem("Hapus", Icons.Filled.Delete, FatihColors.ShutterRed) { menu = false; onDelete() }
            }
        }
    }
}

@Composable
private fun MenuItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = FatihColors.TextPrimary,
    onClick: () -> Unit
) {
    DropdownMenuItem(
        text = { Text(label, color = tint) },
        leadingIcon = { Icon(icon, null, tint = tint) },
        onClick = onClick
    )
}

@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    allowBlank: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = FatihColors.SurfaceHigh,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(ProjectNames.MAX_LENGTH) },
                label = { Text("Nama proyek") },
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name) },
                enabled = allowBlank || name.isNotBlank()
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Batal") } }
    )
}
