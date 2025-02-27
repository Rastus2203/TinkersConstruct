package slimeknights.tconstruct.library.tools.definition;

import com.google.gson.JsonElement;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.level.block.Blocks;
import slimeknights.mantle.lib.util.ToolAction;
import slimeknights.mantle.lib.util.ToolActions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import slimeknights.tconstruct.TConstruct;
import slimeknights.tconstruct.fixture.MaterialItemFixture;
import slimeknights.tconstruct.library.modifiers.ModifierFixture;
import slimeknights.tconstruct.library.tools.SlotType;
import slimeknights.tconstruct.library.tools.definition.aoe.CircleAOEIterator;
import slimeknights.tconstruct.library.tools.definition.aoe.IAreaOfEffectIterator;
import slimeknights.tconstruct.library.tools.definition.harvest.IHarvestLogic;
import slimeknights.tconstruct.library.tools.definition.weapon.IWeaponAttack;
import slimeknights.tconstruct.library.tools.definition.weapon.SweepWeaponAttack;
import slimeknights.tconstruct.library.tools.nbt.IToolStackView;
import slimeknights.tconstruct.library.tools.stat.ToolStats;
import slimeknights.tconstruct.test.BaseMcTest;
import slimeknights.tconstruct.test.BlockHarvestLogic;
import slimeknights.tconstruct.test.JsonFileLoader;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ToolDefinitionLoaderTest extends BaseMcTest {
  private static final ToolDefinitionData WRONG_DATA = ToolDefinitionDataBuilder.builder().stat(ToolStats.DURABILITY, 100).build();
  private static final JsonFileLoader fileLoader = new JsonFileLoader(ToolDefinitionLoader.GSON, ToolDefinitionLoader.FOLDER);
  private static final ToolDefinition NO_PARTS_MINIMAL = ToolDefinition.builder(TConstruct.getResource("minimal_no_parts")).noParts().build();
  private static final ToolDefinition NO_PARTS_FULL = ToolDefinition.builder(TConstruct.getResource("full_no_parts")).noParts().build();
  private static final ToolDefinition MELEE_HARVEST_MINIMAL = ToolDefinition.builder(TConstruct.getResource("minimal_with_parts")).meleeHarvest().build();
  private static final ToolDefinition MELEE_HARVEST_FULL = ToolDefinition.builder(TConstruct.getResource("full_with_parts")).meleeHarvest().build();
  private static final ToolDefinition HAS_PARTS_NO_NEED = ToolDefinition.builder(TConstruct.getResource("has_parts_no_need")).noParts().build();
  private static final ToolDefinition NEED_PARTS_HAS_NONE = ToolDefinition.builder(TConstruct.getResource("need_parts_has_none")).meleeHarvest().build();
  private static final ToolDefinition WRONG_PART_TYPE = ToolDefinition.builder(TConstruct.getResource("wrong_part_type")).meleeHarvest().build();

  @BeforeAll
  static void beforeAll() {
    try {
      IHarvestLogic.LOADER.register(new ResourceLocation("test", "block"), BlockHarvestLogic.LOADER);
      IAreaOfEffectIterator.LOADER.register(new ResourceLocation("test", "circle"), CircleAOEIterator.LOADER);
      IWeaponAttack.LOADER.register(new ResourceLocation("test", "sweep"), SweepWeaponAttack.LOADER);
    } catch (IllegalArgumentException e) {
      // no-op
    }
  }

  /** Helper to do all the stats checks */
  private static void checkFullNonParts(ToolDefinitionData data) {
    // base stats
    assertThat(data.getAllBaseStats()).hasSize(4);
    assertThat(data.getAllBaseStats()).contains(ToolStats.DURABILITY);
    assertThat(data.getAllBaseStats()).contains(ToolStats.ATTACK_DAMAGE);
    assertThat(data.getAllBaseStats()).contains(ToolStats.ATTACK_SPEED);
    assertThat(data.getAllBaseStats()).contains(ToolStats.MINING_SPEED);
    assertThat(data.getBaseStat(ToolStats.DURABILITY)).isEqualTo(100f);
    assertThat(data.getBaseStat(ToolStats.ATTACK_DAMAGE)).isEqualTo(2.5f);
    assertThat(data.getBaseStat(ToolStats.ATTACK_SPEED)).isEqualTo(3.75f);
    assertThat(data.getBaseStat(ToolStats.MINING_SPEED)).isEqualTo(4f);
    // multiplier stats
    assertThat(data.getStats().getMultipliers().getContainedStats()).hasSize(3);
    assertThat(data.getStats().getMultipliers().getContainedStats()).contains(ToolStats.DURABILITY);
    assertThat(data.getStats().getMultipliers().getContainedStats()).contains(ToolStats.ATTACK_DAMAGE);
    assertThat(data.getStats().getMultipliers().getContainedStats()).contains(ToolStats.MINING_SPEED);
    assertThat(data.getMultiplier(ToolStats.DURABILITY)).isEqualTo(1.5f);
    assertThat(data.getMultiplier(ToolStats.ATTACK_DAMAGE)).isEqualTo(2f);
    assertThat(data.getMultiplier(ToolStats.MINING_SPEED)).isEqualTo(0.5f);
    // slots
    assertThat(data.getSlots().containedTypes()).hasSize(3);
    assertThat(data.getSlots().containedTypes()).contains(SlotType.UPGRADE);
    assertThat(data.getSlots().containedTypes()).contains(SlotType.DEFENSE);
    assertThat(data.getSlots().containedTypes()).contains(SlotType.ABILITY);
    assertThat(data.getStartingSlots(SlotType.UPGRADE)).isEqualTo(3);
    assertThat(data.getStartingSlots(SlotType.DEFENSE)).isEqualTo(2);
    assertThat(data.getStartingSlots(SlotType.ABILITY)).isEqualTo(1);
    // traits
    assertThat(data.getTraits()).hasSize(2);
    assertThat(data.getTraits().get(0).getId()).isEqualTo(ModifierFixture.TEST_1);
    assertThat(data.getTraits().get(0).getLevel()).isEqualTo(1);
    assertThat(data.getTraits().get(1).getId()).isEqualTo(ModifierFixture.TEST_2);
    assertThat(data.getTraits().get(1).getLevel()).isEqualTo(3);
    // actions
    assertThat(data.actions).isNotNull();
    assertThat(data.actions).hasSize(2);
    assertThat(data.canPerformAction(ToolActions.AXE_DIG)).isTrue();
    assertThat(data.canPerformAction(ToolAction.get("custom_action"))).isTrue();
    // harvest
    IHarvestLogic harvestLogic = data.getHarvestLogic();
    assertThat(harvestLogic).isInstanceOf(BlockHarvestLogic.class);
    assertThat(harvestLogic.isEffective(mock(IToolStackView.class), Blocks.DIAMOND_BLOCK.defaultBlockState())).isTrue();
    // aoe
    IAreaOfEffectIterator aoe = data.getAOE();
    assertThat(aoe).isInstanceOf(CircleAOEIterator.class);
    assertThat(((CircleAOEIterator)aoe).getDiameter()).isEqualTo(3);
    assertThat(((CircleAOEIterator)aoe).is3D()).isTrue();
    // weapon
    IWeaponAttack attack = data.getAttack();
    assertThat(attack).isInstanceOf(SweepWeaponAttack.class);
    assertThat(((SweepWeaponAttack)attack).getRange()).isEqualTo(5);
  }

  @BeforeAll
  static void setup() {
    SlotType.init();
    MaterialItemFixture.init();
    ModifierFixture.init();
  }

  @Test
  void noParts_minimal() {
    Map<ResourceLocation,JsonElement> splashList = fileLoader.loadFilesAsSplashlist(NO_PARTS_MINIMAL.getId().getPath());
    ToolDefinitionLoader.getInstance().apply(splashList, mock(ResourceManager.class), mock(ProfilerFiller.class));

    ToolDefinitionData data = NO_PARTS_MINIMAL.getData();
    assertThat(data).isNotNull();
    // will not be the empty instance, but will be filled with empty data
    assertThat(data).isNotSameAs(NO_PARTS_MINIMAL.getStatProvider().getDefaultData());
    ToolDefinitionDataTest.checkToolDataEmpty(data);
  }

  @Test
  void noParts_full() {
    Map<ResourceLocation,JsonElement> splashList = fileLoader.loadFilesAsSplashlist(NO_PARTS_FULL.getId().getPath());
    ToolDefinitionLoader.getInstance().apply(splashList, mock(ResourceManager.class), mock(ProfilerFiller.class));

    ToolDefinitionData data = NO_PARTS_FULL.getData();
    assertThat(data).isNotNull();
    assertThat(data).isNotSameAs(NO_PARTS_FULL.getStatProvider().getDefaultData());
    assertThat(data.getParts()).isEmpty();
    checkFullNonParts(data);
  }

  @Test
  void noParts_hasUnneededParts_defaults() {
    HAS_PARTS_NO_NEED.setData(WRONG_DATA); // set to wrong data to ensure something changes
    Map<ResourceLocation,JsonElement> splashList = fileLoader.loadFilesAsSplashlist(HAS_PARTS_NO_NEED.getId().getPath());
    ToolDefinitionLoader.getInstance().apply(splashList, mock(ResourceManager.class), mock(ProfilerFiller.class));
    assertThat(HAS_PARTS_NO_NEED.getData()).isSameAs(HAS_PARTS_NO_NEED.getStatProvider().getDefaultData());
  }

  @Test
  void missingStats_defaults() {
    NO_PARTS_FULL.setData(WRONG_DATA); // set to wrong data to ensure something changes
    Map<ResourceLocation,JsonElement> splashList = fileLoader.loadFilesAsSplashlist(HAS_PARTS_NO_NEED.getId().getPath());
    ToolDefinitionLoader.getInstance().apply(splashList, mock(ResourceManager.class), mock(ProfilerFiller.class));
    assertThat(NO_PARTS_FULL.getData()).isSameAs(NO_PARTS_FULL.getStatProvider().getDefaultData());
  }

  @Test
  void meleeHarvest_minimal() {
    Map<ResourceLocation,JsonElement> splashList = fileLoader.loadFilesAsSplashlist(MELEE_HARVEST_MINIMAL.getId().getPath());
    ToolDefinitionLoader.getInstance().apply(splashList, mock(ResourceManager.class), mock(ProfilerFiller.class));

    ToolDefinitionData data = MELEE_HARVEST_MINIMAL.getData();
    assertThat(data).isNotNull();
    // will not be the empty instance, but will be filled with empty data
    assertThat(data).isNotSameAs(MELEE_HARVEST_MINIMAL.getStatProvider().getDefaultData());
    assertThat(data.getParts()).isNotNull();
    assertThat(data.getParts()).hasSize(1);
    assertThat(data.getParts().get(0).getPart()).isEqualTo(MaterialItemFixture.MATERIAL_ITEM_HEAD);
    assertThat(data.getParts().get(0).getWeight()).isEqualTo(1);
    ToolDefinitionDataTest.checkToolDataNonPartsEmpty(data);
  }

  @Test
  void meleeHarvest_full() {
    Map<ResourceLocation,JsonElement> splashList = fileLoader.loadFilesAsSplashlist(MELEE_HARVEST_FULL.getId().getPath());
    ToolDefinitionLoader.getInstance().apply(splashList, mock(ResourceManager.class), mock(ProfilerFiller.class));

    ToolDefinitionData data = MELEE_HARVEST_FULL.getData();
    assertThat(data).isNotNull();
    assertThat(data).isNotSameAs(MELEE_HARVEST_FULL.getStatProvider().getDefaultData());
    assertThat(data.getParts()).hasSize(3);
    assertThat(data.getParts().get(0).getPart()).isEqualTo(MaterialItemFixture.MATERIAL_ITEM_EXTRA);
    assertThat(data.getParts().get(0).getWeight()).isEqualTo(2);
    assertThat(data.getParts().get(1).getPart()).isEqualTo(MaterialItemFixture.MATERIAL_ITEM_HEAD);
    assertThat(data.getParts().get(1).getWeight()).isEqualTo(1);
    assertThat(data.getParts().get(2).getPart()).isEqualTo(MaterialItemFixture.MATERIAL_ITEM_HANDLE);
    assertThat(data.getParts().get(2).getWeight()).isEqualTo(3);
    checkFullNonParts(data);
  }

  @Test
  void meleeHarvest_missingParts_defaults() {
    NEED_PARTS_HAS_NONE.setData(WRONG_DATA); // set to wrong data to ensure something changes
    Map<ResourceLocation,JsonElement> splashList = fileLoader.loadFilesAsSplashlist(NEED_PARTS_HAS_NONE.getId().getPath());
    ToolDefinitionLoader.getInstance().apply(splashList, mock(ResourceManager.class), mock(ProfilerFiller.class));
    assertThat(NEED_PARTS_HAS_NONE.getData()).isSameAs(NEED_PARTS_HAS_NONE.getStatProvider().getDefaultData());
  }

  @Test
  void meleeHarvest_wrongPartType_defaults() {
    WRONG_PART_TYPE.setData(WRONG_DATA); // set to wrong data to ensure something changes
    Map<ResourceLocation,JsonElement> splashList = fileLoader.loadFilesAsSplashlist(WRONG_PART_TYPE.getId().getPath());
    ToolDefinitionLoader.getInstance().apply(splashList, mock(ResourceManager.class), mock(ProfilerFiller.class));
    assertThat(WRONG_PART_TYPE.getData()).isSameAs(WRONG_PART_TYPE.getStatProvider().getDefaultData());
  }
}
