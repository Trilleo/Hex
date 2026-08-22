package net.trilleo.sound.model

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * The repairs a sequence and its steps make to themselves before anything of Minecraft is involved.
 *
 * Every case here is something GSON or a hand-edited file can actually produce, and every one of them fails
 * quietly rather than throwing: an absent number arrives as zero, an absent object arrives as null, and a
 * value out of range is simply played. A sequence whose steps were left unsorted would play its notes in file
 * order rather than in time order, and a step that referenced another sequence would schedule forever.
 */
class SoundSequenceTest {

    private fun step(block: SoundStep.() -> Unit = {}) = SoundStep().apply(block)

    private fun sequence(block: SoundSequence.() -> Unit = {}) = SoundSequence().apply(block)

    // ---- steps -------------------------------------------------------------------------------------------

    @Test
    fun `a step may not name another sequence`() {
        // A sequence containing itself would schedule forever. Forbidding the shape in the model is cheaper
        // than detecting the cycle it creates, and the marker is stripped rather than the step discarded so
        // a file that tried it still makes a sound.
        val recursive = step { sound = "@itself" }
        recursive.normalize()
        assertEquals("itself", recursive.sound)
    }

    @Test
    fun `a blank sound falls back rather than being played as nothing`() {
        val blank = step { sound = "   " }
        blank.normalize()
        assertEquals(SoundStep.DEFAULT_SOUND, blank.sound)
    }

    @Test
    fun `numbers a hand-edited file can hold are bounded`() {
        val wild = step {
            atMillis = -50.0
            pitch = 9.0
            volume = 4.0
            lane = 99
        }
        wild.normalize()
        assertEquals(0.0, wild.atMillis)
        assertEquals(SoundStep.PITCH_MAX, wild.pitch)
        assertEquals(SoundStep.VOLUME_MAX, wild.volume)
        assertEquals(SoundStep.LANE_MAX, wild.lane)
    }

    @Test
    fun `a NaN is replaced rather than propagated`() {
        // No slider can produce one, but a hand-edited file can, and NaN survives coerceIn untouched — so it
        // has to be caught before the bounding rather than by it.
        val broken = step {
            atMillis = Double.NaN
            pitch = Double.NEGATIVE_INFINITY
            volume = Double.NaN
        }
        broken.normalize()
        assertEquals(0.0, broken.atMillis)
        assertEquals(1.0, broken.pitch)
        assertEquals(1.0, broken.volume)
    }

    @Test
    fun `a copy shares nothing with its original`() {
        val original = step { sound = "minecraft:ui.button.click"; atMillis = 120.0; lane = 3 }
        val copy = original.copy()
        assertNotSame(original, copy)
        copy.atMillis = 999.0
        assertEquals(120.0, original.atMillis)
    }

    // ---- sequences ---------------------------------------------------------------------------------------

    @Test
    fun `steps are sorted into time order`() {
        // The editor draws them wherever their time puts them, so a file listing them out of order is not
        // wrong — but the scheduler walks the list, and an unsorted one would queue them out of order.
        val out = sequence {
            steps = mutableListOf(
                step { atMillis = 500.0 },
                step { atMillis = 0.0 },
                step { atMillis = 250.0 },
            )
        }
        out.normalize()
        assertEquals(listOf(0.0, 250.0, 500.0), out.steps.map { it.atMillis })
    }

    @Test
    fun `an absent tempo picks up the default rather than ruling a grid at no tempo`() {
        // Zero is how GSON leaves a key a file written before this field existed never had.
        val migrated = sequence { bpm = 0.0 }
        migrated.normalize()
        assertEquals(SoundSequence.DEFAULT_BPM, migrated.bpm)
    }

    @Test
    fun `the tempo is bounded`() {
        val fast = sequence { bpm = 10_000.0 }
        fast.normalize()
        assertEquals(SoundSequence.BPM_MAX, fast.bpm)
    }

    @Test
    fun `a runaway step list is truncated rather than scheduled`() {
        val huge = sequence {
            steps = (0 until SoundSequence.MAX_STEPS * 2).mapTo(mutableListOf()) { step { atMillis = it.toDouble() } }
        }
        huge.normalize()
        assertEquals(SoundSequence.MAX_STEPS, huge.steps.size)
    }

    @Test
    fun `duration is the last step, not the number of them`() {
        val timed = sequence {
            steps = mutableListOf(step { atMillis = 0.0 }, step { atMillis = 750.0 })
        }
        assertEquals(750.0, timed.durationMillis())
    }

    @Test
    fun `a loop multiplies the total but not one pass`() {
        val looped = sequence {
            loop = true
            loopCount = 3
            steps = mutableListOf(step { atMillis = 0.0 }, step { atMillis = 400.0 })
        }
        assertEquals(400.0, looped.durationMillis())
        assertEquals(1200.0, looped.totalMillis())
    }

    @Test
    fun `copyDefinition leaves the id and the preset provenance alone`() {
        // Load-bearing: this is how a preset update overwrites an untouched sequence, and keeping the id is
        // what stops every "@id" pointing at it from breaking when it does.
        val installed = sequence {
            id = "alarm"
            name = "My alarm"
            presetId = "alarm"
            presetRevision = 1
            steps = mutableListOf(step { atMillis = 0.0 })
        }
        val shipped = sequence {
            name = "Alarm"
            bpm = 140.0
            steps = mutableListOf(step { atMillis = 0.0 }, step { atMillis = 250.0 })
        }

        shipped.copyDefinition(installed)

        assertEquals("alarm", installed.id)
        assertEquals(1, installed.presetRevision)
        assertEquals("Alarm", installed.name)
        assertEquals(140.0, installed.bpm)
        assertEquals(2, installed.steps.size)
        // Copied, not shared — editing the installed copy must not rewrite the catalogue in memory.
        assertNotSame(shipped.steps[0], installed.steps[0])
    }

    @Test
    fun `a copy is independent of its original`() {
        val original = sequence {
            id = "x"
            steps = mutableListOf(step { atMillis = 10.0 })
        }
        val copy = original.copy()
        assertEquals("x", copy.id)
        copy.steps[0].atMillis = 999.0
        assertEquals(10.0, original.steps[0].atMillis)
        assertTrue(original.steps[0] !== copy.steps[0])
    }
}
