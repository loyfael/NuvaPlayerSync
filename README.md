# LoyPlayerSynchro

[![Version](https://img.shields.io/badge/version-1.0.0-blue.svg)](https://github.com/loyfael/NuvaPlayerSynchro)
[![Minecraft](https://img.shields.io/badge/minecraft-1.21+-green.svg)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/license-AGPLv3-red.svg)](https://github.com/loyfael/NuvaPlayerSynchro/blob/master/LICENSE.txt)

**High-performance player data synchronization plugin for Minecraft servers**

NuvaPlayerSynchro is designed to handle hundreds of concurrent players with optimized threading, advanced caching, and comprehensive crash protection. Perfect for large-scale Minecraft networks requiring reliable player data synchronization.

## ✨ Features

### 🚀 **High Performance**
- **Asynchronous operations** with dedicated thread pools
- **HikariCP connection pooling** for maximum database performance
- **Advanced caching system** with thread-safe operations
- **Batch processing** to reduce database load
- **Adaptive autosave intervals** based on server load

### 🛡️ **Crash Protection**
- **JVM shutdown hooks** for emergency data protection
- **Lag detection** with automatic emergency saves
- **Backup save mechanisms** with retry logic
- **Configurable thresholds** for different server sizes

### 📊 **Smart Synchronization**
- **Player XP** and experience levels
- **Inventory contents** with compression
- **Enderchest contents** 
- **Health and hunger** status
- **Real-time data validation** and caching

### 🌐 **Multi-language Support**
- **English** and **French** translations included
- **Easy to extend** with additional languages
- **Dynamic language switching** without restart

## 📋 Requirements

- **Minecraft**: 1.21+
- **Server Software**: Spigot, Paper, or compatible
- **Java**: 17+
- **Database**: MySQL 5.7+ (recommended for high load)

## 🔧 Installation

1. **Download** the latest release from [Releases](https://github.com/loyfael/NuvaPlayerSynchro/releases)
2. **Place** the `.jar` file in your server's `plugins/` folder
3. **Configure** your database settings in `config.yml`
4. **Restart** your server
5. **Enjoy** high-performance player data synchronization!

## ⚙️ Configuration

### Quick Setup Guide

1. **Configure database settings** in `config.yml`
2. **Adjust autosave intervals** based on your server load
3. **Enable crash protection** for production servers
4. **Optimize MySQL** server-side (see recommendations below)

### Performance Presets

#### 🔹 Small Server (1-50 players)
```yaml
autosave:
  interval: 600 # 10 minutes
  high-load-threshold: 50
crash-protection:
  enabled: false # optional
database:
  mysql:
    maximum-pool-size: 10
```

#### 🔹 Medium Server (50-150 players)
```yaml
autosave:
  interval: 300 # 5 minutes - DEFAULT
  high-load-threshold: 100
crash-protection:
  enabled: true
database:
  mysql:
    maximum-pool-size: 20
```

#### 🔹 Large Server (150+ players)
```yaml
autosave:
  interval: 180 # 3 minutes
  high-load-interval: 60 # 1 minute when busy
  high-load-threshold: 150
crash-protection:
  enabled: true
database:
  mysql:
    maximum-pool-size: 30
```

### Database Configuration

#### MySQL Setup
```sql
CREATE DATABASE minecraft;
CREATE USER 'minecraft'@'localhost' IDENTIFIED BY 'your_secure_password';
GRANT ALL PRIVILEGES ON minecraft.* TO 'minecraft'@'localhost';
FLUSH PRIVILEGES;
```

#### Recommended MySQL Optimizations
Add these to your MySQL configuration (`my.cnf` or `my.ini`):
```ini
[mysqld]
innodb_buffer_pool_size = 1G
innodb_log_file_size = 256M
innodb_flush_log_at_trx_commit = 2
innodb_flush_method = O_DIRECT
max_connections = 500
```

## 🎮 Commands

| Command | Permission | Description |
|---------|------------|-------------|
| `/sync reload` | `playerdatasync.admin` | Reload plugin configuration |
| `/sync stats` | `playerdatasync.admin` | Show performance statistics |
| `/sync <option> [on/off]` | `playerdatasync.admin` | Toggle sync options |
| `/sync cache clear` | `playerdatasync.admin` | Clear inventory cache |

### Available Sync Options
- `xp` - Player experience synchronization
- `inventory` - Player inventory synchronization  
- `enderchest` - Enderchest synchronization
- `health` - Health and hunger synchronization
- `hunger` - Hunger and saturation synchronization

## 📊 Performance Monitoring

The plugin provides detailed performance statistics accessible via `/sync stats`:

- **Database operations** count and timing
- **Cache hit rates** and efficiency metrics
- **Thread pool utilization** for database and inventory operations
- **Connection pool statistics** (active, idle, total)
- **Memory usage** and recommendations

## 🔒 Permissions

| Permission | Description | Default |
|------------|-------------|---------|
| `playerdatasync.admin` | Full administrative access | `op` |
| `playerdatasync.admin.coordinates` | Manage coordinates sync | `op` |

## 🚨 Troubleshooting

### Common Issues

#### Database Connection Problems
```
[SEVERE] SAVE ERROR for player UUID: Connection timeout
```
**Solution**: Check database credentials and increase `connection-timeout` in config

#### High Memory Usage
```
[WARNING] Cache size exceeded recommended limits
```
**Solution**: Reduce `cache-size` or enable `cache-cleanup-interval`

#### Performance Issues
```
[WARNING] High load detected - Using accelerated autosave
```
**Solution**: This is normal behavior. Consider optimizing MySQL or increasing hardware resources.

### Debug Mode
Enable detailed logging by setting:
```yaml
performance:
  log-successful-saves: true
```
⚠️ **Warning**: This will generate many log entries and may impact performance.

## 🤝 Contributing

**Please note**: This plugin was originally developed for my personal Minecraft server needs. While I don't develop features on request or provide custom development services, **community contributions are very welcome!**

### Contribution Guidelines

1. **Fork** the repository
2. **Create** a feature branch (`git checkout -b feature/amazing-feature`)
3. **Commit** your changes (`git commit -m 'Add amazing feature'`)
4. **Push** to the branch (`git push origin feature/amazing-feature`)
5. **Open** a Pull Request

### What I Accept
- ✅ **Bug fixes** and stability improvements
- ✅ **Performance optimizations**
- ✅ **Code quality improvements**
- ✅ **Documentation updates**
- ✅ **New language translations**
- ✅ **Security enhancements**

### What I Don't Provide
- ❌ **Custom feature development** on request
- ❌ **Paid development services**
- ❌ **Priority support** for specific use cases
- ❌ **Server-specific configurations**

**Note**: This plugin meets my server's requirements. If you need additional features, you're encouraged to fork and modify it according to the AGPLv3 license terms.

## 📈 Roadmap

- [ ] **MongoDB** support
- [ ] **SQLite** support for smaller servers
- [ ] **Redis caching** for multi-server networks
- [ ] **API endpoints** for external integrations
- [ ] **Backup/restore** functionality
- [ ] **Data migration tools** from other plugins

## 📝 Changelog

### Version 1.0.0
- ✨ Initial release
- 🚀 High-performance architecture with thread pools
- 🛡️ Comprehensive crash protection
- 🌐 Multi-language support (EN/FR)
- 📊 Advanced performance monitoring
- 🔧 Adaptive autosave based on server load

## 📄 License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPLv3)** - see the [LICENSE](https://github.com/loyfael/NuvaPlayerSynchro/blob/master/LICENSE.txt) file for details.

### What this means:
- ✅ **Free to use** for personal and non-commercial projects
- ✅ **Modification allowed** with proper attribution to original author
- ✅ **Distribution allowed** with source code disclosure
- ❌ **Commercial use prohibited** without explicit permission
- 📝 **Copyleft** - derivative works must remain open source under AGPLv3
- 🔗 **Network use** - even SaaS deployments must provide source code

**Important**: Any fork, modification, or usage of this code **must** credit me (loyfael) and remain under AGPLv3 license.

## 🙏 Acknowledgments

- **HikariCP** for high-performance connection pooling
- **Bukkit/Spigot/PaperMC** community for excellent documentation
- **Contributors** who helped improve this plugin


**Made with ❤️ for the Minecraft community**
