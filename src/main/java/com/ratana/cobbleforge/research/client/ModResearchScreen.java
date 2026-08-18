package com.ratana.cobbleforge.research.client;

import com.ratana.cobbleforge.research.menu.ModResearchMenu;
import com.ratana.cobbleforge.research.network.ResearchActionPayload;
import com.ratana.cobbleforge.research.node.ModResearchNodes;
import com.ratana.cobbleforge.research.node.NodeSlotLayout;
import com.ratana.cobbleforge.research.node.ResearchNodeDefinition;
import com.ratana.cobbleforge.research.player.NodeProgress;


import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;
import net.minecraft.client.gui.components.Button;

import org.joml.Vector2f;
import java.util.Map;
import java.util.List;
import java.util.Optional;

/**
 * Renders the research nodes as a star field. Star position comes from
 * NodeSlotLayout.screenPosition (stable angle per node, radius shrinks with rank).
 *
 * The pay-wall popup here is a minimal placeholder (draw a panel, click confirmPurchase
 * manually) -- swap in a real Button widget once you're happy with the flow; the
 * plumbing to the server (confirmPurchase -> ResearchActionPayload) is already wired.
 */
@OnlyIn(Dist.CLIENT)
public class ModResearchScreen extends AbstractContainerScreen<ModResearchMenu> {

    private static final int STAR_SIZE = 6;
    private static final int FIELD_RADIUS = 70;
    private Button confirmButton;

    /** Node currently selected for the pay-wall popup, if any. */
    private ResourceLocation selectedNode;

    public ModResearchScreen(ModResearchMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 236;
        this.imageHeight = 240;
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF0B0B1A); // night-sky panel

        int centerX = x + imageWidth / 2;
        int centerY = y + 90;

        List<ResourceLocation> order = menu.getCachedSlotOrder();
        Map<ResourceLocation, Vector2f> positions = NodeSlotLayout.layoutAll(order, centerX, centerY);

