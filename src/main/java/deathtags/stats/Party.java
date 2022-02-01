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
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.world.GameType;
import net.minecraftforge.fml.network.NetworkDirection;

public class Party extends PlayerGroup
{	
	public List<PlayerEntity> players = new ArrayList<PlayerEntity>();
	public List<String> local_players = new ArrayList<String>();
	
	public Map<Integer, Party> local_parties = new HashMap<Integer, Party>();
	public Map<String, PartyMemberData> data = new HashMap<String, PartyMemberData>();

	public Party(PlayerEntity player)
	{
		leader = player;
		players.add(player);
		SendUpdate();
	}
	
	public Party()
	{
		
	}
	
	/**
	 * Create a new party and set the leader to a provided leader. Can error and do nothing.
	 * @param leader The player to attempt to make leader.
	 */
	public static void Create ( PlayerEntity leader ) {
		PlayerStats leaderStats = MMOParties.GetStatsByName( leader.getName().getFormattedText() );
		
		if (leaderStats.InParty()) { CommandMessageHelper.SendError( leader, "You are already in a party." ); return; }
		leaderStats.party = new Party (leader); // Set the leaders' party.
		
		CommandMessageHelper.SendInfo( leader ,  "You have created a new party." );
	}
	
	/**
	 * Invite a player to the party.
	 * @param player Target player.
	 */
	public void Invite ( PlayerEntity invoker, PlayerEntity player ) {
		PlayerStats targetPlayer = MMOParties.GetStatsByName( player.getName().getFormattedText() );
		PlayerStats invokerPlayer = MMOParties.GetStatsByName( invoker.getName().getFormattedText() );
		
		if ( invokerPlayer.party.leader != invoker ) // Only the leader may invite.
			{ CommandMessageHelper.SendError( invoker , "You must be the leader of a party to invite others." ); return; }
		
		if ( targetPlayer.InParty () ) // Players already in a party may not be invited.
			{ CommandMessageHelper.SendError( invoker, String.format( "%s is already in a party.", player.getName().getFormattedText() ) ); return; }
		
		targetPlayer.partyInvite = this;
		
		CommandMessageHelper.SendInfo( invoker, String.format( "You have invited %s to the party." , player.getName().getFormattedText() ) );
		CommandMessageHelper.SendInfo( player , String.format ( "%s has invited you to join their party.", invoker.getName().getFormattedText() ) );
	}
	
	/**
	 * Join a player to this party.
	 * @param player The target.
	 */
	public void Join ( PlayerEntity player )
	{
		if (this.players.size() >= 4)
		 { CommandMessageHelper.SendError(player, "This party is currently full."); return; }
			
		this.players.add(player);
		
		PlayerStats stats = MMOParties.GetStatsByName( player.getName().getFormattedText() );
		
		stats.party = this;
		stats.partyInvite = null; // Clear the party invite to prevent potential double joining.
		
		Broadcast( String.format( "%s has joined the party!", player.getName().getFormattedText() ) );
		
		for ( PlayerEntity member : players ) SendPartyMemberData( member, true ); // Update all of the party members.
		
		SendUpdate(); // Send a player stat update.
	}
	
	public void Leave (PlayerEntity player)
	{
		this.players.remove(player);
		
		Broadcast( String.format( "%s has left the party..", player.getName().getFormattedText() ) );
		
		for ( PlayerEntity member : players ) SendPartyMemberData ( member, true );
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
		
		for (PlayerEntity member : players) {
			PlayerStats stats = MMOParties.GetStatsByName ( member.getName().getFormattedText() );
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
		for (PlayerEntity member : players) CommandMessageHelper.SendInfo( member, message );
	}
	
	@Override
	public PlayerEntity[] GetOnlinePlayers()
	{
		return players.toArray(new PlayerEntity[] {});
	}
	
	@Override
	public void SendUpdate()
	{
		String[] playerNames = new String[players.size()];
		int i = 0;
		
		for (PlayerEntity party_player : players) {
			playerNames[i] = party_player.getName().getFormattedText();
			i++;
		}
		
		for (PlayerEntity party_player : players) {
			if (!(party_player instanceof ServerPlayerEntity)) return;
			
			MMOParties.network.sendTo(new MessageUpdateParty(String.join(",", playerNames)), ((ServerPlayerEntity)party_player).connection.netManager, NetworkDirection.PLAY_TO_CLIENT);
		}
	}
	
	@Override
	public void SendPartyMemberData(PlayerEntity member, boolean bypassLimit)
	{
		if (IsDataDifferent(member) || bypassLimit)
		{	
			if (!this.pings.containsKey( member.getName().getFormattedText() ))
				this.pings.put(member.getName().getFormattedText(), new PlayerPing(member, 0, 0, 0, bypassLimit, 0, 0, 0, 0));
			
			this.pings.get( member.getName().getFormattedText() ).Update(member.getHealth(), member.getMaxHealth(), member.getTotalArmorValue(), 
					this.leader==member, member.getAbsorptionAmount(), 0, 0);	
			
			for (PlayerEntity party_player : players) {
				if (!(party_player instanceof ServerPlayerEntity)) return;
						
				MMOParties.network.sendTo(						
					new MessageSendMemberData(
						new PartyPacketDataBuilder ()
						.SetPlayer(member.getName().getFormattedText())
						.SetHealth(member.getHealth())
						.SetMaxHealth(member.getMaxHealth())
						.SetArmor(member.getTotalArmorValue())
						.SetLeader(this.leader==member)
						.SetAbsorption(member.getAbsorptionAmount())
						.SetHunger(member.getFoodStats().getFoodLevel())
				), ((ServerPlayerEntity)party_player).connection.netManager, NetworkDirection.PLAY_TO_CLIENT);
			}
		}
	}
	
	@Override
	public boolean IsDataDifferent(PlayerEntity player)
	{
		if (!this.pings.containsKey( player.getName().getFormattedText() ) || this.pings.get( player.getName().getFormattedText() ).IsDifferent(player))
			return true;
		
		return false;
	}

	@Override
	public boolean IsMember(PlayerEntity player) 
	{
		for (PlayerEntity member : players) {
			if (member.getName().equals(player.getName()))
				return true;
		}
		
		return false;
	}

	@Override
	public boolean IsAllDead() 
	{
		int numDead = 0;
		
		for (PlayerEntity player : this.players) {
			if (player.isSpectator())
				numDead++;
		}
		
		return numDead == this.players.size();
	}
	
	@Override
	public void ReviveAll() 
	{
		for (PlayerEntity player : this.players) {
			if (player.isSpectator()) {
				player.respawnPlayer();
				player.setGameType(GameType.SURVIVAL);
			}
		}
	}

	@Override
	public String GetGroupAlias() {
		return "party";
	}

	/**
	 * Teleport to a player within your party.
	 * @param player Player to teleport.
	 * @param target Player to teleport to.
	 */
	public void Teleport(PlayerEntity player, PlayerEntity target) {
		if ( ! IsMember ( target ) ) 
			{ CommandMessageHelper.SendError(player, "You may only teleport to players within your party."); return; }
		
		MMOParties.GetStatsByName( player.getName().getFormattedText() ).StartTeleport (target);
	}
}
