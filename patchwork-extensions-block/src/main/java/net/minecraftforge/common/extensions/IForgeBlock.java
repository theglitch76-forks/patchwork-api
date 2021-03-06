/*
 * Minecraft Forge, Patchwork Project
 * Copyright (c) 2016-2020, 2019-2020
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation version 2.1
 * of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301  USA
 */

package net.minecraftforge.common.extensions;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.ToolType;

import net.minecraft.block.BedBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FarmlandBlock;
import net.minecraft.block.FenceGateBlock;
import net.minecraft.block.GlazedTerracottaBlock;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.Material;
import net.minecraft.block.PlantBlock;
import net.minecraft.block.Stainable;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.enums.BedPart;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnRestriction;
import net.minecraft.entity.ai.pathing.PathNodeType;
import net.minecraft.entity.boss.WitherEntity;
import net.minecraft.entity.boss.dragon.EnderDragonEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.WitherSkullEntity;
import net.minecraft.fluid.FluidState;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.BlockSoundGroup;
import net.minecraft.state.property.Property;
import net.minecraft.tag.BlockTags;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockRenderView;
import net.minecraft.world.BlockView;
import net.minecraft.world.CollisionView;
import net.minecraft.world.IWorld;
import net.minecraft.world.ModifiableWorld;
import net.minecraft.world.World;
import net.minecraft.world.dimension.TheEndDimension;
import net.minecraft.world.explosion.Explosion;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import net.patchworkmc.impl.extensions.block.BlockHarvestManager;
import net.patchworkmc.impl.extensions.block.PatchworkBlock;
import net.patchworkmc.mixin.extensions.block.FireBlockAccessor;
import net.patchworkmc.mixin.extensions.block.PlantBlockAccessor;

public interface IForgeBlock extends PatchworkBlock {
	default Block getBlock() {
		return (Block) this;
	}

	// Asterisks indicate IForgeBlockState calls. All methods can be assumed to be called from IForgeBlockState.
	// Note that some of these methods may be overridden in patches to vanilla blocks, but I can't figure out how to check for that easily. Just, well, check when you implement one.

	// TODO Call locations: Patches: FlyingEntity*, LivingEntity*, BoatEntity*, ExperienceOrbEntity*, ItemEntity*
	/**
	 * Gets the slipperiness at the given location at the given state. Normally
	 * between 0 and 1.
	 *
	 * <p>Note that entities may reduce slipperiness by a certain factor of their own;
	 * for {@link LivingEntity}, this is {@code .91}.
	 * {@link net.minecraft.entity.ItemEntity} uses {@code .98}, and
	 * {@link net.minecraft.entity.projectile.FishingBobberEntity} uses {@code .92}.
	 *
	 * @param state  state of the block
	 * @param world  the world
	 * @param pos    the position in the world
	 * @param entity the entity in question
	 * @return the factor by which the entity's motion should be multiplied
	 */
	float getSlipperiness(BlockState state, CollisionView world, BlockPos pos, @Nullable Entity entity);

	// TODO Call locations: Patches: Block*, BlockModelRenderer*, World*, Chunk*
	/**
	 * Get a light value for this block, taking into account the given state and coordinates, normal ranges are between 0 and 15.
	 *
	 * @param state
	 * @param world
	 * @param pos
	 * @return The light value
	 */
	default int getLightValue(BlockState state, BlockRenderView world, BlockPos pos) {
		return state.getLuminance();
	}

	// TODO Call locations: Forge classes: ForgeHooks* (called in LivingEntity patch)
	/**
	 * Checks if a player or entity can use this block to 'climb' like a ladder.
	 *
	 * @param state  The current state
	 * @param world  The current world
	 * @param pos    Block position in world
	 * @param entity The entity trying to use the ladder, CAN be null.
	 * @return True if the block should act like a ladder
	 */
	default boolean isLadder(BlockState state, CollisionView world, BlockPos pos, LivingEntity entity) {
		return false;
	}

	/**
	 * note: do not bother implementing hooks, deprecated for removal in 1.15
	 * Check if the face of a block should block rendering.
	 *
	 * <p>Faces which are fully opaque should return true, faces with transparency
	 * or faces which do not span the full size of the block should return false.
	 *
	 * @param state The current block state
	 * @param world The current world
	 * @param pos   Block position in world
	 * @param face  The side to check
	 * @return True if the block is opaque on the specified side.
	 * @deprecated This is no longer used for rendering logic.
	 */
	@Deprecated
	default boolean doesSideBlockRendering(BlockState state, BlockRenderView world, BlockPos pos, Direction face) {
		return state.isFullOpaque(world, pos);
	}

	// TODO Call locations: Patches: World*
	/**
	 * Determines if this block should set fire and deal fire damage
	 * to entities coming into contact with it.
	 *
	 * @param state The current block state
	 * @param world The current world
	 * @param pos   Block position in world
	 * @return True if the block should deal damage
	 */
	default boolean isBurning(BlockState state, BlockView world, BlockPos pos) {
		return this == Blocks.FIRE || this == Blocks.LAVA;
	}

