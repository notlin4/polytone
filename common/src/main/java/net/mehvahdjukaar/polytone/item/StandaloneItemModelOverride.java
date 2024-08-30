package net.mehvahdjukaar.polytone.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.regex.Pattern;

public class StandaloneItemModelOverride implements ItemModelOverride {


    public static final Codec<StandaloneItemModelOverride> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DataComponentMap.CODEC.fieldOf("components").forGetter(ItemModelOverride::components),
            ResourceLocation.CODEC.fieldOf("model").forGetter(ItemModelOverride::model),
            Codec.INT.optionalFieldOf("stack_count").forGetter(i-> Optional.ofNullable(i.stackCount())),
            ExtraCodecs.PATTERN.optionalFieldOf("name_pattern").forGetter(i -> Optional.ofNullable(i.pattern())),
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(StandaloneItemModelOverride::getTarget)
    ).apply(instance, StandaloneItemModelOverride::new));

    private final DataComponentMap components;
    private final Item item;
    @Nullable
    private final Integer stackCount;
    @Nullable
    private final Pattern pattern;
    private final boolean autoModel;
    private ResourceLocation model;

    public StandaloneItemModelOverride(DataComponentMap components, ResourceLocation model,
                                       Optional<Integer> stackCount, Optional<Pattern> pattern, Item target) {
        this.components = components;
        this.item = target;
        this.model = model;
        this.stackCount = stackCount.orElse(null);
        this.pattern = pattern.orElse(null);
        this.autoModel = model.toString().equals("minecraft:generated");
    }

    // ugly
    public void setModel(ResourceLocation model) {
        this.model = model;
    }

    public Item getTarget() {
        return item;
    }

    public boolean isAutoModel() {
        return autoModel;
    }

    @Override
    public DataComponentMap components() {
        return components;
    }

    @Override
    public ResourceLocation model() {
        return model;
    }

    @Override
    public Integer stackCount() {
        return this.stackCount;
    }

    @Override
    public Pattern pattern() {
        return this.pattern;
    }
}
