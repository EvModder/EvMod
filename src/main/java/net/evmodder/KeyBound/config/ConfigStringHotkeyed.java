package net.evmodder.KeyBound.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fi.dy.masa.malilib.MaLiLib;
import fi.dy.masa.malilib.config.options.ConfigString;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeybindMulti;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;
import fi.dy.masa.malilib.util.JsonUtils;
import fi.dy.masa.malilib.util.StringUtils;

public class ConfigStringHotkeyed extends ConfigString implements IHotkey{
	protected final IKeybind keybind;

	public ConfigStringHotkeyed(String name, String defaultValue, String defaultHotkey){
		this(name, defaultValue, defaultHotkey, KeybindSettings.DEFAULT, name + " Comment?", StringUtils.splitCamelCase(name), name);
	}

	public ConfigStringHotkeyed(String name, String defaultValue, String defaultHotkey, String comment){
		this(name, defaultValue, defaultHotkey, KeybindSettings.DEFAULT, comment, StringUtils.splitCamelCase(name), name);
	}

	public ConfigStringHotkeyed(String name, String defaultValue, String defaultHotkey, String comment, String prettyName){
		this(name, defaultValue, defaultHotkey, KeybindSettings.DEFAULT, comment, prettyName, name);
	}

	public ConfigStringHotkeyed(String name, String defaultValue, String defaultHotkey, String comment, String prettyName, String translatedName){
		this(name, defaultValue, defaultHotkey, KeybindSettings.DEFAULT, comment, prettyName, translatedName);
	}

	public ConfigStringHotkeyed(String name, String defaultValue, String defaultHotkey, KeybindSettings settings){
		this(name, defaultValue, defaultHotkey, settings, name + " Comment?", StringUtils.splitCamelCase(name), name);
	}

	public ConfigStringHotkeyed(String name, String defaultValue, String defaultHotkey, KeybindSettings settings, String comment){
		this(name, defaultValue, defaultHotkey, settings, comment, StringUtils.splitCamelCase(name), name);
	}

	public ConfigStringHotkeyed(String name, String defaultValue, String defaultHotkey, KeybindSettings settings, String comment, String prettyName){
		this(name, defaultValue, defaultHotkey, settings, comment, prettyName, name);
	}

	public ConfigStringHotkeyed(String name, String defaultValue, String defaultHotkey, KeybindSettings settings, String comment, String prettyName,
			String translatedName){
		super(name, defaultValue, comment, prettyName, translatedName);

		keybind = KeybindMulti.fromStorageString(defaultHotkey, settings);
//		keybind.setCallback(() -> {
//			InfoUtils.printBooleanConfigToggleMessage(config.getPrettyName(), config.getBooleanValue());
//		});
	}

	@Override public IKeybind getKeybind(){
		return keybind;
	}

	@Override public ConfigStringHotkeyed translatedName(String translatedName){
		return (ConfigStringHotkeyed)super.translatedName(translatedName);
	}

	@Override public ConfigStringHotkeyed apply(String translationPrefix){
		return (ConfigStringHotkeyed)super.apply(translationPrefix);
	}

	@Override public boolean isModified(){
		// Note: calling isModified() for the IHotkey here directly would not work with multi-type configs like the FeatureToggle in Tweakeroo!
		// Thus we need to get the IKeybind and call it for that specifically.
		return super.isModified() || getKeybind().isModified();
	}

	@Override public void resetToDefault(){
		super.resetToDefault();
		keybind.resetToDefault();
	}

	@Override public void setValueFromJsonElement(JsonElement element){
		try{
			if(element.isJsonObject()){
				JsonObject obj = element.getAsJsonObject();

				if(JsonUtils.hasString(obj, "value")){
					super.setValueFromJsonElement(obj.get("value"));
				}
				if(JsonUtils.hasObject(obj, "hotkey")){
					keybind.setValueFromJsonElement(obj.getAsJsonObject("hotkey"));
				}
			}
			else if(element.isJsonPrimitive()){
				super.setValueFromJsonElement(element);
			}
		}
		catch(Exception e){
			MaLiLib.LOGGER.warn("Failed to set config value for '{}' from the JSON element '{}'", getName(), element, e);
		}
	}

	@Override public JsonElement getAsJsonElement(){
		JsonObject obj = new JsonObject();
		obj.add("string", super.getAsJsonElement());
		obj.add("hotkey", getKeybind().getAsJsonElement());
		return obj;
	}
}