	// TODO Call locations: Patches: Block, Block*, PistonBlock*, RepeaterBlock*, WorldRenderer*, ChunkRenderer*, BlockArgumentParser*, FallingBlockEntity*, ChestBlockEntity*, HopperBlockEntity*, Explosion*, World*, WorldChunk*, ChunkRegion*, ChunkHolder*, Forge classes: ForgeHooks, FluidUtil, ForgeHooks*, VanillaInventoryCodeHooks*
	/**
	 * Called throughout the code as a replacement for {@code block instanceof} {@link BlockEntityProvider}.
	 * Allows for blocks to have a block entity conditionally based on block state.
	 *
	 * <p>Return true from this function to specify this block has a block entity.
	 *
	 * @param state State of the current block
	 * @return True if block has a block entity, false otherwise
	 */
	default boolean hasTileEntity(BlockState state) {
		return this instanceof BlockEntityProvider;
	}

	// TODO Call locations: Patches: WorldChunk*, ChunkRegion*
	/**
	 * Called throughout the code as a replacement for {@link BlockEntityProvider#createBlockEntity(BlockView)}
	 * Return the same thing you would from that function.
	 * This will fall back to {@link BlockEntityProvider#createBlockEntity(BlockView)} if this block is a {@link BlockEntityProvider}
	 *
	 * @param state The state of the current block
	 * @param world The world to create the BE in
	 * @return An instance of a class extending {@link BlockEntity}
	 */
	@Nullable
	default BlockEntity createTileEntity(BlockState state, BlockView world) {
		if (getBlock() instanceof BlockEntityProvider) {
			return ((BlockEntityProvider) getBlock()).createBlockEntity(world);
		}

		return null;
	}

	/* TODO IForgeBlock#canHarvestBlock indirectly requires ToolType (via ForgeHooks#canHarvestBlock) */
	/**
	 * Determines if the player can harvest this block, obtaining it's drops when the block is destroyed.
	 *
	 * @param world  The current world
	 * @param pos    The block's current position
	 * @param player The player damaging the block
	 * @return True to spawn the drops
	 */
	default boolean canHarvestBlock(BlockState state, BlockView world, BlockPos pos, PlayerEntity player) {
		return BlockHarvestManager.canHarvestBlock(state, player, world, pos);
	}

	// TODO Call locations: Patches: ServerPlayerInteractionManager*
	/**
	 * Called when a player removes a block.  This is responsible for
	 * actually destroying the block, and the block is intact at time of call.
	 * This is called regardless of whether the player can harvest the block or
	 * not.
	 *
	 * <p>Return true if the block is actually destroyed.
	 *
	 * <p>Note: When used in multiplayer, this is called on both client and
	 * server sides!
	 *
	 * @param state       The current state.
	 * @param world       The current world
	 * @param pos         Block position in world
	 * @param player      The player damaging the block, may be null
	 * @param willHarvest True if {@link Block#onBroken(IWorld, BlockPos, BlockState)} will be called after this if this method returns true.
	 *                    Can be useful to delay the destruction of block entities till after onBroken
	 * @param fluid       The current fluid state at current position
	 * @return True if the block is actually destroyed.
	 */
	default boolean removedByPlayer(BlockState state, World world, BlockPos pos, PlayerEntity player, boolean willHarvest, FluidState fluid) {
		getBlock().onBreak(world, pos, state, player);
		return world.setBlockState(pos, fluid.getBlockState(), world.isClient ? 11 : 3);
	}

	// TODO Call locations: Patches: LivingEntity*, PlayerEntity*, Forge classes: ForgeEventFactory (called from LivingEntity patch)
	/**
	 * Determines if this block is classified as a Bed, Allowing
	 * players to sleep in it, though the block has to specifically
	 * perform the sleeping functionality in it's activated event.
	 *
	 * @param state  The current state
	 * @param world  The current world
	 * @param pos    Block position in world
	 * @param player The player or camera entity, null in some cases.
	 * @return True to treat this as a bed
	 */
	default boolean isBed(BlockState state, BlockView world, BlockPos pos, @Nullable Entity player) {
		return false;
	}

	//TODO Call locations: Patches: SpawnHelper*
	/**
	 * Determines if a specified mob type can spawn on this block, returning false will
	 * prevent any mob from spawning on the block.
	 *
	 * @param state        The current state
	 * @param world        The current world
	 * @param pos          Block position in world
	 * @param restriction  The location spawn restriction
	 * @param entityType   The type of entity attempting to spawn
	 * @return True to allow a mob of the specified category to spawn, false to prevent it.
	 */
	default boolean canCreatureSpawn(BlockState state, BlockView world, BlockPos pos, SpawnRestriction.Location restriction, @Nullable EntityType<?> entityType) {
		return state.allowsSpawning(world, pos, entityType);
	}

	// TODO Call locations: Patches: LivingEntity*, PlayerEntity*
	/**
	 * Returns the position that the sleeper is moved to upon
	 * waking up, or respawning at the bed.
	 *
	 * @param entityType the sleeper's entity type
	 * @param state      The current state
	 * @param world      The current world
	 * @param pos        Block position in world
	 * @param sleeper    The sleeper or camera entity, null in some cases.
	 * @return The spawn position
	 */
	default Optional<Vec3d> getBedSpawnPosition(EntityType<?> entityType, BlockState state, CollisionView world, BlockPos pos, @Nullable LivingEntity sleeper) {
		if (world instanceof World) {
			return BedBlock.findWakeUpPosition(entityType, world, pos, 0);
		}

		return Optional.empty();
	}

