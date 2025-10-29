package net.evmodder.KeyBound.config;

import java.util.List;
import java.util.Objects;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.util.StringUtils;
import net.evmodder.KeyBound.Main;

public class ConfigGui extends GuiConfigsBase{
	private static ConfigGuiTab tab = ConfigGuiTab.HOTKEYS;

	public ConfigGui(){
		super(10, 50, Main.MOD_ID, /*parent=*/null, "keybound.gui.title", StringUtils.getModVersionString(Main.MOD_ID));
	}

	@Override public void initGui(){
		super.initGui();
		clearOptions();

		int x = 10;
		int y = 26;

		x += this.createButton(x, y, -1, ConfigGuiTab.GENERIC);
		x += this.createButton(x, y, -1, ConfigGuiTab.VISUALS);
		x += this.createButton(x, y, -1, ConfigGuiTab.HOTKEYS);
		if(Main.remoteSender != null) x += this.createButton(x, y, -1, ConfigGuiTab.DATABASE);
		// x += this.createButton(x, y, -1, ConfigGuiTab.TEST);
	}

	private int createButton(int x, int y, int width, ConfigGuiTab tab){
		ButtonGeneric button = new ButtonGeneric(x, y, width, 20, tab.getDisplayName());
		button.setEnabled(ConfigGui.tab != tab);
		addButton(button, new ButtonListener(tab, this));

		return button.getWidth() + 2;
	}

	@Override protected boolean useKeybindSearch(){
		return tab == ConfigGuiTab.HOTKEYS;
	}

	@Override public List<ConfigOptionWrapper> getConfigs(){
		List<? extends IConfigBase> configs;
		configs = switch(tab) {
			case GENERIC -> Configs.Generic.getOptions();
			case VISUALS -> Configs.Visuals.getOptions();
			case HOTKEYS -> Configs.Hotkeys.getOptions();
			case DATABASE -> Configs.Database.getOptions();
//			case RENDER_LAYERS -> Collections.emptyList();
		};
		return ConfigOptionWrapper.createFor(configs);
	}

	private record ButtonListener(ConfigGuiTab tab, ConfigGui parent) implements IButtonActionListener {
		@Override public void actionPerformedWithButton(ButtonBase button, int mouseButton){
			ConfigGui.tab = tab;
			parent.reCreateListWidget(); // apply the new config width
			Objects.requireNonNull(parent.getListWidget()).resetScrollbarPosition();
			parent.initGui();
		}
	}

	public enum ConfigGuiTab{
		GENERIC("keybound.gui.button.generic"),
		VISUALS("keybound.gui.button.visuals"),
		HOTKEYS("keybound.gui.button.hotkeys"),
		DATABASE("keybound.gui.button.database");

		private final String translationKey;
		private ConfigGuiTab(String translationKey){this.translationKey = translationKey;}
		public String getDisplayName(){return StringUtils.translate(this.translationKey);}
	}
}