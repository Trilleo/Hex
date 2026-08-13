package net.trilleo.title.model

/**
 * A ready-made look for a title — a starting point, not a mode.
 *
 * Applying one puts its `&` codes at the front of both lines and sets the sound, then gets out of the way: the
 * codes are ordinary text in the boxes afterwards, and can be edited, extended or deleted like any other. The
 * row simply reads [CUSTOM] again once the leading codes no longer match anything. Nothing anywhere stores
 * which preset was used, so a preset can never become a second source of truth.
 *
 * **Only the leading codes are touched.** A code further into a line belongs to the words around it — it is the
 * player saying "and this bit in yellow" — so re-applying a preset to change the sound must not flatten it.
 * The words themselves and the timings are left alone for the same reason: they are what the player had already
 * decided by the time they reached for a preset.
 *
 * The sounds are chosen to be distinguishable with your eyes elsewhere, which is the situation a title is for:
 * a chime, a level-up, a note, a clang.
 */
enum class TitlePreset(
    private val titleCodes: String,
    private val subtitleCodes: String,
    private val sound: String,
    private val pitch: Double,
    private val volume: Double,
) {
    /** Whatever the lines currently say. Never applied — it is the answer [of] gives for an unmatched spec. */
    CUSTOM("", "", "", 1.0, 1.0),

    /** Aqua on grey, with a soft chime. For something worth knowing rather than acting on. */
    INFO("&b", "&7", "minecraft:block.amethyst_block.chime", 1.2, 0.7),

    /** Green and bold, with the level-up. For something that went right. */
    SUCCESS("&a&l", "&7", "minecraft:entity.player.levelup", 1.4, 0.7),

    /** Gold and bold, with a high note. For something about to matter. */
    WARNING("&6&l", "&e", "minecraft:block.note_block.pling", 1.6, 1.0),

    /** Red and bold, with an anvil. For something that matters now. */
    ALERT("&c&l", "&7", "minecraft:block.anvil.land", 1.5, 0.6),

    /** Both lines flowing through the rainbow. For the announcements that are meant to be enjoyed. */
    CHROMA("&z&l", "&z", "minecraft:entity.player.levelup", 1.2, 0.7),
    ;

    /**
     * Writes this preset's codes and sound into [spec], leaving its words and timings untouched.
     *
     * [CUSTOM] does nothing at all: it names the absence of a preset, so "applying" it would have to mean
     * inventing values, and the only honest set to invent is the ones already there.
     */
    fun applyTo(spec: TitleSpec) {
        if (this == CUSTOM) return

        spec.title = TitleFormat.withLeadingCodes(spec.title, titleCodes)
        spec.subtitle = TitleFormat.withLeadingCodes(spec.subtitle, subtitleCodes)

        spec.sound = sound
        spec.pitch = pitch
        spec.volume = volume
    }

    /** Whether [spec] currently starts the way this preset would have started it. */
    private fun matches(spec: TitleSpec): Boolean =
        TitleFormat.leadingCodes(spec.title).equals(titleCodes, ignoreCase = true) &&
            TitleFormat.leadingCodes(spec.subtitle).equals(subtitleCodes, ignoreCase = true) &&
            spec.sound == sound &&
            spec.pitch == pitch &&
            spec.volume == volume

    companion object {
        /**
         * The preset [spec] looks like, or [CUSTOM] when it looks like none of them.
         *
         * What the editor's preset row reads, so choosing a preset and then recolouring one line shows the row
         * falling back to **Custom** rather than going on claiming a look the title no longer has.
         */
        fun of(spec: TitleSpec): TitlePreset =
            entries.firstOrNull { it != CUSTOM && it.matches(spec) } ?: CUSTOM
    }
}
