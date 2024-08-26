package gg.xp.xivsupport.gui.tables;

import gg.xp.xivsupport.gui.GuiGlobals;
import gg.xp.xivsupport.gui.NoCellEditor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableColumn;
import java.awt.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class CustomTableModel<X> extends AbstractTableModel {

	private static final Logger log = LoggerFactory.getLogger(CustomTableModel.class);
	private final ExecutorService exs = Executors.newSingleThreadExecutor();

	public static class CustomTableModelBuilder<B> {
		private final Supplier<List<? extends B>> dataGetter;
		private final List<CustomColumn<? super B>> columns = new ArrayList<>();
		private BiPredicate<? super B, ? super B> selectionEquivalence = Objects::equals;

		private CustomTableModelBuilder(Supplier<List<? extends B>> dataGetter) {
			this.dataGetter = dataGetter;
		}

		public CustomTableModelBuilder<B> addColumn(CustomColumn<? super B> colDef) {
			columns.add(colDef);
			return this;
		}

		public CustomTableModelBuilder<B> apply(Consumer<? super CustomTableModelBuilder<B>> func) {
			func.accept(this);
			return this;
		}

		public CustomTableModelBuilder<B> transform(Function<? super CustomTableModelBuilder<B>, CustomTableModelBuilder<B>> func) {
			return func.apply(this);
		}

		public CustomTableModel<B> build() {
			return new CustomTableModel<B>(dataGetter, columns, selectionEquivalence);
		}

		public CustomTableModelBuilder<B> setItemEquivalence(BiPredicate<? super B, ? super B> selectionEquivalence) {
			this.selectionEquivalence = selectionEquivalence;
			return this;
		}
	}

	public static <B> CustomTableModelBuilder<B> builder(Supplier<List<? extends B>> dataGetter) {
		return new CustomTableModelBuilder<>(dataGetter);
	}


	private final Supplier<List<? extends X>> dataGetter;
	private final List<CustomColumn<? super X>> columns;
	private List<X> data = Collections.emptyList();
	private volatile List<X> newData = data;
	private final BiPredicate<? super X, ? super X> selectionEquivalence;


	private CustomTableModel(Supplier<List<? extends X>> dataGetter, List<CustomColumn<? super X>> columns, BiPredicate<? super X, ? super X> selectionEquivalence) {
		this.dataGetter = dataGetter;
		this.columns = columns;
		this.selectionEquivalence = selectionEquivalence;
		fullRefresh();
	}

	public void appendOnlyRefresh() {
		updateDataOnly();
		SwingUtilities.invokeLater(this::processNewDataAppend);
	}

	private void processNewDataAppend() {
		JTable table = getTable();
		if (table == null) {
			data = newData;
			// This shouldn't really happen anyway, no need to optimize
			fireTableDataChanged();
		}
		else {
			if (data.isEmpty()) {
				// Fast path for when data is currently empty
				fireTableDataChanged();
				return;
			}
			ListSelectionModel selectionModel = table.getSelectionModel();
			int oldSize = data.size();
			int[] oldSelectionIndices = selectionModel.getSelectedIndices();
			List<X> oldSelections;
			try {
				oldSelections = Arrays.stream(oldSelectionIndices)
						.mapToObj(i -> data.get(i))
						.collect(Collectors.toList());
			} catch (Throwable t) {
				log.error("Error re-applying selections, clearing selections.", t);
				oldSelections = Collections.emptyList();
			}
			// TODO: smarter data provider that informs us of append-only operations
			data = newData;
			int newSize = data.size();
			fireTableRowsInserted(oldSize, newSize - 1);
			// fast path for typical case where data is only appended and we only have a single selection
			if (oldSelections.size() == 1) {
				X theItem = oldSelections.get(0);
				int theIndex = oldSelectionIndices[0];
				if (theIndex >= data.size()) {
					return;
				}
				if (data.get(theIndex) == theItem) {
					selectionModel.addSelectionInterval(theIndex, theIndex);
					return;
				}
			}
			for (X oldItem : oldSelections) {
				for (int i = 0; i < data.size(); i++) {
					X newItem = data.get(i);
					if (selectionEquivalence.test(oldItem, newItem)) {
						selectionModel.addSelectionInterval(i, i);
						break;
					}
				}
			}
		}

	}

	public void configureColumns(JTable table) {
		for (int i = 0; i < columns.size(); i++) {
			TableColumn column = table.getColumnModel().getColumn(i);
			CustomColumn<? super X> customColumn = columns.get(i);
			customColumn.configureColumn(column);
		}
	}

	//	public void refreshItem(X instance) {
//		JTable table = getTable();
//		if (table == null) {
//			data = dataGetter.get();
//			fireTableDataChanged();
//		}
//		else {
//			int oldIndex = data.indexOf(instance);
//			ListSelectionModel selectionModel = table.getSelectionModel();
//			int[] oldSelectionIndices = selectionModel.getSelectedIndices();
//			List<X> oldSelections = Arrays.stream(oldSelectionIndices)
//					.mapToObj(i -> data.get(i))
//					.collect(Collectors.toList());
//			// TODO: smarter data provider that informs us of append-only operations
//			{
//				long timeBefore = System.currentTimeMillis();
//				data = dataGetter.get();
//				long timeAfter = System.currentTimeMillis();
//				long delta = timeAfter - timeBefore;
//				// TODO find good value for this - 100 might be a little low
//				if (delta > 50) {
//					log.warn("Slow Data Getter performance: took {}ms to refresh", delta);
//				}
//			}
//			int newIndex = data.indexOf(instance);
//			// TODO: more optimizations could be done in XivState to only report changed combatants
//			if (oldIndex == newIndex && oldIndex >= 0) {
//				fireTableRowsUpdated(oldIndex, newIndex);
//			}
//			else {
//				{
//					long timeBefore = System.currentTimeMillis();
//					fireTableDataChanged();
//					long timeAfter = System.currentTimeMillis();
//					long delta = timeAfter - timeBefore;
//					// TODO find good value for this - 100 might be a little low
//					if (delta > 50) {
//						log.warn("Slow Table performance: took {}ms to refresh", delta);
//					}
//				}
//				for (X oldItem : oldSelections) {
//					for (int i = 0; i < data.size(); i++) {
//						X newItem = data.get(i);
//						if (selectionEquivalence.test(oldItem, newItem)) {
//							selectionModel.addSelectionInterval(i, i);
//							break;
//						}
//					}
//				}
//			}
//		}
//	}
	private final AtomicBoolean pendingRefresh = new AtomicBoolean();

	public void signalNewData() {
		// This setup allows for there to be exactly one refresh in progress, and one pending after that
		boolean skipRefresh = pendingRefresh.compareAndExchange(false, true);
		if (!skipRefresh) {
			exs.submit(() -> {
				fullRefresh();
				try {
					// Cap updates to 1000/x fps, while not delaying updates
					// if they come in less frequently than that
					Thread.sleep(GuiGlobals.REFRESH_MIN_DELAY);
//						Thread.sleep(50);
				}
				catch (InterruptedException e) {
					// ignored
				}
			});
		}
	}

	@SuppressWarnings("unchecked") // Safe since we are only reading the list
	private void updateDataOnly() {
		pendingRefresh.set(false);
		newData = (List<X>) dataGetter.get();
	}

	public void fullRefresh() {
		updateDataOnly();
		SwingUtilities.invokeLater(this::processNewDataFull);
	}

	public void fullRefreshSync() {
		updateDataOnly();
		processNewDataFull();
	}

	public List<X> getData() {
		return Collections.unmodifiableList(data);
	}

	// Experimenting, don't use
	public void overlayHackRefresh() {
		updateDataOnly();
		pendingRefresh.set(false);
		data = newData;
	}

	private void processNewDataFull() {
		JTable table = getTable();
		if (table == null) {
			data = newData;
			fireTableDataChanged();
		}
		else {
			if (data.isEmpty()) {
				// Fast path for when data is currently empty
				data = newData;
				fireTableDataChanged();
				return;
			}
			ListSelectionModel selectionModel = table.getSelectionModel();
			int[] oldSelectionIndices = selectionModel.getSelectedIndices();
			List<X> oldSelections = Arrays.stream(oldSelectionIndices)
					.mapToObj(i -> data.get(i))
					.collect(Collectors.toList());
			// TODO: smarter data provider that informs us of append-only operations
			data = newData;
			fireTableDataChanged();
			for (X oldItem : oldSelections) {
				for (int i = 0; i < data.size(); i++) {
					X newItem = data.get(i);
					if (selectionEquivalence.test(oldItem, newItem)) {
						selectionModel.addSelectionInterval(i, i);
						break;
					}
				}
			}
		}
	}

	public @Nullable Integer getSelectedItemViewportOffsetIfVisible() {
		JTable table = getTable();
		// Determine if row is visible
		int row = table.getSelectedRow();
		if (row >= 0) {
			Rectangle cellRect = table.getCellRect(row, 0, true);
			Rectangle tableRect = table.getVisibleRect();
			if (tableRect.contains(cellRect)) {
				return cellRect.y - tableRect.y;
			}
			else {
				return null;
			}
		}
		return null;
	}

	public void setVisibleItemScrollOffset(int offset) {
		SwingUtilities.invokeLater(() -> {
			JTable table = getTable();
			// Determine if row is visible
			int row = table.getSelectedRow();
			if (row >= 0) {
				Rectangle cellRect = table.getCellRect(row, 0, true);
				Rectangle tableRect = table.getVisibleRect();
				int newY = cellRect.y - offset;
				Rectangle lastCell = table.getCellRect(table.getRowCount() - 1, 0, true);
				int maxLowerBound = lastCell.y + lastCell.height;
				Rectangle rectangle = new Rectangle(cellRect.x, newY, cellRect.width, tableRect.height);
				int overHeight = rectangle.y + rectangle.height - maxLowerBound;
				// For some reason, we have to scroll to 0 first
				table.scrollRectToVisible(new Rectangle(0, 0, 0, 0));
				if (overHeight > 0) {
					table.scrollRectToVisible(cellRect);
				}
				else {
					table.scrollRectToVisible(rectangle);
				}
				log.info("Done scrolling, offset {}", offset);
//				if (overHeight > 0) {
//					rectangle.y -= overHeight;
//				}
//				table.scrollRectToVisible(rectangle);
			}
		});
		log.info("Done scrolling, offset {}", offset);

	}

	@Override
	public int getRowCount() {
		return data.size();
	}

	// TODO: this is technically wrong since multiple tables can have the same model instance
	private JTable getTable() {
		return (JTable) Arrays.stream(getTableModelListeners()).filter(JTable.class::isInstance).findFirst().orElse(null);
	}

//	@Override
//	public void fireTableDataChanged() {
//		JTable table = getTable();
//		if (table == null) {
//			log.warn("Did not find an associated table");
//			super.fireTableDataChanged();
//		}
//		else {
//			Arrays.stream(table.getSelectionModel().getSelectedIndices())
//
//			super.fireTableDataChanged();
//
//		}
//	}

	@Override
	public int getColumnCount() {
		return columns.size();
	}

	@Override
	public String getColumnName(int column) {
		return columns.get(column).getColumnName();
	}

	@Override
	public boolean isCellEditable(int rowIndex, int columnIndex) {
		return false;
	}

	public X getValueForRow(int row) {
		return data.get(row);
	}

	public @Nullable X getSelectedValue() {
		JTable table = getTable();
		if (table != null) {
			int selectedRow = table.getSelectedRow();
			if (selectedRow >= 0) {
				return data.get(selectedRow);
			}
		}
		return null;
	}

	public List<X> getSelectedValues() {
		JTable table = getTable();
		if (table != null) {
			return Arrays.stream(table.getSelectedRows())
					.mapToObj(data::get)
					.toList();
		}
		return Collections.emptyList();
	}

	public void setSelectedValue(@Nullable X value) {
		JTable table = getTable();
		if (value == null) {
			table.clearSelection();
		}
		else {
			int index = data.indexOf(value);
			if (index >= 0) {
				table.setRowSelectionInterval(index, index);
			}
			else {
				table.clearSelection();
			}

		}
	}

	public void scrollToSelectedValue() {
		SwingUtilities.invokeLater(() -> {
			JTable table = getTable();
			int row = table.getSelectedRow();
			if (row >= 0) {
				table.scrollRectToVisible(table.getCellRect(row, 0, true));
			}
		});

	}

	@Override
	public @Nullable Object getValueAt(int rowIndex, int columnIndex) {
		X item;
		try {
			item = data.get(rowIndex);
		}
		catch (IndexOutOfBoundsException oob) {
			// Concurrent change of data size
			return null;
		}
		try {
			long timeBefore = System.nanoTime();
			Object value = columns.get(columnIndex).getValue(item);
			long timeAfter = System.nanoTime();
			long delta = timeAfter - timeBefore;
			// TODO make this value customizable
			if (delta > 1_000_000) {
				log.warn("Slow getValueAt performance: took {}ns to get value at row {} col {}", delta, rowIndex, columnIndex);
			}
			return value;
		}
		catch (Throwable e) {
			log.error("ERROR getting value for row {} col {} value {}", rowIndex, columnIndex, item, e);
			return "INTERNAL ERROR";
		}
	}

	public JTable makeTable() {
		JTable table = new JTable(this);
		configureColumns(table);
		return table;
	}
}
