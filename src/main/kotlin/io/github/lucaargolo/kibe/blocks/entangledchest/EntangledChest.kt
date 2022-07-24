package io.github.lucaargolo.kibe.blocks.entangledchest

import io.github.lucaargolo.kibe.blocks.getEntityType
import io.github.lucaargolo.kibe.items.itemRegistry
import io.github.lucaargolo.kibe.items.miscellaneous.Rune
import io.github.lucaargolo.kibe.utils.BlockScreenHandlerFactory
import net.fabricmc.fabric.api.`object`.builder.v1.block.FabricBlockSettings
import io.github.lucaargolo.kibe.utils.SyncableBlockEntity
import net.minecraft.block.*
import net.minecraft.block.entity.BlockEntity
import net.minecraft.block.entity.BlockEntityTicker
import net.minecraft.block.entity.BlockEntityType
import net.minecraft.entity.mob.ShulkerEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.ItemPlacementContext
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.state.StateManager
import net.minecraft.state.property.Properties
import net.minecraft.util.ActionResult
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.shape.VoxelShape
import net.minecraft.util.shape.VoxelShapes
import net.minecraft.world.BlockView
import net.minecraft.world.World

class EntangledChest: BlockWithEntity(FabricBlockSettings.of(Material.STONE).requiresTool().strength(22.0F, 600.0F)) {

    override fun appendProperties(stateManager: StateManager.Builder<Block?, BlockState?>) {
        stateManager.add(Properties.HORIZONTAL_FACING)
    }

    override fun getPlacementState(ctx: ItemPlacementContext): BlockState? {
        return defaultState.with(Properties.HORIZONTAL_FACING, ctx.playerFacing)
    }

    override fun getRenderType(state: BlockState?): BlockRenderType {
        return BlockRenderType.ENTITYBLOCK_ANIMATED
    }

    override fun hasSidedTransparency(state: BlockState?): Boolean {
        return true
    }

    override fun getCollisionShape(state: BlockState, view: BlockView, pos: BlockPos, context: ShapeContext): VoxelShape {
        return createCuboidShape(1.0, 0.0, 1.0, 15.0, 15.0, 15.0)
    }

    override fun getOutlineShape(state: BlockState, view: BlockView, pos: BlockPos?, context: ShapeContext): VoxelShape {
        var isHoldingRune = false
        itemRegistry.forEach { (_, modItem) -> if(modItem.item is Rune && context.isHolding(modItem.item)) isHoldingRune = true }
        if(isHoldingRune) return VoxelShapes.union(getRunesShape(), createCuboidShape(1.0, 0.0, 1.0, 15.0, 15.0, 15.0))
        if(context.isHolding(Items.DIAMOND) || context.isHolding(Items.GOLD_INGOT)) return VoxelShapes.union(
            VoxelShapes.union(
                createCuboidShape(9.0, 14.0, 7.0, 10.0, 16.0, 9.0),
                createCuboidShape(7.0, 14.0, 6.0, 9.0, 16.0, 10.0)
            ),
            createCuboidShape(6.0, 14.0, 7.0, 7.0, 16.0, 9.0)
        )

        return createCuboidShape(1.0, 0.0, 1.0, 15.0, 15.0, 15.0)
    }

    override fun createBlockEntity(blockPos: BlockPos, blockState: BlockState): BlockEntity {
        return EntangledChestEntity(this, blockPos, blockState)
    }

    override fun <T : BlockEntity?> getTicker(world: World?, blockState: BlockState?, blockEntityType: BlockEntityType<T>?): BlockEntityTicker<T>? {
        return checkType(blockEntityType, getEntityType(this)) { wrld, pos, state, blockEntity -> EntangledChestEntity.tick(wrld, pos, state, blockEntity as EntangledChestEntity) }
    }

