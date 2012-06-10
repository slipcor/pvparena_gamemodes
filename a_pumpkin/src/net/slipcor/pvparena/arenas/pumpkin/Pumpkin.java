package net.slipcor.pvparena.arenas.pumpkin;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.arena.ArenaPlayer.Status;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.StringParser;
import net.slipcor.pvparena.managers.Arenas;
import net.slipcor.pvparena.managers.Spawns;
import net.slipcor.pvparena.managers.Teams;
import net.slipcor.pvparena.neworder.ArenaType;
import net.slipcor.pvparena.runnables.EndRunnable;

public class Pumpkin extends ArenaType {
	/**
	 * TeamName => PlayerName
	 */
	public HashMap<String, String> paTeamFlags = null;
	public HashMap<Location, String> paFlags = null;
	public HashMap<String, ItemStack> paHeadGears = null;
	
	public Pumpkin() {
		super("pumpkin");
	}
	
	@Override
	public String version() {
		return "v0.8.8.0";
	}

	@Override
	public boolean allowsJoinInBattle() {
		return arena.cfg.getBoolean("join.inbattle");
	}

	@Override
	public boolean checkAndCommit() {
db.i("[FLAG]");
		
		ArenaPlayer activePlayer = null;
		for (ArenaPlayer p : arena.getPlayers()) {
			if (p.getStatus().equals(Status.FIGHT)) {
				if (activePlayer != null) {
					db.i("more than one player active => no end :p");
					return false;
				}
				activePlayer = p;
			}
		}
		
		if (activePlayer == null) {
			commit("$%&/", true);
			return false;
		}
		
		ArenaTeam team = Teams.getTeam(arena, activePlayer);
		
		if (team == null) {
			commit("$%&/", true);
			return false;
		}
		
		commit(team.getName(), true);
		return true;
	}
	
	@Override
	public void checkEntityDeath(Player player) {
		db.i("checking death in a pumpkin arena");

		ArenaTeam flagTeam = Teams.getTeam(arena, getHeldFlagTeam(player.getName()));
		if (flagTeam != null) {
			ArenaPlayer ap = ArenaPlayer.parsePlayer(player);
			arena.tellEveryone(
					Language.parse(
							"pumpkinsave",
							Teams.getTeam(arena, ap).colorizePlayer(player),
							Teams.getTeam(arena, ap).getName()
									+ ChatColor.YELLOW,
							flagTeam.colorize() + ChatColor.YELLOW));
			paTeamFlags.remove(flagTeam.getName());
			if (paHeadGears != null
					&& paHeadGears.get(player.getName()) != null) {
				player.getInventory().setHelmet(
						paHeadGears.get(player.getName()).clone());
				paHeadGears.remove(player.getName());
			}

			takeFlag(flagTeam.getColor().name(), false,
					Spawns.getCoords(arena, flagTeam.getName() + "pumpkin"));

		}
	}
	
