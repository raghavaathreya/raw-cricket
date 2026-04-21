package com.handcricket.handler;

import org.springframework.web.socket.*;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.*;

class Player {
    String id;
    String name;
    int score = 0;

    public Player(String id, String name) {
        this.id = id;
        this.name = name;
    }
}

class Room {
    // Use LinkedList to preserve insertion order (player 0 = first to join)
    List<WebSocketSession> sessions = new ArrayList<>();
    List<Player> players = new ArrayList<>();   // <-- FIXED: was HashMap, order was random

    int battingIndex = 0;

    boolean firstInnings = true;
    int target = 0;

    Integer lastBatterMove = null;
    boolean waitingForBowler = false;

    // toss
    boolean tossChoiceDone = false;
    boolean flipDone = false;
    boolean decisionDone = false;

    String tossChoice = null; // HEAD / TAIL
    int tossWinnerIndex = -1;

    // rematch
    Set<String> rematchVotes = new HashSet<>();

    // helper: get player index by session id
    int indexOf(String sessionId) {
        for (int i = 0; i < players.size(); i++) {
            if (players.get(i).id.equals(sessionId)) return i;
        }
        return -1;
    }
}

public class GameHandler extends TextWebSocketHandler {

    static Map<String, Room> rooms = new HashMap<>();
    static Map<String, String> sessionRoom = new HashMap<>();

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

        String msg = message.getPayload().trim();

        // ── Join phase ──────────────────────────────────────────────────────────
        if (!sessionRoom.containsKey(session.getId())) {
            String[] parts = msg.split("\\|");
            joinRoom(session, parts[0], parts[1]);
            return;
        }

        Room room = rooms.get(sessionRoom.get(session.getId()));

        if (room.sessions.size() < 2) return;

        int playerIndex = room.indexOf(session.getId());

        // ── TOSS ────────────────────────────────────────────────────────────────

        // Player 1 (second to join) picks HEAD / TAIL
        if (!room.tossChoiceDone && playerIndex == 1) {
            room.tossChoice = msg;
            room.tossChoiceDone = true;

            send(room.sessions.get(0), "SHOW_FLIP");
            send(room.sessions.get(1), "WAIT_FLIP");
            return;
        }

        // Player 0 (first to join) flips
        if (room.tossChoiceDone && !room.flipDone && msg.equals("FLIP") && playerIndex == 0) {

            room.flipDone = true;

            String result = new Random().nextBoolean() ? "HEAD" : "TAIL";
            broadcast(room, "TOSS_RESULT:" + result);

            room.tossWinnerIndex = result.equalsIgnoreCase(room.tossChoice) ? 1 : 0;

            Player winner = room.players.get(room.tossWinnerIndex);
            broadcast(room, "TOSS_WIN:" + winner.name);

            send(room.sessions.get(room.tossWinnerIndex), "SHOW_DECISION");
            return;
        }

        // Toss winner chooses BAT / BOWL
        if (room.flipDone && !room.decisionDone && (msg.equals("BAT") || msg.equals("BOWL"))) {

            room.decisionDone = true;

            room.battingIndex = msg.equals("BAT") ? room.tossWinnerIndex : 1 - room.tossWinnerIndex;

            Player batter = room.players.get(room.battingIndex);

            broadcast(room, "DECISION:" + batter.name + " bats first");
            broadcast(room, "START");
            broadcast(room, "TURN:" + room.battingIndex + ":" + batter.name);
            return;
        }

        // ── REMATCH ─────────────────────────────────────────────────────────────

        if (msg.equals("REMATCH")) {
            room.rematchVotes.add(session.getId());

            if (room.rematchVotes.size() == 1) {
                // First vote — tell the other player
                for (WebSocketSession s : room.sessions) {
                    if (!s.getId().equals(session.getId())) {
                        send(s, "REMATCH_WAIT");
                    }
                }
            } else if (room.rematchVotes.size() >= 2) {
                // Both voted — reset room state and start new toss
                room.rematchVotes.clear();
                room.battingIndex    = 0;
                room.firstInnings    = true;
                room.target          = 0;
                room.lastBatterMove  = null;
                room.waitingForBowler = false;
                room.tossChoiceDone  = false;
                room.flipDone        = false;
                room.decisionDone    = false;
                room.tossChoice      = null;
                room.tossWinnerIndex = -1;

                // Reset player scores
                for (Player p : room.players) p.score = 0;

                broadcast(room, "REMATCH_START");

                // Kick off toss fresh — player 1 picks head/tail, player 0 flips
                send(room.sessions.get(1), "SHOW_TOSS_CHOICE");
                send(room.sessions.get(0), "WAIT_TOSS");
            }
            return;
        }

        // ── GAME ────────────────────────────────────────────────────────────────

