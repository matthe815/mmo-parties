package deathtags.networking;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Supplier;

import deathtags.core.MMOParties;
import deathtags.stats.Party;
import net.minecraft.network.PacketBuffer;
import net.minecraftforge.fml.network.NetworkEvent;

public class MessageUpdateParty {

	public final String members;


	public MessageUpdateParty(String members) 
	{
		this.members = members;
	}

	public static MessageUpdateParty decode(PacketBuffer buf) 
	{
		return new MessageUpdateParty(buf.readString(1000));
	}

	public static void encode(MessageUpdateParty msg, PacketBuffer buf) 
	{
		buf.writeString(msg.members);
	}

	public static class Handler
	{
		public static void handle(final MessageUpdateParty pkt, Supplier<NetworkEvent.Context> ctx)
		{
			System.out.println("Party update message");

			List<String> players = new ArrayList<String>(Arrays.asList(pkt.members.split(",")));

			if (MMOParties.localParty == null)
				MMOParties.localParty = new Party();

			MMOParties.localParty.local_players = players;		
		}
	}
}