    override fun onUse(state: BlockState, world: World, pos: BlockPos, player: PlayerEntity, hand: Hand, hit: BlockHitResult): ActionResult {
        val poss = player.raycast(4.5, 1.0F, false).pos
        if((poss.y-pos.y) > 0.9375) {
            if(player.getStackInHand(hand).item is Rune) {
                val int = getRuneByPos((poss.x-pos.x), (poss.z-pos.z), state[Properties.HORIZONTAL_FACING])
                if(int != null) {
                    if(!world.isClient) {
                        val oldColor = (world.getBlockEntity(pos) as EntangledChestEntity).runeColors[int]
                        val newColor = (player.getStackInHand(hand).item as Rune).color
                        if(oldColor != newColor) {
                            (world.getBlockEntity(pos) as EntangledChestEntity).runeColors[int] = newColor
                            (world.getBlockEntity(pos) as EntangledChestEntity).updateColorCode()
                            if(!player.isCreative) {
                                oldColor?.let(Rune::getRuneByColor)?.let {
                                    Block.dropStack(world, pos.up(), ItemStack(it))
                                }
                                player.getStackInHand(hand).decrement(1)
                            }
                        }
                        (world.getBlockEntity(pos) as EntangledChestEntity).markDirty()
                        (world.getBlockEntity(pos) as SyncableBlockEntity).sync()
                    }
                    return ActionResult.CONSUME
                }
            }
            if(player.getStackInHand(hand).item == Items.DIAMOND || player.getStackInHand(hand).item == Items.GOLD_INGOT) {
                val x = poss.x-pos.x
                val z = poss.z-pos.z
                if((x in 0.375..0.4375 && z in 0.4375..0.5625) || (x in 0.4375..0.5625 && z in 0.375..0.625) || (x in 0.5625..0.625 && z in 0.4375..0.5625)) {
                    if(player.getStackInHand(hand).item == Items.DIAMOND && (world.getBlockEntity(pos) as EntangledChestEntity).key == DEFAULT_KEY) {
                        if(!world.isClient) {
                            (world.getBlockEntity(pos) as EntangledChestEntity).owner = player.name.asString()
                            (world.getBlockEntity(pos) as EntangledChestEntity).key = "entangledchest-${player.uuid}"
                            (world.getBlockEntity(pos) as EntangledChestEntity).markDirty()
                            (world.getBlockEntity(pos) as SyncableBlockEntity).sync()
                        }
                        if(!player.isCreative) {
                            Block.dropStack(world, pos.up(), ItemStack(Items.GOLD_INGOT))
                            player.getStackInHand(hand).decrement(1)
                        }
                        return ActionResult.CONSUME
                    }else if(player.getStackInHand(hand).item == Items.GOLD_INGOT && (world.getBlockEntity(pos) as EntangledChestEntity).key != DEFAULT_KEY) {
                        if(!world.isClient) {
                            (world.getBlockEntity(pos) as EntangledChestEntity).owner = ""
                            (world.getBlockEntity(pos) as EntangledChestEntity).key = DEFAULT_KEY
                            (world.getBlockEntity(pos) as EntangledChestEntity).markDirty()
                            (world.getBlockEntity(pos) as SyncableBlockEntity).sync()
                        }
                        if(!player.isCreative) {
                            Block.dropStack(world, pos.up(), ItemStack(Items.DIAMOND))
                            player.getStackInHand(hand).decrement(1)
                        }
                        return ActionResult.CONSUME
                    }
                }
            }
        }
        return if(canOpen(world, pos)) {
            player.openHandledScreen(BlockScreenHandlerFactory(this, pos))
            ActionResult.SUCCESS
        }else{
            ActionResult.FAIL
        }
    }

    override fun hasComparatorOutput(state: BlockState?) = true

    override fun getComparatorOutput(state: BlockState?, world: World, pos: BlockPos): Int {
        return (world.getBlockEntity(pos) as? EntangledChestEntity)?.getComparatorOutput() ?: 0
    }