        // Only the current batter (or bowler) should be sending moves
        int batterIndex  = room.battingIndex;
        int bowlerIndex  = 1 - batterIndex;

        int move;
        try {
            move = Integer.parseInt(msg);
            if (move < 1 || move > 6) return;
        } catch (NumberFormatException e) {
            return;
        }

        Player batter  = room.players.get(batterIndex);
        Player bowler  = room.players.get(bowlerIndex);

        if (!room.waitingForBowler) {
            // Expecting batter's move
            if (playerIndex != batterIndex) return;

            room.lastBatterMove = move;
            room.waitingForBowler = true;

            broadcast(room, "BATTER_PLAYED:" + batter.name);
            // Now bowler's turn
            broadcast(room, "TURN:" + bowlerIndex + ":" + bowler.name);

        } else {
            // Expecting bowler's move
            if (playerIndex != bowlerIndex) return;

            int batterMove = room.lastBatterMove;
            int bowlerMove = move;

            broadcast(room, "REVEAL:" + batter.name + ":" + batterMove + ":" + bowler.name + ":" + bowlerMove);

            if (bowlerMove == batterMove) {
                // OUT
                broadcast(room, "OUT:" + batter.name);

                if (room.firstInnings) {
                    room.target = batter.score + 1;
                    room.firstInnings = false;

                    // Swap innings: bowler becomes new batter
                    room.battingIndex = bowlerIndex;
                    room.waitingForBowler = false;

                    Player newBatter = room.players.get(room.battingIndex);
                    int    newBowlerIndex = 1 - room.battingIndex;

                    broadcast(room, "TARGET:" + room.target);
                    broadcast(room, "TURN:" + room.battingIndex + ":" + newBatter.name);

                } else {
                    // Tie: 2nd innings batter got out with same score as 1st innings batter
                    if (batter.score == room.target - 1) {
                        broadcast(room, "TIE");
                    } else {
                        // Got out below target → 1st innings batter (current bowler) wins
                        Player winner = room.players.get(bowlerIndex);
                        broadcast(room, "WIN:" + winner.name);
                    }
                }

            } else {
                // Not out — add runs
                batter.score += batterMove;

                if (room.firstInnings) {
                    broadcast(room, "SCORE:" + batter.name + ":" + batter.score);
                } else {
                    if (batter.score >= room.target) {
                        broadcast(room, "WIN:" + batter.name);
                        return;
                    }
                    int need = room.target - batter.score;
                    broadcast(room, "SCORE:" + batter.name + ":" + batter.score);
                    broadcast(room, "NEED:" + need);
                }

                // Next ball: batter goes again
                room.waitingForBowler = false;
                broadcast(room, "TURN:" + batterIndex + ":" + batter.name);
            }
        }
    }

    private void joinRoom(WebSocketSession session, String name, String roomId) {

        Room room = rooms.getOrDefault(roomId, new Room());

        // Remove any dead (closed) sessions left over from a previous game
        room.sessions.removeIf(s -> !s.isOpen());
        room.players.removeIf(p -> room.sessions.stream().noneMatch(s -> s.getId().equals(p.id)));

        // If room had a full game before and both left, fully reset state
        if (room.sessions.isEmpty()) {
            room.battingIndex     = 0;
            room.firstInnings     = true;
            room.target           = 0;
            room.lastBatterMove   = null;
            room.waitingForBowler = false;
            room.tossChoiceDone   = false;
            room.flipDone         = false;
            room.decisionDone     = false;
            room.tossChoice       = null;
            room.tossWinnerIndex  = -1;
            room.rematchVotes.clear();
        }

        room.sessions.add(session);
        room.players.add(new Player(session.getId(), name));

        rooms.put(roomId, room);
        sessionRoom.put(session.getId(), roomId);

        if (room.sessions.size() == 2) {
            send(room.sessions.get(1), "SHOW_TOSS_CHOICE");
            send(room.sessions.get(0), "WAIT_TOSS");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String roomId = sessionRoom.remove(session.getId());
        if (roomId == null) return;

        Room room = rooms.get(roomId);
        if (room == null) return;

        // Remove this player from the room
        room.sessions.removeIf(s -> s.getId().equals(session.getId()));
        room.players.removeIf(p -> p.id.equals(session.getId()));
        room.rematchVotes.remove(session.getId());

        // Notify remaining player their opponent left
        if (!room.sessions.isEmpty()) {
            broadcast(room, "OPPONENT_LEFT");
        } else {
            // Both gone — delete the room entirely
            rooms.remove(roomId);
        }
    }

    private void broadcast(Room room, String msg) {
        for (WebSocketSession s : room.sessions) send(s, msg);
    }

    private void send(WebSocketSession s, String msg) {
        try { s.sendMessage(new TextMessage(msg)); } catch (Exception ignored) {}
    }
}