	// TODO Call locations: Patches: LivingEntity*
	/**
	 * Called when a user either starts or stops sleeping in the bed.
	 *
	 * @param state    The current state
	 * @param world    The current world
	 * @param pos      Block position in world
	 * @param sleeper  The sleeper or camera entity, null in some cases.
	 * @param occupied True if we are occupying the bed, or false if they are stopping use of the bed
	 */
	default void setBedOccupied(BlockState state, CollisionView world, BlockPos pos, LivingEntity sleeper, boolean occupied) {
		if (world instanceof ModifiableWorld) {
			((ModifiableWorld) world).setBlockState(pos, state.with(BedBlock.OCCUPIED, occupied), 4);
		}
	}

	// TODO Call locations: Patches: LivingEntity*
	/**
	 * Returns the direction of the block. Same values that
	 * are returned by {@link net.minecraft.block.FacingBlock}. Called every frame tick for every living entity. Be VERY fast.
	 *
	 * @param state The current state
	 * @param world The current world
	 * @param pos   Block position in world
	 * @return Bed direction
	 */
	default Direction getBedDirection(BlockState state, CollisionView world, BlockPos pos) {
		return state.get(HorizontalFacingBlock.FACING);
	}

	// This comment is here to note that I didn't miss getting the calls for this method, there just aren't any.
	/**
	 * Determines if the current block is the foot half of the bed.
	 *
	 * @param state The current state
	 * @param world The current world
	 * @param pos   Block position in world
	 * @return True if the current block is the foot side of a bed.
	 */
	default boolean isBedFoot(BlockState state, CollisionView world, BlockPos pos) {
		return state.get(BedBlock.PART) == BedPart.FOOT;
	}

	// This comment is here to note that I didn't miss getting the calls for this method, there just aren't any.
	/**
	 * Called when a leaf should start its decay process.
	 *
	 * @param state The current state
	 * @param world The current world
	 * @param pos   Block position in world
	 */
	default void beginLeaveDecay(BlockState state, CollisionView world, BlockPos pos) {
	}

	// TODO This has 59 calls in patches, which I am not going to list here. Forge classes: FluidAttributes*, ForgeHooks*
	/**
	 * Determines this block should be treated as an air block
	 * by the rest of the code. This method is primarily
	 * useful for creating pure logic-blocks that will be invisible
	 * to the player and otherwise interact as air would.
	 *
	 * @param state The current state
	 * @param world The current world
	 * @param pos   Block position in world
	 * @return True if the block considered air
	 */
	default boolean isAir(BlockState state, BlockView world, BlockPos pos) {
		return state.getMaterial() == Material.AIR;
	}

	// TODO Call locations: Patches: AbstractTreeFeature*, HugeBrownMushroomFeature*, HugeRedMushroomFeature*
	/**
	 * Used during tree growth to determine if newly generated leaves can replace this block.
	 *
	 * @param state The current state
	 * @param world The current world
	 * @param pos   Block position in world
	 * @return true if this block can be replaced by growing leaves.
	 */
	default boolean canBeReplacedByLeaves(BlockState state, CollisionView world, BlockPos pos) {
		return isAir(state, world, pos) || state.matches(BlockTags.LEAVES);
	}

	// TODO Call locations: Patches: AbstractTreeFeature*
	/**
	 * Used during tree growth to determine if newly generated logs can replace this block.
	 *
	 * @param state The current state
	 * @param world The current world
	 * @param pos   Block position in world
	 * @return true if this block can be replaced by growing leaves.
	 */
	default boolean canBeReplacedByLogs(BlockState state, CollisionView world, BlockPos pos) {
		return (isAir(state, world, pos) || state.matches(BlockTags.LEAVES)) || this == Blocks.GRASS_BLOCK || Block.isNaturalDirt(getBlock())
				|| getBlock().matches(BlockTags.LOGS) || getBlock().matches(BlockTags.SAPLINGS) || this == Blocks.VINE;
	}

	// This comment is here to note that I didn't miss getting the calls for this method, there just aren't any.
	/**
	 * Determines if the current block is replaceable by ore veins during world generation.
	 *
	 * @param state  The current state
	 * @param world  The current world
	 * @param pos    Block position in world
	 * @param target The generic target block the gen is looking for, usually stone
	 *               for overworld generation, and netherrack for the nether.
	 * @return True to allow this block to be replaced by a ore
	 */
	default boolean isReplaceableOreGen(BlockState state, CollisionView world, BlockPos pos, Predicate<BlockState> target) {
		return target.test(state);
	}

	// TODO Call locations: Patches: Explosion*
	/**
	 * Location sensitive version of getExplosionResistance.
	 *
	 * @param state     The current state
	 * @param world     The current world
	 * @param pos       Block position in world
	 * @param exploder  The entity that caused the explosion, can be null
	 * @param explosion The explosion
	 * @return The amount of the explosion absorbed.
	 */
	default float getExplosionResistance(BlockState state, CollisionView world, BlockPos pos, @Nullable Entity exploder, Explosion explosion) {
		return this.getBlock().getBlastResistance();
	}

	// TODO Call locations: Patches: RedstoneWireBlock*
	/**
	 * Determine if this block can make a redstone connection on the side provided,
	 * Useful to control which sides are inputs and outputs for redstone wires.
	 *
	 * @param state The current state
	 * @param world The current world
	 * @param pos   Block position in world
	 * @param side  The side that is trying to make the connection, CAN BE NULL
	 * @return True to make the connection
	 */
	default boolean canConnectRedstone(BlockState state, BlockView world, BlockPos pos, @Nullable Direction side) {
		return state.emitsRedstonePower() && side != null;
	}

