package com.fatih.stopmotion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectFilterTest {

    private fun summary(name: String, updatedAt: Long, frames: Int) =
        ProjectSummary(ProjectEntity(name = name, createdAt = 0, updatedAt = updatedAt), frames)

    private val list = listOf(
        summary("Bunga", 100, 5),
        summary("Kucing", 300, 2),
        summary("anjing", 200, 9)
    )

    private fun names(result: List<ProjectSummary>) = result.map { it.project.name }

    @Test
    fun sortRecent_newestFirst() {
        assertEquals(listOf("Kucing", "anjing", "Bunga"), names(ProjectFilter.apply(list, "", ProjectSort.RECENT)))
    }

    @Test
    fun sortName_ignoresLetterCase() {
        assertEquals(listOf("anjing", "Bunga", "Kucing"), names(ProjectFilter.apply(list, "", ProjectSort.NAME)))
    }

    @Test
    fun sortFrames_mostFramesFirst() {
        assertEquals(listOf("anjing", "Bunga", "Kucing"), names(ProjectFilter.apply(list, "", ProjectSort.FRAMES)))
    }

    @Test
    fun query_matchesPartOfNameIgnoringCase() {
        assertEquals(listOf("Kucing", "anjing"), names(ProjectFilter.apply(list, "ING", ProjectSort.RECENT)))
    }

    @Test
    fun query_blankKeepsEverything() {
        assertEquals(3, ProjectFilter.apply(list, "   ", ProjectSort.RECENT).size)
    }

    @Test
    fun query_withoutMatchIsEmpty() {
        assertTrue(ProjectFilter.apply(list, "xyz", ProjectSort.RECENT).isEmpty())
    }
}
