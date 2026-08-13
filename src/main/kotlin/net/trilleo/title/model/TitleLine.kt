package net.trilleo.title.model

import net.trilleo.color.ColorValue

/**
 * One line of a title — the big line or the smaller one under it — and everything about how it is drawn.
 *
 * The two lines are the same shape on purpose. A subtitle is not a lesser thing than a title: a player who
 * wants a red bold warning above a grey italic note wants both halves to be as configurable as each other, and
 * a model where only the top line had a colour would have to grow the rest of the fields the first time
 * someone asked.
 *
 * ### Two ways to say the same thing
 *
 * A line carries a colour and five style flags *and* its [text] may contain `&` codes. They compose rather
 * than compete: the flags and the colour are the line's baseline, and a code inside the text overrides it from
 * that point on — which is what makes `&fBOSS &c&lINCOMING` possible on a line whose colour is white. See
 * [net.trilleo.util.Chroma], which does the parsing and is the same code that colours item names.
 *
 * The flags are here rather than left to `&l` and `&o` because they are what a player looks for first, and
 * because a flag survives editing the text — retyping a message must not silently drop its bolding.
 *
 * Plain, `var`-only and no-arg constructible for the same GSON reasons
 * [net.trilleo.reminder.model.ReminderAction] is.
 */
class TitleLine {

    /**
     * What the line says. May carry `&` codes, `&#RRGGBB` hex colours and `&z` chroma.
     *
     * Empty for a line whose text is supplied by whatever fires the title — a reminder puts its own message on
     * the big line, so only the subtitle's text is stored there. See [TitleSpec.title].
     */
    var text: String = ""

    /**
     * The line's base colour, in [ColorValue]'s vocabulary: `"#RRGGBB"`, `"chroma"`, or `""` to fall back to
     * the mod-wide default from the Titles tab.
     *
     * Chroma is offered here — unlike most cached-component settings — because
     * [net.trilleo.title.Titles] hands the game a component that rebuilds itself every frame, so a flowing
     * title actually flows. See [net.trilleo.title.Titles.LiveText].
     */
    var color: String = ""

    var bold: Boolean = false
    var italic: Boolean = false
    var underline: Boolean = false
    var strikethrough: Boolean = false
    var obfuscated: Boolean = false

    /** Repairs this line in place, covering GSON's reflection gaps and canonicalising the colour. */
    fun normalize() {
        @Suppress("SENSELESS_COMPARISON")
        if (text == null) text = ""
        @Suppress("SENSELESS_COMPARISON")
        if (color == null) color = ""
        color = ColorValue.normalize(color, alpha = false)
    }

    /** A field-for-field copy, for duplicating whatever owns this line. */
    fun copy(): TitleLine = TitleLine().also {
        it.text = text
        it.color = color
        it.bold = bold
        it.italic = italic
        it.underline = underline
        it.strikethrough = strikethrough
        it.obfuscated = obfuscated
    }

    /** Whether any of the five flags is set — what a preset compares and what a summary line reports. */
    fun styled(): Boolean = bold || italic || underline || strikethrough || obfuscated

    /** Turns every flag off, so a preset can state all five rather than only the ones it wants on. */
    fun clearStyles() {
        bold = false
        italic = false
        underline = false
        strikethrough = false
        obfuscated = false
    }
}
