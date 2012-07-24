package net.slipcor.pvparena.arenas.teamdeathmatch;

import java.util.HashSet;
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

public class TeamDeathmatch extends ArenaType {

	public TeamDeathmatch() {
		super("teamdm");
	}
	
	@Override
	public String version() {
		return "v0.8.11.11";
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

	private void commit(String sTeam, boolean win) {

		db.i("[TEAMDM] committing end: " + sTeam);
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
			arena.tellEveryone(Language.parse("teamhaswon",
					Teams.getTeam(arena, winteam).getColor() + "Team "
							+ winteam));
		}

		arena.lives.clear();
		EndRunnable er = new EndRunnable(arena, arena.cfg.getInt("goal.endtimer"),0);
		arena.REALEND_ID = Bukkit.getScheduler().scheduleSyncRepeatingTask(PVPArena.instance,
				er, 20L, 20L);
		er.setId(arena.REALEND_ID);
	}

	@Override
	public int getLives(Player defender) {
		ArenaPlayer ap = ArenaPlayer.parsePlayer(defender);
		ArenaTeam team = Teams.getTeam(arena, ap);
		return arena.lives.get(team.getName());
	}

	@Override
	public void parseRespawn(Player respawnPlayer, ArenaTeam respawnTeam,
			int lives, DamageCause cause, Entity damager) {
		db.i("handling teamdeathmatch");

		if (reduceLivesCheckEndAndCommit(respawnTeam.getName())) {
			return;
		}
		arena.tellEveryone(Language.parse("killedbylives",
				respawnTeam.colorizePlayer(respawnPlayer) + ChatColor.YELLOW,
				arena.parseDeathCause(respawnPlayer, cause, damager),
				String.valueOf(arena.lives.get(respawnTeam.getName()))));
		
		arena.tpPlayerToCoordName(respawnPlayer, respawnTeam.getName()
				+ "spawn");
	}

	@Override
	public void initiate() {
		// arena.lives.clear();
		for (ArenaTeam team : arena.getTeams()) {
			if (team.getTeamMembers().size() > 0) {
				// team is active
				int add = 0;
				for (ArenaPlayer ap : team.getTeamMembers()) {
					add += arena.cfg.getInt("game.lives", 3);
					db.i("adding lives for player " + ap.getName());
				}
				arena.lives.put(team.getName(), add);
			}
		}
	}

	@Override
	public void initLanguage(YamlConfiguration config) {
		config.addDefault("lang.youjoinedteamdm",
				"Welcome to the TeamDeathmatch Arena! You are on team %1%'");
		config.addDefault("lang.playerjoinedteamdm",
				"%1% has joined team %2%!'");
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
}
