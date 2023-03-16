package gg.xp.xivsupport.events.state.internal;

import gg.xp.util.DataUtils;
import gg.xp.xivsupport.models.CurrentMaxPair;
import gg.xp.xivsupport.models.ManaPoints;
import org.jetbrains.annotations.Nullable;

public class MpTracker {

	private final MutableCurMax network = new MutableCurMax();
	private final MutableCurMax memory = new MutableCurMax();
	private final MutableCurMax actMem = new MutableCurMax();
	private ManaPoints computed;
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

	public boolean setOpMemCur(int cur) {
		return dirty |= memory.setCur(cur);
	}

	public boolean setOpMemMax(int max) {
		return dirty |= memory.setMax(max);
	}

	public boolean setOpMem(@Nullable CurrentMaxPair value) {
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

	public @Nullable ManaPoints getMp() {
		if (dirty) {
			recompute();
		}
		return computed;
	}

	private void recompute() {
		int cur = DataUtils.firstNonNegative(memory.cur, network.cur, actMem.cur);
		int max = DataUtils.firstNonNegative(memory.max, network.max, actMem.max);
		if (cur >= 0 && max >= 0) {
			computed = ManaPoints.of(cur, max);
		}
		else {
			computed = null;
		}
		dirty = false;
	}
}
