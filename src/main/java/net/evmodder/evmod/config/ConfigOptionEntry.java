package net.evmodder.evmod.config;

import fi.dy.masa.malilib.config.IConfigOptionListEntry;
import net.evmodder.evmod.Main;

//interface ConfigOptionEntry<T extends Enum<T> & IConfigOptionListEntry> extends IConfigOptionListEntry{
interface ConfigOptionEntry extends IConfigOptionListEntry{
	public String getOptionListName();

	default public String getDisplayName(){return Main.MOD_ID+".gui.label."+getOptionListName()+"."+getStringValue();}

//	default public IConfigOptionListEntry cycle(boolean forward){return cycle((T)this, forward);}
	default public IConfigOptionListEntry cycle(boolean forward){return cycle((Enum<?>)this, forward);}

	default public IConfigOptionListEntry fromString(String name){return fromString(this.getClass(), name);}

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
}