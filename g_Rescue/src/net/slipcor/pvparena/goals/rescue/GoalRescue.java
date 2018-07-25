package net.slipcor.pvparena.goals.rescue;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaClass;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaPlayer.Status;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.classes.PABlockLocation;
import net.slipcor.pvparena.classes.PACheck;
import net.slipcor.pvparena.commands.CommandTree;
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
import net.slipcor.pvparena.runnables.EndRunnable;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

import java.util.*;

public class GoalRescue extends ArenaGoal implements Listener {
    public GoalRescue() {
        super("Rescue");
        debug = new Debug(112);
    }

    private final Map<Entity, ArenaTeam> entityMap = new HashMap<>();

    private String flagName = "";

    @Override
    public String version() {
        return "v1.13.0";
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

        if ("entitytype".equalsIgnoreCase(string)) {
            res.setPriority(this, PRIORITY);
            return res;
        }

        for (ArenaTeam team : arena.getTeams()) {
            final String sTeam = team.getName();
            if (string.contains(sTeam + "rescue")) {
                res.setPriority(this, PRIORITY);
            }
        }

        return res;
    }

    @Override
    public List<String> getMain() {
        List<String> result = Collections.singletonList("entitytype");
        if (arena != null) {
            for (ArenaTeam team : arena.getTeams()) {
                final String sTeam = team.getName();
                result.add(sTeam + "rescue");
            }
        }
        return result;
    }

    @Override
    public CommandTree<String> getSubs(final Arena arena) {
        CommandTree<String> result = new CommandTree<>(null);
        result.define(new String[]{"entitytype", "{EntityType}"});
        result.define(new String[]{"rescueeffect", "{PotionEffectType}"});
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
        String team = checkForMissingTeamSpawn(list);
        if (team != null) {
            return team;
        }
        return checkForMissingTeamCustom(list, "rescue");
    }

