package net.wesjd.anvilgui;

import net.wesjd.anvilgui.version.VersionMatcher;
import net.wesjd.anvilgui.version.VersionWrapper;
import org.apache.commons.lang.Validate;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.Objects;
import java.util.function.BiFunction;
import java.util.function.Consumer;

/**
 * An anvil gui, used for gathering a user's input
 *
 * @author Wesley Smith
 * @since 1.0
 */
public class AnvilGUI {

  /**
   * The local {@link VersionWrapper} object for the server's version
   */
  private static VersionWrapper WRAPPER = new VersionMatcher().match();

  /**
   * The {@link Plugin} that this anvil GUI is associated with
   */
  private final Plugin plugin;
  /**
   * The player who has the GUI open
   */
  private final Player player;
  /**
   * The text that will be displayed to the user
   */
  private String text = "";
  /**
   * A state that decides where the anvil GUI is able to be closed by the user
   */
  private final boolean preventClose;
  /**
   * An {@link Consumer} that is called when the anvil GUI is closed
   */
  private final Consumer<Player> closeListener;
  /**
   * A {@link Consumer} that is called when anvil GUI is closed by escaping
   */
  private final Consumer<Player> closeOnEscape;
  /**
   * An {@link BiFunction} that is called when the {@link Slot#OUTPUT} slot has been clicked
   */
  private final BiFunction<Player, String, Response> completeFunction;

  /**
   * The ItemStack that is in the {@link Slot#INPUT_LEFT} slot.
   */
  private ItemStack insert;
  /**
   * The container id of the inventory, used for NMS methods
   */
  private int containerId;
  /**
   * The inventory that is used on the Bukkit side of things
   */
  private Inventory inventory;
  /**
   * The listener holder class
   */
  private final ListenUp listener = new ListenUp();

  /**
   * Represents the state of the inventory being open
   */
  private boolean open;

  /**
   * Create an AnvilGUI and open it for the player.
   *
   * @param plugin     A {@link org.bukkit.plugin.java.JavaPlugin} instance
   * @param holder     The {@link Player} to open the inventory for
   * @param insert     What to have the text already set to
   * @param biFunction A {@link BiFunction} that is called when the player clicks the {@link Slot#OUTPUT} slot
   * @throws NullPointerException If the server version isn't supported
   * @deprecated As of version 1.2.3, use {@link AnvilGUI.Builder}
   */
  @Deprecated
  public AnvilGUI(Plugin plugin, Player holder, String insert, BiFunction<Player, String, String> biFunction) {
    this(plugin, holder, insert, null, false, null, null, (player, text) -> {
      String response = biFunction.apply(player, text);
      if (response != null) {
        return Response.text(response);
      } else {
        return Response.close();
      }
    });
  }

  /**
   * Create an AnvilGUI and open it for the player.
   *
   * @param plugin           A {@link org.bukkit.plugin.java.JavaPlugin} instance
   * @param player           The {@link Player} to open the inventory for
   * @param text             What to have the text already set to
   * @param insert           The {@link ItemStack} to put in the Anvil GUI
   * @param preventClose     Whether to prevent the inventory from closing
   * @param closeListener    A {@link Consumer} when the inventory closes
   * @param completeFunction A {@link BiFunction} that is called when the player clicks the {@link Slot#OUTPUT} slot
   */
  private AnvilGUI(
          Plugin plugin,
          Player player,
          String text,
          ItemStack insert,
          boolean preventClose,
          Consumer<Player> closeListener,
          Consumer<Player> closeOnEscape,
          BiFunction<Player, String, Response> completeFunction
  ) {
    this.plugin = plugin;
    this.player = player;
    this.text = text;
    this.insert = insert;
    this.preventClose = preventClose;
    this.closeListener = closeListener;
    this.closeOnEscape = closeOnEscape;
    this.completeFunction = completeFunction;
    openInventory();
  }

  /**
   * Opens the anvil GUI
   */
  private void openInventory() {
    this.insert = Objects.isNull(insert) ? makeDefaultItemStack(text) : insert;

    WRAPPER.handleInventoryCloseEvent(player);
    WRAPPER.setActiveContainerDefault(player);

    Bukkit.getPluginManager().registerEvents(listener, plugin);

    final Object container = WRAPPER.newContainerAnvil(player);

    inventory = WRAPPER.toBukkitInventory(container);
    inventory.setItem(Slot.INPUT_LEFT, this.insert);

    containerId = WRAPPER.getNextContainerId(player, container);
    WRAPPER.sendPacketOpenWindow(player, containerId);
    WRAPPER.setActiveContainer(player, container);
    WRAPPER.setActiveContainerId(container, containerId);
    WRAPPER.addActiveContainerSlotListener(container, player);
    open = true;
  }

