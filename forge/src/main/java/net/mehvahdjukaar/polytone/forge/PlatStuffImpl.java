package net.mehvahdjukaar.polytone.forge;

import net.mehvahdjukaar.polytone.mixins.forge.BlockColorsAccessor;
import net.minecraft.client.color.block.BlockColor;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.world.level.block.Block;
import net.neoforged.fml.ModList;
import net.neoforged.fml.ModLoader;
import net.neoforged.fml.javafmlmod.FMLJavaModLoadingContext;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class PlatStuffImpl {

    public static boolean isModStateValid() {
        return !ModLoader.hasErrors();
    }

    public static void addClientReloadListener(Supplier<PreparableReloadListener> listener, ResourceLocation location) {
        Consumer<RegisterClientReloadListenersEvent> eventConsumer = (event) -> {
            event.registerReloadListener(listener.get());
        };
        FMLJavaModLoadingContext.get().getModEventBus().addListener(eventConsumer);
    }


    public static BlockColor getBlockColor(BlockColors colors, Block block) {
        return ((BlockColorsAccessor) colors).getBlockColors().get(block);
    }


    public static String maybeRemapName(String s) {
        return s;
    }

    @org.jetbrains.annotations.Contract
    public static boolean isModLoaded(String namespace) {
        return ModList.get().isLoaded(namespace);
    }
}
