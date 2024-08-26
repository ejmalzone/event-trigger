package gg.xp.xivsupport.events.actlines.events;

import gg.xp.reevent.events.BaseEvent;

import java.io.Serial;
import java.time.Duration;
import java.time.Instant;

public class CountdownStartedEvent extends BaseEvent {
	@Serial
	private static final long serialVersionUID = -509507578603263778L;

	private final int m_duration;

	public CountdownStartedEvent(final int duration) {
		m_duration = duration;
	}

	public int getDuration() {
		return m_duration;
	}

	public Duration getTimeRemaining() {
		return Duration.between(Instant.now(), getHappenedAt().plusSeconds(m_duration));
	}
}
