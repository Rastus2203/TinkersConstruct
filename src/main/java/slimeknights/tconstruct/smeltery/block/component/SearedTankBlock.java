package slimeknights.tconstruct.smeltery.block.component;

import lombok.AllArgsConstructor;
import lombok.Getter;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.fluids.FluidStack;
import slimeknights.mantle.util.BlockEntityHelper;
import slimeknights.tconstruct.library.fluid.FluidTransferUtil;
import slimeknights.tconstruct.library.recipe.FluidValues;
import slimeknights.tconstruct.library.utils.NBTTags;
import slimeknights.tconstruct.smeltery.block.entity.ITankTileEntity;
import slimeknights.tconstruct.smeltery.block.entity.component.TankTileEntity;
import slimeknights.tconstruct.smeltery.block.entity.component.TankTileEntity.ITankBlock;

import javax.annotation.Nullable;
import java.util.Locale;

public class SearedTankBlock extends SearedBlock implements ITankBlock, EntityBlock {
  @Getter
  private final int capacity;
  public SearedTankBlock(Properties properties, int capacity) {
    super(properties);
    this.capacity = capacity;
  }

  @Deprecated
  @Override
  public float getShadeBrightness(BlockState state, BlockGetter worldIn, BlockPos pos) {
    return 1.0F;
  }

  @Override
  @Nullable
  public BlockEntity newBlockEntity(BlockPos pPos, BlockState pState) {
    return new TankTileEntity(pPos, pState);
  }

  @Deprecated
  @Override
  public InteractionResult use(BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
    if (FluidTransferUtil.interactWithTank(world, pos, player, hand, hit)) {
      return InteractionResult.SUCCESS;
    }
    return super.use(state, world, pos, player, hand, hit);
  }

  @Override
  public int getLightEmission(BlockState state, BlockGetter world, BlockPos pos) {
    BlockEntity te = world.getBlockEntity(pos);
    if (te instanceof TankTileEntity) {
      FluidStack fluid = ((TankTileEntity) te).getTank().getFluid();
      return fluid.getFluid().getAttributes().getLuminosity(fluid);
    }
    return super.getLightEmission(state, world, pos);
  }

  @Override
  public void setPlacedBy(Level worldIn, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
    CompoundTag nbt = stack.getTag();
    if (nbt != null) {
      BlockEntityHelper.get(TankTileEntity.class, worldIn, pos).ifPresent(te -> te.updateTank(nbt.getCompound(NBTTags.TANK)));
    }
    super.setPlacedBy(worldIn, pos, state, placer, stack);
  }

  @Deprecated
  @Override
  public boolean hasAnalogOutputSignal(BlockState state) {
    return true;
  }

  @Deprecated
  @Override
  public int getAnalogOutputSignal(BlockState blockState, Level worldIn, BlockPos pos) {
    return ITankTileEntity.getComparatorInputOverride(worldIn, pos);
  }

  @Override
  public ItemStack getCloneItemStack(BlockState state, HitResult target, BlockGetter world, BlockPos pos, Player player) {
    ItemStack stack = new ItemStack(this);
    BlockEntityHelper.get(TankTileEntity.class, world, pos).ifPresent(te -> te.setTankTag(stack));
    return stack;
  }

  @AllArgsConstructor
  public enum TankType implements StringRepresentable {
    FUEL_TANK(TankTileEntity.DEFAULT_CAPACITY),
    FUEL_GAUGE(TankTileEntity.DEFAULT_CAPACITY),
    INGOT_TANK(FluidValues.INGOT * 32),
    INGOT_GAUGE(FluidValues.INGOT * 32);

    @Getter
    private final int capacity;

    @Override
    public String getSerializedName() {
      return this.toString().toLowerCase(Locale.US);
    }
  }
}
