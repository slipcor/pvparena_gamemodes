package net.slipcor.pvparena.arenas.tank;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaClass;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaPlayer.Status;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.StringParser;
import net.slipcor.pvparena.listeners.PlayerListener;
import net.slipcor.pvparena.managers.Arenas;
import net.slipcor.pvparena.managers.Inventories;
import net.slipcor.pvparena.managers.Spawns;
import net.slipcor.pvparena.managers.Teams;
import net.slipcor.pvparena.neworder.ArenaType;
import net.slipcor.pvparena.runnables.EndRunnable;

public class Tank extends ArenaType {
	
	public Tank() {
		super("tank");
	}
	
	static HashMap<Arena, String> tanks = new HashMap<Arena, String>();
	HashMap<String, Integer> status = new HashMap<String, Integer>();
	HashMap<Integer, ItemStack[]> items = new HashMap<Integer, ItemStack[]>();
	
	@Override
	public String version() {
		return "v0.8.11.3";
	}

	@Override
	public void addDefaultTeams(YamlConfiguration config) {
		if (arena.cfg.get("teams") == null) {
			arena.cfg.getYamlConfiguration().addDefault("teams.free",
					ChatColor.WHITE.name());
		}
	}

	@Override
	public boolean checkAndCommit() {
		db.i("[TANK]");
		
		HashSet<ArenaPlayer> aPlayers = new HashSet<ArenaPlayer>();
		
		for (ArenaPlayer p : arena.getPlayers()) {
			if (p.getStatus().equals(Status.FIGHT)) {
				aPlayers.add(p);
			}
		}

		if (aPlayers.size() > 1) {
			return false;
		}

		EndRunnable er = new EndRunnable(arena, arena.cfg.getInt("goal.endtimer"),0);
		arena.REALEND_ID = Bukkit.getScheduler().scheduleSyncRepeatingTask(PVPArena.instance,
				er, 20L, 20L);
		er.setId(arena.REALEND_ID);
		return true;
	}

	@Override
	public String checkSpawns(Set<String> list) {
		if (!list.contains("lounge"))
			return "lounge not set";
		Iterator<String> iter = list.iterator();
		int spawns = 0;
		boolean tank = false;
		while (iter.hasNext()) {
			String s = iter.next();
			if (s.startsWith("spawn")) {
				spawns++;
			}
			if (s.startsWith("tank")) {
				tank = true;
			}
		}
		if (spawns > 3 && tank) {
			return null;
		}
		
		if (!tank) {
			return "tank not set";
		}
		
		return "not enough spawns (" + spawns + ")";
	}
	
	@Override
	public void commitCommand(Arena arena, CommandSender sender, String[] args) {
		if (!(sender instanceof Player)) {
			Language.parse("onlyplayers");
			return;
		}
		
		Player player = (Player) sender;
		
		if (!PVPArena.hasAdminPerms(player)
				&& !(PVPArena.hasCreatePerms(player, arena))) {
			Arenas.tellPlayer(player,
					Language.parse("nopermto", Language.parse("admin")), arena);
			return;
		}
		
		String cmd = args[0];
		if (cmd.equalsIgnoreCase("lounge")) {
			Spawns.setCoords(arena, player, "lounge");
			Arenas.tellPlayer(player, Language.parse("setlounge"));
			return;
		}

		if (cmd.startsWith("spawn") || cmd.startsWith("tank")) {
			Spawns.setCoords(arena, player, cmd);
			Arenas.tellPlayer(player, Language.parse("setspawn", cmd));
			return;
		}
	}

	@Override
	public void configParse() {
		db.i("FreeFight Arena default overrides");

		arena.cfg.set("game.teamKill", true);
		arena.cfg.set("join.manual", false);
		arena.cfg.set("join.random", true);
		arena.cfg.set("game.woolHead", false);
		arena.cfg.set("join.forceeven", false);
		arena.cfg.set("arenatype.randomSpawn", true);
		arena.cfg.set("teams", null);
		arena.cfg.set("teams.free", "WHITE");
		
		if (arena.cfg.getYamlConfiguration().get("tankitems") == null) {
			ArrayList<String> items = new ArrayList<String>();
			items.add("298,299,300,301,268"); // leather
			items.add("302,303,304,305,272"); // chain
			items.add("314,315,316,317,267"); // gold
			items.add("306,307,308,309,276"); // iron
			items.add("310,311,312,313,276"); // diamond
			arena.cfg.set("tankitems", items);
		}
		arena.cfg.save();
		
		List<String> lItems = arena.cfg.getStringList("tankitems", new ArrayList<String>());
		int i = 0;
		for (String s : lItems) {
			items.put(i++, StringParser.getItemStacksFromString(s));
		}
	}

