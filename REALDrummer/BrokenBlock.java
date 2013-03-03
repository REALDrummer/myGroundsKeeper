package REALDrummer;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;

public class BrokenBlock {

	public int id;
	public byte data;
	public Location location;
	public String save_line;

	// objects
	public BrokenBlock(Block block) {
		id = block.getTypeId();
		data = block.getData();
		location = block.getLocation();
		save_line =
				id + ":" + data + "(" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ", "
						+ location.getWorld().getWorldFolder().getName() + ")";
	}

	/**
	 * gjldf;gj;kfdl
	 * 
	 * @param my_save_line
	 *            line that it saves on
	 */
	public BrokenBlock(String my_save_line) {
		// [id]:[data]([x], [y], [z], [world])
		save_line = my_save_line;
		String[] temp = save_line.split("\\(");
		try {
			id = Integer.parseInt(temp[0].split(":")[0]);
			data = Byte.parseByte(temp[0].split(":")[1]);
			// use substring() to remove the closing parenthese at the end of the save line
			temp = temp[1].substring(0, temp[1].length() - 1).split(", ");
			World world = myGroundsKeeper.server.getWorld(temp[3]);
			if (world == null) {
				myGroundsKeeper.console.sendMessage(ChatColor.DARK_RED + "We got a problem reading the creeper hole world from a save line.");
				myGroundsKeeper.console.sendMessage(ChatColor.DARK_RED + "problematic save line: " + ChatColor.WHITE + save_line);
				myGroundsKeeper.console.sendMessage(ChatColor.DARK_RED + "what I read as the world name: " + ChatColor.WHITE + temp[3]);
				return;
			}
			location = new Location(world, Integer.parseInt(temp[0]), Integer.parseInt(temp[1]), Integer.parseInt(temp[2]));
		} catch (NumberFormatException exception) {
			myGroundsKeeper.console.sendMessage(ChatColor.DARK_RED + "We got a problem reading the creeper hole data from a save line.");
			myGroundsKeeper.console.sendMessage(ChatColor.DARK_RED + "problematic save line: " + ChatColor.WHITE + save_line);
			exception.printStackTrace();
			return;
		}
	}
}
