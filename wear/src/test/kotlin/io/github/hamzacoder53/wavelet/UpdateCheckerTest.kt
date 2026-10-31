package io.github.hamzacoder53.wavelet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckerTest {
    @Test
    fun parseVersionFromTagStripsPrefixes() {
        assertEquals("0.6.0", UpdateChecker.parseVersionFromTag("wavelet-v0.6.0"))
        assertEquals("0.6.0", UpdateChecker.parseVersionFromTag("wavelet-0.6.0"))
        assertEquals("0.6.0", UpdateChecker.parseVersionFromTag("v0.6.0"))
        assertEquals("0.6.0", UpdateChecker.parseVersionFromTag("0.6.0"))
    }

    @Test
    fun comparesVersionsNumericallyPerDotPart() {
        assertTrue(UpdateChecker.isVersionNewer("0.6.0", "0.5.0"))
        assertTrue(UpdateChecker.isVersionNewer("0.5.1", "0.5.0"))
        assertTrue(UpdateChecker.isVersionNewer("1.0.0", "0.5.0"))
        assertTrue(UpdateChecker.isVersionNewer("0.5.0.1", "0.5.0"))
        assertTrue(UpdateChecker.isVersionNewer("0.10.0", "0.9.0"))

        assertFalse(UpdateChecker.isVersionNewer("0.5.0", "0.5.0"))
        assertFalse(UpdateChecker.isVersionNewer("0.4.9", "0.5.0"))
        assertFalse(UpdateChecker.isVersionNewer("0.5.0", "0.6.0"))
        assertFalse(UpdateChecker.isVersionNewer("0.5", "0.5.0"))
    }
}
