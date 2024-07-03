package net.evmodder.mixin;

import net.evmodder.KeybindFixer;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.Map;

//Authors: fzzyhmstrs, EvModder
@Mixin(value = KeyBinding.class, priority = 10000)
public abstract class KeybindingMixin {
	@Final
	@Shadow private static Map<String, KeyBinding> KEYS_BY_ID;
	@Shadow private static Map<InputUtil.Key, KeyBinding> KEY_TO_BINDINGS;
	@Shadow private InputUtil.Key boundKey;

//	@Inject(method="onKeyPressed", at=@At(value="HEAD"))
//	private static void onKeyPressedFixed(InputUtil.Key key, CallbackInfo ci, @Local KeyBinding original){
//		KeybindFixer.onKeyPressed(key, original, KEY_TO_BINDINGS.get(key));
//		ci.cancel();
//	}
	@Inject(method="onKeyPressed", at=@At(value="HEAD"), cancellable=true)
	private static void onKeyPressedFixed(InputUtil.Key key, CallbackInfo ci){
		KeybindFixer.onKeyPressed(key);
		ci.cancel();
	}

//	@Inject(method="setKeyPressed", at=@At(value="HEAD"))
//	private static void setKeyPressedFixed(InputUtil.Key key, boolean pressed, CallbackInfo ci, @Local KeyBinding original){
//		KeybindFixer.setKeyPressed(key, pressed, original, KEY_TO_BINDINGS.get(key));
//		ci.cancel();
//	}
	@Inject(method="setKeyPressed", at=@At(value="HEAD"), cancellable=true)
	private static void setKeyPressedFixed(InputUtil.Key key, boolean pressed, CallbackInfo ci){
		KeybindFixer.setKeyPressed(key, pressed);
		ci.cancel();
	}

	@Inject(method="updateKeysByCode", at=@At(value="TAIL"))
	private static void updateByCodeToMultiMap(CallbackInfo ci){
		KeybindFixer.clearMap();
		for(KeyBinding keyBinding : KEYS_BY_ID.values()) {
			KeybindFixer.putKey(((BoundKeyAccessor)keyBinding).getBoundKey(), keyBinding);
		}
	}

	@Inject(method="<init>(Ljava/lang/String;Lnet/minecraft/client/util/InputUtil$Type;ILjava/lang/String;)V", at=@At(value="TAIL"))
	private void putToMultiMap(String translationKey, InputUtil.Type type, int code, String category, CallbackInfo ci){
		KeybindFixer.putKey(boundKey, (KeyBinding)(Object)this);
	}
}