package net.caffeinemc.mods.sodium.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiOptionsRowList;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ResourceLocation;

/**
 * A row appended to the vanilla GuiOptionsRowList on the Video Settings screen,
 * matching the two-column layout of the vanilla option rows. Clicking one of
 * its buttons opens the corresponding Sodium category screen.
 */
public class SodiumOptionsRow extends GuiOptionsRowList.Row {
    private static final ResourceLocation PRESS_SOUND = new ResourceLocation("gui.button.press");

    private static final int BUTTON_LEFT = 300;
    private static final int BUTTON_RIGHT = 301;

    private final GuiButton left;
    private final GuiButton right;
    private final SodiumOptionsScreen.Category leftCategory;
    private final SodiumOptionsScreen.Category rightCategory;

    /**
     * @param screenWidth width of the owning screen (used for the column x positions)
     * @param leftCategory category for the left column button, or null to leave it empty
     * @param rightCategory category for the right column button, or null to leave it empty
     */
    public SodiumOptionsRow(int screenWidth, SodiumOptionsScreen.Category leftCategory,
                            SodiumOptionsScreen.Category rightCategory) {
        super(null, null);
        this.left = leftCategory != null
                ? new GuiButton(BUTTON_LEFT, screenWidth / 2 - 155, 0, 150, 20, leftCategory.buttonLabel)
                : null;
        this.right = rightCategory != null
                ? new GuiButton(BUTTON_RIGHT, screenWidth / 2 - 155 + 160, 0, 150, 20, rightCategory.buttonLabel)
                : null;
        this.leftCategory = leftCategory;
        this.rightCategory = rightCategory;
    }

    @Override
    public boolean mousePressed(int slotIndex, int x, int y, int mouseEvent, int relativeX, int relativeY) {
        Minecraft mc = Minecraft.getMinecraft();

        if (this.left != null && this.left.mousePressed(mc, x, y)) {
            this.playPressSound(mc);
            mc.displayGuiScreen(new SodiumOptionsScreen(mc.currentScreen, this.leftCategory));
            return true;
        }

        if (this.right != null && this.right.mousePressed(mc, x, y)) {
            this.playPressSound(mc);
            mc.displayGuiScreen(new SodiumOptionsScreen(mc.currentScreen, this.rightCategory));
            return true;
        }

        return false;
    }

    @Override
    public void drawEntry(int slotIndex, int x, int y, int listWidth, int slotHeight,
                          int mouseX, int mouseY, boolean isSelected) {
        Minecraft mc = Minecraft.getMinecraft();

        if (this.left != null) {
            this.left.yPosition = y;
            this.left.drawButton(mc, mouseX, mouseY);
        }

        if (this.right != null) {
            this.right.yPosition = y;
            this.right.drawButton(mc, mouseX, mouseY);
        }
    }

    @Override
    public void mouseReleased(int slotIndex, int x, int y, int mouseEvent, int relativeX, int relativeY) {
        if (this.left != null) {
            this.left.mouseReleased(x, y);
        }

        if (this.right != null) {
            this.right.mouseReleased(x, y);
        }
    }

    private void playPressSound(Minecraft mc) {
        mc.getSoundHandler().playSound(net.minecraft.client.audio.PositionedSoundRecord.create(PRESS_SOUND, 1.0F));
    }
}