	// TODO Call locations: Forge classes: ForgeHooks
	/**
	 * Called when a user uses the creative pick block button on this block.
	 *
	 * @param state  The current state
	 * @param target The full target the player is looking at
	 * @param world  The world the block is in
	 * @param pos    The block's position
	 * @param player The player picking the block
	 * @return An {@link ItemStack} to add to the player's inventory, empty itemstack if nothing should be added.
	 */
	default ItemStack getPickBlock(BlockState state, HitResult target, BlockView world, BlockPos pos, PlayerEntity player) {
		return this.getBlock().getPickStack(world, pos, state);
	}

	// No call locations.
	/**
	 * Forge javadoc only said where this was used. It isn't used anywhere, so there's really no way to document this.
	 */
	default boolean isFoliage(BlockState state, CollisionView world, BlockPos pos) {
		return false;
	}

	// TODO Call locations: Patches: LivingEntity*
	/**
	 * Allows a block to override the standard {@link LivingEntity#fall} particles.
	 * This is a server side method that spawns particles with
	 * {@link ServerWorld#spawnParticles}
	 *
	 * @param state1            This block's state.
	 * @param serverworld       The {@link ServerWorld} this block is in.
	 * @param pos               The position of the block.
	 * @param state2            This block's state, but again.
	 * @param entity            The entity that landed on the block
	 * @param numberOfParticles Number of particles the vanilla version of this method would spawn.
	 * @return True to prevent vanilla landing particles from spawning
	 */
	default boolean addLandingEffects(BlockState state1, ServerWorld serverworld, BlockPos pos, BlockState state2, LivingEntity entity, int numberOfParticles) {
		return false;
	}

	// TODO Call locations: Patches: Entity*
	/**
	 * Allows a block to override the standard vanilla running particles.
	 * This is called from {@link Entity#spawnSprintingParticles} and is called both
	 * client and server side, it's up to the implementor to client check / server check.
	 * By default vanilla spawns particles only on the client and the server methods no-op.
	 *
	 * @param state  The state of this block.
	 * @param world  The world.
	 * @param pos    The position at the entity's feet.
	 * @param entity The entity running on the block.
	 * @return True to prevent vanilla running particles from spawning.
	 */
	default boolean addRunningEffects(BlockState state, World world, BlockPos pos, Entity entity) {
		return false;
	}

	// TODO Call locations: Patches: ParticleManager*
	/**
	 * Spawn a digging particle effect in the world, this is a wrapper
	 * around {@link ParticleManager.addBlockBreakParticles} to allow the block more
	 * control over the particles.
	 *
	 * @param state   The current state
	 * @param world   The current world
	 * @param target  The target the player is looking at {x/y/z/side/sub}
	 * @param manager A reference to the current particle manager.
	 * @return True to prevent vanilla digging particles form spawning.
	 */
	@Environment(EnvType.CLIENT)
	default boolean addHitEffects(BlockState state, World worldObj, HitResult target, ParticleManager manager) {
		return false;
	}

	// TODO Call locations: Patches: ParticleManager*
	/**
	 * Spawn particles for when the block is destroyed. Due to the nature
	 * of how this is invoked, the x/y/z locations are not always guaranteed
	 * to host your block. So be sure to do proper sanity checks before assuming
	 * that the location is this block.
	 *
	 * @param state   This block's state
	 * @param world   The current world
	 * @param pos     Position to spawn the particle
	 * @param manager A reference to the current particle manager.
	 * @return True to prevent vanilla break particles from spawning.
	 */
	@Environment(EnvType.CLIENT)
	default boolean addDestroyEffects(BlockState state, World world, BlockPos pos, ParticleManager manager) {
		return false;
	}

	// TODO Call locations: Patches: AbstractTreeFeature*
	/**
	 * Determines if this block can support the passed in plant, allowing it to be planted and grow.
	 * Some examples:
	 * Reeds check if its a reed, or if its sand/dirt/grass and adjacent to water
	 * Cacti checks if its a cacti, or if its sand
	 * Nether types check for soul sand
	 * Crops check for tilled soil
	 * Caves check if it's a solid surface
	 * Plains check if its grass or dirt
	 * Water check if its still water
	 *
	 * @param state     The Current state
	 * @param world     The current world
	 * @param facing    The direction relative to the given position the plant wants to be, typically its UP
	 * @param plantable The plant that wants to check
	 * @return True to allow the plant to be planted/stay.
	 */
	default boolean canSustainPlant(BlockState state, BlockView world, BlockPos pos, Direction facing, IPlantable plantable) {
		BlockState plant = plantable.getPlant(world, pos.offset(facing));

		if (plant.getBlock() == Blocks.CACTUS) {
			return this.getBlock() == Blocks.CACTUS || this.getBlock() == Blocks.SAND || this.getBlock() == Blocks.RED_SAND;
		}

		if (plant.getBlock() == Blocks.SUGAR_CANE && this.getBlock() == Blocks.SUGAR_CANE) {
			return true;
		}

		if (plantable instanceof PlantBlock && ((PlantBlockAccessor) plantable).invokeCanPlantOnTop(state, world, pos)) {
			return true;
		}

		switch (plantable.getPlantType(world, pos)) {
		case Desert:
			return this.getBlock() == Blocks.SAND || this.getBlock() == Blocks.TERRACOTTA || this.getBlock() instanceof GlazedTerracottaBlock;
		case Nether:
			return this.getBlock() == Blocks.SOUL_SAND;
		case Crop:
			return this.getBlock() == Blocks.FARMLAND;
		case Cave:
			return Block.isSideSolidFullSquare(state, world, pos, Direction.UP);
		case Plains:
			return this.getBlock() == Blocks.GRASS_BLOCK || Block.isNaturalDirt(this.getBlock()) || this.getBlock() == Blocks.FARMLAND;
		case Water:
			return state.getMaterial() == Material.WATER;
		case Beach:
			boolean isBeach = this.getBlock() == Blocks.GRASS_BLOCK || Block.isNaturalDirt(this.getBlock()) || this.getBlock() == Blocks.SAND;
			boolean hasWater = (world.getBlockState(pos.east()).getMaterial() == Material.WATER
					|| world.getBlockState(pos.west()).getMaterial() == Material.WATER
					|| world.getBlockState(pos.north()).getMaterial() == Material.WATER
					|| world.getBlockState(pos.south()).getMaterial() == Material.WATER);
			return isBeach && hasWater;
		}

		return false;
	}

