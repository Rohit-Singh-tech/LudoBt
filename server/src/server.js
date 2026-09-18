/**
 * Authoritative Real-Time Ludo Game Server
 * Features:
 * 1. Room Creation with 6-digit PIN & Quick Matchmaking (2P and 4P)
 * 2. Authoritative Dice Generation (anti-cheat random numbers)
 * 3. Authoritative Move & Capture Validation
 * 4. 15-Second Turn Timers with automatic auto-move / auto-pass
 * 5. Full State Synchronization for Reconnection
 */

const { WebSocketServer, WebSocket } = require('ws');

const PORT = process.env.PORT || 8080;
const wss = new WebSocketServer({ port: PORT });

console.log(`[LudoServer] Real-time authoritative server listening on port ${PORT}`);

// Active Rooms Map: roomPin -> Room
const rooms = new Map();

// Quick Match Queues: playerCount -> Array of waiting client objects
const quickMatchQueues = {
  2: [],
  4: []
};

// Client to Room mapping: ws -> { roomPin, playerId }
const clientSessions = new Map();

// Generate random 6-digit numeric PIN
function generateRoomPin() {
  let pin;
  do {
    pin = Math.floor(100000 + Math.random() * 900000).toString();
  } while (rooms.has(pin));
  return pin;
}

// Player Colors
const COLORS = {
  RED: 'RED',
  GREEN: 'GREEN',
  YELLOW: 'YELLOW',
  BLUE: 'BLUE'
};

const OPPOSITE_COLORS = {
  RED: 'YELLOW',
  YELLOW: 'RED',
  BLUE: 'GREEN',
  GREEN: 'BLUE'
};

const ALL_COLORS = ['RED', 'GREEN', 'YELLOW', 'BLUE'];

// Safe Cells on 52-tile track
const SAFE_STAR_CELLS = [1, 8, 14, 21, 27, 34, 40, 47];
const START_OFFSETS = {
  RED: 1,
  GREEN: 14,
  YELLOW: 27,
  BLUE: 40
};

/**
 * Calculate track tile coordinate (1..52) for a token given color and stepCount (1..51)
 */
function getTrackTile(color, stepCount) {
  if (stepCount < 1 || stepCount > 51) return -1;
  const start = START_OFFSETS[color] || 1;
  return ((start - 1 + (stepCount - 1)) % 52) + 1;
}

function sendJson(ws, obj) {
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send(JSON.stringify(obj));
  }
}

function getSanitizedPlayers(players) {
  return players.map(p => ({
    id: p.id,
    name: p.name,
    color: p.color,
    type: 'HUMAN',
    tokens: p.tokens || [
      { id: 0, color: p.color, stepCount: 0, state: 'HOME_BASE' },
      { id: 1, color: p.color, stepCount: 0, state: 'HOME_BASE' },
      { id: 2, color: p.color, stepCount: 0, state: 'HOME_BASE' },
      { id: 3, color: p.color, stepCount: 0, state: 'HOME_BASE' }
    ],
    rank: p.rank || 0,
    isReady: p.isReady !== undefined ? p.isReady : true,
    isHost: !!p.isHost,
    isConnected: p.isConnected !== undefined ? p.isConnected : true
  }));
}

function broadcastToRoom(room, messageObj, excludeWs = null) {
  const jsonStr = JSON.stringify(messageObj);
  room.players.forEach(p => {
    if (p.ws && p.ws.readyState === WebSocket.OPEN && p.ws !== excludeWs) {
      p.ws.send(jsonStr);
    }
  });
}

/**
 * Compute valid token IDs for a player and dice roll
 */
function computeValidTokens(player, diceVal) {
  if (!player || !player.tokens || !diceVal) return [];
  const valid = [];
  player.tokens.forEach(t => {
    if (t.stepCount === 0) {
      // Must roll 6 to enter
      if (diceVal === 6) valid.push(t.id);
    } else if (t.stepCount > 0 && t.stepCount < 57) {
      // Must not overshoot finish 57
      if (t.stepCount + diceVal <= 57) {
        valid.push(t.id);
      }
    }
  });
  return valid;
}

/**
 * Start or Reset 15-second Turn Timer
 */
function startTurnTimer(room) {
  if (room.turnTimer) {
    clearInterval(room.turnTimer);
    room.turnTimer = null;
  }

  if (room.status !== 'IN_PROGRESS') return;

  room.remainingTimerSeconds = 15;

  room.turnTimer = setInterval(() => {
    room.remainingTimerSeconds -= 1;

    if (room.remainingTimerSeconds <= 0) {
      clearInterval(room.turnTimer);
      room.turnTimer = null;
      handleTurnTimeout(room);
    }
  }, 1000);
}

