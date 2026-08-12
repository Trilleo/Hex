package net.trilleo.itemcustom

import com.google.common.collect.ImmutableMultimap
import com.mojang.authlib.GameProfile
import com.mojang.authlib.properties.Property
import com.mojang.authlib.properties.PropertyMap
import net.minecraft.client.Minecraft
import net.minecraft.core.component.DataComponents
import net.minecraft.network.chat.Component
import net.minecraft.resources.Identifier
import net.minecraft.world.item.ItemStack
import net.minecraft.world.item.component.DyedItemColor
import net.minecraft.world.item.component.ResolvableProfile
import net.trilleo.color.ColorValue
import net.trilleo.skyblock.item.SkyblockItem
import net.trilleo.util.Chroma
import java.nio.charset.StandardCharsets
import java.util.*

/**
 * Turns an [ItemCustomization] into the two things the game actually consumes: a name [Component] and an
 * [ItemStack] to draw.
 *
 * Everything here is client-side. Nothing is sent to the server, no packet is altered, and the player's real
 * inventory is untouched — the render path is handed a *copy* of the stack with different components on it,
 * and that copy exists only for the length of one draw. Other players see the item exactly as Hypixel sent it.
 *
 * ### Why the split into two products
 *
 * The name and the appearance travel through completely different code. `ItemStack.getHoverName` is what the
 * tooltip, the hotbar popup and every GUI label read, and it never touches the renderer;
 * `ItemModelResolver.appendItemLayers` is what every drawn item goes through, and it never reads the name. So
 * [nameFor] and [renderStackFor] are separate, and a customization that only renames an item never allocates a
 * stack copy at all.
 *
 * **The name is deliberately not applied as a `CUSTOM_NAME` component on the render copy.** Vanilla's
 * `getStyledHoverName` italicises anything carrying that component, so doing it that way would tilt every
 * renamed item — and the copy is not what `getHoverName` reads anyway.
 */
object ItemCustomizer {

    /** The base64 payload every player-head texture is wrapped in, with the hash substituted in. */
    private const val TEXTURE_URL_PREFIX = "https://textures.minecraft.net/texture/"

    /**
     * Built profiles, keyed by texture hash.
     *
     * Building one means a JSON string, a base64 encode and a `GameProfile` allocation, and the result is a
     * pure function of the hash — so it is computed once per distinct texture the player has configured and
     * then handed out. Bounded in practice by how many customizations exist, which is why no eviction policy
     * is needed. Guarded because the render thread reads it while a GUI edit can write it.
     */
    private val profiles = Collections.synchronizedMap(HashMap<String, ResolvableProfile>())

    /**
     * The customization that applies to [uuid], or null when there is none that would change anything.
     *
     * The whole of the render path's gating: the feature switch, the "is anything configured at all" check and
     * the per-entry enabled flag, in the order that costs least.
     */
    fun customizationFor(uuid: String?): ItemCustomization? {
        if (uuid == null || !ItemCustomizeConfig.anyActive) return null
        return ItemCustomizeConfig.find(uuid)?.takeIf { it.hasEffect() }
    }

    /**
     * The replacement display name, or null when this customization does not change the name.
     *
     * [original] is what vanilla would have returned, which is why the hook that calls this injects at the
     * *return* of `getHoverName` rather than its head: reading the name back off the stack from inside that
     * method would call straight into itself.
     *
     * With a colour but no name, the original is flattened to plain text before being recoloured. Hypixel's
     * names carry their own colours — as `§` codes inside the text, or as styles on child components — and
     * both of those win over a colour set on the parent, so "recolour this item" would visibly do nothing at
     * all unless the existing colouring is dropped first.
     */
    fun nameFor(customization: ItemCustomization, original: Component): Component? {
        val hasName = customization.name.isNotEmpty()
        val color = ColorValue.parse(customization.color)
        if (!hasName && color == null && !customization.chroma) return null

        val text = if (hasName) customization.name else SkyblockItem.strip(original.string)
        val component = Chroma.build(
            text,
            all = customization.chroma,
            // Masked to RGB: a style colour carries no alpha, and a "#AARRGGBB" value pasted into the field
            // would otherwise land in the red channel.
            baseColor = color?.and(0xFFFFFF),
            seconds = ItemCustomizeConfig.chromaSeconds,
            width = ItemCustomizeConfig.chromaWidth,
        )
        // Hypixel's own names are upright, and `getStyledHoverName` italicises anything with a CUSTOM_NAME
        // component — which every Skyblock item has. Saying so on the parent keeps a renamed item looking like
        // its neighbours; the children set italic only where `&o` asked for it, and inherit this otherwise.
        return component.withStyle { it.withItalic(false) }
    }

