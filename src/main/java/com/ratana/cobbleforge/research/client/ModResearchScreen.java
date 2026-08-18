package com.ratana.cobbleforge.research.client;

import com.ratana.cobbleforge.research.menu.ModResearchMenu;
import com.ratana.cobbleforge.research.network.ResearchActionPayload;
import com.ratana.cobbleforge.research.node.ModResearchNodes;
import com.ratana.cobbleforge.research.node.NodeSlotLayout;
import com.ratana.cobbleforge.research.node.ResearchNodeDefinition;
import com.ratana.cobbleforge.research.player.NodeProgress;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.network.PacketDistributor;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Renders the research nodes as a star field, and transitions IN PLACE (no screen
 * close/reopen) into a per-node detail view when a star is clicked. Esc from the
 * detail view returns to the star field; Esc from the star field falls through to
 * vanilla behavior and closes the whole menu.

 * Two things are deliberately pluggable so they're cheap to swap while iterating:
 *  - {@link NodeTooltipProvider}: controls hover-tooltip content in the star field.
 *  - {@link PokemonSpriteSource}: controls where node art comes from (Cobblemon
 *    lookup with a built-in fallback). See the CobblemonSpriteSource TODO below
 *    before wiring the real lookup.
 */
@OnlyIn(Dist.CLIENT)
public class ModResearchScreen extends AbstractContainerScreen<ModResearchMenu> {

    private static final int STAR_SIZE = 6;
    private static final int FIELD_RADIUS = 70;

    /** Built-in placeholder used whenever a real sprite can't be resolved. */
    private static final ResourceLocation FALLBACK_SPRITE =
            ResourceLocation.fromNamespaceAndPath("cobbleforge", "textures/gui/research/unknown_pokemon.png");

    private enum ViewState { STAR_FIELD, NODE_DETAIL }

    private ViewState viewState = ViewState.STAR_FIELD;

    /** Node currently shown in the detail view, if any. */
    private ResourceLocation detailNodeId;

    /** Used to detect a purchase landing while the detail view is open, so we can rebuild it. */
    private NodeProgress lastKnownProgress;

    private Button backButton;
    private Button actionButton;

    /** Swap this out freely while testing different tooltip layouts/content. */
    private NodeTooltipProvider tooltipProvider = new DefaultNodeTooltipProvider();

    /** Swap this out once the real Cobblemon hook is wired; falls back to FALLBACK_SPRITE either way. */
    private PokemonSpriteSource spriteSource = new CobblemonSpriteSource();

    public ModResearchScreen(ModResearchMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 236;
        this.imageHeight = 240;
    }

    // ------------------------------------------------------------------
    // Background rendering — star field vs. node detail
    // ------------------------------------------------------------------

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

    private void renderDetailBg(GuiGraphics graphics, ResourceLocation nodeId) {
        int x = leftPos, y = topPos;
        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF0B0B1A);

        ResearchNodeDefinition def = lookupDefinition(nodeId);
        NodeProgress progress = menu.getCachedProgress(nodeId);
        if (def == null) return;

        DetailSections sections = buildDetailSections(def, progress);

        // Portrait area
        int portraitX = x + (imageWidth - 64) / 2;
        int portraitY = y + 26;
        if (sections.identityRevealed) {
            boolean silhouette = progress == NodeProgress.SILHOUETTE;
            ResourceLocation sprite = spriteSource.spriteFor(def.speciesId(), silhouette);
            if (sprite == null) sprite = FALLBACK_SPRITE;
            graphics.blit(sprite, portraitX, portraitY, 0, 0, 64, 64, 64, 64);
        } else {
            graphics.drawCenteredString(font, "???", x + imageWidth / 2, portraitY + 26, 0xFFFFFF);
        }

        // Description
        int descY = portraitY + 70;
        graphics.drawWordWrap(font, sections.description, x + 12, descY, imageWidth - 24, 0xDDDDDD);

