package gg.xp.xivsupport.events.state.internal;

import gg.xp.xivsupport.models.Position;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class PosTracker {

//	private Position actMem;
//	private Position opMem;
	private Position thePos;

	public boolean setActMem(Position actMem) {
		return set(actMem);
	}

	public boolean setOpMem(Position opMem) {
		return set(opMem);
	}

	private boolean set(Position pos) {
		if (!Objects.equals(this.thePos, pos)) {
			this.thePos = pos;
			return true;
		}
		return false;
	}

	public @Nullable Position get() {
		return thePos;
	}
//	public boolean setActMem(Position actMem) {
//		if (!Objects.equals(this.actMem, actMem)) {
//			this.actMem = actMem;
//			this.opMem = null;
//			return true;
//		}
//		return false;
//	}
//
//	public boolean setOpMem(Position opMem) {
//		if (!Objects.equals(this.opMem, opMem)) {
//			this.opMem = opMem;
//			return true;
//		}
//		return false;
//	}
//
//	public @Nullable Position get() {
//		Position actMem = this.actMem;
//		if (actMem != null) {
//			return actMem;
//		}
//		else {
//			return opMem;
//		}
//	}
}
