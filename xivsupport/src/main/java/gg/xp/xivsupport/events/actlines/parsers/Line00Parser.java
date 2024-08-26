package gg.xp.xivsupport.events.actlines.parsers;

import gg.xp.reevent.events.Event;
import gg.xp.xivsupport.events.actlines.events.ChatLineEvent;
import gg.xp.xivsupport.events.actlines.events.CountdownStartedEvent;
import org.picocontainer.PicoContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SuppressWarnings("unused")
public class Line00Parser extends AbstractACTLineParser<Line00Parser.Fields> {
	private static final Logger log = LoggerFactory.getLogger(Line00Parser.class);

	public Line00Parser(PicoContainer container) {
		super(container, 0, Fields.class);
	}

	enum Fields {
		code, name, line
	}

	private static final Pattern COUNTDOWN_REGEX = Pattern.compile("Battle commencing in (\\d.) seconds!.*");

	@Override
	protected Event convert(FieldMapper<Fields> fields, int lineNumber, ZonedDateTime time) {
		final long hex = fields.getHex(Fields.code);
		final String name = fields.getString(Fields.name);
		final String line = fields.getString(Fields.line);

		final Matcher match = COUNTDOWN_REGEX.matcher(line);
		// if it's a countdown
		if (match.find()) {
			try {
				// need to capture group 1; group 0 is the entire string
				final int duration = Integer.parseInt(match.group(1));
				return new CountdownStartedEvent(duration);
			} catch (final NumberFormatException numberFormatException) {
				log.error(numberFormatException.getMessage());
			}
		}

		return new ChatLineEvent(hex, name, line);
	}
}
