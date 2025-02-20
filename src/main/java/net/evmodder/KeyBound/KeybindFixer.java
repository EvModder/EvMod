package net.evmodder.KeyBound;

import com.google.common.collect.ArrayListMultimap;
import net.evmodder.KeyBound.mixin.AccessorTimesPressed;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;

//Authors: fzzyhmstrs, EvModder
public class KeybindFixer{
	private static ArrayListMultimap<InputUtil.Key, KeyBinding> keyFixMap = ArrayListMultimap.create();

//	public static boolean putKey(InputUtil.Key key, KeyBinding keyBinding){
//		if(keyFixMap.containsKey(key)) return keyFixMap.get(key).add(keyBinding);
//		HashSet<KeyBinding> set = new HashSet<>();
//		set.add(keyBinding);
//		keyFixMap.put(key, set);
//		return true;
//	}
	public static final void putKey(InputUtil.Key key, KeyBinding keyBinding) {
		keyFixMap.put(key, keyBinding);
	}

	public static void clearMap(){keyFixMap.clear();}

//	public static void onKeyPressed(InputUtil.Key key, KeyBinding finalBinding, KeyBinding baseBinding){
//		if(!finalBinding.equals(baseBinding)) return;
//		for(KeyBinding keybind : keyFixMap.get(key)){
//			if(keybind == null || keybind.equals(baseBinding)) continue;
//			TimesPressedAccessor t = (TimesPressedAccessor)keybind;
//			t.setTimesPressed(t.getTimesPressed() + 1);
//		}
//	}
	public static final void onKeyPressed(InputUtil.Key key){
		for(KeyBinding keybind : keyFixMap.get(key)){
			if(keybind == null) return;
			AccessorTimesPressed t = (AccessorTimesPressed)keybind;
			t.setTimesPressed(t.getTimesPressed() + 1);
		}
	}

//	public static void setKeyPressed(InputUtil.Key key, boolean pressed, KeyBinding finalBinding, KeyBinding baseBinding){
//		if(!finalBinding.equals(baseBinding)) return;
//		for(KeyBinding keybind : keyFixMap.get(key)){
//			if(keybind == null || keybind.equals(baseBinding)) continue;
//			keybind.setPressed(pressed);
//		}
//	}
	public static final void setKeyPressed(InputUtil.Key key, boolean pressed){
		for(KeyBinding keybind : keyFixMap.get(key)){
			keybind.setPressed(pressed);
		}
	}
}
