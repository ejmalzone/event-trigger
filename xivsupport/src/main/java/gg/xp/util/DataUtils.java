package gg.xp.util;

public class DataUtils {

	public static int firstNonNegative(int... values) {
		for (int value : values) {
			if (value >= 0) {
				return value;
			}
		}
		return -1;
	}

}