	// TODO Call locations: Patches: AbstractTreeFeature*
	/**
	 * Called when a plant grows on this block.
	 * This does not use ForgeDirection, because large/huge trees can be located in non-representable direction,
	 * so the source location is specified.
	 * Currently this just changes the block to dirt if it was grass.
	 *
	 * <p>Note: This happens DURING the generation, the generation may not be complete when this is called.
	 *
	 * @param state  The current state
	 * @param world  Current world
	 * @param pos    Block position in world
	 * @param source Source plant's position in world
	 */
	default void onPlantGrow(BlockState state, IWorld world, BlockPos pos, BlockPos source) {
		if (Block.isNaturalDirt(getBlock())) {
			world.setBlockState(pos, Blocks.DIRT.getDefaultState(), 2);
		}
	}

	/**
	 * Checks if this soil is fertile, typically this means that growth rates
	 * of plants on this soil will be slightly sped up.
	 * Only vanilla case is tilledField when it is within range of water.
	 *
	 * @param state The current state
	 * @param world The current world
	 * @param pos   Block position in world
	 * @return True if the soil should be considered fertile.
	 */
	default boolean isFertile(BlockState state, BlockView world, BlockPos pos) {
		if (this.getBlock() == Blocks.FARMLAND) {
			return state.get(FarmlandBlock.MOISTURE) > 0;
		}

		return false;
	}

	// TODO Call locations: Patches: BeaconBlockEntity*
	/**
	 * Determines if this block can be used as the base of a beacon.
	 *
	 * @param state  The current state
	 * @param world  The current world
	 * @param pos    Block position in world
	 * @param beacon Beacon position in world
	 * @return True, to support the beacon, and make it active with this block.
	 */
	default boolean isBeaconBase(BlockState state, CollisionView world, BlockPos pos, BlockPos beacon) {
		// TODO implement actual tag-based functionality
		return this == Blocks.EMERALD_BLOCK || this == Blocks.GOLD_BLOCK || this == Blocks.DIAMOND_BLOCK || this == Blocks.IRON_BLOCK;
		// return Tags.Blocks.SUPPORTS_BEACON.contains(state.getBlock());
	}

	// TODO Call locations: Forge classes: BreakEvent*
	/**
	 * Gathers how much experience this block drops when broken.
	 * TODO: there's no equivalent callback in Fabric API, so for now Fabric mods should always return 0 here.
	 *
	 * @param state     The current state
	 * @param world     The world
	 * @param pos       Block position
	 * @param fortune   Level of fortune on the breaker's tool
	 * @param silktouch Level of silk touch on the breaker's tool
	 * @return Amount of XP from breaking this block.
	 */
	default int getExpDrop(BlockState state, CollisionView world, BlockPos pos, int fortune, int silktouch) {
		return 0;
	}

	// TODO Call locations: Patches: PistonBlock
	default BlockState rotate(BlockState state, IWorld world, BlockPos pos, BlockRotation direction) {
		return state.rotate(direction);
	}

	// No call locations
	/**
	 * Get the rotations that can apply to the block at the specified coordinates. Null means no rotations are possible.
	 * Note, this is up to the block to decide. It may not be accurate or representative.
	 *
	 * @param state The current state
	 * @param world The world
	 * @param pos   Block position in world
	 * @return An array of valid axes to rotate around, or null for none or unknown
	 */
	@Nullable
	default Direction[] getValidRotations(BlockState state, BlockView world, BlockPos pos) {
		for (Property<?> prop : state.getProperties()) {
			if ((prop.getName().equals("facing") || prop.getName().equals("rotation")) && prop.getType() == Direction.class) {
				@SuppressWarnings("unchecked")
				Collection<Direction> values = ((Collection<Direction>) prop.getValues());
				return values.toArray(new Direction[values.size()]);
			}
		}

		return null;
	}

	// TODO Call locations: Patches: EnchantingTableBlock*, EnchantingTableContainer*
	/**
	 * Determines the amount of enchanting power this block can provide to an enchanting table.
	 *
	 * @param world The world
	 * @param pos   Block position in world
	 * @return The amount of enchanting power this block produces.
	 */
	default float getEnchantPowerBonus(BlockState state, CollisionView world, BlockPos pos) {
		return this.getBlock() == Blocks.BOOKSHELF ? 1 : 0;
	}

