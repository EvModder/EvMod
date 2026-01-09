/*
 * This file is adaped from the TweakerMore project, licensed under the
 * GNU Lesser General Public License v3.0
 *
 * Copyright (C) 2023  Fallen_Breath and contributors
 *
 * TweakerMore is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * TweakerMore is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with TweakerMore.  If not, see <https://www.gnu.org/licenses/>.
 */
package net.evmodder.evmod.mixin;

import fi.dy.masa.malilib.config.*;
import fi.dy.masa.malilib.config.gui.ConfigOptionChangeListenerTextField;
import fi.dy.masa.malilib.config.gui.ConfigOptionListenerResetConfig;
import fi.dy.masa.malilib.config.gui.ConfigOptionListenerResetConfig.ConfigResetterBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase;
import fi.dy.masa.malilib.gui.GuiConfigsBase.ConfigOptionWrapper;
import fi.dy.masa.malilib.gui.GuiTextFieldGeneric;
import fi.dy.masa.malilib.gui.MaLiLibIcons;
import fi.dy.masa.malilib.gui.button.*;
import fi.dy.masa.malilib.gui.interfaces.IGuiIcon;
import fi.dy.masa.malilib.gui.interfaces.IKeybindConfigGui;
import fi.dy.masa.malilib.gui.widgets.*;
import fi.dy.masa.malilib.gui.widgets.WidgetConfigOption.ListenerSliderToggle;
import fi.dy.masa.malilib.hotkeys.*;
import net.evmodder.evmod.ConfigGui;
import net.evmodder.evmod.config.ConfigStringHotkeyed;
import net.evmodder.evmod.config.ConfigYawPitchHotkeyed;
import net.minecraft.client.gui.DrawContext;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(WidgetConfigOption.class)
public abstract class MixinWidgetConfigOption extends WidgetConfigOptionBase<GuiConfigsBase.ConfigOptionWrapper>{
	@Shadow(remap = false) @Final protected IKeybindConfigGui host;
	@Shadow(remap = false) @Final protected GuiConfigsBase.ConfigOptionWrapper wrapper;
	@Shadow(remap = false) @Final @Mutable protected KeybindSettings initialKeybindSettings;

	@Shadow(remap = false) protected abstract void addKeybindResetButton(int x, int y, IKeybind keybind, ConfigButtonKeybind buttonHotkey);

	@Unique private String initialString$OURS;
	@Unique private float initialYaw$OURS, initialPitch$OURS;
//	@Unique private IConfigOptionListEntry initialOptionListEntry$OURS;

	public MixinWidgetConfigOption(int x, int y, int width, int height, WidgetListConfigOptionsBase<?, ?> parent, GuiConfigsBase.ConfigOptionWrapper entry, int listIndex){
		super(x, y, width, height, parent, entry, listIndex);
	}

	@Unique private void addStringWithHotkey(int x, int y, int configWidth, ConfigStringHotkeyed config){
		int textFieldWidth = (configWidth - 24) * 3 / 5;
		int configHeight = 20;
		GuiTextFieldGeneric textField = createTextField(x, y + 1, textFieldWidth, configHeight - 3);
		textField.setMaxLength(maxTextfieldTextLength);
		textField.setText(config.getStringValue());

		x += textFieldWidth + 3;
		configWidth -= textFieldWidth + 23;
		IKeybind keybind = config.getKeybind();
		ConfigButtonKeybind keybindButton = new ConfigButtonKeybind(x, y, configWidth - 2, 20, keybind, host);
		x += configWidth;
		addWidget(new WidgetKeybindSettings(x, y, 20, 20, keybind, config.getName(), parent, host.getDialogHandler()));

		x += 22;
		ButtonGeneric resetButton = createResetButton(x, y, config);
		ConfigOptionChangeListenerTextField listenerChange = new ConfigOptionChangeListenerTextField(config, textField, resetButton);
		ConfigOptionListenerResetConfig resetListener = new ConfigOptionListenerResetConfig(config, new ConfigResetterBase(){
			@Override public void resetConfigOption(){
				textField.setText(config.getStringValue());
				keybindButton.updateDisplayString();
				resetButton.setEnabled(config.isModified());
			}
		}, resetButton, null);

		addTextField(textField, listenerChange);
		addButton(keybindButton, host.getButtonPressListener());
		addButton(resetButton, resetListener);

	}

