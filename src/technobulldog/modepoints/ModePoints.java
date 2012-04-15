/*
 * Copyright (c) 2012 TechnoBulldog
 *
 * This software is provided 'as-is', without any express or implied
 * warranty. In no event will the authors be held liable for any damages
 * arising from the use of this software.
 * 
 * Permission is granted to anyone to use this software for any purpose,
 * including commercial applications, and to alter it and redistribute it
 * freely, subject to the following restrictions:
 * 
 *  1. The origin of this software must not be misrepresented; you must not
 *  claim that you wrote the original software. If you use this software
 *  in a product, an acknowledgment in the product documentation would be
 *  appreciated but is not required.
 *
 *  2. Altered source versions must be plainly marked as such, and must not be
 *  misrepresented as being the original software.
 *
 *  3. This notice may not be removed or altered from any source
 *  distribution.
 */

package technobulldog.modepoints;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class ModePoints extends JavaPlugin implements Listener {
	
	private Connection mSqlConnection;
	private HashMap<Location, Waypoint> mWaypointList;
	
	private void info(String info) { getServer().getLogger().info("ModePoints: " + info); }
	
	private void severe(String severe) { getServer().getLogger().severe("ModePoints: " + severe); }
	
	private void checkTable() {
		if(mSqlConnection == null)
			return;
		try {
			if(!mSqlConnection.getMetaData().getTables(null, null, "waypoints", null).next())
				mSqlConnection.createStatement().executeUpdate("CREATE TABLE `waypoints` ("
						+ "`id` INTEGER PRIMARY KEY,"
						+ "`name` varchar(32) NOT NULL DEFAULT '0',"
						+ "`network` varchar(32) NOT NULL DEFAULT '0',"
						+ "`world` varchar(32) NOT NULL DEFAULT '0',"
						+ "`x` INTEGER NOT NULL DEFAULT '0',"
						+ "`y` INTEGER NOT NULL DEFAULT '0',"
						+ "`z` INTEGER NOT NULL DEFAULT '0',"
						+ "`material` INTEGER NOT NULL DEFAULT '0'"
						+ ")");
		}
		catch(SQLException e) {
			severe(e.getMessage());
			severe("Database error! Could not verify existing table!");
		}
	}
	
	private void load() {
		try {
			File f = new File(getDataFolder().getAbsolutePath() + File.separator);
			if(!f.exists())
				f.mkdir();
			mSqlConnection = DriverManager.getConnection("jdbc:sqlite:" + getDataFolder().getAbsolutePath() + File.separator + "waypoints.sqlite");
			mSqlConnection.setAutoCommit(true);
			checkTable();
			
			ResultSet rs = mSqlConnection.createStatement().executeQuery("SELECT * FROM 'waypoints'");
			while(rs.next()) {
				Location l = new Location(getServer().getWorld(rs.getString("world")), rs.getInt("x"), rs.getInt("y"), rs.getInt("z"));
				Waypoint w = new Waypoint(rs.getString("name"), rs.getString("network"), l, Material.getMaterial(rs.getInt("material")));
				if(w.verify()) // Make sure that the waypoint is verified before adding it to the list.
					mWaypointList.put(l, w);
			}
			
			mSqlConnection.close();
			mSqlConnection = null;
			info("Waypoints successfully loaded!");
		}
		catch(SQLException e) {
			severe(e.getMessage());
			severe("Database error! Could not load waypoints!");
		}
	}
	
	private void save() {
		try {
			File f = new File(getDataFolder().getAbsolutePath() + File.separator);
			if(!f.exists())
				f.mkdir();
			mSqlConnection = DriverManager.getConnection("jdbc:sqlite:" + getDataFolder().getAbsolutePath() + File.separator + "waypoints.sqlite");
			mSqlConnection.setAutoCommit(true);
			checkTable();
			
			mSqlConnection.createStatement().executeUpdate("DELETE FROM 'waypoints';");
			
			if(mWaypointList.size() > 0) {
				PreparedStatement p = mSqlConnection.prepareStatement("INSERT INTO 'waypoints'" +
						"('name', 'network', 'world', 'x', 'y', 'z', 'material') VALUES (?, ?, ?, ?, ?, ?, ?);");
				for(Location l : mWaypointList.keySet()) {
					Waypoint w = mWaypointList.get(l);
					if(w.verify()) {
						p.setString(1, w.getName());
						p.setString(2, w.getNetwork());
						p.setString(3, w.getLocation().getWorld().getName());
						p.setInt(4, w.getLocation().getBlockX());
						p.setInt(5, w.getLocation().getBlockY());
						p.setInt(6, w.getLocation().getBlockZ());
						p.setInt(7, w.getKey().getId());
						p.addBatch();
					}
					else
						mWaypointList.remove(w);
				}
				p.executeBatch();
			}
			
			mSqlConnection.close();
			mSqlConnection = null;
			info("Waypoints successfully saved!");
		}
		catch(SQLException e) {
			severe(e.getMessage());
			severe("Database error! Could not save waypoints!");
		}
	}
	
	private void teleport(Player p, Block b, String network, String destination, String parameters) {
		boolean leaveSign = false, leaveKey = false;
		String dest = destination.replaceAll("[^a-zA-Z0-9\\s]", "");
		String net = network.replaceAll("[^a-zA-Z0-9\\s]", "");
		String params = parameters.replaceAll("[^a-zA-Z0-9\\s]", "");
		
		// Check teleporter.
		if(destination.equalsIgnoreCase("")) {
			p.sendMessage(ChatColor.LIGHT_PURPLE + "You need to provide a destination.");
			return;
		}
		if(b.getRelative(BlockFace.NORTH).getType() != Material.OBSIDIAN || 
			b.getRelative(BlockFace.SOUTH).getType() != Material.OBSIDIAN || 
			b.getRelative(BlockFace.EAST).getType() != Material.OBSIDIAN || 
			b.getRelative(BlockFace.WEST).getType() != Material.OBSIDIAN) {
			p.sendMessage(ChatColor.LIGHT_PURPLE + "Incorrect teleporter pattern.");
			return;
		}
		
		// Parse parameters.
		String[] paramsx = params.split(" ");
		for(int i = 0; i < paramsx.length; i++) {
			if(paramsx[i].equalsIgnoreCase("LS"))
				leaveSign = true;
			if(paramsx[i].equalsIgnoreCase("LK"))
				leaveKey = true;
		}
		
		// Get waypoint.
		Waypoint w = null;
		for(Location l : mWaypointList.keySet()) {
			Waypoint way = mWaypointList.get(l);
			if(way.getName().equalsIgnoreCase(dest) && way.getNetwork().equalsIgnoreCase(net)) {
				w = way;
			}
		}
		if(w == null) {
			p.sendMessage(ChatColor.LIGHT_PURPLE + "No waypoint with that name was found.");
			b.getRelative(0, 1, 0).setTypeId(0); // Replace the sign with air.
			p.getWorld().dropItem(p.getLocation(), new ItemStack(Material.SIGN, 1)); // Give the user their sign back.
			if(b.getType() != Material.AIR) // Don't give them air though.
				p.getWorld().dropItem(p.getLocation(), new ItemStack(b.getType(), 1)); // Give the user their item back.
			b.setTypeId(0); // Replace the sign with air.
			return;
		}
		
		// Compare the keys.
		if(w.getKey() != b.getType()) { // If keys do not match.
			p.sendMessage(ChatColor.LIGHT_PURPLE + "You have an incorrect key.");
			if(!leaveSign) { // Do not leave the sign.
				b.getRelative(0, 1, 0).setTypeId(0); // Replace the sign with air.
				p.getWorld().dropItem(p.getLocation(), new ItemStack(Material.SIGN, 1)); // Give the user their sign back.
			}
			if(!leaveKey) { // Do not leave the key.
				if(b.getType() != Material.AIR)
					p.getWorld().dropItem(p.getLocation(), new ItemStack(b.getType(), 1)); // Give the user their key back.
				b.setTypeId(0); // Replace the key with air.
			}
			return;
		}
		
		// Verify Teleporter
		if(!w.verify()) {
			p.sendMessage(ChatColor.LIGHT_PURPLE + "Waypoint was obliterated by the GDI Ion Cannon Satellite.");
			mWaypointList.remove(w.getLocation());
			b.getRelative(0, 1, 0).setTypeId(0); // Replace the sign with air.
			p.getWorld().dropItem(p.getLocation(), new ItemStack(Material.SIGN, 1)); // Give the user their sign back.
			if(b.getType() != Material.AIR)
				p.getWorld().dropItem(p.getLocation(), new ItemStack(b.getType(), 1)); // Give the user their key back.
			b.setTypeId(0); // Replace the key with air.
			return;
		}
		
		// Attempt to teleport.
		w.teleport(p);
		if(!leaveSign) { // Do not leave the sign.
			b.getRelative(0, 1, 0).setTypeId(0); // Replace the sign with air.
			p.getWorld().dropItem(p.getLocation(), new ItemStack(Material.SIGN, 1)); // Give the user their sign back.
		}
		if(!leaveKey) { // Do not leave the key.
			if(b.getType() != Material.AIR)
				p.getWorld().dropItem(p.getLocation(), new ItemStack(b.getType(), 1)); // Give the user their key back.
			b.setTypeId(0); // Replace the key with air.
		}
	}
	
	private void waypoint(Player p, Location l, String network, String name) {
		String dest = name.replaceAll("[^a-zA-Z0-9\\s]", "");
		String net = network.replaceAll("[^a-zA-Z0-9\\s]", "");
		
		Block b = l.getBlock();

		// Verify stuff.
		if(dest.equalsIgnoreCase("")) { // If blank don't do anything.
			p.sendMessage(ChatColor.LIGHT_PURPLE + "You must provide a waypoint name.");
			return;
		}
		if(b.getRelative(BlockFace.NORTH).getType() != Material.OBSIDIAN || 
				b.getRelative(BlockFace.SOUTH).getType() != Material.OBSIDIAN || 
				b.getRelative(BlockFace.EAST).getType() != Material.OBSIDIAN || 
				b.getRelative(BlockFace.WEST).getType() != Material.OBSIDIAN) {
			p.sendMessage(ChatColor.LIGHT_PURPLE + "Incorrect waypoint pattern.");
			return;
		}

		// Check for already existing waypoint.
		if(mWaypointList.containsKey(l)) {
			if(mWaypointList.get(l).verify()) {
				p.sendMessage(ChatColor.LIGHT_PURPLE + "There is already a waypoint in this location.");
				return;
			}
			mWaypointList.remove(l); // If the waypoint isn't verified, remove it.
		}
		for(Location loc : mWaypointList.keySet()) {
			Waypoint way = mWaypointList.get(loc);
			if(way.getName().equalsIgnoreCase(dest) && way.getNetwork().equalsIgnoreCase(net)) {
				if(way.verify()) {
					p.sendMessage(ChatColor.LIGHT_PURPLE + "The provided waypoint name is already in use.");
					return;
				}
				mWaypointList.remove(l); // If the waypoint isn't verified, remove it.
			}
		}

		// Create the waypoint.
		Waypoint w = new Waypoint(dest, net, b.getLocation(), b.getType());
		mWaypointList.put(l, w);
		w.printInfo(p);
		b.setType(Material.GLASS);
		b.getRelative(BlockFace.UP).setType(Material.AIR);
		p.getWorld().dropItem(p.getLocation(), new ItemStack(Material.SIGN, 1));
		if(w.getKey() != Material.AIR)
			p.getWorld().dropItem(p.getLocation(), new ItemStack(w.getKey(), 1));
		return;
	}
	
	public void onDisable() {
		save();
		mWaypointList.clear();
		info("Disabled!");
	}
	
	public void onEnable() {
		try {
			Class.forName("org.sqlite.JDBC");
		} 
		catch(ClassNotFoundException e) {
			severe("SQLite not found. Shutting down.");
			return;
		}
		mSqlConnection = null;
		mWaypointList = new HashMap<Location, Waypoint>();
		load();
		getServer().getPluginManager().registerEvents(this, this);
		getServer().getScheduler().scheduleSyncRepeatingTask(this, new Runnable() { public void run() { save(); } }, 72000L, 72000L);
		info("Enabled!");
	}
	
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onBlockBreak(BlockBreakEvent e) {
		if(e.isCancelled() || e == null || e.getBlock().getType() != Material.GLASS)
			return;
		if(mWaypointList.containsKey(e.getBlock().getLocation())) {
			if(!e.getPlayer().hasPermission("modepoints.create")) {
				e.getPlayer().sendMessage(ChatColor.RED + "You do not have permission to destroy this waypoint.");
				e.setCancelled(true);
				return;
			}
			mWaypointList.remove(e.getBlock().getLocation());
			e.getPlayer().sendMessage(ChatColor.GREEN + "Waypoint deleted.");
		}
	}
	
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerInteract(PlayerInteractEvent e) {
		if(e.isCancelled() || e == null || e.getAction() != Action.RIGHT_CLICK_BLOCK)
			return;
		if(!(e.getClickedBlock().getType() == Material.SIGN_POST || e.getClickedBlock().getType() == Material.WALL_SIGN))
			return;
		Sign s = (Sign)e.getClickedBlock().getState();
		if(s.getLine(0).equalsIgnoreCase("[TELEPORTER]") ||
				s.getLine(0).equalsIgnoreCase("-TELEPORTER-") ||
				s.getLine(0).equalsIgnoreCase("[TELEPORT]") ||
				s.getLine(0).equalsIgnoreCase("-TELEPORT-")) {
			if(!e.getPlayer().hasPermission("modepoints.use")) {
				e.getPlayer().sendMessage(ChatColor.RED + "You do not have permission to teleport.");
				e.setCancelled(true);
				return;
			}
			teleport(e.getPlayer(), s.getBlock().getRelative(BlockFace.DOWN), s.getLine(3), s.getLine(1), s.getLine(2));
		}
	}
	
	@EventHandler(priority = EventPriority.HIGHEST)
	public void onSignChange(SignChangeEvent e) {
		if(e.isCancelled() || e == null)
			return;
		if(e.getLine(0).equalsIgnoreCase("[WAYPOINT]") ||
				e.getLine(0).equalsIgnoreCase("-WAYPOINT-")) {
			if(!e.getPlayer().hasPermission("modepoints.create")) {
				e.getPlayer().sendMessage(ChatColor.RED + "You do not have permission to create waypoints.");
				e.setCancelled(true);
				return;
			}
			waypoint(e.getPlayer(), e.getBlock().getRelative(BlockFace.DOWN).getLocation(), e.getLine(2), e.getLine(1));
		}
		if(e.getLine(0).equalsIgnoreCase("[TELEPORTER]") ||
				e.getLine(0).equalsIgnoreCase("-TELEPORTER-") ||
				e.getLine(0).equalsIgnoreCase("[TELEPORT]") ||
				e.getLine(0).equalsIgnoreCase("-TELEPORT-")) {
			if(!e.getPlayer().hasPermission("modepoints.use")) {
				e.getPlayer().sendMessage(ChatColor.RED + "You do not have permission to teleport.");
				e.setCancelled(true);
				return;
			}
			teleport(e.getPlayer(), e.getBlock().getRelative(BlockFace.DOWN), e.getLine(3), e.getLine(1), e.getLine(2));
		}
	}
	
	private class Waypoint {
		private Material mKey; public Material getKey() { return mKey; }
		private Location mLocation; public Location getLocation() { return mLocation; }
		private String mName; public String getName() { return mName; }
		private String mNetwork; public String getNetwork() { return mNetwork; }
		public Waypoint(String name, String network, Location location, Material key) {
			mName = name; mNetwork = network; mLocation = location; mKey = key;
		}
		public void printInfo(Player p) {
			p.sendMessage(ChatColor.GREEN + "Waypoint created! " + mNetwork + ":" + mName +
					" (" + mLocation.getBlockX() + ", " + mLocation.getBlockY() + ", " + mLocation.getBlockZ() + ") (" + mKey.name() + ")");
			info("Waypoint created! " + mNetwork + ":" + mName +
					" (" + mLocation.getBlockX() + ", " + mLocation.getBlockY() + ", " + mLocation.getBlockZ() + ") (" + mKey.name() + ")");
		}
		public void teleport(Player p) {
			Block b = mLocation.getBlock();
			if(b.getRelative(BlockFace.NORTH).getType() != Material.OBSIDIAN ||
					b.getRelative(BlockFace.SOUTH).getType() != Material.OBSIDIAN ||
					b.getRelative(BlockFace.EAST).getType() != Material.OBSIDIAN ||
					b.getRelative(BlockFace.WEST).getType() != Material.OBSIDIAN) {
				p.sendMessage(ChatColor.LIGHT_PURPLE + "Waypoint malfunction. Check your obsidian pattern.");
			}
			int y = 0;
			while(b.getRelative(0, y++, 0).getType() != Material.AIR); // Find the nearest open air block.
			p.teleport(new Location(mLocation.getWorld(), mLocation.getX() + 0.5, mLocation.getY() + y, mLocation.getZ() +
					0.5, p.getLocation().getYaw(), p.getLocation().getPitch()));
		}
		public boolean verify() {
			if(mLocation.getBlock().getType() == Material.GLASS)
				return true;
			return false;
		}
	}
}