        // Recipe / next-steps section
        int recipeY = descY + 40;
        graphics.drawString(font, sections.recipeHeader, x + 12, recipeY, 0xF1C40F, false);
        int lineY = recipeY + 12;
        for (Component line : sections.recipeLines) {
            graphics.drawString(font, line, x + 12, lineY, 0xCCCCCC, false);
            lineY += 10;
        }
    }

    /** Fraction of this node's total unlock cost the player has invested so far, in [0,1]. */
    private double progressFraction(ResourceLocation nodeId) {
        ResearchNodeDefinition def = lookupDefinition(nodeId);
        if (def == null || def.totalCost() <= 0) return 0.0;
        double fraction = menu.getCachedInvested(nodeId) / (double) def.totalCost();
        return Math.clamp(fraction, 0.0, 1.0);
    }

    private void drawStar(GuiGraphics graphics, int cx, int cy, double fraction) {
        int color = lerpWhiteToGold(fraction);
        int half = STAR_SIZE / 2;
        graphics.fill(cx - half, cy - half, cx + half, cy + half, color);
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

    // ------------------------------------------------------------------
    // Labels, tooltip, and top-level render
    // ------------------------------------------------------------------

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, "Research Points: " + menu.getCachedPoints(), 8, 6, 0xFFFFFF, false);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // Detail view may have advanced (a purchase landed) since the last frame — refresh widgets if so.
        if (viewState == ViewState.NODE_DETAIL && detailNodeId != null) {
            NodeProgress current = menu.getCachedProgress(detailNodeId);
            if (current != lastKnownProgress) {
                rebuildDetailWidgets();
            }
        }

        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);

        if (viewState == ViewState.STAR_FIELD) {
            renderStarHoverTooltip(graphics, mouseX, mouseY);
        }
        renderTooltip(graphics, mouseX, mouseY);
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

    // ------------------------------------------------------------------
    // Input handling
    // ------------------------------------------------------------------

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

    // ------------------------------------------------------------------
    // Star field <-> detail transition (in-place, same screen object)
    // ------------------------------------------------------------------

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

        NodeProgress progress = menu.getCachedProgress(detailNodeId);
        lastKnownProgress = progress;
        ResearchActionPayload.Action action = actionForProgress(progress, lookupDefinition(detailNodeId));

        int x = leftPos, y = topPos;

        backButton = Button.builder(Component.literal("< Back"), btn -> closeDetail())
                .bounds(x + 8, y + 8, 50, 16)
                .build();
        addRenderableWidget(backButton);

        if (action != null) {
            actionButton = Button.builder(actionLabel(action), btn -> confirmPurchase(detailNodeId, action))
                    .bounds(x + imageWidth - 90, y + imageHeight - 34, 80, 20)
                    .build();
            addRenderableWidget(actionButton);
        }
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

    private void confirmPurchase(ResourceLocation nodeId, ResearchActionPayload.Action action) {
        PacketDistributor.sendToServer(new ResearchActionPayload(nodeId, action));
        // Stay on the detail view — render() will notice the progress change once the
        // server->client sync lands and rebuild the widgets/text automatically.
    }

    /**
     * Which purchase action (if any) applies at this node's current stage.
     * Bespoke nodes stop needing a purchase after REVEALED — there's no shared
     * "buy ingredients" step for them, their path continues via the chain document instead.
     */
    private static ResearchActionPayload.Action actionForProgress(NodeProgress progress, ResearchNodeDefinition def) {
        boolean bespoke = def != null && def.bespoke();
        return switch (progress) {
            case LOCKED -> ResearchActionPayload.Action.BUY_SILHOUETTE;
            case SILHOUETTE -> ResearchActionPayload.Action.BUY_REVEAL;
            case REVEALED -> bespoke ? null : ResearchActionPayload.Action.BUY_INGREDIENTS;
            case READY_FOR_SACRIFICE -> null;
        };
    }

    private static Component actionLabel(ResearchActionPayload.Action action) {
        return switch (action) {
            case BUY_SILHOUETTE -> Component.literal("Reveal Silhouette");
            case BUY_REVEAL -> Component.literal("Reveal Identity");
            case BUY_INGREDIENTS -> Component.literal("Reveal Requirements");
            default -> Component.literal("Confirm");
        };
    }

    // ------------------------------------------------------------------
    // Detail-view content assembly (the ???-reveal rules)
    // ------------------------------------------------------------------

    private record DetailSections(
            boolean identityRevealed,
            Component description,
            Component recipeHeader,
            List<Component> recipeLines
    ) {}

    private DetailSections buildDetailSections(ResearchNodeDefinition def, NodeProgress progress) {
        // Stage 1 (LOCKED): everything is "???".
        // Stage 2 (SILHOUETTE): identity gets a black-silhouette sprite; description/recipe stay "???".
        // Stage 3 (REVEALED): sprite goes color, description text is revealed.
        //   - bespoke nodes also reveal their "next steps" placeholder here, since they have no
        //     shared recipe step.
        // Stage 4 (READY_FOR_SACRIFICE, non-bespoke only): recipe section gets the real item list.
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

    // ------------------------------------------------------------------
    // Pluggable tooltip content
    // ------------------------------------------------------------------

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
            lines.add(Component.literal("Invested: " + invested + " / " + def.totalCost()));
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

    // ------------------------------------------------------------------
    // Pluggable sprite source (Cobblemon hook)
    // ------------------------------------------------------------------

    /**
     * Resolves a texture for a species. Implementations should return null (not throw) on
     * any failure so the caller can fall back to FALLBACK_SPRITE — a node with a typo'd
     * speciesId or a missing addon should degrade to the placeholder, not crash the screen.
     */
    public interface PokemonSpriteSource {
        @Nullable
        ResourceLocation spriteFor(ResourceLocation speciesId, boolean silhouette);
    }

    /**
     * TODO — not wired yet. This is the part worth checking against your local Cobblemon
     * source/jar before filling in, since the exact hook differs depending on whether you
     * want a flat 2D sprite (cheap, always available) or the real 3D model silhouette-shaded
     * the way Cobblemon's own Pokédex GUI does it (nicer, more involved).
     *
     * Where to look in the Cobblemon sources you have locally:
     *  - `com.cobblemon.mod.common.api.pokemon.PokemonSpecies` — species lookup by id/name
     *    (`PokemonSpecies.INSTANCE.getByIdentifier(...)` / `getByName(...)`), gives you a
     *    `Species` with `resourceIdentifier` you can key off of.
     *  - The Pokédex GUI's own Kotlin class (search the client package for something like
     *    `PokedexGUI` / `PokedexScreen`) is the best reference for "known vs. unknown" —
     *    it already solves exactly this problem (silhouette-for-unseen, color-for-seen).
     *    Reading how *it* decides what to blit is more reliable than guessing texture paths.
     *  - If it renders a flat sprite: look for a texture path convention under
     *    `assets/cobblemon/textures/...` (species icon/portrait folder) you can build a
     *    ResourceLocation from directly — that's the cheap option and matches this method's
     *    signature as-is.
     *  - If it renders the real 3D model (posed, not a flat png): look at
     *    `com.cobblemon.mod.common.client.render.models.blockbench.repository.PokemonModelRepository`
     *    and however the Pokédex GUI invokes it into a render target/GuiGraphics. That's a
     *    render-to-texture problem, not a "return a ResourceLocation" one — this interface
     *    would need to change shape (e.g. a `renderPortrait(graphics, x, y, size, ...)` method
     *    instead) if you go this route. Worth deciding which look you want before building it out.
     *
     * Until this is filled in, every call returns null and the screen shows FALLBACK_SPRITE,
     * which keeps the detail view fully functional (identity/description/recipe reveal logic
     * doesn't depend on this working).
     */
    private static final class CobblemonSpriteSource implements PokemonSpriteSource {
        @Override
        public @Nullable ResourceLocation spriteFor(ResourceLocation speciesId, boolean silhouette) {
            return null; // TODO
        }
    }

    /** Swap in for testing a different sprite source while keeping Cobblemon lookup around. */
    public void setSpriteSource(PokemonSpriteSource source) {
        this.spriteSource = source;
    }
}