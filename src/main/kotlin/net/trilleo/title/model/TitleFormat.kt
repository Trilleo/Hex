package net.trilleo.title.model

import net.trilleo.util.Chroma

/**
 * Reading a title's source as *codes* and *words*, which is the whole of how the title editor works.
 *
 * A title line is one string carrying `&` codes and text together — `&c&lBOSS INCOMING`. That replaced a
 * colour field and five style switches per line, because the codes already said all of it and said it *per
 * segment*: a switch can only bold the whole line, and `&c&lBOSS &einbound` cannot be expressed by switches
 * at all.
 *
 * Everything here is a pure string operation over [Chroma.codeLengthAt], so what these functions call a code is
 * exactly what the parser will call a code.
 *
 * ### The rule that makes the big line work
 *
 * A reminder, a region or a highlight already has a message, and the title shows it. So a title line that is
 * **nothing but codes** styles that message rather than replacing it — see [merge]. `&c&l` means "the alert's
 * own words, in bold red"; `&c&lBOSS` means "the word BOSS, in bold red". One rule, and the editor's live
 * preview shows which one you have written as you write it.
 */
object TitleFormat {

    /** [raw] with every code removed — what a reader would actually see. */
    fun visibleText(raw: String): String {
        val out = StringBuilder(raw.length)
        var i = 0
        while (i < raw.length) {
            val code = Chroma.codeLengthAt(raw, i)
            if (code > 0) {
                i += code
            } else {
                out.append(raw[i])
                i++
            }
        }
        return out.toString()
    }

    /** Whether [raw] says anything at all once its codes are taken away. */
    fun hasText(raw: String): Boolean = visibleText(raw).isNotBlank()

    /**
     * The unbroken run of codes at the start of [raw].
     *
     * What a preset writes and what tells the preset row which preset it is looking at. Only the *leading* run,
     * because a code further in belongs to the words around it and is the player's, not the preset's.
     */
    fun leadingCodes(raw: String): String {
        var i = 0
        while (i < raw.length) {
            val code = Chroma.codeLengthAt(raw, i)
            if (code == 0) break
            i += code
        }
        return raw.substring(0, i)
    }

    /** [raw] with its leading codes taken off, so a preset can put its own on without stacking them up. */
    fun stripLeadingCodes(raw: String): String = raw.substring(leadingCodes(raw).length)

    /** [raw] with [codes] in place of whatever codes it currently starts with. */
    fun withLeadingCodes(raw: String, codes: String): String = codes + stripLeadingCodes(raw)

    /**
     * What the big line actually says: [line] on its own when it has words, and [line] applied to [ownerText]
     * when it is only codes.
     *
     * Blank [line] falls through to the same place, since a blank line has no codes either — so an alert that
     * has never had its title styled shows its message exactly as it always did.
     */
    fun merge(line: String, ownerText: String): String = if (hasText(line)) line else line + ownerText
}
