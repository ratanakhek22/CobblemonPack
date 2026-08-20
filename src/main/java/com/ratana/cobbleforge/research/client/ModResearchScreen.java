package com.ratana.cobbleforge.research.client;

import com.ratana.cobbleforge.research.menu.ModResearchMenu;
import com.ratana.cobbleforge.research.network.ResearchActionPayload;
import com.ratana.cobbleforge.research.network.ResearchRedeemPayload;
import com.ratana.cobbleforge.research.network.ResearchSyncPayload;
import com.ratana.cobbleforge.research.network.ResearchViewPayload;
import com.ratana.cobbleforge.research.node.ModResearchNodes;
import com.ratana.cobbleforge.research.node.NodeSlotLayout;
import com.ratana.cobbleforge.research.node.ResearchNodeDefinition;
import com.ratana.cobbleforge.research.player.NodeProgress;

import com.cobblemon.mod.common.api.pokemon.PokemonSpecies;
import com.cobblemon.mod.common.item.PokemonItem;
import com.cobblemon.mod.common.pokemon.Species;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@OnlyIn(Dist.CLIENT)
public class ModResearchScreen extends AbstractContainerScreen<ModResearchMenu> {

    private static final int STAR_SIZE = 6;
    private static final int FIELD_RADIUS = 70;

    private static final int INVESTMENT_INCREMENT = 10;
    private static final long INVEST_DELAY_MS = 600;

    private static final ItemStack FALLBACK_SPRITE_STACK = new ItemStack(net.minecraft.world.item.Items.BARRIER);

    private static final ResourceLocation POINTS_ICON =
            ResourceLocation.fromNamespaceAndPath("cobbleforge", "textures/gui/icon/research_points.png");
    private static final ResourceLocation SLOT_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("cobbleforge", "textures/gui/slot.png");


    private static final int COLOR_NIGHT_SKY = 0xFF120B2E;
    private static final int COLOR_LEATHER_TRIM = 0xFF6B4423;
    private static final int COLOR_GOLD = 0xFFD4AF37;
    private static final int COLOR_PARCHMENT = 0xFFE8D9B5;
    private static final int COLOR_PARCHMENT_SHADOW = 0xFFCBB98A;
    private static final int COLOR_INK = 0xFF2B1D0E;
    private static final int COLOR_INK_FADED = 0xFF6B5A3E;
    private static final int COLOR_INK_HEADER = 0xFF7A1F1F;

    private enum ViewState { STAR_FIELD, NODE_DETAIL, REDEEM }

    private ViewState viewState = ViewState.STAR_FIELD;

    private ResourceLocation detailNodeId;
    private NodeProgress lastKnownProgress;

    private Button backButton;
    private Button actionButton;
    private Button forgottenKnowledgeButton;
    private Button redeemButton;
    private Button redeemConfirmButton;

    private float panX = 0f;
    private float panY = 0f;
    private boolean mouseDownForPan = false;
    private boolean didDrag = false;
    private double downX, downY;
    private double lastDragMouseX, lastDragMouseY;
    private static final double DRAG_THRESHOLD = 2.0;

    private long investPendingUntilMs = 0L;
    private boolean lastPendingRendered = false;

    private NodeTooltipProvider tooltipProvider = new DefaultNodeTooltipProvider();
    private PokemonSpriteSource spriteSource = new CobblemonSpriteSource();

    public ModResearchScreen(ModResearchMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 236;
        this.imageHeight = 240;
    }

    @Override
    protected void init() {
        super.init();
        addRedeemEntryButton();
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (viewState == ViewState.NODE_DETAIL && detailNodeId != null) {
            renderDetailBg(graphics, detailNodeId);
            return;
        }
        if (viewState == ViewState.REDEEM) {
            renderRedeemBg(graphics);
            return;
        }
        renderStarFieldBg(graphics);
    }

