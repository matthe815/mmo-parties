package deathtags.stats;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import deathtags.core.MMOParties;
import deathtags.helpers.CommandMessageHelper;
import deathtags.networking.MessageSendMemberData;
import deathtags.networking.MessageUpdateParty;
import deathtags.networking.PartyPacketDataBuilder;
import epicsquid.superiorshields.capability.shield.IShieldCapability;
import epicsquid.superiorshields.capability.shield.SuperiorShieldsCapabilityManager;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.Loader;

public class Party extends PlayerGroup
{	
	public List<EntityPlayerMP> players = new ArrayList<EntityPlayerMP>();
	public List<String> local_players = new ArrayList<String>();
	public List<String> playersOffline = new ArrayList<>();

	public Map<String, PartyMemberData> data = new HashMap<String, PartyMemberData>();

	public Party(EntityPlayerMP player)
	{
		leader = player;
		players.add(player);
		playersOffline.add(player.getName());
		SendUpdate();
	}
	
	public Party() {}
	
	/**
	 * Create a new party and set the leader to a provided leader. Can error and do nothing.
	 * @param leader The player to attempt to make leader.
	 */
	public static void Create ( EntityPlayerMP leader ) {
		PlayerStats leaderStats = MMOParties.GetStatsByName( leader.getName() );
		
		if (leaderStats.InParty()) { CommandMessageHelper.SendError( leader, "You are already in a party." ); return; }
		leaderStats.party = new Party (leader); // Set the leaders' party.
		
		CommandMessageHelper.SendInfo( leader ,  "You have created a new party." );
	}
	
	/**
	 * Invite a player to the party.
	 * @param player Target player.
	 */
	public void Invite ( EntityPlayerMP invoker, EntityPlayerMP player ) {
		PlayerStats targetPlayer = MMOParties.GetStatsByName( player.getName() );
		PlayerStats invokerPlayer = MMOParties.GetStatsByName( invoker.getName() );
		
		if ( invokerPlayer.party.leader != invoker ) // Only the leader may invite.
			{ CommandMessageHelper.SendError( invoker , "You must be the leader of a party to invite others." ); return; }
		
		if ( targetPlayer.InParty () ) // Players already in a party may not be invited.
			{ CommandMessageHelper.SendError( invoker, String.format( "%s is already in a party.", player.getName().toString() ) ); return; }
		
		targetPlayer.partyInvite = this;
		
		CommandMessageHelper.SendInfo( invoker, String.format( "You have invited %s to the party." , player.getName () ) );
		CommandMessageHelper.SendInfo( player , String.format ( "%s has invited you to join their party.", invoker.getName() ) );
	}
	
	/**
	 * Join a player to this party.
	 * @param player The target.
	 */
	public void Join ( EntityPlayerMP player )
	{
		if (this.players.size() >= 4)
		 { CommandMessageHelper.SendError(player, "This party is currently full."); return; }
			
		this.players.add(player);
		
		PlayerStats stats = MMOParties.GetStatsByName( player.getName() );
		
		stats.party = this;
		stats.partyInvite = null; // Clear the party invite to prevent potential double joining.
		
		Broadcast( String.format( "%s has joined the party!", player.getName() ) );
		
		for ( EntityPlayerMP member : players ) SendPartyMemberData( member, true ); // Update all of the party members.
		
		SendUpdate(); // Send a player stat update.
	}
	
	public void Leave (EntityPlayerMP player)
	{
		this.players.remove(player);
		
		Broadcast( String.format( "%s has left the party..", player.getName() ) );
		
		for ( EntityPlayerMP member : players ) SendPartyMemberData ( member, true );
		SendPartyMemberData(player, true); // Send one last update.
		
		if (player == this.leader && players.size() > 0) this.leader = players.get(0); // If the player was the leader, then assign a new leader.

		SendUpdate();
		
		// Disband the party of 1 player.
		if (players.size() == 1) Disband();
	}
	
