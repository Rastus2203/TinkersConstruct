package slimeknights.tconstruct.tools;

import com.mojang.blaze3d.platform.InputConstants;
import io.github.fabricators_of_create.porting_lib.event.ModelLoadCallback;
import io.github.fabricators_of_create.porting_lib.event.PlayerTickEvents;
import io.github.fabricators_of_create.porting_lib.model.ModelLoaderRegistry;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.particle.v1.ParticleFactoryRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.client.screenhandler.v1.ScreenRegistry;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.ItemEntityRenderer;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import slimeknights.mantle.data.ISafeManagerReloadListener;
import slimeknights.mantle.data.fabric.IdentifiableISafeManagerReloadListener;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.common.ClientEventBase;
import slimeknights.tconstruct.common.network.TinkerNetwork;
import slimeknights.tconstruct.library.TinkerRegistries;
import slimeknights.tconstruct.library.client.materials.MaterialTooltipCache;
import slimeknights.tconstruct.library.client.model.tools.MaterialModel;
import slimeknights.tconstruct.library.client.model.tools.ToolModel;
import slimeknights.tconstruct.library.client.modifiers.BreakableDyedModifierModel;
import slimeknights.tconstruct.library.client.modifiers.BreakableMaterialModifierModel;
import slimeknights.tconstruct.library.client.modifiers.BreakableModifierModel;
import slimeknights.tconstruct.library.client.modifiers.FluidModifierModel;
import slimeknights.tconstruct.library.client.modifiers.ModifierModelManager;
import slimeknights.tconstruct.library.client.modifiers.ModifierModelManager.ModifierModelRegistrationEvent;
import slimeknights.tconstruct.library.client.modifiers.NormalModifierModel;
import slimeknights.tconstruct.library.client.modifiers.TankModifierModel;
import slimeknights.tconstruct.library.modifiers.Modifier;
import slimeknights.tconstruct.library.utils.HarvestTiers;
import slimeknights.tconstruct.tools.client.ArmorModelHelper;
import slimeknights.tconstruct.tools.client.OverslimeModifierModel;
import slimeknights.tconstruct.tools.client.PlateArmorModel;
import slimeknights.tconstruct.tools.client.SlimelytraArmorModel;
import slimeknights.tconstruct.tools.client.SlimeskullArmorModel;
import slimeknights.tconstruct.tools.client.ToolContainerScreen;
import slimeknights.tconstruct.tools.client.particles.AxeAttackParticle;
import slimeknights.tconstruct.tools.client.particles.HammerAttackParticle;
import slimeknights.tconstruct.tools.logic.InteractionHandler;
import slimeknights.tconstruct.tools.modifiers.ability.armor.DoubleJumpModifier;
import slimeknights.tconstruct.tools.network.TinkerControlPacket;

import static slimeknights.tconstruct.library.client.model.tools.ToolModel.registerItemColors;

@SuppressWarnings("unused")
public class ToolClientEvents extends ClientEventBase {
  /** Keybinding for interacting using a helmet */
  private static final KeyMapping HELMET_INTERACT = new KeyMapping(TConstruct.makeTranslationKey("key", "helmet_interact")/*, KeyConflictContext.IN_GAME*/, InputConstants.getKey("key.keyboard.z").getValue(), "key.categories.tconstruct");
  /** Keybinding for interacting using leggings */
  private static final KeyMapping LEGGINGS_INTERACT = new KeyMapping(TConstruct.makeTranslationKey("key", "leggings_interact")/*, KeyConflictContext.IN_GAME*/, InputConstants.getKey("key.keyboard.i").getValue(), "key.categories.tconstruct");

  /** Listener to clear modifier cache */
  private static final IdentifiableISafeManagerReloadListener MODIFIER_RELOAD_LISTENER = new IdentifiableISafeManagerReloadListener(TConstruct.getResource("modifier_reload_listener")) {
    @Override
    public void onReloadSafe(ResourceManager manager) {
      for (Modifier modifier : TinkerRegistries.MODIFIERS) {
        modifier.clearCache(PackType.CLIENT_RESOURCES);
      }
    }
  };

  static void addResourceListener() {
    ModifierModelManager.init(ResourceManagerHelper.get(PackType.CLIENT_RESOURCES));
    MaterialTooltipCache.init(ResourceManagerHelper.get(PackType.CLIENT_RESOURCES));
    ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(MODIFIER_RELOAD_LISTENER);
    ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(PlateArmorModel.RELOAD_LISTENER);
    ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(SlimeskullArmorModel.RELOAD_LISTENER);
    ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(SlimelytraArmorModel.RELOAD_LISTENER);
    ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(HarvestTiers.RELOAD_LISTENER);
  }

  static void registerModelLoaders(ResourceManager manager, BlockColors colors, ProfilerFiller profiler, int mipLevel) {
    ModelLoaderRegistry.registerLoader(TConstruct.getResource("material"), MaterialModel.LOADER);
    ModelLoaderRegistry.registerLoader(TConstruct.getResource("tool"), ToolModel.LOADER);
  }

