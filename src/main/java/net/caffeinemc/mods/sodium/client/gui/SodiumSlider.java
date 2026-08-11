package net.caffeinemc.mods.sodium.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;

import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * A vanilla-1.8.9-styled integer slider: it renders exactly like a plain
 * GuiButton (button texture, "Name: Value" text) and the value is set by
 * clicking (or dragging while pressed). This matches the behaviour of the
 * vanilla GuiOptionSlider in 1.8.9, where the knob sprites are effectively
 * dead code (GuiScreen in 1.8.9 has no mouseDragged dispatch).
 *
 * <p>Because 1.8.9 does not forward drag events to widgets, the owning screen
 * forwards {@code mouseClickMove} events here while {@link #isDragging()}.
 * The configuration is written once when the mouse is released.
 */
public class SodiumSlider extends GuiButton {
    private final int min;
    private final int max;
    private final String prefix;
    private final IntConsumer onSet;
    private final IntFunction<String> fmt;
    private final Runnable onRelease;

    private int value;
    private boolean dragging;

    public SodiumSlider(int id, int x, int y, int width, String prefix, int min, int max, int value,
                        IntConsumer onSet, IntFunction<String> fmt, Runnable onRelease) {
        super(id, x, y, width, 20, "");
        this.prefix = prefix;
        this.min = min;
        this.max = max;
        this.value = value < min ? min : (value > max ? max : value);
        this.onSet = onSet;
        this.fmt = fmt;
        this.onRelease = onRelease;
        this.updateDisplayString();
    }

    public boolean isDragging() {
        return this.dragging;
    }

    private void updateDisplayString() {
        this.displayString = this.prefix + ": " + this.fmt.apply(this.value);
    }

    private void setValueFromMouse(int mouseX) {
        float f = (float) (mouseX - (this.xPosition + 4)) / (float) (this.width - 8);
        if (f < 0.0F) {
            f = 0.0F;
        } else if (f > 1.0F) {
            f = 1.0F;
        }

        int v = Math.round(this.min + f * (float) (this.max - this.min));
        if (v != this.value) {
            this.value = v;
            this.updateDisplayString();
            this.onSet.accept(v);
        }
    }

    @Override
    public boolean mousePressed(Minecraft mc, int mouseX, int mouseY) {
        if (super.mousePressed(mc, mouseX, mouseY)) {
            this.dragging = true;
            this.setValueFromMouse(mouseX);
            return true;
        }
        return false;
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY) {
        if (this.dragging) {
            this.dragging = false;
            if (this.onRelease != null) {
                this.onRelease.run();
            }
        }
    }

    /**
     * Called by the owning screen from GuiScreen#mouseClickMove (1.8.9 has no
     * mouseDragged dispatch in GuiScreen). Kept as GuiButton#mouseDragged so
     * the vanilla widget contract is respected.
     */
    @Override
    public void mouseDragged(Minecraft mc, int mouseX, int mouseY) {
        if (this.dragging) {
            this.setValueFromMouse(mouseX);
        }
    }
}
