package loyfael;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class MessageManager {
  private final Main plugin;
  private FileConfiguration messages;

  public MessageManager(Main plugin) {
    this.plugin = plugin;
  }

  public void load(String language) {
    try {
      // Ne pas écraser le fichier s'il existe déjà
      File file = new File(plugin.getDataFolder(), "message_" + language + ".yml");
      if (!file.exists()) {
        plugin.saveResource("message_" + language + ".yml", false);
      }
      this.messages = YamlConfiguration.loadConfiguration(file);
    } catch (Exception e) {
      plugin.getLogger().warning("Impossible de charger le fichier de langue: message_" + language + ".yml");
      plugin.getLogger().warning("Utilisation des messages par défaut");
      // Créer une configuration vide par défaut
      this.messages = new YamlConfiguration();
    }
  }

  public String get(String key) {
    if (messages == null) return key;
    return messages.getString(key, key);
  }
}
