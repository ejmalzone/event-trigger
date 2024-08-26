package gg.xp.xivsupport;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.reevent.scan.LiveOnly;
import gg.xp.xivsupport.events.actlines.events.HasPrimaryValue;
import gg.xp.xivsupport.events.triggers.marks.AutoMarkKeyHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.awt.event.KeyEvent;
import java.io.Serial;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MyKeyPressHandler implements FilteredEventHandler {
	public static final class KeyPressRequest extends BaseEvent implements HasPrimaryValue {
		@Serial
		private static final long serialVersionUID = -3520916842042620376L;
		private final int keyCode;

		// Leaving this private for now - need a way to prevent abuse
		public KeyPressRequest(int keyCode) {
			this.keyCode = keyCode;
		}

		public int getKeyCode() {
			return keyCode;
		}

		@Override
		public String getPrimaryValue() {
			return KeyEvent.getKeyText(keyCode);
		}
	}

	private static final ExecutorService exs = Executors.newSingleThreadExecutor();
	private static final Logger log = LoggerFactory.getLogger(MyKeyPressHandler.class);

	@Override
	public boolean enabled(EventContext context) {
		return true;
	}

	@LiveOnly
	@HandleEvents
	public static void doKeyPress(EventContext context, MyKeyPressHandler.KeyPressRequest event) {
		pressAndReleaseKey(event.getKeyCode());
	}

	public static void pressAndReleaseKey(int keyCode) {
		exs.submit(() -> {
			try {
				log.info("Pressing cdKey {} ({})", keyCode, KeyEvent.getKeyText(keyCode));
				new Robot().keyPress(keyCode);
				Thread.sleep(50);
				new Robot().keyRelease(keyCode);
				Thread.sleep(50);
			}
			catch (AWTException | InterruptedException e) {
				throw new RuntimeException(e);
			}
		});
	}
}
