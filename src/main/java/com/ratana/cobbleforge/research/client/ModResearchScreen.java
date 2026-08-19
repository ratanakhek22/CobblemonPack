package com.ratana.cobbleforge.research.client;

import com.ratana.cobbleforge.research.menu.ModResearchMenu;
import com.ratana.cobbleforge.research.network.ResearchActionPayload;
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

    /** Every "research deeper" click costs exactly this, regardless of node or stage — the
     *  whole point is that the cost can never hint at what kind of node this is. */
    private static final int INVESTMENT_INCREMENT = 10;

    /** Client-side-only cooldown after a click, so a fast double-click can't fire two
     *  investments before the first sync response lands. Purely a UX/race guard — the
     *  server should still be the actual source of truth on what's affordable. */
    private static final long INVEST_DELAY_MS = 600;

    /** Built-in placeholder used whenever a real sprite can't be resolved (unknown species, etc). */
    private static final ItemStack FALLBACK_SPRITE_STACK = new ItemStack(net.minecraft.world.item.Items.BARRIER);

    // ---------------- tome color palette ----------------
    // Kept as named constants so the look can be re-tuned in one place without touching
    // any of the layout/render call sites below.

    /** Deep night-sky panel color for the star-field page. */
    private static final int COLOR_NIGHT_SKY = 0xFF120B2E;
    /** Leather cover / binding color used for the frame around both pages. */
    private static final int COLOR_LEATHER_TRIM = 0xFF6B4423;
    /** Gilt edging accent — pinstripe border, dividers, star color at full progress. */
    private static final int COLOR_GOLD = 0xFFD4AF37;
    /** Aged parchment page background for the detail/"tome page" view. */
    private static final int COLOR_PARCHMENT = 0xFFE8D9B5;
    /** Slightly darker parchment tone, used for edge shading and the portrait frame backing. */
    private static final int COLOR_PARCHMENT_SHADOW = 0xFFCBB98A;
    /** Main ink color for body text on the parchment page. */
    private static final int COLOR_INK = 0xFF2B1D0E;
    /** Faded ink, for secondary/placeholder text ("???", required items list, etc). */
    private static final int COLOR_INK_FADED = 0xFF6B5A3E;
    /** Illuminated-manuscript-style deep red, used for section headers. */
    private static final int COLOR_INK_HEADER = 0xFF7A1F1F;

    private enum ViewState { STAR_FIELD, NODE_DETAIL }

    private ViewState viewState = ViewState.STAR_FIELD;

    private ResourceLocation detailNodeId;

    /** Used to detect a purchase landing while the detail view is open, so we can rebuild it. */
    private NodeProgress lastKnownProgress;

    private Button backButton;
    private Button actionButton;

    /** 0 = no investment pending. Otherwise, a click is "in flight" until this time passes. */
    private long investPendingUntilMs = 0L;
    private boolean lastPendingRendered = false;

    /** Swap this out freely while testing different tooltip layouts/content. */
    private NodeTooltipProvider tooltipProvider = new DefaultNodeTooltipProvider();

    /** Swap this out to try a different sprite strategy; falls back to FALLBACK_SPRITE_STACK either way. */
    private PokemonSpriteSource spriteSource = new CobblemonSpriteSource();

    public ModResearchScreen(ModResearchMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 236;
        this.imageHeight = 240;
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        if (viewState == ViewState.NODE_DETAIL && detailNodeId != null) {
            renderDetailBg(graphics, detailNodeId);
            return;
        }
        renderStarFieldBg(graphics);
    }

    private void renderStarFieldBg(GuiGraphics graphics) {
        int x = leftPos, y = topPos;

        graphics.fill(x, y, x + imageWidth, y + imageHeight, COLOR_NIGHT_SKY);
        renderNightVignette(graphics, x, y);
        renderTomeFrame(graphics, x, y);

        int centerX = x + imageWidth / 2;
        int centerY = y + 90;

        List<ResourceLocation> order = menu.getCachedSlotOrder();
        Map<ResourceLocation, Vector2f> positions = NodeSlotLayout.layoutAll(order, centerX, centerY);

        drawConstellationLines(graphics, order, positions);

        for (ResourceLocation nodeId : order) {
            Vector2f pos = positions.get(nodeId);
            drawStar(graphics, Math.round(pos.x()), Math.round(pos.y()), progressFraction(nodeId));
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

        // Portrait area
        int portraitX = x + (imageWidth - 16) / 2;
        int portraitY = y + 26;
        renderPortraitFrame(graphics, portraitX, portraitY);
        if (sections.identityRevealed) {
            boolean silhouette = progress == NodeProgress.SILHOUETTE;
            renderPortrait(graphics, def.speciesId(), silhouette, portraitX, portraitY);
        } else {
            graphics.drawCenteredString(font, "???", x + imageWidth / 2, portraitY + 4, COLOR_INK);
        }

        // Description
        int descY = portraitY + 40;
        graphics.drawWordWrap(font, sections.description, x + 12, descY, imageWidth - 24, COLOR_INK);

        // Purely decorative divider between description and the recipe/next-steps section —
        // doesn't move recipeY/lineY, just draws a rule above them.
        int dividerY = descY + 32;
        graphics.fill(x + 12, dividerY, x + imageWidth - 12, dividerY + 1, COLOR_GOLD);

        // Recipe / next-steps section
        int recipeY = descY + 40;
        graphics.drawString(font, sections.recipeHeader, x + 12, recipeY, COLOR_INK_HEADER, false);
        int lineY = recipeY + 12;
        for (Component line : sections.recipeLines) {
            graphics.drawString(font, line, x + 12, lineY, COLOR_INK_FADED, false);
            lineY += 10;
        }
    }

    /**
     * A node whose species doesn't resolve at all (typo'd id, species removed from the
     * modpack, addon mod not installed, etc). Deliberately its own render path rather than a
     * fallback baked into the normal LOCKED/SILHOUETTE/REVEALED flow — none of those stages
     * mean anything if there's no real species behind them, so we don't let the player spend
     * points revealing further into something that can never resolve.
     */
    private void renderUnavailableDetail(GuiGraphics graphics, ResearchNodeDefinition def) {
        int x = leftPos, y = topPos;

        int portraitX = x + (imageWidth - 16) / 2;
        int portraitY = y + 26;
        renderPortraitFrame(graphics, portraitX, portraitY);
        graphics.renderItem(FALLBACK_SPRITE_STACK, portraitX, portraitY);

        int textY = portraitY + 24;
        graphics.drawCenteredString(font, "Research entry unavailable", x + imageWidth / 2, textY, COLOR_INK_HEADER);

        Component detail = Component.literal(
                "This Pokémon isn't in the current modpack, or its entry is misconfigured. (" + def.speciesId() + ")");
        graphics.drawWordWrap(font, detail, x + 12, textY + 14, imageWidth - 24, COLOR_INK_FADED);
    }

    /**
     * Renders the node's item-form sprite via GuiGraphics#renderItem (vanilla item rendering,
     * the mechanism Cobblemon's PokemonItem is built to support). Silhouette tinting is baked
     * into the returned ItemStack itself (Cobblemon's own tint parameter), not applied here.
     */
    private void renderPortrait(GuiGraphics graphics, ResourceLocation speciesId, boolean silhouette, int x, int y) {
        ItemStack stack = spriteSource.spriteFor(speciesId, silhouette);
        if (stack == null) stack = FALLBACK_SPRITE_STACK;
        graphics.renderItem(stack, x, y);
    }

    /**
     * Small gilt-edged box drawn behind the 16x16 portrait item icon, so it reads as an
     * illustration set into the page rather than a floating item sprite. Purely decorative —
     * doesn't affect the (px, py) the caller passes to renderItem.
     */
    private void renderPortraitFrame(GuiGraphics graphics, int px, int py) {
        int pad = 3;
        int size = 16;
        graphics.fill(px - pad, py - pad, px + size + pad, py + size + pad, COLOR_PARCHMENT_SHADOW);
        graphics.fill(px - pad, py - pad, px + size + pad, py - pad + 1, COLOR_GOLD);
        graphics.fill(px - pad, py + size + pad - 1, px + size + pad, py + size + pad, COLOR_GOLD);
        graphics.fill(px - pad, py - pad, px - pad + 1, py + size + pad, COLOR_GOLD);
        graphics.fill(px + size + pad - 1, py - pad, px + size + pad, py + size + pad, COLOR_GOLD);
    }

    /** Fraction of this node's total unlock cost the player has invested so far, in [0,1]. */
    private double progressFraction(ResourceLocation nodeId) {
        ResearchNodeDefinition def = lookupDefinition(nodeId);
        if (def == null || def.totalCost() <= 0) return 0.0;
        double fraction = menu.getCachedInvested(nodeId) / (double) def.totalCost();
        return Math.clamp(fraction, 0.0, 1.0);
    }

    /**
     * Draws a node as a small 4-point twinkling star with a soft glow, colored by progress
     * (white -> gold, same lerpWhiteToGold as before). The glow/arm sizes are derived from
     * STAR_SIZE so the visual stays roughly matched to findStarAt's unchanged hit radius.
     */
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

    /**
     * Faint gold lines connecting consecutive nodes in slot order, echoing a constellation
     * chart. Purely decorative — reads from the same order/positions already computed for
     * star placement and findStarAt, doesn't touch either.
     */
    private void drawConstellationLines(GuiGraphics graphics, List<ResourceLocation> order,
                                        Map<ResourceLocation, Vector2f> positions) {
        if (order.size() < 2) return;
        int lineColor = 0x40D4AF37;
        for (int i = 0; i < order.size() - 1; i++) {
            Vector2f a = positions.get(order.get(i));
            Vector2f b = positions.get(order.get(i + 1));
            if (a == null || b == null) continue;
            drawLine(graphics, Math.round(a.x()), Math.round(a.y()), Math.round(b.x()), Math.round(b.y()), lineColor);
        }
    }

    /** Simple integer Bresenham line, plotted as 1x1 fills since GuiGraphics has no line primitive. */
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

    /** Thin leather-brown + gilt pinstripe frame drawn around the whole panel, shared by both
     *  pages (and thus visible around the player-inventory area too, same as the old flat fill
     *  covered the entire panel). */
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

    /** Subtle darkened edge bands on the night-sky page, cheap stand-in for a radial vignette. */
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

    /** Aged-page edge shading on the parchment/detail page, same technique as the night
     *  vignette but tinted brown instead of black. */
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

    private static int lerpWhiteToGold(double fraction) {
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
        graphics.drawString(font, "Research Points: " + menu.getCachedPoints(), 8, 6, COLOR_GOLD, true);
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

    /** Cycles "." / ".." / "..." roughly every 200ms — placeholder animation, easy to swap
     *  for a real hourglass icon once the rest of the look gets a pass. */
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
        if (viewState == ViewState.STAR_FIELD) {
            Optional<ResourceLocation> hit = findStarAt(mouseX, mouseY);
            if (hit.isPresent()) {
                openDetail(hit.get());
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // First Esc: leave the detail view and return to the star field, same screen instance.
        // Second Esc (already on the star field): fall through to vanilla, which closes the menu.
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && viewState == ViewState.NODE_DETAIL) {
            closeDetail();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
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

    private void openDetail(ResourceLocation nodeId) {
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

        // Unavailable nodes never get an investment button — there's nothing valid to buy into.
        if (def == null || !spriteSource.isKnown(def.speciesId())) return;

        int bx = x + imageWidth - 110, by = y + imageHeight - 34, bw = 100, bh = 20;

        if (!canInvestMore(progress, def)) {
            actionButton = Button.builder(Component.literal("Fully Unlocked"), btn -> {})
                    .bounds(bx, by, bw, bh)
                    .build();
            actionButton.active = false;
            addRenderableWidget(actionButton);
            return;
        }

        boolean pending = isInvestPending();
        boolean affordable = menu.getCachedPoints() >= INVESTMENT_INCREMENT;

        Component label = pending
                ? Component.literal("Researching" + spinnerDots())
                : Component.literal("Research Deeper (" + INVESTMENT_INCREMENT + ")");

        actionButton = Button.builder(label, btn -> startInvestment(detailNodeId))
                .bounds(bx, by, bw, bh)
                .build();
        actionButton.active = affordable && !pending;
        addRenderableWidget(actionButton);
    }

    /** Whether this node can still absorb another investment at all — independent of whether
     *  the player can currently afford one. False once fully unlocked, or once a bespoke node
     *  hits its terminal REVEALED stage (bespoke nodes never reach READY_FOR_SACRIFICE — their
     *  path continues via the chain document instead, not through more investment here). */
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
    }

    private void startInvestment(ResourceLocation nodeId) {
        // NOTE: this assumes ResearchActionPayload.Action gains a generic INVEST entry, and
        // the server-side handler in ResearchNetworking adds INVESTMENT_INCREMENT to that
        // node's invested total and recomputes NodeProgress by checking which weight-derived
        // thresholds have now been crossed — rather than matching an exact "buy this stage"
        // cost like the old BUY_SILHOUETTE/BUY_REVEAL/BUY_INGREDIENTS actions did. That's a
        // real behavior change on the server side, not just a rename; I haven't seen
        // ResearchActionPayload.java or ResearchNetworking.java in full, so I've left this as
        // the shape the client needs rather than guessing at their contents.
        PacketDistributor.sendToServer(new ResearchActionPayload(nodeId, ResearchActionPayload.Action.INVEST));

        investPendingUntilMs = System.currentTimeMillis() + INVEST_DELAY_MS;
        if (actionButton != null) {
            actionButton.active = false;
        }
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
        // TODO: point this at whatever lang-key/data convention the node's flavor text ends
        // up living in (translatable key keyed by speciesId is the obvious default).
        return Component.translatable("research.cobbleforge.description." + def.speciesId().getPath());
    }

    private static List<Component> requiredItemLinesFor(ResearchNodeDefinition def) {
        // TODO: wire to the real required-items list once ResearchNodeDefinition exposes one
        // (e.g. def.requiredItems() -> List<ItemStack> or List<ResourceLocation>). Placeholder
        // below just proves the reveal-timing logic; swap the body, not the call sites.
        List<Component> lines = new ArrayList<>();
        lines.add(Component.literal("(recipe items TODO — wire ResearchNodeDefinition#requiredItems)"));
        return lines;
    }

    /** Implement this to try a different hover-tooltip layout without touching render logic. */
    public interface NodeTooltipProvider {
        List<Component> buildTooltip(ResearchNodeDefinition def, NodeProgress progress, int invested);
    }

    public static final class DefaultNodeTooltipProvider implements NodeTooltipProvider {
        @Override
        public List<Component> buildTooltip(ResearchNodeDefinition def, NodeProgress progress, int invested) {
            List<Component> lines = new ArrayList<>();
            lines.add(Component.literal(progress == NodeProgress.LOCKED ? "???" : def.speciesId().getPath()));
            lines.add(Component.literal(stageLabel(progress)));
            lines.add(Component.literal("Invested: " + invested));
//            lines.add(Component.literal("Invested: " + invested + " / " + def.totalCost()));
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

    /** Swap in for A/B-testing an alternate tooltip while keeping the default around. */
    public void setTooltipProvider(NodeTooltipProvider provider) {
        this.tooltipProvider = provider;
    }

    /**
     * Resolves a species to its Cobblemon item-form ItemStack, with the silhouette tint baked
     * in when requested. Kept as one method (not tint applied separately) since Cobblemon's
     * own PokemonItem.from takes the tint at creation time, not as a post-render step.
     */
    public interface PokemonSpriteSource {
        @Nullable
        ItemStack spriteFor(ResourceLocation speciesId, boolean silhouette);

        /** Cheap existence check, used to detect a node whose species doesn't resolve at all. */
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

    /** Swap in for testing a different sprite source while keeping Cobblemon lookup around. */
    public void setSpriteSource(PokemonSpriteSource source) {
        this.spriteSource = source;
    }
}