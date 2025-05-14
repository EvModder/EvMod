package net.evmodder.EvLib;

public enum Command{
	PING,
	DB_PEARL_FETCH_BY_UUID,
	DB_PEARL_FETCH_BY_XZ,
	DB_PEARL_STORE_BY_UUID,
	DB_PEARL_STORE_BY_XZ,
	DB_PEARL_XZ_KEY_UPDATE,

	DB_MAPART_STORE,

	DB_PLAYER_FETCH_QUIT_TS,
	DB_PLAYER_STORE_QUIT_TS,
	DB_PLAYER_FETCH_JOIN_TS,
	DB_PLAYER_STORE_JOIN_TS,

	DB_PLAYER_STORE_IGNORE,
	DB_PLAYER_STORE_UNIGNORE,
	DB_PLAYER_FETCH_IGNORES,

	P2P_PEARL_REGISTER,
	P2P_PEARL_PULL,

	P2P_CHAT_AS, // Send chat/cmds as another player


	// Commands (0-255)
//	public static final int PING = 0;
//	public static final int EPEARL_TRIGGER = 1;
//	public static final int EPEARL_OWNER_FETCH = 2;//key is pearl entity UUID
//	public static final int EPEARL_OWNER_STORE = 3;
////	public static final int SEND_CHAT_AS = 4;//TODO: send to server (which will pass on to other client) vs send directly to client. many similar such cmds
//	public static final int GET_LIST = 5;

//	// Remaining bytes - command variants
//	public static final int EPEARL_UUID = 0<<8;
//	public static final int EPEARL_XZ = 1<<8;
//	public static final int EPEARL_XZ_KEY_UPDATE = 2<<8;
//
//	public static final int P2P_SEND_CHAT_AS = 0<<8;
//	public static final int P2P_EPEARL_TRIGGER = 0<<8;
}