	public HashSet<String> getAddedSpawns() {
		HashSet<String> result = new HashSet<String>();

		result.add("spawn");
		result.add("lounge");
		result.add("tank");
		
		return result;
	}

	private void getFreeSpawn(Player player, String string) {
		// calculate a free spawn, if applicable
		db.i(arena.playerCount + " calculating free spawn for " + player.getName());
		if (arena.playerCount < 1) {
			// arena empty, randomly put player
			arena.tpPlayerToCoordName(player, string);
			return;
		}

		HashSet<Location> spawns = Spawns.getSpawns(arena, "free");
		if (arena.playerCount >= spawns.size()) {
			// full anyways, randomly put player
			arena.tpPlayerToCoordName(player, string);
			return;
		}

		// calculate "most free"

		for (Location loc : spawns) {
			db.i("checking loc: " + loc.toString());
			boolean possible = false;
			for (ArenaPlayer ap : arena.getPlayers()) {
				if (ap.getStatus().equals(Status.FIGHT)) {
					if (ap.get().getLocation().distance(loc) < 1) {
						db.i(player.getName() + " would be too near: " + ap.getName());
						possible = false;
						continue;
					} else {
						db.i(player.getName() + " would be " + ap.get().getLocation().distance(loc) + " blocks away from " + ap.getName());
						possible = true;
					}
				}
			}
			if (!possible) {
				continue;
			}
			db.i("intelligently teleporting " + player + " to spawn");
			

			ArenaPlayer ap = ArenaPlayer.parsePlayer(player);
			
			ap.setTelePass(true);
			player.teleport(loc);
			ap.setTelePass(false);
			return;
		}
	}
	
	@Override
	public String guessSpawn(String place) {
		if (!place.contains("spawn")) {
			db.i("place not found!");
			return null;
		}
		// no exact match: assume we have multiple spawnpoints
		HashMap<Integer, String> locs = new HashMap<Integer, String>();
		int i = 0;

		db.i("searching for team spawns");

		HashMap<String, Object> coords = (HashMap<String, Object>) arena.cfg
				.getYamlConfiguration().getConfigurationSection("spawns")
				.getValues(false);
		for (String name : coords.keySet()) {
			if (name.startsWith(place)) {
				locs.put(i++, name);
				db.i("found match: " + name);
			}
		}

		if (locs.size() < 1) {
			return null;
		}
		Random r = new Random();

		place = locs.get(r.nextInt(locs.size()));
		
		return place;
	}

	@Override
	public void initiate() {
		arena.playerCount = 0;
		arena.cfg.set("game.teamKill", Boolean.valueOf(true));
		for (ArenaTeam team : arena.getTeams()) {
			for (ArenaPlayer ap : team.getTeamMembers()) {
				getFreeSpawn(ap.get(), "spawn");
				ap.setStatus(Status.FIGHT);
				arena.lives.put(ap.getName(),
						arena.cfg.getInt("game.lives", 3));
				resetArmor(ap.get());
				arena.playerCount++;
			}
		}
	}
	
	@Override
	public void initLanguage(YamlConfiguration config) {
		config.addDefault("lang.youjoinedtank",
				"Welcome to the Tank Arena");
		config.addDefault("lang.playerjoinedtank",
				"%1% has joined the Tank Arena");
		config.addDefault("lang.tankmode",
				"TANK MODE! Everyone kill %1%, the tank!");
		config.addDefault("lang.tankwon",
				"The tank has won! Congratulations to %1%!");
		config.addDefault("lang.tankdown",
				"The tank is down!");
	}
	
	@Override
	public boolean isFreeForAll() {
		return true;
	}

	@Override
	public boolean parseCommand(String cmd) {
		return (cmd.equalsIgnoreCase("lounge") || cmd.startsWith("spawn")) || cmd.startsWith("tank");
	}
	
	@Override
	public void parseRespawn(Player respawnPlayer,
			ArenaTeam respawnTeam, int lives, DamageCause cause, Entity damager) {

		if (tanks.containsKey(arena)) {
			if (respawnPlayer.getName().equals(tanks.get(arena))) {
				tanks.remove(respawnPlayer.getName());
				arena.playerLeave(respawnPlayer, "lose");
				arena.tellEveryone(Language.parse("tankdown"));
				EndRunnable er = new EndRunnable(arena, arena.cfg.getInt("goal.endtimer"),0);
				arena.REALEND_ID = Bukkit.getScheduler().scheduleSyncRepeatingTask(PVPArena.instance,
						er, 20L, 20L);
				er.setId(arena.REALEND_ID);
				return;
			}
			PlayerListener.commitPlayerDeath(arena, respawnPlayer, respawnPlayer.getLastDamageCause());
			arena.tellEveryone(Language.parse("tankwon"));
		} else {
			// stockup
			resetArmor(respawnPlayer);
			
			if ((damager instanceof Player) && upgradeArmor((Player) damager)) {
				arena.tellEveryone(Language.parse("killedby",
						respawnTeam.colorizePlayer(respawnPlayer) + ChatColor.YELLOW,
						arena.parseDeathCause(respawnPlayer, cause, damager)));
				return;
			}
		}
		
		arena.tellEveryone(Language.parse("killedby",
				respawnTeam.colorizePlayer(respawnPlayer) + ChatColor.YELLOW,
				arena.parseDeathCause(respawnPlayer, cause, damager)));
		
		arena.tpPlayerToCoordName(respawnPlayer, "spawn");
	}
	
