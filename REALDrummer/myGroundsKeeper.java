package REALDrummer;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;

import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.XMLEvent;

import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

public class myGroundsKeeper extends JavaPlugin implements Listener {

	public static JavaPlugin myGroundsKeeper;
	public static Server server;
	public static ConsoleCommandSender console;
	public static BukkitScheduler scheduler;
	private static boolean auto_update = true;
	private static String[] enable_messages = { "Man, I hate creepers.", "Ka-BOOM!!\nThat's a'ight. I got it.", "\"Maintaining the beauty of your server since 2013\"...",
			"I went through all that training with that red-headed nut Willie for this?", "I'm tiny, but that don't mean I ain't good at mah job.",
			"No configurations, commands, settings, or complications. Just sit back and I'll fix your screwups." }, disable_messages = {
			"I'll get rid of the rest of your creeper holes once you come back.", "Phew. Another day of work all wrapped up.",
			"Great. More money to give to my ex. Thanks, I guess.", "My favorite time of day: punchin' out." };
	// HashMap<locations of block breaks that need to be fixed, ids and data for the blocks to be fixed in this format: [id]:[data]>
	private static ArrayList<ArrayList<BrokenBlock>> creeper_holes = new ArrayList<ArrayList<BrokenBlock>>();

	// plugin enable/disable and the command operator
	public void onEnable() {
		myGroundsKeeper = this;
		server = getServer();
		console = server.getConsoleSender();
		scheduler = server.getScheduler();
		// register this class as a listener
		server.getPluginManager().registerEvents(this, this);
		load();
		if (auto_update)
			checkForUpdates(console);
		// done enabling
		String enable_message = enable_messages[(int) (Math.random() * enable_messages.length)];
		console.sendMessage(ChatColor.DARK_GREEN + enable_message);
		for (Player player : server.getOnlinePlayers())
			if (player.isOp())
				player.sendMessage(ChatColor.DARK_GREEN + enable_message);
	}

	public void onDisable() {
		save();
		// done disabling
		String disable_message = disable_messages[(int) (Math.random() * disable_messages.length)];
		console.sendMessage(ChatColor.DARK_GREEN + disable_message);
		for (Player player : server.getOnlinePlayers())
			if (player.isOp())
				player.sendMessage(ChatColor.DARK_GREEN + disable_message);
	}

	public boolean onCommand(CommandSender sender, Command cmd, String command, String[] parameters) {
		if ((command.equalsIgnoreCase("myGroundsKeeper") || command.equalsIgnoreCase("mGK")) && parameters.length == 1 && parameters[0].toLowerCase().startsWith("update")) {
			if (!(sender instanceof Player) || sender.isOp())
				checkForUpdates(sender);
			else if (command.equalsIgnoreCase("myGroundsKeeper"))
				sender.sendMessage(ChatColor.RED + "Ya ain't allowed to use " + ChatColor.DARK_GREEN + "/myGroundsKeeper update" + ChatColor.RED + ".");
			else
				sender.sendMessage(ChatColor.RED + "Ya ain't allowed to use " + ChatColor.DARK_GREEN + "/mGK update" + ChatColor.RED + ".");
			return true;
		} else if ((command.equalsIgnoreCase("myGroundsKeeper") || command.equalsIgnoreCase("mGK")) && parameters.length > 1
				&& parameters[0].toLowerCase().startsWith("update")) {
			if (!(sender instanceof Player) || sender.isOp()) {
				if (parameters[1].equalsIgnoreCase("on"))
					if (auto_update)
						sender.sendMessage(ChatColor.RED + "The auto-updater's already on.");
					else {
						auto_update = true;
						sender.sendMessage(ChatColor.DARK_GREEN + "Yeah, fine. I'll check for updates. REALDrummer thanks ya.");
					}
				else if (parameters[1].equalsIgnoreCase("off"))
					if (!auto_update)
						sender.sendMessage(ChatColor.RED
								+ "The auto-updater's already off. Oh, and REALDrummer is still nagging me to tell you you should turn it on. Just use /mGK update on, he says.");
					else {
						auto_update = false;
						sender.sendMessage(ChatColor.DARK_GREEN
								+ "I couldn't care less if you turn off the auto-updater. It's less work for me. REALDrummer, my boss, tells me you should keep it on, though. You could miss out on important updates or somethin'.");
					}
			} else if (command.equalsIgnoreCase("myGroundsKeeper"))
				sender.sendMessage(ChatColor.RED + "Ya ain't allowed to use " + ChatColor.DARK_GREEN + "/myGroundsKeeper update" + ChatColor.RED + ".");
			else
				sender.sendMessage(ChatColor.RED + "Ya ain't allowed to use " + ChatColor.DARK_GREEN + "/mGK update" + ChatColor.RED + ".");
			return true;
		}
		return false;
	}

