package net.evmodder.EvLib;

public class TextUtils{
	static long[] scale = new long[]{31536000000L, /*2628000000L,*/ 604800000L, 86400000L, 3600000L, 60000L, 1000L};
	static char[] units = new char[]{'y', /*'m',*/ 'w', 'd', 'h', 'm', 's'};
	public static String formatTime(long millisecond){
		return formatTime(millisecond, /*show0s=*/true, ", ", units.length, scale, units);
	}
	public static String formatTime(long millisecond, boolean show0s){
		return formatTime(millisecond, show0s, ", ", units.length, scale, units);
	}
	public static String formatTime(long millisecond, boolean show0s, int sigUnits){//e
		return formatTime(millisecond, show0s, ", ", sigUnits);
	}
	public static String formatTime(long millisecond, boolean show0s, String sep){
		return formatTime(millisecond, show0s, sep, units.length, scale, units);
	}
	public static String formatTime(long millisecond, boolean show0s, String sep, int sigUnits){
		return formatTime(millisecond, show0s, sep, sigUnits, scale, units);
	}
	public static String formatTime(long millis, boolean show0s, String sep, int sigUnits, long[] scale, char[] units){
		if(millis / scale[scale.length-1] == 0){
			return new StringBuilder("0").append(units[units.length-1]).toString();
		}
		int i = 0, unitsShown = 0;
		while(millis < scale[i]) ++i;
		StringBuilder builder = new StringBuilder("");
		for(; i < scale.length-1; ++i){
			if(show0s || millis / scale[i] != 0){
				long scaledTime = millis / scale[i];
				builder.append(scaledTime).append(units[i]).append(sep);
				if(++unitsShown == sigUnits) break;
			}
			millis %= scale[i];
		}
		if((show0s || (millis / scale[scale.length-1]) != 0) && unitsShown < sigUnits)
			return builder
			.append(millis / scale[scale.length-1])
			.append(units[units.length-1]).toString();
		else return builder.substring(0, builder.length()-sep.length()); // cut off trailing sep
	}
}
