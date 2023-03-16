package gg.xp.xivsupport.events.actlines.parsers;

import gg.xp.reevent.events.Event;
import gg.xp.xivsupport.events.actlines.events.MapEffectEvent;
import gg.xp.xivsupport.events.actlines.events.RawAddCombatantEvent;
import gg.xp.xivsupport.events.actlines.events.RawRemoveCombatantEvent;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.models.XivCombatant;
import org.picocontainer.PicoContainer;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class Line261Parser extends AbstractACTLineParser<Line261Parser.Fields> {

	private final XivState state;

	public Line261Parser(PicoContainer container, XivState state) {
		super(container, 261, Fields.class);
		this.state = state;
	}

	enum Fields {
		UpdateType, Id
	}

	@Override
	protected Event convert(FieldMapper<Fields> fields, int lineNumber, ZonedDateTime time) {
		String type = fields.getString(Fields.UpdateType);
		// Remove is easy
		if ("Remove".equals(type)) {
			state.removeSpecificCombatant(fields.getHex(Fields.Id));
			return null;
		}
		// Add/Change
		XivCombatant entity = fields.getEntity(Fields.Id);

		List<String> raw = fields.getRawLineSplit();
		// Cut off line number, timestamp, UpdateType, Id
		List<String> dataFields = raw.subList(4, raw.size());

		// Assemble k/v data
		Map<String, String> rawData = new HashMap<>(dataFields.size() / 2);
		for (int i = 0; i < dataFields.size(); i+=2) {
			String field = dataFields.get(i);
			String rawValue = dataFields.get(i + 1);
			rawData.put(field, rawValue);
		}

		state.provideCombatantRawStringMap(entity.getId(), rawData);
		state.flushProvidedValues();
		return null;
	}
}