	// No call locations.
	/**
	 * Re-colors this block in the world.
	 *
	 * @param state   The current state
	 * @param world   The world
	 * @param pos     Block position
	 * @param facing  ??? (this method has no usages)
	 * @param color   Color to recolor to.
	 * @return if the block was affected
	 */
	@SuppressWarnings("unchecked")
	default boolean recolorBlock(BlockState state, IWorld world, BlockPos pos, Direction facing, DyeColor color) {
		for (Property<?> prop : state.getProperties()) {
			if (prop.getName().equals("color") && prop.getType() == DyeColor.class) {
				DyeColor current = (DyeColor) state.get(prop);

				if (current != color && prop.getValues().contains(color)) {
					world.setBlockState(pos, state.with(((Property<DyeColor>) prop), color), 3);
					return true;
				}
			}
		}

		return false;
	}

	// TODO Call locations: Patches: World*
	/**
	 * Called when a block entity on a side of this block changes is created or is destroyed.
	 *
	 * @param state    The state of this block
	 * @param world    The world
	 * @param pos      Block position in world
	 * @param neighbor Block position of neighbor
	 */
	default void onNeighborChange(BlockState state, CollisionView world, BlockPos pos, BlockPos neighbor) {
	}

	// No call locations.
	/**
	 * Called on an Observer block whenever an update for an Observer is received.
	 *
	 * @param observerState   The Observer block's state.
	 * @param world           The current world.
	 * @param observerPos     The Observer block's position.
	 * @param changedBlock    The updated block.
	 * @param changedBlockPos The updated block's position.
	 */
	default void observedNeighborChange(BlockState observerState, World world, BlockPos observerPos, Block changedBlock, BlockPos changedBlockPos) {
	}

	// TODO Call locations: Patches: World*
	/**
	 * Called to determine whether to allow the a block to handle its own indirect power rather than using the default rules.
	 *
	 * @param state This block's state
	 * @param world The world
	 * @param pos   Block position in world
	 * @param side  The INPUT side of the block to be powered - ie the opposite of this block's output side
	 * @return Whether weak power should be checked normally
	 */
	default boolean shouldCheckWeakPower(BlockState state, CollisionView world, BlockPos pos, Direction side) {
		return state.isSimpleFullBlock(world, pos);
	}

	// TODO Call locations: Patches: World*
	/**
	 * If this block should be notified of weak changes.
	 * Weak changes are changes 1 block away through a solid block.
	 * Similar to comparators.
	 *
	 * @param state The current state
	 * @param world The current world
	 * @param pos   Block position in world
	 * @return true To be notified of changes
	 */
	default boolean getWeakChanges(BlockState state, CollisionView world, BlockPos pos) {
		return false;
	}

	/* TODO IForgeBlock#getHarvestTool needs ToolType */
	/**
	 * Queries the class of tool required to harvest this block, if null is returned
	 * we assume that anything can harvest this block.
	 */
	ToolType getHarvestTool(BlockState state);

	// TODO Call locations: Patches: PickaxeItem*, Forge classes: ForgeHooks*
	/**
	 * Queries the harvest level of this block.
	 *
	 * @return Harvest level, or -1 if tool is not required.
	 */
	int getHarvestLevel(BlockState state);

	/* TODO IForgeBlock#isToolEffective needs ToolType */
	/**
	 * Checks if the specified tool type is efficient on this block,
	 * meaning that it digs at full speed.
	 */
	default boolean isToolEffective(BlockState state, ToolType tool) {
		if (tool == ToolType.PICKAXE && (this.getBlock() == Blocks.REDSTONE_ORE || this.getBlock() == Blocks.REDSTONE_LAMP || this.getBlock() == Blocks.OBSIDIAN)) {
			return false;
		}

		return tool == getHarvestTool(state);
	}

	// TODO Call locations: Forge classes: ForgeHooksClient
	/**
	 * Can return IExtendedBlockState.
	 */
	default BlockState getExtendedState(BlockState state, BlockView world, BlockPos pos) {
		return state;
	}

	// TODO Call locations: Patches: ChunkRenderer*
	/**
	 * Queries if this block should render in a given layer.
	 * A custom {@link net.minecraft.client.render.model.BakedModel} can use {@link net.minecraftforge.client.MinecraftForgeClient#getRenderLayer()} to alter the model based on layer.
	 */
	default boolean canRenderInLayer(BlockState state, RenderLayer layer) {
		return this.getBlock().getRenderLayer() == layer;
	}

	// TODO Call locations: Patches: ClientPlayerInteractionManager*, WorldRenderer*, Entity*, LivingEntity*, FoxEntity*, HorseBaseEntity*, LlamaEntity*, BlockItem*
	/**
	 * Sensitive version of {@link Block#getSoundType}.
	 *
	 * @param state  The state
	 * @param world  The world
	 * @param pos    The position. Note that the world may not necessarily have {@code state} here!
	 * @param entity The entity that is breaking/stepping on/placing/hitting/falling on this block, or null if no entity is in this context
	 * @return A {@link BlockSoundGroup} to use
	 */
	default BlockSoundGroup getSoundType(BlockState state, CollisionView world, BlockPos pos, @Nullable Entity entity) {
		return this.getBlock().getSoundGroup(state);
	}

