# 🏏 Raw Cricket

> **Real-Time 2-Player Hand Cricket**  
> Play the classic hand cricket game with anyone, anywhere — in real time.

🔗 **Live:** [raw-cricket.netlify.app](https://raw-cricket.netlify.app)

---

## 📸 Preview

| Batting (Blue Theme) | Bowling (Red Theme) |
|---|---|
| Player batting sees blue UI | Player bowling sees red UI |

---

## 🎮 How to Play

1. Open [raw-cricket.netlify.app](https://raw-cricket.netlify.app)
2. Enter your name and a room code
3. Share the same room code with your friend
4. **Toss** — one player picks Head/Tail, other flips the coin
5. **Toss winner** chooses to Bat or Bowl
6. **Batting player** picks a number (1-6), then bowling player picks a number
7. If numbers **differ** → runs added to batter's score
8. If numbers **match** → batter is OUT, innings switches
9. Second innings player chases the target
10. **Win** by reaching the target, or **Tie** if both score the same

---

## ✨ Features

- 🔴🔵 **Dynamic themes** — blue for batting, red for bowling, switches every innings
- 🪙 **Toss system** — animated coin flip with Head/Tail selection
- ⏱️ **10-second timer** per move with visual countdown bar
- 🎭 **Hidden moves** — both numbers revealed simultaneously (prevents cheating)
- 📊 **Series tracking** — win/loss record persists across rematches in the same room
- 🔄 **Rematch** — play again in the same room without rejoining
- 🤝 **Tie detection** — handles equal scores correctly
- 🚪 **Quit Room** — shows series result on exit
- ⚠️ **Disconnect handling** — notifies remaining player if opponent leaves

---

## 🛠️ Tech Stack

### Backend
| Technology | Purpose |
|---|---|
| Java 17 | Language |
| Spring Boot 3.2 | Framework |
| Spring WebSocket | Real-time communication via `TextWebSocketHandler` |
| Embedded Tomcat | Web server (bundled in JAR) |
| Maven | Build tool |
| Docker | Containerization for deployment |

### Frontend
| Technology | Purpose |
|---|---|
| HTML5 | Structure |
| CSS3 | Styling, themes, animations |
| Vanilla JavaScript (ES6) | WebSocket client, game logic, DOM updates |

### Deployment
| Service | Purpose |
|---|---|
| Render | Backend (Docker container) |
| Netlify | Frontend (static hosting) |

---

## 🏗️ Architecture

```
Browser (Player 1)          Spring Boot Server          Browser (Player 2)
   Netlify          ←──── WebSocket (wss://) ────→         Netlify
                              Render
                          GameHandler.java
                         (all state lives here)
```

Both players connect to the same server via WebSocket. The server holds all game state — clients only send move numbers and render what the server broadcasts back.

**Why WebSocket over REST?**  
REST is request-response — the client must ask before the server can reply. In a real-time game, the server needs to push data to both players simultaneously. WebSocket keeps a persistent two-way connection open for instant bidirectional messaging.

---

## 📁 Project Structure

```
raw-cricket/
├── frontend/
│   └── index.html              # Entire frontend — single file
├── src/main/java/com/handcricket/
│   ├── GameApplication.java    # Spring Boot entry point
│   ├── config/
│   │   └── WebSocketConfig.java  # Registers /game WebSocket endpoint
│   └── handler/
│       └── GameHandler.java    # Game state machine (all logic here)
├── src/main/resources/
│   └── application.properties  # server.port=${PORT:8080}
├── Dockerfile                  # Multi-stage build for Render
└── pom.xml                     # Maven dependencies
```

---

## ⚙️ Core Logic

### Game State Machine
```
JOIN → TOSS (3 phases) → GAME → REMATCH → TOSS ...
```

### Ball-by-Ball Flow
```
1. Batter picks number     → server stores secretly
2. Bowler picks number     → server compares both
3. Numbers differ          → batter scores runs
4. Numbers match           → OUT!
```

### Innings Switch
```java
room.target = batter.score + 1;  // must beat, not match
room.battingIndex = bowlerIndex; // roles swap
```

### Tie Detection
```java
if (batter.score == room.target - 1) → TIE
```

### Key Bug Fixed
`players` was originally a `HashMap` — iteration order not guaranteed, causing random player assignment for toss. Fixed by switching to `ArrayList` which preserves insertion order.

---

## 🚀 Running Locally

**Prerequisites:** Java 17+, Maven

```bash
# Clone
git clone https://github.com/raghavaathreya/raw-cricket.git
cd raw-cricket

# Run backend
mvn spring-boot:run

# Open frontend
# Just open frontend/index.html in your browser
# It auto-connects to localhost:8080 when on localhost
```

---

## 🌐 WebSocket Message Protocol

All messages are plain strings — no JSON, no STOMP.

| Direction | Message | Meaning |
|---|---|---|
| Server → Both | `TOSS_RESULT:HEAD` | Coin flip result |
| Server → Both | `TURN:0:raghav` | Whose turn to play |
| Server → Both | `REVEAL:raghav:4:shreyas:6` | Both moves revealed |
| Server → Both | `SCORE:raghav:10` | Updated score |
| Server → Both | `OUT:raghav` | Player is out |
| Server → Both | `WIN:raghav` | Match winner |
| Server → Both | `TIE` | Match tied |
| Server → One | `SHOW_DECISION` | Toss winner picks BAT/BOWL |
| Server → One | `REMATCH_WAIT` | Opponent wants rematch |
| Client → Server | `1`–`6` | Game move |
| Client → Server | `REMATCH` | Vote for new match |

---

## 📝 License

MIT — free to use, modify, and share.

👤 Author
   
Raghavendra G V
GitHub: @raghavaathreya
