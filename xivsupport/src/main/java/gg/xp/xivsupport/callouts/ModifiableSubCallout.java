package gg.xp.xivsupport.callouts;

public class ModifiableSubCallout {

	private final String settingKey;
	private final String label;
	private final Object object;
	private final String defaultScript;

	public ModifiableSubCallout(String settingKey, String label, Object object, String defaultScript) {
		this.settingKey = settingKey;
		this.label = label;
		this.object = object;
		this.defaultScript = defaultScript;
	}

	public String getSettingKey() {
		return settingKey;
	}

	public String getLabel() {
		return label;
	}

	public Object getObject() {
		return object;
	}

	public String getDefaultScript() {
		return defaultScript;
	}
}