	@Override
	public void checkInteract(Player player, Block block) {
		if (block == null) {
			return;
		}
		db.i("checking interact");

		if (!block.getType().equals(Material.PUMPKIN)) {
			db.i("pumpkin & not pumpkin");
			return;
		}
		
		db.i("pumpkin click!");

		Vector vLoc;
		String sTeam;
		Vector vFlag = null;
		ArenaPlayer ap = ArenaPlayer.parsePlayer(player);

		if (paTeamFlags.containsValue(player.getName())) {
			db.i("player " + player.getName() + " has got a pumpkin");
			vLoc = block.getLocation().toVector();
			sTeam = Teams.getTeam(arena, ap).getName();
			db.i("block: " + vLoc.toString());
			if (Spawns.getSpawns(arena, sTeam + "pumpkin").size() > 0) {
				vFlag = Spawns.getNearest(Spawns.getSpawns(arena, sTeam + "pumpkin"), player.getLocation()).toVector();
			} else {
				db.i(sTeam + "pumpkin = null");
			}

			db.i("player is in the team " + sTeam);
			if ((vFlag != null && vLoc.distance(vFlag) < 2)) {

				db.i("player is at his " + "pumpkin");

				if (paTeamFlags.containsKey(sTeam)) {
					db.i("the pumpkin of the own team is taken!");

					if (arena.cfg.getBoolean("game.mustbesafe")) {
						db.i("cancelling");

						Arenas.tellPlayer(player,
								Language.parse("pumpkinnotsafe"));
						return;
					}
				}

				String flagTeam = getHeldFlagTeam(player.getName());

				db.i("the pumpkin belongs to team " + flagTeam);

				try {

					arena.tellEveryone(Language.parse("pumpkinhomeleft",
							Teams.getTeam(arena, sTeam).colorizePlayer(player)
									+ ChatColor.YELLOW,
									Teams.getTeam(arena, flagTeam).colorize() + ChatColor.YELLOW,
							String.valueOf(arena.lives.get(flagTeam) - 1)));
					paTeamFlags.remove(flagTeam);
				} catch (Exception e) {
					Bukkit.getLogger().severe(
							"[PVP Arena] team unknown/no lives: " + flagTeam);
				}

				takeFlag(Teams.getTeam(arena, flagTeam).getColor().name(), false,
						Spawns.getCoords(arena, flagTeam + "pumpkin"));
				player.getInventory().setHelmet(
						paHeadGears.get(player.getName()).clone());
				paHeadGears.remove(player.getName());

				reduceLivesCheckEndAndCommit(flagTeam);
			}
		} else {
			for (ArenaTeam team : arena.getTeams()) {
				String aTeam = team.getName();
				ArenaTeam pTeam = Teams.getTeam(arena, ap);
				
				if (aTeam.equals(pTeam.getName()))
					continue;
				if (team.getTeamMembers().size() < 1)
					continue; // dont check for inactive teams
				if (paTeamFlags.containsKey(aTeam)) {
					continue; // already taken
				}
				db.i("checking for pumpkin of team " + aTeam);
				vLoc = block.getLocation().toVector();
				db.i("block: " + vLoc.toString());
				if (Spawns.getSpawns(arena, aTeam + "pumpkin").size() > 0) {
					vFlag = Spawns.getNearest(Spawns.getSpawns(arena, aTeam + "pumpkin"), player.getLocation()).toVector();
				}
				if ((vFlag != null) && (vLoc.distance(vFlag) < 2)) {
					db.i("pumpkin found!");
					db.i("vFlag: " + vFlag.toString());
					arena.tellEveryone(Language.parse("pumpkingrab",
							pTeam.colorizePlayer(player)
									+ ChatColor.YELLOW,
							team.colorize() + ChatColor.YELLOW));

					try {
						paHeadGears.put(player.getName(), player
								.getInventory().getHelmet().clone());
					} catch (Exception e) {

					}
					ItemStack is = block.getState().getData().toItemStack()
								.clone();
						player.getInventory().setHelmet(is);

					takeFlag(team.getColor().name(), true,
							block.getLocation());

					paTeamFlags.put(aTeam, player.getName());
					return;
				}
			}
		}
	}
	
	@Override
	protected boolean checkSetFlag(Player player, Block block) {
		if (Arena.regionmodify.equals("")) {
			return false;
		}
		setFlag(player, block);
		if (Arena.regionmodify.equals("")) {
			return true; // success :)
		}
		return false;
	}
	
	@Override
	public String checkSpawns(Set<String> list) {
		
		for (ArenaTeam team : arena.getTeams()) {
			String sTeam = team.getName();
			if (!list.contains(team + "pumpkin")) {
				boolean found = false;
				for (String s : list) {
					if (s.startsWith(sTeam) && s.endsWith("pumpkin")) {
						found = true;
						break;
					}
				}
				if (!found)
					return team.getName() + "pumpkin not set";
			}
		}
		return super.checkSpawns(list);
	}
	

