package net.evmodder.KeyBound.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import fi.dy.masa.malilib.MaLiLib;
import fi.dy.masa.malilib.config.ConfigType;
import fi.dy.masa.malilib.config.IConfigSlider;
import fi.dy.masa.malilib.config.options.ConfigBase;
import fi.dy.masa.malilib.hotkeys.IHotkey;
import fi.dy.masa.malilib.hotkeys.IKeybind;
import fi.dy.masa.malilib.hotkeys.KeybindMulti;
import fi.dy.masa.malilib.hotkeys.KeybindSettings;
import fi.dy.masa.malilib.util.JsonUtils;
import fi.dy.masa.malilib.util.StringUtils;

public class ConfigYawPitchHotkeyed extends ConfigBase<ConfigYawPitchHotkeyed> implements IHotkey, IConfigSlider{
	final IKeybind keybind;
	final float defaultYaw, defaultPitch;
	float yaw; // -180 to +180
	float pitch; // -90 to +90
	private boolean useSlider;

	public ConfigYawPitchHotkeyed(String name, int defaultYaw, int defaultPitch, String defaultHotkey){
		this(name, defaultYaw, defaultPitch, defaultHotkey, KeybindSettings.DEFAULT, name + " Comment?", StringUtils.splitCamelCase(name), name);
	}

	public ConfigYawPitchHotkeyed(String name, float defaultYaw, float defaultPitch, String defaultHotkey, String comment){
		this(name, defaultYaw, defaultPitch, defaultHotkey, KeybindSettings.DEFAULT, comment, StringUtils.splitCamelCase(name), name);
	}

	public ConfigYawPitchHotkeyed(String name, float defaultYaw, float defaultPitch, String defaultHotkey, String comment, String prettyName){
		this(name, defaultYaw, defaultPitch, defaultHotkey, KeybindSettings.DEFAULT, comment, prettyName, name);
	}

	public ConfigYawPitchHotkeyed(String name, float defaultYaw, float defaultPitch, String defaultHotkey, String comment, String prettyName, String translatedName){
		this(name, defaultYaw, defaultPitch, defaultHotkey, KeybindSettings.DEFAULT, comment, prettyName, translatedName);
	}

	public ConfigYawPitchHotkeyed(String name, float defaultYaw, float defaultPitch, String defaultHotkey, KeybindSettings settings){
		this(name, defaultYaw, defaultPitch, defaultHotkey, settings, name + " Comment?", StringUtils.splitCamelCase(name), name);
	}

	public ConfigYawPitchHotkeyed(String name, float defaultYaw, float defaultPitch, String defaultHotkey, KeybindSettings settings, String comment){
		this(name, defaultYaw, defaultPitch, defaultHotkey, settings, comment, StringUtils.splitCamelCase(name), name);
	}

	public ConfigYawPitchHotkeyed(String name, float defaultYaw, float defaultPitch, String defaultHotkey, KeybindSettings settings, String comment, String prettyName){
		this(name, defaultYaw, defaultPitch, defaultHotkey, settings, comment, prettyName, name);
	}

	public ConfigYawPitchHotkeyed(String name, float defaultYaw, float defaultPitch, String defaultHotkey, KeybindSettings settings, String comment, String prettyName,
			String translatedName){
		super(ConfigType.FLOAT, name, comment, prettyName, translatedName);

		this.yaw = defaultYaw;
		this.pitch = defaultPitch;
		this.defaultYaw = defaultYaw;
		this.defaultPitch = defaultPitch;
		keybind = KeybindMulti.fromStorageString(defaultHotkey, settings);
	}

	public float getYaw(){ return yaw; }
	public float getPitch(){ return pitch; }

	public void setYaw(float yaw){ this.yaw = yaw; }
	public void setPitch(float pitch){ this.pitch = pitch; }

	@Override public boolean shouldUseSlider(){return useSlider;}
	@Override public void toggleUseSlider(){useSlider = !useSlider;}

	@Override public IKeybind getKeybind(){ return keybind;}

	@Override public ConfigYawPitchHotkeyed translatedName(String translatedName){
		return super.translatedName(translatedName);
	}

	@Override public ConfigYawPitchHotkeyed apply(String translationPrefix){
		return super.apply(translationPrefix);
	}

	@Override public boolean isModified(){
		// Note: calling isModified() for the IHotkey here directly would not work with multi-type configs like the FeatureToggle in Tweakeroo!
		// Thus we need to get the IKeybind and call it for that specifically.
		return yaw != defaultYaw || pitch != defaultPitch || getKeybind().isModified();
	}


	@Override public void resetToDefault(){
		yaw = defaultYaw;
		pitch = defaultPitch;
		keybind.resetToDefault();
	}

	@Override public void setValueFromJsonElement(JsonElement element){
		try{
			if(element.isJsonObject()){
				JsonObject obj = element.getAsJsonObject();

				if(JsonUtils.hasFloat(obj, "yaw")) yaw = obj.get("yaw").getAsFloat();
				if(JsonUtils.hasFloat(obj, "pitch")) pitch = obj.get("pitch").getAsFloat();
				if(JsonUtils.hasObject(obj, "hotkey")) keybind.setValueFromJsonElement(obj.getAsJsonObject("hotkey"));
			}
		}
		catch(Exception e){
			MaLiLib.LOGGER.warn("Failed to set config value for '{}' from the JSON element '{}'", getName(), element, e);
		}
	}

	@Override public JsonElement getAsJsonElement(){
		JsonObject obj = new JsonObject();
		obj.add("yaw", new JsonPrimitive(yaw));
		obj.add("pitch", new JsonPrimitive(pitch));
		obj.add("hotkey", getKeybind().getAsJsonElement());
		return obj;
	}
}