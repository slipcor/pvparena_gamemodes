package net.slipcor.pvparena.goals.beacons;

import net.slipcor.pvparena.arena.Arena;
import org.bukkit.Bukkit;

class BeaconMainRunnable implements Runnable {
    public int rID = -1;
    private final Arena arena;
    private final GoalBeacons beacons;

    public BeaconMainRunnable(final Arena arena, final GoalBeacons goal) {
        this.arena = arena;
        beacons = goal;
        arena.getDebugger().i("BeaconMainRunnable constructor");
    }

    /**
     * the run method, commit arena end
     */
    @Override
    public void run() {
        if (!arena.isFightInProgress() || arena.realEndRunner != null) {
            Bukkit.getScheduler().cancelTask(rID);
        }
        beacons.checkMove();
    }
}