	private boolean upgradeArmor(Player player) {
		int playerStatus = 1;
		if (status.containsKey(player.getName())) {
			playerStatus = status.get(player.getName()) + 1;
		}
		setArmor(player, playerStatus);
		status.put(player.getName(), playerStatus);
		if (playerStatus == items.size() - 1) {
			// player now is tank
			tanks.put(arena, player.getName());
			
			startTankMode(player);
			return true;
		}
		return false;
	}

	private void startTankMode(Player tank) {
		
		ArenaTeam tankTeam = new ArenaTeam("tank", "PINK");
		
		Teams.addTeam(arena, tankTeam);
		ArenaPlayer apTank = null;
		
		for (ArenaPlayer ap : arena.getPlayers()) {
			if (ap.getStatus().equals(Status.FIGHT)) {
				if (ap.get().equals(tank)) {
					arena.tpPlayerToCoordName(ap.get(), "tank");
					apTank = ap;
				} else {
					Inventories.givePlayerFightItems(arena, ap.get());
					arena.tpPlayerToCoordName(ap.get(), "spawn");
				}
			}
		}
		
		for (ArenaTeam team : arena.getTeams()) {
			if (team.getName().equals("free")) {
				team.remove(apTank);
			} else {
				team.add(apTank);
			}
		}
		arena.tellEveryone(Language.parse("tankmode", tank.getName()));
		arena.cfg.set("game.teamKill", Boolean.valueOf(false));
	}

	private void resetArmor(Player player) {
		status.put(player.getName(), 0);
		setArmor(player, 0);
	}

	private void setArmor(Player player, int i) {
		Inventories.clearInventory(player);
		ArenaClass.equip(player, items.get(i));
	}

	@Override
	public int ready(Arena arena) {
		db.i("ready(): reading playerteammap");
		for (ArenaTeam team : arena.getTeams()) {
			if (team.getTeamMembers().size() < 1) {
				db.i("skipping TEAM " + team.getName());
				continue;
			}
			db.i("TEAM " + team.getName());
			if (arena.cfg.getInt("ready.minTeam") > 0
					&& team.getTeamMembers().size() < arena.cfg
							.getInt("ready.minTeam")) {
				return -3;
			}
		}
		return 1;
	}
	
	@Override
	public void reset(boolean force) {
		tanks.clear();
		status.clear();
	}
	
	@Override
	public void timed() {
		int i;
		int max = -1;

		HashSet<String> result = new HashSet<String>();
		
		if (tanks.containsKey(arena)) {
			for (ArenaPlayer ap : arena.getPlayers()) {
				if (ap.getName().equals(tanks.get(arena)) || !ap.getStatus().equals(Status.FIGHT)) {
					continue;
				}
				ap.losses++;
				arena.tpPlayerToCoordName(ap.get(), "spectator");
			}
			String name = tanks.get(arena);
			result.add(name);
			PVPArena.instance.getAmm().announceWinner(arena, name);
			arena.tellEveryone(Language.parse("playerhaswon",
					"§f" + name + "§e"));
		} else {

			for (String sPlayer : arena.lives.keySet()) {
				i = arena.lives.get(sPlayer);
	
				if (i > max) {
					result = new HashSet<String>();
					result.add(sPlayer);
					max = i;
				} else if (i == max) {
					result.add(sPlayer);
				}
	
			}
	
			for (ArenaPlayer p : arena.getPlayers()) {
				if (!p.getStatus().equals(Status.FIGHT)) {
					continue;
				}
				if (!result.contains(p.getName())) {
					p.losses++;
					arena.tpPlayerToCoordName(p.get(), "spectator");
				} else {
					PVPArena.instance.getAmm().announceWinner(arena, p.getName());
					arena.tellEveryone(Language.parse("playerhaswon",
							"§f" + p.getName() + "§e"));
				}
			}
		}
		PVPArena.instance.getAmm().timedEnd(arena, result);
		EndRunnable er = new EndRunnable(arena, arena.cfg.getInt("goal.endtimer"),0);
		arena.REALEND_ID = Bukkit.getScheduler().scheduleSyncRepeatingTask(PVPArena.instance,
				er, 20L, 20L);
		er.setId(arena.REALEND_ID);
	}
}
