package com.ratana.cobbleforge.research.item;

import com.ratana.cobbleforge.research.node.TypeGroup;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.NotNull;

public class AncientItem extends Item {
    private final TypeGroup group;

    public AncientItem(Properties properties, TypeGroup group) {
        super(properties);
        this.group = group;
    }

    public @NotNull TypeGroup group() {
        return group;
    }
}