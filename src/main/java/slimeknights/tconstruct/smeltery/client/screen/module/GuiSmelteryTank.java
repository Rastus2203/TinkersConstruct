package slimeknights.tconstruct.smeltery.client.screen.module;

import io.github.fabricators_of_create.porting_lib.fluids.FluidStack;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import slimeknights.mantle.fluid.tooltip.FluidTooltipHandler;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.network.TinkerNetwork;
import slimeknights.tconstruct.library.client.GuiUtil;
import slimeknights.tconstruct.smeltery.block.entity.tank.SmelteryTank;
import slimeknights.tconstruct.smeltery.network.SmelteryFluidClickedPacket;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Helper class to draw the smeltery tank in UIs
 */
public class GuiSmelteryTank {
  // fluid tooltips
  public static final Component TOOLTIP_CAPACITY = TConstruct.makeTranslation("gui", "melting.capacity");
  public static final Component TOOLTIP_AVAILABLE = TConstruct.makeTranslation("gui", "melting.available");
  public static final Component TOOLTIP_USED = TConstruct.makeTranslation("gui", "melting.used");

  private final AbstractContainerScreen<?> parent;
  private final SmelteryTank<?> tank;
  private final int x, y, width, height;
  private final BiConsumer<Long,List<Component>> formatter;

  private int[] liquidHeights;

  public GuiSmelteryTank(AbstractContainerScreen<?> parent, SmelteryTank<?> tank, int x, int y, int width, int height, ResourceLocation tooltipId) {
    this.parent = parent;
    this.tank = tank;
    this.x = x;
    this.y = y;
    this.width = width;
    this.height = height;
    this.formatter = (amount, tooltip) -> FluidTooltipHandler.appendNamedList(tooltipId, amount, tooltip);
  }

  /**
   * Calculates the heights of the liquids
   * @param   refresh  If true, refresh the heights
   * @return  Array of liquid heights at each index
   */
  private int[] calcLiquidHeights(boolean refresh) {
    assert tank != null;
    if (liquidHeights == null || refresh) {
      liquidHeights = calcLiquidHeights(tank.getFluids(), Math.max(tank.getContained(), tank.getCapacity()), height, 3);
    }
    return liquidHeights;
  }

  /**
   * Checks if a position is within the tank
   * @param checkX  X position to check
   * @param checkY  Y position to check
   * @return  True if within the tank
   */
  private boolean withinTank(int checkX, int checkY) {
    return x <= checkX && checkX < (x + width) && y <= checkY && checkY < (y + height);
  }

  /**
   * Renders the smeltery tank
   * @param graphics  Gui Graphics instance
   */
  public void renderFluids(GuiGraphics graphics) {
    // draw liquids
    if (tank.getContained() > 0) {
      int[] heights = calcLiquidHeights(true);

      int bottom = y + width;
      for (int i = 0; i < heights.length; i++) {
        int fluidH = heights[i];
        FluidStack liquid = tank.getFluids().get(i);
        GuiUtil.renderTiledFluid(graphics.pose(), parent, liquid, x, bottom - fluidH, width, fluidH, 100);
        bottom -= fluidH;
      }
    }
  }

  /**
   * Gets the fluid under the mouse at the given Y position relative to the tank bottom
   * @param heights  Fluids heights
   * @param y  Y position to check
   * @return  Fluid index under mouse, or -1 if no fluid
   */
  private int getFluidHovered(int[] heights, int y) {
    for (int i = 0; i < heights.length; i++) {
      if (y < heights[i]) {
        return i;
      }
      y -= heights[i];
    }

    return -1;
  }

  /**
   * Gets the fluid under the mouse at the given Y mouse position
   * @param heights  Fluids heights
   * @param checkY   Mouse Y position
   * @return  Fluid index under mouse, or -1 if no fluid
   */
  private int getFluidFromMouse(int[] heights, int checkY) {
    return getFluidHovered(heights, (y + height) - checkY - 1);
  }

  /**
   * Renders a highlight on the hovered fluid
   * @param graphics  Gui graphics instance
   * @param mouseX    Mouse X
   * @param mouseY    Mouse Y
   */
  public void renderHighlight(GuiGraphics graphics, int mouseX, int mouseY) {
    int checkX = mouseX - parent.leftPos;
    int checkY = mouseY - parent.topPos;
    if (withinTank(checkX, checkY)) {
      if (tank.getContained() == 0) {
        GuiUtil.renderHighlight(graphics, x, y, width, height);
      } else {
        int[] heights = calcLiquidHeights(false);
        int hovered = getFluidFromMouse(heights, checkY);

        // sum all heights below the hovered fluid
        int heightSum = 0;
        int loopMax = hovered == -1 ? heights.length : hovered + 1;
        for (int i = 0; i < loopMax; i++) {
          heightSum += heights[i];
        }
        // render the area
        if (hovered == -1) {
          GuiUtil.renderHighlight(graphics, x, y, width, height - heightSum);
        } else {
          GuiUtil.renderHighlight(graphics, x, (y + height) - heightSum, width, heights[hovered]);
        }
      }
    }
  }

