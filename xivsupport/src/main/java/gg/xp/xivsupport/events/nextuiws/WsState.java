package gg.xp.xivsupport.events.nextuiws;

import gg.xp.reevent.context.SubState;

// TODO: just have users query ActWs classes directly
public class WsState implements SubState {

	private volatile boolean isConnected;

	void setConnected(boolean connected) {
		isConnected = connected;
	}

	public boolean isConnected() {
		return isConnected;
	}
}
