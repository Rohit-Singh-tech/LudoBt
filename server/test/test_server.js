const { spawn } = require('child_process');
const WebSocket = require('ws');
const path = require('path');

const PORT = 8089;
const SERVER_PATH = path.join(__dirname, '..', 'src', 'server.js');

console.log('[Test] Starting server for testing on port', PORT);
const serverProc = spawn('node', [SERVER_PATH], {
  env: { ...process.env, PORT: PORT.toString() },
  stdio: 'pipe'
});

serverProc.stdout.on('data', d => console.log('[Server stdout]', d.toString().trim()));
serverProc.stderr.on('data', d => console.error('[Server stderr]', d.toString().trim()));

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

async function runTests() {
  await delay(1000); // Give server 1 sec to bind

  console.log('[Test] Connecting Player A...');
  const wsA = new WebSocket(`ws://localhost:${PORT}`);
  const messagesA = [];
  wsA.on('message', data => {
    const msg = JSON.parse(data.toString());
    messagesA.push(msg);
    console.log('[Player A received]', msg.type);
  });

  await new Promise(res => wsA.on('open', res));

  // Player A creates 2-player room with RED goti
  console.log('[Test] Player A sending CreateRoomRequest...');
  wsA.send(JSON.stringify({
    type: 'CreateRoomRequest',
    playerCount: 2,
    playerName: 'Alice',
    color: 'RED'
  }));

  await delay(500);
  const roomCreatedMsg = messagesA.find(m => m.type === 'RoomCreatedEvent');
  if (!roomCreatedMsg || !roomCreatedMsg.roomPin) {
    throw new Error('RoomCreatedEvent not received or missing roomPin');
  }
  const pin = roomCreatedMsg.roomPin;
  console.log(`[Test] SUCCESS: Room created with 6-digit PIN: ${pin}`);

  // Connect Player B and Join Room
  console.log('[Test] Connecting Player B...');
  const wsB = new WebSocket(`ws://localhost:${PORT}`);
  const messagesB = [];
  wsB.on('message', data => {
    const msg = JSON.parse(data.toString());
    messagesB.push(msg);
    console.log('[Player B received]', msg.type);
  });

  await new Promise(res => wsB.on('open', res));

  console.log(`[Test] Player B sending JoinRoomRequest with PIN ${pin}...`);
  wsB.send(JSON.stringify({
    type: 'JoinRoomRequest',
    roomPin: pin,
    playerName: 'Bob',
    color: 'YELLOW'
  }));

  await delay(1200);

  // Both should receive StartGame
  const startMsgA = messagesA.find(m => m.type === 'StartGame');
  const startMsgB = messagesB.find(m => m.type === 'StartGame');

  if (!startMsgA || !startMsgB) {
    throw new Error('StartGame event not received by both players');
  }
  console.log('[Test] SUCCESS: StartGame received by both players');
  console.log('[Test] Player A seat:', startMsgA.assignedPlayerIndex, 'Player B seat:', startMsgB.assignedPlayerIndex);

  // First player rolls dice
  const firstPlayerWs = startMsgA.assignedPlayerIndex === 0 ? wsA : wsB;
  const firstPlayerId = startMsgA.assignedPlayerIndex === 0 ? 'CL_Alice' : 'CL_Bob';
  const firstPlayer = startMsgA.initialGameState.players[0];

  console.log(`[Test] Rolling dice for first player: ${firstPlayer.name} (${firstPlayer.id})...`);
  firstPlayerWs.send(JSON.stringify({
    type: 'RollDiceRequest'
  }));

  await delay(500);

  const rollMsg = messagesA.find(m => m.type === 'DiceRolledEvent') || messagesB.find(m => m.type === 'DiceRolledEvent');
  if (!rollMsg) {
    throw new Error('DiceRolledEvent not received');
  }
  console.log(`[Test] SUCCESS: Server Authoritative Dice Roll = ${rollMsg.value}, Valid Tokens: ${JSON.stringify(rollMsg.validTokenIds)}`);

  console.log('[Test] All tests PASSED successfully!');

  wsA.close();
  wsB.close();
  serverProc.kill();
  process.exit(0);
}

runTests().catch(err => {
  console.error('[Test FAILED]', err);
  serverProc.kill();
  process.exit(1);
});
