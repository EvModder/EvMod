package net.evmodder.KeyBound;

import java.util.List;
import java.util.Objects;

import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.gui.button.IButtonActionListener;
import fi.dy.masa.malilib.util.StringUtils;

public class ConfigGui extends GuiConfigsBase{
	enum ConfigGuiTab{
		ALL(Main.MOD_ID+".gui.button.all"),
		GENERIC(Main.MOD_ID+".gui.button.generic"),
		VISUALS(Main.MOD_ID+".gui.button.visuals"),
		HOTKEYS(Main.MOD_ID+".gui.button.hotkeys"),
		DATABASE(Main.MOD_ID+".gui.button.database");

		private final String translationKey;
		private ConfigGuiTab(String translationKey){this.translationKey = translationKey;}
		public String getDisplayName(){return StringUtils.translate(this.translationKey);}
	}
	private static ConfigGuiTab tab = ConfigGuiTab.HOTKEYS;

	ConfigGui(){
		super(10, 50, Main.MOD_ID, /*parent=*/null, Main.MOD_ID+".gui.title", StringUtils.getModVersionString(Main.MOD_ID));
		setConfigWidth(224);
	}

	private int createButton(int x, int y, int width, ConfigGuiTab tab){
		ButtonGeneric button = new ButtonGeneric(x, y, width, 20, tab.getDisplayName());
		button.setEnabled(ConfigGui.tab != tab);
		addButton(button, new ButtonListener(tab, this));

		return button.getWidth() + 2;
	}

	@Override public void initGui(){
		super.initGui();
		clearOptions();

		int x = 10;
		int y = 26;

		x += createButton(x, y, -1, ConfigGuiTab.ALL);
		x += createButton(x, y, -1, ConfigGuiTab.GENERIC);
		x += createButton(x, y, -1, ConfigGuiTab.VISUALS);
		x += createButton(x, y, -1, ConfigGuiTab.HOTKEYS);
		if(Main.remoteSender != null) x += createButton(x, y, -1, ConfigGuiTab.DATABASE);
		// x += this.createButton(x, y, -1, ConfigGuiTab.TEST);
	}

	@Override protected boolean useKeybindSearch(){
		return tab == ConfigGuiTab.HOTKEYS;
	}

	@Override public List<ConfigOptionWrapper> getConfigs(){
		List<? extends IConfigBase> configs;
		configs = switch(tab) {
			case ALL -> Configs.allOptions();
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
}