  /**
   * Makes the default {@link ItemStack} when it has not been
   * provided by the {@link AnvilGUI.Builder}.
   *
   * @param text The default {@link String} that you want to inset.
   * @return A default {@link ItemStack} to insert to the GUI.
   */
  private ItemStack makeDefaultItemStack(String text) {
    final ItemStack paper = new ItemStack(Material.PAPER);
    final ItemMeta paperMeta = paper.getItemMeta();
    paperMeta.setDisplayName(text);
    paper.setItemMeta(paperMeta);
    return paper;
  }

  /**
   * Closes the inventory if it's open.
   *
   * @throws IllegalArgumentException If the inventory isn't open
   */
  public void closeInventory(Boolean escapeClose) {
    Validate.isTrue(open, "You can't close an inventory that isn't open!");
    open = false;

    WRAPPER.handleInventoryCloseEvent(player);
    WRAPPER.setActiveContainerDefault(player);
    WRAPPER.sendPacketCloseWindow(player, containerId);

    HandlerList.unregisterAll(listener);


      if (escapeClose && closeOnEscape != null)
        closeOnEscape.accept(player);
      else if (!escapeClose && closeListener != null)
        closeListener.accept(player);
  }

  /**
   * Returns the Bukkit inventory for this anvil gui
   *
   * @return the {@link Inventory} for this anvil gui
   */
  public Inventory getInventory() {
    return inventory;
  }