        for (ResourceLocation nodeId : order) {
            Vector2f pos = positions.get(nodeId);
            drawStar(graphics, Math.round(pos.x()), Math.round(pos.y()), progressFraction(nodeId));
        }
    }

    /** Fraction of this node's total unlock cost the player has invested so far, in [0,1]. */
    private double progressFraction(ResourceLocation nodeId) {
        ResearchNodeDefinition def = lookupDefinition(nodeId);
        if (def == null || def.totalCost() <= 0) return 0.0;
        double fraction = menu.getCachedInvested(nodeId) / (double) def.totalCost();
        return Math.max(0.0, Math.min(1.0, fraction));
    }

    private void drawStar(GuiGraphics graphics, int cx, int cy, double fraction) {
        // Placeholder squares -- swap for a real star sprite atlas later. Color runs
        // white (untouched) -> gold (fully paid), independent of star size, so an
        // in-progress node reads at a glance regardless of its current rank/radius.
        int color = lerpWhiteToGold(fraction);
        int half = STAR_SIZE / 2;
        graphics.fill(cx - half, cy - half, cx + half, cy + half, color);
    }

    private static int lerpWhiteToGold(double fraction) {
        // White (255,255,255) -> gold (241,196,15)
        int r = lerpChannel(255, 241, fraction);
        int g = lerpChannel(255, 196, fraction);
        int b = lerpChannel(255, 15, fraction);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(int from, int to, double fraction) {
        return from + (int) Math.round((to - from) * fraction);
    }

    private static ResearchNodeDefinition lookupDefinition(ResourceLocation nodeId) {
        return ModResearchNodes.get(nodeId);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, "Research Points: " + menu.getCachedPoints(), 8, 6, 0xFFFFFF, false);
        if (selectedNode != null) {
            renderPaywallPopup(graphics, selectedNode);
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        Optional<ResourceLocation> hit = findStarAt(mouseX, mouseY);
        if (hit.isPresent()) {
            openPopupFor(hit.get());
            return true;
        }
        if (selectedNode != null && !isInsidePopup(mouseX, mouseY)) {
            closePopup();
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private Optional<ResourceLocation> findStarAt(double mouseX, double mouseY) {
        int centerX = leftPos + imageWidth / 2;
        int centerY = topPos + 90;
        List<ResourceLocation> order = menu.getCachedSlotOrder();
        Map<ResourceLocation, Vector2f> positions = NodeSlotLayout.layoutAll(order, centerX, centerY);

        for (ResourceLocation nodeId : order) {
            Vector2f pos = positions.get(nodeId);
            double dist = Math.hypot(mouseX - pos.x(), mouseY - pos.y());
            if (dist <= STAR_SIZE) return Optional.of(nodeId);
        }
        return Optional.empty();
    }

    private void renderPaywallPopup(GuiGraphics graphics, ResourceLocation nodeId) {
        NodeProgress progress = menu.getCachedProgress(nodeId);
        int px = leftPos + 40, py = topPos + 190, pw = 160, ph = 40;
        graphics.fill(px, py, px + pw, py + ph, 0xEE1A1A2E);

        String label = switch (progress) {
            case LOCKED -> "Pay to reveal silhouette";
            case SILHOUETTE -> "Pay to reveal identity";
            case REVEALED -> "Pay to learn requirements";
            case READY_FOR_SACRIFICE -> "Fully unlocked";
        };
        graphics.drawString(font, label, px + 6, py + 6, 0xFFFFFF, false);
        graphics.drawString(font, "Invested: " + menu.getCachedInvested(nodeId), px + 6, py + 20, 0xAAAAAA, false);
        // TODO: replace with a real Button widget wired to confirmPurchase(nodeId, ...)
    }

    private boolean isInsidePopup(double mouseX, double mouseY) {
        int px = leftPos + 40, py = topPos + 190, pw = 160, ph = 40;
        return mouseX >= px && mouseX <= px + pw && mouseY >= py && mouseY <= py + ph;
    }

    /** Call this from the popup's confirm button once you add a real Button widget. */
    private void confirmPurchase(ResourceLocation nodeId, ResearchActionPayload.Action action) {
        PacketDistributor.sendToServer(new ResearchActionPayload(nodeId, action));
        selectedNode = null;
    }

    private void openPopupFor(ResourceLocation nodeId) {
        selectedNode = nodeId;
        rebuildConfirmButton();
    }

    private void closePopup() {
        selectedNode = null;
        if (confirmButton != null) {
            removeWidget(confirmButton);
            confirmButton = null;
        }
    }

    private void rebuildConfirmButton() {
        if (confirmButton != null) {
            removeWidget(confirmButton);
            confirmButton = null;
        }
        if (selectedNode == null) return;

        NodeProgress progress = menu.getCachedProgress(selectedNode);
        ResearchActionPayload.Action action = actionForProgress(progress);
        if (action == null) return; // READY_FOR_SACRIFICE -- nothing left to buy, no button

        int px = leftPos + 40, py = topPos + 190, pw = 160, ph = 40;
        int buttonWidth = 70, buttonHeight = 16;
        int bx = px + pw - buttonWidth - 6;
        int by = py + ph - buttonHeight - 6;

        confirmButton = Button.builder(Component.literal("Confirm"),
                        btn -> confirmPurchase(selectedNode, action))
                .bounds(bx, by, buttonWidth, buttonHeight)
                .build();
        addRenderableWidget(confirmButton);
    }

    private static ResearchActionPayload.Action actionForProgress(NodeProgress progress) {
        return switch (progress) {
            case LOCKED -> ResearchActionPayload.Action.BUY_SILHOUETTE;
            case SILHOUETTE -> ResearchActionPayload.Action.BUY_REVEAL;
            case REVEALED -> ResearchActionPayload.Action.BUY_INGREDIENTS;
            case READY_FOR_SACRIFICE -> null;
        };
    }
}