	// the intra-command method
	private void doYourThing(final ArrayList<BrokenBlock> creeper_hole) {
		// the cooldown time is between 15 and 45 seconds
		// 15 seconds * 20 ticks/second = 300 ticks
		server.getScheduler().scheduleSyncDelayedTask(myGroundsKeeper, new Runnable() {
			@Override
			public void run() {
				creeper_holes.remove(creeper_hole);
				if (creeper_hole.size() == 0)
					return;
				// find the block(s) with the lowest y-coordinate
				// repairing these blocks first makes it easier for people who might accidentally go into these holes while they are being repaired by
				// decreasing the possibility of suffocating them. It also makes the repair process look prettier. Most importantly, though, it makes it so that
				// I don't have to program this plugin to account for certain complications -- namely gravel and sand falling during repair
				ArrayList<BrokenBlock> lowest_blocks = new ArrayList<BrokenBlock>();
				lowest_blocks.add(creeper_hole.get(0));
				for (int i = 0; i < creeper_hole.size(); i++)
					// if the block that's there is water (9) or lava (11), it likely flowed into the hole, so we should still repair those blocks
					// if the block that's there now is neither air nor any of these other blocks, it's likely that someone already placed a block there to
					// repair it themselves, so we should ignore this block and remove it from the list of blocks to repair
					if (creeper_hole.get(i).location.getBlock().getTypeId() != 0 && creeper_hole.get(i).location.getBlock().getTypeId() != 9
							&& creeper_hole.get(i).location.getBlock().getTypeId() != 11) {
						creeper_hole.remove(i);
						if (creeper_hole.size() == 0)
							return;
						i--;
					} else if (creeper_hole.get(i).location.getY() <= lowest_blocks.get(0).location.getY()) {
						if (creeper_hole.get(i).location.getY() < lowest_blocks.get(0).location.getY())
							lowest_blocks = new ArrayList<BrokenBlock>();
						lowest_blocks.add(creeper_hole.get(i));
					}
				// fix it!
				BrokenBlock block = lowest_blocks.get((int) (Math.random() * lowest_blocks.size()));
				block.location.getBlock().setTypeId(block.id);
				block.location.getBlock().setData(block.data);
				// make myGuardDog log the action
				if (server.getPluginManager().getPlugin("myGuardDog") != null)
					myGuardDog.events.add(new Event("myGroundsKeeper", "repaired", (Block) block, null));
				creeper_hole.remove(block);
				// update the information in creeper_holes
				creeper_holes.add(creeper_hole);
				// schedule another event for the next block to repair
				scheduler.scheduleSyncDelayedTask(myGroundsKeeper, this, (long) (60 + 120 * Math.random()));
			}
		}, (long) (60 + 120 * Math.random()));
	}

	// listeners
	@EventHandler(priority = EventPriority.HIGHEST)
	public void handleNewExplosions(EntityExplodeEvent event) {
		// we're only worried about creepers and Ghasts
		if (event.getEntityType() != EntityType.CREEPER && event.getEntityType() != EntityType.GHAST && event.getEntityType() != EntityType.WITHER)
			return;
		final ArrayList<BrokenBlock> creeper_hole = new ArrayList<BrokenBlock>();
		for (Block block : event.blockList()) {
			creeper_hole.add(new BrokenBlock(block));
			block.setTypeId(0);
		}
		doYourThing(creeper_hole);
	}

	@EventHandler
	public void cancelEndermanInteractions(EntityChangeBlockEvent event) {
		if (event.getEntityType() == EntityType.ENDERMAN)
			event.setCancelled(true);
	}

