package gg.xp.xivsupport.callouts.gui;

import gg.xp.reevent.events.EventMaster;
import gg.xp.reevent.scan.ScanMe;
import gg.xp.xivdata.data.duties.*;
import gg.xp.xivsupport.callouts.CalloutDefaults;
import gg.xp.xivsupport.callouts.CalloutDefaultsRepository;
import gg.xp.xivsupport.callouts.CalloutGroup;
import gg.xp.xivsupport.callouts.ModifiedCalloutRepository;
import gg.xp.xivsupport.callouts.audio.SoundFilesManager;
import gg.xp.xivsupport.callouts.audio.gui.SoundFileTab;
import gg.xp.xivsupport.callouts.conversions.DutySpecificArenaSectorConverter;
import gg.xp.xivsupport.callouts.conversions.GlobalArenaSectorConverter;
import gg.xp.xivsupport.events.debug.DebugCommand;
import gg.xp.xivsupport.gui.TitleBorderFullsizePanel;
import gg.xp.xivsupport.gui.WrapLayout;
import gg.xp.xivsupport.gui.extra.DutyPluginTab;
import gg.xp.xivsupport.gui.extra.PluginTab;
import gg.xp.xivsupport.gui.extra.TabDef;
import gg.xp.xivsupport.gui.tabs.FixedWidthVerticalTabPane;
import gg.xp.xivsupport.gui.nav.GlobalUiRegistry;
import gg.xp.xivsupport.gui.tabs.SmartTabbedPane;
import gg.xp.xivsupport.gui.util.GuiUtil;
import gg.xp.xivsupport.persistence.gui.BooleanSettingGui;
import org.picocontainer.PicoContainer;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ScanMe
public class DutiesTab implements PluginTab {

	private final ModifiedCalloutRepository backend;
	private final PicoContainer container;
	private final SoundFilesManager soundMgr;
	private final EventMaster master;
	private final GlobalArenaSectorConverter asc;
	private final SoundFileTab sft;
	private final GlobalUiRegistry reg;
	private final CalloutDefaultsRepository defaultsRepo;
	private SmartTabbedPane tabPane;

	public DutiesTab(ModifiedCalloutRepository backend,
	                 PicoContainer container,
	                 SoundFilesManager soundMgr,
	                 EventMaster master,
	                 GlobalArenaSectorConverter asc,
	                 SoundFileTab sft,
	                 GlobalUiRegistry reg,
	                 CalloutDefaultsRepository defaultsRepo

	) {
		this.backend = backend;
		this.container = container;
		this.soundMgr = soundMgr;
		this.master = master;
		this.asc = asc;
		this.sft = sft;
		this.reg = reg;
		this.defaultsRepo = defaultsRepo;
	}

	@Override
	public String getTabName() {
		return "Duties";
	}


	private static final class DutyTabContents {
		final Duty duty;
		final List<CalloutGroup> calls = new ArrayList<>();
		final List<DutyPluginTab> extraTabs = new ArrayList<>();

		private DutyTabContents(Duty duty) {
			this.duty = duty;
		}
	}

	private final Set<KnownDuty> alreadyHasAscTab = EnumSet.noneOf(KnownDuty.class);

	private void activateItem(Object item) {
		if (item instanceof Duty duty) {
			GuiUtil.invokeLaterSequentially(
					() -> activateExpac(duty.getExpac()),
					() -> activateDutyType(duty.getType()),
					() -> activateDuty(duty)
			);
		}
		else if (item instanceof DutyPluginTab dpt) {
			KnownDuty duty = dpt.getDuty();
			GuiUtil.invokeLaterSequentially(
					() -> activateExpac(duty.getExpac()),
					() -> activateDutyType(duty.getType()),
					() -> activateDuty(duty),
					() -> activatePluginTab(dpt)
			);
		}
	}

	private void activateExpac(Expansion expac) {
		tabPane.selectTabByKey(expac);
	}

	private void activateDutyType(DutyType type) {
		Component comp = tabPane.getSelectedComponent();
		if (comp instanceof SmartTabbedPane pane) {
			pane.selectTabByKey(type);
		}
	}

	private void activateDuty(Duty duty) {
		Component comp = tabPane.getSelectedComponent();
		if (comp instanceof SmartTabbedPane pane) {
			Component comp2 = pane.getSelectedComponent();
			if (comp2 instanceof SmartTabbedPane p2) {
				p2.selectTabByKey(duty);
			}
		}
	}

	private void activatePluginTab(DutyPluginTab dpt) {
		Component comp = tabPane.getSelectedComponent();
		if (comp instanceof SmartTabbedPane pane) {
			Component comp2 = pane.getSelectedComponent();
			if (comp2 instanceof SmartTabbedPane p2) {
				Component comp3 = p2.getSelectedComponent();
				if (comp3 instanceof SmartTabbedPane p3) {
					p3.selectTabByKey(dpt);
				}
			}
		}
	}

	private void activateGeneralTab() {
		tabPane.selectTabByKey("General");
	}

