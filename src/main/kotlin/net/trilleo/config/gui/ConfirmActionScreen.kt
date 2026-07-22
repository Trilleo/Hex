package net.trilleo.config.gui

import net.minecraft.client.gui.GuiGraphicsExtractor
import net.minecraft.client.gui.components.Button
import net.minecraft.client.gui.screens.Screen
import net.minecraft.network.chat.Component

/**
 * A yes/no (or save/discard/cancel) prompt in front of something that cannot be undone.
 *
 * Hand-rolled rather than using vanilla's `ConfirmScreen` for two reasons: its constructor shape has moved
 * between versions and pinning to it buys nothing here, and this needs a three-way variant that vanilla's
 * two-button prompt cannot express.
 *
 * Choosing anything closes this screen first and then runs the action, so the action is free to open a screen
 * of its own without being immediately replaced by the dismissal.
 *
 * @param parent the screen to return to when the prompt is dismissed.
 * @param message the question, drawn above the buttons.
 * @param detail an optional second line for the consequence, drawn muted.
 * @param choices the buttons, left to right. A choice with a null action just returns to [parent].
 */
class ConfirmActionScreen(
    private val parent: Screen?,
    title: Component,
    private val message: Component,
    private val detail: Component? = null,
    private val choices: List<Choice>,
) : Screen(title) {

    /** One button on the prompt. [action] is null for a pure "never mind". */
    data class Choice(val label: Component, val action: (() -> Unit)?)

    override fun init() {
        val totalWidth = choices.size * BUTTON_WIDTH + (choices.size - 1) * GAP
        var x = width / 2 - totalWidth / 2
        val y = height / 2 + BUTTON_OFFSET_Y

        choices.forEach { choice ->
            addRenderableWidget(
                Button.builder(choice.label) {
                    // Dismiss first: an action that opens its own screen must not be undone by this one
                    // closing afterwards.
                    minecraft.setScreen(parent)
                    choice.action?.invoke()
                }.bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build(),
            )
            x += BUTTON_WIDTH + GAP
        }
    }

    override fun extractBackground(extractor: GuiGraphicsExtractor, mouseX: Int, mouseY: Int, delta: Float) {
        super.extractBackground(extractor, mouseX, mouseY, delta)
        extractor.centeredText(font, title, width / 2, height / 2 - TITLE_OFFSET_Y, TITLE_COLOR)
        extractor.centeredText(font, message, width / 2, height / 2 - MESSAGE_OFFSET_Y, TITLE_COLOR)
        detail?.let { extractor.centeredText(font, it, width / 2, height / 2 - DETAIL_OFFSET_Y, MUTED_COLOR) }
    }

    /** Escape means "no", which is the last choice — the cancel slot by convention here. */
    override fun onClose() {
        minecraft.setScreen(parent)
    }

    private companion object {
        const val BUTTON_WIDTH = 100
        const val BUTTON_HEIGHT = 20
        const val GAP = 6
        const val BUTTON_OFFSET_Y = 10
        const val TITLE_OFFSET_Y = 50
        const val MESSAGE_OFFSET_Y = 30
        const val DETAIL_OFFSET_Y = 18

        const val TITLE_COLOR = 0xFFFFFFFF.toInt()
        const val MUTED_COLOR = 0xFF9A9A9A.toInt()
    }
}
