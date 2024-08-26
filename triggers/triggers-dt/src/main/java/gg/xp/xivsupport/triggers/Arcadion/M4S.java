package gg.xp.xivsupport.triggers.Arcadion;

import gg.xp.reevent.events.BaseEvent;
import gg.xp.reevent.events.EventContext;
import gg.xp.reevent.scan.AutoChildEventHandler;
import gg.xp.reevent.scan.AutoFeed;
import gg.xp.reevent.scan.FilteredEventHandler;
import gg.xp.xivdata.data.*;
import gg.xp.xivdata.data.duties.*;
import gg.xp.xivsupport.callouts.CalloutRepo;
import gg.xp.xivsupport.callouts.ModifiableCallout;
import gg.xp.xivsupport.events.actlines.events.AbilityCastStart;
import gg.xp.xivsupport.events.actlines.events.AbilityUsedEvent;
import gg.xp.xivsupport.events.actlines.events.ActorControlExtraEvent;
import gg.xp.xivsupport.events.actlines.events.BuffApplied;
import gg.xp.xivsupport.events.actlines.events.DescribesCastLocation;
import gg.xp.xivsupport.events.actlines.events.HasPrimaryValue;
import gg.xp.xivsupport.events.actlines.events.MapEffectEvent;
import gg.xp.xivsupport.events.actlines.events.TetherEvent;
import gg.xp.xivsupport.events.actlines.events.vfx.StatusLoopVfxApplied;
import gg.xp.xivsupport.events.state.XivState;
import gg.xp.xivsupport.events.state.combatstate.ActiveCastRepository;
import gg.xp.xivsupport.events.state.combatstate.StatusEffectRepository;
import gg.xp.xivsupport.events.triggers.seq.SequentialTrigger;
import gg.xp.xivsupport.events.triggers.seq.SequentialTriggerConcurrencyMode;
import gg.xp.xivsupport.events.triggers.seq.SequentialTriggerController;
import gg.xp.xivsupport.events.triggers.seq.SqtTemplates;
import gg.xp.xivsupport.events.triggers.support.NpcCastCallout;
import gg.xp.xivsupport.events.triggers.support.PlayerStatusCallout;
import gg.xp.xivsupport.models.ArenaPos;
import gg.xp.xivsupport.models.ArenaSector;
import gg.xp.xivsupport.models.Position;
import gg.xp.xivsupport.models.XivCombatant;
import gg.xp.xivsupport.models.XivPlayerCharacter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serial;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

@CalloutRepo(name = "M4S", duty = KnownDuty.M4S)
public class M4S extends AutoChildEventHandler implements FilteredEventHandler {

	private static final Logger log = LoggerFactory.getLogger(M4S.class);

	public M4S(XivState state, StatusEffectRepository buffs, ActiveCastRepository casts) {
		this.state = state;
		this.buffs = buffs;
		this.casts = casts;
	}

	private XivState state;
	private StatusEffectRepository buffs;
	private ActiveCastRepository casts;
	private static final ArenaPos ap = new ArenaPos(100, 100, 5, 5);
	private static final ArenaPos apOuterCorners = new ArenaPos(100, 100, 12, 12);
	private static final int positronBuff = 0xFA0;
	private static final int negatronBuff = 0xFA1;

	@Override
	public boolean enabled(EventContext context) {
		return state.dutyIs(KnownDuty.M4S);
	}

	@NpcCastCallout(0x95EF)
	private final ModifiableCallout<AbilityCastStart> wrathOfZeus = ModifiableCallout.durationBasedCall("Wrath of Zeus", "Raidwide");

	private final ModifiableCallout<AbilityCastStart> electrifyingInsideSafe = ModifiableCallout.durationBasedCall("Electrifying Witch Hunt: Inside Safe", "Inside, Avoid Lines");
	private final ModifiableCallout<AbilityCastStart> electrifyingOutsideSafe = ModifiableCallout.durationBasedCall("Electrifying Witch Hunt: Outside Safe", "Outside, Avoid Lines");
	@AutoFeed
	private final SequentialTrigger<BaseEvent> electrifyingWitchHunt = SqtTemplates.sq(30_000,
			(AbilityCastStart.class), acs -> acs.abilityIdMatches(0x95E5),
			(e1, s) -> {
				int count = casts.getActiveCastsById(0x95EA).size();
				if (count == 2) {
					s.updateCall(electrifyingInsideSafe, e1);
				}
				else if (count == 3) {
					s.updateCall(electrifyingOutsideSafe, e1);
				}
				else {
					log.error("Bad count: {}", count);
				}
			});

	@NpcCastCallout({0x8DEF, 0x9671})
	private final ModifiableCallout<AbilityCastStart> bewitchingFlight = ModifiableCallout.durationBasedCall("Betwitching Flight", "Avoid Lines");

	@NpcCastCallout(0x92C2)
	private final ModifiableCallout<AbilityCastStart> wickedBolt = ModifiableCallout.durationBasedCall("Wicked Bolt", "Stack, Multiple Hits");

	private static boolean baitOut(BuffApplied buff) {
		long rawStacks = buff.getRawStacks();
		if (rawStacks == 759) {
			return true;
		}
		else if (rawStacks == 758) {
			return false;
		}
		else {
			throw new IllegalArgumentException("Unrecognized stack count %s".formatted(rawStacks));
		}

	}

