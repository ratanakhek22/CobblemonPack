package com.ratana.cobbleforge.research.player;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, "cobbleforge");

    public static final Supplier<AttachmentType<ResearchPlayerData>> RESEARCH_DATA =
            ATTACHMENT_TYPES.register("research_data", () ->
                    AttachmentType.builder(ResearchPlayerData::new)
                            .serialize(ResearchPlayerData.CODEC)
                            .build());
}