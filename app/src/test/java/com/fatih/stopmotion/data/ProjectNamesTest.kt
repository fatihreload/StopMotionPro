package com.fatih.stopmotion.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectNamesTest {

    @Test
    fun clean_trimsAndCollapsesSpaces() {
        assertEquals("Halo dunia", ProjectNames.clean("  Halo   dunia  "))
    }

    @Test
    fun clean_limitsLength() {
        assertEquals(ProjectNames.MAX_LENGTH, ProjectNames.clean("a".repeat(60)).length)
    }

    @Test
    fun clean_blankBecomesEmpty() {
        assertEquals("", ProjectNames.clean("     "))
    }

    @Test
    fun copyName_firstCopy() {
        assertEquals("Film (salinan)", ProjectNames.copyName("Film", emptyList()))
    }

    @Test
    fun copyName_numbersFurtherCopies() {
        assertEquals("Film (salinan 2)", ProjectNames.copyName("Film", listOf("Film (salinan)")))
    }

    @Test
    fun copyName_ignoresLetterCase() {
        val existing = listOf("film (SALINAN)", "Film (salinan 2)")
        assertEquals("Film (salinan 3)", ProjectNames.copyName("Film", existing))
    }

    @Test
    fun copyName_emptyBaseUsesDefault() {
        assertEquals("Proyek (salinan)", ProjectNames.copyName("", emptyList()))
    }

    @Test
    fun copyName_longNameStaysWithinLimit() {
        val result = ProjectNames.copyName("a".repeat(40), emptyList())
        assertTrue(result.length <= ProjectNames.MAX_LENGTH)
        assertTrue(result.endsWith(" (salinan)"))
    }

    @Test
    fun uniqueName_keepsFreeName() {
        assertEquals("Film", ProjectNames.uniqueName("Film", emptyList()))
    }

    @Test
    fun uniqueName_addsNumberWhenTaken() {
        assertEquals("Film (2)", ProjectNames.uniqueName("Film", listOf("film")))
        assertEquals("Film (3)", ProjectNames.uniqueName("Film", listOf("Film", "Film (2)")))
    }

    @Test
    fun formatBytes_usesReadableUnits() {
        assertEquals("0 B", ProjectNames.formatBytes(0))
        assertEquals("1023 B", ProjectNames.formatBytes(1023))
        assertEquals("1 KB", ProjectNames.formatBytes(1024))
        assertEquals("1.0 MB", ProjectNames.formatBytes(1_048_576))
        assertEquals("1.5 MB", ProjectNames.formatBytes(1_572_864))
        assertEquals("1.00 GB", ProjectNames.formatBytes(1_073_741_824))
    }

    @Test
    fun fileSafe_replacesUnsafeCharacters() {
        assertEquals("Film_Saya", ProjectNames.fileSafe("Film Saya!"))
        assertEquals("a-b_c", ProjectNames.fileSafe("a-b_c"))
    }

    @Test
    fun fileSafe_fallsBackWhenNothingLeft() {
        assertEquals("animasi", ProjectNames.fileSafe("???"))
    }
}
