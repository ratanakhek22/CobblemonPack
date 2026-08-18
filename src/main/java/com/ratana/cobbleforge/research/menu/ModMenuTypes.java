package com.ratana.cobbleforge.research.menu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES =
            DeferredRegister.create(Registries.MENU, "cobbleforge");

    public static final Supplier<MenuType<ModResearchMenu>> RESEARCH_TABLE =
            MENU_TYPES.register("research_table",
                    () -> new MenuType<>(ModResearchMenu::new, net.minecraft.world.flag.FeatureFlags.DEFAULT_FLAGS));
}