	private void goofyAddFloatWidget(GuiTextFieldGeneric textFieldI, ButtonGeneric resetButton, ConfigYawPitchHotkeyed config, boolean yaw){
		ConfigOptionChangeListenerTextField changeListener = new ConfigOptionChangeListenerTextField(config, textFieldI, resetButton){
			@Override public boolean onTextChange(GuiTextFieldGeneric textField){
				if(textFieldI.getText().isBlank()) resetButton.setEnabled(true);
				return super.onTextChange(textField);
			}
		};
		WidgetConfigOptionBase<ConfigOptionWrapper> fakeChildWidget = new WidgetConfigOptionBase<>(
				textFieldI.getX(), textFieldI.getY(), textFieldI.getWidth(), textFieldI.getHeight(), parent, wrapper, 1){
			{
				addTextField(textFieldI, changeListener);
			}
			@Override public void render(DrawContext drawContext, int mouseX, int mouseY, boolean selected){
				textFieldI.render(drawContext, mouseX, mouseY, 0f);
			}

			@Override public boolean wasConfigModified(){
				return yaw ? initialYaw$OURS != config.getYaw() : initialPitch$OURS != config.getPitch();
			}

			@Override public void applyNewValueToConfig(){
				try{
					if(yaw) config.setYaw(Float.parseFloat(textFieldI.getText()));
					else config.setPitch(Float.parseFloat(textFieldI.getText()));
				}
				catch(NumberFormatException e){}
			}
		};
		addWidget(fakeChildWidget);
	}
	@Unique private void addYawPitchWithHotkey(int x, int y, int configWidth, ConfigYawPitchHotkeyed config){
//		if(config.shouldUseSlider()){
//			addConfigSliderEntry(x, y, resetX, configWidth, configHeight, (IConfigSlider) config);//WidgetConfigOption
//		}
//		else{
//			addConfigTextFieldEntry(x, y, resetX, configWidth, configHeight, (IConfigValue) config);//WidgetConfigOption
//		}

		int sliderWidth = MaLiLibIcons.BTN_SLIDER.getWidth();
		int textFieldWidth = (configWidth - sliderWidth - 40) * 3 / 10;
		int configHeight = 20;
		GuiTextFieldGeneric textField1 = createTextField(x, y + 1, textFieldWidth, configHeight - 3);
		x += textFieldWidth + 2;
		GuiTextFieldGeneric textField2 = createTextField(x, y + 1, textFieldWidth, configHeight - 3);
		textField1.setMaxLength(maxTextfieldTextLength);
		textField1.setText(""+config.getYaw());
		textField2.setMaxLength(maxTextfieldTextLength);
		textField2.setText(""+config.getPitch());

		x += textFieldWidth + 4;
		IGuiIcon icon = config.shouldUseSlider() ? MaLiLibIcons.BTN_TXTFIELD : MaLiLibIcons.BTN_SLIDER;
		ButtonGeneric togglesliderBtn = new ButtonGeneric(x, y + 2, icon);
		x += sliderWidth + 1;

		configWidth -= textFieldWidth*2 + sliderWidth + 27;
		IKeybind keybind = config.getKeybind();
		ConfigButtonKeybind keybindButton = new ConfigButtonKeybind(x, y, configWidth - 2, 20, keybind, host);
		x += configWidth;
		addWidget(new WidgetKeybindSettings(x, y, 20, 20, keybind, config.getName(), parent, host.getDialogHandler()));

		x += 22;
		ButtonGeneric resetButton = createResetButton(x, y, config);

		ConfigOptionListenerResetConfig resetListener = new ConfigOptionListenerResetConfig(config, new ConfigResetterBase(){
			@Override public void resetConfigOption(){
				textField1.setText(""+config.getYaw());
				textField2.setText(""+config.getPitch());
				keybindButton.updateDisplayString();
				resetButton.setEnabled(config.isModified());
			}
		}, resetButton, null);

		goofyAddFloatWidget(textField1, resetButton, config, /*yaw=*/true);
		goofyAddFloatWidget(textField2, resetButton, config, /*yaw=*/false);
		addButton(togglesliderBtn, new ListenerSliderToggle(config));
		addButton(keybindButton, host.getButtonPressListener());
		addButton(resetButton, resetListener);

	}

	@Unique private boolean isOurConfigGui(){
//		return parent instanceof WidgetListConfigOptions wlco && wlco.parent instanceof ConfigGui;
		return parent instanceof WidgetListConfigOptions && ((AccessorWidgetListConfigOptions)parent).getParent() instanceof ConfigGui;
	}

