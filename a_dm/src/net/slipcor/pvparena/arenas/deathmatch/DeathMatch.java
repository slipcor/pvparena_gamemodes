package net.slipcor.pvparena.arenas.deathmatch;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.arena.ArenaPlayer.Status;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.managers.Teams;
import net.slipcor.pvparena.neworder.ArenaType;
import net.slipcor.pvparena.runnables.EndRunnable;

public class DeathMatch extends ArenaType {	
	public DeathMatch() {
		super("dm");
	}
	
	@Override
	public String version() {
		return "v0.8.10.0";
	}
	
	@Override
	public void addDefaultTeams(YamlConfiguration config) {
		config.addDefault("game.woolHead", Boolean.valueOf(false));
		if (arena.cfg.get("teams") == null) {
			db.i("no teams defined, adding custom red and blue!");
			arena.cfg.getYamlConfiguration().addDefault("teams.red",
					ChatColor.RED.name());
			arena.cfg.getYamlConfiguration().addDefault("teams.blue",
					ChatColor.BLUE.name());
		}
	}

	@Override
	public boolean checkAndCommit() {

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

		for (ArenaTeam team : arena.getTeams()) {
			for (ArenaPlayer ap : team.getTeamMembers()) {
				if (ap.getStatus().equals(Status.FIGHT)) {
					commit(team.getName(), true);
					return true;
				}
			}
		}
		
		commit("", true);
		return true;
	}

	private void commit(String sTeam, boolean win) {

		db.i("[DM] committing end: " + sTeam);
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
	public void initLanguage(YamlConfiguration config) {
		config.addDefault("lang.frag",
				"%1% killed another player! Total frags: %2%.");
	}

	@Override
	public void parseRespawn(Player respawnPlayer, ArenaTeam respawnTeam,
			int lives, DamageCause cause, Entity damager) {
		db.i("handling deathmatch");

		ArenaTeam team = Teams.getTeam(arena, ArenaPlayer.parsePlayer(respawnPlayer));

		Player attacker = null;

		if (damager instanceof Player) {
			attacker = (Player) damager;
		}

		if (team == null || attacker == null) {
			return;
		}

		if (reduceLivesCheckEndAndCommit(respawnPlayer.getName())) {
			return;
		}
		arena.tellEveryone(
				Language.parse(
						"frag",
						team.colorizePlayer(attacker) + ChatColor.YELLOW,
						String.valueOf(arena.cfg.getInt("game.lives")
								- arena.lives.get(respawnPlayer.getName()))));
		arena.tpPlayerToCoordName(respawnPlayer, respawnTeam.getName() + "spawn");
	}

	@Override
	public boolean reduceLivesCheckEndAndCommit(String sPlayer) {

		db.i("reducing lives of team " + sPlayer);
		if (arena.lives.get(sPlayer) != null) {
			int i = arena.lives.get(sPlayer) - 1;
			if (i > 0) {
				arena.lives.put(sPlayer, i);
			} else {
				arena.lives.remove(sPlayer);
				for (ArenaTeam aTeam : arena.getTeams()) {
					if (!aTeam.getTeamMembers().contains(ArenaPlayer.parsePlayer(Bukkit.getPlayerExact(sPlayer)))) {
						continue;
					}
					commit(aTeam.getName(), false);
					return true;
				}
			}
		}
		return false;
	}

	@Override
	public void timed() {
		int i;

		int max = 10000;
		HashSet<String> result = new HashSet<String>();
		db.i("timed end!");

		for (String sTeam : arena.lives.keySet()) {
			i = arena.lives.get(sTeam);

			if (i < max) {
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
				arena.tellEveryone(
						Language.parse("teamhaswon", team.getColor() + "Team "
								+ team.getName()));
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
}