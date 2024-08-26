package gg.xp.xivsupport.events.actlines.parsers;

import gg.xp.reevent.events.Event;
import gg.xp.xivsupport.events.actlines.events.LbUpdateEvent;
import org.jetbrains.annotations.Nullable;
import org.picocontainer.PicoContainer;

import java.time.ZonedDateTime;

public class Line36Parser extends AbstractACTLineParser<Line36Parser.Fields> {
	public Line36Parser(PicoContainer container) {
		super(container, 36, Fields.class);
	}

	enum Fields {
		valueHex, bars
	}

	@Override
	protected Event convert(FieldMapper<Fields> fields, int lineNumber, ZonedDateTime time) {
		return new LbUpdateEvent(
				fields.getHex(Fields.valueHex),
				fields.getInt(Fields.bars)
		);
	}
}
