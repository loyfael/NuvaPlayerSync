package loyfael;

import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.*;
import java.util.Base64;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class InventoryUtils {
    // Cache pour éviter la re-sérialisation d'inventaires identiques
    private static final ConcurrentHashMap<Integer, String> serializationCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 1000;

    @SuppressWarnings("deprecation")
    public static String itemStackArrayToBase64(ItemStack[] items) throws IOException {
        // Calculer un hash rapide de l'inventaire
        int inventoryHash = calculateInventoryHash(items);
        String cached = serializationCache.get(inventoryHash);
        if (cached != null) {
            return cached;
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             GZIPOutputStream gzipOut = new GZIPOutputStream(outputStream)) {

            try (BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(gzipOut)) {
                dataOutput.writeInt(items.length);
                for (ItemStack item : items) {
                    if (item != null && item.getAmount() > 0) {
                        dataOutput.writeBoolean(true);
                        dataOutput.writeObject(item);
                    } else {
                        dataOutput.writeBoolean(false);
                    }
                }
            }

            String result = Base64.getEncoder().encodeToString(outputStream.toByteArray());

            // Mettre en cache si l'inventaire n'est pas trop gros
            if (result.length() < 10000 && serializationCache.size() < MAX_CACHE_SIZE) {
                serializationCache.put(inventoryHash, result);
            }

            return result;
        }
    }

    @SuppressWarnings("deprecation")
    public static ItemStack[] itemStackArrayFromBase64(String data) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64.getDecoder().decode(data));
             GZIPInputStream gzipIn = new GZIPInputStream(inputStream);
             BukkitObjectInputStream dataInput = new BukkitObjectInputStream(gzipIn)) {

            ItemStack[] items = new ItemStack[dataInput.readInt()];
            for (int i = 0; i < items.length; i++) {
                if (dataInput.readBoolean()) {
                    items[i] = (ItemStack) dataInput.readObject();
                } else {
                    items[i] = null;
                }
            }
            return items;
        }
    }

    private static int calculateInventoryHash(ItemStack[] items) {
        int hash = 1;
        for (ItemStack item : items) {
            if (item != null && item.getAmount() > 0) {
                hash = 31 * hash + item.getType().hashCode();
                hash = 31 * hash + item.getAmount();
                if (item.hasItemMeta()) {
                    hash = 31 * hash + item.getItemMeta().hashCode();
                }
            }
        }
        return hash;
    }

    public static void clearCache() {
        serializationCache.clear();
    }

    public static int getCacheSize() {
        return serializationCache.size();
    }
}
