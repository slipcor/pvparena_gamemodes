package net.slipcor.pvparena.goals.pillars;

import net.slipcor.pvparena.PVPArena;
import net.slipcor.pvparena.arena.ArenaPlayer;
import net.slipcor.pvparena.arena.ArenaTeam;
import net.slipcor.pvparena.classes.PABlockLocation;
import net.slipcor.pvparena.core.StringParser;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class Pillar {
    private final PABlockLocation baseLocation; // the base block location
    private final int maxHeight; // the maximum block height, including base block
    //private final GoalPillars goal; // the goal instance
    private final int maxClicks; // the needed clicks to break
    private final int initialHeight; // the starting height

    private final ArenaTeam defaultTeam; // the initial claiming team

    private int clicks; // the current clicks
    private int height; // existing blocks
    private ArenaTeam owner; // the owning team
    private ArenaTeam claiming; // the claiming team

    /**
     * <pre>
     * NONE - nothing to announce
     * BLOCK_BROKEN - a block has been broken
     * BLOCK_PLACED - a block has been placed
     * LOWER - the pillar now is lower
     * HIGHER - the pillar now is higher
     * CLAIMED - the pillar has been claimed
     * UNCLAIMED - the pillar has been unclaimed
     * </pre>
     */
    protected enum PillarResult {
        NONE,
        BLOCK_BROKEN,
        BLOCK_PLACED,
        LOWER,
        HIGHER,
        CLAIMED,
        UNCLAIMED
    }

    /**
     * Create a Pillar instance
     *
     * @param location  the base block location
     * @param mHeight   the maximum height. Should be > 0
     * @param mClicks   the needed clicks to claim. < 1 to disable
     * @param teamOwner the owning team. null if unclaimed
     */
    public Pillar(final PABlockLocation location, final int iHeight, final int mHeight, final int mClicks, final ArenaTeam teamOwner) {
        //goal = mod;
        baseLocation = location;
        maxHeight = mHeight;
        maxClicks = mClicks;
        owner = teamOwner;
        height = iHeight;
        initialHeight = height;

        defaultTeam = teamOwner;
    }

    public PillarResult blockBreak(final ArenaPlayer player) {
        if (player == null || player.get() == null || player.getArenaTeam() == null) {
            PVPArena.instance.getLogger().warning(String.valueOf(player));
            return PillarResult.NONE;
        }

        final ArenaTeam claiming = player.getArenaTeam();
        if (claiming.equals(owner)) {
            return PillarResult.NONE;
        }

        return decreaseHeight(true);
    }

    public PillarResult blockClick(final ArenaPlayer player) {
        if (player == null || player.get() == null || player.getArenaTeam() == null) {
            PVPArena.instance.getLogger().warning(String.valueOf(player));
            return PillarResult.NONE;
        }

        final ArenaTeam playerTeam = player.getArenaTeam();
        if (!playerTeam.equals(claiming)) {
            clicks = 0; // other team claiming, reset!
        }

        claiming = playerTeam;

        if (maxClicks < 1 || ++clicks < maxClicks) {
            return PillarResult.NONE;
        }

        if (height >= maxHeight && claiming.equals(owner)) {
            return PillarResult.NONE;
        }

        clicks = 0;

        if (owner == null) {
            owner = claiming;
            baseLocation.toLocation().getBlock().setTypeIdAndData(Material.WOOL.getId(), StringParser.getColorDataFromENUM(claiming.getColor().name()), true);

            return PillarResult.CLAIMED;
        }

        return claiming.equals(owner) ? increaseHeight(false) : decreaseHeight(false);
    }

    public PillarResult blockPlace(final ArenaPlayer player) {


        if (maxClicks > 0) {

            return blockClick(player);
        }

        if (player == null || player.get() == null || player.getArenaTeam() == null) {
            PVPArena.instance.getLogger().warning(String.valueOf(player));
            return PillarResult.NONE;
        }
        final ArenaTeam playerTeam = player.getArenaTeam();

        if (owner != null && !playerTeam.equals(owner)) {
            return PillarResult.NONE;
        }

        if (owner == null) {
            owner = claiming;
            baseLocation.toLocation().getBlock().setTypeIdAndData(Material.WOOL.getId(), StringParser.getColorDataFromENUM(claiming.getColor().name()), true);
            height = 1;
            return PillarResult.CLAIMED;
        }

        final PillarResult result = increaseHeight(true);

        if (result == PillarResult.BLOCK_PLACED) {
            class RunLater implements Runnable {
                private final Player player;

                RunLater(final Player player) {
                    this.player = player;
                }

                @Override
                public void run() {
                    final ItemStack removal = player.getItemInHand().clone();
                    removal.setAmount(1);
                    player.getInventory().remove(removal);
                    player.updateInventory();
                }

            }
            Bukkit.getScheduler().runTaskLater(PVPArena.instance, new RunLater(player.get()), 5L);

        }

        return result;
    }

    public boolean containsLocation(final PABlockLocation location) {
        final PABlockLocation loc = new PABlockLocation(baseLocation.toLocation());
        for (int pos = 0; pos < maxHeight; pos++) {
            loc.setY(baseLocation.getY() + pos);
            if (loc.equals(location)) {
                return true;
            }
        }
        return false;
    }

    private PillarResult decreaseHeight(final boolean breaking) {

        height--;
        Block removing = baseLocation.toLocation().getBlock().getRelative(BlockFace.UP, height);
        removing.setType(Material.AIR);

        if (height < 1) {
            removing = baseLocation.toLocation().getBlock().getRelative(BlockFace.UP, height);
            owner = null;
            height = 1;
            if (breaking) {
                removing.breakNaturally();
                class RunLater implements Runnable {

                    @Override
                    public void run() {
                        baseLocation.toLocation().getBlock().setTypeIdAndData(Material.WOOL.getId(), (byte) 0, true);
                    }

                }
                Bukkit.getScheduler().runTaskLater(PVPArena.instance, new RunLater(), 1L);
                return PillarResult.BLOCK_BROKEN;
            } else {
                removing.setTypeIdAndData(Material.WOOL.getId(), StringParser.getColorDataFromENUM("WHITE"), true);
                return PillarResult.UNCLAIMED;
            }
        }
        class RunLater implements Runnable {

            @Override
            public void run() {
                baseLocation.toLocation().getBlock().setTypeIdAndData(Material.WOOL.getId(), StringParser.getColorDataFromENUM(owner.getColor().name()), true);
            }

        }
        Bukkit.getScheduler().runTaskLater(PVPArena.instance, new RunLater(), 1L);
        return PillarResult.LOWER;
    }

    public double getClaimStatus() {
        double sum = 0;

        final double oneBlock = maxHeight > 0 ? 1d / maxHeight : 1;

        sum += maxHeight > 0 ? (double) height * oneBlock : 1;

        return sum;
    }

    public ArenaTeam getDefaultTeam() {
        return defaultTeam;
    }

    public PABlockLocation getLocation() {
        return baseLocation;
    }

    public ArenaTeam getOwner() {
        return owner;
    }

    private PillarResult increaseHeight(final boolean place) {

        if (height >= maxHeight) {
            return PillarResult.NONE;
        }


        final Block newBlock = baseLocation.toLocation().getBlock().getRelative(BlockFace.UP, height);

        class RunLater implements Runnable {

            @Override
            public void run() {
                newBlock.setTypeIdAndData(Material.WOOL.getId(), StringParser.getColorDataFromENUM(claiming.getColor().name()), true);
            }

        }

        Bukkit.getScheduler().runTaskLater(PVPArena.instance, new RunLater(), 5L);

        height++;

        return place ? PillarResult.BLOCK_PLACED : PillarResult.HIGHER;
    }

    public void reset() {
        owner = defaultTeam;
        height = owner == null ? 1 : initialHeight;

        final Location loc = baseLocation.toLocation();
        int pos = 0;
        final String color = owner == null ? "WHITE" : owner.getColor().name();
        final byte bColor = StringParser.getColorDataFromENUM(color);

        while (pos < maxHeight) {
            if (pos < height) {
                loc.getBlock().getRelative(BlockFace.UP, pos).setTypeIdAndData(Material.WOOL.getId(), bColor, false);
            } else {
                loc.getBlock().getRelative(BlockFace.UP, pos).setType(Material.AIR);
            }
            pos++;
        }
    }
}
