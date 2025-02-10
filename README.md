# Minecraft Server

This repository contains a Docker Compose setup for running a Minecraft server with Velocity proxy and MariaDB database.

## Prerequisites

- Docker
- Docker Compose
- Git

## Setup

### 1. Environment Variables

The server requires several environment files to be configured:

#### Create `proxy.env`:
```env
# Velocity proxy configuration
CFG_MYSQL_DATABASE=minecraft
CFG_MYSQL_USER=minecraft
CFG_MYSQL_PASSWORD=your_password
```

#### Create `survival1.env`:
```env
CFG_MYSQL_DATABASE=minecraft
CFG_MYSQL_USER=minecraft
CFG_MYSQL_PASSWORD=your_password
```

#### Create `database.env`:
```env
MYSQL_ROOT_PASSWORD=your_root_password
MYSQL_DATABASE=minecraft
MYSQL_USER=minecraft
MYSQL_PASSWORD=your_password
```

### 2. Generate Velocity Secret

The Velocity proxy requires a secret key for secure communication. Generate it using:

```bash
# Generate a random secret
openssl rand -base64 32 > velocity.secret
```

### 3. Running the Server

1. Set your user and group IDs for proper file permissions:
```bash
export UID=$(id -u)
export GID=$(id -g)
```

2. Start the server:
```bash
docker compose up -d
```

The server will be accessible at `localhost:25565`.

## Server Components

- **Proxy**: Velocity proxy server running on port 25565
- **Survival1**: Paper Minecraft server (version 1.21.4)
- **Database**: MariaDB 11.4.5 for plugin data storage

## Directory Structure

- `store/`: Persistent data storage
  - `mc1/`: Minecraft server data
  - `proxy/`: Velocity proxy data
- `mc1/`: Minecraft server configuration
  - `plugins/`: Server plugins
  - `config/`: Plugin configurations
- `proxy/`: Velocity proxy configuration
  - `plugins/`: Proxy plugins
  - `config/`: Proxy configurations

## Stopping the Server

To stop the server:
```bash
docker compose down
```

To stop and remove all data (including world data):
```bash
docker compose down -v
```

## Importing an Existing World

To import an existing Minecraft world:

1. Stop the server if it's running:
```bash
docker compose down
```

2. Navigate to the world data directory:
```bash
cd store/mc1/world
```

3. Import your world files:
   - Copy your world files (folders like `world`, `world_nether`, `world_the_end`) into `store/mc1/`
   - Ensure proper file permissions:
```bash
# Replace [world_folder] with your world folder name
sudo chown -R $UID:$GID store/mc1/[world_folder]
```

4. Update the `level-name` in `store/mc1/server.properties` to match your world folder name if different from the default.

5. Start the server:
```bash
docker compose up -d
```

Note: Make sure your world version is compatible with the server version (1.21.4). You may need to use a world upgrader if importing from an older version.

