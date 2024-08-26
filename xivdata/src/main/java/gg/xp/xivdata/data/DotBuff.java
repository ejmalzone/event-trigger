package gg.xp.xivdata.data;

import java.util.Set;

import static gg.xp.xivdata.data.Job.*;

public enum DotBuff {
	// List of ALL buffs to track - WL/BL will be done by user settings
	// JLS/javac being dumb, had to put the L there to make it a long
	AST_Combust(AST, "Combust/II/III", 0x346L, 0x34bL, 0x759L),
	BLM_Thunder(BLM, "Thunder/High Thunder", 0xa1L, 0xa2L, 0xa3L, 0x4baL, 0xf1fL, 0xf20L),
	BLU_Bleeding(BLU, "Bleeding", 0x6b2L),
	BRD_CombinedDots(BRD, "Bard DoTs", 0x4b0L, 0x4b1L, 0x7cL, 0x81L),
	DRG_ChaosThrust(DRG, "Chaos Thrust/Chaotic Spring", 0x76L, 0xa9FL),
	MNK_Demolish(MNK, "Demolish", 0xf6L),
	MNK_Twinsnakes(MNK, "Twin Snakes", 0xbb9L),
	NIN_ShadowFang(NIN, "Shadow Fang", 0x1fcL),
	// TODO: These are mutually exclusive, tracker doesn't handle that all too well at the moment
	// It looks awkward due to preapp, and also if your SkS is too low, the buff expires, and the
	// expired one stays in red status for a bit.
	PLD_GoringBlade(PLD, "Goring/Valor", 0x2d5L, 0xAA1L),
	WAR_StormsEye(WAR, "Storm's Eye", 0x5AL, 0xa75L),
	SAM_Higanbana(SAM, "Higanbana", 0x4ccL),
	SAM_Fugetsu(SAM, "Fugetsu", 0x512L),
	SAM_Fuka(SAM, "Fuka", 0x513L),
	SCH_Bio(SCH, "Bio/II/Biolysis", 0xb3L, 0xbdL, 0x767L),
	//	SMN_Bio(SMN, "Bio/II/III", 0xb3L, 0xbdL, 0x4beL),
//	SMN_Miasma(SMN, "Miasma/II/III", 0xb4L, 0xbcL, 0x4bfL),
	WHM_Aero(WHM, "Aero/II/III/Dia", 0x8fL, 0x90L, 0x31eL, 0x74fL),
	SGE_Dosis(SGE, "Dosis", 2614L, 2615L, 2616L, 3897L),
	RPR_Death(RPR, "Death's Design", 2586L),
	// Using ADV instead of null is kind of a hack but works
	ASS_DotAction(ADV, "Spirit Dart", 0xD1FL);

	private final Job job;
	private final String label;
	private final Set<Long> buffIds;

	DotBuff(Job job, String label, Long... buffIds) {
		this.job = job;
		this.label = label;
		this.buffIds = Set.of(buffIds);
	}

	public Job getJob() {
		return job;
	}

	public String getLabel() {
		return label;
	}


	public boolean matches(long id) {
		return buffIds.contains(id);
	}
}
