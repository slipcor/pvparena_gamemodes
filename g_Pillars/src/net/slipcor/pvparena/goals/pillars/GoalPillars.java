package net.slipcor.pvparena.goals.pillars;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaPlayer.Status;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.classes.PABlockLocation;
import net.slipcor.pvparena.classes.PACheck;
import net.slipcor.pvparena.classes.PASpawn;
import net.slipcor.pvparena.commands.CommandTree;
import net.slipcor.pvparena.commands.PAA_Region;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.core.Debug;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.core.StringParser;
import net.slipcor.pvparena.goals.pillars.Pillar.PillarResult;
import net.slipcor.pvparena.loadables.ArenaGoal;
import net.slipcor.pvparena.loadables.ArenaModuleManager;
import net.slipcor.pvparena.managers.SpawnManager;
import net.slipcor.pvparena.managers.StatisticsManager.type;
import net.slipcor.pvparena.managers.TeamManager;
import net.slipcor.pvparena.runnables.EndRunnable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class GoalPillars extends ArenaGoal implements Listener {
    public GoalPillars() {
        super("Pillars");
        debug = new Debug(100);
    }

    private Map<String, String> flagMap;
    private Map<String, ItemStack> headGearMap;
    private Map<String, Pillar> pillarMap;
    private Map<ArenaTeam, Double> scores;

    private String flagName = "";

    private boolean announceTick = true;
    private boolean breakable;
    private boolean onlyFree = true;
    private int tickPoints = 1;
    private int offset;

    private BukkitTask pillarRunner;

    @Override
    public String version() {
        return "v1.3.2.51";
    }

    private static final int PRIORITY = 8;

    @Override
    public boolean allowsJoinInBattle() {
        return arena.getArenaConfig().getBoolean(CFG.PERMS_JOININBATTLE);
    }

    @Override
    public PACheck checkCommand(final PACheck res, final String string) {
        if (res.getPriority() > PRIORITY) {
            return res;
        }

        if ("flageffect".equalsIgnoreCase(string)
                || "touchdown".equalsIgnoreCase(string)
                || string.contains("pillar")) {
            res.setPriority(this, PRIORITY);
        }

        return res;
    }

    @Override
    public List<String> getMain() {
        return Arrays.asList("flageffect", "touchdown", "pillar");
    }

    @Override
    public CommandTree<String> getSubs(final Arena arena) {
        final CommandTree<String> result = new CommandTree<>(null);
        result.define(new String[]{"flageffect", "{PotionEffectType}"});
        result.define(new String[]{"flageffect", "none"});
        return result;
    }

    @Override
    public PACheck checkEnd(final PACheck res) {

        if (res.getPriority() > PRIORITY) {
            return res;
        }

        final int count = TeamManager.countActiveTeams(arena);

        if (count == 1) {
            res.setPriority(this, PRIORITY); // yep. only one team left. go!
        } else if (count == 0) {
            arena.getDebugger().i("No teams playing!");
        }

        return res;
    }

    @Override
    public String checkForMissingSpawns(final Set<String> list) {
        for (final String s : list) {
            if (s.contains("pillar")) {
                return null;
            }
        }
        return "no pillar set";
    }

    /**
     * hook into an interacting player
     *
     * @param res    the PACheck instance
     * @param player the interacting player
     * @param block  the block being clicked
     * @return the PACheck instance
     */
    //@SuppressWarnings("deprecation")
    @Override
    public PACheck checkInteract(final PACheck res, final Player player, final Block block) {
        if (block == null || res.getPriority() > PRIORITY) {
            return res;
        }
        debug.i("checking interact", player);

        ItemStack flagType = StringParser.getItemStackFromString(arena.getArenaConfig().getString(
                CFG.GOAL_FLAGS_FLAGTYPE));

        if (block.getType() != flagType.getType() || (flagType.getData().getData()>0 && flagType.getData().getData() != block.getData())) {
            debug.i("block, but not flag", player);
            return res;
        }
        debug.i("flag click!", player);

        for (final String pillarName : getPillarMap().keySet()) {
            Pillar pillar = getPillarMap().get(pillarName);
            final ArenaTeam owner = pillar.getOwner();
            final ArenaPlayer clicker = ArenaPlayer.parsePlayer(player.getName());
            if (pillar.getLocation().equals(new PABlockLocation(block.getLocation()))) {
                announce(pillarName, owner, clicker, pillar.blockClick(clicker));
                //res.setPriority(this, PRIORITY);
                return res;
            }
        }

        return res;
    }

    /**
     * Announce a pillar change
     *  @param pillarName the pillar name we talk about
     * @param owner      the former owning team
     * @param player     the change causing player
     * @param result     the PillarResult
     */
    private void announce(final String pillarName, final ArenaTeam owner, final ArenaPlayer player,
                          final PillarResult result) {

        if (result == PillarResult.NONE) {
            return;
        }

        final String message = Language.parse(MSG.getByNode("nulang.goal.pillars.msg." + result.name().toLowerCase()),
                pillarName, player.getArenaTeam().colorizePlayer(player.get()) + ChatColor.YELLOW);

        switch (result) {
            case BLOCK_BROKEN:
            case LOWER:
                // owner + player
                arena.tellTeam(owner.getName(), message, owner.getColor(), null);
                arena.msg(player.get(), message);
                break;
            case BLOCK_PLACED:
            case HIGHER:
                // owner
                arena.tellTeam(owner.getName(), message, owner.getColor(), null);
                //arena.msg(player.get(), message);
                break;
            case CLAIMED:
            case UNCLAIMED:
                // fighters
                arena.broadcast(message);
                break;
            default:
                break;

        }
    }

    /*
        private void applyEffects(final Player player) {
            final String value = arena.getArenaConfig().getString(
                    CFG.GOAL_FLAGS_FLAGEFFECT);

            if (value.equalsIgnoreCase("none")) {
                return;
            }

            PotionEffectType pet = null;

            final String[] split = value.split("x");

            int amp = 1;

            if (split.length > 1) {
                try {
                    amp = Integer.parseInt(split[1]);
                } catch (Exception e) {

                }
            }

            for (PotionEffectType x : PotionEffectType.values()) {
                if (x == null) {
                    continue;
                }
                if (x.getName().equalsIgnoreCase(split[0])) {
                    pet = x;
                    break;
                }
            }

            if (pet == null) {
                PVPArena.instance.getLogger().warning(
                        "Invalid Potion Effect Definition: " + value);
                return;
            }

            player.addPotionEffect(new PotionEffect(pet, amp, 2147000));
        }
    */
    @Override
    public PACheck checkJoin(final CommandSender sender, final PACheck res, final String[] args) {
        if (res.getPriority() >= PRIORITY) {
            return res;
        }

        final int maxPlayers = arena.getArenaConfig().getInt(CFG.READY_MAXPLAYERS);
        final int maxTeamPlayers = arena.getArenaConfig().getInt(
                CFG.READY_MAXTEAMPLAYERS);

        if (maxPlayers > 0 && arena.getFighters().size() >= maxPlayers) {
            res.setError(this, Language.parse(MSG.ERROR_JOIN_ARENA_FULL));
            return res;
        }

        if (args == null || args.length < 1) {
            return res;
        }

        if (!arena.isFreeForAll()) {
            final ArenaTeam team = arena.getTeam(args[0]);

            if (team != null && maxTeamPlayers > 0
                    && team.getTeamMembers().size() >= maxTeamPlayers) {
                res.setError(this, Language.parse(MSG.ERROR_JOIN_TEAM_FULL, team.getName()));
                return res;
            }
        }

        res.setPriority(this, PRIORITY);
        return res;
    }

    @Override
    public PACheck checkSetBlock(final PACheck res, final Player player, final Block block) {

        if (res.getPriority() > PRIORITY
                || !PAA_Region.activeSelections.containsKey(player.getName())) {
            return res;
        }
        res.setPriority(this, PRIORITY); // success :)

        return res;
    }

    private void commit(final Arena arena, final String sTeam) {
        debug.i("[Pillar] committing end: " + sTeam);
        debug.i("win: " + true);

        String winteam = sTeam;

        for (final ArenaTeam team : arena.getTeams()) {
            if (team.getName().equals(sTeam)) {
                continue;
            }
            for (final ArenaPlayer ap : team.getTeamMembers()) {

                ap.addStatistic(arena.getName(), type.LOSSES, 1);
                arena.tpPlayerToCoordName(ap.get(), "spectator");
                ap.setTelePass(false);
            }
        }
        for (final ArenaTeam team : arena.getTeams()) {
            for (final ArenaPlayer ap : team.getTeamMembers()) {
                if (ap.getStatus() != Status.FIGHT) {
                    continue;
                }
                winteam = team.getName();
                break;
            }
        }

        if (arena.getTeam(winteam) != null) {

            ArenaModuleManager
                    .announce(
                            arena,
                            Language.parse(MSG.TEAM_HAS_WON,
                                    arena.getTeam(winteam).getColor()
                                            + winteam + ChatColor.YELLOW),
                            "WINNER");
            arena.broadcast(Language.parse(MSG.TEAM_HAS_WON,
                    arena.getTeam(winteam).getColor() + winteam
                            + ChatColor.YELLOW));
        }

        new EndRunnable(arena, arena.getArenaConfig().getInt(
                CFG.TIME_ENDCOUNTDOWN));
    }

    @Override
    public void commitCommand(final CommandSender sender, final String[] args) {
        if ("flageffect".equalsIgnoreCase(args[0])) {
            // /pa [arena] flageffect SLOW 2
            if (args.length < 2) {
                arena.msg(
                        sender,
                        Language.parse(MSG.ERROR_INVALID_ARGUMENT_COUNT,
                                String.valueOf(args.length), "2"));
                return;
            }

            if ("none".equalsIgnoreCase(args[1])) {
                arena.getArenaConfig().set(CFG.GOAL_FLAGS_FLAGEFFECT, args[1]);

                arena.getArenaConfig().save();
                arena.msg(
                        sender,
                        Language.parse(MSG.SET_DONE,
                                CFG.GOAL_FLAGS_FLAGEFFECT.getNode(), args[1]));
                return;
            }

            PotionEffectType pet = null;

            for (final PotionEffectType x : PotionEffectType.values()) {
                if (x == null) {
                    continue;
                }
                if (x.getName().equalsIgnoreCase(args[1])) {
                    pet = x;
                    break;
                }
            }

            if (pet == null) {
                arena.msg(sender, Language.parse(
                        MSG.ERROR_POTIONEFFECTTYPE_NOTFOUND, args[1]));
                return;
            }

            int amp = 1;

            if (args.length == 5) {
                try {
                    amp = Integer.parseInt(args[2]);
                } catch (final Exception e) {
                    arena.msg(sender,
                            Language.parse(MSG.ERROR_NOT_NUMERIC, args[2]));
                    return;
                }
            }
            final String value = args[1] + 'x' + amp;
            arena.getArenaConfig().set(CFG.GOAL_FLAGS_FLAGEFFECT, value);

            arena.getArenaConfig().save();
            arena.msg(
                    sender,
                    Language.parse(MSG.SET_DONE,
                            CFG.GOAL_FLAGS_FLAGEFFECT.getNode(), value));

        } else if (args[0].contains("pillar")) {
            flagName = args[0];
            PAA_Region.activeSelections.put(sender.getName(), arena);

            arena.msg(sender,
                    Language.parse(MSG.GOAL_FLAGS_TOSET, flagName));
        }
    }

    @Override
    public void commitEnd(final boolean force) {
        debug.i("[Pillar]");

        ArenaTeam aTeam = null;

        for (final ArenaTeam team : arena.getTeams()) {
            for (final ArenaPlayer ap : team.getTeamMembers()) {
                if (ap.getStatus() == Status.FIGHT) {
                    aTeam = team;
                    break;
                }
            }
        }

        if (aTeam != null && !force) {

            ArenaModuleManager.announce(
                    arena,
                    Language.parse(MSG.TEAM_HAS_WON, aTeam.getColor()
                            + aTeam.getName() + ChatColor.YELLOW), "WINNER");
            arena.broadcast(Language.parse(MSG.TEAM_HAS_WON, aTeam.getColor()
                    + aTeam.getName() + ChatColor.YELLOW));
        }

        if (ArenaModuleManager.commitEnd(arena, aTeam)) {
            return;
        }
        new EndRunnable(arena, arena.getArenaConfig().getInt(
                CFG.TIME_ENDCOUNTDOWN));
    }

    @Override
    public boolean commitSetFlag(final Player player, final Block block) {
        if (block == null
                || block.getType() != Material.WOOL) {
            return false;
        }

        if (!PVPArena.hasAdminPerms(player)
                && !PVPArena.hasCreatePerms(player, arena)) {
            return false;
        }

        debug.i("trying to set a flag", player);

        SpawnManager.setBlock(arena, new PABlockLocation(block.getLocation()),
                flagName);

        arena.msg(player, Language.parse(MSG.GOAL_FLAGS_SET, flagName));

        PAA_Region.activeSelections.remove(player.getName());
        flagName = "";

        return true;
    }

    @Override
    public void commitStart() {
    }

    @Override
    public void configParse(final YamlConfiguration config) {
        Bukkit.getPluginManager().registerEvents(this, PVPArena.instance);
    }

    @Override
    public void disconnect(final ArenaPlayer aPlayer) {
        if (getFlagMap() == null) {
            return;
        }
        final String sTeam = getHeldFlagTeam(aPlayer.getName());
        final ArenaTeam flagTeam = arena.getTeam(sTeam);

        if (sTeam == null) {
            return;
        }

        arena.broadcast(Language.parse(MSG.GOAL_FLAGS_DROPPED, aPlayer
                .getArenaTeam().getColorCodeString()
                + aPlayer.getName()
                + ChatColor.YELLOW, flagTeam.getName() + ChatColor.YELLOW));
        getFlagMap().remove(flagTeam.getName());
        if (getHeadGearMap() != null && getHeadGearMap().get(aPlayer.getName()) != null) {
            if (aPlayer.get() != null) {
                aPlayer.get().getInventory()
                        .setHelmet(getHeadGearMap().get(aPlayer.getName()).clone());
            }
            getHeadGearMap().remove(aPlayer.getName());
        }

        //TODO maybe replace the block that has been taken
    }

    private Map<String, String> getFlagMap() {
        if (flagMap == null) {
            flagMap = new HashMap<>();
        }
        return flagMap;
    }

    @Override
    public PACheck getLives(final PACheck res, final ArenaPlayer aPlayer) {
        if (scores == null) {
            res.setError(this, "0");
        } else if (!res.hasError() && res.getPriority() <= PRIORITY) {
            res.setError(
                    this,
                    String.valueOf(scores.containsKey(aPlayer.getArenaTeam()) ?
                            scores.get(aPlayer.getArenaTeam()) : 0));
        }
        return res;
    }

    private Map<String, ItemStack> getHeadGearMap() {
        if (headGearMap == null) {
            headGearMap = new HashMap<>();
        }
        return headGearMap;
    }

    /**
     * get the team name of the flag a player holds
     *
     * @param player the player to check
     * @return a team name
     */
    private String getHeldFlagTeam(final String player) {
        if (getFlagMap().size() < 1) {
            return null;
        }

        debug.i("getting held FLAG of player " + player, player);
        for (final String sTeam : getFlagMap().keySet()) {
            debug.i("team " + sTeam + " is in " + getFlagMap().get(sTeam)
                    + "s hands", player);
            if (player.equals(getFlagMap().get(sTeam))) {
                return sTeam;
            }
        }
        return null;
    }

    private Map<String, Pillar> getPillarMap() {
        if (pillarMap == null) {
            pillarMap = new HashMap<>();
        }
        return pillarMap;
    }

    @Override
    public boolean hasSpawn(final String string) {
        for (final String teamName : arena.getTeamNames()) {
            if (string.toLowerCase().startsWith(
                    teamName.toLowerCase() + "spawn")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isInternal() {
        return true;
    }

    @Override
    public void parsePlayerDeath(final Player player,
                                 final EntityDamageEvent lastDamageCause) {

        if (getFlagMap() == null) {
            debug.i("no flags set!!", player);
            return;
        }
        final String sTeam = getHeldFlagTeam(player.getName());
        final ArenaTeam flagTeam = arena.getTeam(sTeam);
        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());

        if (flagTeam == null) {
            return;
        }

        arena.broadcast(Language.parse(MSG.GOAL_FLAGS_DROPPED, aPlayer
                        .getArenaTeam().colorizePlayer(player) + ChatColor.YELLOW,
                flagTeam.getColoredName() + ChatColor.YELLOW));
        getFlagMap().remove(flagTeam.getName());
        if (getHeadGearMap() != null
                && getHeadGearMap().get(player.getName()) != null) {
            player.getInventory().setHelmet(
                    getHeadGearMap().get(player.getName()).clone());
            getHeadGearMap().remove(player.getName());
        }

        // TODO maybe bring back the flag
    }

    @Override
    public void parseStart() {
        getPillarMap().clear();

        if (scores == null) {
            scores = new HashMap<>();
        }

        scores.clear();

        final Set<PASpawn> map = SpawnManager.getPASpawnsStartingWith(arena, "pillar");

        final int emptyHeight = arena.getArenaConfig().getInt(CFG.GOAL_PILLARS_EMPTYHEIGHT);
        breakable = arena.getArenaConfig().getBoolean(CFG.GOAL_PILLARS_BREAKABLE);
        final int tickInterval = arena.getArenaConfig().getInt(CFG.GOAL_PILLARS_INTERVAL);
        final int maxClicks = arena.getArenaConfig().getInt(CFG.GOAL_PILLARS_MAXCLICKS);
        final int maxHeight = arena.getArenaConfig().getInt(CFG.GOAL_PILLARS_MAXHEIGHT);
        final int teamHeight = arena.getArenaConfig().getInt(CFG.GOAL_PILLARS_TEAMHEIGHT);
        tickPoints = arena.getArenaConfig().getInt(CFG.GOAL_PILLARS_TICKPOINTS);
        onlyFree = arena.getArenaConfig().getBoolean(CFG.GOAL_PILLARS_ONLYFREE);
        announceTick = arena.getArenaConfig().getBoolean(CFG.GOAL_PILLARS_ANNOUNCETICK);

        for (final PASpawn spawn : map) {

            final String[] split = spawn.getName().split("pillar");
            final ArenaTeam owner = arena.getTeam(split[0]);
            getPillarMap().put(spawn.getName(), new Pillar(
                    new PABlockLocation(spawn.getLocation().toLocation()),
                    owner == null ? emptyHeight : teamHeight,
                    maxHeight,
                    maxClicks,
                    owner));
        }

        for (final Pillar pillar : getPillarMap().values()) {
            pillar.reset();
        }

        pillarRunner = Bukkit.getScheduler().runTaskTimer(PVPArena.instance, new PillarRunner(this), tickInterval * 1L, tickInterval * 1L);

    }

    private boolean reduceLivesCheckEndAndCommit() {
        debug.i("checking lives");

        final int max = arena.getArenaConfig().getInt(CFG.GOAL_PILLARS_LIVES);

        for (final Map.Entry<ArenaTeam, Double> arenaTeamDoubleEntry : scores.entrySet()) {
            final double score = arenaTeamDoubleEntry.getValue();
            if (score >= max) {
                commit(arena, arenaTeamDoubleEntry.getKey().getName());
                return true;
            }
        }

        return false;
    }

    /*
        private void removeEffects(final Player player) {
            final String value = arena.getArenaConfig().getString(
                    CFG.GOAL_FLAGS_FLAGEFFECT);

            if (value.equalsIgnoreCase("none")) {
                return;
            }

            PotionEffectType pet = null;

            final String[] split = value.split("x");

            for (PotionEffectType x : PotionEffectType.values()) {
                if (x == null) {
                    continue;
                }
                if (x.getName().equalsIgnoreCase(split[0])) {
                    pet = x;
                    break;
                }
            }

            if (pet == null) {
                PVPArena.instance.getLogger().warning(
                        "Invalid Potion Effect Definition: " + value);
                return;
            }

            player.removePotionEffect(pet);
            player.addPotionEffect(new PotionEffect(pet, 0, 1));
        }
    */
    @Override
    public void reset(final boolean force) {
        getFlagMap().clear();
        getHeadGearMap().clear();
        for (final Pillar p : getPillarMap().values()) {
            p.reset();
        }

        if (pillarRunner != null) {
            pillarRunner.cancel();
            pillarRunner = null;
        }
    }

    @Override
    public void setDefaults(final YamlConfiguration config) {
        if (arena.isFreeForAll()) {
            return;
        }

        if (config.get("teams.free") != null) {
            config.set("teams", null);
        }
        if (config.get("teams") == null) {
            debug.i("no teams defined, adding custom red and blue!");
            config.addDefault("teams.red", ChatColor.RED.name());
            config.addDefault("teams.blue", ChatColor.BLUE.name());
        }
        if (arena.getArenaConfig().getBoolean(CFG.GOAL_FLAGS_WOOLFLAGHEAD)
                && config.get("flagColors") == null) {
            debug.i("no flagheads defined, adding white and black!");
            config.addDefault("flagColors.red", "WHITE");
            config.addDefault("flagColors.blue", "BLACK");
        }
    }

    @Override
    public Map<String, Double> timedEnd(final Map<String, Double> scores) {

        for (final ArenaTeam team : arena.getTeams()) {
            double score = this.scores.containsKey(team) ? this.scores
                    .get(team) : 0;
            if (scores.containsKey(team.getName())) {
                scores.put(team.getName(), scores.get(team.getName()) + score);
            } else {
                scores.put(team.getName(), score);
            }
        }

        return scores;
    }

    void tick() {

        if (arena.realEndRunner != null) {
            return;
        }

        boolean didsomething = false;

        boolean claimedAll = true;
        ArenaTeam allClaimed = null;

        for (final String name : getPillarMap().keySet()) {
            final Pillar pillar = getPillarMap().get(name);
            if (pillar.getOwner() == null) {
                claimedAll = false;
                continue;
            }
            if ((allClaimed == null || allClaimed == pillar.getOwner())
                    // ^ noone claimed yet or the current team is claiming
                    && claimedAll) { // and so far everything claimed
                allClaimed = pillar.getOwner(); // update if needed, done
            }

            if (onlyFree &&
                    (pillar.getDefaultTeam() != null
                            || name.contains(pillar.getOwner().getName()))) {
                // only free
                // team spawn OR claimed by the only team that can claim
                continue;
            }

            if (pillar.getDefaultTeam() != null
                    && pillar.getDefaultTeam() == pillar.getOwner()) {
                // we have a default and the default team is owning
                continue;
            }

            if (pillar.getDefaultTeam() == null) {
                // no default team, check if the pillar name contains a team name
                // continue if owner is NOT that team

                boolean conti = false;

                for (final String teamName : arena.getTeamNames()) {
                    if (name.contains(teamName)
                            && !pillar.getOwner().getName().equals(teamName)) {
                        conti = true;
                        break;
                    }
                }

                if (conti) {
                    continue;
                }

            }

            final ArenaTeam owner = pillar.getOwner();
            double score = scores.containsKey(owner) ? scores.get(owner) : 0;

            score += pillar.getClaimStatus() * tickPoints;

            scores.put(owner, score);
            didsomething = true;
        }

        if (claimedAll && arena.getArenaConfig().getBoolean(CFG.GOAL_PILLARS_CLAIMALL)) {
            // team: allClaimed
            for (final ArenaTeam otherTeam : arena.getTeams()) {
                if (otherTeam.equals(allClaimed)) {
                    continue;
                }
                getLifeMap().remove(otherTeam.getName());
                for (final ArenaPlayer ap : otherTeam.getTeamMembers()) {
                    if (ap.getStatus() == Status.FIGHT) {
                        ap.setStatus(Status.LOST);
                        arena.removePlayer(ap.get(), CFG.TP_LOSE.toString(),
                                true, false);
                    }
                }
            }
            PACheck.handleEnd(arena, false);
            return;
        }

        if (scores.isEmpty() || !didsomething) {
            return;
        }

        if (reduceLivesCheckEndAndCommit() || !announceTick) {
            return;
        }

        offset = ++offset % arena.getArenaConfig().getInt(CFG.GOAL_PILLARS_ANNOUNCEOFFSET);

        if (offset != 0) {
            return;
        }

        final Set<String> msgs = new HashSet<>();

        for (final Map.Entry<ArenaTeam, Double> arenaTeamDoubleEntry : scores.entrySet()) {
            msgs.add(Language.parse(MSG.GOAL_PILLARS_MSG_SCORE, arenaTeamDoubleEntry.getKey().getColoredName() + ChatColor.YELLOW, String.format("%.1f", arenaTeamDoubleEntry.getValue())));
        }

        arena.broadcast(StringParser.joinSet(msgs, " - "));
    }

    @Override
    public void unload(final Player player) {
        disconnect(ArenaPlayer.parsePlayer(player.getName()));
        if (allowsJoinInBattle()) {
            arena.hasNotPlayed(ArenaPlayer.parsePlayer(player.getName()));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlagAdd(final BlockPlaceEvent event) {
        final Player player = event.getPlayer();
        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());

        if (!arena.equals(aPlayer.getArena())) {
            return;
        }

        final Block block = event.getBlockAgainst();

        ItemStack flagType = StringParser.getItemStackFromString(arena.getArenaConfig().getString(
                CFG.GOAL_FLAGS_FLAGTYPE));

        if (block.getType() != flagType.getType() || (flagType.getData().getData()>0 && flagType.getData().getData() != block.getData())) {
            debug.i("block, but not flag", player);
            return;
        }
        debug.i("flag place?", player);

        for (final String pillarName : getPillarMap().keySet()) {
            Pillar pillar = getPillarMap().get(pillarName);
            final ArenaTeam owner = pillar.getOwner();
            final ArenaPlayer clicker = ArenaPlayer.parsePlayer(player.getName());
            if (pillar.getLocation().equals(new PABlockLocation(block.getLocation()))) {

                debug.i("cancel!", player);
                event.setCancelled(true);
                if (!breakable) {
                    debug.i("!breakable => OUT", player);
                    return;
                }

                announce(pillarName, owner, clicker, pillar.blockPlace(clicker));
                return;
            } else {
                if (pillar.containsLocation(new PABlockLocation(event.getBlock().getLocation()))) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFlagClaim(final BlockBreakEvent event) {

        final Block block = event.getBlock();
        final Player player = event.getPlayer();

        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());
        if (!arena.equals(aPlayer.getArena())) {
            return;
        }

        ItemStack flagType = StringParser.getItemStackFromString(arena.getArenaConfig().getString(
                CFG.GOAL_FLAGS_FLAGTYPE));
        if (block.getType() != flagType.getType() || (flagType.getData().getData()>0 && flagType.getData().getData() != block.getData())) {
            debug.i("block, but not flag", player);
            return;
        }
        debug.i("flag break?", player);


        for (final String pillarName : getPillarMap().keySet()) {
            Pillar pillar = getPillarMap().get(pillarName);
            final ArenaTeam owner = pillar.getOwner();
            final ArenaPlayer clicker = ArenaPlayer.parsePlayer(player.getName());
            if (pillar.getLocation().equals(new PABlockLocation(block.getLocation()))) {

                if (!breakable || owner == null) {
                    debug.i("!breakable || " + owner + "=> CANCEL", player);
                    event.setCancelled(true);
                    return;
                }

                final PillarResult result = pillar.blockBreak(clicker);

                event.setCancelled(result == PillarResult.NONE);

                announce(pillarName, owner, clicker, result);
                return;
            } else {
                if (pillar.containsLocation(new PABlockLocation(event.getBlock().getLocation()))) {
                    event.setCancelled(true);
                    return;
                }
            }
        }
    }
/*
    @EventHandler(ignoreCancelled = true)
	public void onInventoryClick(final InventoryClickEvent event) {
		final Player player = (Player) event.getWhoClicked();

		final Arena arena = ArenaPlayer.parsePlayer(player.getName()).getArena();

		if (arena == null || !arena.getName().equals(this.arena.getName())) {
			return;
		}

		if (getHeldFlagTeam(player.getName()) == null) {
			return;
		}

		if (event.getInventory().getType().equals(InventoryType.CRAFTING)
				&& event.getRawSlot() != 5) {
			return;
		}

		event.setCancelled(true);
	}
	*/
}