    @Suppress("DEPRECATION")
    override fun neighborUpdate(state: BlockState, world: World, pos: BlockPos, block: Block, fromPos: BlockPos, notify: Boolean) {
        (world.getBlockEntity(pos) as? EntangledChestEntity)?.let comparatorCheck@{
            if(it.isBeingCompared) {
                world.getBlockState(pos.north()).let { state ->
                    if(state.isOf(Blocks.COMPARATOR) && state[Properties.HORIZONTAL_FACING] == Direction.SOUTH) return@comparatorCheck
                }
                world.getBlockState(pos.south()).let { state ->
                    if(state.isOf(Blocks.COMPARATOR) && state[Properties.HORIZONTAL_FACING] == Direction.NORTH) return@comparatorCheck
                }
                world.getBlockState(pos.east()).let { state ->
                    if(state.isOf(Blocks.COMPARATOR) && state[Properties.HORIZONTAL_FACING] == Direction.WEST) return@comparatorCheck
                }
                world.getBlockState(pos.west()).let { state ->
                    if(state.isOf(Blocks.COMPARATOR) && state[Properties.HORIZONTAL_FACING] == Direction.EAST) return@comparatorCheck
                }
                it.isBeingCompared = false
            }
        }
        super.neighborUpdate(state, world, pos, block, fromPos, notify)
    }

    @Suppress("DEPRECATION")
    override fun onStateReplaced(state: BlockState, world: World, pos: BlockPos, newState: BlockState, moved: Boolean) {
        if (!state.isOf(newState.block)) {
            (world.getBlockEntity(pos) as? EntangledChestEntity)?.let {
                world.updateComparators(pos, this)
            }
            super.onStateReplaced(state, world, pos, newState, moved)
        }
    }

    companion object {
        const val DEFAULT_KEY = "entangledchest-global"

        fun canOpen(world: World?, pos: BlockPos): Boolean {
            val box = ShulkerEntity.calculateBoundingBox(Direction.UP, 0.0f, 0.25f).offset(pos).contract(1.0E-6)
            return world?.isSpaceEmpty(box) ?: true
        }
        fun getRuneByPos(x: Double, z: Double, direction: Direction): Int? {
            val int = when(x) {
                in 0.6875..0.8125 -> {
                    when(z) {
                        in 0.6875..0.8125 -> 1
                        in 0.4375..0.5625 -> 8
                        in 0.1875..0.3125 -> 7
                        else -> null
                    }
                }
                in 0.4375..0.5625 -> {
                    when(z) {
                        in 0.6875..0.8125 -> 2
                        in 0.1875..0.3125 -> 6
                        else -> null
                    }
                }
                in 0.1875..0.3125 -> {
                    when(z) {
                        in 0.6875..0.8125 -> 3
                        in 0.4375..0.5625 -> 4
                        in 0.1875..0.3125 -> 5
                        else -> null
                    }
                }
                else -> null
            }
            return if(int == null) null
            else {
                when(direction) {
                    Direction.SOUTH -> int
                    Direction.EAST -> if (int + 2 > 8) (int+2)-8 else (int+2)
                    Direction.NORTH -> if (int + 4 > 8) (int+4)-8 else (int+4)
                    Direction.WEST -> if (int + 6 > 8) (int+6)-8 else (int+6)
                    else -> null
                }
            }
        }

        fun getRunesShape(): VoxelShape {
            return VoxelShapes.union(
                VoxelShapes.union(
                    VoxelShapes.union(
                        createCuboidShape(11.0, 14.0, 11.0, 13.0, 16.0, 13.0),
                        createCuboidShape(11.0, 14.0, 7.0, 13.0, 16.0, 9.0)
                    ),
                    VoxelShapes.union(
                        createCuboidShape(11.0, 14.0, 3.0, 13.0, 16.0, 5.0),
                        createCuboidShape(7.0, 14.0, 3.0, 9.0, 16.0, 5.0)
                    )
                ),
                VoxelShapes.union(
                    VoxelShapes.union(
                        createCuboidShape(3.0, 14.0, 3.0, 5.0, 16.0, 5.0),
                        createCuboidShape(3.0, 14.0, 7.0, 5.0, 16.0, 9.0)
                    ),
                    VoxelShapes.union(
                        createCuboidShape(3.0, 14.0, 11.0, 5.0, 16.0, 13.0),
                        createCuboidShape(7.0, 14.0, 11.0, 9.0, 16.0, 13.0)
                    )
                )
            )
        }
    }

}