package net.evmodder.EvLib;

public class Commands{
	// Commands (0-255)
	public static final int PING = 0;
	public static final int EPEARL_TRIGGER = 1;
	public static final int EPEARL_OWNER_FETCH = 2;//key is pearl entity UUID
	public static final int EPEARL_OWNER_STORE = 3;
	public static final int SEND_CHAT_AS = 4;//TODO: send to server (which will pass on to other client) vs send directly to client. many similar such cmds
	public static final int GET_LIST = 5;

	// Remaining bytes - command variants
	public static final int EPEARL_UUID = 0<<8;
	public static final int EPEARL_XZ = 1<<8;
	public static final int EPEARL_XZ_KEY_UPDATE = 2<<8;
}