/**
 * Handle turn timeout (auto-roll or auto-pass)
 */
function handleTurnTimeout(room) {
  const currentPl = room.gameState.players[room.gameState.currentPlayerIndex];
  if (!currentPl) return;

  console.log(`[LudoServer] Turn timeout for player ${currentPl.name} in room ${room.pin}`);

  // If dice not rolled yet, auto-roll
  if (room.gameState.diceValue === null) {
    handleServerDiceRoll(room, currentPl.id, true);
  } else {
    // If dice already rolled and tokens movable, move first token
    if (room.gameState.validTokenIds.length > 0) {
      handleServerTokenMove(room, currentPl.id, room.gameState.validTokenIds[0], true);
    } else {
      passTurn(room, `Turn timed out for ${currentPl.name}`);
    }
  }
}

/**
 * Pass turn to next player
 */
function passTurn(room, reason = '') {
  const currentIdx = room.gameState.currentPlayerIndex;
  const nextIdx = (currentIdx + 1) % room.gameState.players.length;

  room.gameState.currentPlayerIndex = nextIdx;
  room.gameState.diceValue = null;
  room.gameState.isRolling = false;
  room.gameState.validTokenIds = [];
  room.gameState.consecutiveSixes = 0;
  room.gameState.sequenceNumber += 1;

  const nextPl = room.gameState.players[nextIdx];
  room.gameState.lastMoveDescription = reason || `${nextPl.name}'s turn. Roll the dice!`;

  broadcastToRoom(room, {
    type: 'FullStateSync',
    gameState: room.gameState
  });

  startTurnTimer(room);
}

/**
 * Server authoritative dice roll
 */
function handleServerDiceRoll(room, playerId, isAuto = false) {
  const currentPl = room.gameState.players[room.gameState.currentPlayerIndex];
  if (!currentPl || currentPl.id !== playerId) return;
  if (room.gameState.diceValue !== null || room.gameState.isRolling) return;

  const roll = Math.floor(Math.random() * 6) + 1;
  const consecutiveSixes = roll === 6 ? room.gameState.consecutiveSixes + 1 : 0;

  room.gameState.sequenceNumber += 1;

  if (consecutiveSixes >= 3) {
    // 3 consecutive sixes penalty
    room.gameState.diceValue = roll;
    room.gameState.isRolling = false;
    room.gameState.validTokenIds = [];
    room.gameState.consecutiveSixes = 0;
    room.gameState.lastMoveDescription = `${currentPl.name} rolled three 6s in a row! Turn forfeited.`;

    broadcastToRoom(room, {
      type: 'DiceRolledEvent',
      playerId: currentPl.id,
      value: roll,
      validTokenIds: [],
      seq: room.gameState.sequenceNumber
    });

    setTimeout(() => {
      passTurn(room);
    }, 1000);
    return;
  }

  const validTokens = computeValidTokens(currentPl, roll);

  room.gameState.diceValue = roll;
  room.gameState.isRolling = false;
  room.gameState.consecutiveSixes = consecutiveSixes;
  room.gameState.validTokenIds = validTokens;
  room.gameState.lastMoveDescription = validTokens.length === 0
    ? `${currentPl.name} rolled ${roll}. No valid moves!`
    : `${currentPl.name} rolled ${roll}. Select a token to move!`;

  broadcastToRoom(room, {
    type: 'DiceRolledEvent',
    playerId: currentPl.id,
    value: roll,
    validTokenIds: validTokens,
    seq: room.gameState.sequenceNumber
  });

  if (validTokens.length === 0) {
    setTimeout(() => {
      passTurn(room);
    }, 1000);
  } else if (isAuto) {
    // If auto timeout triggered, automatically move first token after 600ms
    setTimeout(() => {
      handleServerTokenMove(room, currentPl.id, validTokens[0], true);
    }, 600);
  }
}

/**
 * Server authoritative token move
 */