	// TODO Call locations: Patches: BeaconBlockEntity*
	/**
	 * @param state     The state
	 * @param world     The world
	 * @param pos       The position of this state
	 * @param beaconPos The position of the beacon
	 * @return A float RGB [0.0, 1.0] array to be averaged with a beacon's existing beam color, or null to do nothing to the beam
	 */
	@Nullable
	default float[] getBeaconColorMultiplier(BlockState state, CollisionView world, BlockPos pos, BlockPos beaconPos) {
		if (getBlock() instanceof Stainable) {
			return ((Stainable) getBlock()).getColor().getColorComponents();
		}

		return null;
	}

	// No call locations.
	/**
	 * Use this to change the fog color used when the entity is "inside" a material.
	 * {@link Vec3d} is used here as "r/g/b" 0 - 1 values.
	 *
	 * @param state         The state at the entity viewport.
	 * @param world         The world.
	 * @param pos           The position at the entity viewport.
	 * @param entity        the entity
	 * @param originalColor The current fog color, You are not expected to use this, Return as the default if applicable.
	 * @return The new fog color.
	 */
	@Environment(EnvType.CLIENT)
	default Vec3d getFogColor(BlockState state, CollisionView world, BlockPos pos, Entity entity, Vec3d originalColor, float partialTicks) {
		if (state.getMaterial() == Material.WATER) {
			float visibility = 0.0F;

			if (entity instanceof LivingEntity) {
				LivingEntity ent = (LivingEntity) entity;
				visibility = (float) EnchantmentHelper.getRespiration(ent) * 0.2F;

				if (ent.hasStatusEffect(StatusEffects.WATER_BREATHING)) {
					visibility = visibility * 0.3F + 0.6F;
				}
			}

			return new Vec3d(0.02F + visibility, 0.02F + visibility, 0.2F + visibility);
		} else if (state.getMaterial() == Material.LAVA) {
			return new Vec3d(0.6F, 0.1F, 0.0F);
		}

		return originalColor;
	}

	// TODO Call locations: Patches: Camera*
	/**
	 * Used to determine the state 'viewed' by an entity.
	 * Can be used by fluid blocks to determine if the viewpoint is within the fluid or not.
	 *
	 * @param state     the state
	 * @param world     the world
	 * @param pos       the position
	 * @param viewpoint the viewpoint
	 * @return the block state that should be 'seen'
	 */
	default BlockState getStateAtViewpoint(BlockState state, BlockView world, BlockPos pos, Vec3d viewpoint) {
		return state;
	}

	// No call locations.
	/**
	 * Gets the {@link BlockState} to place.
	 *
	 * @param state  ??? (presumably this block's state, but it has not yet been placed?)
	 * @param facing The side the block is being placed on
	 * @param state2 ???
	 * @param world  The world the block is being placed in
	 * @param pos1   ??? (presumably where it's being placed)
	 * @param pos2   ???
	 * @param hand   The hand the block is being placed from
	 * @return The state to be placed in the world
	 */
	default BlockState getStateForPlacement(BlockState state, Direction facing, BlockState state2, IWorld world, BlockPos pos1, BlockPos pos2, Hand hand) {
		return this.getBlock().getStateForNeighborUpdate(state, facing, state2, world, pos1, pos2);
	}

	// No call locations.
	/**
	 * Determines if another block can connect to this block.
	 *
	 * @param state  This block's state
	 * @param world  The current world
	 * @param pos    The position of this block
	 * @param facing The side the connecting block is on
	 * @return True to allow another block to connect to this block
	 */
	default boolean canBeConnectedTo(BlockState state, BlockView world, BlockPos pos, Direction facing) {
		return false;
	}

	// TODO Call locations: Patches: LandPathNodeMaker, LandPathNodeMaker*
	/**
	 * Get the {@code PathNodeType} for this block. Return {@code null} for vanilla behavior.
	 *
	 * @return the PathNodeType
	 */
	@Nullable
	default PathNodeType getAiPathNodeType(BlockState state, BlockView world, BlockPos pos, @Nullable MobEntity entity) {
		return ((IForgeBlockState) state).isBurning(world, pos) ? PathNodeType.DAMAGE_FIRE : null;
	}

	// TODO Call locations: Patches: PistonBlockEntity, PistonHandler*
	/**
	 * @param state The state
	 * @return true if the block is sticky block which used for pull or push adjacent blocks (use by piston)
	 */
	default boolean isStickyBlock(BlockState state) {
		return state.getBlock() == Blocks.SLIME_BLOCK;
	}

	// TODO Call locations: Patches: FireBlock*
	/**
	 * Chance that fire will spread and consume this block.
	 * 300 being a 100% chance, 0, being a 0% chance.
	 *
	 * @param state The current state
	 * @param world The current world
	 * @param pos   Block position in world
	 * @param face  The face that the fire is coming from
	 * @return A number ranging from 0 to 300 relating used to determine if the block will be consumed by fire
	 */
	default int getFlammability(BlockState state, BlockView world, BlockPos pos, Direction face) {
		return ((FireBlockAccessor) Blocks.FIRE).invokeGetSpreadChance(state);
	}

	// TODO Call locations: Patches: FireBlock*, DispenserBehavior*
	/**
	 * Called when fire is updating, checks if a block face can catch fire.
	 *
	 * @param state The current state
	 * @param world The current world
	 * @param pos   Block position in world
	 * @param face  The face that the fire is coming from
	 * @return True if the face can be on fire, false otherwise.
	 */
	default boolean isFlammable(BlockState state, BlockView world, BlockPos pos, Direction face) {
		return ((IForgeBlockState) state).getFlammability(world, pos, face) > 0;
	}

