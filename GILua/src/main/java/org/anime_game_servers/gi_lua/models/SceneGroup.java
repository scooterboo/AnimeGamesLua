package org.anime_game_servers.gi_lua.models;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.val;
import org.anime_game_servers.gi_lua.models.scene.block.GroupLifecycle;
import org.anime_game_servers.gi_lua.models.scene.group.SceneMonsterPool;
import org.anime_game_servers.gi_lua.models.scene.group.ScenePoint;
import org.anime_game_servers.gi_lua.utils.GIScriptLoader;
import org.anime_game_servers.lua.engine.LuaScript;
import org.anime_game_servers.lua.models.ScriptType;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

@SuppressWarnings("FieldMayBeFinal")
@ToString
@Getter
public class SceneGroup {
    public transient int block_id; // Not an actual variable in the scripts but we will keep it here for reference

    @Setter
    private int id;
    // from block
    private int refresh_id;
    private int area;
    @Nullable
    private Position pos;
    @Nullable
    private SceneReplaceable is_replaceable;
    private final boolean dynamic_load = false;
    @Nullable
    private SceneBusiness business;
    @Nullable
    private GroupLifecycle life_cycle = GroupLifecycle.FULL_TIME__CYCLE;
    private int activity_revise_level_grow_id;
    private int rely_start_world_level_limit_activity_id;
    private int vision_type;
    private boolean across_block = false;
    private boolean unload_when_disconnect = false;
    private boolean ignore_world_level_revise = false;
    private boolean force_unload_nodelay = false;
    private boolean force_clean_sub_entity = false;
    private boolean is_load_by_vision_type = false;
    private int load_strategy;
    private Set<String> forbid_monster_die; //todo find enum values
    private List<Integer> related_level_tag_series_list;
    private List<Integer> group_tag_list;
    private List<Integer> weathers;

    // from group script
    @Nullable
    private Map<Integer, SceneMonster> monsters; // <ConfigId, Monster>
    @Nullable
    private Map<Integer, SceneNPC> npcs; // <ConfigId, Npc>
    @Nullable
    private Map<Integer, SceneGadget> gadgets; // <ConfigId, Gadgets>
    @Nullable
    private Map<String, SceneTrigger> triggers; // <TriggerName, Trigger>
    @Nullable
    private Map<Integer, SceneRegion> regions;  // <ConfigId, Region>
    @Nullable
    private Map<Integer, ScenePoint> points;  // <ConfigId, ScenePoint>
    @Nullable
    private List<SceneVar> variables;

    @Nullable
    private SceneInitConfig init_config;
    @Nullable
    private List<SceneSuite> suites;

    private List<SceneMonsterPool> monster_pools;
    private List<List<Integer>> sight_groups;

    @Nullable
    private SceneGarbage garbages;


    // internal
    private transient boolean loaded; // Not an actual variable in the scripts either
    private transient LuaScript script;

    public static SceneGroup of(int groupId) {
        var group = new SceneGroup();
        group.id = groupId;
        return group;
    }

    public int getBusinessType() {
        return this.business == null ? 0 : this.business.getType();
    }

    public boolean hasGarbages() {
        return this.garbages != null && !garbages.isEmpty();
    }

    @Nullable
    public List<SceneGadget> getGarbageGadgets() {
        return this.garbages == null ? null : this.garbages.getGadgets();
    }

    public boolean isReplaceable() {
        return this.is_replaceable != null && this.is_replaceable.isValue();
    }

    public SceneSuite getSuiteByIndex(int index) {
        if (index < 1 || index > suites.size()) {
            return null;
        }
        return this.suites.get(index - 1);
    }

