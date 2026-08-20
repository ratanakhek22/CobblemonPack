package com.ratana.cobbleforge.research;

public final class ResearchConstants {
    private ResearchConstants() {}

    /** Flat cost per "research deeper" click, everywhere, regardless of node or stage. */
    public static final int INVESTMENT_INCREMENT = 10;

    /** Points credited toward a rolled node when an Ancient Item finds an eligible target. */
    public static final int ANCIENT_ITEM_DISCOUNT = 10;

    /** Flat points awarded when an Ancient Item's rolled group has no eligible target. */
    public static final int NO_TARGET_FALLBACK_POINTS = 4;

    /** Must match ModResearchScreen.imageWidth exactly -- the two are the same panel, this is
     *  just the copy the menu (which has no Screen reference) needs for its own slot math. */
    public static final int PANEL_WIDTH = 236;

    /** "mr_mime" -> "Mr Mime". Assumes species paths use underscores as word separators. */
    public static String capitalize(String path) {
        String[] parts = path.split("_");
        StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (part.isEmpty()) continue;
            if (!sb.isEmpty()) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.toString();
    }
}