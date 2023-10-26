package com.bankedtooltips.data;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

@Slf4j
@Singleton
public class PluginDataStore {
    private static final String PLUGIN_BASE_GROUP = "bankedTooltips";
    private static final String CURRENT_LIST_KEY = "currentList";
    private static final String SNAPSHOT_LIST_KEY = "snapshotList";
    private static final String NAME_MAP_KEY = "nameMap";

    private final Object dataLock = new Object();
    private ConfigManager configManager;
    private final ItemDataParser itemDataParser;
    private final Map<String, String> nameMap = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
    private final List<BankSave> currentBankList;
    private final List<BankSave> snapshotBanksList;
    private final BlockingQueue<ConfigWrite> configWritesQueue = new LinkedBlockingQueue<>();
    private final List<DataStoreUpdateListener> listeners = new ArrayList<>();

    @Inject
    private PluginDataStore(ConfigManager configManager, ItemDataParser itemDataParser) {
        this.configManager = configManager;
        this.itemDataParser = itemDataParser;
        currentBankList = loadCurrentBankList();
        snapshotBanksList = loadSnapshotBanksList();
        nameMap.putAll(loadNameMapData());
        Thread configWriter = new Thread(new ConfigWriter(), "Bank Memory config writer");
        configWriter.setDaemon(true);
        configWriter.start();
    }

    private List<BankSave> loadCurrentBankList() {
        Type deserialiseType = new TypeToken<List<BankSave>>(){}.getType();
        List<BankSave> fromDataStore = loadDataFromConfig(CURRENT_LIST_KEY, deserialiseType,
                new ArrayList<>(), "Current bank list");
        return upgradeBankSaves(fromDataStore);
    }

    private <T> T loadDataFromConfig(String configKey, Type deserialiseType, T defaultInstance, String dataName) {
        String jsonString = configManager.getConfiguration(PLUGIN_BASE_GROUP, configKey);
        if (jsonString == null) {
            // Never set before
            return defaultInstance;
        }

        Gson gson = buildGson();
        try {
            T loadedData = gson.fromJson(jsonString, deserialiseType);
            return loadedData == null ? defaultInstance : loadedData;
        } catch (JsonParseException ex) {
//            log.error("{} json invalid. All is lost", dataName, ex);
            configManager.unsetConfiguration(PLUGIN_BASE_GROUP, configKey);
            return defaultInstance;
        }
    }

    private List<BankSave> upgradeBankSaves(List<BankSave> bankSaves) {
        return bankSaves.stream()
                .map(BankSave::cleanItemData)
                .collect(Collectors.toList());
    }

    private List<BankSave> loadSnapshotBanksList() {
        Type deserialiseType = new TypeToken<List<BankSave>>(){}.getType();
        List<BankSave> fromDataStore =  loadDataFromConfig(SNAPSHOT_LIST_KEY, deserialiseType, new ArrayList<>(), "Snapshot bank list");
        return upgradeBankSaves(fromDataStore);
    }

    private Gson buildGson() {
        Type itemDataListType = new TypeToken<ImmutableList<BankItem>>(){}.getType();
        return new GsonBuilder()
                .registerTypeAdapter(itemDataListType, itemDataParser)
                .create();
    }

    private Map<String, String> loadNameMapData() {
        Type deserialiseType = new TypeToken<HashMap<String, String>>(){}.getType();
        return loadDataFromConfig(NAME_MAP_KEY, deserialiseType, new HashMap<>(), "Display name map");
    }

    public void registerDisplayNameForLogin(String login, String displayName) {
       try {
           List<DataStoreUpdateListener> listenersCopy;
           boolean changed;
           synchronized (dataLock) {
               listenersCopy = new ArrayList<>(listeners);
               String oldValue = nameMap.put(login, displayName);
               changed = !Objects.equals(oldValue, displayName);
               if (changed) {
                   ConfigWrite write = new ConfigWrite(PLUGIN_BASE_GROUP, NAME_MAP_KEY, new HashMap<>(nameMap));
                   scheduleConfigWrite(write);
               }
           }
           if (changed) {
               listenersCopy.forEach(DataStoreUpdateListener::displayNameMapUpdated);
           }
       } catch (Exception e) {
              StringWriter sw = new StringWriter();
              e.printStackTrace(new PrintWriter(sw));
       }
    }

    public DisplayNameMapper getDisplayNameMapper() {
        synchronized (dataLock) {
            return new DisplayNameMapper(nameMap);
        }
    }

    public void addListener(DataStoreUpdateListener listener) {
        synchronized (dataLock) {
            listeners.add(listener);
        }
    }

    public void removeListener(DataStoreUpdateListener listener) {
        synchronized (dataLock) {
            listeners.remove(listener);
        }
    }

    public Optional<BankSave> getDataForCurrentBank(BankWorldType worldType, String login) {
        if (Strings.isNullOrEmpty(login)) {
            return Optional.empty();
        }
        synchronized (dataLock) {
            return currentBankList.stream()
                    .filter(s -> s.getWorldType() == worldType && s.getUserName().equalsIgnoreCase(login))
                    .findAny();
        }
    }

    public List<BankSave> getCurrentBanksList() {
        synchronized (dataLock) {
            return new ArrayList<>(currentBankList);
        }
    }

