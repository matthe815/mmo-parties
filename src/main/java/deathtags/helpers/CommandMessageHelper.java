package deathtags.helpers;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.text.StringTextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextFormatting;

public class CommandMessageHelper {

	/**
	 * Send a gray information chat to a player.
	 * @param player A server player.
	 * @param message The message to send.
	 */
	public static void SendInfo ( PlayerEntity player, String message ) 
	{
		player.sendMessage( 
			new StringTextComponent( message )
			.setStyle( 
				new Style()
			)
		);
	}
	
	/**
	 * Send a gray error chat to a player.
	 * @param player A server player.
	 * @param message The message to send.
	 */
	public static void SendError ( PlayerEntity player, String message ) 
	{
		player.sendMessage( 
			new StringTextComponent( message )
			.setStyle( 
				new Style() 
				.setColor( TextFormatting.RED )
			)
		);
	}
	
}