	/*
	Buff b9a for witch hunt
	These all apply at the start, so need to collect them
	759 bait far?
	758 bait close?
	 */
	// electrifying witch hunt 95e5: ?
	// This puts stuff on 4 people
	// other 4 have to bait
	// bait near/far based on buff
	private final ModifiableCallout<AbilityCastStart> witchHuntInsideNoBait = ModifiableCallout.durationBasedCall("Witch Hunt: Inside Safe, No Bait", "Inside, Stay { baitOut ? 'In' : 'Out'}");
	private final ModifiableCallout<AbilityCastStart> witchHuntInsideBait = ModifiableCallout.durationBasedCall("Witch Hunt: Inside Safe, Bait", "Inside, Bait { baitOut ? 'Out' : 'In' }");
	private final ModifiableCallout<AbilityCastStart> witchHuntOutsideNoBait = ModifiableCallout.durationBasedCall("Witch Hunt: Outside Safe, No Bait", "Outside, Stay { baitOut ? 'In' : 'Out'}");
	private final ModifiableCallout<AbilityCastStart> witchHuntOutsideBait = ModifiableCallout.durationBasedCall("Witch Hunt: Outside Safe, Bait", "Outside, Bait { baitOut ? 'Out' : 'In' }");
	@AutoFeed
	private final SequentialTrigger<BaseEvent> witchHunt = SqtTemplates.sq(30_000,
			// TODO: other ID?
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x95DE),
			(e1, s) -> {
				int count = casts.getActiveCastsById(0x95EA).size();
				// Whether the inside is safe, else outside is safe
				// If there are two bursts, inside is safe. otherwise outside is safe.
				boolean insideSafe = count == 2;
				// Whether player is baiting
				// Player should bait if they do not have the lightning buff
				boolean playerBaiting = !buffs.isStatusOnTarget(state.getPlayer(), 0x24B);
				log.info("Player baiting");
				BuffApplied bossBuff = s.findOrWaitForBuff(buffs, ba -> ba.getTarget().equals(e1.getSource()) && ba.buffIdMatches(0xB9A));

				// true means bait is far, false means bait is close
				boolean baitOut = baitOut(bossBuff);
				s.setParam("baitOut", baitOut);
				// TODO: got a wrong call. Called correct in/out, but bait position was wrong
				// 3:16 PM
				if (insideSafe) {
					s.updateCall(playerBaiting ? witchHuntInsideBait : witchHuntInsideNoBait, e1);
				}
				else {
					s.updateCall(playerBaiting ? witchHuntOutsideBait : witchHuntOutsideNoBait, e1);
				}
			});

	// widening witch hunt: 95e0: out first
	// alternates between close/far
	// narrowing witch hunt: 95e1: in first
	// alternates between close/far
	private final ModifiableCallout<AbilityCastStart> widening = ModifiableCallout.durationBasedCall("Widening Initial", "Outside, Baiters { baitOut ? 'Out' : 'In'}");
	private final ModifiableCallout<AbilityCastStart> narrowing = ModifiableCallout.durationBasedCall("Narrowing Initial", "Inside, Baiters { baitOut ? 'Out' : 'In'}");
	private final ModifiableCallout<?> wideNarrowOutF = new ModifiableCallout<>("Widening/Narrowing Outside Followup", "Outside, Baiters { baitOut ? 'Out' : 'In'}");
	private final ModifiableCallout<?> wideNarrowInF = new ModifiableCallout<>("Widening/Narrowing Inside Followup", "Inside, Baiters { baitOut ? 'Out' : 'In'}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> wideningNarrowing = SqtTemplates.sq(60_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x95e0, 0x95e1),
			(e1, s) -> {
				boolean isWidening = e1.abilityIdMatches(0x95e0);
				var baitOuts = new boolean[4];
				var firstBuff = s.findOrWaitForBuff(buffs, ba -> ba.getTarget().equals(e1.getSource()) && ba.buffIdMatches(0xB9A));
				baitOuts[0] = baitOut(firstBuff);

				s.setParam("baitOut", baitOuts[0]);
				if (isWidening) {
					s.updateCall(widening, e1);
				}
				else {
					s.updateCall(narrowing, e1);
				}

				for (int i = 1; i <= 3; i++) {
					var nextBuff = s.waitEvent(BuffApplied.class, ba -> ba.buffIdMatches(0xB9A));
					baitOuts[i] = baitOut(nextBuff);
				}

				s.waitEvent(AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x4D11, 0x4D12));

				for (int i = 1; i <= 3; i++) {
					s.setParam("baitOut", baitOuts[i]);
					// The widening/narrowing alternates each time
					if (isWidening ^ (i % 2 != 0)) {
						// We already called first one
						s.updateCall(wideNarrowOutF);
					}
					else {
						s.updateCall(wideNarrowInF);
					}
					s.waitEvent(AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x4D11, 0x4D12));

				}

			});

	@NpcCastCallout(0x95c6)
	private final ModifiableCallout<AbilityCastStart> witchgleamBasic = ModifiableCallout.durationBasedCall("Witchgleam: Basic", "Stand on Cardinals, Multiple Hits");
	@NpcCastCallout(0x95f0)
	private final ModifiableCallout<AbilityCastStart> wickedJolt = ModifiableCallout.durationBasedCall("Wicked Jolt", "Tank Buster on {event.target}");

	private final ModifiableCallout<AbilityCastStart> sparkBuddies = ModifiableCallout.durationBasedCall("Sidewise Spark + Buddies", "Buddies {safeSide}");
	private final ModifiableCallout<AbilityCastStart> sparkSpread = ModifiableCallout.durationBasedCall("Sidewise Spark + Spread", "Spread {safeSide}");

	private enum SparkMech {
		Buddies,
		Spread
	}

	private @Nullable SparkMech getSparkMech() {
		XivCombatant boss = state.npcById(17322);
		if (boss == null) {
			log.error("No boss!");
			return null;
		}
		var buff = buffs.findStatusOnTarget(boss, 0xB9A);
		if (buff == null) {
			log.error("No buff!");
			return null;
		}
		switch ((int) buff.getRawStacks()) {
			case 752 -> {
				return SparkMech.Buddies;
			}
			case 753 -> {
				return SparkMech.Spread;
			}
			default -> {
				log.error("Unknown buff stacks: {}", buff.getRawStacks());
				return null;
			}
		}
	}

	//95c8 symphoniy fantastique
	@AutoFeed
	private final SequentialTrigger<BaseEvent> symphonyFantastique = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x95c8),
			(e1, s) -> {
				// The cross call is handled elsewhere
				// Gather Spark II casts
				var spark2s = s.waitEvents(2, AbilityCastStart.class, acs -> acs.abilityIdMatches(0x95CA));
				// Gather sidewise spark cast
				AbilityCastStart sidewiseSpark = s.waitEvent(AbilityCastStart.class, acs -> acs.abilityIdMatches(0x95ED, 0x95EC));
				// Compute safe
				var safeSide = sidewiseSpark.abilityIdMatches(0x95EC) ? ArenaSector.WEST : ArenaSector.EAST;
				Set<ArenaSector> possibleSafe = EnumSet.of(safeSide.plusEighths(-1), safeSide.plusEighths(1));
				spark2s.stream().map(AbilityCastStart::getSource).map(ap::forCombatant).forEach(possibleSafe::remove);
				if (possibleSafe.size() == 1) {
					s.setParam("safeSide", possibleSafe.iterator().next());
				}
				else {
					s.setParam("safeSide", ArenaSector.UNKNOWN);
				}
				SparkMech sparkMech = getSparkMech();
				if (sparkMech == SparkMech.Buddies) {
					s.updateCall(sparkBuddies, sidewiseSpark);
				}
				else if (sparkMech == SparkMech.Spread) {
					s.updateCall(sparkSpread, sidewiseSpark);
				}
				else {
					log.error("Sparkmech null!");
				}

			});

	private final ModifiableCallout<AbilityCastStart> electropeEdgeInitial = ModifiableCallout.durationBasedCall("Electrope Edge", "Clock Positions");
	private final ModifiableCallout<?> electropeEdgeFail = new ModifiableCallout<>("Electrope Edge: Fail/Invalid", "Fail");
	private final ModifiableCallout<?> electropeEdge1long = new ModifiableCallout<>("Electrope Edge: 1 Long", "1 Long")
			.extendedDescription("""
					Please note that long 1 and long 2 are commonly referred to as long 2 and long 3 by the community. \
					In addition, all of these callouts have variables {playerCount} which is the number of times you were hit, \
					{playerCountPlus} which is adjusted to always be 2 or 3 (i.e. add one to playerCount if you were long), and \
					{playerIsLong} which is true if you were long.""");
	private final ModifiableCallout<?> electropeEdge2long = new ModifiableCallout<>("Electrope Edge: 2 Long", "2 Long");
	private final ModifiableCallout<?> electropeEdge2short = new ModifiableCallout<>("Electrope Edge: 2 Short", "2 Short");
	private final ModifiableCallout<?> electropeEdge3short = new ModifiableCallout<>("Electrope Edge: 3 Short", "3 Short");

	private final ModifiableCallout<?> electropeSafeSpot = new ModifiableCallout<>("Electrope Edge: Nothing", "{safe} Safe");
	private final ModifiableCallout<?> electropeSides = new ModifiableCallout<>("Electrope Edge: Spark II (Sides)", "Spark 2 - {sides}");
	private final ModifiableCallout<?> electropeCorners = new ModifiableCallout<>("Electrope Edge: Spark III (Far Corners)", "Spark 3 - {corners}");

	private final ModifiableCallout<AbilityCastStart> electropeBuddies = ModifiableCallout.durationBasedCall("Sidewise Spark + Buddies", "Buddies {safeSide} with {buddies}");
	private final ModifiableCallout<AbilityCastStart> electropeSpread = ModifiableCallout.durationBasedCall("Sidewise Spark + Spread", "Spread {safeSide}");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> electropeEdge = SqtTemplates.multiInvocation(120_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x95c5),
			(e1, s) -> {
				// TODO
				// or is this handled by other triggers already?
			},
			(e1, s) -> {
				s.updateCall(electropeEdgeInitial, e1);
				int condenserBuffId = 0xF9F;
				var myBuff = s.waitEvent(BuffApplied.class, ba -> ba.buffIdMatches(condenserBuffId) && ba.getTarget().isThePlayer());
				// Collect hits, stop when we see lightning cage cast
				List<AbilityUsedEvent> hits = s.waitEventsUntil(99, AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x9786),
						AbilityCastStart.class, acs -> acs.abilityIdMatches(0x95CE));
				int myCount = (int) hits.stream().filter(hit -> hit.getTarget().isThePlayer()).count();
				// 22 short, 42 long
				boolean playerIsLong = myBuff.getInitialDuration().toSeconds() > 30;
				s.setParam("playerIsLong", playerIsLong);
				s.setParam("playerCount", myCount);
				s.setParam("playerCountPlus", playerIsLong ? (myCount + 1) : myCount);
				log.info("Electrope {} {}", myCount, playerIsLong);
				if (playerIsLong) {
					s.updateCall(switch (myCount) {
						case 1 -> electropeEdge1long;
						case 2 -> electropeEdge2long;
						default -> electropeEdgeFail;
					});
				}
				else {
					s.updateCall(switch (myCount) {
						case 2 -> electropeEdge2short;
						case 3 -> electropeEdge3short;
						default -> electropeEdgeFail;
					});
				}

				{
					var lightningCageCasts = s.waitEventsQuickSuccession(12, AbilityCastStart.class, acs -> acs.abilityIdMatches(0x95CF));
					s.waitMs(50);
					// try cast positions first
					var unsafeCorners = lightningCageCasts.stream()
							.map(AbilityCastStart::getLocationInfo)
							.filter(Objects::nonNull)
							.map(DescribesCastLocation::getPos)
							.filter(Objects::nonNull)
							.map(apOuterCorners::forPosition)
							.filter(ArenaSector::isIntercard)
							.toList();
					int limit = 5;
					while (unsafeCorners.size() != 2) {
						s.waitThenRefreshCombatants(100);
						// The safe spot is always between the unsafe corners
						unsafeCorners = lightningCageCasts.stream()
								.map(AbilityCastStart::getSource)
								.map(state::getLatestCombatantData)
								.map(apOuterCorners::forCombatant)
								.filter(ArenaSector::isIntercard)
								.toList();
						if (limit-- < 0) {
							log.error("unsafeCorners fail!");
							break;
						}
					}
					if (unsafeCorners.size() == 2) {
						ArenaSector safe = ArenaSector.tryCombineTwoQuadrants(unsafeCorners);
						if (safe == null) {
							log.error("Safe fail! unsafeCorners: {}", unsafeCorners);
						}
						else {

							s.setParam("safe", safe);
							s.setParam("sides", List.of(safe.plusEighths(-2), safe.plusEighths(2)));
							s.setParam("corners", List.of(safe.plusEighths(-3), safe.plusEighths(3)));
							if (playerIsLong) {
								// Call safe spot
								s.updateCall(electropeSafeSpot);
							}
							else {
								if (myCount == 2) {
									s.updateCall(electropeSides);
								}
								else {
									s.updateCall(electropeCorners);
								}
							}
						}
					}
					else {
						// This should be fixed now
						log.error("unsafeCorners bad! {} {}", unsafeCorners, lightningCageCasts);
					}
				}

				// The boss also does a sidewise spark 95ED (cleaving left) or 95EC (cleaving right)

				AbilityCastStart sidewiseSpark = s.waitEvent(AbilityCastStart.class, acs -> acs.abilityIdMatches(0x95ED, 0x95EC));
				s.setParam("safeSide", sidewiseSpark.abilityIdMatches(0x95EC) ? ArenaSector.WEST : ArenaSector.EAST);
				SparkMech sparkMech = getSparkMech();
				if (sparkMech == SparkMech.Buddies) {
					List<XivPlayerCharacter> buddies;
					Job playerJob = state.getPlayerJob();
					boolean playerIsDps = playerJob.isDps();
					if (playerIsLong) {
						// look for buddies with no debuff and same role
						buddies = state.getPartyList()
								.stream()
								.filter(pc -> pc.getJob().isDps() == playerIsDps)
								.filter(pc -> !buffs.isStatusOnTarget(pc, condenserBuffId))
								// Prioritize same role first
								.sorted(Comparator.comparing(pc -> pc.getJob().getCategory() == playerJob.getCategory() ? 0 : 1))
								.toList();
					}
					else {
						// look for buddies with debuff and same role
						buddies = buffs.findBuffs(ba -> ba.buffIdMatches(condenserBuffId))
								.stream()
								.map(BuffApplied::getTarget)
								.map(XivPlayerCharacter.class::cast)
								.filter(pc -> pc.getJob().isDps() == playerIsDps)
								// Prioritize same role first
								.sorted(Comparator.comparing(pc -> pc.getJob().getCategory() == playerJob.getCategory() ? 0 : 1))
								.toList();
					}
					s.setParam("buddies", buddies);
					s.updateCall(electropeBuddies, sidewiseSpark);
				}
				else if (sparkMech == SparkMech.Spread) {
					s.updateCall(electropeSpread, sidewiseSpark);

				}
				else {
					log.error("Sparkmech null!");
				}

				{
					var lightningCageCasts = s.waitEventsQuickSuccession(12, AbilityCastStart.class, acs -> acs.abilityIdMatches(0x95CF));
					s.waitMs(50);
					// try cast positions first
					var unsafeCorners = lightningCageCasts.stream()
							.map(AbilityCastStart::getLocationInfo)
							.filter(Objects::nonNull)
							.map(DescribesCastLocation::getPos)
							.filter(Objects::nonNull)
							.map(apOuterCorners::forPosition)
							.filter(ArenaSector::isIntercard)
							.toList();
					int limit = 5;
					while (unsafeCorners.size() != 2) {
						s.waitThenRefreshCombatants(100);
						// The safe spot is always between the unsafe corners
						unsafeCorners = lightningCageCasts.stream()
								.map(AbilityCastStart::getSource)
								.map(state::getLatestCombatantData)
								.map(apOuterCorners::forCombatant)
								.filter(ArenaSector::isIntercard)
								.toList();
						if (limit-- < 0) {
							log.error("unsafeCorners fail!");
							break;
						}
					}
					if (unsafeCorners.size() == 2) {
						ArenaSector safe = ArenaSector.tryCombineTwoQuadrants(unsafeCorners);
						s.setParam("safe", safe);
						s.setParam("sides", List.of(safe.plusEighths(-2), safe.plusEighths(2)));
						s.setParam("corners", List.of(safe.plusEighths(-3), safe.plusEighths(3)));
						if (!playerIsLong) {
							// Call safe spot
							s.updateCall(electropeSafeSpot);
						}
						else {
							if (myCount == 1) {
								s.updateCall(electropeSides);
							}
							else {
								s.updateCall(electropeCorners);
							}
						}

					}
					else {
						// This should be fixed now
						log.error("unsafeCorners bad! {} {}", unsafeCorners, lightningCageCasts);
					}
				}

				// stack marker handled elsewhere


			});

	private final ModifiableCallout<AbilityCastStart> westSafe = ModifiableCallout.durationBasedCall("Stampeding Thunder: West Safe", "West");
	private final ModifiableCallout<AbilityCastStart> eastSafe = ModifiableCallout.durationBasedCall("Stampeding Thunder: East Safe", "East");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> stampedingThunderSq = SqtTemplates.sq(30_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x8E2F),
			(e1, s) -> {
				s.waitThenRefreshCombatants(200);
				boolean westHit = state.getLatestCombatantData(e1.getTarget()).getPos().x() < 100;
				if (westHit) {
					s.updateCall(eastSafe, e1);
				}
				else {
					s.updateCall(westSafe, e1);
				}
			});

	private final ModifiableCallout<AbilityCastStart> positronStream = ModifiableCallout.durationBasedCall("Positron", "Go {positive}");
	private final ModifiableCallout<AbilityCastStart> negatronStream = ModifiableCallout.durationBasedCall("Negatron", "Go {negative}");

	@PlayerStatusCallout(0xFA2)
	private final ModifiableCallout<BuffApplied> remote = ModifiableCallout.<BuffApplied>durationBasedCall("Remote Current", "Remote Current").autoIcon();
	@PlayerStatusCallout(0xFA3)
	private final ModifiableCallout<BuffApplied> proximate = ModifiableCallout.<BuffApplied>durationBasedCall("Proximate Current", "Proximate Current").autoIcon();
	@PlayerStatusCallout(0xFA4)
	private final ModifiableCallout<BuffApplied> spinning = ModifiableCallout.<BuffApplied>durationBasedCall("Spinning Conductor", "Spinning").autoIcon();
	@PlayerStatusCallout(0xFA5)
	private final ModifiableCallout<BuffApplied> roundhouse = ModifiableCallout.<BuffApplied>durationBasedCall("Roundhouse Conductor", "Roundhouse - Spread").autoIcon();
	@PlayerStatusCallout(0xFA6)
	private final ModifiableCallout<BuffApplied> collider = ModifiableCallout.<BuffApplied>durationBasedCall("Collider Conductor", "Get Hit by Protean").autoIcon();

	@AutoFeed
	private final SequentialTrigger<BaseEvent> electronStream = SqtTemplates.sq(120_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x95D6, 0x95D7),
			(e1, s) -> {
//				hits with positron stream (95d8) and negatron stream (95d9)
//				get hit by opposite color
//				applies to 1 of each group:
//				Collider Conductor (7s, FA6)
//				2x Spinning Conductor (5s, FA4) OR ???
//				Remote Conductor (5s, FA2) OR ??? (FA3 far tether)
//				You also get 2 stacks of the opposite of what you got hit by (e.g. positron gets negatron):
//				Positron FA0
//				Negatron FA1
//
//				Collider (FA6) - need to get hit by protean
//				Close tether (FA3) or far tether (FA2) - will shoot protean when tether condition satisfied
//				Spinning (FA4) - dynamo
//						? (FA5) - tiny chariot

				// do it again 2 more times


				for (int i = 0; i < 3; i++) {
					var posCast = s.findOrWaitForCast(casts, acs -> acs.abilityIdMatches(0x95D8), false);
					// TODO: use positions if this continues to be flaky

					s.waitThenRefreshCombatants(100);
					ArenaSector positiveSide = ArenaPos.combatantFacing(state.getLatestCombatantData(posCast.getSource()));
					s.setParam("positive", positiveSide);
					s.setParam("negative", positiveSide.opposite());

					var playerBuff = buffs.findStatusOnTarget(state.getPlayer(), ba -> ba.buffIdMatches(positronBuff, negatronBuff));
					if (playerBuff == null) {
						log.error("Player has no buff!");
					}
					else if (playerBuff.buffIdMatches(positronBuff)) {
						// get hit by pos
						s.updateCall(negatronStream, posCast);
					}
					else {
						// get hit by neg
						s.updateCall(positronStream, posCast);
					}
					// delay so we don't immediately re-capture the same event
					s.waitCastFinished(casts, posCast);
					s.waitMs(1_000);
				}
			});

	private final ModifiableCallout<AbilityCastStart> transplantCast = ModifiableCallout.durationBasedCall("Electrope Transplant: Casted", "Dodge Proteans");
	private final ModifiableCallout<?> transplantMove = new ModifiableCallout<>("Electrope Transplant: Instant", "Move");
	private final ModifiableCallout<?> transplantMoveFront = new ModifiableCallout<>("Electrope Transplant: Cover", "Cover");
	private final ModifiableCallout<?> transplantMoveBack = new ModifiableCallout<>("Electrope Transplant: Get Covered", "Behind");
	private final ModifiableCallout<?> transition = new ModifiableCallout<>("Transition", "Multiple Raidwides, Get Knocked South");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> electropeTransplant = SqtTemplates.sq(120_000,
			(AbilityCastStart.class), acs -> acs.abilityIdMatches(0x98D3),
			(e1, s) -> {
				int waitTime = 200;
				log.info("Electrope Transplant: Start");
				for (int i = 0; i < 2; i++) {

					log.info("Electrope Transplant: Start round {}", i);
					AbilityCastStart cast = s.waitEvent(AbilityCastStart.class, acs -> acs.abilityIdMatches(0x90FE));
					s.updateCall(transplantCast, cast);
					s.waitEvent(AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x90FE));
					s.updateCall(transplantMove);
					s.waitMs(waitTime);
					s.waitEvent(AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x98CD));
					s.updateCall(transplantMove);
					s.waitMs(waitTime);
					s.waitEvent(AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x98CD));
					s.updateCall(transplantMove);
					s.waitMs(waitTime);
					s.waitEvent(AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x98CD));
					s.updateCall(transplantMove);
					s.waitMs(waitTime);
					s.waitEvent(AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x98CD));
					List<XivCombatant> playersThatGotHit = s.waitEventsQuickSuccession(8, AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x98CE))
							.stream()
							.map(AbilityUsedEvent::getTarget)
							.toList();
					if (playersThatGotHit.contains(state.getPlayer())) {
						s.updateCall(transplantMoveBack);
					}
					else {
						s.updateCall(transplantMoveFront);
					}
					s.waitMs(waitTime);
					s.waitEvent(AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x98CD));
					s.updateCall(transplantMove);
					s.waitMs(waitTime);
				}
				s.waitMs(1800);
				s.updateCall(transition);
			});


	// POST TRANSITION

	private static final ArenaPos finalAp = new ArenaPos(100, 165, 5, 5);

	@NpcCastCallout(0x95F2)
	private final ModifiableCallout<AbilityCastStart> crossTailSwitch = ModifiableCallout.durationBasedCall("Cross Tail Switch", "Multiple Raidwides");

	private final ModifiableCallout<AbilityCastStart> sabertailSafeSpots = ModifiableCallout.<AbilityCastStart>durationBasedCall("Sabertail Safe Spots", "{westSafeDirection} {westSafeEdge ? 'Side' : 'Corner'} and {eastSafeDirection} {eastSafeEdge ? 'Side' : 'Corner'}")
			.extendedDescription("""
					By default, this callout will call out both sides, in a form like 'Southwest Edge and Northeast Corner', indicating that the \
					middle-south row is safe for the west group, and the northmost row is safe for the east group. You can also use the parameters \
					{westSafeRow} and {eastSafeRow}, which are simple numbers where 1 is the northmost row, and 4 is the southmost row.
					""");

	private static final int exaflareCastId = 0x95F5;

	@AutoFeed
	private final SequentialTrigger<BaseEvent> saberTailSq = SqtTemplates.sq(10_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(exaflareCastId),
			(e1, s) -> {
				// Which row (1 = northmost, 4 = southmost) for the west side and right side
				Integer westSafeRow = null;
				Integer eastSafeRow = null;
				List<AbilityCastStart> casts = new ArrayList<>(16);
				casts.add(e1);
				casts.addAll(s.waitEvents(15, AbilityCastStart.class, acs -> acs.abilityIdMatches(exaflareCastId)));

				// Should have all 16 casts now

				s.waitThenRefreshCombatants(200);

				Map<Integer, List<Position>> exas = casts.stream()
						.map(AbilityCastStart::getSource)
						.map(state::getLatestCombatantData)
						.map(XivCombatant::getPos)
						.collect(Collectors.groupingBy(item -> {
							double y = item.getY();
							// Intentionally skipping any that seem to be in the middle
							if (y < 155) {
								return 1;
							}
							else if (y < 162) {
								return 2;
							}
							else if (y > 178) {
								return 4;
							}
							else if (y > 168) {
								return 3;
							}
							// Invalid
							else {
								throw new IllegalArgumentException("Invalid y: " + y);
							}
						}));

				for (var entry : exas.entrySet()) {
					Integer row = entry.getKey();
					List<Position> positions = entry.getValue();
// If none are facing west, then that row is safe for west party
					if (positions.stream().noneMatch(pos -> ArenaPos.combatantFacing(pos) == ArenaSector.WEST)) {
						westSafeRow = row;
					}
					else if (positions.stream().noneMatch(pos -> ArenaPos.combatantFacing(pos) == ArenaSector.EAST)) {
						eastSafeRow = row;
					}
					// else, not a safe row for either side
				}

				if (westSafeRow != null && eastSafeRow != null) {
					s.setParam("westSafeRow", westSafeRow);
					s.setParam("westSafeDirection", switch (westSafeRow) {
						case 1, 2 -> ArenaSector.NORTHWEST;
						case 3, 4 -> ArenaSector.SOUTHWEST;
						default -> ArenaSector.UNKNOWN;
					});
					s.setParam("westSafeEdge", westSafeRow == 2 || westSafeRow == 3);

					s.setParam("eastSafeRow", eastSafeRow);
					s.setParam("eastSafeDirection", switch (eastSafeRow) {
						case 1, 2 -> ArenaSector.NORTHEAST;
						case 3, 4 -> ArenaSector.SOUTHEAST;
						default -> ArenaSector.UNKNOWN;
					});
					s.setParam("eastSafeEdge", eastSafeRow == 2 || eastSafeRow == 3);
					s.updateCall(sabertailSafeSpots, e1);
				}

			});

	// Wicked special: out of middle (9610, 9611)
	// in middle 9612 + 2x 9613

	private final ModifiableCallout<AbilityCastStart> wickedSpecialOutOfMiddle = ModifiableCallout.durationBasedCall("Wicked Special: Out of Middle", "Sides");
	private final ModifiableCallout<AbilityCastStart> wickedSpecialInMiddle = ModifiableCallout.durationBasedCall("Wicked Special: In Middle", "Middle");

	// The ones not run here are handled elsewhere
	@AutoFeed
	private final SequentialTrigger<BaseEvent> wickedSpecialStandalone = SqtTemplates.multiInvocation(60_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x9610, 0x9612),
			(e1, s) -> {
				if (e1.abilityIdMatches(0x9610)) {
					s.updateCall(wickedSpecialOutOfMiddle, e1);
				}
				else {
					s.updateCall(wickedSpecialInMiddle, e1);
				}
			});

	// The two people that did nothing need to grab the tethers
	private final ModifiableCallout<?> mustardBombInitialTetherNonTank = new ModifiableCallout<>("Mustard Bombs: Initial Tether, Not Tank", "Tethers to Tanks then Spread");
	private final ModifiableCallout<?> mustardBombInitialTank = new ModifiableCallout<>("Mustard Bombs: Tank", "Grab Tethers");
	private final ModifiableCallout<?> mustardBombAvoidTethers = new ModifiableCallout<>("Mustard Bombs: Avoid Tethers", "Avoid Tethers");
	private final ModifiableCallout<?> mustardBombTankAfter = new ModifiableCallout<>("Mustard Bombs: Tank", "Give Tethers Away");
	private final ModifiableCallout<?> mustardBombGrabTethersAfter = new ModifiableCallout<>("Mustard Bombs: Grab Tethers", "Grab Bombs from Tanks");

	// azure thunmder 962f
	@NpcCastCallout(0x962F)
	private final ModifiableCallout<AbilityCastStart> azureThunder = ModifiableCallout.durationBasedCall("Azure Thunder", "Raidwide");


	@AutoFeed
	private final SequentialTrigger<BaseEvent> mustardBomb = SqtTemplates.sq(60_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x961E),
			(e1, s) -> {
				if (state.playerJobMatches(Job::isTank)) {
					s.updateCall(mustardBombInitialTank);
					s.waitEvent(AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x961F));
					s.updateCall(mustardBombTankAfter);
				}
				else {
					s.updateCall(mustardBombInitialTetherNonTank);
					// Kindling Cauldron hits
					var kindlingCauldrons = s.waitEventsQuickSuccession(8, AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x9620));
					if (kindlingCauldrons.stream().anyMatch(e -> e.getTarget().isThePlayer())) {
						s.updateCall(mustardBombAvoidTethers);
					}
					else {
						s.updateCall(mustardBombGrabTethersAfter);
					}
				}
			});


	@NpcCastCallout(0x9602)
	private final ModifiableCallout<AbilityCastStart> aetherialConversionFireWE = ModifiableCallout.durationBasedCall("Aetherial Conversion Fire West->East", "Later: East Safe then West");
	@NpcCastCallout(0x9604)
	private final ModifiableCallout<AbilityCastStart> aetherialConversionFireEW = ModifiableCallout.durationBasedCall("Aetherial Conversion Fire East->West", "Later: West Safe then East");
	@NpcCastCallout(0x9603)
	private final ModifiableCallout<AbilityCastStart> aetherialConversionWaterWE = ModifiableCallout.durationBasedCall("Aetherial Conversion Water West->East", "Later: Knockback West then East");
	@NpcCastCallout(0x9605)
	private final ModifiableCallout<AbilityCastStart> aetherialConversionWaterEW = ModifiableCallout.durationBasedCall("Aetherial Conversion Water East->West", "Later: Knockback East then West");

	@NpcCastCallout(0x9606)
	private final ModifiableCallout<AbilityCastStart> tailThrustFireWE = ModifiableCallout.durationBasedCall("Tail Thrust: Fire West->East", "East Safe then West");
	@NpcCastCallout(0x9608)
	private final ModifiableCallout<AbilityCastStart> tailThrustFireEW = ModifiableCallout.durationBasedCall("Tail Thrust: Fire East->West", "West Safe then East");
	@NpcCastCallout(0x9607)
	private final ModifiableCallout<AbilityCastStart> tailThrustWaterWE = ModifiableCallout.durationBasedCall("Tail Thrust: Water West->East", "Knockback West then East");
	@NpcCastCallout(0x9609)
	private final ModifiableCallout<AbilityCastStart> tailThrustWaterEW = ModifiableCallout.durationBasedCall("Tail Thrust: Water East->West", "Knockback East then West");

	private final ModifiableCallout<AbilityCastStart> wickedFireInitial = ModifiableCallout.durationBasedCall("Twilight Sabbath: Bait Puddle", "Bait Middle");
	private final ModifiableCallout<?> wickedFireSafeSpot = new ModifiableCallout<>("Twilight Sabbath: First Safe Spot", "{safe} safe");
	private final ModifiableCallout<AbilityCastStart> wickedFireSafeSpotIn = ModifiableCallout.durationBasedCall("Twilight Sabbath: Second Safe Spot, In", "{safe} safe, In");
	private final ModifiableCallout<AbilityCastStart> wickedFireSafeSpotOut = ModifiableCallout.durationBasedCall("Twilight Sabbath: Second Safe Spot, Out", "{safe} safe, Out");

	@AutoFeed
	private final SequentialTrigger<BaseEvent> twilightSabbath = SqtTemplates.sq(60_000,
			// This is the "wicked fire" baited aoe. The original twilight sabbath cast is
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x9630),
			(e1, s) -> {
				log.info("Twilight Sabbath: Start");
				s.updateCall(wickedFireInitial, e1);
				for (int i = 0; i < 2; i++) {
					log.info("Twilight Sabbath: Loop {}", i);

					var fx = s.waitEvents(2, StatusLoopVfxApplied.class, e -> e.getTarget().npcIdMatches(17323));
					Set<ArenaSector> safeSpots = EnumSet.of(ArenaSector.NORTHWEST, ArenaSector.NORTHEAST, ArenaSector.SOUTHWEST, ArenaSector.SOUTHEAST);
					s.waitThenRefreshCombatants(50);
					fx.forEach(f -> {
						ArenaSector combatantLocation = finalAp.forCombatant(state.getLatestCombatantData(f.getTarget()));
						ArenaSector unsafe;
						// Cleaving right
						if (f.vfxIdMatches(793)) {
							// e.g. if add is S and cleaving right, E is unsafe
							unsafe = combatantLocation.plusQuads(-1);
						}
						// Cleaving left
						else if (f.vfxIdMatches(794)) {
							unsafe = combatantLocation.plusQuads(1);
						}
						else {
							log.error("Bad vfx id: {}", f.getStatusLoopVfx().getId());
							return;
						}
						log.info("Unsafe: {} -> {}", combatantLocation, unsafe);
						safeSpots.remove(unsafe.plusEighths(-1));
						safeSpots.remove(unsafe.plusEighths(1));
					});
					fx.stream()
							.map(StatusLoopVfxApplied::getTarget)
							.map(state::getLatestCombatantData)
							.map(finalAp::forCombatant)
							.forEach(safeSpots::remove);

					if (safeSpots.size() != 1) {
						log.error("Bad safeSpots spots! {}", safeSpots);
						continue;
					}
					ArenaSector safe = safeSpots.iterator().next();
					s.setParam("safe", safe);
					if (i == 0) {
						s.updateCall(wickedFireSafeSpot);
					}
					else {
						var wicked = s.findOrWaitForCast(casts, acs -> acs.abilityIdMatches(0x9610, 0x9612), false);
						if (wicked.abilityIdMatches(0x9610)) {
							s.updateCall(wickedFireSafeSpotOut, wicked);
						}
						else {
							s.updateCall(wickedFireSafeSpotIn, wicked);
						}
					}
				}


			});

	// concentrated burst
	// buddies into spread at 3:32PM

	private final ModifiableCallout<?> midnightSabbathSpreadCardinal = new ModifiableCallout<>("Midnight Sabbath: Spread in Cardinals", "Spread in Cardinals");
	private final ModifiableCallout<?> midnightSabbathSpreadIntercards = new ModifiableCallout<>("Midnight Sabbath: Spread in Intercards", "Spread in Intercards");
	private final ModifiableCallout<?> midnightSabbathBuddyCardinal = new ModifiableCallout<>("Midnight Sabbath: Buddy in Cardinals", "Buddy in Cardinals");
	private final ModifiableCallout<?> midnightSabbathBuddyIntercards = new ModifiableCallout<>("Midnight Sabbath: Buddy in Intercards", "Buddy in Intercards");

	private record MidnightSabbathMechanic(boolean isDonut, boolean isCardinal, boolean spread) {
		boolean cardinalSafe() {
			// If donut, then cast location is safe
			// if gun, then cast location is unsafe
			return isDonut == isCardinal;
		}
	}

	@AutoFeed
	private final SequentialTrigger<BaseEvent> midnightSabbath = SqtTemplates.sq(60_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x9AB9),
			(e1, s) -> {
				// This is the one with eight adds around the arena, and you have to dodge in/out with either partners or spread
				/*
				Midnight Sabbath 2: Clones will spawn with either wings or guns.
				If wings, go into the first active set ((all cardinals or all intercardinals first) on your quadrant.
				If guns, start on the inactive set.

				gun vs wing is determined by weapon ID
				gun = 7
				wing = 31
				gun fired = 6
				First vs second set appears to be ActorControlExtraEvent, where
				first set is 31 F4:0:0:0 and second set is 31 F2:0:0:0
				Weapon ID can also be read from ActorControlExtraEvent, it's 3F xx:0:0:0 where xx is the ID.

				Concentrated burst 962B is partners then spread
				Scattered burst 962C is spread then partners
				 */

//				Set<XivCombatant> donuts = new HashSet<>();
//				Set<XivCombatant> lasers = new HashSet<>();
//				Set<XivCombatant> firstSet = new HashSet<>(4);
//				Set<XivCombatant> secondSet = new HashSet<>(4);

				var mechanics = new MidnightSabbathMechanic[2];

				int weaponCategory = 0x3F;
				int orderCategory = 0x197;
				List<ActorControlExtraEvent> events = s.waitEventsQuickSuccession(16, ActorControlExtraEvent.class,
						acee -> acee.getTarget().npcIdMatches(17323)
						        && (acee.getCategory() == orderCategory || acee.getCategory() == weaponCategory));
				s.refreshCombatants();
				Map<XivCombatant, List<ActorControlExtraEvent>> eventsByCombatant = events.stream().collect(Collectors.groupingBy(ActorControlExtraEvent::getTarget));

				var buddySpread = s.waitEvent(AbilityCastStart.class, acs -> acs.abilityIdMatches(0x962B, 0x962C));

				boolean spreadFirst = (buddySpread.abilityIdMatches(0x962C));

				eventsByCombatant.forEach((cbt, cbtEvents) -> {
					ArenaSector location = finalAp.forCombatant(state.getLatestCombatantData(cbt));
					// Not usable
					if (!location.isOutside()) {
						return;
					}
					// Which set it is in
					Boolean firstSet = null;
					// Which mechanic
					Boolean isDonut = null;
					for (ActorControlExtraEvent event : cbtEvents) {
						if (event.getCategory() == orderCategory) {
							// First/second set
							switch ((int) event.getData0()) {
								case 0x11D3, 0x11D1 -> firstSet = true;
								case 0x11D4, 0x11D2 -> firstSet = false;
								default -> log.error("Unrecognized: {}", event.getPrimaryValue());
							}
						}
						else if (event.getCategory() == weaponCategory) {
							// Weapon ID
							switch ((int) event.getData0()) {
								case 7 -> isDonut = false;
								case 31 -> isDonut = true;
								default -> log.error("Unrecognized: {}", event.getPrimaryValue());
							}
						}
					}
					log.info("Midnight Sabbath: firstSet {}, isDonut {}, {}", firstSet, isDonut, cbt);
					if (firstSet != null && isDonut != null) {
						// Stack/spread is inverted on the second
						var out = new MidnightSabbathMechanic(isDonut, location.isCardinal(), firstSet == spreadFirst);
						int index = firstSet ? 0 : 1;
						MidnightSabbathMechanic existing = mechanics[index];
						if (existing == null) {
							mechanics[index] = out;
						}
						else {
							if (!existing.equals(out)) {
								log.warn("Mechanic disagreement at {}! new {} vs old {}", index, out, existing);
							}
						}
					}
				});

				MidnightSabbathMechanic firstMech = mechanics[0];
				if (firstMech != null) {
					log.info("Midnight Sabbath: firstMech {}", firstMech);
					// do the first call
					if (firstMech.cardinalSafe()) {
						s.updateCall(firstMech.spread ? midnightSabbathSpreadCardinal : midnightSabbathBuddyCardinal);
					}
					else {
						s.updateCall(firstMech.spread ? midnightSabbathSpreadIntercards : midnightSabbathBuddyIntercards);
					}
				}
				else {
					log.error("Midnight Sabbath: firstMech null!");
				}
				s.waitEvent(AbilityUsedEvent.class, aue -> aue.getPrecursor() == buddySpread);
				MidnightSabbathMechanic secondMech = mechanics[1];
				if (secondMech != null) {
					log.info("Midnight Sabbath: secondMech {}", secondMech);
					// do the second call
					if (secondMech.cardinalSafe()) {
						s.updateCall(secondMech.spread ? midnightSabbathSpreadCardinal : midnightSabbathBuddyCardinal);
					}
					else {
						s.updateCall(secondMech.spread ? midnightSabbathSpreadIntercards : midnightSabbathBuddyIntercards);
					}
				}
				else {
					log.error("Midnight Sabbath: secondMech null!");
				}

				Runnable wickedCall = delayedCallWickedSpecial(s);
				// Wait for other mechanic to resolve first before calling in/out
				s.waitEvent(AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x9627, 0x962D, 0x962E));
				wickedCall.run();
			});

	@NpcCastCallout(0x949B)
	private final ModifiableCallout<AbilityCastStart> wickedThunder = ModifiableCallout.durationBasedCall("Wicked Thunder", "Raidwide");

	private final ModifiableCallout<BuffApplied> ionCluster2shortPos = ModifiableCallout.<BuffApplied>durationBasedCall("Ion Cluster 2: Short Positron", "Short Positron").autoIcon();
	private final ModifiableCallout<BuffApplied> ionCluster2shortNeg = ModifiableCallout.<BuffApplied>durationBasedCall("Ion Cluster 2: Short Negatron", "Short Negatron").autoIcon();
	private final ModifiableCallout<BuffApplied> ionCluster2longPos = ModifiableCallout.<BuffApplied>durationBasedCall("Ion Cluster 2: Long Positron", "Long Positron").autoIcon();
	private final ModifiableCallout<BuffApplied> ionCluster2longNeg = ModifiableCallout.<BuffApplied>durationBasedCall("Ion Cluster 2: Long Negatron", "Long Negatron").autoIcon();

	private final ModifiableCallout<BuffApplied> ionCluster2baitFirstSet = ModifiableCallout.<BuffApplied>durationBasedCall("Ion Cluster 2: Bait First Set", "Bait {baitLocations}").autoIcon()
			.extendedDescription("These callouts sort the bait locations in clockwise order from north.");
	private final ModifiableCallout<?> ionCluster2avoidFirstSet = new ModifiableCallout<>("Ion Cluster 2: Take First Tower", "Soak {towers} Towers");
	private final ModifiableCallout<BuffApplied> ionCluster2baitSecondSet = ModifiableCallout.<BuffApplied>durationBasedCall("Ion Cluster 2: Bait Second Set", "Bait {baitLocations}").autoIcon();
	private final ModifiableCallout<?> ionCluster2avoidSecondSet = new ModifiableCallout<>("Ion Cluster 2: Take Second Tower", "Soak {towers} Tower");


	@NpcCastCallout(0x9614)
	private final ModifiableCallout<AbilityCastStart> flameSlash = ModifiableCallout.durationBasedCall("Flame Slash", "Out of Middle, Arena Splitting");

	@NpcCastCallout(value = 0x9617, suppressMs = 200)
	private final ModifiableCallout<AbilityCastStart> rainingSwordSoakTower = ModifiableCallout.durationBasedCall("Raining Swords: Soak Tower", "Soak Tower");

	private final ModifiableCallout<?> rainingSwordNorthmost = new ModifiableCallout<>("Raining Swords: Northmost Safe", "North");
	private final ModifiableCallout<?> rainingSwordNorthmiddle = new ModifiableCallout<>("Raining Swords: North-middle Safe", "North-Middle");
	private final ModifiableCallout<?> rainingSwordSouthmiddle = new ModifiableCallout<>("Raining Swords: South-middle Safe", "South-Middle");
	private final ModifiableCallout<?> rainingSwordSouthmost = new ModifiableCallout<>("Raining Swords: Southmost Safe", "South");
	private final ModifiableCallout<?> rainingSwordAll = new ModifiableCallout<>("Raining Swords: All", "{{remaining.collect { ['South', 'South-Middle', 'North-Middle', 'North'][it] } }}")
			.disabledByDefault()
			.extendedDescription("""
					This callout will tell you all remaining swords, numbered 0-3, as a list, where 0 is southmost. \
					You can also use {remainingOtherSide} to get the same info for side which the player is not on. \
					The default callout shows how to map these numbers to text.""");

	@SuppressWarnings("SerializableInnerClassWithNonSerializableOuterClass")
	private final class RainingSwordSafeSpotEvent extends BaseEvent implements HasPrimaryValue {
		@Serial
		private static final long serialVersionUID = -1177380546147020596L;
		final ArenaSector side;
		// Indexed from 0, i.e. 0 = southmost, 3 = northmost
		final int safeSpot;

		private RainingSwordSafeSpotEvent(ArenaSector side, int safeSpot) {
			this.side = side;
			this.safeSpot = safeSpot;
		}

		ModifiableCallout<?> getCallout() {
			return switch (safeSpot) {
				case 3 -> rainingSwordNorthmost;
				case 2 -> rainingSwordNorthmiddle;
				case 1 -> rainingSwordSouthmiddle;
				case 0 -> rainingSwordSouthmost;
				default -> throw new IllegalArgumentException("Bad index: " + safeSpot);
			};
		}

		@Override
		public String toString() {
			return "RainingSwordSafeSpotEvent{" +
			       "side=" + side +
			       ", safeSpot=" + safeSpot +
			       '}';
		}


		@Override
		public String getPrimaryValue() {
			return "%s %s".formatted(safeSpot, side);
		}
	}

	// This trigger is ONLY responsible for collecting - not any callouts!
	@AutoFeed
	private final SequentialTrigger<BaseEvent> rainingSwordsColl = SqtTemplates.sq(60_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x9616),
			(e1, s) -> {
				s.waitEvent(AbilityUsedEvent.class, aue -> aue.getPrecursor() == e1);
				// Swords should all be present at this point
				// Normally I would do this by position, but the sword IDs seem to have stable positions
				// Lowest ID is bottom left, then up, then over and up
				// There's the initial tethers (279) then the follow up (280).
				// There are 7 follow up sets, for 8 sets in total
				int npcId = 17327;
				// Find the 8 swords
				List<XivCombatant> swords = new ArrayList<>(state.npcsById(npcId));
				swords.sort(Comparator.comparing(XivCombatant::getId));
				if (swords.size() != 8) {
					throw new RuntimeException("Expected 8 swords, there were %s".formatted(swords.size()));
				}
				// Divide into left and right
				List<XivCombatant> leftSwords = swords.subList(0, 4);
				List<XivCombatant> rightSwords = swords.subList(4, 8);
				// Get the lowest ID for each side
				long leftBaseId = leftSwords.get(0).getId();
				long rightBaseId = rightSwords.get(0).getId();
				boolean startRight = false;
				for (int i = 0; i < 8; i++) {
					// These tethers all use the 'source' field as the sword that it is jumping TO
					var tethers = s.waitEvents(3, TetherEvent.class, te -> te.eitherTargetMatches(cbt -> cbt.npcIdMatches(npcId)));
					if (i == 0) {
						// If this is the first iteration, we need to determine whether we are left or right
						startRight = rightSwords.contains(tethers.get(0).getSource());
						log.info("Starting {}", startRight ? "right" : "left");
					}
					// Alternate sides
					boolean thisSideRight = startRight ^ (i % 2 != 0);
					long baseId = thisSideRight ? rightBaseId : leftBaseId;
					Set<Integer> safe = new HashSet<>(Set.of(0, 1, 2, 3));
					tethers.forEach(tether -> {
						int index = (int) (tether.getSource().getId() - baseId);
						log.info("Tether index: {}", index);
						safe.remove(index);
					});
					if (safe.size() != 1) {
						throw new RuntimeException("Safe: " + safe);
					}
					s.accept(new RainingSwordSafeSpotEvent(thisSideRight ? ArenaSector.EAST : ArenaSector.WEST, safe.iterator().next()));
				}
			});

	// This trigger does the actual callouts
	@AutoFeed
	private final SequentialTrigger<BaseEvent> rainingSwordsCall = SqtTemplates.sq(60_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x9616),
			(e1, s) -> {
				Queue<@NotNull Optional<ModifiableCallout<?>>> queue = new ArrayDeque<>();
				List<Integer> remaining = new ArrayList<>(4);
				List<Integer> remainingOtherSide = new ArrayList<>(4);
				s.setParam("remaining", remaining);
				s.setParam("remainingOtherSide", remainingOtherSide);
				// First collect everything
				for (int i = 0; i < 8; i++) {
					int wave = i / 2;
					var event = s.waitEvent(RainingSwordSafeSpotEvent.class);
					ArenaSector playerSide = state.getPlayer().getPos().x() > 100 ? ArenaSector.EAST : ArenaSector.WEST;
					boolean isMySide = playerSide == event.side;
					// The exception is that if this is the first wave, fire the callout immediately
					(isMySide ? remaining : remainingOtherSide).add(event.safeSpot);
					if (wave == 0) {
						if (isMySide) {
							s.updateCall(event.getCallout());
						}
						// Nothing to do
					}
					else {
						if (isMySide) {
							queue.add(Optional.of(event.getCallout()));
						}
						else {
							// If off-side, add null as a marker
							queue.add(Optional.empty());
						}
					}
				}
				s.call(rainingSwordAll);
				// Now burn through the queue, waiting for the chain lightning hits
				for (Optional<ModifiableCallout<?>> item : queue) {
					// Wait for another round of hits
					s.waitEventsQuickSuccession(3, AbilityUsedEvent.class, aue -> aue.abilityIdMatches(0x961A, 0x961B) && aue.isFirstTarget());
					// If not a null marker, fire the call
					item.ifPresent(s::updateCall);
					item.ifPresentOrElse(t -> remaining.remove(0), () -> remainingOtherSide.remove(0));
				}
			});

	/**
	 * Wait for a wicked special cast, and return a runnable that triggers the correct callout.
	 * <p>
	 * It returns a runnable rather than directly running the call so that it can be delayed.
	 *
	 * @param s The Sequential Trigger Controller to use
	 * @return The runnable as described above
	 */
	private Runnable delayedCallWickedSpecial(SequentialTriggerController<?> s) {
		var wicked = s.waitEvent(AbilityCastStart.class, acs -> acs.abilityIdMatches(0x9610, 0x9612));
		if (wicked.abilityIdMatches(0x9610)) {
			return () -> s.updateCall(wickedSpecialOutOfMiddle, wicked);
		}
		else {
			return () -> s.updateCall(wickedSpecialInMiddle, wicked);
		}
	}

	/**
	 * Wait for and call out a wicked thunder cast.
	 *
	 * @param s The sequential trigger controller
	 */
	private void callWickedThunder(SequentialTriggerController<?> s) {
		delayedCallWickedSpecial(s).run();
	}

	// Ion Cluster #2, aka Sunrise Sabbath
	@AutoFeed
	private final SequentialTrigger<BaseEvent> ionCluster2sq = SqtTemplates.sq(60_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x9622),
			(e1, s) -> {
				var playerBuff = s.waitEvent(BuffApplied.class, ba -> ba.getTarget().isThePlayer() && ba.buffIdMatches(positronBuff, negatronBuff));
				boolean playerPos = playerBuff.buffIdMatches(positronBuff);
				// 23 short, 38 long
				boolean playerLong = playerBuff.getInitialDuration().toSeconds() > 30;
				if (playerLong) {
					s.updateCall(playerPos ? ionCluster2longPos : ionCluster2longNeg, playerBuff);
				}
				else {
					s.updateCall(playerPos ? ionCluster2shortPos : ionCluster2shortNeg, playerBuff);
				}
				// Now, wait for special buff to be placed on the guns
				// Positron (FA0) needs to bait gun with B9A 757,
				// Negatron (FA1) needs to bait gun with B9A 756.
				int neededGun = playerPos ? 757 : 756;
				{
					// First round
					var gunBuffs = s.waitEventsQuickSuccession(4, BuffApplied.class, ba -> ba.buffIdMatches(0xB9A));
					if (playerLong) {
						s.waitThenRefreshCombatants(100);
						List<XivCombatant> towerNpcs = state.npcsById(17323)
								.stream()
								.filter(cbt -> cbt.getWeaponId() == 28)
								.toList();
						if (towerNpcs.size() != 2) {
							log.error("Tower npc fail! {}", towerNpcs);
						}
						else {
							// The logic here is to directly compute where the tower would end up based on the facing
							// angle of the mob casting it. Adding 21.21 makes it a near-perfect match for diagonal
							// jumps, and ends up with the correct answer for cardinal jumps even though they don't
							// actually swap positions in game.
							// Example E->S jump: (115, 165) -> (100, 180) for a distance of  21.21
							Set<ArenaSector> towerLocations = towerNpcs.stream()
									.map(npc -> {
										Position approxHitLocation = npc.getPos().translateRelative(0, 21.21);
										return finalAp.forPosition(approxHitLocation);
									}).collect(Collectors.toSet());
							// This is collected and compared as a set, but then manually written as a list so that it
							// always appears in a consistent order.
							if (towerLocations.equals(Set.of(ArenaSector.WEST, ArenaSector.EAST))) {
								s.setParam("towers", List.of(ArenaSector.WEST, ArenaSector.EAST));
							}
							else if (towerLocations.equals(Set.of(ArenaSector.NORTH, ArenaSector.SOUTH))) {
								s.setParam("towers", List.of(ArenaSector.NORTH, ArenaSector.SOUTH));
							}
							else {
								log.error("Bad tower locations! {}", towerLocations);
							}
						}
						// Even if we errored, this call is better than nothing
						s.updateCall(ionCluster2avoidFirstSet);

					}
					else {
						List<ArenaSector> acceptableGuns = gunBuffs.stream()
								.filter(ba -> ba.getRawStacks() == neededGun)
								.map(BuffApplied::getTarget)
								.map(finalAp::forCombatant)
								// The arena sectors are defined north -> CW, so using the ordinal is an acceptable
								// way to sort them in a north-first CW sort order.
								// TODO: add a note of this in the description
								.sorted(Comparator.comparing(Enum::ordinal))
								.toList();
						s.setParam("baitLocations", acceptableGuns);
						s.updateCall(ionCluster2baitFirstSet, playerBuff);
					}
				}
				callWickedThunder(s);
				{
					// First round
					var gunBuffs = s.waitEventsQuickSuccession(4, BuffApplied.class, ba -> ba.buffIdMatches(0xB9A));
					if (playerLong) {
						List<ArenaSector> acceptableGuns = gunBuffs.stream()
								.filter(ba -> ba.getRawStacks() == neededGun)
								.map(BuffApplied::getTarget)
								.map(finalAp::forCombatant)
								.sorted(Comparator.comparing(Enum::ordinal))
								.toList();
						s.setParam("baitLocations", acceptableGuns);
						s.updateCall(ionCluster2baitSecondSet, playerBuff);
					}
					else {
						s.waitThenRefreshCombatants(100);
						List<XivCombatant> towerNpcs = state.npcsById(17323)
								.stream()
								.filter(cbt -> cbt.getWeaponId() == 28)
								.toList();
						if (towerNpcs.size() != 2) {
							log.error("Tower npc fail 2! {}", towerNpcs);
						}
						else {
							// The logic here is to directly compute where the tower would end up based on the facing
							// angle of the mob casting it. Adding 21.21 makes it a near-perfect match for diagonal
							// jumps, and ends up with the correct answer for cardinal jumps even though they don't
							// actually swap positions in game.
							// Example E->S jump: (115, 165) -> (100, 180) for a distance of  21.21
							Set<ArenaSector> towerLocations = towerNpcs.stream()
									.map(npc -> {
										Position approxHitLocation = npc.getPos().translateRelative(0, 21.21);
										return finalAp.forPosition(approxHitLocation);
									}).collect(Collectors.toSet());
							if (towerLocations.equals(Set.of(ArenaSector.NORTH, ArenaSector.SOUTH))
							    || towerLocations.equals(Set.of(ArenaSector.WEST, ArenaSector.EAST))) {
								s.setParam("towers", towerLocations.stream().toList());
							}
							else {
								log.error("Bad tower locations 2! {}", towerLocations);
							}
						}
						// Even if we errored, this call is better than nothing
						s.updateCall(ionCluster2avoidSecondSet);
					}
				}
			});

	private final ModifiableCallout<AbilityCastStart> swordQuiverRaidwide = ModifiableCallout.durationBasedCall("Sword Quiver: Raidwides", "Raidwides");
	// Old versions that used the map effect
