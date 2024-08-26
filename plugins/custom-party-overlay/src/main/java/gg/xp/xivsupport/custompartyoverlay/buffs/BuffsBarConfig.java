package gg.xp.xivsupport.custompartyoverlay.buffs;

import gg.xp.xivsupport.persistence.PersistenceProvider;
import gg.xp.xivsupport.persistence.settings.BooleanSetting;
import gg.xp.xivsupport.persistence.settings.ColorSetting;
import gg.xp.xivsupport.persistence.settings.IntSetting;
import gg.xp.xivsupport.persistence.settings.ObservableSetting;
import gg.xp.xivsupport.persistence.settings.WeakRunnable;

import java.util.List;

public class BuffsBarConfig extends ObservableSetting {

	private final BooleanSetting timers;

	private final ColorSetting normalTextColor;
	private final ColorSetting myBuffTextColor;
	private final ColorSetting removeableBuffColor;

	private final BooleanSetting shadows;
	private final BooleanSetting rtl;
	private final BooleanSetting showPreapps;
	private final IntSetting preappOpacity;

	private final IntSetting xPadding;

	protected final String settingKeyBase;

	protected BuffsBarConfig(PersistenceProvider pers, String settingKeyBase) {
		this.settingKeyBase = settingKeyBase;
		normalTextColor = new ColorSetting(pers, settingKeyBase + "normal-color", BuffsBar.defaultTextColor);
		myBuffTextColor = new ColorSetting(pers, settingKeyBase + "mybuff-color", BuffsBar.defaultMyBuffColor);
		removeableBuffColor = new ColorSetting(pers, settingKeyBase + "removeablebuff-color", BuffsBar.defaultRemovableBuffColor);
		timers = new BooleanSetting(pers, settingKeyBase + "show-timers", true);
		shadows = new BooleanSetting(pers, settingKeyBase + "text-shadows", true);
		showPreapps = new BooleanSetting(pers, settingKeyBase + "show-preapps", false);
		rtl = new BooleanSetting(pers, settingKeyBase + "rtl", false);
		xPadding = new IntSetting(pers, settingKeyBase + "xpad", 0, -20, 1000);
		preappOpacity = new IntSetting(pers, settingKeyBase + "preapp-opacity", 50, 0, 100);
		List.of(normalTextColor, myBuffTextColor, removeableBuffColor, timers, shadows, xPadding, preappOpacity)
				.forEach(this::registerListener);
	}

	protected final void registerListener(ObservableSetting setting) {
		setting.addListener(WeakRunnable.of(this, BuffsBarConfig::notifyListeners));
	}

	public BooleanSetting getTimers() {
		return timers;
	}

	public ColorSetting getNormalTextColor() {
		return normalTextColor;
	}

	public ColorSetting getMyBuffTextColor() {
		return myBuffTextColor;
	}

	public ColorSetting getRemoveableBuffColor() {
		return removeableBuffColor;
	}

	public BooleanSetting getShadows() {
		return shadows;
	}

	public IntSetting getxPadding() {
		return xPadding;
	}

	public BooleanSetting getRtl() {
		return rtl;
	}

	public BooleanSetting getShowPreapps() {
		return showPreapps;
	}

	public IntSetting getPreappOpacity() {
		return preappOpacity;
	}
}