    /**
     * A copy of [stack] carrying this customization's appearance, or null when nothing about the appearance
     * changes — in which case the caller keeps the original stack and no allocation happens.
     *
     * The copy is made lazily on the first field that needs it, so an entry that only renames an item passes
     * straight through.
     */
    fun renderStackFor(customization: ItemCustomization, stack: ItemStack): ItemStack? {
        var copy: ItemStack? = null
        fun target(): ItemStack = copy ?: stack.copy().also { copy = it }

        when (customization.glintOrDefault) {
            GlintOverride.ON -> target().set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true)
            GlintOverride.OFF -> target().set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, false)
            // Leaves the component alone, so vanilla decides.
            GlintOverride.DEFAULT -> Unit
        }

        val profile = profileFor(customization.texture)
        if (profile != null) target().set(DataComponents.PROFILE, profile)

        // A texture with no model of its own implies the head model: the skin only has something to be drawn
        // on once the item is rendered as a head, and setting a texture on a sword and seeing a sword would
        // read as the field being broken.
        val model = modelIdOf(customization.model)
            ?: Identifier.withDefaultNamespace("player_head").takeIf { profile != null }
        if (model != null) target().set(DataComponents.ITEM_MODEL, model)

        ColorValue.parse(customization.dye)?.let {
            target().set(DataComponents.DYED_COLOR, DyedItemColor(it and 0xFFFFFF))
        }

        return copy
    }

    /**
     * The stack a preview should draw for [stack] — the customized copy when there is one, otherwise the
     * original. For the editor screen, which wants the answer regardless of which render hook would have
     * produced it.
     */
    fun previewOf(stack: ItemStack, customization: ItemCustomization): ItemStack =
        renderStackFor(customization, stack) ?: stack

    // ---- field parsing ---------------------------------------------------------------------------------

    /**
     * Reduces whatever was typed into the texture field to a bare hash.
     *
     * Three forms are accepted because all three are what people have to hand: the hash on its own, the
     * `textures.minecraft.net` URL it appears in, and the base64 blob copied out of an item's NBT (which is
     * that URL wrapped in JSON). Anything else is returned trimmed and will simply fail to resolve, which the
     * editor reports before it can be saved.
     */
    fun normalizeTexture(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""

        // A base64 blob decodes to JSON containing the URL; anything that is not valid base64 is not one.
        val decoded = runCatching { String(Base64.getDecoder().decode(trimmed), StandardCharsets.UTF_8) }
            .getOrNull()
        val source = if (decoded != null && decoded.contains("textures.minecraft.net")) decoded else trimmed

        val marker = source.lastIndexOf("texture/")
        val tail = if (marker >= 0) source.substring(marker + "texture/".length) else source
        // Stops at the first character a hash cannot contain, which is what closes the JSON string in the
        // decoded case and trims a trailing slash or query in the URL case.
        return tail.takeWhile { it.isDigit() || it in 'a'..'f' || it in 'A'..'F' }.lowercase(Locale.ROOT)
    }

    /** The full skin URL for a bare [hash], as the profile property has to spell it. */
    fun textureUrl(hash: String): String = TEXTURE_URL_PREFIX + hash

    /** Parses a model id, or null when [raw] is blank or not a valid [Identifier]. */
    fun modelIdOf(raw: String): Identifier? =
        raw.trim().takeIf { it.isNotEmpty() }?.let { Identifier.tryParse(it) }

    /**
     * Whether [raw] names a model this client has actually loaded.
     *
     * Asked by comparing the lookup against the lookup for an id nothing can have registered: the model
     * manager answers an unknown id with its shared "missing" model, so two unknown ids come back as the same
     * instance. That avoids depending on a constant whose name changes between versions, and it stays correct
     * if the fallback is ever replaced.
     *
     * Returns true for a blank value — "no model set" is not an error.
     */
    fun modelKnown(raw: String): Boolean {
        if (raw.isBlank()) return true
        val id = modelIdOf(raw) ?: return false
        val models = Minecraft.getInstance().modelManager
        val missing = models.getItemModel(Identifier.fromNamespaceAndPath("hex", "missing_probe"))
        return models.getItemModel(id) !== missing
    }

    // ---- profile construction --------------------------------------------------------------------------

    /**
     * The head profile for a texture [hash], or null when it is blank.
     *
     * The profile is built exactly the way Hypixel builds the ones it sends: a `textures` property holding the
     * base64 of a tiny JSON document naming the skin URL. Vanilla's skin manager reads that property, fetches
     * the texture and caches it — the same path every Skyblock head already takes, so nothing here depends on
     * the property being signed.
     *
     * The profile's own id is derived from the hash so it is stable across sessions: vanilla's skin cache is
     * keyed on the profile, and a fresh random id each frame would defeat it.
     */
    private fun profileFor(hash: String): ResolvableProfile? {
        if (hash.isEmpty()) return null
        return profiles.getOrPut(hash) {
            val payload = """{"textures":{"SKIN":{"url":"${textureUrl(hash)}"}}}"""
            val encoded = Base64.getEncoder().encodeToString(payload.toByteArray(StandardCharsets.UTF_8))
            val properties = PropertyMap(ImmutableMultimap.of("textures", Property("textures", encoded)))
            val id = UUID.nameUUIDFromBytes("hex:$hash".toByteArray(StandardCharsets.UTF_8))
            ResolvableProfile.createResolved(GameProfile(id, PROFILE_NAME, properties))
        }
    }

    /**
     * The name on every generated profile. Never displayed — the head model reads only the skin — but authlib
     * wants one, and a constant keeps two customizations sharing a texture sharing a cache entry.
     */
    private const val PROFILE_NAME = "hex"
}