	@Override
	public Component getTabContents() {
		this.tabPane = new SmartTabbedPane(JTabbedPane.LEFT);
		Map<Expansion, Map<DutyType, Map<Duty, DutyTabContents>>> contents = new EnumMap<>(Expansion.class);
		DutyTabContents nonSpecific = new DutyTabContents(KnownDuty.None);
		List<CalloutGroup> allCallouts = new ArrayList<>(backend.getAllCallouts());
		allCallouts.sort(Comparator.comparing(CalloutGroup::getDuty));
		Set<Duty> duties = new HashSet<>();
		allCallouts.forEach(group -> {
			KnownDuty duty = group.getDuty();
			if (duty == KnownDuty.None) {
				nonSpecific.calls.add(group);
				return;
			}
			duties.add(duty);
			DutyTabContents tabContents = contents.computeIfAbsent(duty.getExpac(), d -> new LinkedHashMap<>())
					.computeIfAbsent(duty.getType(), d -> new LinkedHashMap<>())
					.computeIfAbsent(duty, DutyTabContents::new);
			tabContents.calls.add(group);
			if (!alreadyHasAscTab.contains(duty)) {
				DutySpecificArenaSectorConverter dsc = asc.getDutySpecificConverter(duty);
				if (dsc != null) {
					tabContents.extraTabs.add(new DutyPluginTab() {
						@Override
						public String getTabName() {
							return "Arena Positions";
						}

						@Override
						public Component getTabContents() {
							return new ArenaSectorConverterGui(dsc);
						}

						@Override
						public KnownDuty getDuty() {
							return duty;
						}
					});
				}
				alreadyHasAscTab.add(duty);
			}
		});
		for (Duty duty : duties) {
			reg.registerItem(duty, "Duty: " + duty.getName(), List.of(duty.getName()), () -> activateItem(duty), DutiesTab.class);
		}

		container.getComponents(DutyPluginTab.class).forEach(tab -> {
			KnownDuty duty = tab.getDuty();
			DutyTabContents tabContents = contents.computeIfAbsent(duty.getExpac(), d -> new LinkedHashMap<>())
					.computeIfAbsent(duty.getType(), d -> new LinkedHashMap<>())
					.computeIfAbsent(duty, DutyTabContents::new);
			tabContents.extraTabs.add(tab);
			reg.registerItem(tab, "Duty Plugin: " + duty.getName() + " -> " + tab.getTabName(), List.of(tab.getTabName()), () -> this.activateItem(tab), DutiesTab.class, duty);

		});


		nonSpecific.extraTabs.add(new DutyPluginTab() {
			@Override
			public String getTabName() {
				return "Test Callouts";
			}

			@Override
			public Component getTabContents() {
				TitleBorderFullsizePanel outer = new TitleBorderFullsizePanel("Test Callouts");
				outer.setLayout(new BorderLayout());
				JButton testButton = new JButton("Test");
				testButton.addActionListener(l -> {
					// TODO: not a good way of doing this
					master.pushEvent(new DebugCommand("testcall"));
				});
				JPanel panel = new JPanel();
				panel.add(testButton);
				outer.add(panel, BorderLayout.NORTH);
				return outer;
			}

			@Override
			public KnownDuty getDuty() {
				return null;
			}
		});

		tabPane.add("General", makeDutyComponent(nonSpecific));

		contents.forEach((expac, types) -> {
			tabPane.addTabLazy(new TabDef() {
				@Override
				public String getTabName() {
					return expac.getName();
				}

				@Override
				public List<Object> keys() {
					return List.of(expac);
				}

				@Override
				public Component getTabContents() {
					SmartTabbedPane expacTab = new FixedWidthVerticalTabPane(80);
					types.forEach((type, duties) -> {
						expacTab.addTabLazy(new TabDef() {
							@Override
							public String getTabName() {
								return type.getName();
							}

							@Override
							public List<Object> keys() {
								return List.of(type);
							}

							@Override
							public Component getTabContents() {
								SmartTabbedPane typeTab = new FixedWidthVerticalTabPane(100);
								duties.forEach((duty, dutyContent) -> {
									typeTab.addTab(new TabDef() {
										@Override
										public String getTabName() {
											return duty.getName();
										}

										@Override
										public List<Object> keys() {
											return Collections.singletonList(duty);
										}

										@Override
										public Component getTabContents() {
											return makeDutyComponent(dutyContent);
										}
									});
								});
								return typeTab;
							}
						});
					});
					return expacTab;
				}
			});
		});

		return tabPane;
	}

	private Component makeDutyComponent(DutyTabContents dutyContent) {
		SmartTabbedPane tabPane = new SmartTabbedPane(JTabbedPane.TOP);

		{
			JPanel panel = new JPanel(new BorderLayout());
			CalloutHelper ch = new CalloutHelper(dutyContent.calls, soundMgr, sft);
			JScrollPane scroller = new JScrollPane(ch, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
			panel.add(scroller, BorderLayout.CENTER);

			JPanel settingsPanel = new JPanel();
			settingsPanel.setBorder(new TitledBorder("Global Settings (All Duties)"));
			settingsPanel.setLayout(new WrapLayout());

			CalloutDefaults globalDefaults = defaultsRepo.getGlobalDefaults();
			JCheckBox defaultEnable = new BooleanSettingGui(globalDefaults.getEnableCallout(), "Enable By Default", true).getComponent();
			settingsPanel.add(defaultEnable);
			JCheckBox defaultTts = new BooleanSettingGui(globalDefaults.getEnableTts(), "Enable TTS By Default", true).getComponent();
			settingsPanel.add(defaultTts);
			JCheckBox defaultText = new BooleanSettingGui(globalDefaults.getEnableText(), "Enable Text By Default", true).getComponent();
			settingsPanel.add(defaultText);
			JCheckBox enableTts = new BooleanSettingGui(backend.getEnableTts(), "Allow TTS").getComponent();
			settingsPanel.add(enableTts);
			JCheckBox enableOverlay = new BooleanSettingGui(backend.getEnableOverlay(), "Enable Overlay").getComponent();
			settingsPanel.add(enableOverlay);

			panel.add(settingsPanel, BorderLayout.NORTH);

			tabPane.add("Callouts", panel);
		}

		{
			for (DutyPluginTab extraTab : dutyContent.extraTabs) {
				tabPane.addTab(extraTab);
			}
		}

		return tabPane;
	}

	@Override
	public int getSortOrder() {
		return 0;
	}

	@Override
	public boolean asyncOk() {
		return false;
	}
}