function handleServerTokenMove(room, playerId, tokenId, isAuto = false) {
  const currentPl = room.gameState.players[room.gameState.currentPlayerIndex];
  if (!currentPl || currentPl.id !== playerId) return;
  if (room.gameState.diceValue === null) return;
  if (!room.gameState.validTokenIds.includes(tokenId)) return;

  const roll = room.gameState.diceValue;
  const token = currentPl.tokens.find(t => t.id === tokenId);
  if (!token) return;

  const fromStep = token.stepCount;
  let toStep = fromStep === 0 ? 1 : fromStep + roll;
  if (toStep > 57) toStep = 57;

  token.stepCount = toStep;
  if (toStep === 57) {
    token.state = 'FINISHED';
  } else if (toStep >= 52) {
    token.state = 'HOME_COLUMN';
  } else {
    token.state = 'ACTIVE';
  }

  // Check Track Collision / Capture
  let capturedColor = null;
  let capturedTokenId = null;

  if (toStep >= 1 && toStep <= 51) {
    const landingTrackTile = getTrackTile(currentPl.color, toStep);
    const isLandingSafe = SAFE_STAR_CELLS.includes(landingTrackTile);

    if (!isLandingSafe && landingTrackTile !== -1) {
      // Check other players' tokens
      room.gameState.players.forEach(otherPl => {
        if (otherPl.id !== currentPl.id) {
          otherPl.tokens.forEach(otherT => {
            if (otherT.stepCount >= 1 && otherT.stepCount <= 51) {
              const otherTile = getTrackTile(otherPl.color, otherT.stepCount);
              if (otherTile === landingTrackTile) {
                // CAPTURE!
                otherT.stepCount = 0;
                otherT.state = 'HOME_BASE';
                capturedColor = otherPl.color;
                capturedTokenId = otherT.id;
              }
            }
          });
        }
      });
    }
  }

  // Check victory
  const hasFinishedAll = currentPl.tokens.every(t => t.stepCount === 57);
  if (hasFinishedAll && !room.gameState.winnerPodium.includes(currentPl.color)) {
    room.gameState.winnerPodium.push(currentPl.color);
    if (room.gameState.winnerPodium.length >= room.gameState.players.length - 1) {
      room.gameState.status = 'GAME_OVER';
    }
  }

  const extraTurn = (roll === 6 || capturedColor !== null || toStep === 57) && room.gameState.status !== 'GAME_OVER';
  room.gameState.sequenceNumber += 1;

  broadcastToRoom(room, {
    type: 'TokenMovedEvent',
    playerId: currentPl.id,
    tokenId: tokenId,
    fromStep: fromStep,
    toStep: toStep,
    capturedPlayerColor: capturedColor,
    capturedTokenId: capturedTokenId,
    extraTurnGranted: extraTurn,
    nextPlayerIndex: extraTurn ? room.gameState.currentPlayerIndex : (room.gameState.currentPlayerIndex + 1) % room.gameState.players.length,
    seq: room.gameState.sequenceNumber
  });

  if (extraTurn) {
    room.gameState.diceValue = null;
    room.gameState.isRolling = false;
    room.gameState.validTokenIds = [];
    room.gameState.lastMoveDescription = `${currentPl.name} earned an extra turn! Roll again!`;

    broadcastToRoom(room, {
      type: 'FullStateSync',
      gameState: room.gameState
    });

    startTurnTimer(room);
  } else {
    setTimeout(() => {
      passTurn(room);
    }, 600);
  }
}

/**
 * Start Match once room has enough players
 */
function launchRoomMatch(room) {
  if (room.status === 'IN_PROGRESS') return;
  room.status = 'IN_PROGRESS';

  // Build initial GameState
  room.gameState = {
    gameId: `ROOM-${room.pin}`,
    mode: 'ONLINE',
    players: room.players.map((p, idx) => ({
      id: p.id,
      name: p.name,
      color: p.color,
      type: 'HUMAN',
      tokens: [
        { id: 0, color: p.color, stepCount: 0, state: 'HOME_BASE' },
        { id: 1, color: p.color, stepCount: 0, state: 'HOME_BASE' },
        { id: 2, color: p.color, stepCount: 0, state: 'HOME_BASE' },
        { id: 3, color: p.color, stepCount: 0, state: 'HOME_BASE' }
      ],
      rank: 0,
      isHost: idx === 0,
      isReady: true,
      isConnected: true
    })),
    currentPlayerIndex: 0,
    diceValue: null,
    isRolling: false,
    validTokenIds: [],
    consecutiveSixes: 0,
    status: 'IN_PROGRESS',
    turnTimeRemainingSec: 15,
    winnerPodium: [],
    lastMoveDescription: `${room.players[0].name}'s turn. Roll the dice!`,
    aiDifficulty: 'MEDIUM',
    sequenceNumber: 1
  };

  // Send StartGame to each player with their assignedPlayerIndex
  room.players.forEach((p, idx) => {
    sendJson(p.ws, {
      type: 'StartGame',
      initialGameState: room.gameState,
      assignedPlayerIndex: idx
    });
  });

  console.log(`[LudoServer] Room ${room.pin} match started with ${room.players.length} players`);
  startTurnTimer(room);
}

