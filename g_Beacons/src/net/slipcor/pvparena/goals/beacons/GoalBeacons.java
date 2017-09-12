package net.slipcor.pvparena.goals.beacons;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaClass;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaPlayer.Status;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.classes.PABlockLocation;
import net.slipcor.pvparena.classes.PACheck;
import net.slipcor.pvparena.classes.PAClaimBar;
import net.slipcor.pvparena.commands.PAA_Region;
import net.slipcor.pvparena.core.Config.CFG;
import net.slipcor.pvparena.core.Debug;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.Language.MSG;
import net.slipcor.pvparena.core.StringParser;
import net.slipcor.pvparena.events.PAGoalEvent;
import net.slipcor.pvparena.loadables.ArenaGoal;
import net.slipcor.pvparena.loadables.ArenaModuleManager;
import net.slipcor.pvparena.managers.SpawnManager;
import net.slipcor.pvparena.managers.TeamManager;
import net.slipcor.pvparena.runnables.CircleParticleRunnable;
import net.slipcor.pvparena.runnables.EndRunnable;
import org.bukkit.*;
import org.bukkit.block.Beacon;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.BeaconInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class GoalBeacons extends ArenaGoal {

    private BukkitTask circleTask = null;
    private boolean selectingBeacons = false;

    private static BlockFace[] fakePyramid = new BlockFace[]{
            BlockFace.SELF,
            BlockFace.NORTH,BlockFace.EAST,BlockFace.SOUTH,BlockFace.WEST,
            BlockFace.NORTH_EAST,BlockFace.NORTH_WEST,BlockFace.SOUTH_EAST,BlockFace.SOUTH_WEST
    };

    public GoalBeacons() {
        super("Beacons");
        debug = new Debug(109);
    }

    private Map<Location, String> beaconMap = new HashMap<>();
    private Map<Location, BeaconRunnable> runnerMap = new HashMap<>();
    private Map<Location, PAClaimBar> flagBars = new HashMap<>();

    private BeaconUpdateRunnable runner = null;

    protected int announceOffset;
    private Location activeBeaconLocation = null;

    @Override
    public String version() {
        return "v1.3.4.277";
    }

    private static final int PRIORITY = 10;

    @Override
    public boolean allowsJoinInBattle() {
        return arena.getArenaConfig().getBoolean(CFG.PERMS_JOININBATTLE);
    }

    private void barStart(Location location, String title, ChatColor color, int range, long interval) {
        if (!arena.getArenaConfig().getBoolean(CFG.GOAL_BEACONS_BOSSBAR)) {
            return;
        }
        if (getBarMap().containsKey(location)) {
            PAClaimBar claimBar = getBarMap().get(location);
            claimBar.restart(title, color, location, range, interval);
        } else {
            PAClaimBar claimBar = new PAClaimBar(arena, title, color, location, range, interval);
            getBarMap().put(location, claimBar);
        }
    }

    private void barStop(Location location) {
        if (getBarMap().containsKey(location)) {
            getBarMap().get(location).stop();
        }
    }

    @Override
    public PACheck checkCommand(final PACheck res, final String string) {
        if (res.getPriority() > PRIORITY) {
            return res;
        }

        if ("beacon".equals(string)) {
            res.setPriority(this, PRIORITY);
        }

        return res;
    }

    @Override
    public List<String> getMain() {
        return Collections.singletonList("beacon");
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

        final String team = checkForMissingTeamSpawn(list);
        if (team != null) {
            return team;
        }
        int count = 0;
        for (final String s : list) {
            if (s.startsWith("beacon")) {
                count++;
            }
        }
        if (count < 1) {
            return "beacon: " + count + " / 1";
        }
        return null;
    }

    @Override
    public PACheck checkJoin(final CommandSender sender, final PACheck res, final String[] args) {
        if (res.getPriority() >= PRIORITY) {
            return res;
        }

        final int maxPlayers = arena.getArenaConfig().getInt(CFG.READY_MAXPLAYERS);
        final int maxTeamPlayers = arena.getArenaConfig().getInt(
                CFG.READY_MAXTEAMPLAYERS);

        if (maxPlayers > 0 && arena.getFighters().size() >= maxPlayers) {
            res.setError(this, Language.parse(arena, MSG.ERROR_JOIN_ARENA_FULL));
            return res;
        }

        if (args == null || args.length < 1) {
            return res;
        }

        if (!arena.isFreeForAll()) {
            final ArenaTeam team = arena.getTeam(args[0]);

            if (team != null && maxTeamPlayers > 0
                    && team.getTeamMembers().size() >= maxTeamPlayers) {
                res.setError(this, Language.parse(arena, MSG.ERROR_JOIN_TEAM_FULL, team.getName()));
                return res;
            }
        }

        res.setPriority(this, PRIORITY);
        return res;
    }

    /**
     * return a hashset of players names being near a specified location, except
     * one player
     *
     * @param loc      the location to check
     * @param distance the distance in blocks
     * @return a set of player names
     */
    private Set<String> checkLocationPresentTeams(final Location loc, final int distance) {
        final Set<String> result = new HashSet<>();

        for (final ArenaPlayer p : arena.getFighters()) {

            if (p.get().getLocation().distance(loc) > distance) {
                continue;
            }

            result.add(p.getArenaTeam().getName());
        }

        return result;
    }

    void checkMove() {

        /**
         * possible Situations
         *
         * >>- beacon is unclaimed and no one is there
         * >>- beacon is unclaimed and team a is there
         * >>- beacon is unclaimed and multiple teams are there
         *
         * >>- beacon is being claimed by team a, no one is present
         * >>- beacon is being claimed by team a, team a is present
         * >>- beacon is being claimed by team a, multiple teams are present
         * >>- beacon is being claimed by team a, team b is present
         *
         * >>- beacon is claimed by team a, no one is present
         * >>- beacon is claimed by team a, team a is present
         * >>- beacon is claimed by team a, multiple teams are present
         * >>- beacon is claimed by team a, team b is present
         *
         * >>- beacon is claimed by team a and being unclaimed, no one is present
         * >>- beacon is claimed by team a and being unclaimed, team a is present
         * >>- beacon is claimed by team a and being unclaimed, multiple teams are present
         * >>- beacon is claimed by team a and being unclaimed, team b is present
         *
         */

        arena.getDebugger().i("------------------");
        arena.getDebugger().i("   checkMove();");
        arena.getDebugger().i("------------------");

        final int checkDistance = arena.getArenaConfig().getInt(
                CFG.GOAL_BEACONS_CLAIMRANGE);

        for (final PABlockLocation paLoc : SpawnManager.getBlocksStartingWith(arena, "beacon")) {

            final Location loc = paLoc.toLocation();

            if (!loc.equals(activeBeaconLocation)) {
                continue;
            }

            final Set<String> teams = checkLocationPresentTeams(paLoc.toLocation(),
                    checkDistance);

            arena.getDebugger().i("teams: " + StringParser.joinSet(teams, ", "));

            // teams now contains all teams near the beacon

            if (teams.size() < 1) {
                // arena.getDebugger().info("=> noone there!");
                // no one there
                if (getRunnerMap().containsKey(loc)) {
                    arena.getDebugger().i("beacon is being (un)claimed! Cancelling!");
                    // cancel unclaiming/claiming if noone's near
                    Bukkit.getScheduler().cancelTask(getRunnerMap().get(loc).runID);
                    getRunnerMap().remove(loc);
                    barStop(loc);
                }
                if (getBeaconMap().containsKey(loc)) {
                    final String team = getBeaconMap().get(loc);

                    if (!getLifeMap().containsKey(team)) {
                        continue;
                    }

                    // beacon claimed! add score!
                    maybeAddScoreAndBroadCast(team);
                }
                continue;
            }

            // there are actually teams at the beacon
            arena.getDebugger().i("=> at least one team is at the beacon!");

            if (getBeaconMap().containsKey(loc)) {
                // beacon is taken. by whom?
                if (teams.contains(getBeaconMap().get(loc))) {
                    // owning team is there
                    arena.getDebugger().i("  - owning team is there");
                    if (teams.size() > 1) {
                        // another team is there
                        arena.getDebugger().i("    - and another one");
                        if (getRunnerMap().containsKey(loc)) {
                            // it is being unclaimed
                            arena.getDebugger().i("      - being unclaimed. continue!");
                        } else {
                            // unclaim
                            arena.getDebugger().i("      - not being unclaimed. do it!");
                            ArenaTeam team = arena.getTeam(getBeaconMap().get(loc));
                            arena.broadcast(Language.parse(arena, MSG.GOAL_BEACONS_CONTESTING, team.getColoredName() + ChatColor.YELLOW));
                            final BeaconRunnable beaconRunner = new BeaconRunnable(
                                    arena, false, loc,
                                    getBeaconMap().get(loc), this);
                            beaconRunner.runID = Bukkit.getScheduler()
                                    .scheduleSyncRepeatingTask(
                                            PVPArena.instance, beaconRunner, 10 * 20L,
                                            10 * 20L);
                            getRunnerMap().put(loc, beaconRunner);
                            barStart(loc, "claiming", ChatColor.WHITE, arena.getArenaConfig().getInt(CFG.GOAL_BEACONS_CLAIMRANGE), 200L);
                        }
                    } else {
                        // just the owning team is there
                        arena.getDebugger().i("    - noone else");
                        if (getRunnerMap().containsKey(loc)) {
                            arena.getDebugger().i("      - being unclaimed. cancel!");
                            // it is being unclaimed
                            // cancel task!
                            Bukkit.getScheduler()
                                    .cancelTask(getRunnerMap().get(loc).runID);
                            getRunnerMap().remove(loc);
                            barStop(loc);
                        } else {

                            final String team = getBeaconMap().get(loc);

                            if (!getLifeMap().containsKey(team)) {
                                continue;
                            }

                            maybeAddScoreAndBroadCast(team);
                        }
                    }
                    continue;
                }

                arena.getDebugger().i("  - owning team is not there!");
                // owning team is NOT there ==> unclaim!

                if (getRunnerMap().containsKey(loc)) {
                    if (getRunnerMap().get(loc).take) {
                        arena.getDebugger().i("    - runnable is trying to score, abort");

                        Bukkit.getScheduler().cancelTask(getRunnerMap().get(loc).runID);
                        getRunnerMap().remove(loc);
                        barStop(loc);
                    } else {
                        arena.getDebugger().i("    - being unclaimed. continue.");
                    }
                    continue;
                }
                arena.getDebugger().i("    - not yet being unclaimed, do it!");
                // create an unclaim runnable
                ArenaTeam team = arena.getTeam(getBeaconMap().get(loc));
                arena.broadcast(Language.parse(arena, MSG.GOAL_BEACONS_UNCLAIMING, team.getColoredName() + ChatColor.YELLOW));
                final BeaconRunnable running = new BeaconRunnable(arena,
                        false, loc, getBeaconMap().get(loc), this);
                final long interval = 20L * 10;

                running.runID = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                        PVPArena.instance, running, interval, interval);
                getRunnerMap().put(loc, running);
                barStart(loc, "unclaiming", ChatColor.WHITE, arena.getArenaConfig().getInt(CFG.GOAL_BEACONS_CLAIMRANGE), interval);
            } else {
                // beacon not taken
                arena.getDebugger().i("- beacon not taken");

				/*
                 * check if a runnable
				 * 	yes
				 * 		check if only that team
				 * 			yes => continue;
				 * 			no => cancel
				 * 	no
				 * 		check if only that team
				 * 			yes => create runnable;
				 * 			no => continue
				 */
                if (getRunnerMap().containsKey(loc)) {
                    arena.getDebugger().i("  - being claimed");
                    if (teams.size() < 2) {
                        arena.getDebugger().i("  - only one team present");
                        if (teams.contains(getRunnerMap().get(loc).team)) {
                            // just THE team that is claiming => NEXT
                            arena.getDebugger().i("  - claiming team present. next!");
                            continue;
                        }
                    }
                    arena.getDebugger().i("  - more than one team or another team. cancel claim!");
                    // more than THE team that is claiming => cancel!
                    Bukkit.getScheduler().cancelTask(getRunnerMap().get(loc).runID);
                    getRunnerMap().remove(loc);
                    barStop(loc);
                } else {
                    arena.getDebugger().i("  - not being claimed");
                    // not being claimed
                    if (teams.size() < 2) {
                        arena.getDebugger().i("  - just one team present");
                        for (final String sName : teams) {
                            arena.getDebugger().i("TEAM " + sName + " IS CLAIMING "
                                    + loc);
                            final ArenaTeam team = arena.getTeam(sName);
                            arena.broadcast(Language.parse(arena,
                                    MSG.GOAL_BEACONS_CLAIMING,
                                    team.getColoredName() + ChatColor.YELLOW));

                            final BeaconRunnable running = new BeaconRunnable(
                                    arena, true, loc, sName, this);
                            final long interval = 20L * 10;
                            running.runID = Bukkit.getScheduler()
                                    .scheduleSyncRepeatingTask(
                                            PVPArena.instance, running,
                                            interval, interval);
                            getRunnerMap().put(loc, running);
                            barStart(loc, "claiming", team.getColor(), arena.getArenaConfig().getInt(CFG.GOAL_BEACONS_CLAIMRANGE), interval);
                        }
                    } else {
                        arena.getDebugger().i("  - more than one team present. continue!");
                    }
                }
            }
        }
    }

    private void maybeAddScoreAndBroadCast(final String team) {

        reduceLivesCheckEndAndCommit(arena, team);

        final int max = arena.getArenaConfig().getInt(CFG.GOAL_BEACONS_LIVES);
        if (!getLifeMap().containsKey(team)) {
            return;
        }

        final int lives = getLifeMap().get(team);

        if ((max - lives) % announceOffset != 0) {
            return;
        }

        arena.broadcast(Language.parse(arena, MSG.GOAL_BEACONS_SCORE,
                arena.getTeam(team).getColoredName()
                        + ChatColor.YELLOW, (max - lives) + "/" + max));
    }

    @Override
    public PACheck checkSetBlock(final PACheck res, final Player player, final Block block) {

        if (res.getPriority() > PRIORITY
                || !PAA_Region.activeSelections.containsKey(player.getName())) {
            return res;
        }
        if (!selectingBeacons || block == null || (block.getType() != Material.STAINED_GLASS && block.getType() != Material.GLASS)) {
            return res;
        }
        res.setPriority(this, PRIORITY); // success :)

        return res;
    }

    private void commit(final Arena arena, final String sTeam) {
        if (arena.realEndRunner != null) {
            arena.getDebugger().i("[BEACONS] already ending");
            return;
        }
        arena.getDebugger().i("[BEACONS] committing end: " + sTeam);
        arena.getDebugger().i("win: " + true);

        String winteam = sTeam;

        for (final ArenaTeam team : arena.getTeams()) {
            if (team.getName().equals(sTeam)) {
                continue;
            }
            for (final ArenaPlayer ap : team.getTeamMembers()) {

                ap.addLosses();
                ap.setStatus(Status.LOST);
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
                            Language.parse(arena, MSG.TEAM_HAS_WON,
                                    arena.getTeam(winteam).getColor()
                                            + winteam + ChatColor.YELLOW),
                            "WINNER");
            arena.broadcast(Language.parse(arena, MSG.TEAM_HAS_WON,
                    arena.getTeam(winteam).getColor() + winteam
                            + ChatColor.YELLOW));
        }

        getLifeMap().clear();
        new EndRunnable(arena, arena.getArenaConfig().getInt(
                CFG.TIME_ENDCOUNTDOWN));
    }

    @Override
    public void commitCommand(final CommandSender sender, final String[] args) {
        if (PAA_Region.activeSelections.containsKey(sender.getName())) {
            PAA_Region.activeSelections.remove(sender.getName());
            arena.msg(sender, Language.parse(arena, MSG.GOAL_BEACONS_SETDONE));
            selectingBeacons = false;
        } else {
            PAA_Region.activeSelections.put(sender.getName(), arena);
            arena.msg(sender, Language.parse(arena, MSG.GOAL_BEACONS_TOSET));
            selectingBeacons = true;
        }
    }

    @Override
    public void commitEnd(final boolean force) {
        if (arena.realEndRunner != null) {
            arena.getDebugger().i("[BEACONS] already ending");
            return;
        }
        arena.getDebugger().i("[BEACONS]");

        if (activeBeaconLocation != null) {
            activateBeacon(false);
        }
        activeBeaconLocation = null;

        final PAGoalEvent gEvent = new PAGoalEvent(arena, this, "");
        Bukkit.getPluginManager().callEvent(gEvent);
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
                    Language.parse(arena, MSG.TEAM_HAS_WON, aTeam.getColor()
                            + aTeam.getName() + ChatColor.YELLOW), "END");

            ArenaModuleManager.announce(
                    arena,
                    Language.parse(arena, MSG.TEAM_HAS_WON, aTeam.getColor()
                            + aTeam.getName() + ChatColor.YELLOW), "WINNER");
            arena.broadcast(Language.parse(arena, MSG.TEAM_HAS_WON, aTeam.getColor()
                    + aTeam.getName() + ChatColor.YELLOW));
        }

        if (ArenaModuleManager.commitEnd(arena, aTeam)) {
            return;
        }
        new EndRunnable(arena, arena.getArenaConfig().getInt(
                CFG.TIME_ENDCOUNTDOWN));
    }

    protected void activateBeacon(boolean show) {
        if (activeBeaconLocation == null) {
            return;
        }
        if (show) {
            arena.broadcast(Language.parse(MSG.GOAL_BEACONS_CHANGED));
        }
        Block bGlass = activeBeaconLocation.getBlock();
        Block bBeacon = bGlass.getRelative(BlockFace.DOWN);
        Material mat = show? Material.IRON_BLOCK : Material.STONE;
        bBeacon.setType(Material.BEACON);
        Beacon b = (Beacon) bBeacon.getState();
        BeaconInventory bi = (BeaconInventory) b.getInventory();
        if (show) {
            bi.setItem(new ItemStack(Material.IRON_INGOT, 1));
        } else {
            bi.setItem(new ItemStack(Material.AIR, 0));
        }

        Block bDiamondCenter = bBeacon.getRelative(BlockFace.DOWN);
        for (ArenaPlayer ap : arena.getEveryone()) {
            Player p = ap.get();
            if (p == null) {
                continue;
            }
            if (!show) {
                ap.get().sendBlockChange(bGlass.getLocation(), Material.STAINED_GLASS,
                        StringParser.getColorDataFromENUM("WHITE"));
            } else {

            }
            for (BlockFace face : GoalBeacons.fakePyramid) {
                p.sendBlockChange(bDiamondCenter.getRelative(face).getLocation(), mat, (byte) 0);
            }
        }
    }

    @Override
    public boolean commitSetFlag(final Player player, final Block block) {

        if (PVPArena.hasAdminPerms(player)
                || PVPArena.hasCreatePerms(player, arena)
                && player.getInventory().getItemInMainHand() != null
                && player.getInventory().getItemInMainHand().getType().name() == arena
                .getArenaConfig().getString(CFG.GENERAL_WAND)) {

            final Set<PABlockLocation> beacons = SpawnManager.getBlocksStartingWith(arena,
                    "beacon");

            if (beacons.contains(new PABlockLocation(block.getLocation()))) {
                return false;
            }

            final String beaconName = "beacon" + beacons.size();

            SpawnManager.setBlock(arena,
                    new PABlockLocation(block.getLocation()), beaconName);

            arena.msg(player, Language.parse(arena, MSG.GOAL_BEACONS_SET, beaconName));
            return true;
        }
        return false;
    }

    @Override
    public void displayInfo(final CommandSender sender) {
        sender.sendMessage("needed points: " +
                arena.getArenaConfig().getInt(CFG.GOAL_BEACONS_LIVES));
        sender.sendMessage("claim range: " +
                arena.getArenaConfig().getInt(CFG.GOAL_BEACONS_CLAIMRANGE));
    }

    private Map<Location, PAClaimBar> getBarMap() {
        if (flagBars == null) {
            flagBars = new HashMap<>();
        }
        return flagBars;
    }

    protected Map<Location, String> getBeaconMap() {
        if (beaconMap == null) {
            beaconMap = new HashMap<>();
        }
        return beaconMap;
    }

    protected Map<String, Integer> getMyLifeMap() {
        return super.getLifeMap();
    }

    @Override
    public PACheck getLives(final PACheck res, final ArenaPlayer aPlayer) {
        if (res.getPriority() <= PRIORITY + 1000) {
            final int max = arena.getArenaConfig().getInt(CFG.GOAL_BEACONS_LIVES);
            res.setError(
                    this,
                    String.valueOf(getLifeMap().containsKey(aPlayer.getArenaTeam()
                            .getName()) ? (max-getLifeMap().get(aPlayer
                            .getArenaTeam().getName())) : max));
        }
        return res;
    }

    protected Map<Location, BeaconRunnable> getRunnerMap() {
        if (runnerMap == null) {
            runnerMap = new HashMap<>();
        }
        return runnerMap;
    }

    @Override
    public boolean hasSpawn(final String string) {
        for (final String teamName : arena.getTeamNames()) {
            if (string.toLowerCase().startsWith(
                    teamName.toLowerCase() + "spawn")) {
                return true;
            }

            if (arena.getArenaConfig().getBoolean(CFG.GENERAL_CLASSSPAWN)) {
                for (final ArenaClass aClass : arena.getClasses()) {
                    if (string.toLowerCase().startsWith(teamName.toLowerCase() +
                            aClass.getName().toLowerCase() + "spawn")) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public void initate(final Player player) {
        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());
        final ArenaTeam team = aPlayer.getArenaTeam();
        if (!getLifeMap().containsKey(team.getName())) {
            getLifeMap().put(aPlayer.getArenaTeam().getName(), arena.getArenaConfig()
                    .getInt(CFG.GOAL_BEACONS_LIVES));

            final Set<PABlockLocation> spawns = SpawnManager.getBlocksStartingWith(arena, "beacon");
            for (final PABlockLocation spawn : spawns) {
                takeBeacon(spawn);
            }
        }
    }

    @Override
    public void parseStart() {
        getLifeMap().clear();
        for (final ArenaTeam team : arena.getTeams()) {
            if (!team.getTeamMembers().isEmpty()) {
                arena.getDebugger().i("adding team " + team.getName());
                // team is active
                getLifeMap().put(team.getName(),
                        arena.getArenaConfig().getInt(CFG.GOAL_BEACONS_LIVES, 3));
            }
        }
        final Set<PABlockLocation> spawns = SpawnManager.getBlocksStartingWith(arena, "beacon");
        for (final PABlockLocation spawn : spawns) {
            takeBeacon(spawn);
        }

        final BeaconMainRunnable beaconMainRunner = new BeaconMainRunnable(arena, this);
        final int tickInterval = arena.getArenaConfig().getInt(CFG.GOAL_BEACONS_TICKINTERVAL);
        beaconMainRunner.rID = Bukkit.getScheduler().scheduleSyncRepeatingTask(
                PVPArena.instance, beaconMainRunner, tickInterval, tickInterval);

        announceOffset = arena.getArenaConfig().getBoolean(CFG.GOAL_BEACONS_CHANGEONCLAIM) ? 1 : arena.getArenaConfig().getInt(CFG.GOAL_BEACONS_ANNOUNCEOFFSET);

        this.runner = new BeaconUpdateRunnable(arena, this);

        final int runInterval = arena.getArenaConfig().getInt(CFG.GOAL_BEACONS_CHANGESECONDS);
        if (runInterval > 0) {
            Bukkit.getScheduler().scheduleSyncRepeatingTask(PVPArena.instance, runner, runInterval*20, runInterval*20);
        }
        runner.run();

        circleTask = Bukkit.getScheduler().runTaskTimer(PVPArena.instance, new CircleParticleRunnable(arena, CFG.GOAL_BEACONS_CLAIMRANGE), 1L, 1L);
    }

    protected boolean reduceLivesCheckEndAndCommit(final Arena arena, final String team) {

        arena.getDebugger().i("reducing lives of team " + team);
        if (getLifeMap().get(team) != null) {
            final int iLives = getLifeMap().get(team) - arena.getArenaConfig().getInt(CFG.GOAL_BEACONS_TICKREWARD);
            if (iLives > 0) {

                final PAGoalEvent gEvent = new PAGoalEvent(arena, this, "trigger:" + team);
                Bukkit.getPluginManager().callEvent(gEvent);
                getLifeMap().put(team, iLives);
            } else {
                getLifeMap().remove(team);
                commit(arena, team);
                return true;
            }
        }
        return false;
    }

    @Override
    public void reset(final boolean force) {
        getLifeMap().clear();
        getRunnerMap().clear();
        getBarMap().clear();
        getBeaconMap().clear();

        activateBeacon(false);
        activeBeaconLocation = null;
        try {
            Bukkit.getScheduler().cancelTask(runner.rID);
        } catch (Exception e) {
        }

        if (circleTask != null) {
            circleTask.cancel();
            circleTask = null;
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
            arena.getDebugger().i("no teams defined, adding custom red and blue!");
            config.addDefault("teams.red", ChatColor.RED.name());
            config.addDefault("teams.blue", ChatColor.BLUE.name());
        }
    }

    /**
     * take/reset an arena beacon
     *
     * @param paBlockLocation the location to take/reset*/
    void takeBeacon(final PABlockLocation paBlockLocation) {
        for (ArenaPlayer ap : arena.getEveryone()) {
            ap.get().sendBlockChange(paBlockLocation.toLocation(), Material.STAINED_GLASS, (byte) 0);
        }
    }

    @Override
    public Map<String, Double> timedEnd(final Map<String, Double> scores) {

        for (final ArenaTeam team : arena.getTeams()) {
            double score = getLifeMap().containsKey(team.getName()) ? getLifeMap()
                    .get(team.getName()) : 0;
            if (scores.containsKey(team.getName())) {
                scores.put(team.getName(), scores.get(team.getName()) + score);
            } else {
                scores.put(team.getName(), score);
            }
        }

        return scores;
    }

    public void updateBeacon() {
        Set<PABlockLocation> test = SpawnManager.getBlocksStartingWith(arena, "beacon");
        PABlockLocation[] values = test.toArray(new PABlockLocation[test.size()]);
        int pos = new Random().nextInt(values.length);

        int maxCheck = 10;

        while (values[pos].toLocation().equals(activeBeaconLocation)) {
            pos = new Random().nextInt(values.length);
            if (--maxCheck < 0) {
                break; // default to the same then
            }
        }
        // disable old
        if (activeBeaconLocation != null) {
            BeaconRunnable beaconRunner = getRunnerMap().get(activeBeaconLocation);
            if (beaconRunner != null) {
                Bukkit.getScheduler().cancelTask(beaconRunner.runID);
            }
            beaconRunner = new BeaconRunnable(arena, false, activeBeaconLocation, "", this);
            beaconRunner.run();

            // hide old
            activateBeacon(false);
        }
        // update, show new
        activeBeaconLocation = values[pos].toLocation();
        activateBeacon(true);
    }


}
