package dev.shadowsoffire.placebo.util;

import java.util.ArrayList;
import java.util.List;

import dev.shadowsoffire.placebo.mixin.client.AbstractContainerScreenMixin;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.DefaultTooltipPositioner;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

/**
 * Implement this on a screen class to be able to call {@link #drawOnLeft(GuiGraphicsExtractor, List, int)}
 * <p>
 * Applied to all screens via {@link AbstractContainerScreenMixin}.
 */
public interface DrawsOnLeft {

    /**
     * Renders a list of text as a tooltip attached to the left edge of the currently open container screen.
     * <p>
     * This method will automatically compress the text to fit in the available space between the left edge of the screen and the left edge of the game window.
     */
    default void drawOnLeft(GuiGraphicsExtractor gfx, List<? extends FormattedText> list, int y) {
        if (list.isEmpty()) return;
        int xPos = __ths().leftPos - 16 - list.stream().map(__ths().font::width).max(Integer::compare).get();
        int maxWidth = 9999;
        if (xPos < 0) {
            maxWidth = __ths().leftPos - 6;
            xPos = -8;
        }

        List<FormattedText> split = new ArrayList<>();
        int _maxWidth = maxWidth;
        list.forEach(text -> {
            Style style = text instanceof Component comp ? comp.getStyle() : Style.EMPTY;
            __ths().font.getSplitter().splitLines(text, _maxWidth, style, (splitLine, isBlank) -> split.add(splitLine));
        });

        this.submitTooltip(gfx, split, xPos, y);
    }

    /**
     * Renders a list of text as a tooltip attached to the left edge of the currently open container screen.
     * <p>
     * This method will compress the text to fit in the specified maxWidth, ignoring the size of the game window.
     */
    default void drawOnLeft(GuiGraphicsExtractor gfx, List<? extends FormattedText> list, int y, int maxWidth) {
        if (list.isEmpty()) return;

        List<FormattedText> split = new ArrayList<>();
        list.forEach(text -> {
            Style style = text instanceof Component comp ? comp.getStyle() : Style.EMPTY;
            __ths().font.getSplitter().splitLines(text, maxWidth, style, (splitLine, isBlank) -> split.add(splitLine));
        });

        int xPos = __ths().leftPos - 16 - split.stream().map(__ths().font::width).max(Integer::compare).get();
        this.submitTooltip(gfx, split, xPos, y);
    }

    private void submitTooltip(GuiGraphicsExtractor gfx, List<FormattedText> split, int xPos, int y) {
        // Deviation: NeoForge fired ClientHooks.gatherTooltipComponents (which dispatched a gather event). Fabric has no such event,
        // so we map the split text lines directly to text-only ClientTooltipComponents, matching the vanilla text-gathering path.
        List<ClientTooltipComponent> lines = split.stream()
            .map(text -> ClientTooltipComponent.create(Language.getInstance().getVisualOrder(text)))
            .toList();
        gfx.tooltip(__ths().font, lines, xPos, y, DefaultTooltipPositioner.INSTANCE, null);
    }

    default AbstractContainerScreen<?> __ths() {
        return (AbstractContainerScreen<?>) this;
    }

    public static void draw(AbstractContainerScreen<?> screen, GuiGraphicsExtractor gfx, List<Component> list, int y) {
        ((DrawsOnLeft) screen).drawOnLeft(gfx, list, y);
    }

}
