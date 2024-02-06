package gg.xp.xivsupport.events.triggers.duties;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.Event;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.telestosupport.doodle.CreateDoodleRequest;
import gg.xp.telestosupport.doodle.EntityDoodleLocation;
import gg.xp.telestosupport.doodle.LineDoodleSpec;
import gg.xp.xivdata.data.duties.*;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.abilityeffect.StatusAppliedEffect;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.state.combatstate.StatusEffectRepository;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.models.XivCombatant;
import gg.xp.xivsupport.models.XivPlayerCharacter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.swingexplorer.graphics.Player;

import java.awt.*;
import java.sql.Array;
import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;

@CalloutRepo(name = "TEA", duty = KnownDuty.TEA)
public class TEA extends AutoChildEventHandler implements FilteredEventHandler {

	private static final Logger log = LoggerFactory.getLogger(TEA.class);

	private final XivState state;
	private StatusEffectRepository buffs;

	public TEA(XivState state, StatusEffectRepository buffs) {
		this.state = state;
		this.buffs = buffs;
	}

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.TEA);
	}

	public enum Nisi {
		BLUE(0x8AE, 0x8B0, Color.BLUE),
		PURPLE(0x859, 0x85B, Color.MAGENTA),
		ORANGE(0x8AF, 0x8B1, Color.ORANGE),
		GREEN(0x85A, 0x85C, Color.GREEN);

		public final int hasId, needsId;
		public final Color color;

		Nisi(final int has, final int needs, final Color c) {
			hasId = has;
			needsId = needs;
			color = c;
		}
	}

	public EnumMap<Nisi, List<XivCombatant>> findPartners() {
		final List<Long> carryingNisiIds = new ArrayList<>();
		final EnumMap<Nisi, List<XivCombatant>> partners = new EnumMap<>(Nisi.class);

		for (final Nisi nisi : Nisi.values()) {
			// intialize the
			partners.put(nisi, new ArrayList<>());
			// finds the 4 players currently carrying Nisis
			buffs.findBuffsById(nisi.hasId).stream().map(BuffApplied::getTarget).forEach(player -> {
				carryingNisiIds.add(player.getId());
				partners.get(nisi).add(player);
			});
		}

		for (final Nisi nisi : Nisi.values()) {
			buffs.findBuffsById(nisi.needsId).stream().map(BuffApplied::getTarget).forEach(player -> {
				// if the player who needs the nisi doesn't currently have one
				if (!carryingNisiIds.contains(player.getId())) {
					partners.get(nisi).add(player);
				}
			});
		}

		return partners;
	}

	@NpcCastCallout(0x4826)
	private final ModifiableCallout<AbilityCastStart> cascade = new ModifiableCallout<>("Cascade", "Cascade");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> verdict = SqtTemplates.sq(60_000, AbilityCastStart.class, acs -> acs.abilityIdMatches(0x483B),
		(event, context) -> {
			context.waitMs(6_000);
			final StringBuilder builder = new StringBuilder();
			final EnumMap<Nisi, List<XivCombatant>> partners = findPartners();
			for (final Nisi nisi : Nisi.values()) {
				final var players = partners.get(nisi);
				final var player1 = players.get(0);
				final var player2 = players.get(1);

				// log it
				builder.setLength(0);
				builder.append("[PARTNERS FOUND] ");
				builder.append(nisi.name());
				builder.append(": ");
				builder.append(player1.getName());
				builder.append(" <-> ");
				builder.append(player2.getName());
				log.info(builder.toString());

				// make the doodle
				final var location1 = new EntityDoodleLocation(player1);
				final var location2 = new EntityDoodleLocation(player2);
				final var line = new LineDoodleSpec(location1, location2, 10);
				line.color = nisi.color;
				// make it last a long time for now
				line.expiryTime = Duration.ofSeconds(30);

				final var doodleRequest = new CreateDoodleRequest(line);
				context.accept(doodleRequest);
			}
		});
}
