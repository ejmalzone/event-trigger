package gg.xp.xivsupport.persistence;

import gg.xp.xivsupport.persistence.settings.BooleanSetting;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class PropertiesFilePersistenceProvider extends BaseStringPersistenceProvider {

	private static final Logger log = LoggerFactory.getLogger(PropertiesFilePersistenceProvider.class);
	private final ExecutorService exs = Executors.newSingleThreadExecutor();
	private final Properties properties;
	private final File file;
	private final File backupFile;
	private final boolean canBeReadOnly;
	private final BooleanSetting readOnlySetting;
	private boolean readOnly;


	public PropertiesFilePersistenceProvider(File file) {
		this(file, false);
	}

	public PropertiesFilePersistenceProvider(File file, boolean canBeReadOnly) {
		this.canBeReadOnly = canBeReadOnly;
		this.file = file;
		this.backupFile = Paths.get(file.getParentFile().toPath().toString(), file.getName() + ".backup").toFile();
		Properties properties = new Properties();
		try {
			try (FileInputStream stream = new FileInputStream(file)) {
				properties.load(stream);
			}
		}
		catch (FileNotFoundException e) {
			log.info("Properties file does not yet exist");
		}
		catch (IOException e) {
			throw new RuntimeException("Could not load properties", e);
		}
		this.properties = properties;
		writeChangesToDisk();
		this.readOnlySetting = new BooleanSetting(this, "read-only-settings", false);
		this.readOnlySetting.addListener(this::checkReadOnlySetting);
		readOnly = this.canBeReadOnly && readOnlySetting.get();
	}

	private void checkReadOnlySetting() {
		writeChangesToDisk();
		readOnly = canBeReadOnly && readOnlySetting.get();
		writeChangesToDisk();
	}

	private Future<?> writeChangesToDisk() {
		if (readOnly) {
			log.info("Not Saving Settings - Read Only");
			return CompletableFuture.completedFuture(null);
		}
		return exs.submit(() -> {
			try {
				log.trace("Saving Persistent Settings");
				File parentFile = file.getParentFile();
				if (parentFile != null) {
					parentFile.mkdirs();
				}
				try (FileOutputStream stream = new FileOutputStream(file)) {
					properties.store(stream, "Saved programmatically - close program before editing");
				}
				if (!backupFile.exists()) {
					if (!backupFile.createNewFile()) {
						throw new RuntimeException("Could not create backup file");
					}
				}
				try (FileOutputStream stream = new FileOutputStream(backupFile)) {
					properties.store(stream, "Saved programmatically - backup file");
				}
			}
			catch (IOException e) {
				log.error("Error saving properties! Changes may not be saved!", e);
			}
		});
	}

	public void flush() {
		Future<?> future = writeChangesToDisk();
		try {
			future.get(10, TimeUnit.SECONDS);
		}
		catch (InterruptedException | ExecutionException | TimeoutException e) {
			log.error("ERROR SAVING SETTINGS - CHANGES MAY NOT BE SAVED TO DISK!", e);
			throw new RuntimeException(e);
		}
	}

	@Override
	protected void setValue(@NotNull String key, @Nullable String value) {
		String truncated = StringUtils.abbreviate(value, 300);
		log.info("Setting changed: {} -> {}", key, truncated);
		properties.setProperty(key, value);
		writeChangesToDisk();
	}

	@Override
	protected void deleteValue(@NotNull String key) {
		log.info("Setting deleted: {}", key);
		properties.remove(key);
		writeChangesToDisk();
	}

	public BooleanSetting getReadOnlySetting() {
		return readOnlySetting;
	}

	@Override
	protected @Nullable String getValue(@NotNull String key) {
		return properties.getProperty(key);
	}

	@Override
	protected void clearAllValues() {
		log.info("Settings wiped");
		properties.clear();
		writeChangesToDisk();
	}

	public void writeDatedBackupFile() {
		if (!readOnly) {
			File datedBackupDir = file.getParentFile().toPath().resolve("setting backups").toFile();
			datedBackupDir.mkdirs();
			File datedBackupFile = datedBackupDir.toPath().resolve(ZonedDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH_mm_ss")) + '_' + file.getName()).toFile();
			try {
				datedBackupFile.createNewFile();
				try (FileOutputStream stream = new FileOutputStream(datedBackupFile)) {
					properties.store(stream, "Saved programmatically - this is a backup file");
				}
			}
			catch (IOException e) {
				throw new RuntimeException(e);
			}

		}
	}
}