    public synchronized SceneGroup load(int sceneId, GIScriptLoader scriptLoader) {
        if (this.loaded) {
            return this;
        }
        // Set flag here so if there is no script, we don't call this function over and over again.
        this.loaded = true;

        val cs = scriptLoader.getSceneScript(sceneId, "scene" + sceneId + "_group" + this.id + ".lua", ScriptType.EXECUTABLE);

        if (cs == null) {
            return this;
        }

        this.script = cs;

        // Eval script
        try {
            cs.evaluate();

            // Set
            this.monsters = cs.getGlobalVariableList("monsters", SceneMonster.class).stream()
                .collect(Collectors.toMap(x -> x.config_id, y -> y, (a, b) -> a));
            this.monsters.values().forEach(m -> m.group = this);

            this.npcs = cs.getGlobalVariableList("npcs", SceneNPC.class).stream()
                .collect(Collectors.toMap(x -> x.config_id, y -> y, (a, b) -> a));
            this.npcs.values().forEach(m -> m.group = this);

            this.gadgets = cs.getGlobalVariableList("gadgets", SceneGadget.class).stream()
                .collect(Collectors.toMap(x -> x.config_id, y -> y, (a, b) -> a));
            this.gadgets.values().forEach(m -> m.group = this);

            this.triggers = cs.getGlobalVariableList("triggers", SceneTrigger.class).stream()
                .collect(Collectors.toMap(SceneTrigger::getName, y -> y, (a, b) -> a));
            this.triggers.values().forEach(t -> t.setCurrentGroup(this));

            this.suites = cs.getGlobalVariableList("suites", SceneSuite.class);
            this.regions = cs.getGlobalVariableList("regions", SceneRegion.class).stream()
                .collect(Collectors.toMap(x -> x.config_id, y -> y, (a, b) -> a));
            this.regions.values().forEach(m -> m.group = this);

            this.init_config = cs.getGlobalVariable("init_config", SceneInitConfig.class);

            // Garbages // TODO: fix properly later
            /*Object garbagesValue = this.bindings.get("garbages");
            if (garbagesValue instanceof LuaValue garbagesTable) {
                this.garbages = new SceneGarbage();
                if (garbagesTable.checktable().get("gadgets") != LuaValue.NIL) {
                    this.garbages.gadgets = ScriptLoader.getSerializer().toList(SceneGadget.class, garbagesTable.checktable().get("gadgets").checktable());
                    this.garbages.gadgets.forEach(m -> m.group = this);
                }
            }*/

            // Add variables to suite
            this.variables = cs.getGlobalVariableList("variables", SceneVar.class);

            // Add monsters and gadgets to suite
            this.suites.forEach(i -> i.init(this));

        } catch (Exception e) {
            //Grasscutter.getLogger().error("An error occurred while loading group " + this.id + " in scene " + sceneId + ".", e);
        }

        //Grasscutter.getLogger().debug("Successfully loaded group {} in scene {}.", this.id, sceneId);
        return this;
    }

    public int findInitSuiteIndex(int exclude_index) { //TODO: Investigate end index
        if (init_config == null) return 1;
        if (init_config.getIo_type() == 1) return init_config.getSuite(); //IO TYPE FLOW
        if (init_config.isRand_suite()) {
            if (suites.size() == 1) {
                return init_config.getSuite();
            } else {
                List<Integer> randSuiteList = new ArrayList<>();
                for (int i = 0; i < suites.size(); i++) {
                    if (i == exclude_index) continue;

                    var suite = suites.get(i);
                    for (int j = 0; j < suite.getRand_weight(); j++) randSuiteList.add(Integer.valueOf(i + 1));
                }
                return randSuiteList.get(new Random().nextInt(randSuiteList.size()));
            }
        }
        return init_config.getSuite();
    }

    public Optional<SceneBossChest> searchBossChestInGroup() {
        return this.gadgets.values().stream().map(g -> g.getBoss_chest()).filter(Objects::nonNull)
            .filter(bossChest -> bossChest.getMonster_config_id() > 0)
            .findFirst();
    }

    /*public List<SceneGroup> getReplaceableGroups(Collection<SceneGroup> loadedGroups) {
        return this.is_replaceable == null ? List.of() :
            Optional.ofNullable(GameData.getGroupReplacements().get(this.id)).stream()
                .map(GroupReplacementData::getReplace_groups)
                .flatMap(List::stream)
                .map(replacementId -> loadedGroups.stream().filter(g -> g.id == replacementId).findFirst())
                .filter(Optional::isPresent).map(Optional::get)
                .filter(replacementGroup -> replacementGroup.is_replaceable != null)
                .filter(replacementGroup -> (replacementGroup.is_replaceable.isValue()
                    && replacementGroup.is_replaceable.getVersion() <= this.is_replaceable.getVersion())
                    || replacementGroup.is_replaceable.isNew_bin_only())
                .toList();
    }*/
}
