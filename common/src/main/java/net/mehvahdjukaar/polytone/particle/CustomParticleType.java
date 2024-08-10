package net.mehvahdjukaar.polytone.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.mehvahdjukaar.polytone.block.BlockContextExpression;
import net.mehvahdjukaar.polytone.colormap.Colormap;
import net.mehvahdjukaar.polytone.colormap.IColorGetter;
import net.mehvahdjukaar.polytone.sound.ParticleSoundEmitter;
import net.mehvahdjukaar.polytone.utils.ColorUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class CustomParticleType implements CustomParticleFactory {

    private static BlockState STATE_HACK = Blocks.AIR.defaultBlockState();

    private final RenderType renderType;
    private final @Nullable Initializer initializer;
    private final @Nullable Ticker ticker;
    private final List<ParticleSoundEmitter> sounds;
    private final List<ParticleParticleEmitter> particles;
    private transient SpriteSet spriteSet;

    private CustomParticleType(RenderType renderType, @Nullable Initializer initializer, @Nullable Ticker ticker,
                               List<ParticleSoundEmitter> sounds, List<ParticleParticleEmitter> particles) {
        this.renderType = renderType;
        this.initializer = initializer;
        this.ticker = ticker;
        this.sounds = sounds;
        this.particles = particles;
    }

    public static final Codec<CustomParticleType> CODEC = RecordCodecBuilder.create(i -> i.group(
            RenderType.CODEC.optionalFieldOf("render_type", RenderType.OPAQUE)
                    .forGetter(CustomParticleType::getRenderType),
            Initializer.CODEC.optionalFieldOf("initializer").forGetter(c -> Optional.ofNullable(c.initializer)),
            Ticker.CODEC.optionalFieldOf("ticker").forGetter(c -> Optional.ofNullable(c.ticker)),
            ParticleSoundEmitter.CODEC.listOf().optionalFieldOf("sound_emitters", List.of()).forGetter(c -> c.sounds),
            ParticleParticleEmitter.CODEC.listOf().optionalFieldOf("particle_emitters", List.of()).forGetter(c -> c.particles)
    ).apply(i, CustomParticleType::new));

    private CustomParticleType(RenderType renderType, Optional<Initializer> initializer,
                               Optional<Ticker> ticker, List<ParticleSoundEmitter> sounds, List<ParticleParticleEmitter> particles) {
        this(renderType, initializer.orElse(null), ticker.orElse(null), sounds, particles);
    }

    public static void setStateHack(BlockState state) {
        STATE_HACK = state;
    }

    private RenderType getRenderType() {
        return renderType;
    }

    @Override
    public Particle createParticle(SimpleParticleType type, ClientLevel world, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed,
                                   @Nullable BlockState state) {
        if (spriteSet != null) {
            return new Instance(world, x, y, z, xSpeed, ySpeed, zSpeed, state, this);
        } else {
            throw new IllegalStateException("Sprite set not set for custom particle type");
        }
    }

    @Override
    public void setSpriteSet(ParticleEngine.MutableSpriteSet mutableSpriteSet) {
        this.spriteSet = mutableSpriteSet;
    }

    public static class Instance extends TextureSheetParticle {

        private final ParticleRenderType renderType;
        private final @Nullable Ticker ticker;
        private final SpriteSet spriteSet;
        private final Habitat habitat;
        private final List<ParticleTickable> tickables;
        private float oQuadSize;
        private double custom;

        protected Instance(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed,
                           @Nullable BlockState state, CustomParticleType type) {
            super(level, x, y, z, xSpeed, ySpeed, zSpeed);
            this.tickables = new ArrayList<>();
            this.tickables.addAll(type.sounds);
            this.tickables.addAll(type.particles);
            //for normal particles since its simple particle types (so that they can be ued in biomes) we can pass extra params
            if (state == null) state = STATE_HACK;

            // remove randomness
            this.x = x;
            this.y = y;
            this.z = z;
            this.xd = xSpeed;
            this.yd = ySpeed;
            this.zd = zSpeed;
            this.renderType = type.renderType.get();
            this.ticker = type.ticker;
            this.spriteSet = type.spriteSet;
            Initializer initializer = type.initializer;
            if (initializer != null) {
                BlockPos pos = BlockPos.containing(x, y, z);
                if (initializer.roll != null) {
                    this.roll = (float) initializer.roll.getValue(level, pos, state);
                }
                if (initializer.size != null) {
                    this.scale((float) initializer.size.getValue(level, pos, state));
                }
                if (initializer.red != null) {
                    this.rCol = (float) initializer.red.getValue(level, pos, state);
                }
                if (initializer.green != null) {
                    this.gCol = (float) initializer.green.getValue(level, pos, state);
                }
                if (initializer.blue != null) {
                    this.bCol = (float) initializer.blue.getValue(level, pos, state);
                }
                if (initializer.alpha != null) {
                    this.alpha = (float) initializer.alpha.getValue(level, pos, state);
                }
                if (initializer.colormap != null) {
                    float[] unpack = ColorUtils.unpack(initializer.colormap.getColor(state, level, pos, 0));
                    this.setColor(unpack[0], unpack[1], unpack[2]);
                }
                if (initializer.lifetime != null) {
                    this.lifetime = (int) Math.max(1, initializer.lifetime.getValue(level, pos, state));
                }
                if (initializer.friction != null) {
                    this.friction = (float) initializer.friction.getValue(level, pos, state);
                }
                if (initializer.custom != null) {
                    initializer.custom.getValue(level, pos, state);
                }
                this.hasPhysics = initializer.hasPhysics;
                this.habitat = initializer.habitat;
            } else {
                this.habitat = Habitat.ANY;
            }
            this.setSpriteFromAge(spriteSet);

            // some people might want this
            if (this.ticker != null && this.ticker.removeIf != null) {
                if (this.ticker.removeIf.getValue(this, level) > 0) {
                    this.remove();
                    this.alpha = 0;
                }
            }
        }

        public double getCustom() {
            return custom;
        }

        @Override
        public void tick() {
            this.setSpriteFromAge(spriteSet);
            super.tick();

            if (this.ticker != null) {
                if (this.ticker.roll != null) {
                    this.oRoll = this.roll;
                    this.roll = (float) this.ticker.roll.getValue(this, level);
                }
                if (this.ticker.size != null) {
                    this.oQuadSize = this.quadSize;
                    this.quadSize = (float) this.ticker.size.getValue(this, level);
                }
                if (this.ticker.red != null) {
                    this.rCol = (float) this.ticker.red.getValue(this, level);
                }
                if (this.ticker.green != null) {
                    this.gCol = (float) this.ticker.green.getValue(this, level);
                }
                if (this.ticker.blue != null) {
                    this.bCol = (float) this.ticker.blue.getValue(this, level);
                }
                if (this.ticker.alpha != null) {
                    this.alpha = (float) this.ticker.alpha.getValue(this, level);
                }
                if (this.ticker.colormap != null) {
                    BlockPos pos = BlockPos.containing(x, y, z);
                    float[] unpack = ColorUtils.unpack(this.ticker.colormap.getColor(null, level, pos, 0));
                    this.setColor(unpack[0], unpack[1], unpack[2]);
                }
                if (this.ticker.x != null) {
                    this.x = this.ticker.x.getValue(this, level);
                }
                if (this.ticker.y != null) {
                    this.y = this.ticker.y.getValue(this, level);
                }
                if (this.ticker.z != null) {
                    this.z = this.ticker.z.getValue(this, level);
                }
                if (this.ticker.dx != null) {
                    this.xd = this.ticker.dx.getValue(this, level);
                }
                if (this.ticker.dy != null) {
                    this.yd = this.ticker.dy.getValue(this, level);
                }
                if (this.ticker.dz != null) {
                    this.zd = this.ticker.dz.getValue(this, level);
                }
                if (this.ticker.custom != null) {
                    this.custom = this.ticker.custom.getValue(this, level);
                }
                if (this.ticker.removeIf != null) {
                    if (this.ticker.removeIf.getValue(this, level) > 0) {
                        this.remove();
                    }
                }
            }
            if (this.x == this.xo && this.y == this.yo && this.z == this.zo && hasPhysics) {
                this.remove();
            }

            //TODO: check for any block collision. also check this on my mods
            if (this.hasPhysics && this.stoppedByCollision) {
                this.remove();
            }

            if (habitat != Habitat.ANY) {
                BlockState state = level.getBlockState(BlockPos.containing(x, y, z));
                if (habitat == Habitat.LIQUID && !state.getFluidState().isEmpty()) {
                    this.remove();
                }
                if (habitat == Habitat.AIR && !state.isAir()) {
                    this.remove();
                }
            }
            if (!this.removed) {
                for (ParticleTickable tickable : this.tickables) {
                    tickable.tick(this, level);
                }
            }
        }

        @Override
        public float getQuadSize(float scaleFactor) {
            return Mth.lerp(scaleFactor, this.oQuadSize, this.quadSize);
        }

        @Override
        public ParticleRenderType getRenderType() {
            return renderType;
        }
    }

    private enum RenderType {
        TERRAIN,
        OPAQUE,
        TRANSLUCENT,
        CUSTOM;

        public static final Codec<RenderType> CODEC = Codec.STRING.xmap(
                a -> valueOf(a.toUpperCase()), e -> e.name().toLowerCase(Locale.ROOT)
        );

        public ParticleRenderType get() {
            return switch (this) {
                case TERRAIN -> ParticleRenderType.TERRAIN_SHEET;
                case TRANSLUCENT -> ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
                case CUSTOM -> ParticleRenderType.CUSTOM;
                default -> ParticleRenderType.PARTICLE_SHEET_OPAQUE;
            };
        }
    }

    private record Ticker(@Nullable ParticleContentExpression x, @Nullable ParticleContentExpression y,
                          @Nullable ParticleContentExpression z,
                          @Nullable ParticleContentExpression dx, @Nullable ParticleContentExpression dy,
                          @Nullable ParticleContentExpression dz,
                          @Nullable ParticleContentExpression size,
                          @Nullable ParticleContentExpression red, @Nullable ParticleContentExpression green,
                          @Nullable ParticleContentExpression blue, @Nullable ParticleContentExpression alpha,
                          @Nullable ParticleContentExpression roll,
                          @Nullable ParticleContentExpression custom,
                          @Nullable ParticleContentExpression removeIf,
                          @Nullable IColorGetter colormap) {

        private static final Codec<Ticker> CODEC = RecordCodecBuilder.create(i -> i.group(
                ParticleContentExpression.CODEC.optionalFieldOf("x").forGetter(p -> Optional.ofNullable(p.x)),
                ParticleContentExpression.CODEC.optionalFieldOf("y").forGetter(p -> Optional.ofNullable(p.y)),
                ParticleContentExpression.CODEC.optionalFieldOf("z").forGetter(p -> Optional.ofNullable(p.z)),
                ParticleContentExpression.CODEC.optionalFieldOf("dx").forGetter(p -> Optional.ofNullable(p.dx)),
                ParticleContentExpression.CODEC.optionalFieldOf("dy").forGetter(p -> Optional.ofNullable(p.dy)),
                ParticleContentExpression.CODEC.optionalFieldOf("dz").forGetter(p -> Optional.ofNullable(p.dz)),
                ParticleContentExpression.CODEC.optionalFieldOf("size").forGetter(p -> Optional.ofNullable(p.size)),
                ParticleContentExpression.CODEC.optionalFieldOf("red").forGetter(p -> Optional.ofNullable(p.red)),
                ParticleContentExpression.CODEC.optionalFieldOf("green").forGetter(p -> Optional.ofNullable(p.green)),
                ParticleContentExpression.CODEC.optionalFieldOf("blue").forGetter(p -> Optional.ofNullable(p.blue)),
                ParticleContentExpression.CODEC.optionalFieldOf("alpha").forGetter(p -> Optional.ofNullable(p.alpha)),
                ParticleContentExpression.CODEC.optionalFieldOf("roll").forGetter(p -> Optional.ofNullable(p.roll)),
                ParticleContentExpression.CODEC.optionalFieldOf("custom").forGetter(p -> Optional.ofNullable(p.custom)),
                ParticleContentExpression.CODEC.optionalFieldOf("remove_condition").forGetter(p -> Optional.ofNullable(p.removeIf)),
                Colormap.CODEC.optionalFieldOf("colormap").forGetter(p -> Optional.ofNullable(p.colormap))
        ).apply(i, Ticker::new));

        private Ticker(Optional<ParticleContentExpression> x, Optional<ParticleContentExpression> y,
                       Optional<ParticleContentExpression> z, Optional<ParticleContentExpression> dx,
                       Optional<ParticleContentExpression> dy, Optional<ParticleContentExpression> dz,
                       Optional<ParticleContentExpression> size, Optional<ParticleContentExpression> red,
                       Optional<ParticleContentExpression> green, Optional<ParticleContentExpression> blue,
                       Optional<ParticleContentExpression> alpha, Optional<ParticleContentExpression> roll,
                       Optional<ParticleContentExpression> custom,
                       Optional<ParticleContentExpression> removeIf,
                       Optional<IColorGetter> colormap) {
            this(x.orElse(null), y.orElse(null),
                    z.orElse(null), dx.orElse(null),
                    dy.orElse(null), dz.orElse(null),
                    size.orElse(null), red.orElse(null),
                    green.orElse(null), blue.orElse(null),
                    alpha.orElse(null), roll.orElse(null),
                    custom.orElse(null), removeIf.orElse(null),
                    colormap.orElse(null));
        }
    }

    public record Initializer(@Nullable BlockContextExpression size,
                              @Nullable BlockContextExpression lifetime,
                              @Nullable BlockContextExpression red,
                              @Nullable BlockContextExpression green,
                              @Nullable BlockContextExpression blue,
                              @Nullable BlockContextExpression alpha,
                              @Nullable BlockContextExpression roll,
                              @Nullable BlockContextExpression friction,
                              @Nullable BlockContextExpression custom,
                              @Nullable IColorGetter colormap,
                              Habitat habitat, boolean hasPhysics) {

        public static final Codec<Initializer> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockContextExpression.CODEC.optionalFieldOf("size").forGetter(p -> Optional.ofNullable(p.size)),
                BlockContextExpression.CODEC.optionalFieldOf("lifetime").forGetter(p -> Optional.ofNullable(p.lifetime)),
                BlockContextExpression.CODEC.optionalFieldOf("red").forGetter(p -> Optional.ofNullable(p.red)),
                BlockContextExpression.CODEC.optionalFieldOf("green").forGetter(p -> Optional.ofNullable(p.green)),
                BlockContextExpression.CODEC.optionalFieldOf("blue").forGetter(p -> Optional.ofNullable(p.blue)),
                BlockContextExpression.CODEC.optionalFieldOf("alpha").forGetter(p -> Optional.ofNullable(p.alpha)),
                BlockContextExpression.CODEC.optionalFieldOf("roll").forGetter(p -> Optional.ofNullable(p.roll)),
                BlockContextExpression.CODEC.optionalFieldOf("friction").forGetter(p -> Optional.ofNullable(p.friction)),
                BlockContextExpression.CODEC.optionalFieldOf("custom").forGetter(p -> Optional.ofNullable(p.custom)),
                Colormap.CODEC.optionalFieldOf("colormap").forGetter(p -> Optional.ofNullable(p.colormap)),
                Habitat.CODEC.optionalFieldOf("habitat", Habitat.ANY).forGetter(p -> p.habitat),
                Codec.BOOL.optionalFieldOf("has_physics", true).forGetter(p -> p.hasPhysics)
        ).apply(i, Initializer::new));

        private Initializer(Optional<BlockContextExpression> size, Optional<BlockContextExpression> lifetime,
                            Optional<BlockContextExpression> red, Optional<BlockContextExpression> green,
                            Optional<BlockContextExpression> blue, Optional<BlockContextExpression> alpha,
                            Optional<BlockContextExpression> roll,
                            Optional<BlockContextExpression> friction,
                            Optional<BlockContextExpression> custom,
                            Optional<IColorGetter> colormap, Habitat habitat, boolean hasPhysics) {
            this(size.orElse(null), lifetime.orElse(null), red.orElse(null),
                    green.orElse(null), blue.orElse(null), alpha.orElse(null),
                    roll.orElse(null), friction.orElse(null),
                    custom.orElse(null), colormap.orElse(null),
                    habitat, hasPhysics);
        }
    }

    private enum Habitat implements StringRepresentable {
        LIQUID, AIR, ANY;

        private static final Codec<Habitat> CODEC = StringRepresentable.fromEnum(Habitat::values);

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}

