package net.slipcor.pvparena.goals.pillars;

import org.bukkit.scheduler.BukkitRunnable;

public class PillarRunner extends BukkitRunnable {

	private final GoalPillars goal;
	
	public PillarRunner(final GoalPillars goal) {
		super();
		this.goal = goal;
	}

	@Override
	public void run() {
		goal.tick();
	}

}
