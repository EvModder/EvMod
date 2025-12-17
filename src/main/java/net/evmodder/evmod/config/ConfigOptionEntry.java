package net.evmodder.evmod.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import fi.dy.masa.malilib.util.StringUtils;
import net.evmodder.evmod.Main;

//interface ConfigOptionEntry<T extends Enum<T> & IConfigOptionListEntry> extends IConfigOptionListEntry{
interface ConfigOptionEntry extends IConfigOptionListEntry{
	public String getOptionListName();

	public default String getDisplayName(){return StringUtils.translate(Main.MOD_ID+".gui.label."+getOptionListName()+"."+getStringValue());}

//	private static <T extends Enum<T> & IConfigOptionListEntry> T cycle(T t, boolean forward){
	private static IConfigOptionListEntry cycle(Enum<?> t, boolean forward){
		int id = t.ordinal();
		Enum<?>[] values = t.getClass().getEnumConstants();
		assert id >= 0 && id < values.length;
		return (IConfigOptionListEntry)values[forward ? (++id==values.length ? 0 : id) : (--id==0 ? values.length-1 : id)];
	}

//	private static <T extends Enum<T> & IConfigOptionListEntry> T fromString(Class<T> clazz, String name){
	private static IConfigOptionListEntry fromString(Class<?> clazz, String name){
		for(var v : clazz.getEnumConstants()) if(((IConfigOptionListEntry)v).getStringValue().equalsIgnoreCase(name)) return (IConfigOptionListEntry)v;
		throw new IllegalArgumentException(clazz.getName()+": Invalid argument provided to fromString(): "+name);
	}

//	public default IConfigOptionListEntry cycle(boolean forward){return cycle((T)this, forward);}
	public default IConfigOptionListEntry cycle(boolean forward){return cycle((Enum<?>)this, forward);}

	public default IConfigOptionListEntry fromString(String name){return fromString(this.getClass(), name);}
}