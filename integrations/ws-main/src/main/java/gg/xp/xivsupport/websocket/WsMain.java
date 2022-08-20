package gg.xp.xivsupport.websocket;

public class WsMain {

	/*
	TODO: actually write this

	Ideal for how to untangle dependencies to make websocket implementations pluggable:
	1. Individual WebSocket modules AND this module depend on xivsupport
	2. This module depends on all known WS implementations
	3. A new scanning thing (like PluginTab but for Settings) is implemented and exposed
	4. This module provides a new WS tab that lets you choose an implementation and configure
		all implementations.
	It would be nice to parameterize instead of #2, but that might be overkill given that
	there's only two implementations.
	 */

}
