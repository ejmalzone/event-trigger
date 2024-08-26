package gg.xp.xivsupport.events.triggers.support;

import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.events.TypedEventHandler;
import gg.xp.reevent.scan.FeedHandlerChildInfo;
import gg.xp.reevent.scan.FeedHelperAdapter;
import gg.xp.reevent.scan.ScanMe;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;

@ScanMe
public class PlayerStatusAdapter implements FeedHelperAdapter<PlayerStatusCallout, BuffApplied, ModifiableCallout<BuffApplied>> {

	@Override
	public Class<BuffApplied> eventType() {
		return BuffApplied.class;
	}

	@Override
	public TypedEventHandler<BuffApplied> makeHandler(FeedHandlerChildInfo<PlayerStatusCallout, ModifiableCallout<BuffApplied>> info) {
		long[] castIds = info.getAnnotation().value();
		return new TypedEventHandler<>() {
			@Override
			public Class<? extends BuffApplied> getType() {
				return BuffApplied.class;
			}

			@Override
			public void handle(EventContext context, BuffApplied event) {
				if (event.getTarget().isThePlayer() && event.buffIdMatches(castIds)) {
					context.accept(info.getHandlerFieldValue().getModified(event));
				}
			}
		};
	}
}