// WebSocket Connection Handler
wss.on('connection', (ws) => {
  const clientId = 'CL_' + Math.random().toString(36).substring(2, 9);
  console.log(`[LudoServer] Client connected: ${clientId}`);

  ws.on('message', (data) => {
    try {
      const msg = JSON.parse(data.toString());
      handleClientMessage(ws, clientId, msg);
    } catch (err) {
      console.error(`[LudoServer] JSON parse error from ${clientId}:`, err.message);
    }
  });

  ws.on('close', () => {
    handleClientDisconnect(ws, clientId);
  });

  ws.on('error', (err) => {
    console.error(`[LudoServer] Socket error for ${clientId}:`, err.message);
  });
});

function handleClientMessage(ws, clientId, msg) {
  const type = msg.type;

  switch (type) {
    case 'Ping': {
      sendJson(ws, { type: 'Pong', timestamp: Date.now() });
      break;
    }

    case 'CreateRoomRequest': {
      // Host creates private room
      const pin = generateRoomPin();
      const count = msg.playerCount === 2 ? 2 : 4;
      const hostColor = msg.color || COLORS.RED;

      const room = {
        pin: pin,
        hostId: clientId,
        playerCount: count,
        players: [{
          id: clientId,
          name: msg.playerName || 'Host',
          color: hostColor,
          ws: ws,
          isHost: true,
          isReady: true,
          isConnected: true
        }],
        status: 'LOBBY',
        gameState: null,
        turnTimer: null
      };

      rooms.set(pin, room);
      clientSessions.set(ws, { roomPin: pin, playerId: clientId });

      sendJson(ws, {
        type: 'RoomCreatedEvent',
        roomPin: pin,
        hostPlayerId: clientId
      });

      sendJson(ws, {
        type: 'LobbyUpdate',
        players: getSanitizedPlayers(room.players),
        hostId: clientId,
        roomPin: pin
      });

      console.log(`[LudoServer] Private room created: ${pin} by ${msg.playerName} (${hostColor})`);
      break;
    }

    case 'JoinRoomRequest': {
      const pin = (msg.roomPin || '').trim();
      const room = rooms.get(pin);

      if (!room) {
        sendJson(ws, { type: 'ErrorMessage', message: `Room PIN ${pin} not found` });
        return;
      }

      if (room.status !== 'LOBBY') {
        sendJson(ws, { type: 'ErrorMessage', message: 'Match is already in progress' });
        return;
      }

      if (room.players.length >= room.playerCount) {
        sendJson(ws, { type: 'ErrorMessage', message: 'Room is full' });
        return;
      }

      // Assign non-conflicting color
      const usedColors = room.players.map(p => p.color);
      let assignedColor = msg.color;

      if (room.playerCount === 2) {
        // Enforce diagonally opposite color in 2P
        assignedColor = OPPOSITE_COLORS[room.players[0].color];
      } else if (!assignedColor || usedColors.includes(assignedColor)) {
        assignedColor = ALL_COLORS.find(c => !usedColors.includes(c)) || COLORS.BLUE;
      }

      room.players.push({
        id: clientId,
        name: msg.playerName || `Player ${room.players.length + 1}`,
        color: assignedColor,
        ws: ws,
        isHost: false,
        isReady: true,
        isConnected: true
      });

      clientSessions.set(ws, { roomPin: pin, playerId: clientId });

      broadcastToRoom(room, {
        type: 'LobbyUpdate',
        players: getSanitizedPlayers(room.players),
        hostId: room.hostId,
        roomPin: pin
      });

      console.log(`[LudoServer] ${msg.playerName} joined room ${pin} as ${assignedColor}`);

      // Auto start if room reached required players
      if (room.players.length === room.playerCount) {
        setTimeout(() => launchRoomMatch(room), 500);
      }
      break;
    }

    case 'QuickMatchRequest': {
      const count = msg.playerCount === 2 ? 2 : 4;
      const queue = quickMatchQueues[count];
      const preferredColor = msg.color || COLORS.BLUE;

      console.log(`[LudoServer] QuickMatch request from ${msg.playerName} for ${count}P`);

      // Check if existing room in queue is open
      let targetRoom = null;
      for (const pin of queue) {
        const r = rooms.get(pin);
        if (r && r.status === 'LOBBY' && r.players.length < r.playerCount) {
          targetRoom = r;
          break;
        }
      }

      if (!targetRoom) {
        // Create new quick match room
        const pin = generateRoomPin();
        targetRoom = {
          pin: pin,
          hostId: clientId,
          playerCount: count,
          players: [{
            id: clientId,
            name: msg.playerName || 'Player 1',
            color: preferredColor,
            ws: ws,
            isHost: true,
            isReady: true,
            isConnected: true
          }],
          status: 'LOBBY',
          gameState: null,
          turnTimer: null
        };

        rooms.set(pin, targetRoom);
        queue.push(pin);
        clientSessions.set(ws, { roomPin: pin, playerId: clientId });

        sendJson(ws, {
          type: 'RoomCreatedEvent',
          roomPin: pin,
          hostPlayerId: clientId
        });

        sendJson(ws, {
          type: 'LobbyUpdate',
          players: getSanitizedPlayers(targetRoom.players),
          hostId: clientId,
          roomPin: pin
        });
      } else {
        // Join existing quick match room
        const usedColors = targetRoom.players.map(p => p.color);
        let assignedColor = preferredColor;

        if (targetRoom.playerCount === 2) {
          assignedColor = OPPOSITE_COLORS[targetRoom.players[0].color];
        } else if (usedColors.includes(assignedColor)) {
          assignedColor = ALL_COLORS.find(c => !usedColors.includes(c)) || COLORS.YELLOW;
        }

        targetRoom.players.push({
          id: clientId,
          name: msg.playerName || `Player ${targetRoom.players.length + 1}`,
          color: assignedColor,
          ws: ws,
          isHost: false,
          isReady: true,
          isConnected: true
        });

        clientSessions.set(ws, { roomPin: targetRoom.pin, playerId: clientId });

        broadcastToRoom(targetRoom, {
          type: 'LobbyUpdate',
          players: getSanitizedPlayers(targetRoom.players),
          hostId: targetRoom.hostId,
          roomPin: targetRoom.pin
        });

        if (targetRoom.players.length === targetRoom.playerCount) {
          // Remove from queue and launch
          const qIdx = queue.indexOf(targetRoom.pin);
          if (qIdx !== -1) queue.splice(qIdx, 1);

          setTimeout(() => launchRoomMatch(targetRoom), 500);
        }
      }
      break;
    }

    case 'RollDiceRequest': {
      const session = clientSessions.get(ws);
      if (!session) return;
      const room = rooms.get(session.roomPin);
      if (!room || room.status !== 'IN_PROGRESS') return;

      handleServerDiceRoll(room, session.playerId, false);
      break;
    }

    case 'MoveTokenRequest': {
      const session = clientSessions.get(ws);
      if (!session) return;
      const room = rooms.get(session.roomPin);
      if (!room || room.status !== 'IN_PROGRESS') return;

      handleServerTokenMove(room, session.playerId, msg.tokenId, false);
      break;
    }

    case 'QuickReaction': {
      const session = clientSessions.get(ws);
      if (!session) return;
      const room = rooms.get(session.roomPin);
      if (room) {
        broadcastToRoom(room, {
          type: 'QuickReaction',
          playerId: session.playerId,
          emojiOrText: msg.emojiOrText
        });
      }
      break;
    }

    default:
      console.log(`[LudoServer] Unhandled message type: ${type}`);
  }
}

function handleClientDisconnect(ws, clientId) {
  const session = clientSessions.get(ws);
  if (!session) return;

  const { roomPin, playerId } = session;
  clientSessions.delete(ws);

  const room = rooms.get(roomPin);
  if (!room) return;

  const player = room.players.find(p => p.id === playerId);
  if (player) {
    player.isConnected = false;
    player.ws = null;
    console.log(`[LudoServer] Player ${player.name} disconnected from room ${roomPin}`);

    broadcastToRoom(room, {
      type: 'PlayerDisconnected',
      playerId: playerId
    });
  }

  // If all disconnected, clean up room after 2 minutes
  const anyConnected = room.players.some(p => p.isConnected);
  if (!anyConnected) {
    if (room.turnTimer) clearInterval(room.turnTimer);
    setTimeout(() => {
      const stillEmpty = !room.players.some(p => p.isConnected);
      if (stillEmpty) {
        rooms.delete(roomPin);
        console.log(`[LudoServer] Room ${roomPin} cleaned up`);
      }
    }, 120000);
  }
}