  /**
   * Simply holds the listeners for the GUI
   */
  private class ListenUp implements Listener {

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
      if (event.getInventory().equals(inventory) && event.getRawSlot() < 3) {
        event.setCancelled(true);
        final Player clicker = (Player) event.getWhoClicked();
        if (event.getRawSlot() == Slot.OUTPUT) {
          final ItemStack clicked = inventory.getItem(Slot.OUTPUT);
          if (clicked == null || clicked.getType() == Material.AIR) return;

          final Response response = completeFunction.apply(clicker, clicked.hasItemMeta() ? clicked.getItemMeta().getDisplayName() : "");
          if (response.getText() != null) {
            final ItemMeta meta = clicked.getItemMeta();
            meta.setDisplayName(response.getText());
            clicked.setItemMeta(meta);
            inventory.setItem(Slot.INPUT_LEFT, clicked);
          } else {
            closeInventory(false);
          }
        }
      }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
      if (open && event.getInventory().equals(inventory)) {
        closeInventory(true);
        if (preventClose) {
          Bukkit.getScheduler().runTask(plugin, AnvilGUI.this::openInventory);
        }
      }
    }

  }

  /**
   * A builder class for an {@link AnvilGUI} object
   */
  public static class Builder {

    /**
     * An {@link Consumer} that is called when the anvil GUI is closed
     */
    private Consumer<Player> closeListener;

    private Consumer<Player> closeOnEscapeListener;
    /**
     * A state that decides where the anvil GUI is able to be closed by the user
     */
    private boolean preventClose = false;
    /**
     * An {@link BiFunction} that is called when the anvil output slot has been clicked
     */
    private BiFunction<Player, String, Response> completeFunction;
    /**
     * The {@link Plugin} that this anvil GUI is associated with
     */
    private Plugin plugin;
    /**
     * The text that will be displayed to the user
     */
    private String text = "";
    /**
     * The {@link ItemStack} that would get inserted into the GUI.
     */
    private ItemStack itemStack;

    /**
     * Prevents the closing of the anvil GUI by the user
     *
     * @return The {@link Builder} instance
     */
    public Builder preventClose() {
      preventClose = true;
      return this;
    }

    /**
     * Listens for when the inventory is closed
     *
     * @param closeListener An {@link Consumer} that is called when the anvil GUI is closed
     * @return The {@link Builder} instance
     * @throws IllegalArgumentException when the closeListener is null
     */
    public Builder onClose(Consumer<Player> closeListener) {
      Validate.notNull(closeListener, "closeListener cannot be null");
      this.closeListener = closeListener;
      return this;
    }

    public Builder onCloseOnEscape(Consumer<Player> closeListener) {
      Validate.notNull(closeListener, "closeListener cannot be null");
      this.closeOnEscapeListener = closeListener;
      return this;
    }

    /**
     * Handles the inventory output slot when it is clicked
     *
     * @param completeFunction An {@link BiFunction} that is called when the user clicks the output slot
     * @return The {@link Builder} instance
     * @throws IllegalArgumentException when the completeFunction is null
     */
    public Builder onComplete(BiFunction<Player, String, Response> completeFunction) {
      Validate.notNull(completeFunction, "Complete function cannot be null");
      this.completeFunction = completeFunction;
      return this;
    }

    /**
     * Sets the plugin for the {@link AnvilGUI}
     *
     * @param plugin The {@link Plugin} this anvil GUI is associated with
     * @return The {@link Builder} instance
     * @throws IllegalArgumentException if the plugin is null
     */
    public Builder plugin(Plugin plugin) {
      Validate.notNull(plugin, "Plugin cannot be null");
      this.plugin = plugin;
      return this;
    }

    /**
     * Sets the text that is to be displayed to the user
     *
     * @param text The text that is to be displayed to the user
     * @return The {@link Builder} instance
     * @throws IllegalArgumentException if the text is null
     */
    public Builder text(String text) {
      Validate.notNull(text, "Text cannot be null");
      this.text = text;
      return this;
    }

    /**
     * Sets the ItemStack that will be inserted into the
     * Anvil GUI.
     *
     * @param itemStack The {@link ItemStack} that will be displayed
     * @return The {@link Builder} instance
     * @throws IllegalArgumentException if the {@link ItemStack} is null
     */
    public Builder itemStack(ItemStack itemStack) {
      Validate.notNull(itemStack, "The ItemStack cannot be null");
      this.itemStack = itemStack;
      return this;
    }

    /**
     * Creates the anvil GUI and opens it for the player
     *
     * @param player The {@link Player} the anvil GUI should open for
     * @return The {@link AnvilGUI} instance from this builder
     * @throws IllegalArgumentException when the onComplete function, plugin, or player is null
     */
    public AnvilGUI open(Player player) {
      Validate.notNull(plugin, "Plugin cannot be null");
      Validate.notNull(completeFunction, "Complete function cannot be null");
      Validate.notNull(player, "Player cannot be null");
      return new AnvilGUI(plugin, player, text, itemStack, preventClose, closeListener, closeOnEscapeListener, completeFunction);
    }

  }

  /**
   * Represents a response when the player clicks the output item in the anvil GUI
   */
  public static class Response {

    /**
     * The text that is to be displayed to the user
     */
    private final String text;

    /**
     * Creates a response to the user's input
     *
     * @param text The text that is to be displayed to the user, which can be null to close the inventory
     */
    private Response(String text) {
      this.text = text;
    }

    /**
     * Gets the text that is to be displayed to the user
     *
     * @return The text that is to be displayed to the user
     */
    public String getText() {
      return text;
    }

    /**
     * Returns an {@link Response} object for when the anvil GUI is to close
     *
     * @return An {@link Response} object for when the anvil GUI is to close
     */
    public static Response close() {
      return new Response(null);
    }

    /**
     * Returns an {@link Response} object for when the anvil GUI is to display text to the user
     *
     * @param text The text that is to be displayed to the user
     * @return An {@link Response} object for when the anvil GUI is to display text to the user
     */
    public static Response text(String text) {
      return new Response(text);
    }

  }

  /**
   * Class wrapping the magic constants of slot numbers in an anvil GUI
   */
  public static class Slot {

    /**
     * The slot on the far left, where the first input is inserted. An {@link ItemStack} is always inserted
     * here to be renamed
     */
    public static final int INPUT_LEFT = 0;
    /**
     * Not used, but in a real anvil you are able to put the second item you want to combine here
     */
    public static final int INPUT_RIGHT = 1;
    /**
     * The output slot, where an item is put when two items are combined from {@link #INPUT_LEFT} and
     * {@link #INPUT_RIGHT} or {@link #INPUT_LEFT} is renamed
     */
    public static final int OUTPUT = 2;

  }

}
