package gg.xp.xivsupport.events.actlines.events;

import gg.xp.reevent.events.BaseEvent;

import java.io.Serial;

public class LbUpdateEvent extends BaseEvent {
	@Serial
	private static final long serialVersionUID = 6319266663308300259L;

	private final long m_amount;
	private final int m_bars;

	public LbUpdateEvent(final long lbAmount, final int bars) {
		m_amount = lbAmount;
		m_bars = bars;
	}

	public long getAmount() {
		return m_amount;
	}

	public int getMaxBars() {
		return m_bars;
	}

	public int getCurrentBars() {
		return (int) (m_amount / 10_000L);
	}
}
