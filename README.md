# NuvaPlayerSynchro

[![Version](https://img.shields.io/badge/version-1.3.0-blue.svg)](https://github.com/loyfael/NuvaPlayerSynchro)
[![Minecraft](https://img.shields.io/badge/minecraft-1.21.7-green.svg)](https://www.minecraft.net/)
[![License](https://img.shields.io/badge/license-AGPLv3-red.svg)](https://github.com/loyfael/NuvaPlayerSynchro/blob/master/LICENSE.txt)

**Ultra-high-performance player data synchronization plugin for Minecraft servers powered by MongoDB**

NuvaPlayerSynchro is designed to handle **thousands of concurrent players** with MongoDB's NoSQL performance, optimized threading, advanced caching, and comprehensive crash protection. Perfect for massive Minecraft networks requiring blazing-fast player data synchronization.

> [!WARNING]
> NuvaPlayerSynchro is designed for high-concurrency environments and is theoretically suitable for large Minecraft networks.
> However, this plugin is **not yet ready for production use** and should preferably be used on **test or development servers**.
> If you choose to use it in a production environment, it is entirely **at your own risk**.
> It is **strongly recommended** to install an additional inventory backup plugin to ensure player inventory recovery in case of data loss or unexpected issues.
> If you experience any problems, please open an issue on the GitHub repository.

## ✨ Features

### 🚀 **Ultra-High Performance**
- **MongoDB NoSQL database** for extreme concurrent operations
- **Asynchronous operations** with dedicated thread pools (cores × 4)
- **MongoDB native connection pooling** (20-100 connections) for maximum throughput
- **Intelligent caching system** with LRU eviction (2000+ entries)
- **Bulk write operations** with compression (zstd/snappy/zlib)
- **Adaptive autosave intervals** based on server load

### 🛡️ **Advanced Crash Protection**
- **JVM shutdown hooks** for emergency data protection
- **Lag detection** with automatic emergency saves
- **Multi-layer backup mechanisms** with progressive retry logic
- **Configurable thresholds** optimized for different server sizes

### ⚡ **Smart Synchronization**
- **Player XP** and experience levels
- **Inventory contents** with intelligent compression
- **Enderchest contents** with caching optimization
- **Health and hunger** status with real-time updates
- **Batch processing** for reduced database load

### 🌐 **Multi-language Support**
- **English** and **French** translations included
- **Easy to extend** with additional languages
- **Dynamic language switching** without restart

### 📊 **Performance Monitoring**
- **Real-time statistics** via `/sync stats` command
- **MongoDB connection monitoring** with detailed metrics
- **Memory usage alerts** and performance recommendations
- **Debug mode** for troubleshooting

## 📋 Requirements

- **Minecraft**: 1.21+
- **Server Software**: Spigot, Paper, or compatible
- **Java**: 21+
- **Database**: MongoDB 4.4+ (recommended: 6.0+)
- **RAM**: Minimum 4GB (recommended: 8GB+ for large servers)

## 🔧 Installation

### 1. Install MongoDB

**Ubuntu/Debian:**
```bash
# Install MongoDB
wget -qO - https://www.mongodb.org/static/pgp/server-6.0.asc | sudo apt-key add -
echo "deb [ arch=amd64,arm64 ] https://repo.mongodb.org/apt/ubuntu focal/mongodb-org/6.0 multiverse" | sudo tee /etc/apt/sources.list.d/mongodb-org-6.0.list
sudo apt-get update
sudo apt-get install -y mongodb-org

# Start MongoDB
sudo systemctl start mongod
sudo systemctl enable mongod
```

**Windows:**
Download and install from [MongoDB Download Center](https://www.mongodb.com/try/download/community)

**Docker:**
```bash
docker run -d --name mongodb -p 27017:27017 mongo:6.0
```

### 2. Install Plugin

1. **Download** the latest release from [Releases](https://github.com/loyfael/NuvaPlayerSynchro/releases)
2. **Place** the `.jar` file in your server's `plugins/` folder
3. **Configure** your MongoDB settings in `config.yml`
4. **Restart** your server
5. **Enjoy** ultra-high-performance player data synchronization!

## ⚙️ Configuration

### Basic MongoDB Setup

```yaml
database:
  type: "mongodb"
  mongodb:
    host: "localhost"
    port: 27017
    database: "minecraft"
    username: ""  # Leave empty for no auth
    password: ""  # Leave empty for no auth
```

### Performance Presets

#### Small Server (1-50 players)
```yaml
autosave:
  interval: 600  # 10 minutes
  high-load-threshold: 50
database:
  mongodb:
    connection-pool:
      max-size: 30
```

#### Medium Server (50-200 players) - **DEFAULT**
```yaml
autosave:
  interval: 300  # 5 minutes
  high-load-threshold: 100
database:
  mongodb:
    connection-pool:
      max-size: 50
```

#### Large Server (200-500 players)
```yaml
autosave:
  interval: 180  # 3 minutes
  high-load-interval: 45
  high-load-threshold: 200
database:
  mongodb:
    connection-pool:
      max-size: 75
```

#### Massive Server (500+ players)
```yaml
autosave:
  interval: 120  # 2 minutes
  high-load-interval: 30
  high-load-threshold: 300
database:
  mongodb:
    connection-pool:
      max-size: 100
```

## 🎯 Performance Optimizations

### Why MongoDB over MySQL/MariaDB/PostgreSQL?

MongoDB was specifically chosen for this plugin because player data in Minecraft is naturally hierarchical and document-based (inventories, enderchests, complex nested structures). Unlike MySQL which requires complex table relationships and JOIN operations that become bottlenecks under high concurrency, MongoDB stores player data as single documents, enabling atomic operations and eliminating the need for expensive relational queries. The NoSQL architecture provides superior horizontal scalability, native JSON handling for inventory serialization, and built-in sharding capabilities that make it ideal for massive Minecraft networks with thousands of concurrent players. Additionally, MongoDB's connection pooling and bulk write operations offer significantly better performance for the frequent read/write patterns typical in player data synchronization.

### MongoDB Server Configuration
Add to your `mongod.conf`:

```yaml
storage:
  wiredTiger:
    engineConfig:
      cacheSizeGB: 2
    collectionConfig:
      blockCompressor: zstd
net:
  maxIncomingConnections: 200
operationProfiling:
  mode: off
```
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
And please, test your new feature in tests servers.

### What I Don't Provide
- ❌ **Custom feature development** on request
- ❌ **Paid development services**
- ❌ **Priority support** for specific use cases
- ❌ **Server-specific configurations**

**Note**: This plugin meets my server's requirements. If you need additional features, you're encouraged to fork and modify it according to the AGPLv3 license terms.
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

**Made with ❤️ for the Minecraft community**