	// file management
	public void load() {
		creeper_holes = new ArrayList<ArrayList<BrokenBlock>>();
		// check the creeper holes file
		File creeper_holes_file = new File(getDataFolder().getParentFile(), "myGroundsKeeper.txt");
		if (creeper_holes_file.exists())
			// read the creeper holes.txt file
			try {
				BufferedReader in = new BufferedReader(new FileReader(creeper_holes_file));
				String save_line = in.readLine();
				ArrayList<BrokenBlock> creeper_hole = new ArrayList<BrokenBlock>();
				auto_update = true;
				while (save_line != null) {
					if (save_line.equals("updater off"))
						auto_update = false;
					else if (save_line.equals("==========")) {
						creeper_holes.add(creeper_hole);
						doYourThing(creeper_hole);
						creeper_hole = new ArrayList<BrokenBlock>();
					} else if (save_line.length() > 0)
						creeper_hole.add(new BrokenBlock(save_line));
					save_line = in.readLine();
				}
				in.close();
			} catch (IOException exception) {
				console.sendMessage(ChatColor.DARK_RED + "Got an IOException cloggin' up the works tryin' to load yer data.");
				exception.printStackTrace();
				return;
			}
		// send the console a confirmation message
		if (creeper_holes.size() > 1)
			console.sendMessage(ChatColor.DARK_GREEN + "I loaded yer " + creeper_holes.size() + " unfinished jobs from file.");
		else if (creeper_holes.size() == 1)
			console.sendMessage(ChatColor.DARK_GREEN + "I loaded yer 1 unfinished job from file.");
		// delete the temporary file
		creeper_holes_file.delete();
	}

	public void save() {
		// check the creeper holes file
		File creeper_holes_file = new File(getDataFolder().getParentFile(), "myGroundsKeeper.txt");
		// save the creeper holes
		try {
			creeper_holes_file.createNewFile();
			BufferedWriter out = new BufferedWriter(new FileWriter(creeper_holes_file));
			if (!auto_update) {
				out.write("updater off");
				out.newLine();
			}
			for (ArrayList<BrokenBlock> creeper_hole : creeper_holes) {
				for (BrokenBlock broken_block : creeper_hole) {
					out.write(broken_block.save_line);
					out.newLine();
				}
				out.write("==========");
				out.newLine();
			}
			out.flush();
			out.close();
		} catch (IOException exception) {
			console.sendMessage(ChatColor.DARK_RED + "Got an IOException cloggin' up the works tryin' to load yer data.");
			exception.printStackTrace();
			return;
		}
		// send the console a confirmation message
		if (creeper_holes.size() > 1)
			console.sendMessage(ChatColor.DARK_GREEN + "I saved yer " + creeper_holes.size() + " unfinished jobs.");
		else if (creeper_holes.size() == 1)
			console.sendMessage(ChatColor.DARK_GREEN + "I saved yer 1 unfinished job.");
	}

