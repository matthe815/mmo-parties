package deathtags.commands;

import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;

import deathtags.core.MMOParties;
import deathtags.helpers.CommandMessageHelper;
import deathtags.stats.Party;
import deathtags.stats.PlayerStats;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.entity.player.ServerPlayerEntity;

public class PartyCommand {
	
	public static LiteralArgumentBuilder<CommandSource> register () {
		return Commands.literal("party")
				.requires(cs -> cs.hasPermissionLevel(0))
				.then(Commands.argument("sub", StringArgumentType.string()).executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "sub"), null))
						.suggests(
								new SuggestionProvider<CommandSource>() {
									
									@Override
									public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSource> arg0, SuggestionsBuilder arg1)
											throws CommandSyntaxException {
										return arg1.suggest("invite").suggest("tp").suggest("accept").suggest("deny").suggest("leader").suggest("disband").buildFuture();
									}
								}
						).then(
								Commands.argument("player", StringArgumentType.string())
								.executes(ctx -> run(ctx, StringArgumentType.getString(ctx, "sub"), StringArgumentType.getString(ctx, "player")))
								.suggests(new SuggestionProvider<CommandSource>() {
									
									@Override
									public CompletableFuture<Suggestions> getSuggestions(CommandContext<CommandSource> arg0, SuggestionsBuilder arg1)
											throws CommandSyntaxException {
										// TODO Auto-generated method stub
										SuggestionsBuilder builder = arg1;
										
										for (String playerName : arg0.getSource().getServer().getPlayerList().getOnlinePlayerNames())
										{
											if (arg0.getSource().asPlayer().getName().getFormattedText() != playerName)
												arg1.suggest(playerName);
										}
										
										return builder.buildFuture();
									}
								})
							));
	}
	
	private static int run(CommandContext<CommandSource> context, String sub, String targetStr) throws CommandSyntaxException {
		ServerPlayerEntity player = (ServerPlayerEntity) context.getSource().asPlayer();
		ServerPlayerEntity target = context.getSource().getServer().getPlayerList().getPlayerByUsername(targetStr);

		if (player.world.isRemote) return 0; // Only perform operations on the server side.
			
		PlayerStats stats = MMOParties.GetStatsByName(player.getName().getFormattedText());
		
		switch (sub) {
			case "tp":
				if (!stats.InParty()) 
					{ CommandMessageHelper.SendError( player, "You must be in a party to teleport someone." ); return 0; }
				
				stats.party.Teleport ( player, target );
				break;
			
			case "create":
				Party.Create( player ); // Create a new party for the player.
				break;
			
			case "invite":
				if (!stats.InParty()) Party.Create ( player ); // Create a party to invite with if not existant.
				
				stats.party.Invite ( player, target ); // Send an invite to the target player.
				break;
			
			case "accept":				
				if (stats.partyInvite == null)
					{ CommandMessageHelper.SendError( player , "You do not currently have an invite." ); return 0; }
				
				stats.partyInvite.Join(player); // Accept an invite to a player.	
				break;
				
			case "deny":
				if (stats.partyInvite == null)
					{ CommandMessageHelper.SendError( player , "You do not currently have an invite." ); return 0; }

				stats.partyInvite = null; // Deny the invite.
				break;
			
			case "leave":
				if (!stats.InParty())
					{ CommandMessageHelper.SendError( player, "You are not currently in a party." ); return 0; }
				
				stats.party.Leave(player); // Perform the leave actions.
				stats.party = null;
				
				CommandMessageHelper.SendInfo( player, "You have left your party." );
				break;
				
			case "leader":
				if (!stats.InParty())
					{ CommandMessageHelper.SendError( player, "You are not currently in a party." ); return 0; }
				
				if (stats.party.leader != player) // Only the leader can promote.
					{ CommandMessageHelper.SendError( player, "Only the leader may promote members." ); return 0; }
				
				stats.party.leader = target; // Assign leadership.
				stats.party.Broadcast(String.format( "%s has been given leadership of the party. ", target.getName() ) );
				break;
				
			case "disband":
				if (!stats.InParty())
					{ CommandMessageHelper.SendError( player, "You are not currently in a party." ); return 0; }
			
				if (stats.party.leader != player) // Only the leader can promote.
					{ CommandMessageHelper.SendError( player, "Only the leader may disband." ); return 0; }
			
				stats.party.Disband();
				break;
			default:
				break;
		}
		
		return 0;
	}
}
