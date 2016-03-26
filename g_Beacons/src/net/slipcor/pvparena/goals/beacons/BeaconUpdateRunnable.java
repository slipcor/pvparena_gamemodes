package net.slipcor.pvparena.goals.beacons;

import net.slipcor.pvparena.arena.Arena;
import org.bukkit.Bukkit;

class BeaconUpdateRunnable implements Runnable {
    public int rID = -1;
    private final Arena arena;
    private final GoalBeacons beacons;

    public BeaconUpdateRunnable(final Arena arena, final GoalBeacons goal) {
        this.arena = arena;
        beacons = goal;
        arena.getDebugger().i("BeaconUpdateRunnable constructor");
    }

    @Override
    public void run() {
        if (!arena.isFightInProgress() || arena.realEndRunner != null) {
            Bukkit.getScheduler().cancelTask(rID);
            return;
        }
        beacons.updateBeacon();
    }
}
