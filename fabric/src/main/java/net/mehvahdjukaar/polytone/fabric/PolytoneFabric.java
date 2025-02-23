package net.mehvahdjukaar.polytone.fabric;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.CommonLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.mehvahdjukaar.polytone.Polytone;
import net.mehvahdjukaar.polytone.item.IPolytoneItem;
import net.mehvahdjukaar.polytone.slotify.ScreenModifier;
import net.mehvahdjukaar.polytone.slotify.SlotifyScreen;
import net.mehvahdjukaar.polytone.utils.ClientFrameTicker;
import net.minecraft.server.MinecraftServer;

public class PolytoneFabric implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        FabricLoader instance = FabricLoader.getInstance();
        boolean iris = FabricLoader.getInstance().isModLoaded("iris") || FabricLoader.getInstance().isModLoaded("oculus");
        Polytone.init(instance.isDevelopmentEnvironment(), false, iris);

        CommonLifecycleEvents.TAGS_LOADED.register((registries, client) -> {
            if (client) {
                Polytone.onTagsReceived(registries);
            }
        });
        WorldRenderEvents.START.register((context) ->
                ClientFrameTicker.onRenderTick(context.gameRenderer().getMinecraft()));

        ClientTickEvents.START_CLIENT_TICK.register((client) -> {
            if (client.level != null) {
                ClientFrameTicker.onTick(client.level);
            }
        });

        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            if (screen instanceof SlotifyScreen ss) {
                ScreenModifier guiModifier = Polytone.SLOTIFY.getGuiModifier(screen);
                if (guiModifier != null && !guiModifier.extraRenderables().isEmpty()) {
                    ScreenEvents.afterRender(screen).register((screen1, graphics, mouseX, mouseY, tickDelta) -> {

                        var matrices = graphics.pose();
                        matrices.pushPose();
                        matrices.setIdentity();
                        matrices.translate(scaledWidth / 2F, scaledHeight / 2F, 500);

                        ss.polytone$renderExtraSprites(graphics, mouseX, mouseY, tickDelta);
                        matrices.popPose();
                    });
                }
            }
        });

        ItemTooltipCallback.EVENT.register((stack, c, context, lines) -> {
            var modifier = ((IPolytoneItem) stack.getItem()).polytone$getModifier();
            if (modifier != null) {
                modifier.modifyTooltips(lines);
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            currentServer = server;
        });

    }


    public static MinecraftServer currentServer;
}
