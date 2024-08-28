package net.mehvahdjukaar.polytone.particle;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.mehvahdjukaar.polytone.colormap.Colormap;
import net.mehvahdjukaar.polytone.colormap.IColorGetter;
import net.mehvahdjukaar.polytone.sound.ParticleSoundEmitter;
import net.mehvahdjukaar.polytone.utils.ColorUtils;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class CustomParticleType implements CustomParticleFactory {

    private static BlockState STATE_HACK = Blocks.AIR.defaultBlockState();

    private final RenderType renderType;
    private final @Nullable ParticleInitializer initializer;
    private final @Nullable Ticker ticker;
    private final List<ParticleSoundEmitter> sounds;
    private final List<ParticleParticleEmitter> particles;

    private final int lightLevel;
    private final LiquidAffinity liquidAffinity;
    private final boolean hasPhysics;
    private final boolean killOnContact;
    private final @Nullable IColorGetter colormap;

    private transient SpriteSet spriteSet;

    private CustomParticleType(RenderType renderType, int light, boolean hasPhysics, boolean killOnContact,
                               LiquidAffinity liquidAffinity, @Nullable IColorGetter colormap,
                               @Nullable ParticleInitializer initializer, @Nullable Ticker ticker,
                               List<ParticleSoundEmitter> sounds, List<ParticleParticleEmitter> particles) {
        this.renderType = renderType;
        this.initializer = initializer;
        this.ticker = ticker;
        this.sounds = sounds;
        this.particles = particles;
        this.lightLevel = light;
        this.hasPhysics = hasPhysics;
        this.killOnContact = killOnContact;
        this.liquidAffinity = liquidAffinity;
        this.colormap = colormap;
    }

    public static final Codec<CustomParticleType> CODEC = RecordCodecBuilder.create(i -> i.group(
            RenderType.CODEC.optionalFieldOf("render_type", RenderType.OPAQUE)
                    .forGetter(CustomParticleType::getRenderType),
            Codec.intRange(0, 15).optionalFieldOf("light_level", 0).forGetter(c -> c.lightLevel),
            Codec.BOOL.optionalFieldOf("has_physics", true).forGetter(c -> c.hasPhysics),
            Codec.BOOL.optionalFieldOf("kill_on_contact", false).forGetter(c -> c.killOnContact),
            LiquidAffinity.CODEC.optionalFieldOf("liquid_affinity", LiquidAffinity.ANY).forGetter(c -> c.liquidAffinity),
            Colormap.CODEC.optionalFieldOf("colormap").forGetter(c -> Optional.ofNullable(c.colormap)),
            ParticleInitializer.CODEC.optionalFieldOf("initializer").forGetter(c -> Optional.ofNullable(c.initializer)),
            Ticker.CODEC.optionalFieldOf("ticker").forGetter(c -> Optional.ofNullable(c.ticker)),
            ParticleSoundEmitter.CODEC.listOf().optionalFieldOf("sound_emitters", List.of()).forGetter(c -> c.sounds),
            ParticleParticleEmitter.CODEC.listOf().optionalFieldOf("particle_emitters", List.of()).forGetter(c -> c.particles)
    ).apply(i, CustomParticleType::new));

    private CustomParticleType(RenderType renderType, int light, boolean hasPhysics, boolean killOnContact,
                               LiquidAffinity liquidAffinity, Optional<IColorGetter> colormap,
                               Optional<ParticleInitializer> initializer,
                               Optional<Ticker> ticker, List<ParticleSoundEmitter> sounds, List<ParticleParticleEmitter> particles) {
        this(renderType, light, hasPhysics, killOnContact, liquidAffinity, colormap.orElse(null), initializer.orElse(null), ticker.orElse(null), sounds, particles);
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
            // some people might want this

            Instance newParticle = new Instance(world, x, y, z, xSpeed, ySpeed, zSpeed, state, this,
                    BuiltInRegistries.PARTICLE_TYPE.getKey(type));

            if (this.hasPhysics) {
                for (VoxelShape voxelShape : world.getBlockCollisions(null, newParticle.getBoundingBox())) {
                    if (!voxelShape.isEmpty()) {
                        return null;
                    }
                }
            }

            if (this.ticker != null && this.ticker.removeIf != null) {
                if (this.ticker.removeIf.getValue(newParticle, world) > 0) {
                    return null;
                }
            }
            return newParticle;
        } else {
            throw new IllegalStateException("Sprite set not set for custom particle type");
        }
    }

    @Override
    public void setSpriteSet(ParticleEngine.MutableSpriteSet mutableSpriteSet) {
        this.spriteSet = mutableSpriteSet;
    }

    public static class Instance extends TextureSheetParticle {

        protected final ParticleRenderType renderType;
        protected final @Nullable Ticker ticker;
        protected final SpriteSet spriteSet;
        protected final LiquidAffinity liquidAffinity;
        protected final @Nullable IColorGetter colormap;
        protected final List<ParticleTickable> tickables;
        protected final int light;
        protected float oQuadSize;
        protected double custom;
        protected boolean killOnContact;

        private ResourceLocation name;

        protected Instance(ClientLevel level, double x, double y, double z, double xSpeed, double ySpeed, double zSpeed,
                           @Nullable BlockState state, CustomParticleType customType, ResourceLocation typeId) {
            super(level, x, y, z, xSpeed, ySpeed, zSpeed);
            this.setSize(0.1f, 0.1f);
            this.name = typeId;
            this.light = customType.lightLevel;
            this.killOnContact = customType.killOnContact;
            this.colormap = customType.colormap;
            this.tickables = new ArrayList<>();
            this.tickables.addAll(customType.sounds);
            this.tickables.addAll(customType.particles);
            //for normal particles since its simple particle types (so that they can be ued in biomes) we can pass extra params
            if (state == null) state = STATE_HACK;

            // remove randomness
            this.x = x;
            this.y = y;
            this.z = z;
            this.xd = xSpeed;
            this.yd = ySpeed;
            this.zd = zSpeed;
            this.renderType = customType.renderType.get();
            this.ticker = customType.ticker;
            this.spriteSet = customType.spriteSet;
            ParticleInitializer initializer = customType.initializer;
            BlockPos pos = BlockPos.containing(x, y, z);
            if (initializer != null) {
                initializer.initialize(this, level, state, pos);
            }
            this.oQuadSize = quadSize;

            this.liquidAffinity = customType.liquidAffinity;
            this.hasPhysics = customType.hasPhysics;

            if (this.colormap != null) {
                float[] unpack = ColorUtils.unpack(this.colormap.getColor(state, level, pos, 0));
                this.setColor(unpack[0], unpack[1], unpack[2]);
            }

            this.setSpriteFromAge(spriteSet);
        }


        public double getCustom() {
            return custom;
        }

        @Override
        protected int getLightColor(float partialTick) {
            int total = super.getLightColor(partialTick);
            if (this.light > 0) {
                int sky = LightTexture.sky(total);
                int block = LightTexture.block(total);
                block = Math.max(block, light);
                return LightTexture.pack(block, sky);
            }
            return total;
        }

        @Override
        public void tick() {
            this.setSpriteFromAge(spriteSet);
            super.tick();

            if (this.ticker != null) {
                this.ticker.tick(this, level);
            }

            if (this.colormap != null) {
                BlockPos pos = BlockPos.containing(x, y, z);
                float[] unpack = ColorUtils.unpack(this.colormap.getColor(null, level, pos, 0));
                this.setColor(unpack[0], unpack[1], unpack[2]);
            }

            if (this.age > 1 && this.x == this.xo && this.y == this.yo && this.z == this.zo && hasPhysics) {
                this.remove();
            }

            //TODO: check for any block collision. also check this on my mods
            if (this.hasPhysics && this.stoppedByCollision) {
                this.remove();
            }

            if (liquidAffinity != LiquidAffinity.ANY) {
                BlockState state = level.getBlockState(BlockPos.containing(x, y, z));
                if (liquidAffinity == LiquidAffinity.LIQUIDS ^ !state.getFluidState().isEmpty()) {
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
        public void move(double x, double y, double z) {
            super.move(x, y, z);
            if (this.killOnContact && this.age > 1) {
                Vec3 myPos = new Vec3(this.x, this.y, this.z);
                Vec3 wantedPos = new Vec3(this.xo + x, this.yo + y, this.zo + z);
                if (myPos.distanceToSqr(wantedPos) > 0.000001) {
                    // collided with any block. pop. It fragile
                    this.remove();
                    this.xd = 0;
                    this.yd = 0;
                    this.zd = 0;
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

    private enum RenderType implements StringRepresentable {
        TERRAIN,
        OPAQUE,
        TRANSLUCENT,
        LIT,
        INVISIBLE;

        public static final Codec<RenderType> CODEC = StringRepresentable.fromEnum(RenderType::values);

        public ParticleRenderType get() {
            return switch (this) {
                case TERRAIN -> ParticleRenderType.TERRAIN_SHEET;
                case TRANSLUCENT -> ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
                case LIT -> ParticleRenderType.PARTICLE_SHEET_LIT;
                case INVISIBLE -> ParticleRenderType.NO_RENDER;
                default -> ParticleRenderType.PARTICLE_SHEET_OPAQUE;
            };
        }

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    //TODO: merge this and particle modifier
    protected record Ticker(@Nullable ParticleContextExpression x, @Nullable ParticleContextExpression y,
                          @Nullable ParticleContextExpression z,
                          @Nullable ParticleContextExpression dx, @Nullable ParticleContextExpression dy,
                          @Nullable ParticleContextExpression dz,
                          @Nullable ParticleContextExpression size,
                          @Nullable ParticleContextExpression red, @Nullable ParticleContextExpression green,
                          @Nullable ParticleContextExpression blue, @Nullable ParticleContextExpression alpha,
                          @Nullable ParticleContextExpression roll,
                          @Nullable ParticleContextExpression custom,
                          @Nullable ParticleContextExpression removeIf) {

        private static final Codec<Ticker> CODEC = RecordCodecBuilder.create(i -> i.group(
                ParticleContextExpression.CODEC.optionalFieldOf("x").forGetter(p -> Optional.ofNullable(p.x)),
                ParticleContextExpression.CODEC.optionalFieldOf("y").forGetter(p -> Optional.ofNullable(p.y)),
                ParticleContextExpression.CODEC.optionalFieldOf("z").forGetter(p -> Optional.ofNullable(p.z)),
                ParticleContextExpression.CODEC.optionalFieldOf("dx").forGetter(p -> Optional.ofNullable(p.dx)),
                ParticleContextExpression.CODEC.optionalFieldOf("dy").forGetter(p -> Optional.ofNullable(p.dy)),
                ParticleContextExpression.CODEC.optionalFieldOf("dz").forGetter(p -> Optional.ofNullable(p.dz)),
                ParticleContextExpression.CODEC.optionalFieldOf("size").forGetter(p -> Optional.ofNullable(p.size)),
                ParticleContextExpression.CODEC.optionalFieldOf("red").forGetter(p -> Optional.ofNullable(p.red)),
                ParticleContextExpression.CODEC.optionalFieldOf("green").forGetter(p -> Optional.ofNullable(p.green)),
                ParticleContextExpression.CODEC.optionalFieldOf("blue").forGetter(p -> Optional.ofNullable(p.blue)),
                ParticleContextExpression.CODEC.optionalFieldOf("alpha").forGetter(p -> Optional.ofNullable(p.alpha)),
                ParticleContextExpression.CODEC.optionalFieldOf("roll").forGetter(p -> Optional.ofNullable(p.roll)),
                ParticleContextExpression.CODEC.optionalFieldOf("custom").forGetter(p -> Optional.ofNullable(p.custom)),
                ParticleContextExpression.CODEC.optionalFieldOf("remove_condition").forGetter(p -> Optional.ofNullable(p.removeIf))
        ).apply(i, Ticker::new));

        private Ticker(Optional<ParticleContextExpression> x, Optional<ParticleContextExpression> y,
                       Optional<ParticleContextExpression> z, Optional<ParticleContextExpression> dx,
                       Optional<ParticleContextExpression> dy, Optional<ParticleContextExpression> dz,
                       Optional<ParticleContextExpression> size, Optional<ParticleContextExpression> red,
                       Optional<ParticleContextExpression> green, Optional<ParticleContextExpression> blue,
                       Optional<ParticleContextExpression> alpha, Optional<ParticleContextExpression> roll,
                       Optional<ParticleContextExpression> custom,
                       Optional<ParticleContextExpression> removeIf) {
            this(x.orElse(null), y.orElse(null),
                    z.orElse(null), dx.orElse(null),
                    dy.orElse(null), dz.orElse(null),
                    size.orElse(null), red.orElse(null),
                    green.orElse(null), blue.orElse(null),
                    alpha.orElse(null), roll.orElse(null),
                    custom.orElse(null), removeIf.orElse(null)
            );
        }

        private void tick(CustomParticleType.Instance particle, ClientLevel level) {
            if (this.roll != null) {
                particle.oRoll = particle.roll;
                particle.roll = (float) particle.ticker.roll.getValue(particle, level);
            }
            if (this.size != null) {
                particle.oQuadSize = particle.quadSize;
                particle.quadSize = (float) this.size.getValue(particle, level);
            }
            if (this.red != null) {
                particle.rCol = (float) this.red.getValue(particle, level);
            }
            if (this.green != null) {
                particle.gCol = (float) this.green.getValue(particle, level);
            }
            if (this.blue != null) {
                particle.bCol = (float) this.blue.getValue(particle, level);
            }
            if (this.alpha != null) {
                particle.alpha = (float) this.alpha.getValue(particle, level);
            }
            if (this.x != null) {
                particle.x = this.x.getValue(particle, level);
            }
            if (this.y != null) {
                particle.y = this.y.getValue(particle, level);
            }
            if (this.z != null) {
                particle.z = this.z.getValue(particle, level);
            }
            if (this.dx != null) {
                particle.xd = this.dx.getValue(particle, level);
            }
            if (this.dy != null) {
                particle.yd = this.dy.getValue(particle, level);
            }
            if (this.dz != null) {
                particle.zd = this.dz.getValue(particle, level);
            }
            if (this.custom != null) {
                particle.custom = this.custom.getValue(particle, level);
            }
            if (this.removeIf != null) {
                if (this.removeIf.getValue(particle, level) > 0) {
                    particle.remove();
                }
            }
        }

    }

    protected enum LiquidAffinity implements StringRepresentable {
        LIQUIDS, NON_LIQUIDS, ANY;

        private static final Codec<LiquidAffinity> CODEC = StringRepresentable.fromEnum(LiquidAffinity::values);

        @Override
        public String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}