//	private final ModifiableCallout<MapEffectEvent> swordQuiverRearUnsafe = new ModifiableCallout<MapEffectEvent>("Sword Quiver: Rear Unsafe (Untested)", "Front/Middle");
//	private final ModifiableCallout<MapEffectEvent> swordQuiverMiddleUnsafe = new ModifiableCallout<MapEffectEvent>("Sword Quiver: Rear Unsafe (Untested)", "Front/Back");
//	private final ModifiableCallout<MapEffectEvent> swordQuiverFrontUnsafe = new ModifiableCallout<MapEffectEvent>("Sword Quiver: Rear Unsafe (Untested)", "Middle/Back");
	// 13.9 seconds after cast start. Cast is 4.7 seconds long.
	private static final Duration swordQuiverOffset = Duration.ofMillis(13_900 - 4_700);
	private final ModifiableCallout<AbilityCastStart> swordQuiverRearUnsafe = ModifiableCallout.durationBasedCallWithOffset("Sword Quiver: Rear Unsafe (Untested)", "Front/Middle", swordQuiverOffset);
	private final ModifiableCallout<AbilityCastStart> swordQuiverMiddleUnsafe = ModifiableCallout.durationBasedCallWithOffset("Sword Quiver: Rear Unsafe (Untested)", "Front/Back", swordQuiverOffset);
	private final ModifiableCallout<AbilityCastStart> swordQuiverFrontUnsafe = ModifiableCallout.durationBasedCallWithOffset("Sword Quiver: Rear Unsafe (Untested)", "Middle/Back", swordQuiverOffset);

	@AutoFeed
	private final SequentialTrigger<BaseEvent> swordQuiver = SqtTemplates.sq(60_000,
			AbilityCastStart.class, acs -> acs.abilityIdMatches(0x95F9, 0x95FA, 0x95FB),
			(e1, s) -> {
				s.updateCall(swordQuiverRaidwide, e1);
				// Rear unsafe:
				// 95FB from boss
				// Then 95FF, 95FE, 95FD, 95FE in descending cast times to hit the players
				// So if anything, 95FF is the one we care about
				// Map effects between waves:
				// 800375BE:20001:16:0:0
				// 800375BE:20001:15:0:0
				// 800375BE:20001:14:0:0
				// 800375BE:20001:19:0:0
				// Guessing that either the 95FF is a different cast (like 96A0 and 96A1),
				// or that the map effects would be 17 and 18
				// Front unsafe:
				// 95F9 from boss
				// 800375BE:20001:16:0:0
				// 800375BE:20001:15:0:0
				// 800375BE:20001:14:0:0
				// 800375BE:20001:17:0:0
				// Middle unsafe:
				// 95FA from boss
				// 800375BE:20001:16:0:0
				// 800375BE:20001:15:0:0
				// 800375BE:20001:14:0:0
				// 800375BE:20001:18:0:0

//				var mee = s.waitEvent(MapEffectEvent.class, e -> {
//					long index = e.getIndex();
//					return e.getFlags() == 0x20001
//					       && index == 0x17 || index == 0x18 || index == 0x19;
//				});
//				s.updateCall(switch ((int) mee.getIndex()) {
//					case 0x17 -> swordQuiverFrontUnsafe;
//					case 0x18 -> swordQuiverMiddleUnsafe;
//					case 0x19 -> swordQuiverRearUnsafe;
//					default -> throw new RuntimeException("Bad index " + mee.getIndex());
//				}, mee);
				s.waitMs(5_000);
				switch (((int) e1.getAbility().getId())) {
					case 0x95F9 -> s.updateCall(swordQuiverFrontUnsafe, e1);
					case 0x95FA -> s.updateCall(swordQuiverMiddleUnsafe, e1);
					case 0x95FB -> s.updateCall(swordQuiverRearUnsafe, e1);
				}
			}).setConcurrency(SequentialTriggerConcurrencyMode.CONCURRENT);

	@NpcCastCallout(0x9632)
	private final ModifiableCallout<AbilityCastStart> enrage = ModifiableCallout.durationBasedCall("Enrage", "Enrage");
}