package com.ghostchu.quickshop.compatibility.residence;

import com.bekvon.bukkit.residence.Residence;
import com.bekvon.bukkit.residence.api.ResidenceApi;
import com.bekvon.bukkit.residence.containers.Flags;
import com.bekvon.bukkit.residence.containers.ResAdmin;
import com.bekvon.bukkit.residence.containers.ResidencePlayer;
import com.bekvon.bukkit.residence.economy.rent.RentedLand;
import com.bekvon.bukkit.residence.event.ResidenceDeleteEvent;
import com.bekvon.bukkit.residence.event.ResidenceRentEvent;
import com.bekvon.bukkit.residence.permissions.PermissionGroup;
import com.bekvon.bukkit.residence.protection.ClaimedResidence;
import com.bekvon.bukkit.residence.protection.FlagPermissions;
import com.ghostchu.quickshop.api.event.economy.ShopPurchaseEvent;
import com.ghostchu.quickshop.api.event.management.ShopCreateEvent;
import com.ghostchu.quickshop.api.event.management.ShopPermissionCheckEvent;
import com.ghostchu.quickshop.api.shop.Shop;
import com.ghostchu.quickshop.compatibility.CompatibilityModule;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.server.PluginEnableEvent;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class Main extends CompatibilityModule {

  private static final String CREATE_FLAG = "quickshop-create";
  private static final String TRADE_FLAG = "quickshop-trade";
  private boolean whitelist;
  private boolean defaultTrade;
  private boolean deleteShopsOnRentEnd;
  private boolean deleteShopsOnResidenceDelete;

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
    deleteShopsOnRentEnd = getConfig().getBoolean("delete-shops-on-rent-end", true);
    deleteShopsOnResidenceDelete = getConfig().getBoolean("delete-shops-on-residence-delete", false);

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

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onResidenceRentEnd(final ResidenceRentEvent event) {
    if (!deleteShopsOnRentEnd) {
      return;
    }
    final ResidenceRentEvent.RentEventType cause = event.getCause();
    if (cause != ResidenceRentEvent.RentEventType.RENT_EXPIRE && cause != ResidenceRentEvent.RentEventType.UNRENTABLE) {
      return;
    }
    final ClaimedResidence res = event.getResidence();
    if (res == null || res.getName() == null || res.getWorldName() == null) {
      return;
    }
    final String resName = res.getName();
    final String worldName = res.getWorldName();
    final UUID landlordUuid = res.getOwnerUUID();
    final RentedLand rentedLand = res.getRentedLand();
    final UUID formerRenterUuid = rentedLand != null ? rentedLand.getUniqueId() : null;

    // Defer 1 tick: RENT_EXPIRE fires BEFORE Residence checks AutoPay renewal.
    // Only delete shops if the residence is actually no longer rented by the former renter.
    Bukkit.getGlobalRegionScheduler().runDelayed(this, task -> {
      final ClaimedResidence live = Residence.getInstance().getResidenceManager().getByName(resName);
      if (live == null) {
        return;
      }
      if (live.isRented()) {
        final RentedLand currentRent = live.getRentedLand();
        if (currentRent != null && formerRenterUuid != null && formerRenterUuid.equals(currentRent.getUniqueId())) {
          // AutoPay succeeded; renter still holds the residence
          return;
        }
      }
      final int removed = deleteShopsInResidence(live, worldName, landlordUuid, true,
          "Residence: shop deleted because rent ended in " + resName);
      if (removed > 0) {
        getLogger().info("Removed " + removed + " QuickShop shop(s) in '" + resName + "' after rent ended.");
      }
    }, 1L);
  }

  @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
  public void onResidenceDelete(final ResidenceDeleteEvent event) {
    if (!deleteShopsOnResidenceDelete) {
      return;
    }
    final ClaimedResidence res = event.getResidence();
    if (res == null || res.getWorldName() == null) {
      return;
    }
    final int removed = deleteShopsInResidence(res, res.getWorldName(), null, false,
        "Residence: shop deleted because residence '" + res.getName() + "' was deleted");
    if (removed > 0) {
      getLogger().info("Removed " + removed + " QuickShop shop(s) because residence '" + res.getName() + "' was deleted.");
    }
  }

  private int deleteShopsInResidence(final ClaimedResidence residence, final String worldName, final UUID preserveOwnerUuid,
      final boolean exactResidenceMatch, final String reason) {
    final List<Shop> worldShops = new ArrayList<>(getApi().getShopManager().getShopsInWorld(worldName));
    int count = 0;
    for (final Shop shop : worldShops) {
      final Location loc = shop.bukkitLocation();
      if (loc == null || !residence.containsLoc(loc)) {
        continue;
      }
      if (exactResidenceMatch) {
        // Do not accidentally delete shops inside a separately-managed subzone when a parent residence unrents
        final ClaimedResidence deepest = Residence.getInstance().getResidenceManager().getByLoc(loc);
        if (deepest == null || !deepest.getName().equalsIgnoreCase(residence.getName())) {
          continue;
        }
      }
      if (preserveOwnerUuid != null) {
        final UUID shopOwner = shop.getOwner().getUniqueIdIfRealPlayer().orElse(null);
        if (preserveOwnerUuid.equals(shopOwner)) {
          continue;
        }
      }
      recordDeletion(null, shop, reason);
      Bukkit.getRegionScheduler().run(this, loc, task -> getApi().getShopManager().deleteShop(shop));
      count++;
    }
    return count;
  }

}
