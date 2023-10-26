package com.bankedtooltips.data;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import lombok.Value;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.client.game.ItemManager;

import javax.annotation.Nullable;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

@Value
public class BankSave {
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss, d MMM uuuu");
    private static final SaferUsernameFunction SAFER_USERNAME = new SaferUsernameFunction();
    private static final long ID_BASE = System.currentTimeMillis();
    private static final AtomicInteger idIncrementer = new AtomicInteger();

    private static final int NULL_ITEM_ID = -1;

    long id;
    BankWorldType worldType;
    String dateTimeString;
    String userName;
    @Nullable String saveName;
    ImmutableList<BankItem> itemData;

    @VisibleForTesting
    public BankSave(
            BankWorldType worldType,
            String userName,
            @Nullable String saveName,
            String dateTimeString,
            ImmutableList<BankItem> itemData) {
        id = ID_BASE + idIncrementer.incrementAndGet();
        this.worldType = worldType;
        this.userName = userName;
        this.saveName = saveName;
        this.dateTimeString = dateTimeString;
        this.itemData = itemData;
    }

    public BankWorldType getWorldType() {
        return worldType == null ? BankWorldType.DEFAULT : worldType;
    }

    public static BankSave fromCurrentBank(
            BankWorldType worldType,
            String userName,
            ItemContainer bank,
            ItemManager itemManager
    ) {
        Objects.requireNonNull(bank);
        Item[] contents = bank.getItems();
        ImmutableList.Builder<BankItem> itemData = ImmutableList.builder();

        for (Item item: contents) {
            int idInBank = item.getId();
            int canonId = itemManager.canonicalize(idInBank);

            if (idInBank != canonId) continue;
            else if (isItemToClean(idInBank)) continue;

            itemData.add(new BankItem(canonId, item.getQuantity()));
        }

        String timeString = DATE_FORMATTER.format(ZonedDateTime.now());
        return new BankSave(worldType, userName, null, timeString, itemData.build());
    }

    private static boolean isItemToClean(int itemId) {
        return itemId == NULL_ITEM_ID || itemId == ItemID.BANK_FILLER;
    }

    public static BankSave snapshotFromExistingBank(String newName, BankSave existingBank) {
        Objects.requireNonNull(newName);

        return new BankSave(
                existingBank.worldType,
                existingBank.userName,
                newName,
                existingBank.dateTimeString,
                existingBank.itemData
        );
    }

    public static BankSave cleanItemData(BankSave existingBank) {
        Objects.requireNonNull(existingBank);

        ImmutableList<BankItem> cleanItemData = existingBank.itemData.stream()
                .filter(i -> !isItemToClean(i.getItemId()))
                .collect(ImmutableList.toImmutableList());

        return new BankSave(
                existingBank.worldType,
                existingBank.userName,
                existingBank.saveName,
                existingBank.dateTimeString,
                cleanItemData
        );
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("id", id)
                .add("username", SAFER_USERNAME.from(userName))
                .add("dateTimeString", dateTimeString)
                .add("saveName", saveName)
                .add("itemData", itemData)
                .toString();
    }

    public String getUserName() {
        return userName;
    }

    public long getId() {
        return id;
    }

    public int getItemQuantity(int itemId) {
        for (BankItem item: itemData) {
            if (item.getItemId() == itemId) return item.getQuantity();
        }

        return 0;
    }
}
