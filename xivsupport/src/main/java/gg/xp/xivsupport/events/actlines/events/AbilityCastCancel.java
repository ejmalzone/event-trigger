package gg.xp.xivsupport.events.actlines.events;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.xivsupport.models.XivAbility;
import gg.xp.xivsupport.models.XivCombatant;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.Serial;

/**
 * Represents an ability cast being cancelled/interrupted/etc
 */
public class AbilityCastCancel extends BaseEvent implements HasSourceEntity, HasAbility, HasCastPrecursor {

	@Serial
	private static final long serialVersionUID = -5704173639583049362L;
	private final XivCombatant source;
	private final XivAbility ability;
	private final String reason;
	private @Nullable AbilityCastStart precursor;

	public AbilityCastCancel(XivCombatant source, XivAbility ability, String reason) {
		this.source = source;
		this.ability = ability;
		this.reason = reason;
	}

	@Override
	public XivCombatant getSource() {
		return source;
	}

	@Override
	public XivAbility getAbility() {
		return ability;
	}

	public String getReason() {
		return reason;
	}

	@Override
	public @Nullable AbilityCastStart getPrecursor() {
		return precursor;
	}

	@Override
	public void setPrecursor(@NotNull AbilityCastStart precursor) {
		this.precursor = precursor;
	}
}