    public List<BankSave> getSnapshotBanksList() {
        synchronized (dataLock) {
            return new ArrayList<>(snapshotBanksList);
        }
    }

    public Optional<BankSave> getBankSaveWithId(int id) {
        synchronized (dataLock) {
            return Stream.concat(currentBankList.stream(), snapshotBanksList.stream())
                    .filter(s -> s.getId() == id)
                    .findFirst();
        }
    }

    public Optional<BankSave> getItemQuantity(long itemId) {
        synchronized (dataLock) {
            return Stream.concat(currentBankList.stream(), snapshotBanksList.stream())
                    .filter(s -> s.getId() == itemId)
                    .findFirst();
        }
    }

    public void saveAsCurrentBank(BankSave newSave) {
        List<DataStoreUpdateListener> listenersCopy;
        synchronized (dataLock) {
            listenersCopy = new ArrayList<>(listeners);
            saveAsCurrentBankImpl(newSave);
        }
        listenersCopy.forEach(DataStoreUpdateListener::currentBanksListChanged);
    }

    private void saveAsCurrentBankImpl(BankSave newSave) {
        // Check if there is a current bank for existing login and remove it
        try {
            currentBankList.stream()
                    .filter(s -> s.getUserName().equalsIgnoreCase(newSave.getUserName())
                            && s.getWorldType() == newSave.getWorldType())
                    .findAny()
                    .ifPresent(currentBankList::remove);

            // Save new current bank at top of list
            currentBankList.add(0, newSave);
            ConfigWrite configWrite = new ConfigWrite(
                    PLUGIN_BASE_GROUP, CURRENT_LIST_KEY, new ArrayList<>(currentBankList));
            scheduleConfigWrite(configWrite);
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
        }
    }

    public void saveAsSnapshotBank(String newName, BankSave existingSave) {
        try {
            List<DataStoreUpdateListener> listenersCopy;
            synchronized (dataLock) {
                listenersCopy = new ArrayList<>(listeners);
                snapshotBanksList.add(0, BankSave.snapshotFromExistingBank(newName, existingSave));
                ConfigWrite configWrite = new ConfigWrite(
                        PLUGIN_BASE_GROUP, SNAPSHOT_LIST_KEY, new ArrayList<>(snapshotBanksList));
                scheduleConfigWrite(configWrite);
            }
            listenersCopy.forEach(DataStoreUpdateListener::snapshotBanksListChanged);
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            e.printStackTrace(new PrintWriter(sw));
        }
    }

    private void scheduleConfigWrite(ConfigWrite configWrite) {
        try {
//            log.debug("Scheduling write for {}.{}", configWrite.configGroup, configWrite.configKey);
            configWritesQueue.put(configWrite);
        } catch (InterruptedException ex) {
//            log.error("Unexpected interrupt whilst schedule config write. Data not being written", ex);
        }
    }

    public void deleteBankSaveWithId(long saveId) {
        List<DataStoreUpdateListener> listenersCopy;
        boolean changed;
        synchronized (dataLock) {
            listenersCopy = new ArrayList<>(listeners);
            changed = deleteBankSaveWithIdImpl(saveId, currentBankList, CURRENT_LIST_KEY)
                    || deleteBankSaveWithIdImpl(saveId, snapshotBanksList, SNAPSHOT_LIST_KEY);
        }
        if (changed) {
            listenersCopy.forEach(DataStoreUpdateListener::currentBanksListChanged);
        } else {
//            log.error("Tried deleting missing bank save: {}", saveId);
        }
    }

    private boolean deleteBankSaveWithIdImpl(long id, List<BankSave> saveList, String listConfigKey) {
        Optional<BankSave> save = saveList.stream()
                .filter(s -> s.getId() == id)
                .findFirst();
        if (save.isPresent()) {
            try {
                saveList.remove(save.get());
                ConfigWrite configWrite = new ConfigWrite(
                        PLUGIN_BASE_GROUP, listConfigKey, new ArrayList<>(saveList));
                scheduleConfigWrite(configWrite);
                return true;
            } catch (Exception e) {
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
            }
        }
        return false;
    }

    @AllArgsConstructor
    private static class ConfigWrite {
        final String configGroup;
        final String configKey;
        final Object data;
    }

    // NB: technically there's a race condition because it's possible that the client could shut down before the call
    // to the ConfigManager is made. However, us trying to react to a client shutdown by writing all the remaining
    // writes won't work since the ConfigManager is the first thing to react to a client shutdown and won't perform any
    // new writes once it has.
    // Not very likely to happen anyway since it basically requires user to do something in game (to trigger plugin) and
    // then super quickly (like <10ms) close the client.
    private class ConfigWriter implements Runnable {
        @Override
        public void run() {
            Gson gson = buildGson();
            while (!Thread.interrupted()) {
                ConfigWrite write;
                try {
                    write = configWritesQueue.take();
//                    log.debug("Got write for {}.{}", write.configGroup, write.configKey);
                } catch (InterruptedException ex) {
//                    log.warn("ConfigWriter thread interrupted", ex);
                    break;
                }
                configManager.setConfiguration(write.configGroup, write.configKey, gson.toJson(write.data));
            }
        }
    }

}

