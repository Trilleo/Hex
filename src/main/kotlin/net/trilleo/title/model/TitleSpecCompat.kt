package net.trilleo.title.model

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter
import net.trilleo.color.ColorValue
import net.trilleo.util.Chroma

/**
 * Reads a [TitleSpec] whose two lines were written as objects rather than as strings.
 *
 * A title line briefly held a colour and five style flags — `{"color": "#FF5555", "bold": true, …}` — before
 * becoming the one string of `&` codes and words it is now. GSON meets that as a *type* mismatch and throws,
 * and a throw here is not a title falling back to plain: the whole config file fails to parse, so a set of
 * chat highlight rules or regions goes with it.
 *
 * ### Why this is worth carrying
 *
 * That shape was never in a release — it existed between two commits on `master` — so this converts a file
 * almost nobody has. It is here anyway because the alternative is not "those people get a default title", it
 * is "those people lose the file". [net.trilleo.config.JsonConfig] now keeps an unreadable file rather than
 * letting the next save flatten it, which makes the loss recoverable; this makes it not happen.
 *
 * Written as a [TypeAdapterFactory] over the reflective adapter rather than a hand-written deserializer, so
 * every field [TitleSpec] has — and every field it grows — is still read by GSON in the ordinary way. All this
 * does is rewrite two members of the tree on the way past.
 */
class TitleSpecCompat : TypeAdapterFactory {

    override fun <T : Any> create(gson: Gson, type: TypeToken<T>): TypeAdapter<T>? {
        if (type.rawType != TitleSpec::class.java) return null

        val delegate = gson.getDelegateAdapter(this, type)
        val trees = gson.getAdapter(JsonElement::class.java)

        return object : TypeAdapter<T>() {
            // Written by the reflective adapter untouched: this build only ever writes strings, and a
            // compatibility read that also changed the write would keep the old shape alive for ever.
            override fun write(out: JsonWriter, value: T) = delegate.write(out, value)

            override fun read(reader: JsonReader): T {
                val tree = trees.read(reader)
                if (tree.isJsonObject) {
                    val spec = tree.asJsonObject
                    flatten(spec, "title")
                    flatten(spec, "subtitle")
                }
                return delegate.fromJsonTree(tree)
            }
        }.nullSafe()
    }

    private companion object {

        /** Replaces `spec.<member>` with a code string, when it is one of the old objects. */
        fun flatten(spec: JsonObject, member: String) {
            val line = spec.get(member) ?: return
            if (!line.isJsonObject) return
            spec.addProperty(member, codesOf(line.asJsonObject))
        }

        /**
         * The old line object as the codes that say the same thing.
         *
         * The colour goes first because a colour code clears the style flags after it — the same rule
         * [Chroma] parses by — so `&#FF5555&l` is red and bold where `&l&#FF5555` is only red. That ordering
         * is the whole reason this is a conversion rather than a concatenation.
         */
        fun codesOf(line: JsonObject): String {
            val out = StringBuilder()

            when (val color = line.string("color")) {
                "" -> Unit
                else -> if (ColorValue.isChroma(color)) {
                    out.append('&').append(Chroma.CODE)
                } else {
                    ColorValue.parse(color)?.let {
                        out.append('&').append(Chroma.HEX_MARKER)
                            .append(ColorValue.format(it, alpha = false).removePrefix("#"))
                    }
                }
            }

            if (line.flag("bold")) out.append("&l")
            if (line.flag("italic")) out.append("&o")
            if (line.flag("underline")) out.append("&n")
            if (line.flag("strikethrough")) out.append("&m")
            if (line.flag("obfuscated")) out.append("&k")

            // Last, so every code above applies to it. The big line's was always empty — its words came from
            // the alert's message, which is exactly what a line of nothing but codes still means.
            return out.append(line.string("text")).toString()
        }

        /** A string member, or `""` for one that is absent, null, or not a string. */
        fun JsonObject.string(member: String): String {
            val value = get(member) ?: return ""
            return if (value.isJsonPrimitive && value.asJsonPrimitive.isString) value.asString else ""
        }

        /** A boolean member, or `false` for one that is absent, null, or not a boolean. */
        fun JsonObject.flag(member: String): Boolean {
            val value = get(member) ?: return false
            return value.isJsonPrimitive && value.asJsonPrimitive.isBoolean && value.asBoolean
        }
    }
}
