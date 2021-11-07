package gg.xp.events.actlines.parsers;

import gg.xp.events.Event;
import gg.xp.events.actlines.events.AbilityUsedEvent;
import gg.xp.events.models.XivAbility;
import gg.xp.events.models.XivEntity;

import java.time.ZonedDateTime;

@SuppressWarnings("unused")
public class Line22Parser extends AbstractACTLineParser<Line22Parser.Fields> {

	public Line22Parser() {
		super(22, Fields.class);
	}

	enum Fields {
		casterId, casterName, abilityId, abilityName, targetId, targetName, castTime, flags, damage;
	}

	@Override
	protected Event convert(FieldMapper<Fields> fields, int lineNumber, ZonedDateTime time) {
		return new AbilityUsedEvent(
				new XivAbility(fields.getHex(Fields.abilityId), fields.getString(Fields.abilityName)),
				new XivEntity(fields.getHex(Fields.casterId), fields.getString(Fields.casterName)),
				new XivEntity(fields.getHex(Fields.targetId), fields.getString(Fields.targetName)),
				fields.getHex(Fields.flags),
				// TODO
				0
//				fields.getLong(Fields.damage)
		);
	}
}
