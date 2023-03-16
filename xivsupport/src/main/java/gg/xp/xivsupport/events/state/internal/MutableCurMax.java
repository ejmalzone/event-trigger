package gg.xp.xivsupport.events.state.internal;

import gg.xp.xivsupport.models.CurrentMaxPair;
import org.jetbrains.annotations.Nullable;

public class MutableCurMax {

	public int cur = -1;
	public int max = -1;

	public boolean setCur(int cur) {
		if (cur != this.cur) {
			this.cur = cur;
			return true;
		}
		return false;
	}

	public boolean setMax(int max) {
		if (max != this.max) {
			this.max = max;
			return true;
		}
		return false;
	}

	public boolean set(@Nullable CurrentMaxPair value) {
		if (value == null) {
			return setCur(-1) || setMax(-1);
		}
		else {
			return setCur((int) value.current()) | setMax((int) value.max());
		}
	}
}
