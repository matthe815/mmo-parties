package deathtags.events;

import deathtags.core.MMOParties;
import deathtags.stats.PlayerStats;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@EventBusSubscriber
public class EventHandler {
	
	@SubscribeEvent
	public void onPlayerJoined(PlayerLoggedInEvent event)
	{
		System.out.println("Player connected to the server.");
		
		PlayerEntity player = (PlayerEntity) event.getPlayer();
		
		if (!MMOParties.PlayerStats.containsKey(player))
			MMOParties.PlayerStats.put(player, new PlayerStats ( player ));
	}
	
	@SubscribeEvent
	public void onPlayerLeave(PlayerLoggedOutEvent event)
	{
		System.out.println("Player disconnected from the server.");
		
		if (MMOParties.GetStatsByName(event.getPlayer().getName().getFormattedText()) == null)
			return;
		
		PlayerStats playerStats = MMOParties.GetStatsByName( event.getPlayer().getName().getFormattedText() );
		PlayerEntity player = (PlayerEntity) event.getPlayer();
		
		// Leave a party if you're currently in one.
		if ( playerStats.InParty() ) playerStats.party.Leave(player);
		
		MMOParties.PlayerStats.remove(player);
	}

	@SubscribeEvent(priority=EventPriority.HIGHEST)
	public void OnPlayerHurt(LivingHurtEvent event)
	{
		if (event.getEntity().world.isRemote) return; // Perform on the server only.
		if (! (event.getEntityLiving() instanceof PlayerEntity) || ! (event.getSource().getTrueSource() instanceof PlayerEntity) ) return; // Only perform on players.
		
		PlayerEntity player = (PlayerEntity) event.getEntityLiving();
		PlayerEntity source = (PlayerEntity) event.getSource().getTrueSource();
		
		PlayerStats playerStats = MMOParties.GetStatsByName( player.getName().getFormattedText() );
		PlayerStats sourceStats = MMOParties.GetStatsByName( source.getName().getFormattedText() );
		
		if (!playerStats.InParty() || !sourceStats.InParty()) return; // Nothing matters if both players aren't in a party.
		if (playerStats.party.IsMember( source )) event.setCanceled(true); // Cancel received damage if it's from a party member.
		
		if (playerStats.party.opposer == null) {
			playerStats.party.opposer = sourceStats.party;
			playerStats.party.opposer.opposer = playerStats.party;
		}
	}
	
	@SubscribeEvent
	public void OnPlayerMove(PlayerTickEvent event)
	{
		if (event.player.world.isRemote)
			return;
		
		PlayerEntity player = (PlayerEntity)event.player;
		PlayerStats stats = MMOParties.GetStatsByName(player.getName().getFormattedText());
		
		// Process teleporting.
		if (stats.teleportTicks > 0) {
			stats.teleportTicks --;
			
			if (stats.teleportTicks <= 0) stats.CommenceTeleport(); // Teleport the player.
		}

		if (stats.party != null) MMOParties.PlayerStats.get(player).party.SendPartyMemberData(player, false); // Sync the player.
		
		if (!player.isSpectator()) return;
		
		if (stats.deathPosition != null && player.getPosition().compareTo(stats.deathPosition) != 0)
			player.setPosition(stats.deathPosition.getX(), stats.deathPosition.getY(), stats.deathPosition.getZ());
	}
}