	// the plugin command
	private void checkForUpdates(CommandSender sender) {
		URL url = null;
		try {
			url = new URL("http://dev.bukkit.org/server-mods/realdrummers-mygroundskeeper/files.rss/");
		} catch (MalformedURLException exception) {
			sender.sendMessage("Gah. That updater machine took a crap. Bad U.R.L....");
		}
		if (url != null) {
			String new_version_name = null, new_version_link = null;
			try {
				// Set header values intial to the empty string
				String title = "";
				String link = "";
				// First create a new XMLInputFactory
				XMLInputFactory inputFactory = XMLInputFactory.newInstance();
				// Setup a new eventReader
				InputStream in = null;
				try {
					in = url.openStream();
				} catch (IOException e) {
					sender.sendMessage(ChatColor.DARK_RED + "I'm gettin' nada from BukkitDev.");
					return;
				}
				XMLEventReader eventReader = inputFactory.createXMLEventReader(in);
				// Read the XML document
				while (eventReader.hasNext()) {
					XMLEvent event = eventReader.nextEvent();
					if (event.isStartElement()) {
						if (event.asStartElement().getName().getLocalPart().equals("title")) {
							event = eventReader.nextEvent();
							title = event.asCharacters().getData();
							continue;
						}
						if (event.asStartElement().getName().getLocalPart().equals("link")) {
							event = eventReader.nextEvent();
							link = event.asCharacters().getData();
							continue;
						}
					} else if (event.isEndElement()) {
						if (event.asEndElement().getName().getLocalPart().equals("item")) {
							new_version_name = title;
							new_version_link = link;
							// All done, we don't need to know about older
							// files.
							break;
						}
					}
				}
			} catch (XMLStreamException exception) {
				sender.sendMessage(ChatColor.DARK_RED + "Ugh. XMLStreamException thing...I dunno. Tell REALDrummer. I'm at a loss.");
				return;
			}
			boolean new_version_is_out = false;
			String version = getDescription().getVersion(), newest_online_version = "";
			if (new_version_name.split("v").length == 2) {
				newest_online_version = new_version_name.split("v")[new_version_name.split("v").length - 1].split(" ")[0];
				// get the newest file's version number
				if (!version.contains("-DEV") && !version.contains("-PRE") && !version.equalsIgnoreCase(newest_online_version))
					try {
						if (Double.parseDouble(version) < Double.parseDouble(newest_online_version))
							new_version_is_out = true;
					} catch (NumberFormatException exception) {
					}
			} else
				sender.sendMessage(ChatColor.RED
						+ "That REALDrummer! He forgot to put the version number in the title of the BukkitDev project! Would ya go tell him he's a moron for me? I'd appreciate it.");
			if (new_version_is_out) {
				String fileLink = null;
				try {
					// Open a connection to the page
					BufferedReader reader = new BufferedReader(new InputStreamReader(new URL(new_version_link).openConnection().getInputStream()));
					String line;
					while ((line = reader.readLine()) != null)
						// Search for the download link
						if (line.contains("<li class=\"user-action user-action-download\">"))
							// Get the raw link
							fileLink = line.split("<a href=\"")[1].split("\">Download</a>")[0];
					reader.close();
					reader = null;
				} catch (Exception exception) {
					sender.sendMessage(ChatColor.DARK_RED + "I'm gettin' nada from BukkitDev.");
					exception.printStackTrace();
					return;
				}
				if (fileLink != null) {
					if (!new File(this.getDataFolder(), "myGroundsKeeper.jar").exists()) {
						BufferedInputStream in = null;
						FileOutputStream fout = null;
						try {
							getDataFolder().mkdirs();
							// download the file
							url = new URL(fileLink);
							in = new BufferedInputStream(url.openStream());
							fout = new FileOutputStream(this.getDataFolder().getAbsolutePath() + "/myGroundsKeeper.jar");
							byte[] data = new byte[1024];
							int count;
							while ((count = in.read(data, 0, 1024)) != -1)
								fout.write(data, 0, count);
							if (!(sender instanceof Player))
								sender.sendMessage(ChatColor.DARK_GREEN + "" + ChatColor.UNDERLINE + "That's the most work I've done all week. Yer myGroundsKeeper v"
										+ newest_online_version + " is in yer myGroundsKeeper folder. Go get it if you like.");
							for (Player player : server.getOnlinePlayers())
								if (player.isOp() && (!(sender instanceof Player) || !sender.getName().equals(player.getName())))
									player.sendMessage(ChatColor.DARK_GREEN + "" + ChatColor.UNDERLINE + "That's the most work I've done all week. Yer myGroundsKeeper v"
											+ newest_online_version + " is in yer myGroundsKeeper folder. Go get it if you like.");
						} catch (Exception ex) {
							sender.sendMessage(ChatColor.DARK_RED + "Oh, boy. myGroundsKeeper v" + newest_online_version
									+ " is out, but somethin' jacked up the download. You're gonna have to go to BukkitDev and get it yourself. It's your problem now.");
						} finally {
							try {
								if (in != null)
									in.close();
								if (fout != null)
									fout.close();
							} catch (Exception ex) {
							}
						}
					} else
						sender.sendMessage(ChatColor.RED
								+ "Hey, I don't really care, but REALDrummer says he insists that you get the new myGroundsKeeper out o' yer plugin folder and put it on yer server.");
				}
			} else
				sender.sendMessage(ChatColor.DARK_GREEN + "No new versions of myGroundsKeeper're out yet.");
		}
	}
}
