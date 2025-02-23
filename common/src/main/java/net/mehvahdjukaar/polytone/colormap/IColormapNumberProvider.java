package net.mehvahdjukaar.polytone.colormap;

import com.mojang.serialization.Codec;
import net.mehvahdjukaar.polytone.biome.BiomeIdMapper;
import net.mehvahdjukaar.polytone.utils.ClientFrameTicker;
import net.mehvahdjukaar.polytone.utils.MapRegistry;
import net.mehvahdjukaar.polytone.utils.ReferenceOrDirectCodec;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import static net.mehvahdjukaar.polytone.utils.ColorUtils.getClimateSettings;

public interface IColormapNumberProvider {

    MapRegistry<IColormapNumberProvider> BUILTIN_PROVIDERS = new MapRegistry<>("Colormap Number Providers");

    Codec<IColormapNumberProvider> CODEC = new ReferenceOrDirectCodec<>(BUILTIN_PROVIDERS,
            ColormapExpressionProvider.CODEC, true);

    float getValue(@Nullable BlockState state, @Nullable BlockPos pos, @Nullable Biome biome,
                   @Nullable BiomeIdMapper mapper, @Nullable ItemStack stack);

    default boolean usesBiome() {
        return true;
    }

    default boolean usesPos() {
        return true;
    }

    default boolean usesState() {
        return true;
    }

    record Const(float c) implements IColormapNumberProvider {

        @Override
        public float getValue(BlockState state, @NotNull BlockPos pos, @Nullable Biome biome,
                              @Nullable BiomeIdMapper mapper, @Nullable ItemStack stack) {
            return c;
        }

        @Override
        public boolean usesState() {
            return false;
        }

        @Override
        public boolean usesBiome() {
            return false;
        }

        @Override
        public boolean usesPos() {
            return false;
        }
    }

    IColormapNumberProvider ZERO = BUILTIN_PROVIDERS.register("zero", new Const(0));
    IColormapNumberProvider ONE = BUILTIN_PROVIDERS.register("one", new Const(1));

    //why inverted. for sunset colormaps
    IColormapNumberProvider DAY_TIME = BUILTIN_PROVIDERS.register("day_time", (state, pos, biome, mapper, stack) ->
            (float) ( 1f-(ClientFrameTicker.getDayTime() % 24000 / 24000f)));


    IColormapNumberProvider TEMPERATURE = BUILTIN_PROVIDERS.register("temperature", new IColormapNumberProvider() {
        @Override
        public float getValue(BlockState state, @NotNull BlockPos pos, @Nullable Biome biome,
                              @Nullable BiomeIdMapper mapper, @Nullable ItemStack stack) {
            return biome == null ? 0 : getClimateSettings(biome).temperature;
        }

        @Override
        public boolean usesState() {
            return false;
        }
    });

    IColormapNumberProvider LEGACY_TEMPERATURE = BUILTIN_PROVIDERS.register("legacy_temperature", new IColormapNumberProvider() {
        @Override
        public float getValue(BlockState state, @NotNull BlockPos pos, @Nullable Biome biome,
                              @Nullable BiomeIdMapper mapper, @Nullable ItemStack stack) {
            return biome == null ? 0 : biome.getTemperature(pos, Minecraft.getInstance().level.getSeaLevel()); // TODO BAD!
        }

        @Override
        public boolean usesState() {
            return false;
        }
    });

    IColormapNumberProvider DOWNFALL = BUILTIN_PROVIDERS.register("downfall", new IColormapNumberProvider() {
        @Override
        public float getValue(BlockState state, @NotNull BlockPos pos, @Nullable Biome biome,
                              @Nullable BiomeIdMapper mapper, @Nullable ItemStack stack) {
            return biome == null ? 0 : getClimateSettings(biome).downfall;
        }

        @Override
        public boolean usesState() {
            return false;
        }
    });

    // grid format
    IColormapNumberProvider BIOME_ID = BUILTIN_PROVIDERS.register("biome_id",
            new IColormapNumberProvider() {
                @Override
                public float getValue(@Nullable BlockState state, @Nullable BlockPos pos, @Nullable Biome biome, @Nullable BiomeIdMapper mapper, @Nullable ItemStack stack) {
                    if (biome == null) return 0;
                    return 1 - mapper.getIndex(biome);
                }

                @Override
                public boolean usesState() {
                    return false;
                }
            }
    );


    IColormapNumberProvider Y_LEVEL = BUILTIN_PROVIDERS.register("y_level", new IColormapNumberProvider() {
        @Override
        public float getValue(@Nullable BlockState state, @Nullable BlockPos pos, @Nullable Biome biome, @Nullable BiomeIdMapper mapper, @Nullable ItemStack stack) {
            if (pos == null) return 64;
            // some builtin variance just because
            // 0-128 RANGE. no clue what that darn mod did but this is good enough. People shouldn't use this anyway
            RandomSource rs = RandomSource.create(pos.hashCode() * pos.asLong());
            float yVariance = 4;
            float v = yVariance * (rs.nextFloat() - 0.5f);
            return 1 - ((pos.getY() + 64 + v) / 256f);
        }

        @Override
        public boolean usesBiome() {
            return false;
        }

        @Override
        public boolean usesState() {
            return false;
        }
    });


    IColormapNumberProvider DAMAGE = BUILTIN_PROVIDERS.register("item_damage", new IColormapNumberProvider() {
        @Override
        public float getValue(@Nullable BlockState state, @Nullable BlockPos pos, @Nullable Biome biome, @Nullable BiomeIdMapper mapper, @Nullable ItemStack stack) {
            if (stack == null) return 0;
            return 1 - stack.getDamageValue() / (float) stack.getMaxDamage();
        }

        @Override
        public boolean usesBiome() {
            return false;
        }

        @Override
        public boolean usesPos() {
            return false;
        }

        @Override
        public boolean usesState() {
            return false;
        }
    });

}