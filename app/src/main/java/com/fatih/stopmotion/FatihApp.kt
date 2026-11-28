package com.fatih.stopmotion

import android.app.Application
import android.content.ComponentCallbacks2
import android.content.Context
import com.fatih.stopmotion.data.AppDatabase
import com.fatih.stopmotion.data.AudioRepository
import com.fatih.stopmotion.data.ChromaRepository
import com.fatih.stopmotion.data.LayerRepository
import com.fatih.stopmotion.data.FrameBitmapCache
import com.fatih.stopmotion.data.FrameImageCache
import com.fatih.stopmotion.data.ProjectManager
import com.fatih.stopmotion.data.ProjectRepository
import com.fatih.stopmotion.data.ProjectStorage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FatihApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Riwayat undo tidak bertahan antar sesi, jadi frame yang dihapus lunak aman dibersihkan.
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            runCatching { container.repository.purgeAllDeleted() }
        }
    }

    /** Saat memori sistem menipis, buang cache bitmap agar aplikasi tidak dimatikan sistem. */
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            container.bitmapCache.clear()
            container.imageCache.clear()
        }
    }
}

/** Penyedia dependency sederhana (tanpa framework DI). */
class AppContainer(context: Context) {
    val storage = ProjectStorage(context.applicationContext)
    val database = AppDatabase.build(context.applicationContext)
    val repository = ProjectRepository(database, storage)
    val audioRepository = AudioRepository(context.applicationContext, database, storage)
    val chromaRepository = ChromaRepository(context.applicationContext, database, storage)
    val layerRepository = LayerRepository(context.applicationContext, database, storage)
    val projectManager = ProjectManager(database, storage)
    val bitmapCache = FrameBitmapCache()
    val imageCache = FrameImageCache()
}
