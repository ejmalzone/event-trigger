package gg.xp.xivsupport.events.actlines.parsers;

import gg.xp.reevent.events.Event;
import gg.xp.xivsupport.events.actlines.events.HpMpTickEvent;
import gg.xp.xivsupport.models.XivCombatant;
import org.picocontainer.PicoContainer;

import java.time.ZonedDateTime;

@SuppressWarnings("unused")
public class Line39Parser extends AbstractACTLineParser<Line39Parser.Fields> {

	public Line39Parser(PicoContainer container) {
		super(container, 39, Fields.class);
	}

	enum Fields {
		id, name,
		targetCurHp, targetMaxHp, targetCurMp, targetMaxMp, targetUnknown1, targetUnknown2, targetX, targetY, targetZ, targetHeading
	}

	@Override
	protected Event convert(FieldMapper<Fields> fields, int lineNumber, ZonedDateTime time) {
		fields.setTrustedHp(true);
		XivCombatant combatant = fields.getEntity(Fields.id, Fields.name, Fields.targetCurHp, Fields.targetMaxHp, Fields.targetCurMp, Fields.targetMaxMp, Fields.targetX, Fields.targetY, Fields.targetZ, Fields.targetHeading);
		return new HpMpTickEvent(combatant);
	}
}