	// TODO Call locations: Patches: TNTBlock, FireBlock*, DispenserBehavior*
	/**
	 * If the block is flammable, this is called when it gets lit on fire.
	 *
	 * @param state   The current state
	 * @param world   The current world
	 * @param pos     Block position in world
	 * @param face    The face that the fire is coming from
	 * @param igniter The entity that lit the fire
	 */
	default void catchFire(BlockState state, World world, BlockPos pos, @Nullable Direction face, @Nullable LivingEntity igniter) {
	}

	// No call locations.
	/**
	 * Called when fire is updating on a neighbor block.
	 * The higher the number returned, the faster fire will spread around this block.
	 *
	 * @param state The current state
	 * @param world The current world
	 * @param pos   Block position in world
	 * @param face  The face that the fire is coming from
	 * @return A number that is used to determine the speed of fire growth around the block
	 */
	default int getFireSpreadSpeed(BlockState state, BlockView world, BlockPos pos, Direction face) {
		return ((FireBlockAccessor) Blocks.FIRE).invokeGetBurnChance(state);
	}

	// TODO Call locations: Patches: FireBlock*
	/**
	 * Currently only called by fire when it is on top of this block.
	 * Returning true will prevent the fire from naturally dying during updating.
	 * Also prevents firing from dying from rain.
	 *
	 * @param state The current state
	 * @param world The current world
	 * @param pos   Block position in world
	 * @param side  The face that the fire is coming from
	 * @return True if this block sustains fire, meaning it will never go out.
	 */
	default boolean isFireSource(BlockState state, BlockView world, BlockPos pos, Direction side) {
		if (side != Direction.UP) {
			return false;
		}

		if (getBlock() == Blocks.NETHERRACK || getBlock() == Blocks.MAGMA_BLOCK) {
			return true;
		}

		return world instanceof CollisionView && ((CollisionView) world).getDimension() instanceof TheEndDimension && getBlock() == Blocks.BEDROCK;
	}

	// TODO Call locations: Patches: WitherEntity*, WitherSkullEntity*, Forge classes: ForgeHooks*
	/**
	 * Determines if this block is can be destroyed by the specified entities normal behavior.
	 *
	 * @param state The current state
	 * @param world The current world
	 * @param pos   Block position in world
	 * @return True to allow the entity to destroy this block
	 */
	default boolean canEntityDestroy(BlockState state, BlockView world, BlockPos pos, Entity entity) {
		if (entity instanceof EnderDragonEntity) {
			return !BlockTags.DRAGON_IMMUNE.contains(this.getBlock());
		} else if ((entity instanceof WitherEntity) || (entity instanceof WitherSkullEntity)) {
			return ((IForgeBlockState) state).isAir(world, pos) || WitherEntity.canDestroy(state);
		}

		return true;
	}

	// No call locations.
	/**
	 * Ray traces through the blocks collision from start vector to end vector returning a ray trace hit.
	 *
	 * @param state    The current state
	 * @param world    The current world
	 * @param pos      Block position in world
	 * @param start    The start vector
	 * @param end      The end vector
	 * @param original The original result from {@link Block#collisionRayTrace(IBlockState, World, BlockPos, Vec3d, Vec3d)}
	 * @return A result that suits your block
	 */
	@Nullable
	default HitResult getRayTraceResult(BlockState state, World world, BlockPos pos, Vec3d start, Vec3d end, HitResult original) {
		return original;
	}

	// TODO Call locations: Patches: Explosion*
	/**
	 * Determines if this block should drop loot when exploded.
	 */
	default boolean canDropFromExplosion(BlockState state, BlockView world, BlockPos pos, Explosion explosion) {
		return state.getBlock().shouldDropItemsOnExplosion(explosion);
	}

	// Call locations: Patches: DebugHud (no todo because this works fine without it -- this method should never be overridden anyway.)
	/**
	 * Retrieves a list of tags names this is known to be associated with.
	 * This should be used in favor of {@link net.minecraft.tag.TagContainer#getTagsFor}, as this caches the result.
	 */
	Set<Identifier> getTags();

	// TODO Call locations: Patches: Explosion*
	/**
	 * Called when the block is destroyed by an explosion.
	 * Useful for allowing the block to take into account block entities,
	 * state, etc. when exploded, before it is removed.
	 *
	 * @param world     The current world
	 * @param pos       Block position in world
	 * @param explosion The explosion instance affecting the block
	 */
	default void onBlockExploded(BlockState state, World world, BlockPos pos, Explosion explosion) {
		world.setBlockState(pos, Blocks.AIR.getDefaultState(), 3);
		getBlock().onDestroyedByExplosion(world, pos, explosion);
	}

	// TODO Call locations: Patches: Entity*, ServerPlayerEntity*
	/**
	 * Determines if this block's collision box should be treated as though it can extend above its block space.
	 * Use this to replicate fence and wall behavior.
	 */
	default boolean collisionExtendsVertically(BlockState state, BlockView world, BlockPos pos, Entity collidingEntity) {
		return getBlock().matches(BlockTags.FENCES) || getBlock().matches(BlockTags.WALLS) || getBlock() instanceof FenceGateBlock;
	}
}
