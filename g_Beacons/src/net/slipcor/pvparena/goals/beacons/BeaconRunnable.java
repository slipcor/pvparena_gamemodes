package net.slipcor.pvparena.goals.beacons;

import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.core.Config;
import net.slipcor.pvparena.core.Language;
import net.slipcor.pvparena.core.StringParser;
import org.bukkit.*;

class BeaconRunnable implements Runnable {
    public final boolean take;
    public final Location loc;
    public int runID = -1;
    private final Arena arena;
    public final String team;
    private final GoalBeacons beacons;

    /**
     * create a beacon runnable
     *
     * @param arena the arena we are running in
     */
    public BeaconRunnable(final Arena arena, final boolean take, final Location loc2, final String teamName,
                          final GoalBeacons goal) {
        this.arena = arena;
        this.take = take;
        team = teamName;
        loc = loc2;
        beacons = goal;
        arena.getDebugger().i("BeaconRunnable constructor");
    }

    /**
     * the run method
     */
    @Override
    public void run() {
        arena.getDebugger().i("BeaconRunnable commiting");
        arena.getDebugger().i("team " + team + ", take: " + take);
        if (take) {
            // claim a beacon for the team
            if (!beacons.getBeaconMap().containsKey(loc)) {
                // beacon unclaimed! claim!
                arena.getDebugger().i("clag unclaimed. claim!");
                beacons.getBeaconMap().put(loc, team);
                // long interval = 20L * 5;


                if (arena.getArenaConfig().getBoolean(Config.CFG.GOAL_BEACONS_CHANGEONCLAIM)) {

                    beacons.reduceLivesCheckEndAndCommit(arena, team);

                    final int max = arena.getArenaConfig().getInt(Config.CFG.GOAL_BEACONS_LIVES);
                    if (!beacons.getMyLifeMap().containsKey(team)) {
                        return;
                    }

                    final int lives = beacons.getMyLifeMap().get(team);

                    if ((max - lives) % beacons.announceOffset != 0) {
                        return;
                    }

                    arena.broadcast(Language.parse(arena,
                            Language.MSG.GOAL_BEACONS_CLAIMED_REMAINING, arena.getTeam(team)
                                    .getColoredName() + ChatColor.YELLOW, String.valueOf(lives)));

                    Bukkit.getScheduler().cancelTask(runID);
                    beacons.getRunnerMap().remove(loc);

                    beacons.updateBeacon();
                    return;
                } else {
                    arena.broadcast(Language.parse(arena,
                            Language.MSG.GOAL_BEACONS_CLAIMED, arena.getTeam(team)
                                    .getColoredName() + ChatColor.YELLOW));
                }


                takeBeacon(arena, loc, team);
                beacons.getBeaconMap().put(loc, team);

                // claim done. end timer
                Bukkit.getScheduler().cancelTask(runID);
                beacons.getRunnerMap().remove(loc);
            }
        } else {
            // unclaim
            arena.getDebugger().i("unclaimed");
            takeBeacon(arena, loc, "");
            Bukkit.getScheduler().cancelTask(runID);
            beacons.getRunnerMap().remove(loc);
            beacons.getBeaconMap().remove(loc);
        }
    }


    private void takeBeacon(final Arena arena, final Location lBlock, final String name) {
        ArenaTeam team = null;
        for (final ArenaTeam t : arena.getTeams()) {
            if (t.getName().equals(name)) {
                team = t;
            }
        }
        if (team == null) {
            for (ArenaPlayer ap : arena.getEveryone()) {
                ap.get().sendBlockChange(lBlock, Material.STAINED_GLASS, DyeColor.WHITE.getData());
            }
/*
            if (!"".equals(name)) {
                beacons.activateBeacon(true);
            }*/
            return;
        }
        try {
            for (ArenaPlayer ap : arena.getEveryone()) {
                ap.get().sendBlockChange(lBlock, Material.STAINED_GLASS, StringParser.getColorDataFromENUM(team.getColor().name()));
            }
/*
            beacons.activateBeacon(true);*/
        } catch (Exception e) {
            e.printStackTrace();
            return;
        }
    }
}