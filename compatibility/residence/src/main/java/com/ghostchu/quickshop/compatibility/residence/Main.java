package com.ghostchu.quickshop.compatibility.residence;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.api.ResidenceApi;
import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.containers.ResAdmin;
import com.bekvon.bukkit.residence.containers.ResidencePlayer;
import com.bekvon.bukkit.residence.permissions.PermissionGroup;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.bekvon.bukkit.residence.protection.FlagPermissions;
import com.ghostchu.quickshop.api.event.economy.ShopPurchaseEvent;
import com.ghostchu.quickshop.api.event.management.ShopCreateEvent;
import com.ghostchu.quickshop.api.event.management.ShopPermissionCheckEvent;
import com.ghostchu.quickshop.compatibility.CompatibilityModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

public final class Main extends CompatibilityModule {

  private static final String CREATE_FLAG = "quickshop-create";
  private static final String TRADE_FLAG = "quickshop-trade";
  private boolean whitelist;
  private boolean defaultTrade;

  @Override
  public void init() {

    final Plugin resPlug = getServer().getPluginManager().getPlugin("Residence");
    if(resPlug == null) {
      getLogger().info("Dependency not found: Residence");
      Bukkit.getPluginManager().disablePlugin(this);
      return;
    }

    initFlags();
  }

  public void initFlags() {

    whitelist = getConfig().getBoolean("whitelist-mode");
    defaultTrade = getConfig().getBoolean("trade-default", false);

    FlagPermissions.addFlag(CREATE_FLAG);
    FlagPermissions.addFlag(TRADE_FLAG);
  }

  @EventHandler(ignoreCancelled = true)
  public void onEnable(final PluginEnableEvent event) {

    if (!event.getPlugin().getName().equalsIgnoreCase("Residence")) {
      return;
    }

    initFlags();
  }

  @EventHandler(ignoreCancelled = true)
  public void onPreCreation(final ShopCreateEvent event) {

    if(!event.phase().cancellable()) {
      return;
    }

    final Location shopLoc = event.location();
    final ClaimedResidence residence = Residence.getInstance().getResidenceManager().getByLoc(shopLoc);
    if(residence == null) {
      if(whitelist) {
        event.setCancelled(true, getApi().getTextManager().of(event.user(), "addon.residence.you-cannot-create-shop-in-wildness").forLocale());
      }
      return;
    }
    event.user().getBukkitPlayer().ifPresent(player->{
      if(!hasFlag(residence, shopLoc, player, CREATE_FLAG, false)) {
        event.setCancelled(true, getApi().getTextManager().of(event.user(), "addon.residence.creation-flag-denied").forLocale());
      }
    });
  }

  private boolean hasFlag(final ClaimedResidence residence, final Location shopLoc, final Player player, final String name, final boolean baseDefault) {
    if (ResAdmin.isResAdmin(player)) {
      return true;
    }
    final String worldName = shopLoc.getWorld() != null ? shopLoc.getWorld().getName() : residence.getWorldName();
    FlagPermissions worldPerms = null;
    if (worldName != null) {
      final ResidencePlayer rPlayer = ResidencePlayer.get(player);
      final PermissionGroup group = rPlayer != null ? rPlayer.getGroup(worldName) : null;
      final String groupName = group != null ? group.getGroupName() : null;
      worldPerms = groupName != null
          ? Residence.getInstance().getWorldFlags().getPerms(worldName, groupName)
          : Residence.getInstance().getWorldFlags().getPerms(worldName);
    }
    final boolean worldDefault = worldPerms != null
        ? playerHas(worldPerms, player, worldName, name, baseDefault)
        : baseDefault;
    return playerHas(residence.getPermissions(), player, worldName, name, worldDefault);
  }

  private boolean playerHas(final FlagPermissions permissions, final Player player, final String worldName, final String name, final boolean def) {

    final Flags internalFlag = Flags.getFlag(name);
    if(internalFlag != null) {
      return permissions.playerHas(player, worldName, internalFlag, def);
    }
    return permissions.playerHas(player.getUniqueId(), worldName, name, def);
  }

  @EventHandler(ignoreCancelled = true)
  public void onPurchase(final ShopPurchaseEvent event) {

    final Location shopLoc = event.getShop().bukkitLocation();
    final ClaimedResidence residence = Residence.getInstance().getResidenceManager().getByLoc(shopLoc);
    if(residence == null) {
      return;
    }
    event.getPurchaser().getBukkitPlayer().ifPresent(player->{
      if(!hasFlag(residence, shopLoc, player, TRADE_FLAG, defaultTrade)) {
        event.setCancelled(true, getApi().getTextManager().of(player, "addon.residence.trade-flag-denied").forLocale());
      }
    });
  }

  @EventHandler(ignoreCancelled = true)
  public void permissionOverride(final ShopPermissionCheckEvent event) {

    if(!getConfig().getBoolean("allow-permission-override") || event.shop().isEmpty()) {
      return;
    }
    final Location shopLoc = event.shop().get().bukkitLocation();


    final ClaimedResidence residence = ResidenceApi.getResidenceManager().getByLoc(shopLoc);
    if(residence == null) {
      return;
    }
    if(residence.getOwnerUUID().equals(event.playerUUID())) {
      event.hasPermission(true);
    }
  }

}
