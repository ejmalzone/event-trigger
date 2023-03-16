package gg.xp.xivsupport.events.state.internal;

import gg.xp.util.DataUtils;
import gg.xp.xivsupport.models.CurrentMaxPair;
import gg.xp.xivsupport.models.HitPoints;
import org.jetbrains.annotations.Nullable;

public class HpTracker {

	private final MutableCurMax network = new MutableCurMax();
	private final MutableCurMax memory = new MutableCurMax();
	private final MutableCurMax actMem = new MutableCurMax();
	private HitPoints computed;
	private boolean dirty;

	public boolean setNetCur(int cur) {
		return dirty |= network.setCur(cur);
	}

	public boolean setNetMax(int max) {
		return dirty |= network.setMax(max);
	}

	public boolean setNet(@Nullable CurrentMaxPair value) {
		return dirty |= network.set(value);
	}

	public boolean setMemCur(int cur) {
		return dirty |= memory.setCur(cur);
	}

	public boolean setMemMax(int max) {
		return dirty |= memory.setMax(max);
	}

	public boolean setMem(@Nullable CurrentMaxPair value) {
		return dirty |= memory.set(value);
	}

	public boolean setActMemCur(int cur) {
		return dirty |= actMem.setCur(cur);
	}

	public boolean setActMemMax(int max) {
		return dirty |= actMem.setMax(max);
	}

	public boolean setActMem(@Nullable CurrentMaxPair value) {
		return dirty |= actMem.set(value);
	}

	public @Nullable HitPoints getHp() {
		if (dirty) {
			recompute();
		}
		return computed;
	}

	private void recompute() {
		int cur = DataUtils.firstNonNegative(network.cur, memory.cur, actMem.cur);
		int max = DataUtils.firstNonNegative(network.max, memory.max, actMem.max);
		if (cur >= 0 && max >= 0) {
			computed = new HitPoints(cur, max);
		}
		else {
			computed = null;
		}
		dirty = false;
	}
}