	/**
	 * [FLAG] commit the arena end
	 * 
	 * @param sTeam
	 *            the team name
	 * @param win
	 *            winning team?
	 */
	private void commit(String sTeam, boolean win) {
		db.i("[FLAG] committing end: " + sTeam);
		db.i("win: " + String.valueOf(win));

		String winteam = sTeam;

		for (ArenaTeam team : arena.getTeams()) {
			if (team.getName().equals(sTeam) == win) {
				continue;
			}
			for (ArenaPlayer ap : team.getTeamMembers()) {

				ap.losses++;
				arena.tpPlayerToCoordName(ap.get(), "spectator");
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

		if (Teams.getTeam(arena, winteam) != null) {
			PVPArena.instance.getAmm().announceWinner(arena,
					Language.parse("teamhaswon", "Team " + winteam));
			arena.tellEveryone(
					Language.parse("teamhaswon", Teams.getTeam(arena, winteam)
							.getColor() + "Team " + winteam));
		}

		arena.lives.clear();
		EndRunnable er = new EndRunnable(arena, arena.cfg.getInt("goal.endtimer"),0);
		arena.REALEND_ID = Bukkit.getScheduler().scheduleSyncRepeatingTask(PVPArena.instance,
				er, 20L, 20L);
		er.setId(arena.REALEND_ID);
	}
	
	@Override
	public void commitCommand(Arena arena, CommandSender sender, String[] args) {
		if (!(sender instanceof Player)) {
			Language.parse("onlyplayers");
			return;
		}
		
		System.out.print(StringParser.parseArray(args));
		
		Player player = (Player) sender;
		
		if (!PVPArena.hasAdminPerms(player)
				&& !(PVPArena.hasCreatePerms(player, arena))) {
			Arenas.tellPlayer(player,
					Language.parse("nopermto", Language.parse("admin")), arena);
			return;
		}
		
		if (args[0].startsWith("spawn") || args[0].equals("spawn")) {
			Arenas.tellPlayer(sender, Language.parse("errorspawnfree", args[0]),
					arena);
			return;
		}

		if (args[0].contains("spawn")) {
			String[] split = args[0].split("spawn");
			String sName = split[0];
			if (Teams.getTeam(arena, sName) == null) {
				Arenas.tellPlayer(sender, Language.parse("arenateamunknown", sName), arena);
				return;
			}

			Spawns.setCoords(arena, player, args[0]);
			Arenas.tellPlayer(player, Language.parse("setspawn", sName), arena);
			return;
		}
		
		if (args[0].equals("lounge")) {
			Arenas.tellPlayer(sender, Language.parse("errorloungefree", args[0]),
					arena);
			return;
		}

		if (args[0].contains("lounge")) {
			String[] split = args[0].split("lounge");
			String sName = split[0];
			if (Teams.getTeam(arena, sName) == null) {
				Arenas.tellPlayer(sender, Language.parse("arenateamunknown", sName), arena);
				return;
			}

			Spawns.setCoords(arena, player, args[0]);
			Arenas.tellPlayer(player, Language.parse("loungeset", sName), arena);
			return;
		}
		Arena.regionmodify = arena.name + ":" + args[0];
		Arenas.tellPlayer(sender, Language.parse("tosetpumpkin", args[0]));
	}
	
	@Override
	public void configParse() {
		paTeamFlags = new HashMap<String, String>();
		paHeadGears = new HashMap<String, ItemStack>();

		arena.cfg.getYamlConfiguration().addDefault("pumpkin.replace", Integer.valueOf(Material.JACK_O_LANTERN.getId()));
		arena.cfg.getYamlConfiguration().options().copyDefaults(true);
	}

	protected short getFlagOverrideTeamShort(String team) {
		if (arena.cfg.get("flagColors." + team) == null) {

			return StringParser.getColorDataFromENUM(Teams.getTeam(arena, team).getColor().name());
		}
		return StringParser.getColorDataFromENUM(arena.cfg
				.getString("flagColors." + team));
	}
	
	@Override
	public int getLives(Player defender) {
		ArenaPlayer ap = ArenaPlayer.parsePlayer(defender);
		ArenaTeam team = Teams.getTeam(arena,ap);
		return arena.lives.get(team.getName());
	}

	/**
	 * get the team name of the flag a player holds
	 * 
	 * @param player
	 *            the player to check
	 * @return a team name
	 */
	private String getHeldFlagTeam(String player) {
		db.i("getting held FLAG of player " + player);
		for (String sTeam : paTeamFlags.keySet()) {
			db.i("team " + sTeam + " is in " + paTeamFlags.get(sTeam)
					+ "s hands");
			if (player.equals(paTeamFlags.get(sTeam))) {
				return sTeam;
			}
		}
		return null;
	}
	
	@Override
	public String guessSpawn(String place) {
		// no exact match: assume we have multiple spawnpoints
		HashMap<Integer, String> locs = new HashMap<Integer, String>();
		int i = 0;

		db.i("searching for team spawns: " + place);

		HashMap<String, Object> coords = (HashMap<String, Object>) arena.cfg
				.getYamlConfiguration().getConfigurationSection("spawns")
				.getValues(false);
		for (String name : coords.keySet()) {
			if (name.startsWith(place)) {
				locs.put(i++, name);
				db.i("found match: " + name);
			}
			if (name.endsWith("pumpkin")) {
				for (ArenaTeam team : arena.getTeams()) {
					String sTeam = team.getName();
					if (name.startsWith(sTeam) && place.startsWith(sTeam)) {
						locs.put(i++, name);
						db.i("found match: " + name);
					}
				}
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
		arena.lives.clear();
		for (ArenaTeam team : arena.getTeams()) {
			if (team.getTeamMembers().size() > 0) {
				arena.lives.put(team.getName(), arena.cfg.getInt("game.lives", 3));
				db.i("TEAM LIVES: " + team.getName());
			}
			takeFlag(team.getColor().name(), false,
					Spawns.getCoords(arena, team.getName() + "pumpkin"));
		}
	}
	
	@Override
	public void initLanguage(YamlConfiguration config) {
		config.addDefault("lang.killedby", "%1% has been killed by %2%!");
		config.addDefault("lang.pumpkinhomeleft",
				"Player %1% brought home the pumpkin of team %2%! Lives left: %3%");
		config.addDefault("lang.pumpkingrab",
				"Player %1% grabbed the pumpkin of team %2%!");
		config.addDefault("lang.pumpkinsave",
				"Player %1% dropped the pumpkin of team %2%!");
		config.addDefault("lang.setpumpkin", "Pumpkin set: %1%");
		config.addDefault("lang.tosetpumpkin", "Pumpkin to set: %1%");
		config.addDefault("lang.pumpkinnotsafe",
				"Your pumpkin is taken! Cannot bring back an enemy pumpkin!'");
	}

	@Override
	public boolean parseCommand(String s) {
		if (s.contains("spawn")) {
			String[] split = s.split("spawn");
			String sName = split[0];
			if (Teams.getTeam(arena, sName) == null) {
				return false;
			}
			return true;
		}
		if (s.contains("lounge")) {
			String[] split = s.split("lounge");
			String sName = split[0];
			if (Teams.getTeam(arena, sName) == null) {
				return false;
			}
			return true;
		}
		if (!s.contains("pumpkin")) {
			return false;
		}
		String sName = s.replace("pumpkin", "");


		for (ArenaTeam team : arena.getTeams()) {
			String sTeam = team.getName();
			if (sName.startsWith(sTeam)) {
				return true;
			}
		}
		
		return false;
	}

	@Override
	public void parseRespawn(Player respawnPlayer, ArenaTeam respawnTeam,
			int lives, DamageCause cause, Entity damager) {
		arena.tellEveryone(Language.parse("killedby", respawnTeam.
				colorizePlayer(respawnPlayer) + ChatColor.YELLOW,
				arena.parseDeathCause(respawnPlayer, cause, damager)));
		arena.tpPlayerToCoordName(respawnPlayer, respawnTeam.getName() + "spawn");

		checkEntityDeath(respawnPlayer);
	}
	
	@Override
	public int reduceLives(Player player, int lives) {
		return lives;
	}

	@Override
	public boolean reduceLivesCheckEndAndCommit(String team) {
		
		db.i("reducing lives of team " + team);
		if (arena.lives.get(team) != null) {
			int i = arena.lives.get(team) - 1;
			if (i > 0) {
				arena.lives.put(team, i);
			} else {
				arena.lives.remove(team);
				commit(team, false);
				return true;
			}
		}
		return false;
	}

	private void setFlag(Player player, Block block) {
		if (block == null) {
			return;
		}
		if (!block.getType().equals(Material.PUMPKIN)) {
			return;
		}

		db.i("trying to set a pumpkin");
		
		if (!PVPArena.hasAdminPerms(player)
				&& !(PVPArena.hasCreatePerms(player, arena))) {
			return;
		}

		String sName = Arena.regionmodify.replace(arena.name + ":", "");

		// command : /pa redflag1
		// location: red1flag:

		Spawns.setCoords(arena, block.getLocation(), sName + "pumpkin");

		Arenas.tellPlayer(player, Language.parse("setpumpkin", sName));

		Arena.regionmodify = "";
	}
	
	@Override
	public void reset(boolean force) {
		if (paTeamFlags != null) {
			paTeamFlags.clear();
		}
		if (paHeadGears != null) {
			paHeadGears.clear();
		}
	}

	private void takeFlag(String flagColor, boolean take,
			Location lBlock) {
		if (take) {
			lBlock.getBlock().setType(Material.getMaterial(arena.cfg.getInt("pumpkin.replace")));
		} else {
			lBlock.getBlock().setType(Material.PUMPKIN);
		}
	}

	/**
	 * hook into the timed end
	 */
	@Override
	public void timed() {
		int i;

		int max = -1;
		HashSet<String> result = new HashSet<String>();
		db.i("timed end!");

		for (String sTeam : arena.lives.keySet()) {
			i = arena.lives.get(sTeam);

			if (i > max) {
				result = new HashSet<String>();
				result.add(sTeam);
				max = i;
			} else if (i == max) {
				result.add(sTeam);
			}

		}

		for (ArenaTeam team : arena.getTeams()) {
			if (result.contains(team.getName())) {
				PVPArena.instance.getAmm().announceWinner(arena,
						Language.parse("teamhaswon", "Team " + team.getName()));
				arena.tellEveryone(Language.parse("teamhaswon", team.getColor()
						+ "Team " + team.getName()));
			}
			for (ArenaPlayer p : arena.getPlayers()) {
				if (!p.getStatus().equals(Status.FIGHT)) {
					continue;
				}
				if (!result.contains(team.getName())) {
					p.losses++;
					arena.tpPlayerToCoordName(p.get(), "spectator");
				}
			}
		}

		PVPArena.instance.getAmm().timedEnd(arena, result);
		EndRunnable er = new EndRunnable(arena, arena.cfg.getInt("goal.endtimer"),0);
		arena.REALEND_ID = Bukkit.getScheduler().scheduleSyncRepeatingTask(PVPArena.instance,
				er, 20L, 20L);
		er.setId(arena.REALEND_ID);
	}
	
	@Override
	public void unload(Player player) {
		String flag = this.getHeldFlagTeam(player.getName());
		ArenaPlayer ap = ArenaPlayer.parsePlayer(player);
		if (flag != null) {
			ArenaTeam flagTeam = Teams.getTeam(arena, flag);
			arena.tellEveryone(Language.parse("flagsave",
					Teams.getTeam(arena, ap).colorizePlayer(player), Teams
							.getTeam(arena, ap).getName() + ChatColor.YELLOW,
					flagTeam.colorize() + ChatColor.YELLOW));
			paTeamFlags.remove(flag);

			takeFlag(flagTeam.getColor().name(), false,
					Spawns.getCoords(arena, flagTeam.getName() + "flag"));
		}
	}

	@Override
	public boolean usesFlags() {
		return true;
	}
}