  /**
   * Gets the tooltip for the tank based on the given mouse position
   * @param graphics  Gui graphics instance
   * @param mouseX    Mouse X
   * @param mouseY    Mouse Y
   */
  public void drawTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
    // Liquids
    int checkX = mouseX - parent.leftPos;
    int checkY = mouseY - parent.topPos;
    if (withinTank(checkX, checkY)) {
      int hovered = tank.getContained() == 0 ? -1 : getFluidFromMouse(calcLiquidHeights(false), checkY);
      List<Component> tooltip;
      if (hovered == -1) {
        BiConsumer<Long, List<Component>> formatter = Screen.hasShiftDown() ? FluidTooltipHandler.BUCKET_FORMATTER : this.formatter;

        tooltip = new ArrayList<>();
        tooltip.add(TOOLTIP_CAPACITY);

        formatter.accept(tank.getCapacity(), tooltip);
        long remaining = tank.getRemainingSpace();
        if (remaining > 0) {
          tooltip.add(TOOLTIP_AVAILABLE);
          formatter.accept(remaining, tooltip);
        }
        long used = tank.getContained();
        if (used > 0) {
          tooltip.add(TOOLTIP_USED);
          formatter.accept(used, tooltip);
        }
        FluidTooltipHandler.appendShift(tooltip);
      }
      else {
        tooltip = FluidTooltipHandler.getFluidTooltip(tank.getFluidInTank(hovered));
      }
      graphics.renderComponentTooltip(Screens.getTextRenderer(parent), tooltip, mouseX, mouseY);
    }
  }

  /**
   * Checks if the tank was clicked at the given location
   */
  public void handleClick(int mouseX, int mouseY) {
    if (tank.getContained() > 0 && withinTank(mouseX, mouseY)) {
      int index = getFluidFromMouse(calcLiquidHeights(false), mouseY);
      if (index != -1) {
        TinkerNetwork.getInstance().sendToServer(new SmelteryFluidClickedPacket(index));
      }
    }
  }

  /**
   * Gets the ingredient under the mouse
   * @param checkX  Mouse X position
   * @param checkY  Mouse Y position
   * @return  Ingredient
   */
  @Nullable
  public FluidStack getIngredient(int checkX, int checkY) {
    if (tank.getContained() > 0 && withinTank(checkX, checkY)) {
      int index = getFluidFromMouse(calcLiquidHeights(false), checkY);
      if (index != -1) {
        return tank.getFluidInTank(index);
      }
    }
    return null;
  }


  /* Utils */

  /**
   * Calculate the rendering heights for all the liquids
   *
   * @param liquids  The liquids
   * @param capacity Max capacity of smeltery, to calculate how much height one liquid takes up
   * @param height   Maximum height, basically represents how much height full capacity is
   * @param min      Minimum amount of height for a fluid. A fluid can never have less than this value height returned
   * @return Array with heights corresponding to input-list liquids
   */
  public static int[] calcLiquidHeights(List<FluidStack> liquids, long capacity, int height, int min) {
    int[] fluidHeights = new int[liquids.size()];

    int totalFluidAmount = 0;
    if (liquids.size() > 0) {
      for(int i = 0; i < liquids.size(); i++) {
        FluidStack liquid = liquids.get(i);

        float h = (float) liquid.getAmount() / (float) capacity;
        totalFluidAmount += liquid.getAmount();
        fluidHeights[i] = Math.max(min, (int) Math.ceil(h * (float) height));
      }

      // if not completely full, leave a few pixels for the empty tank display
      if(totalFluidAmount < capacity) {
        height -= min;
      }

      // check if we have enough height to render everything, if not remove pixels from the tallest liquid
      int sum;
      do {
        sum = 0;
        int biggest = -1;
        int m = 0;
        for(int i = 0; i < fluidHeights.length; i++) {
          sum += fluidHeights[i];
          if(fluidHeights[i] > biggest) {
            biggest = fluidHeights[i];
            m = i;
          }
        }

        // we can't get a result without going negative
        if(fluidHeights[m] == 0) {
          break;
        }

        // remove a pixel from the biggest one
        if(sum > height) {
          fluidHeights[m]--;
        }
      } while(sum > height);
    }

    return fluidHeights;
  }
}
