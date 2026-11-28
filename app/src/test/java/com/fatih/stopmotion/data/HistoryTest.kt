package com.fatih.stopmotion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryTest {

    private fun frames(vararg ids: Long) = ids.map {
        FrameEntity(id = it, projectId = 1, position = it.toInt(), filePath = "p$it", thumbPath = "t$it", createdAt = 0)
    }

    private fun ids(list: List<FrameEntity>?) = list?.map { it.id }

    @Test
    fun startsEmptyWithNothingToUndo() {
        val h = History()
        assertTrue(h.isEmpty())
        assertNull(h.stepBack())
        assertNull(h.stepForward())
        assertFalse(h.info.value.canUndo)
        assertFalse(h.info.value.canRedo)
    }

    @Test
    fun undoAndRedoWalkThroughSnapshots() {
        val h = History()
        h.push(frames(1), frames(1, 2))
        assertTrue(h.info.value.canUndo)
        assertFalse(h.info.value.canRedo)

        assertEquals(listOf(1L), ids(h.stepBack()))
        assertFalse(h.info.value.canUndo)
        assertTrue(h.info.value.canRedo)

        assertEquals(listOf(1L, 2L), ids(h.stepForward()))
        assertFalse(h.info.value.canRedo)
    }

    @Test
    fun newActionAfterUndoDropsRedoBranch() {
        val h = History()
        h.push(frames(1), frames(1, 2))
        h.stepBack()
        h.push(null, frames(1, 3))

        assertFalse(h.info.value.canRedo)
        assertEquals(listOf(1L), ids(h.stepBack()))
        assertNull(h.stepBack())
    }

    @Test
    fun keepsOnlyTheLastStates() {
        val h = History()
        h.push(frames(0), frames(1))
        repeat(40) { h.push(null, frames(it + 2L)) }

        var steps = 0
        while (h.stepBack() != null) steps++
        assertEquals(History.MAX_STATES - 1, steps)
    }
}