    private void renderStarFieldBg(GuiGraphics graphics) {
        int x = leftPos, y = topPos;

        graphics.fill(x, y, x + imageWidth, y + imageHeight, COLOR_NIGHT_SKY);
        renderNightVignette(graphics, x, y);
        renderTomeFrame(graphics, x, y);

        int centerX = x + imageWidth / 2 + Math.round(panX);
        int centerY = y + imageHeight / 2 + Math.round(panY);

        List<ResourceLocation> order = menu.getCachedSlotOrder();
        Map<ResourceLocation, Vector2f> positions = NodeSlotLayout.layoutAll(order, centerX, centerY);
        double angle = currentSpinAngle();

        drawConstellationLines(graphics, order, positions, centerX, centerY, angle);

        for (ResourceLocation nodeId : order) {
            Vector2f raw = positions.get(nodeId);
            Vector2f spun = applySpin(raw, centerX, centerY, angle);
            if (!isWithinStarFieldBounds(spun.x(), spun.y())) continue;
            drawStar(graphics, Math.round(spun.x()), Math.round(spun.y()), progressFraction(nodeId));
        }
    }

    private void renderDetailBg(GuiGraphics graphics, ResourceLocation nodeId) {
        int x = leftPos, y = topPos;

        graphics.fill(x, y, x + imageWidth, y + imageHeight, COLOR_PARCHMENT);
        renderParchmentShading(graphics, x, y);
        renderTomeFrame(graphics, x, y);

        ResearchNodeDefinition def = lookupDefinition(nodeId);
        if (def == null) return;

        if (!spriteSource.isKnown(def.speciesId())) {
            renderUnavailableDetail(graphics, def);
            return;
        }

        NodeProgress progress = menu.getCachedProgress(nodeId);
        DetailSections sections = buildDetailSections(def, progress);

        String titleText = sections.identityRevealed ? capitalize(def.speciesId().getPath()) : "???";
        drawCenteredNoShadow(graphics, titleText, x + imageWidth / 2, y + 10, COLOR_INK_HEADER);

        int contentTop = y + 26;
        int buttonRowTop = y + imageHeight - 34;
        int contentBottom = buttonRowTop - 6;
        int sectionHeight = (contentBottom - contentTop) / 3;

        int section1Top = contentTop;
        int section2Top = section1Top + sectionHeight;
        int section3Top = section2Top + sectionHeight;
        int section3Bottom = contentBottom;

        // ---- Section 1: portrait, enlarged, centered in its band ----
        int portraitSize = 32;
        int portraitX = x + imageWidth / 2 - portraitSize / 2;
        int portraitY = section1Top + (sectionHeight - portraitSize) / 2;
        renderPortraitFrame(graphics, portraitX, portraitY, portraitSize);
        if (sections.identityRevealed) {
            boolean silhouette = progress == NodeProgress.SILHOUETTE;
            renderPortrait(graphics, def.speciesId(), silhouette, portraitX, portraitY, portraitSize);
        } else {
            graphics.drawCenteredString(font, "???", x + imageWidth / 2, portraitY + portraitSize / 2 - 4, COLOR_INK);
        }

        // ---- Section 2: description ----
        graphics.drawWordWrap(font, sections.description, x + 12, section2Top, imageWidth - 24, COLOR_INK);

        // ---- Section 3: recipe / next steps ----
        graphics.drawString(font, sections.recipeHeader, x + 12, section3Top, COLOR_INK_HEADER, false);
        int lineY = section3Top + 12;
        for (Component line : sections.recipeLines) {
            graphics.drawString(font, line, x + 12, lineY, COLOR_INK_FADED, false);
            lineY += 10;
            if (lineY > section3Bottom) break;
        }
    }

