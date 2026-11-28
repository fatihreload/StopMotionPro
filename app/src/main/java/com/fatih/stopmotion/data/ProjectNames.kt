package com.fatih.stopmotion.data

import java.util.Locale

/** Fungsi murni (mudah diuji) untuk nama proyek dan format tampilan. */
object ProjectNames {
    const val MAX_LENGTH = 40

    fun clean(raw: String): String =
        raw.trim().replace(Regex("\\s+"), " ").take(MAX_LENGTH).trim()

    private fun withSuffix(base: String, suffix: String): String =
        base.take((MAX_LENGTH - suffix.length).coerceAtLeast(1)).trimEnd() + suffix

    /** "Nama (salinan)", "Nama (salinan 2)", ... yang belum dipakai (tidak peka huruf besar/kecil). */
    fun copyName(base: String, existing: Collection<String>): String {
        val taken = existing.map { it.lowercase(Locale.ROOT) }.toSet()
        val root = clean(base).ifEmpty { "Proyek" }
        var candidate = withSuffix(root, " (salinan)")
        var i = 2
        while (candidate.lowercase(Locale.ROOT) in taken) {
            candidate = withSuffix(root, " (salinan $i)")
            i++
        }
        return candidate
    }

    /** Untuk impor cadangan: nama asli bila bebas, jika tidak "Nama (2)", "Nama (3)", ... */
    fun uniqueName(base: String, existing: Collection<String>): String {
        val taken = existing.map { it.lowercase(Locale.ROOT) }.toSet()
        val root = clean(base).ifEmpty { "Proyek" }
        if (root.lowercase(Locale.ROOT) !in taken) return root
        var i = 2
        var candidate = withSuffix(root, " ($i)")
        while (candidate.lowercase(Locale.ROOT) in taken) {
            i++
            candidate = withSuffix(root, " ($i)")
        }
        return candidate
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", bytes / 1073741824.0)
        bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / 1048576.0)
        bytes >= 1L shl 10 -> String.format(Locale.US, "%d KB", bytes / 1024)
        else -> "$bytes B"
    }

    /** Nama file aman untuk cadangan/ekspor. */
    fun fileSafe(name: String): String =
        name.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifBlank { "animasi" }
}