	/**
	 * Disband the party.
	 */
	public void Disband ()
	{
		Broadcast("The party has been disbanded.");
		
		leader = null;
		
		for (EntityPlayerMP member : players) {
			PlayerStats stats = MMOParties.GetStatsByName ( member.getName() );
			stats.party = null;
		}
	}
	
	/**
	 * Broadcast a message to every member within the group.
	 * @param message The message to send.
	 */
	@Override
	public void Broadcast ( String message )
	{
		for (EntityPlayerMP member : players) CommandMessageHelper.SendInfo( member, message );
	}
	
	@Override
	public EntityPlayerMP[] GetOnlinePlayers()
	{
		return players.toArray(new EntityPlayerMP[] {});
	}
	
	@Override
	public void SendUpdate()
	{
		String[] playerNames = new String[players.size()];
		int i = 0;
		
		for (EntityPlayerMP party_player : players) {
			playerNames[i] = party_player.getName();
			i++;
		}
		
		for (EntityPlayerMP party_player : players) {
			MMOParties.network.sendTo(new MessageUpdateParty(String.join(",", playerNames)), party_player);
		}
	}
	
	@Override
	public void SendPartyMemberData(EntityPlayerMP member, boolean bypassLimit)
	{
		if (!IsDataDifferent(member) && !bypassLimit) return; // Stop here if there's no bypass or data difference.
		
		if (!this.pings.containsKey(member.getName()))
			this.pings.put(member.getName(), new PlayerPing(member, 0, 0, 0, bypassLimit, 0, 0, 0, 0));

		if (Loader.isModLoaded("superiorshields")) {
			IShieldCapability shields = member.getCapability(SuperiorShieldsCapabilityManager.shieldCapability, null);

			this.pings.get(member.getName()).Update(member.getHealth(), member.getMaxHealth(), member.getTotalArmorValue(), 
					this.leader==member, member.getAbsorptionAmount(), shields.getCurrentHp(), shields.getMaxHp());
		} else
			this.pings.get(member.getName()).Update(member.getHealth(), member.getMaxHealth(), member.getTotalArmorValue(), 
					this.leader==member, member.getAbsorptionAmount(), 0, 0);

		PartyPacketDataBuilder builder = new PartyPacketDataBuilder()
				.SetPlayer(member.getName())
				.SetHealth(member.getHealth())
				.SetMaxHealth(member.getMaxHealth())
				.SetLeader(this.leader==member)
				.SetArmor(member.getTotalArmorValue())
				.SetAbsorption(member.getAbsorptionAmount())
				.SetHunger(member.getFoodStats().getFoodLevel());

		if (Loader.isModLoaded("superiorshields")) {
			IShieldCapability shields = member.getCapability(SuperiorShieldsCapabilityManager.shieldCapability, null);

			builder
			.SetShields(shields.getCurrentHp())
			.SetMaxShields(shields.getMaxHp());
		}

		for (EntityPlayerMP player : players) {
			MMOParties.network.sendTo(
				new MessageSendMemberData(builder),
				player
			);	
		}
	}
	
	@Override
	public boolean IsDataDifferent(EntityPlayerMP player)
	{
		if (!this.pings.containsKey(player.getName()) || this.pings.get(player.getName()).IsDifferent(player))
			return true;
		
		return false;
	}

	@Override
	public boolean IsMember(EntityPlayerMP player) 
	{
		for (EntityPlayerMP member : players) {
			if (member.getName().equals(player.getName()))
				return true;
		}
		
		return false;
	}

	public boolean IsMemberOffline(EntityPlayer player)
	{
		return playersOffline.contains(player.getName());
	}

	/**
	 * Teleport to a player within your party.
	 * @param player Player to teleport.
	 * @param target Player to teleport to.
	 */
	public void Teleport(EntityPlayerMP player, EntityPlayerMP target) {
		if ( ! IsMember ( target ) ) 
			{ CommandMessageHelper.SendError(player, "You may only teleport to players within your party."); return; }
		
		MMOParties.GetStatsByName(player.getName()).StartTeleport (target);
	}
}