    private void renderRedeemBg(GuiGraphics graphics) {
        int x = leftPos, y = topPos;

        graphics.fill(x, y, x + imageWidth, y + imageHeight, COLOR_PARCHMENT);
        renderParchmentShading(graphics, x, y);
        renderTomeFrame(graphics, x, y);

        // Shared slot backgrounds — coordinates MUST match ModResearchMenu's ConditionalSlot
        // positions exactly, since nothing ties these two together automatically.
        renderSlotBackground(graphics, x + 12, y + 92);
        renderSlotBackground(graphics, x + 12, y + 120);
        renderSlotBackground(graphics, x + 206, y + 92);
        renderSlotBackground(graphics, x + 206, y + 120);

        // Player inventory backgrounds
        int inventoryLeft = x + (imageWidth - 162) / 2;
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                renderSlotBackground(graphics, inventoryLeft + col * 18, y + 160 + row * 18);
            }
        }
        for (int col = 0; col < 9; col++) {
            renderSlotBackground(graphics, inventoryLeft + col * 18, y + 160 + 58);
        }

        drawCenteredNoShadow(graphics, "Redeem", x + imageWidth / 2, y + 10, COLOR_INK_HEADER);

        ResearchSyncPayload.RedeemResult result = menu.getLastRedeemResult();
        if (result != null) {
            String text = redeemResultText(result);
            drawCenteredNoShadow(graphics, text, x + imageWidth / 2, y + imageHeight / 2 - 25, COLOR_INK_HEADER);
        }
    }

    private void renderUnavailableDetail(GuiGraphics graphics, ResearchNodeDefinition def) {
        int x = leftPos, y = topPos;

        int portraitX = x + (imageWidth - 16) / 2;
        int portraitY = y + 26;
        renderPortraitFrame(graphics, portraitX, portraitY, 16);
        graphics.renderItem(FALLBACK_SPRITE_STACK, portraitX, portraitY);

        int textY = portraitY + 24;
        graphics.drawCenteredString(font, "Research entry unavailable", x + imageWidth / 2, textY, COLOR_INK_HEADER);

        Component detail = Component.literal(
                "This Pokémon isn't in the current modpack, or its entry is misconfigured. (" + def.speciesId() + ")");
        graphics.drawWordWrap(font, detail, x + 12, textY + 14, imageWidth - 24, COLOR_INK_FADED);
    }

    private void renderPortraitFrame(GuiGraphics graphics, int px, int py, int size) {
        int pad = 3;
        graphics.fill(px - pad, py - pad, px + size + pad, py + size + pad, COLOR_PARCHMENT_SHADOW);
        graphics.fill(px - pad, py - pad, px + size + pad, py - pad + 1, COLOR_GOLD);
        graphics.fill(px - pad, py + size + pad - 1, px + size + pad, py + size + pad, COLOR_GOLD);
        graphics.fill(px - pad, py - pad, px - pad + 1, py + size + pad, COLOR_GOLD);
        graphics.fill(px + size + pad - 1, py - pad, px + size + pad, py + size + pad, COLOR_GOLD);
    }

    private void renderPortrait(GuiGraphics graphics, ResourceLocation speciesId, boolean silhouette, int x, int y, int size) {
        ItemStack stack = spriteSource.spriteFor(speciesId, silhouette);
        if (stack == null) stack = FALLBACK_SPRITE_STACK;
        float scale = size / 16f;
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        graphics.pose().scale(scale, scale, 1f);
        graphics.renderItem(stack, 0, 0);
        graphics.pose().popPose();
    }

    private double progressFraction(ResourceLocation nodeId) {
        ResearchNodeDefinition def = lookupDefinition(nodeId);
        if (def == null || def.totalCost() <= 0) return 0.0;
        double fraction = menu.getCachedInvested(nodeId) / (double) def.totalCost();
        return Math.clamp(fraction, 0.0, 1.0);
    }

    private void drawStar(GuiGraphics graphics, int cx, int cy, double fraction) {
        int color = lerpWhiteToGold(fraction);
        int glowColor = (color & 0x00FFFFFF) | 0x33000000;

        int glowHalf = STAR_SIZE + 3;
        graphics.fill(cx - glowHalf, cy - glowHalf, cx + glowHalf, cy + glowHalf, glowColor);

        graphics.fill(cx - STAR_SIZE, cy - 1, cx + STAR_SIZE, cy + 1, color);
        graphics.fill(cx - 1, cy - STAR_SIZE, cx + 1, cy + STAR_SIZE, color);

        int core = 2;
        graphics.fill(cx - core, cy - core, cx + core, cy + core, 0xFFFFFFFF);
    }

    private void drawConstellationLines(GuiGraphics graphics, List<ResourceLocation> order,
                                        Map<ResourceLocation, Vector2f> positions,
                                        int centerX, int centerY, double angle) {
        if (order.size() < 2) return;
        int lineColor = 0x40D4AF37;
        for (int i = 0; i < order.size() - 1; i++) {
            Vector2f a = positions.get(order.get(i));
            Vector2f b = positions.get(order.get(i + 1));
            if (a == null || b == null) continue;
            Vector2f sa = applySpin(a, centerX, centerY, angle);
            Vector2f sb = applySpin(b, centerX, centerY, angle);
            // Simplification: drops the whole segment if either end is off-panel, rather than
            // clipping at the boundary — a long line will vanish slightly before its visible
            // portion runs out. Fine as a first pass; revisit if it looks wrong in practice.
            if (!isWithinStarFieldBounds(sa.x(), sa.y()) || !isWithinStarFieldBounds(sb.x(), sb.y())) continue;
            drawLine(graphics, Math.round(sa.x()), Math.round(sa.y()), Math.round(sb.x()), Math.round(sb.y()), lineColor);
        }
    }

    private static void drawLine(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        int dx = Math.abs(x2 - x1);
        int dy = -Math.abs(y2 - y1);
        int sx = x1 < x2 ? 1 : -1;
        int sy = y1 < y2 ? 1 : -1;
        int err = dx + dy;
        int x = x1, y = y1;
        while (true) {
            graphics.fill(x, y, x + 1, y + 1, color);
            if (x == x2 && y == y2) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x += sx; }
            if (e2 <= dx) { err += dx; y += sy; }
        }
    }

    private void renderTomeFrame(GuiGraphics graphics, int x, int y) {
        int w = imageWidth, h = imageHeight;
        int border = 4;

        graphics.fill(x, y, x + w, y + border, COLOR_LEATHER_TRIM);
        graphics.fill(x, y + h - border, x + w, y + h, COLOR_LEATHER_TRIM);
        graphics.fill(x, y, x + border, y + h, COLOR_LEATHER_TRIM);
        graphics.fill(x + w - border, y, x + w, y + h, COLOR_LEATHER_TRIM);

        int gx = x + border, gy = y + border;
        int gw = w - border * 2, gh = h - border * 2;
        graphics.fill(gx, gy, gx + gw, gy + 1, COLOR_GOLD);
        graphics.fill(gx, gy + gh - 1, gx + gw, gy + gh, COLOR_GOLD);
        graphics.fill(gx, gy, gx + 1, gy + gh, COLOR_GOLD);
        graphics.fill(gx + gw - 1, gy, gx + gw, gy + gh, COLOR_GOLD);
    }

    private void renderNightVignette(GuiGraphics graphics, int x, int y) {
        int w = imageWidth, h = imageHeight;
        for (int i = 0; i < 5; i++) {
            int alpha = 0x22 - i * 4;
            if (alpha <= 0) break;
            int color = alpha << 24;
            graphics.fill(x, y + i, x + w, y + i + 1, color);
            graphics.fill(x, y + h - i - 1, x + w, y + h - i, color);
            graphics.fill(x + i, y, x + i + 1, y + h, color);
            graphics.fill(x + w - i - 1, y, x + w - i, y + h, color);
        }
    }

    private void renderParchmentShading(GuiGraphics graphics, int x, int y) {
        int w = imageWidth, h = imageHeight;
        for (int i = 0; i < 5; i++) {
            int alpha = 0x20 - i * 4;
            if (alpha <= 0) break;
            int color = (alpha << 24) | 0x3B2415;
            graphics.fill(x, y + i, x + w, y + i + 1, color);
            graphics.fill(x, y + h - i - 1, x + w, y + h - i, color);
            graphics.fill(x + i, y, x + i + 1, y + h, color);
            graphics.fill(x + w - i - 1, y, x + w - i, y + h, color);
        }
    }

    private boolean isWithinStarFieldBounds(float px, float py) {
        int x = leftPos, y = topPos;
        int border = 4; // matches renderTomeFrame's border thickness
        return px >= x + border && px <= x + imageWidth - border
                && py >= y + border && py <= y + imageHeight - border;
    }

    private void drawCenteredNoShadow(GuiGraphics graphics, String text, int centerX, int y, int color) {
        int width = font.width(text);
        graphics.drawString(font, text, centerX - width / 2, y, color, false);
    }

    private void renderSlotBackground(GuiGraphics graphics, int x, int y) {
        graphics.blit(SLOT_TEXTURE, x - 1, y - 1, 0, 0, 18, 18, 18, 18);
    }

    private static int lerpWhiteToGold(double fraction) {
        int r = lerpChannel(255, 241, fraction);
        int g = lerpChannel(255, 196, fraction);
        int b = lerpChannel(255, 15, fraction);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int lerpChannel(int from, int to, double fraction) {
        return from + (int) Math.round((to - from) * fraction);
    }

    /** "mr_mime" -> "Mr Mime". Guessing your species paths use underscores as word separators —
     *  adjust the split character if that's wrong for your actual node data. */
    private static String capitalize(String path) {
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }

    private static ResearchNodeDefinition lookupDefinition(ResourceLocation nodeId) {
        return ModResearchNodes.get(nodeId);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        String text = String.valueOf(menu.getCachedPoints());
        int iconSize = 12;
        int gap = 3;
        int textWidth = font.width(text);

        int totalWidth = iconSize + gap + textWidth;
        int startX = imageWidth - totalWidth - 8;
        int iconY = 8;

        graphics.blit(POINTS_ICON, startX, iconY, 0, 0, iconSize, iconSize, iconSize, iconSize);
        graphics.drawString(font, text, startX + iconSize + gap, iconY + 2, COLOR_GOLD, false);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (viewState == ViewState.NODE_DETAIL && detailNodeId != null) {
            NodeProgress current = menu.getCachedProgress(detailNodeId);
            boolean pendingNow = isInvestPending();
            if (current != lastKnownProgress || pendingNow != lastPendingRendered) {
                rebuildDetailWidgets();
            }
            lastPendingRendered = pendingNow;

            if (pendingNow && actionButton != null) {
                actionButton.setMessage(Component.literal("Researching" + spinnerDots()));
            }
            if (pendingNow && forgottenKnowledgeButton != null) {
                forgottenKnowledgeButton.setMessage(Component.literal("Researching" + spinnerDots()));
            }
        }
        if (viewState == ViewState.REDEEM) {
            updateRedeemConfirmButtonState();
        }

        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        if (viewState == ViewState.STAR_FIELD) {
            renderStarHoverTooltip(graphics, mouseX, mouseY);
        }
        renderTooltip(graphics, mouseX, mouseY);
    }

    private boolean isInvestPending() {
        return System.currentTimeMillis() < investPendingUntilMs;
    }

    private String spinnerDots() {
        long elapsed = INVEST_DELAY_MS - (investPendingUntilMs - System.currentTimeMillis());
        int frame = (int) ((Math.max(elapsed, 0) / 200) % 3);
        return ".".repeat(frame + 1);
    }

    private void renderStarHoverTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        Optional<ResourceLocation> hovered = findStarAt(mouseX, mouseY);
        if (hovered.isEmpty()) return;

        ResourceLocation nodeId = hovered.get();
        ResearchNodeDefinition def = lookupDefinition(nodeId);
        if (def == null) return;

        NodeProgress progress = menu.getCachedProgress(nodeId);
        int invested = menu.getCachedInvested(nodeId);
        List<Component> lines = tooltipProvider.buildTooltip(def, progress, invested);
        graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        boolean hitWidget = this.children().stream().anyMatch(c -> c.isMouseOver(mouseX, mouseY));

        if (!hitWidget && viewState == ViewState.STAR_FIELD && button == 0) {
            mouseDownForPan = true;
            didDrag = false;
            downX = mouseX;
            downY = mouseY;
            lastDragMouseX = mouseX;
            lastDragMouseY = mouseY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (viewState == ViewState.STAR_FIELD && mouseDownForPan && button == 0) {
            panX += (float) (mouseX - lastDragMouseX);
            panY += (float) (mouseY - lastDragMouseY);
            lastDragMouseX = mouseX;
            lastDragMouseY = mouseY;
            if (!didDrag && Math.hypot(mouseX - downX, mouseY - downY) > DRAG_THRESHOLD) {
                didDrag = true;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseDownForPan) {
            mouseDownForPan = false;
            if (!didDrag) {
                findStarAt(mouseX, mouseY).ifPresent(this::openDetail);
            }
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (viewState == ViewState.NODE_DETAIL) {
                closeDetail();
                return true;
            }
            if (viewState == ViewState.REDEEM) {
                closeRedeem();
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** FIXED: previous version computed `dist` but never compared it against STAR_SIZE or
     *  returned a hit — every click fell through to Optional.empty(), so star clicking was
     *  silently non-functional. Restored the missing threshold check + return. */
    private Optional<ResourceLocation> findStarAt(double mouseX, double mouseY) {
        int centerX = leftPos + imageWidth / 2 + Math.round(panX);
        int centerY = topPos + imageHeight / 2 + Math.round(panY);
        List<ResourceLocation> order = menu.getCachedSlotOrder();
        Map<ResourceLocation, Vector2f> positions = NodeSlotLayout.layoutAll(order, centerX, centerY);
        double angle = currentSpinAngle();

        for (ResourceLocation nodeId : order) {
            Vector2f raw = positions.get(nodeId);
            Vector2f spun = applySpin(raw, centerX, centerY, angle);
            if (!isWithinStarFieldBounds(spun.x(), spun.y())) continue;
            double dist = Math.hypot(mouseX - spun.x(), mouseY - spun.y());
            if (dist <= STAR_SIZE) return Optional.of(nodeId);
        }
        return Optional.empty();
    }

    /** Rotates a point around (centerX, centerY) by angle radians. Pan is intentionally NOT
     *  applied here — callers pass an already-panned center, so spin and pan compose cleanly
     *  without double-applying either. */
    private Vector2f applySpin(Vector2f pos, float centerX, float centerY, double angle) {
        float dx = pos.x() - centerX;
        float dy = pos.y() - centerY;
        double cos = Math.cos(angle), sin = Math.sin(angle);
        return new Vector2f(
                centerX + (float) (dx * cos - dy * sin),
                centerY + (float) (dx * sin + dy * cos));
    }

    private double currentSpinAngle() {
        // Full rotation every 6 minutes — slow enough not to fight clicking. Retune freely.
        return (System.currentTimeMillis() % 360000) / 360000.0 * Math.PI * 2;
    }

    private void openDetail(ResourceLocation nodeId) {
        removeRedeemEntryButton();
        viewState = ViewState.NODE_DETAIL;
        detailNodeId = nodeId;
        lastKnownProgress = menu.getCachedProgress(nodeId);
        rebuildDetailWidgets();
    }

    private void closeDetail() {
        removeDetailWidgets();
        viewState = ViewState.STAR_FIELD;
        detailNodeId = null;
        lastKnownProgress = null;
        panX = 0f;
        panY = 0f;
        addRedeemEntryButton();
    }

    private void rebuildDetailWidgets() {
        removeDetailWidgets();
        if (detailNodeId == null) return;

        ResearchNodeDefinition def = lookupDefinition(detailNodeId);
        NodeProgress progress = menu.getCachedProgress(detailNodeId);
        lastKnownProgress = progress;

        int x = leftPos, y = topPos;

        backButton = Button.builder(Component.literal("< Back"), btn -> closeDetail())
                .bounds(x + 8, y + 8, 50, 16)
                .build();
        addRenderableWidget(backButton);

        if (def == null || !spriteSource.isKnown(def.speciesId())) return;

        // ---- Bottom row: two buttons, even halves, side by side ----
        int by = y + imageHeight - 34, bh = 20;
        int gap = 4;
        int halfWidth = (imageWidth - 16 - gap) / 2;
        int bx = x + 8;
        int fkx = bx + halfWidth + gap;

        if (!canInvestMore(progress, def)) {
            actionButton = Button.builder(Component.literal("Fully Unlocked"), btn -> {})
                    .bounds(bx, by, halfWidth, bh)
                    .build();
            actionButton.active = false;
            addRenderableWidget(actionButton);
            return;
        }

        boolean pending = isInvestPending();
        boolean journalPresent = menu.hasJournalClient();
        boolean affordable = menu.getCachedPoints() >= INVESTMENT_INCREMENT;

        Component label = pending
                ? Component.literal("Researching" + spinnerDots())
                : Component.literal("Research Deeper (" + INVESTMENT_INCREMENT + ")");

        actionButton = Button.builder(label, btn -> startInvestment(detailNodeId))
                .bounds(bx, by, halfWidth, bh)
                .build();
        actionButton.active = journalPresent && affordable && !pending;
        addRenderableWidget(actionButton);

        boolean hasForgottenKnowledge = menu.getForgottenKnowledgeCount() >= 1;

        if (hasForgottenKnowledge) {
            Component fkLabel = pending
                    ? Component.literal("Researching" + spinnerDots())
                    : Component.literal("Use Forgotten Knowledge");

            forgottenKnowledgeButton = Button.builder(fkLabel, btn -> startForgottenKnowledge(detailNodeId))
                    .bounds(fkx, by, halfWidth, bh)
                    .build();
            forgottenKnowledgeButton.active = journalPresent && !pending;
            addRenderableWidget(forgottenKnowledgeButton);
        }
    }

    private static boolean canInvestMore(NodeProgress progress, ResearchNodeDefinition def) {
        if (progress == NodeProgress.READY_FOR_SACRIFICE) return false;
        return !(def.bespoke() && progress == NodeProgress.REVEALED);
    }

    private void removeDetailWidgets() {
        if (backButton != null) {
            removeWidget(backButton);
            backButton = null;
        }
        if (actionButton != null) {
            removeWidget(actionButton);
            actionButton = null;
        }
        if (forgottenKnowledgeButton != null) {
            removeWidget(forgottenKnowledgeButton);
            forgottenKnowledgeButton = null;
        }
        if (redeemConfirmButton != null) {
            removeWidget(redeemConfirmButton);
            redeemConfirmButton = null;
        }
    }

    private void startInvestment(ResourceLocation nodeId) {
        PacketDistributor.sendToServer(new ResearchActionPayload(nodeId, ResearchActionPayload.Action.INVEST));

        investPendingUntilMs = System.currentTimeMillis() + INVEST_DELAY_MS;
        if (actionButton != null) {
            actionButton.active = false;
        }
    }

    private void startForgottenKnowledge(ResourceLocation nodeId) {
        PacketDistributor.sendToServer(new ResearchActionPayload(nodeId, ResearchActionPayload.Action.SPEND_FORGOTTEN_KNOWLEDGE));

        investPendingUntilMs = System.currentTimeMillis() + INVEST_DELAY_MS;
        if (actionButton != null) actionButton.active = false;
        if (forgottenKnowledgeButton != null) forgottenKnowledgeButton.active = false;
    }

    private void addRedeemEntryButton() {
        int x = leftPos, y = topPos;
        redeemButton = Button.builder(Component.literal("Redeem"), btn -> openRedeem())
                .bounds(x + 8, y + 8, 50, 16)
                .build();
        addRenderableWidget(redeemButton);
    }

    private void removeRedeemEntryButton() {
        if (redeemButton != null) {
            removeWidget(redeemButton);
            redeemButton = null;
        }
    }

    private void openRedeem() {
        removeRedeemEntryButton();
        viewState = ViewState.REDEEM;
        PacketDistributor.sendToServer(new ResearchViewPayload(true));
        menu.setView(ModResearchMenu.MenuView.REDEEM);
        rebuildRedeemWidgets();
    }

    private String redeemResultText(ResearchSyncPayload.RedeemResult result) {
        if (result.wasFallback()) {
            return result.amount() + " points (no eligible target)";
        }
        String label = result.nodeId()
                .map(id -> {
                    NodeProgress progress = menu.getCachedProgress(id);
                    return progress == NodeProgress.LOCKED ? "???" : capitalize(id.getPath());
                })
                .orElse("???");
        return result.amount() + " points towards " + label;
    }

    private void closeRedeem() {
        removeRedeemWidgets();
        viewState = ViewState.STAR_FIELD;
        PacketDistributor.sendToServer(new ResearchViewPayload(false));
        menu.setView(ModResearchMenu.MenuView.EXPLORE);
        menu.clearLastRedeemResult();
        panX = 0f;
        panY = 0f;
        addRedeemEntryButton();
    }

    private void rebuildRedeemWidgets() {
        removeDetailWidgets();

        int x = leftPos, y = topPos;
        backButton = Button.builder(Component.literal("< Back"), btn -> closeRedeem())
                .bounds(x + 8, y + 8, 50, 16)
                .build();
        addRenderableWidget(backButton);

        int bw = 100, bh = 20;
        int bx = x + (imageWidth - bw) / 2, by = y + 115; // above the inventory, which starts at y+160
        redeemConfirmButton = Button.builder(Component.literal("Redeem"), btn -> startRedeem())
                .bounds(bx, by, bw, bh)
                .build();
        updateRedeemConfirmButtonState();
        addRenderableWidget(redeemConfirmButton);
    }

    private void updateRedeemConfirmButtonState() {
        if (redeemConfirmButton == null) return;
        boolean ready = menu.hasJournalClient() && menu.hasAncientItemClient() && menu.hasBrushClient() && !isInvestPending();
        redeemConfirmButton.active = ready;
    }

    private void startRedeem() {
        PacketDistributor.sendToServer(new ResearchRedeemPayload());
        investPendingUntilMs = System.currentTimeMillis() + INVEST_DELAY_MS;
        if (redeemConfirmButton != null) redeemConfirmButton.active = false;
    }

    private void removeRedeemWidgets() {
        removeDetailWidgets();
    }

    private record DetailSections(
            boolean identityRevealed,
            Component description,
            Component recipeHeader,
            List<Component> recipeLines
    ) {}

    private DetailSections buildDetailSections(ResearchNodeDefinition def, NodeProgress progress) {
        boolean identityRevealed = progress != NodeProgress.LOCKED;
        boolean infoRevealed = progress == NodeProgress.REVEALED || progress == NodeProgress.READY_FOR_SACRIFICE;
        boolean recipeRevealed = progress == NodeProgress.READY_FOR_SACRIFICE;

        Component description = infoRevealed ? descriptionFor(def) : Component.literal("???");

        Component recipeHeader;
        List<Component> recipeLines = new ArrayList<>();
        if (def.bespoke()) {
            recipeHeader = Component.literal("Next Steps");
            recipeLines.add(infoRevealed ? Component.literal("Quests: TODO") : Component.literal("???"));
        } else {
            recipeHeader = Component.literal("Required Items");
            if (recipeRevealed) {
                recipeLines.addAll(requiredItemLinesFor(def));
            } else {
                recipeLines.add(Component.literal("???"));
            }
        }

        return new DetailSections(identityRevealed, description, recipeHeader, recipeLines);
    }

    private static Component descriptionFor(ResearchNodeDefinition def) {
        return Component.translatable("research.cobbleforge.description." + def.speciesId().getPath());
    }

    private static List<Component> requiredItemLinesFor(ResearchNodeDefinition def) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("(recipe items TODO — wire ResearchNodeDefinition#requiredItems)"));
        return lines;
    }

    public interface NodeTooltipProvider {
        List<Component> buildTooltip(ResearchNodeDefinition def, NodeProgress progress, int invested);
    }

    public static final class DefaultNodeTooltipProvider implements NodeTooltipProvider {
        @Override
        public List<Component> buildTooltip(ResearchNodeDefinition def, NodeProgress progress, int invested) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(progress == NodeProgress.LOCKED ? "???" : capitalize(def.speciesId().getPath())));
            lines.add(Component.literal(stageLabel(progress)));
            lines.add(Component.literal("Invested: " + invested));
            return lines;
        }

        private static String stageLabel(NodeProgress progress) {
            return switch (progress) {
                case LOCKED -> "Locked";
                case SILHOUETTE -> "Silhouette revealed";
                case REVEALED -> "Identity revealed";
                case READY_FOR_SACRIFICE -> "Ready";
            };
        }
    }

    public void setTooltipProvider(NodeTooltipProvider provider) {
        this.tooltipProvider = provider;
    }

    public interface PokemonSpriteSource {
        @Nullable
        ItemStack spriteFor(ResourceLocation speciesId, boolean silhouette);

        boolean isKnown(ResourceLocation speciesId);
    }

    private static final class CobblemonSpriteSource implements PokemonSpriteSource {
        private static final Vector4f SILHOUETTE_TINT = new Vector4f(0f, 0f, 0f, 1f);

        @Override
        public @Nullable ItemStack spriteFor(ResourceLocation speciesId, boolean silhouette) {
            Species species = PokemonSpecies.getByIdentifier(speciesId);
            if (species == null) return null;

            Vector4f tint = silhouette ? SILHOUETTE_TINT : null;
            return PokemonItem.from(species, new String[0], 1, tint);
        }

        @Override
        public boolean isKnown(ResourceLocation speciesId) {
            return PokemonSpecies.getByIdentifier(speciesId) != null;
        }
    }

    public void setSpriteSource(PokemonSpriteSource source) {
        this.spriteSource = source;
    }
}