  static void registerModifierModels(ModifierModelRegistrationEvent event) {
    event.registerModel(TConstruct.getResource("normal"), NormalModifierModel.UNBAKED_INSTANCE);
    event.registerModel(TConstruct.getResource("breakable"), BreakableModifierModel.UNBAKED_INSTANCE);
    event.registerModel(TConstruct.getResource("overslime"), OverslimeModifierModel.UNBAKED_INSTANCE);
    event.registerModel(TConstruct.getResource("fluid"), FluidModifierModel.UNBAKED_INSTANCE);
    event.registerModel(TConstruct.getResource("tank"), TankModifierModel.UNBAKED_INSTANCE);
    event.registerModel(TConstruct.getResource("breakable_material"), BreakableMaterialModifierModel.UNBAKED_INSTANCE);
    event.registerModel(TConstruct.getResource("breakable_dyed"), BreakableDyedModifierModel.UNBAKED_INSTANCE);
  }

  static void registerRenderers() {
    EntityRendererRegistry.register(TinkerTools.indestructibleItem.get(), ItemEntityRenderer::new);
  }

  public static void clientSetupEvent() {
    PlayerTickEvents.START.register(ToolClientEvents::handleKeyBindings);
    ArmorModelHelper.init();

    // keybinds
    KeyBindingHelper.registerKeyBinding(HELMET_INTERACT);
    KeyBindingHelper.registerKeyBinding(LEGGINGS_INTERACT);

    // screens
    ScreenRegistry.register(TinkerTools.toolContainer.get(), ToolContainerScreen::new);

    registerRenderers();
    registerParticleFactories();
    itemColors();
    addResourceListener();
    ModifierModelRegistrationEvent.EVENT.register(ToolClientEvents::registerModifierModels);
    ModelLoadCallback.EVENT.register(ToolClientEvents::registerModelLoaders);
  }

  static void registerParticleFactories() {
    ParticleFactoryRegistry.getInstance().register(TinkerTools.hammerAttackParticle.get(), HammerAttackParticle.Factory::new);
    ParticleFactoryRegistry.getInstance().register(TinkerTools.axeAttackParticle.get(), AxeAttackParticle.Factory::new);
  }

  static void itemColors() {

    // tint tool textures for fallback
    // rock
    registerItemColors(TinkerTools.pickaxe);
    registerItemColors(TinkerTools.sledgeHammer);
    registerItemColors(TinkerTools.veinHammer);
    // dirt
    registerItemColors(TinkerTools.mattock);
    registerItemColors(TinkerTools.pickadze);
    registerItemColors(TinkerTools.excavator);
    // wood
    registerItemColors(TinkerTools.handAxe);
    registerItemColors(TinkerTools.broadAxe);
    // scythe
    registerItemColors(TinkerTools.kama);
    registerItemColors(TinkerTools.scythe);
    // weapon
    registerItemColors(TinkerTools.dagger);
    registerItemColors(TinkerTools.sword);
    registerItemColors(TinkerTools.cleaver);
  }

  // values to check if a key was being pressed last tick, safe as a static value as we only care about a single player client side
  /** If true, we were jumping last tick */
  private static boolean wasJumping = false;
  /** If true, we were interacting with helmet last tick */
  private static boolean wasHelmetInteracting = false;
  /** If true, we were interacting with leggings last tick */
  private static boolean wasLeggingsInteracting = false;

  /** Called on player tick to handle keybinding presses */
  private static void handleKeyBindings(Player player) {
    Minecraft minecraft = Minecraft.getInstance();
    if (minecraft.player != null && minecraft.player == player && player.level.isClientSide() && !minecraft.player.isSpectator()) {

      // jumping in mid air for double jump
      // ensure we pressed the key since the last tick, holding should not use all your jumps at once
      boolean isJumping = minecraft.options.keyJump.isDown();
      if (!wasJumping && isJumping) {
        if (DoubleJumpModifier.extraJump(player)) {
          TinkerNetwork.getInstance().sendToServer(TinkerControlPacket.DOUBLE_JUMP);
        }
      }
      wasJumping = isJumping;

      // helmet interaction
      boolean isHelmetInteracting = HELMET_INTERACT.isDown();
      if (!wasHelmetInteracting && isHelmetInteracting) {
        if (InteractionHandler.startArmorInteract(player, EquipmentSlot.HEAD)) {
          TinkerNetwork.getInstance().sendToServer(TinkerControlPacket.START_HELMET_INTERACT);
        }
      }
      if (wasHelmetInteracting && !isHelmetInteracting) {
        if (InteractionHandler.stopArmorInteract(player, EquipmentSlot.HEAD)) {
          TinkerNetwork.getInstance().sendToServer(TinkerControlPacket.STOP_HELMET_INTERACT);
        }
      }

      // leggings interaction
      boolean isLeggingsInteract = LEGGINGS_INTERACT.isDown();
      if (!wasLeggingsInteracting && isLeggingsInteract) {
        if (InteractionHandler.startArmorInteract(player, EquipmentSlot.LEGS)) {
          TinkerNetwork.getInstance().sendToServer(TinkerControlPacket.START_LEGGINGS_INTERACT);
        }
      }
      if (wasLeggingsInteracting && !isLeggingsInteract) {
        if (InteractionHandler.stopArmorInteract(player, EquipmentSlot.LEGS)) {
          TinkerNetwork.getInstance().sendToServer(TinkerControlPacket.STOP_LEGGINGS_INTERACT);
        }
      }

      wasHelmetInteracting = isHelmetInteracting;
      wasLeggingsInteracting = isLeggingsInteract;
    }
  }
}
