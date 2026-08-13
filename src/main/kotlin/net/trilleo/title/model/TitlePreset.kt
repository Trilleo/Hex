package net.trilleo.title.model

/**
 * A ready-made look for a title — a starting point, not a mode.
 *
 * Applying one writes colours, style flags and a sound into a [TitleSpec] and then gets out of the way: the
 * spec goes on being edited field by field, and the row simply reads [CUSTOM] again once it no longer matches
 * anything. Nothing anywhere stores which preset was used, so a preset can never become a second source of
 * truth that the individual settings could disagree with.
 *
 * **Text and timings are deliberately left alone.** They are the two things a player has already decided by
 * the time they reach for a preset — the words are the whole point of the alert, and how long it holds is
 * about pacing rather than appearance — so re-applying a preset to tune a colour must not silently retype the
 * message or reset the dwell time.
 *
 * The sounds are chosen to be distinguishable with your eyes elsewhere, which is the situation a title is for:
 * a chime, a level-up, a note, a clang.
 */
enum class TitlePreset(
    private val titleColor: String,
    private val subtitleColor: String,
    private val bold: Boolean,
    private val sound: String,
    private val pitch: Double,
    private val volume: Double,
) {
    /** Whatever the spec currently says. Never applied — it is the answer [of] gives for an unmatched spec. */
    CUSTOM("", "", false, "", 1.0, 1.0),

    /** Aqua on grey, with a soft chime. For something worth knowing rather than acting on. */
    INFO("#55FFFF", "#AAAAAA", false, "minecraft:block.amethyst_block.chime", 1.2, 0.7),

    /** Green and bold, with the level-up. For something that went right. */
    SUCCESS("#55FF55", "#AAAAAA", true, "minecraft:entity.player.levelup", 1.4, 0.7),

    /** Gold and bold, with a high note. For something about to matter. */
    WARNING("#FFAA00", "#FFFF55", true, "minecraft:block.note_block.pling", 1.6, 1.0),

    /** Red and bold, with an anvil. For something that matters now. */
    ALERT("#FF5555", "#FFAAAA", true, "minecraft:block.anvil.land", 1.5, 0.6),

    /** Both lines flowing through the rainbow. For the announcements that are meant to be enjoyed. */
    CHROMA("chroma", "chroma", true, "minecraft:entity.player.levelup", 1.2, 0.7),
    ;

    /**
     * Writes this preset's look into [spec], leaving its text and timings untouched.
     *
     * [CUSTOM] does nothing at all: it names the absence of a preset, so "applying" it would have to mean
     * inventing values, and the only honest set to invent is the ones already there.
     */
    fun applyTo(spec: TitleSpec) {
        if (this == CUSTOM) return

        spec.title.color = titleColor
        spec.title.clearStyles()
        spec.title.bold = bold

        spec.subtitle.color = subtitleColor
        spec.subtitle.clearStyles()

        spec.sound = sound
        spec.pitch = pitch
        spec.volume = volume
    }

    /** Whether [spec] currently looks exactly like this preset. */
    private fun matches(spec: TitleSpec): Boolean =
        spec.title.color.equals(titleColor, ignoreCase = true) &&
            spec.subtitle.color.equals(subtitleColor, ignoreCase = true) &&
            spec.title.bold == bold &&
            !spec.title.italic && !spec.title.underline &&
            !spec.title.strikethrough && !spec.title.obfuscated &&
            !spec.subtitle.styled() &&
            spec.sound == sound &&
            spec.pitch == pitch &&
            spec.volume == volume

    companion object {
        /**
         * The preset [spec] looks like, or [CUSTOM] when it looks like none of them.
         *
         * What the editor's preset row reads, so choosing a preset and then nudging one colour shows the row
         * falling back to **Custom** rather than going on claiming a look the title no longer has.
         */
        fun of(spec: TitleSpec): TitlePreset =
            entries.firstOrNull { it != CUSTOM && it.matches(spec) } ?: CUSTOM
    }
}