	@Inject(method="addConfigOption", at=@At(value="FIELD",
			target="Lfi/dy/masa/malilib/config/ConfigType;BOOLEAN:Lfi/dy/masa/malilib/config/ConfigType;", remap=false), remap=false, cancellable=true)
	private void customConfigGui(int x, int y, float zLevel, int labelWidth, int configWidth, IConfigBase config, CallbackInfo ci){
		if(!isOurConfigGui() || !(config instanceof IHotkey)) return;

		boolean modified = true;
//		if(config instanceof IHotkeyTogglable iht){
//			addHotkeyTogglableButtons(x, y, configWidth, iht);
//		}
		/*else */if(config instanceof ConfigStringHotkeyed csh){
			addStringWithHotkey(x, y, configWidth, csh);
		}
		else if(config instanceof ConfigYawPitchHotkeyed cyph){
			addYawPitchWithHotkey(x, y, configWidth, cyph);
		}
//		else if(config instanceof IOptionListHotkeyed iolh){
//			addOptionListWithHotkey(x, y, configWidth, iolh);
//		}
//		else if(ih.getKeybind() instanceof KeybindMulti){
//			addButtonAndHotkeyWidgets(x, y, configWidth, (IHotkey)config);
//		}
		else{
			modified = false;
		}
		if(modified) ci.cancel();
	}


//	@Unique private void addOptionListWithHotkey(int x, int y, int configWidth, IOptionListHotkeyed config){
//		int optionBtnWidth = (configWidth - 24) / 2;
//		ConfigButtonOptionList optionButton = new ConfigButtonOptionList(x, y, optionBtnWidth, 20, config);
//		((ConfigButtonOptionListHovering)optionButton).setEnableValueHovering$OURS();
//		addValueWithKeybindWidgets(x, y, configWidth, config, optionButton);
//	}

	@Inject(method = "<init>", at = @At("TAIL"), remap = false)
	private void initInitialState(CallbackInfo ci){
		if(!isOurConfigGui() || wrapper.getType() != GuiConfigsBase.ConfigOptionWrapper.Type.CONFIG) return;

		IConfigBase config = wrapper.getConfig();
		if(config instanceof ConfigStringHotkeyed csh){
			initialString$OURS = csh.getStringValue();
			initialStringValue = csh.getKeybind().getStringValue();
			initialKeybindSettings = csh.getKeybind().getSettings();
		}
		if(config instanceof ConfigYawPitchHotkeyed cyph){
			initialYaw$OURS = cyph.getYaw();
			initialPitch$OURS = cyph.getPitch();
			initialStringValue = cyph.getKeybind().getStringValue();
			initialKeybindSettings = cyph.getKeybind().getSettings();
		}
//		else if(config instanceof IOptionListHotkeyed iolh){
//			initialOptionListEntry$OURS = iolh.getOptionListValue();
//			initialStringValue = iolh.getKeybind().getStringValue();
//			initialKeybindSettings = iolh.getKeybind().getSettings();
//		}
	}

	@Inject(method = "wasConfigModified", at=@At(value="INVOKE",
			target="Lfi/dy/masa/malilib/gui/GuiConfigsBase$ConfigOptionWrapper;getConfig()Lfi/dy/masa/malilib/config/IConfigBase;",
			ordinal=0, remap=false), cancellable=true, remap=false)
	private void specialJudgeCustomConfigValueHotkeyed(CallbackInfoReturnable<Boolean> cir){
		IConfigBase config = wrapper.getConfig();
//		if(!OurConfigs.hasConfig(config)) return;

		if(config instanceof ConfigStringHotkeyed csh){
			IKeybind keybind = csh.getKeybind();
			cir.setReturnValue(
					!Objects.equals(initialString$OURS, csh.getStringValue())
					|| !Objects.equals(initialStringValue, keybind.getStringValue())
					|| !Objects.equals(initialKeybindSettings, keybind.getSettings()));
		}
		if(config instanceof ConfigYawPitchHotkeyed cyph){
			IKeybind keybind = cyph.getKeybind();
			cir.setReturnValue(
					initialYaw$OURS != cyph.getYaw() || initialPitch$OURS != cyph.getPitch()
					|| !Objects.equals(initialStringValue, keybind.getStringValue())
					|| !Objects.equals(initialKeybindSettings, keybind.getSettings()));
		}
//		if(config instanceof IOptionListHotkeyed iolh){
//			IKeybind keybind = iolh.getKeybind();
//			cir.setReturnValue(
//					!Objects.equals(initialOptionListEntry$OURS, iolh.getOptionListValue())
//					|| !Objects.equals(initialStringValue, keybind.getStringValue())
//					|| !Objects.equals(initialKeybindSettings, keybind.getSettings()));
//		}
	}
}