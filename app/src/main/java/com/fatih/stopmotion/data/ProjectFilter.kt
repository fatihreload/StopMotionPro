package com.fatih.stopmotion.data

import java.util.Locale

enum class ProjectSort(val label: String) {
    RECENT("Terbaru"),
    NAME("Nama"),
    FRAMES("Frame")
}

object ProjectFilter {
    fun apply(list: List<ProjectSummary>, query: String, sort: ProjectSort): List<ProjectSummary> {
        val q = query.trim().lowercase(Locale.ROOT)
        val filtered = if (q.isEmpty()) list else list.filter {
            it.project.name.lowercase(Locale.ROOT).contains(q)
        }
        return when (sort) {
            ProjectSort.RECENT -> filtered.sortedByDescending { it.project.updatedAt }
            ProjectSort.NAME -> filtered.sortedBy { it.project.name.lowercase(Locale.ROOT) }
            ProjectSort.FRAMES -> filtered.sortedByDescending { it.frameCount }
        }
    }
}
