package net.evmodder.KeyBound.mixin;

import net.evmodder.KeyBound.keybinds.Keybind;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.google.common.collect.ArrayListMultimap;
import java.util.List;
import java.util.Map;

//Authors: fzzyhmstrs, EvModder
@Mixin(value = KeyBinding.class, priority = 10000)
abstract class MixinKeyBinding{
	class KeybindFixer{
		private static ArrayListMultimap<InputUtil.Key, KeyBinding> keyFixMap = ArrayListMultimap.create();

		public static final void putKey(InputUtil.Key key, KeyBinding keyBinding){keyFixMap.put(key, keyBinding);}
		public static void clearMap(){keyFixMap.clear();}

		public static final void onKeyPressed(InputUtil.Key key){
			keyFixMap.get(key).forEach(kb -> {
				AccessorTimesPressed t = (AccessorTimesPressed)kb;
				t.setTimesPressed(t.getTimesPressed() + 1);
			});
		}

		public static final void setKeyPressed(InputUtil.Key key, boolean pressed){
			List<KeyBinding> kbs = keyFixMap.get(key);
			if(kbs.isEmpty()) return;
			final KeyBinding kb1 = kbs.getFirst();
			Keybind.allKeybinds.forEach(evKb -> {if(evKb.internalKeyBinding.equals(kb1)) evKb.setPresssed(pressed);});//TODO: this is way too jank
			keyFixMap.get(key).forEach(kb -> kb.setPressed(pressed));
		}
	}

	@Final
	@Shadow private static Map<String, KeyBinding> KEYS_BY_ID;
//	@Shadow private static Map<InputUtil.Key, KeyBinding> KEY_TO_BINDINGS;
	@Shadow private InputUtil.Key boundKey;

	@Inject(method="onKeyPressed", at=@At("HEAD"), cancellable=true)
	private static void onKeyPressed_Mixin(InputUtil.Key key, CallbackInfo ci){
//		Main.LOGGER.info("EVKB: onKeyPressed_Mixin");
		KeybindFixer.onKeyPressed(key);
		ci.cancel();
	}

	@Inject(method="setKeyPressed", at=@At(value="HEAD"), cancellable=true)
	private static void setKeyPressed_Mixin(InputUtil.Key key, boolean pressed, CallbackInfo ci){
//		Main.LOGGER.info("EVKB: setKeyPressed_Mixin");
		KeybindFixer.setKeyPressed(key, pressed);
		ci.cancel();
	}

	@Inject(method="updateKeysByCode", at=@At(value="TAIL"))
	private static void updateKeysByCode_Mixin(CallbackInfo ci){
//		Main.LOGGER.info("EVKB: updateKeysByCode_Mixin");
		KeybindFixer.clearMap();
		for(KeyBinding keyBinding : KEYS_BY_ID.values()) {
			KeybindFixer.putKey(((AccessorBoundKey)keyBinding).getBoundKey(), keyBinding);
		}
	}

	@Inject(method="<init>(Ljava/lang/String;Lnet/minecraft/client/util/InputUtil$Type;ILjava/lang/String;)V", at=@At(value="TAIL"))
	private void KeyBindingConstr_Mixin(String translationKey, InputUtil.Type type, int code, String category, CallbackInfo ci){
//		Main.LOGGER.info("EVKB: KeyBindingConstr_Mixin");
		KeybindFixer.putKey(boundKey, (KeyBinding)(Object)this);
	}
}