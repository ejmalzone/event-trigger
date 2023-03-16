package gg.xp.xivsupport.events.state.internal;

import gg.xp.xivdata.data.*;

public record BasicCombatantInfo(
		String name,
		int rawType,
		long npcId,
		long npcNameId,
		Job job,
		int level
) {
}
