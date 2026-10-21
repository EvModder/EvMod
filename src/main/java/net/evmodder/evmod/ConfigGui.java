package net.evmodder.evmod;

import java.util.ArrayList;
import java.util.List;
import fi.dy.masa.malilib.config.IConfigBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.button.ButtonBase;
import fi.dy.masa.malilib.gui.button.ButtonGeneric;
import fi.dy.masa.malilib.util.StringUtils;

public class ConfigGui extends GuiConfigsBase{
	private enum ConfigGuiTab{
		ALL(Main.MOD_ID+".gui.button.all"),
		GENERIC(Main.MOD_ID+".gui.button.generic"),
		VISUALS(Main.MOD_ID+".gui.button.visuals"),
		HOTKEYS(Main.MOD_ID+".gui.button.hotkeys"),
		DATABASE(Main.MOD_ID+".gui.button.database");

		private final String translationKey;
		private ConfigGuiTab(String translationKey){this.translationKey = translationKey;}
		public String getDisplayName(){return StringUtils.translate(translationKey);}
		public String getDescription(){return StringUtils.translate(translationKey+".hover");}
	}

	private final Configs configs;
	ConfigGui(Configs configs){
		super(10, 50, Main.MOD_ID, /*parent=*/null, Main.MOD_ID+".gui.title", Main.MOD_VERSION);
		this.configs = configs;
		setConfigWidth(224);
	}

	private int createButton(int x, int y, ConfigGuiTab tab){
		ButtonGeneric button = new ButtonGeneric(x, y, -1, 20,
				tab == ConfigGuiTab.DATABASE ? StringUtils.translate(configs.dataTabTranslationKey) : tab.getDisplayName(), tab.getDescription());
		button.setEnabled(tab.ordinal() != configs.guiTab);
		addButton(button, (ButtonBase _, int _)->{
			if(configs.guiTab == tab.ordinal()) return;
			configs.guiTab = tab.ordinal();
			reCreateListWidget();
			getListWidget().resetScrollbarPosition();
			initGui();
		});
		return button.getWidth() + 2;
	}

	@Override public void initGui(){
		super.initGui();
		clearOptions();

		int x = 10;
		int y = 26;

		if(!Main.mapArtFeaturesOnly) x += createButton(x, y, ConfigGuiTab.ALL);
		x += createButton(x, y, ConfigGuiTab.GENERIC);
		x += createButton(x, y, ConfigGuiTab.VISUALS);
		x += createButton(x, y, ConfigGuiTab.HOTKEYS);
		if(!configs.getDatabaseConfigs().isEmpty()) x += createButton(x, y, ConfigGuiTab.DATABASE);
	}

	@Override protected boolean useKeybindSearch(){return configs.guiTab == ConfigGuiTab.HOTKEYS.ordinal();}

	private String remoteDatabaseSeparator(){
		final String label = StringUtils.translate(Main.MOD_ID+".gui.label.remoteDatabase");
		final int length = Math.max(0, (getBrowserWidth()-14-getStringWidth(" "+label+" ")) / (2*getStringWidth(" ")));
		final String rule = "\u00a78\u00a7m"+" ".repeat(length)+"\u00a7r";
		return rule+" \u00a77"+label+" "+rule;
	}

	@Override public List<ConfigOptionWrapper> getConfigs(){
		final List<IConfigBase> options = switch(ConfigGuiTab.values()[configs.guiTab]){
			case ALL -> configs.getAllConfigs();
			case GENERIC -> configs.getGenericConfigs();
			case VISUALS -> configs.getVisualsConfigs();
			case HOTKEYS -> configs.getHotkeysConfigs();
			case DATABASE -> configs.getDatabaseConfigs();
//			case RENDER_LAYERS -> Collections.emptyList();
		};
		final List<ConfigOptionWrapper> wrappers = new ArrayList<>();
		for(IConfigBase option : options){
			if(option == Configs.Database.CLIENT_ID && !wrappers.isEmpty()){
				wrappers.add(new ConfigOptionWrapper(remoteDatabaseSeparator()));
			}
			wrappers.add(new ConfigOptionWrapper(option));
		}
		return wrappers;
	}
}