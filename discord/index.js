const { Client, Events, GatewayIntentBits } = require('discord.js');
const { token } = require('./config.json');
const WebSocket = require('ws');

const config = {
  token: token,
  channelId: '643453729776992264',
  wsPort: 8080,
  authToken: ''
};

const client = new Client({
  intents: [
    GatewayIntentBits.Guilds,
    GatewayIntentBits.GuildMessages,
    GatewayIntentBits.MessageContent
  ]
});

const wss = new WebSocket.Server({ port: config.wsPort, host: "0.0.0.0" });
console.log(`WebSocket server started on port ${config.wsPort}`);

const connectedClients = new Set();

const serverStatus = new Map();

wss.on('connection', (ws) => {
  console.log('New client connected');
  
  let authenticated = false;
  let serverName = 'Unknown Server';
  
  ws.on('message', (message) => {
    try {
      const data = JSON.parse(message.toString());
      const messageType = data.type || '';
      
      if (messageType === 'auth') {
        if (data.token === config.authToken) {
          authenticated = true;
          console.log('Client authenticated');
          ws.send(JSON.stringify({ 
            type: 'auth', 
            status: 'success' 
          }));
        } else {
          console.log('Authentication failed');
          ws.send(JSON.stringify({ 
            type: 'auth', 
            status: 'failed', 
            message: 'Invalid token' 
          }));
          ws.close();
        }
        return;
      }
      
      if (!authenticated && config.authToken !== '') {
        ws.send(JSON.stringify({ 
          type: 'error', 
          message: 'Not authenticated' 
        }));
        return;
      }
      
      if (messageType === 'server_info') {
        serverName = data.name || 'Minecraft Server';
        
        serverStatus.set(ws, {
          name: data.name,
          version: data.version,
          online: data.online,
          max: data.max,
          lastUpdate: new Date()
        });
        
        console.log(`Server info received: ${serverName} (${data.online}/${data.max})`);
        
        ws.send(JSON.stringify({
          type: 'system',
          content: 'Successfully connected to Discord!'
        }));
      }
      
      if (messageType === 'chat') {
        const channel = client.channels.cache.get(config.channelId);
        if (channel) {
          channel.send(`**${data.username}**: ${data.content}`);
          console.log(`Message sent to Discord: ${data.username}: ${data.content}`);
        }
      }
      
      if (messageType === 'player_join') {
        const channel = client.channels.cache.get(config.channelId);
        if (channel) {
          channel.send(`**${data.username}** joined the server (${data.online}/${data.max} players)`);
          console.log(`Join message sent to Discord: ${data.username} joined`);
        }
        
        if (serverStatus.has(ws)) {
          const status = serverStatus.get(ws);
          status.online = data.online;
          serverStatus.set(ws, status);
        }
      }
      
      if (messageType === 'player_leave') {
        const channel = client.channels.cache.get(config.channelId);
        if (channel) {
          channel.send(`**${data.username}** left the server (${data.online}/${data.max} players)`);
          console.log(`Leave message sent to Discord: ${data.username} left`);
        }
        
        if (serverStatus.has(ws)) {
          const status = serverStatus.get(ws);
          status.online = data.online;
          serverStatus.set(ws, status);
        }
      }
      
      if (messageType === 'player_list') {
        console.log('Received player list:', data.players);
      }
      
    } catch (error) {
      console.error('Error processing message:', error);
    }
  });
  
  ws.on('close', () => {
    console.log(`Client disconnected: ${serverName}`);
    connectedClients.delete(ws);
    serverStatus.delete(ws);
  });
  
  ws.on('error', (error) => {
    console.error('WebSocket error:', error);
  });
  
  connectedClients.add(ws);
  
  ws.send(JSON.stringify({
    type: 'time_sync',
    timestamp: Date.now()
  }));
});

client.once('ready', () => {
  console.log(`Logged in as ${client.user.tag}!`);
  
  setInterval(() => {
    let totalPlayers = 0;
    let totalServers = serverStatus.size;
    
    serverStatus.forEach(server => {
      totalPlayers += server.online;
    });
    
    console.log(`Status: ${totalServers} servers connected, ${totalPlayers} players online`);
    
    if (totalServers > 0) {
      client.user.setActivity(`${totalPlayers} players on ${totalServers} servers`, { type: 'WATCHING' });
    }
  }, 60000);
});

client.on('messageCreate', (message) => {
  if (message.author.bot) return;
  
  if (message.channelId !== config.channelId) return;
  
  const messageData = {
    type: 'chat',
    username: message.author.username,
    content: message.content
  };
  
  let sentCount = 0;
  connectedClients.forEach((client) => {
    if (client.readyState === WebSocket.OPEN) {
      client.send(JSON.stringify(messageData));
      sentCount++;
    }
  });
  
  if (sentCount > 0) {
    console.log(`Message sent to ${sentCount} Minecraft servers: ${message.author.username}: ${message.content}`);
  }
  
  if (message.content === '!players') {
    connectedClients.forEach(client => {
      if (client.readyState === WebSocket.OPEN) {
        client.send(JSON.stringify({ type: 'request_players' }));
      }
    });
    
    let statusMessage = '**Connected Minecraft Servers:**\n';
    
    if (serverStatus.size === 0) {
      statusMessage += 'No servers connected.';
    } else {
      serverStatus.forEach((status, _) => {
        statusMessage += `- **${status.name}**: ${status.online}/${status.max} players (${status.version})\n`;
      });
    }
    
    message.channel.send(statusMessage);
  }
});

client.login(config.token);

process.on('SIGINT', () => {
  console.log('Shutting down...');
  wss.close(() => {
    console.log('WebSocket server closed');
    process.exit(0);
  });
});