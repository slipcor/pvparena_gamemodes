package net.slipcor.pvparena.arenas.domination;

import org.bukkit.Bukkit;
import net.slipcor.pvparena.arena.Arena;
import net.slipcor.pvparena.core.Debug;

public class DominationMainRunnable implements Runnable {
	public int ID = -1;
	private final Arena arena;
	private Debug db = new Debug(39);
	private final Domination domination;

	public DominationMainRunnable(Arena a, Domination d) {
		arena = a;
		domination = d;
		db.i("DominationMainRunnable constructor");
	}

	/**
	 * the run method, commit arena end
	 */
	@Override
	public void run() {
		if (!arena.fightInProgress || arena.REALEND_ID != -1) {
			Bukkit.getScheduler().cancelTask(ID);
		}
		domination.checkMove();
	}
}
