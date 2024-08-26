package gg.xp.xivsupport.events.triggers;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.reevent.scan.HandleEvents;
import gg.xp.reevent.scan.LiveOnly;
import gg.xp.telestosupport.TelestoGameCommand;
import gg.xp.xivsupport.events.actlines.events.AbilityResolvedEvent;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.ChatLineEvent;
import gg.xp.xivsupport.events.actlines.events.CountdownStartedEvent;
import gg.xp.xivsupport.events.actlines.events.LbUpdateEvent;
import gg.xp.xivsupport.events.misc.EchoEvent;
import gg.xp.xivsupport.events.misc.pulls.PullStartedEvent;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.speech.TtsRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.Period;
import java.time.temporal.TemporalUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

public class PullHelperHandler extends AutoChildEventHandler implements FilteredEventHandler {
	private static final Logger log = LoggerFactory.getLogger(PullHelperHandler.class);

	@Override
	public boolean enabled(EventContext context) {
		return true;
	}

	@AutoFeed
	public final SequentialTrigger<BaseEvent> pullTimingEvent = SqtTemplates.sq(60 * 1_000, CountdownStartedEvent.class, countdownStartedEvent -> true,
		(pullStartedEvent, context) -> {
			final DecimalFormat df = new DecimalFormat("0.00");
			df.setRoundingMode(RoundingMode.FLOOR);

//			context.accept(new TtsRequest("pull started"));
			// wait for any damage that isn't superbolide
			final Set<Long> playerPulls = new HashSet<>();
			log.info("Waiting for damage events...");

			final List<AbilityResolvedEvent> firstDamages = context.waitEventsQuickSuccession(8, AbilityResolvedEvent.class, abilityResolvedEvent -> {
				final var abilityUsedEvent = abilityResolvedEvent.getOriginalEvent();
				if (!abilityUsedEvent.getSource().isPc()) {
					log.info("NPC ability");
					return false;
				}

				// if its superbolide
				if (abilityUsedEvent.getAbility().getId() == 0x3F18) {
					log.info("Ability rejected: Superbolide");
					return false;
				}

				final long actorId = abilityUsedEvent.getSource().getId();
				if (abilityUsedEvent.getDamage() > 0 && !playerPulls.contains(actorId)) {
					log.info("Damaging ability found from new actor: " + abilityUsedEvent.getSource().getName());
					playerPulls.add(actorId);
					return true;
				}

				log.info("Non-damaging ability found or existent actor: " + abilityUsedEvent.getSource().getName());
				// either non-damaging ability or player has already pulled
				return false;
			}, Duration.ofSeconds(10));

			firstDamages.sort(Comparator.comparing(BaseEvent::getHappenedAt));

			final Instant startTime = firstDamages.get(0).getHappenedAt();
			final Stream<TelestoGameCommand> commands = firstDamages.stream().map(event -> {
				final StringBuilder builder = new StringBuilder();
				builder.append(event.getSource().getName());
				builder.append(": ");
				builder.append(df.format(Duration.between(startTime, event.getHappenedAt()).toMillis() / 1_000.0));
				return new TelestoGameCommand("/p " + builder);
			});

			commands.forEach(context::accept);
		});

	@AutoFeed
	public final SequentialTrigger<BaseEvent> rangePullEvent = SqtTemplates.sq(10 * 60 * 1_000, CountdownStartedEvent.class, countdownStartedEvent -> true,
		(pullStartedEvent, context) -> {
			final DecimalFormat df = new DecimalFormat("-0.00");
			df.setRoundingMode(RoundingMode.FLOOR);

			final var firstLbTick = context.waitEvent(LbUpdateEvent.class, lbUpdateEvent -> true);
			final var firstDamage = context.waitEvent(AbilityResolvedEvent.class, abilityResolvedEvent -> {
				final var abilityUsedEvent = abilityResolvedEvent.getOriginalEvent();

				// if it's a boss move, we don't care
				if (!abilityResolvedEvent.getSource().isPc()) {
					return false;
				}

				// if its superbolide, we don't care
				if (abilityUsedEvent.getAbility().getId() == 0x3F18) {
					return false;
				}

				return abilityUsedEvent.getDamage() > 0;
			});

			final StringBuilder builder = new StringBuilder();
			builder.append("aggro tick time: ");
			builder.append(df.format(Duration.between(firstLbTick.getHappenedAt(), firstDamage.getHappenedAt()).toMillis() / 1_000.0));
			context.accept(new TelestoGameCommand("/p " + builder));
		});
}