    private void applyEffects(final Player player) {
        final String value = arena.getArenaConfig().getString(
                CFG.GOAL_RESCUE_RESCUEEFFECT);

        if (value.equalsIgnoreCase("none")) {
            return;
        }

        final String[] split = value.split("x");

        int amp = 1;

        if (split.length > 1) {
            try {
                amp = Integer.parseInt(split[1]);
            } catch (Exception e) {

            }
        }

        PotionEffectType pet = null;
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

    @Override
    public PACheck checkSetBlock(final PACheck res, final Player player, final Block block) {

        if (res.getPriority() > PRIORITY
                || !PAA_Region.activeSelections.containsKey(player.getName())) {
            return res;
        }

        ItemStack flagType = StringParser.getItemStackFromString(arena.getArenaConfig().getString(
                CFG.GOAL_FLAGS_FLAGTYPE));
        if (block.getType() != flagType.getType() || (flagType.getData().getData()>0 && flagType.getData().getData() != block.getData())) {
            return res;
        }

        if (!PVPArena.hasAdminPerms(player)
                && !(PVPArena.hasCreatePerms(player, arena))) {
            return res;
        }
        res.setPriority(this, PRIORITY); // success :)

        return res;
    }

    private void commit(final Arena arena, final String sTeam, final boolean win) {
        if (arena.realEndRunner != null) {
            arena.getDebugger().i("[R] already ending");
            return;
        }
        arena.getDebugger().i("[R] committing end: " + sTeam);
        arena.getDebugger().i("win: " + win);

        String winteam = sTeam;

        for (ArenaTeam team : arena.getTeams()) {
            if (team.getName().equals(sTeam) == win) {
                continue;
            }
            for (ArenaPlayer ap : team.getTeamMembers()) {

                ap.addLosses();
            /*
				arena.tpPlayerToCoordName(ap.get(), "spectator");
				ap.setTelePass(false);*/

                ap.setStatus(Status.LOST);
            }
        }
        for (ArenaTeam team : arena.getTeams()) {
            for (ArenaPlayer ap : team.getTeamMembers()) {
                if (!ap.getStatus().equals(Status.FIGHT)) {
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
        arena.realEndRunner = new EndRunnable(arena, arena.getArenaConfig().getInt(
                CFG.TIME_ENDCOUNTDOWN));
    }

    @Override
    public void commitCommand(final CommandSender sender, final String[] args) {
        if (args[0].equalsIgnoreCase("entitytype")) {
            if (args.length < 2) {
                arena.msg(
                        sender,
                        Language.parse(arena, MSG.ERROR_INVALID_ARGUMENT_COUNT,
                                String.valueOf(args.length), "2"));
                return;
            }

            try {

                EntityType type = EntityType.valueOf(args[1]);

                arena.getArenaConfig().set(CFG.GOAL_RESCUE_RESCUETYPE,
                        type.name());
                arena.getArenaConfig().save();
                arena.msg(sender, Language.parse(arena, MSG.GOAL_RESCUE_TYPESET,
                        CFG.GOAL_RESCUE_RESCUETYPE.toString()));
            } catch (Exception e) {
                arena.msg(sender,
                        Language.parse(arena, MSG.ERROR_ARGUMENT_TYPE, args[1], "ENTITYTYPE"));
            }

        } else if (args[0].equalsIgnoreCase("rescueeffect")) {

            // /pa [arena] rescueeffect SLOW 2
            if (args.length < 2) {
                arena.msg(
                        sender,
                        Language.parse(arena, MSG.ERROR_INVALID_ARGUMENT_COUNT,
                                String.valueOf(args.length), "2"));
                return;
            }

            if (args[1].equalsIgnoreCase("none")) {
                arena.getArenaConfig().set(CFG.GOAL_RESCUE_RESCUEEFFECT, args[1]);

                arena.getArenaConfig().save();
                arena.msg(
                        sender,
                        Language.parse(arena, MSG.SET_DONE,
                                CFG.GOAL_RESCUE_RESCUEEFFECT.getNode(), args[1]));
                return;
            }

            PotionEffectType pet = null;

            for (PotionEffectType x : PotionEffectType.values()) {
                if (x == null) {
                    continue;
                }
                if (x.getName().equalsIgnoreCase(args[1])) {
                    pet = x;
                    break;
                }
            }

            if (pet == null) {
                arena.msg(sender, Language.parse(arena,
                        MSG.ERROR_POTIONEFFECTTYPE_NOTFOUND, args[1]));
                return;
            }

            int amp = 1;

            if (args.length == 5) {
                try {
                    amp = Integer.parseInt(args[2]);
                } catch (Exception e) {
                    arena.msg(sender,
                            Language.parse(arena, MSG.ERROR_NOT_NUMERIC, args[2]));
                    return;
                }
            }
            final String value = args[1] + 'x' + amp;
            arena.getArenaConfig().set(CFG.GOAL_RESCUE_RESCUEEFFECT, value);

            arena.getArenaConfig().save();
            arena.msg(
                    sender,
                    Language.parse(arena, MSG.SET_DONE,
                            CFG.GOAL_RESCUE_RESCUEEFFECT.getNode(), value));

        } else if (args[0].contains("rescue")) {
            for (ArenaTeam team : arena.getTeams()) {
                final String sTeam = team.getName();
                if (args[0].contains(sTeam + "rescue")) {
                    flagName = args[0];
                    PAA_Region.activeSelections.put(sender.getName(), arena);

                    arena.msg(sender,
                            Language.parse(arena, MSG.GOAL_RESCUE_TOSET, flagName));
                }
            }
        }
    }

    @Override
    public void commitEnd(final boolean force) {
        if (arena.realEndRunner != null) {
            arena.getDebugger().i("[R] already ending");
            return;
        }
        arena.getDebugger().i("[R]");

        PAGoalEvent gEvent = new PAGoalEvent(arena, this, "");
        Bukkit.getPluginManager().callEvent(gEvent);
        ArenaTeam aTeam = null;

        for (ArenaTeam team : arena.getTeams()) {
            for (ArenaPlayer ap : team.getTeamMembers()) {
                if (ap.getStatus().equals(Status.FIGHT)) {
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

    @Override
    public boolean commitSetFlag(final Player player, final Block block) {

        arena.getDebugger().i("trying to set a flag", player);

        // command : /pa redrescue1
        // location: red1rescue:

        SpawnManager.setBlock(arena, new PABlockLocation(block.getLocation()),
                flagName);

        arena.msg(player, Language.parse(arena, MSG.GOAL_FLAGS_SET, flagName));

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

        final ArenaTeam flagTeam = getHeldFlagTeam(aPlayer.getName());
        final Entity e = getHeldFlag(aPlayer.getName());

        if (flagTeam == null) {
            return;
        }

        arena.broadcast(Language.parse(arena, MSG.GOAL_RESCUE_DROPPED, aPlayer
                .getArenaTeam().getColorCodeString()
                + aPlayer.getName()
                + ChatColor.YELLOW, flagTeam.getName() + ChatColor.YELLOW));

        takeFlag(flagTeam, false,
                SpawnManager.getBlockByExactName(arena, flagTeam.getName() + "rescue"), (Player) e.getVehicle(), e);

    }

    @Override
    public void displayInfo(CommandSender sender) {
        sender.sendMessage("rescueeffect: " +
                arena.getArenaConfig().getString(CFG.GOAL_RESCUE_RESCUEEFFECT));
        sender.sendMessage("rescuetype: " +
                arena.getArenaConfig().getString(CFG.GOAL_RESCUE_RESCUETYPE));
        sender.sendMessage("lives: " +
                arena.getArenaConfig().getInt(CFG.GOAL_RESCUE_LIVES));
        sender.sendMessage(StringParser.colorVar("mustbesafe",
                arena.getArenaConfig().getBoolean(CFG.GOAL_RESCUE_MUSTBESAFE)));
    }

    @Override
    public PACheck getLives(final PACheck res, final ArenaPlayer aPlayer) {
        if (res.getPriority() <= PRIORITY + 1000) {
            res.setError(
                    this,
                    String.valueOf(getLifeMap().containsKey(aPlayer.getArenaTeam()
                            .getName()) ? getLifeMap().get(aPlayer
                            .getArenaTeam().getName()) : 0));
        }
        return res;
    }

    private ArenaTeam getHeldFlagTeam(final String playerName) {
        for (Map.Entry<Entity, ArenaTeam> entityArenaTeamEntry : entityMap.entrySet()) {
            if (entityArenaTeamEntry.getKey().getVehicle() instanceof Player) {
                if ((entityArenaTeamEntry.getKey().getVehicle()).getName().equals(playerName)) {
                    return entityArenaTeamEntry.getValue();
                }
            }
        }
        return null;
    }

    private Entity getHeldFlag(String playerName) {
        for (Entity e : entityMap.keySet()) {
            if (e.getVehicle() instanceof Player) {
                if ((e.getVehicle()).getName().equals(playerName)) {
                    return e;
                }
            }
        }
        return null;
    }

    private ArenaTeam getHeldFlagTeam(final Player player) {
        if (player.getPassenger() == null) {
            return null;
        }

        arena.getDebugger().i("getting held FLAG of player " + player, player);

        if (entityMap.containsKey(player.getPassenger())) {
            ArenaTeam team = entityMap.get(player.getPassenger());
            arena.getDebugger().i("team " + team.getName() + " is in " + player.getName()
                    + "s hands", player);
            return team;
        }
        return null;
    }

    @Override
    public boolean hasSpawn(final String string) {
        for (String teamName : arena.getTeamNames()) {
            if (string.toLowerCase().equals(teamName.toLowerCase() + "rescue")) {
                return true;
            }
            if (string.toLowerCase().startsWith(
                    teamName.toLowerCase() + "spawn")) {
                return true;
            }

            if (arena.getArenaConfig().getBoolean(CFG.GENERAL_CLASSSPAWN)) {
                for (ArenaClass aClass : arena.getClasses()) {
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
                    .getInt(CFG.GOAL_RESCUE_LIVES));

            takeFlag(team, false,
                    SpawnManager.getBlockByExactName(arena, team.getName() + "rescue"), null, null);
        }
    }

    @Override
    public void parsePlayerDeath(final Player player,
                                 final EntityDamageEvent lastDamageCause) {

        final ArenaTeam flagTeam = getHeldFlagTeam(player);

        if (flagTeam == null) {
            return;
        }

        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());

        arena.broadcast(Language.parse(arena, MSG.GOAL_RESCUE_DROPPED, aPlayer
                        .getArenaTeam().colorizePlayer(player) + ChatColor.YELLOW,
                flagTeam.getColoredName() + ChatColor.YELLOW));

        takeFlag(flagTeam, false,
                SpawnManager.getBlockByExactName(arena, flagTeam.getName() + "rescue"), player, player.getPassenger());

    }

    @Override
    public void parseStart() {
        getLifeMap().clear();
        for (ArenaTeam team : arena.getTeams()) {
            if (team.getTeamMembers().size() > 0) {
                arena.getDebugger().i("adding team " + team.getName());
                // team is active
                getLifeMap().put(team.getName(),
                        arena.getArenaConfig().getInt(CFG.GOAL_RESCUE_LIVES, 3));
            }
            takeFlag(team, false,
                    SpawnManager.getBlockByExactName(arena, team.getName() + "rescue"), null, null);
        }
    }

    private boolean reduceLivesCheckEndAndCommit(final Arena arena, final String team) {

        arena.getDebugger().i("reducing lives of team " + team);
        if (getLifeMap().get(team) == null) {
            if (team.contains(":")) {
                final String realTeam = team.split(":")[1];
                final int iLives = getLifeMap().get(realTeam) - 1;
                if (iLives > 0) {
                    getLifeMap().put(realTeam, iLives);
                } else {
                    getLifeMap().remove(realTeam);
                    commit(arena, realTeam, true);
                    return true;
                }
            }
        } else {
            if (getLifeMap().get(team) != null) {
                final int iLives = getLifeMap().get(team) - 1;
                if (iLives > 0) {
                    getLifeMap().put(team, iLives);
                } else {
                    getLifeMap().remove(team);
                    commit(arena, team, false);
                    return true;
                }
            }
        }

        return false;
    }

    private void removeEffects(final Player player) {
        final String value = arena.getArenaConfig().getString(
                CFG.GOAL_RESCUE_RESCUEEFFECT);

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

    @Override
    public void reset(final boolean force) {
        getLifeMap().clear();
        entityMap.clear();
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
     * take/reset an arena flag
     *
     * @param arenaTeam       the team to set
     * @param take            true if take, else reset
     * @param paBlockLocation the location to take/reset
     */
    private void takeFlag(final ArenaTeam arenaTeam, final boolean take,
                          final PABlockLocation paBlockLocation, final Player taker, final Entity flag) {
        if (paBlockLocation == null) {
            PVPArena.instance.getLogger().severe("GoalRescue takeFlag location is null!");
            return;
        }

        if (take) {
            // normal -> player
            taker.setPassenger(flag);
        } else {
            // player -> remove
            // spawn new
            if (taker != null) {
                if (flag != null) {
                    taker.eject();
                    flag.teleport(paBlockLocation.toLocation().add(0, 1, 0));
                }
            } else if (flag != null) {
                if (flag.getVehicle() != null) {
                    flag.getVehicle().eject();
                }
                flag.teleport(paBlockLocation.toLocation().add(0, 1, 0));
            } else { // both null, create from scratch!
                EntityType type = EntityType.valueOf(arena.getArenaConfig().getString(CFG.GOAL_RESCUE_RESCUETYPE));
                Location pos = paBlockLocation.toLocation().add(0, 1, 0);
                Entity e = pos.getWorld().spawnEntity(pos, type);
                entityMap.put(e, arenaTeam);
            }

        }
    }

    @Override
    public Map<String, Double> timedEnd(final Map<String, Double> scores) {

        for (ArenaTeam team : arena.getTeams()) {
            double score = (getLifeMap().containsKey(team.getName()) ? getLifeMap()
                    .get(team.getName()) : 0);
            if (scores.containsKey(team.getName())) {
                scores.put(team.getName(), scores.get(team.getName()) + score);
            } else {
                scores.put(team.getName(), score);
            }
        }

        return scores;
    }

    @Override
    public void unload(final Player player) {
        disconnect(ArenaPlayer.parsePlayer(player.getName()));
        if (allowsJoinInBattle()) {
            arena.hasNotPlayed(ArenaPlayer.parsePlayer(player.getName()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRescueClaim(final EntityDamageByEntityEvent event) {
        arena.getDebugger().i("EDBEE");

        if (!entityMap.containsKey(event.getEntity())) {
            arena.getDebugger().i("EDBEE");
            return;
        }

        Entity damager = event.getDamager();

        if (damager instanceof Projectile) {
            Projectile pro = (Projectile) damager;
            ProjectileSource source = pro.getShooter();

            if (source instanceof LivingEntity) {
                damager = (Entity) source;
            }
        }

        if (!(damager instanceof Player)) {
            event.setCancelled(true);
            return;
        }


        final Player player = (Player) damager;

        if (!arena.hasPlayer(player)) {

            arena.getDebugger().i("external player damaging: NOPE", player);
            event.setCancelled(true);
            return;
        }

        final Block block = event.getEntity().getLocation().getBlock();

        arena.getDebugger().i("hostage hit!", player);

        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());

        boolean isRider = false;

        for (Entity e : entityMap.keySet()) {
            if (player.equals(e.getVehicle())) {
                isRider = true;
                break;
            }
        }

        if (isRider) {
            arena.getDebugger().i("already carries a hostage!", player);
            event.setCancelled(true);
            return;
        }
        final ArenaTeam pTeam = aPlayer.getArenaTeam();
        if (pTeam == null) {
            event.setCancelled(true);
            return;
        }

        for (Map.Entry<Entity, ArenaTeam> entityArenaTeamEntry : entityMap.entrySet()) {
            if (entityArenaTeamEntry.getKey().getVehicle() != null) {
                arena.getDebugger().i("taken!OUT! ", player);
                continue; // ignore riding hostages
            }
            if (entityArenaTeamEntry.getKey().equals(event.getEntity())) {
                // we found the hit entity
                if (entityArenaTeamEntry.getValue().equals(pTeam)) {
                    // same team. out!
                    arena.getDebugger().i("equals!OUT! ", player);
                    event.setCancelled(true);
                    attemptScore(player);
                    return;

                }
                ArenaTeam team = entityArenaTeamEntry.getValue();
                arena.broadcast(Language
                        .parse(arena, MSG.GOAL_FLAGS_GRABBED,
                                pTeam.colorizePlayer(player)
                                        + ChatColor.YELLOW,
                                team.getColoredName()
                                        + ChatColor.YELLOW));


                applyEffects(player);

                takeFlag(team, true,
                        new PABlockLocation(block.getLocation()), player, entityArenaTeamEntry.getKey());

            }
        }
    }

    private void attemptScore(final Player player) {

        arena.getDebugger().i("checking score", player);

        if (player.getPassenger() == null) {
            arena.getDebugger().i("no passenger", player);
        }

        ArenaTeam carry = entityMap.get(player.getPassenger());

        if (carry == null) {
            arena.getDebugger().i("no hostage passenger", player);
            return;
        }

        final ArenaPlayer aPlayer = ArenaPlayer.parsePlayer(player.getName());

        arena.getDebugger().i("player is at his flag", player);

        boolean isSafe = true;

        for (Map.Entry<Entity, ArenaTeam> entityArenaTeamEntry : entityMap.entrySet()) {
            if (entityArenaTeamEntry.getKey().getVehicle() != null && entityArenaTeamEntry.getValue().equals(aPlayer.getArenaTeam())) {
                isSafe = false;
                break;
            }
        }

        if (!isSafe) {
            arena.getDebugger().i("a hostage of the own team is taken!", player);

            if (arena.getArenaConfig().getBoolean(
                    CFG.GOAL_FLAGS_MUSTBESAFE)) {
                arena.getDebugger().i("cancelling", player);

                arena.msg(player,
                        Language.parse(arena, MSG.GOAL_RESCUE_NOTSAFE));
                return;
            }
        }

        arena.getDebugger().i("the hostage belongs to team " + carry.getName(), player);

        try {

            arena.broadcast(Language.parse(arena,
                    MSG.GOAL_FLAGS_BROUGHTHOME, aPlayer.getArenaTeam().colorizePlayer(player)
                            + ChatColor.YELLOW,
                    carry.getColoredName()
                            + ChatColor.YELLOW, String
                            .valueOf(getLifeMap().get(carry.getName()) - 1)));

        } catch (Exception e) {
            Bukkit.getLogger().severe(
                    "[PVP Arena] team unknown/no lives: " + carry.getName());
            e.printStackTrace();
        }
        takeFlag(arena.getTeam(carry.getName()), false,
                SpawnManager.getBlockByExactName(arena, carry.getName() + "rescue"), player, player.getPassenger());
        removeEffects(player);

        reduceLivesCheckEndAndCommit(arena, carry.getName());

        PAGoalEvent gEvent = new PAGoalEvent(arena, this, "trigger:" + player.getName());
        Bukkit.getPluginManager().callEvent(gEvent);
    }

    @EventHandler
    public void onEntityTargetEvent(final EntityTargetEvent event) {

        //TODO hook things to block, like... creepers igniting? villagers being traded? what?

        if (entityMap.containsKey(event.getEntity())) {
            event.setCancelled(true);
        }
    }
}
