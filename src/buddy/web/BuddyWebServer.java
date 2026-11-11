package buddy.web;

import buddy.commands.Commands;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

public class BuddyWebServer {

  private final Commands commands;
  private HttpServer server;

  public BuddyWebServer(Commands commands) {
    this.commands = commands;
  }

  public void start() throws IOException {
    server = HttpServer.create(new InetSocketAddress(8080), 0);
    server.setExecutor(Executors.newFixedThreadPool(8));

    server.createContext("/", this::handleRoot);
    server.createContext("/api/command", this::handleCommand);
    server.createContext("/api/status", this::handleStatus);
    server.createContext("/api/dashboard", this::handleDashboard);

    server.start();
  }

  private void handleRoot(HttpExchange exchange) throws IOException {
    byte[] html = loadHtml();
    exchange.getResponseHeaders().add("Content-Type", "text/html; charset=UTF-8");
    exchange.sendResponseHeaders(200, html.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(html);
    }
  }

  private void handleCommand(HttpExchange exchange) throws IOException {
    Map<String, String> params = parseForm(exchange);
    String raw = params.getOrDefault("text", "").trim();

    if (raw.isEmpty()) {
      sendJson(exchange, 400, "{\"ok\":false,\"message\":\"Command is empty\"}");
      return;
    }

    try {
      String result = commands.process(raw);
      sendJson(exchange, 200, "{\"ok\":true,\"response\":\"" + jsonEscape(result) + "\"}");
    } catch (Exception e) {
      sendJson(exchange, 500, "{\"ok\":false,\"message\":\"" + jsonEscape(e.getMessage()) + "\"}");
    }
  }

  private void handleStatus(HttpExchange exchange) throws IOException {
    String body = "{\"ok\":true,\"status\":\"online\",\"voice\":\"ready\"}";
    sendJson(exchange, 200, body);
  }

  private void handleDashboard(HttpExchange exchange) throws IOException {
    String query = exchange.getRequestURI().getRawQuery();
    String section = "home";
    if (query != null) {
      for (String pair : query.split("&")) {
        String[] parts = pair.split("=", 2);
        if (parts.length == 2 && parts[0].equals("section")) {
          section = java.net.URLDecoder.decode(parts[1], StandardCharsets.UTF_8);
          break;
        }
      }
    }

    List<String> items = new ArrayList<>();
    String title;
    try {
      switch (section) {
        case "activity":
          title = "Recent activity";
          items = readRecentLines("buddy_activity.txt", 40);
          break;
        case "memory":
          title = "Saved memory";
          items = readRecentLines("buddy_user_memory.txt", 100);
          break;
        case "web":
          title = "Saved websites";
          items = readRecentLines("buddy_websites.txt", 100);
          break;
        case "files":
          title = "Quick access";
          items = List.of("Desktop", "Downloads", "Documents", "Pictures", "Music", "Videos");
          break;
        case "ai":
          title = "AI Core";
          items = List.of("Buddy command engine — online", "Voice input — browser microphone", "Voice replies — browser speech");
          break;
        default:
          sendJson(exchange, 400, "{\"ok\":false,\"message\":\"Unknown dashboard section\"}");
          return;
      }
    } catch (IOException e) {
      sendJson(exchange, 500, "{\"ok\":false,\"message\":\"Could not load dashboard data: "
          + jsonEscape(e.getMessage()) + "\"}");
      return;
    }

    StringBuilder json = new StringBuilder("{\"ok\":true,\"title\":\"")
        .append(jsonEscape(title)).append("\",\"items\":[");
    for (int i = 0; i < items.size(); i++) {
      if (i > 0) {
        json.append(',');
      }
      json.append('"').append(jsonEscape(items.get(i))).append('"');
    }
    json.append("]}");
    sendJson(exchange, 200, json.toString());
  }

  private List<String> readRecentLines(String fileName, int limit) throws IOException {
    Path path = Path.of(fileName);
    if (!Files.exists(path)) {
      return List.of();
    }
    List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
    int start = Math.max(0, lines.size() - limit);
    List<String> recent = new ArrayList<>(lines.subList(start, lines.size()));
    java.util.Collections.reverse(recent);
    return recent;
  }

  private Map<String, String> parseForm(HttpExchange exchange) throws IOException {
    String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    if (body == null || body.isEmpty()) {
      return Map.of();
    }

    String[] pairs = body.split("&");
    java.util.Map<String, String> map = new java.util.HashMap<>();
    for (String pair : pairs) {
      String[] kv = pair.split("=", 2);
      if (kv.length == 2) {
        map.put(java.net.URLDecoder.decode(kv[0], StandardCharsets.UTF_8),
            java.net.URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
      }
    }
    return map;
  }

  private void sendJson(HttpExchange exchange, int statusCode, String json) throws IOException {
    byte[] body = json.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
    exchange.sendResponseHeaders(statusCode, body.length);
    try (OutputStream os = exchange.getResponseBody()) {
      os.write(body);
    }
  }

  private String jsonEscape(String value) {
    if (value == null) {
      return "";
    }
    return value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", " ")
        .replace("\r", " ")
        .replace("\t", " ");
  }

  private byte[] loadHtml() {
    String html = """
        <!doctype html>
        <html lang="en">
        <head>
          <meta charset="UTF-8" />
          <meta name="viewport" content="width=device-width, initial-scale=1" />
          <title>Buddy — Voice Assistant</title>
          <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/css/bootstrap.min.css" rel="stylesheet" />
          <style>
            :root {
              --bg: #020c14;
              --bg-2: #031521;
              --panel: rgba(6, 21, 30, 0.9);
              --panel-2: rgba(10, 28, 38, 0.8);
              --line: rgba(120, 180, 240, 0.18);
              --line-strong: rgba(128, 206, 255, 0.38);
              --sky: #77e1ff;
              --cyan: #4ad8ff;
              --teal: #74f3d7;
              --text: #ebf8ff;
              --muted: #8aaec2;
              --muted-2: #6a8ca8;
              --warning: #ffcf6f;
              --danger: #ff8da3;
              --on: #86f4bb;
            }

            * { box-sizing: border-box; }

            html, body {
              margin: 0;
              min-height: 100%;
              background: #010b13;
              color: var(--text);
              font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif;
            }

            body {
              background:
                radial-gradient(circle at 20% 10%, rgba(40, 128, 185, 0.22), transparent 25%),
                radial-gradient(circle at 80% 0%, rgba(44, 152, 191, 0.18), transparent 28%),
                linear-gradient(180deg, #020d16 0%, #020d15 100%);
              padding: 16px 18px 12px;
            }

            .app-shell {
              width: 100%;
              max-width: 1600px;
              margin: 0 auto;
              height: calc(100vh - 32px);
              min-height: 820px;
              background: rgba(2, 12, 19, 0.92);
              border: 1px solid rgba(120, 180, 240, 0.24);
              box-shadow: 0 0 0 1px rgba(15, 40, 58, 0.9), 0 12px 40px rgba(0, 0, 0, 0.4);
              overflow: hidden;
            }

            .topbar {
              height: 68px;
              display: flex;
              align-items: center;
              justify-content: space-between;
              padding: 0 18px 0 14px;
              border-bottom: 1px solid rgba(120, 180, 240, 0.18);
              background: rgba(3, 17, 25, 0.9);
            }

            .brand-wrap {
              display: flex;
              align-items: center;
              gap: 14px;
              min-width: 0;
            }

            .brand-icon {
              width: 26px;
              height: 26px;
              border-radius: 8px;
              background: linear-gradient(180deg, #27b5ef 0%, #0b6ec1 100%);
              display: grid;
              place-items: center;
              font-weight: 800;
              color: white;
              font-size: 18px;
              box-shadow: 0 0 18px rgba(82, 220, 255, 0.5);
            }

            .brand-name {
              font-size: 2rem;
              font-weight: 800;
              letter-spacing: 0.08em;
              color: #eaf9ff;
              line-height: 1;
              display: flex;
              align-items: center;
            }

            .brand-sub {
              color: var(--muted);
              font-size: 0.7rem;
              letter-spacing: 0.4em;
              text-transform: uppercase;
              margin-left: 12px;
              opacity: 0.8;
            }

            .topbar-right {
              display: flex;
              align-items: center;
              gap: 12px;
              color: var(--text);
            }

            .ready-pill {
              display: inline-flex;
              align-items: center;
              justify-content: center;
              min-width: 84px;
              padding: 6px 12px;
              border-radius: 999px;
              background: rgba(86, 214, 160, 0.15);
              border: 1px solid rgba(100, 223, 165, 0.5);
              color: #7efac7;
              font-size: 0.74rem;
              letter-spacing: 0.2em;
              text-transform: uppercase;
              font-weight: 700;
            }

            .clock {
              color: var(--text);
              font-size: 0.8rem;
              letter-spacing: 0.18em;
              font-weight: 700;
              white-space: nowrap;
            }

            .tiny-icons {
              display: flex;
              align-items: center;
              gap: 8px;
            }

            .tiny-box {
              width: 18px;
              height: 18px;
              border: 1px solid rgba(135, 184, 220, 0.45);
              border-radius: 4px;
              display: grid;
              place-items: center;
              color: var(--muted);
              font-size: 0.56rem;
            }

            .workspace {
              display: flex;
              height: calc(100% - 68px);
              min-height: 0;
            }

            .sidebar {
              width: 220px;
              background: rgba(3, 15, 22, 0.92);
              border-right: 1px solid rgba(120, 180, 240, 0.18);
              padding: 22px 14px 16px;
              display: flex;
              flex-direction: column;
              justify-content: space-between;
            }

            .sidebar h2 {
              margin: 0 0 20px;
              font-size: 1.1rem;
              letter-spacing: 0.22em;
              font-weight: 800;
              color: var(--sky);
            }

            .nav-list {
              display: flex;
              flex-direction: column;
              gap: 8px;
            }

            .nav-item {
              display: flex;
              align-items: center;
              width: 100%;
              min-height: 32px;
              padding: 9px 10px;
              border-radius: 6px;
              color: var(--text);
              font-size: 0.8rem;
              letter-spacing: 0.22em;
              text-transform: uppercase;
              opacity: 0.8;
              border: 1px solid transparent;
            }

            .nav-item.active {
              background: rgba(18, 32, 41, 0.9);
              border: 1px solid rgba(125, 186, 255, 0.16);
              box-shadow: inset 0 0 14px rgba(114, 182, 255, 0.04);
            }

            .sidebar-footer {
              display: flex;
              flex-direction: column;
              align-items: flex-start;
              gap: 10px;
              font-size: 0.72rem;
              letter-spacing: 0.12em;
              color: var(--muted);
              text-transform: uppercase;
              padding-top: 12px;
              border-top: 1px solid rgba(120, 180, 240, 0.15);
            }

            .sidebar-footer-status {
              display: flex;
              align-items: center;
              gap: 8px;
            }

            .sidebar-signature {
              color: #b7a4d5;
              font-size: 0.68rem;
              letter-spacing: 0.04em;
              text-transform: none;
            }

            .dot {
              width: 9px;
              height: 9px;
              border-radius: 50%;
              background: #7ef7be;
              box-shadow: 0 0 12px rgba(126, 247, 190, 0.9);
            }

            .content {
              flex: 1;
              min-width: 0;
              display: flex;
              flex-direction: column;
              background: rgba(4, 19, 26, 0.95);
            }

            .main-panel {
              flex: 1;
              display: grid;
              grid-template-columns: minmax(0, 3.3fr) minmax(180px, 0.7fr);
              gap: 18px;
              padding: 18px 18px 12px 18px;
              min-height: 0;
            }

            .hero-area {
              display: flex;
              flex-direction: column;
              min-width: 0;
            }

            .hero {
              position: relative;
              flex: 1;
              min-height: 420px;
              border: 1px solid rgba(120, 180, 240, 0.16);
              background: linear-gradient(180deg, rgba(1, 27, 40, 0.85), rgba(3, 18, 29, 0.8));
              overflow: hidden;
              --tilt-x: 0deg;
              --tilt-y: 0deg;
            }

            .hero::before {
              content: "";
              position: absolute;
              inset: 0;
              background:
                radial-gradient(circle at center, rgba(44, 122, 159, 0.3), transparent 38%),
                linear-gradient(180deg, rgba(3, 12, 17, 0.2), rgba(5, 15, 20, 0.8));
            }

            .orb-wrap {
              position: absolute;
              left: 50%;
              top: 52%;
              width: 420px;
              height: 420px;
              transform: translate(-50%, -50%);
              display: grid;
              place-items: center;
              z-index: 2;
            }

            .orb {
              position: relative;
              width: 100%;
              height: 100%;
              border-radius: 50%;
              background: radial-gradient(circle at 50% 50%, rgba(76, 214, 255, 0.75), rgba(17, 74, 104, 0.8) 38%, rgba(3, 17, 25, 0.95) 80%);
              border: 1px solid rgba(110, 185, 255, 0.35);
              box-shadow: 0 0 40px rgba(85, 214, 255, 0.32), inset 0 0 40px rgba(121, 224, 255, 0.18);
              transform-style: preserve-3d;
              transform: rotateX(0deg) rotateY(0deg);
            }

            .orb::before {
              content: "";
              position: absolute;
              inset: 18px 22px 18px 22px;
              border-radius: 50%;
              border: 1px solid rgba(116, 227, 255, 0.28);
              background: transparent;
              transform: rotateY(10deg) translateZ(10px);
            }

            .orb::after {
              content: "JARVIS";
              position: absolute;
              left: 50%;
              top: 50%;
              transform: translate(-50%, -50%);
              color: var(--text);
              font-size: 2.2rem;
              font-weight: 800;
              letter-spacing: 0.22em;
              text-shadow: 0 0 18px rgba(128, 226, 255, 0.7);
            }

            .orb-ring {
              position: absolute;
              inset: 20px;
              border-radius: 50%;
              border: 1px solid rgba(144, 206, 255, 0.2);
              transform: rotateX(72deg);
              z-index: 1;
            }

            .orb-ring::before, .orb-ring::after {
              content: "";
              position: absolute;
              inset: 36px;
              border: 1px solid rgba(144, 206, 255, 0.15);
              border-radius: 50%;
            }

            .orb-ring::after {
              inset: 72px;
            }

            .status-label {
              position: absolute;
              left: 50%;
              bottom: 78px;
              transform: translateX(-50%);
              z-index: 4;
              text-align: center;
            }

            .status-label .ready {
              font-size: 2rem;
              font-weight: 700;
              color: var(--text);
              letter-spacing: 0.06em;
            }

            .status-label .sub {
              margin-top: 4px;
              font-size: 0.95rem;
              color: var(--muted);
              letter-spacing: 0.02em;
            }

            .hero-bottom {
              display: flex;
              align-items: center;
              justify-content: space-between;
              gap: 14px;
              margin-top: 14px;
              min-height: 42px;
            }

            .voice-bar {
              display: flex;
              align-items: center;
              gap: 8px;
              flex: 1;
              min-width: 0;
            }

            .voice-line {
              height: 4px;
              width: 100%;
              border-radius: 999px;
              background: linear-gradient(90deg, rgba(88, 199, 255, 0.22), rgba(88, 199, 255, 0.8), rgba(88, 199, 255, 0.22));
              overflow: hidden;
              position: relative;
            }

            .voice-line::before {
              content: "";
              position: absolute;
              inset: 0;
              background: linear-gradient(90deg, rgba(255,255,255,0), rgba(255,255,255,0.8), rgba(255,255,255,0));
              transform: translateX(-100%);
              animation: shimmer 2.2s linear infinite;
            }

            @keyframes shimmer {
              100% { transform: translateX(100%); }
            }

            .right-panel {
              display: flex;
              flex-direction: column;
              gap: 12px;
              min-width: 0;
            }

            .mini-box {
              border: 1px solid rgba(120, 180, 240, 0.18);
              background: rgba(5, 17, 25, 0.85);
              min-height: 120px;
              padding: 12px 12px 10px;
            }

            .mini-box h4 {
              margin: 0 0 10px;
              font-size: 0.7rem;
              letter-spacing: 0.22em;
              text-transform: uppercase;
              color: var(--muted);
            }

            .mini-box .metric {
              font-size: 1.05rem;
              font-weight: 700;
              color: var(--text);
              margin-bottom: 3px;
            }

            .mini-box .metric small {
              color: var(--muted);
              font-weight: 600;
            }

            .mini-box .metric-row {
              display: flex;
              justify-content: space-between;
              align-items: center;
              margin-bottom: 4px;
              color: var(--muted);
              font-size: 0.75rem;
            }

            .live-card {
              flex: 1;
              min-height: 0;
              padding: 12px 12px 10px;
              display: flex;
              flex-direction: column;
            }

            .live-list {
              list-style: none;
              margin: 0;
              padding: 0;
              display: flex;
              flex-direction: column;
              gap: 8px;
              color: var(--muted);
              font-size: 0.78rem;
              line-height: 1.4;
            }

            .live-list li {
              padding-left: 10px;
              position: relative;
            }

            .live-list li::before {
              content: "";
              position: absolute;
              left: 0;
              top: 8px;
              width: 5px;
              height: 5px;
              border-radius: 50%;
              background: rgba(112, 220, 255, 0.8);
            }

            .lower-grid {
              display: grid;
              grid-template-columns: 1.2fr 1.2fr 1.2fr;
              gap: 14px;
              padding: 0 18px 14px;
              min-height: 0;
            }

            .panel {
              border: 1px solid rgba(120, 180, 240, 0.18);
              background: rgba(5, 17, 25, 0.9);
              min-height: 170px;
              padding: 12px 12px 10px;
            }

            .panel-header {
              display: flex;
              align-items: center;
              justify-content: space-between;
              margin-bottom: 10px;
            }

            .panel-title {
              color: var(--sky);
              font-size: 0.7rem;
              letter-spacing: 0.22em;
              text-transform: uppercase;
              font-weight: 700;
            }

            .panel-link {
              color: var(--muted);
              font-size: 0.64rem;
              letter-spacing: 0.12em;
              text-transform: uppercase;
            }

            .agent-grid {
              display: grid;
              grid-template-columns: repeat(2, minmax(0, 1fr));
              gap: 8px;
              margin-top: 8px;
            }

            .agent-card {
              border: 1px solid rgba(120, 180, 240, 0.12);
              background: rgba(8, 21, 31, 0.7);
              border-radius: 8px;
              padding: 8px 10px;
              display: flex;
              align-items: center;
              justify-content: space-between;
              gap: 8px;
              min-height: 52px;
            }

            .agent-card[data-command] {
              cursor: pointer;
              transition: transform 140ms ease, filter 140ms ease, box-shadow 140ms ease;
            }

            .agent-card .left {
              display: flex;
              align-items: center;
              flex: 1;
              min-width: 0;
              gap: 8px;
            }

            .agent-card .left > span {
              min-width: 0;
              overflow-wrap: anywhere;
            }

            .agent-card .status-tag { flex: 0 0 auto; }

            .small-icon {
              width: 20px;
              height: 20px;
              border-radius: 50%;
              background: rgba(122, 214, 255, 0.25);
              border: 1px solid rgba(122, 214, 255, 0.5);
            }

            .status-tag {
              font-size: 0.58rem;
              letter-spacing: 0.12em;
              text-transform: uppercase;
              color: #66f4c3;
            }

            .wave-bars {
              height: 28px;
              display: flex;
              align-items: end;
              justify-content: space-between;
              gap: 4px;
              margin-top: 8px;
            }

            .wave-bars span {
              width: 8px;
              border-radius: 999px;
              background: linear-gradient(180deg, rgba(120, 226, 255, 0.9), rgba(100, 255, 205, 0.5));
              animation: bar 1.2s ease-in-out infinite;
            }

            .wave-bars span:nth-child(1) { height: 12%; animation-delay: 0.1s; }
            .wave-bars span:nth-child(2) { height: 30%; animation-delay: 0.2s; }
            .wave-bars span:nth-child(3) { height: 52%; animation-delay: 0.3s; }
            .wave-bars span:nth-child(4) { height: 68%; animation-delay: 0.4s; }
            .wave-bars span:nth-child(5) { height: 42%; animation-delay: 0.5s; }
            .wave-bars span:nth-child(6) { height: 76%; animation-delay: 0.6s; }
            .wave-bars span:nth-child(7) { height: 52%; animation-delay: 0.7s; }
            .wave-bars span:nth-child(8) { height: 64%; animation-delay: 0.8s; }
            @keyframes bar {
              0%, 100% { transform: scaleY(0.7); opacity: 0.65; }
              50% { transform: scaleY(1.08); opacity: 1; }
            }

            .small-panel-grid {
              display: grid;
              grid-template-columns: repeat(3, minmax(0, 1fr));
              gap: 10px;
              margin-top: 10px;
            }

            .tiny-stat {
              padding: 10px 6px 0;
              text-align: center;
              border: 1px solid rgba(120, 180, 240, 0.12);
              background: rgba(8, 21, 31, 0.6);
              border-radius: 8px;
            }

            .tiny-stat strong {
              display: block;
              font-size: 1.1rem;
              color: var(--text);
            }

            .tiny-stat span {
              font-size: 0.62rem;
              letter-spacing: 0.14em;
              text-transform: uppercase;
              color: var(--muted);
            }

            .bottom-bar {
              height: 56px;
              border-top: 1px solid rgba(120, 180, 240, 0.18);
              display: flex;
              align-items: center;
              justify-content: space-between;
              padding: 0 14px;
              background: rgba(2, 11, 16, 0.85);
            }

            .talk-box {
              display: flex;
              align-items: center;
              justify-content: space-between;
              width: 100%;
              min-height: 42px;
              padding: 0 18px;
              border-radius: 12px;
              border: 1px solid rgba(114, 204, 255, 0.24);
              background: rgba(5, 24, 36, 0.7);
              color: var(--text);
              font-size: 0.8rem;
              letter-spacing: 0.2em;
              text-transform: uppercase;
            }

            .talk-box .right {
              display: flex;
              align-items: center;
              gap: 12px;
            }

            .icon-button {
              width: 26px;
              height: 26px;
              border-radius: 8px;
              display: grid;
              place-items: center;
              border: 1px solid rgba(114, 204, 255, 0.24);
              background: rgba(8, 22, 30, 0.8);
              color: var(--sky);
              font-size: 0.72rem;
            }

            .command-form {
              display: flex;
              align-items: stretch;
              gap: 8px;
              margin-top: 10px;
            }

            .command-input {
              flex: 1;
              min-width: 0;
              background: rgba(9, 18, 25, 0.9);
              border: 1px solid rgba(120, 180, 240, 0.3);
              color: var(--text);
              border-radius: 999px;
              padding: 10px 14px;
              outline: none;
            }

            .command-input:focus {
              border-color: var(--cyan);
              box-shadow: 0 0 0 3px rgba(74, 216, 255, 0.14);
            }

            .send-button {
              min-width: 84px;
              padding: 0 16px;
              background: linear-gradient(180deg, #0f6075, #104465);
              border: 1px solid rgba(137, 227, 255, 0.35);
              border-radius: 999px;
              color: white;
              font-weight: 700;
              cursor: pointer;
              transition: transform 140ms ease, filter 140ms ease, box-shadow 140ms ease;
            }

            .send-button:hover:not(:disabled),
            .agent-card[data-command]:hover {
              transform: translateY(-2px);
              filter: brightness(1.2);
              box-shadow: 0 5px 16px rgba(74, 216, 255, 0.2);
            }

            .send-button:focus-visible,
            .agent-card[data-command]:focus-visible {
              outline: 2px solid var(--cyan);
              outline-offset: 3px;
            }

            .send-button:disabled {
              cursor: wait;
              opacity: 0.65;
            }

            .chat-box {
              height: 160px;
              border: 1px solid rgba(120, 180, 240, 0.15);
              background: rgba(255, 255, 255, 0.03);
              border-radius: 8px;
              overflow: auto;
              padding: 14px;
              color: var(--muted);
            }

            .message + .message { margin-top: 10px; }
            .message.user { color: var(--text); }
            .message.error { color: var(--danger); }

            @media (max-width: 1100px) {
              .main-panel {
                grid-template-columns: 1fr;
              }
              .right-panel {
                display: grid;
                grid-template-columns: repeat(2, minmax(0, 1fr));
              }
              .lower-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
              .lower-grid > .panel:first-child { grid-column: 1 / -1; }
            }

            @media (min-width: 821px) and (max-height: 850px) {
              body { padding: 8px; }
              .app-shell {
                height: auto;
                min-height: calc(100vh - 16px);
                overflow: visible;
              }
              .workspace { height: auto; min-height: calc(100vh - 84px); }
              .content { height: auto; }
              .main-panel { flex: 0 0 auto; min-height: auto; }
            }

            @media (max-width: 820px) {
              body { padding: 0; }
              .app-shell {
                height: auto;
                min-height: 100vh;
                overflow: visible;
                border: 0;
              }
              .topbar { min-height: 60px; height: auto; padding: 10px 12px; gap: 8px; }
              .brand-wrap { gap: 8px; }
              .brand-name { font-size: 1.4rem; }
              .brand-sub { margin-left: 0; letter-spacing: 0.1em; }
              .topbar-right { gap: 7px; }
              .ready-pill { min-width: 0; padding: 5px 8px; }
              .clock { font-size: 0.7rem; letter-spacing: 0; }
              .tiny-icons { display: none; }
              .workspace { display: flex; height: auto; min-height: calc(100vh - 60px); flex-direction: column; }
              .sidebar {
                width: 100%;
                padding: 8px 12px;
                flex-direction: row;
                align-items: center;
                gap: 10px;
                border-right: 0;
                border-bottom: 1px solid rgba(120, 180, 240, 0.18);
              }
              .sidebar h2 { display: none; }
              .sidebar > div:first-child { flex: 1; min-width: 0; }
              .nav-list { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 4px; }
              .nav-item { justify-content: center; width: auto; min-height: 36px; padding: 8px 4px; text-align: center; white-space: normal; letter-spacing: 0.08em; }
              .sidebar-footer { display: none; }
              .content { width: 100%; }
              .main-panel { padding: 12px; gap: 12px; }
              .hero { min-height: 330px; }
              .orb-wrap { width: min(64vw, 300px); height: min(64vw, 300px); }
              .status-label { bottom: 28px; width: calc(100% - 24px); }
              .status-label .ready { font-size: 1.5rem; }
              .status-label .sub { font-size: 0.85rem; }
              .right-panel { grid-template-columns: repeat(2, minmax(0, 1fr)); }
              .mini-box { min-height: 96px; }
              .live-card { grid-column: 1 / -1; }
              .lower-grid { grid-template-columns: minmax(0, 1fr); gap: 10px; padding: 0 12px 12px; }
              .lower-grid > .panel:first-child { grid-column: auto; }
              .panel { min-width: 0; min-height: 0; }
              .bottom-bar { height: auto; min-height: 56px; padding: 8px 12px; }
              .talk-box { padding: 8px 12px; letter-spacing: 0.08em; }
            }

            @media (max-width: 420px) {
              .brand-icon { display: none; }
              .brand-name { font-size: 1.15rem; }
              .brand-sub { font-size: 0.58rem; }
              .ready-pill { font-size: 0.64rem; letter-spacing: 0.08em; }
              .nav-item { font-size: 0.62rem; letter-spacing: 0.02em; }
              .right-panel { grid-template-columns: 1fr 1fr; }
              .command-form { align-items: stretch; }
              .send-button { min-width: 68px; padding: 0 10px; }
            }

            body { padding: 0; background: #0c0d12; color: #f2f2f7; }
            .app-shell { max-width: none; height: 100vh; min-height: 0; border: 0; background: #0c0d12; box-shadow: none; }
            .topbar { flex: 0 0 64px; height: 64px; padding: 0 26px; background: #101116; border-color: #24252d; }
            .brand-icon { border-radius: 50%; background: linear-gradient(140deg, #d6b7ff, #835bdb); color: #15111e; }
            .brand-name { font-size: 1.15rem; letter-spacing: .12em; }
            .brand-sub { margin-left: 10px; letter-spacing: .12em; }
            .ready-pill { text-transform: none; letter-spacing: .04em; font-weight: 600; }
            .workspace { flex: 1; height: auto; min-height: 0; }
            .sidebar { width: 218px; padding: 24px 12px 16px; background: #101116; border-color: #24252d; }
            .sidebar h2 { color: #bda2f2; font-size: .72rem; letter-spacing: .18em; margin: 0 0 14px 12px; }
            .nav-list { gap: 5px; }
            .nav-item { justify-content: flex-start; gap: 12px; min-height: 42px; padding: 9px 12px; border-radius: 9px; color: #c3c2cc; font-size: .76rem; letter-spacing: .04em; text-transform: none; opacity: 1; cursor: pointer; background: transparent; }
            .nav-item:hover { background: #1b1b24; color: #fff; }
            .nav-item.active { background: #24212d; border-color: #383044; color: #eadfff; box-shadow: none; }
            .sidebar-footer { border-color: #24252d; letter-spacing: .04em; }
            .dot { width: 7px; height: 7px; }
            .content { display: grid; grid-template-columns: minmax(0, 1fr) 260px; grid-template-rows: auto minmax(0, 1fr); gap: 16px; padding: 30px clamp(18px, 4vw, 64px); min-height: 0; background: #0c0d12; }
            .content::before { content: "A more natural way to get things done"; grid-column: 1 / -1; color: #f1eff7; font-size: clamp(1.35rem, 2.2vw, 1.8rem); font-weight: 650; letter-spacing: -.025em; padding: 0 2px 6px; align-self: end; }
            .main-panel, .lower-grid { display: contents; }
            .hero-area, .right-panel, .bottom-bar, .lower-grid > .panel:last-child { display: none !important; }
            #commandPanel, #agentsPanel { min-width: 0; min-height: 0; border: 1px solid #292a34; border-radius: 16px; padding: 18px; background: #121319; }
            #commandPanel { grid-column: 1; grid-row: 2; display: flex; flex-direction: column; }
            #agentsPanel { grid-column: 2; grid-row: 2; }
            .panel-header { justify-content: flex-start; gap: 12px; margin-bottom: 14px; }
            .panel-title { color: #e8e4ef; font-size: .95rem; letter-spacing: 0; text-transform: none; }
            .panel-link { margin-left: auto; }
            .assistant-heading { display: flex; align-items: center; gap: 12px; }
            .assistant-heading small { display: block; color: #9594a2; font-size: .75rem; margin-top: 3px; }
            .voice-orb { position: relative; flex: 0 0 42px; width: 42px; height: 42px; border-radius: 50%; display: grid; place-items: center; color: #fff; font-weight: 700; background: radial-gradient(circle at 34% 28%, #f1dcff, #a982ed 34%, #583c9f 68%, #201a38 100%); box-shadow: 0 0 26px #a37be655, inset 0 0 10px #fff5; }
            .voice-orb::before, .voice-orb::after { content: ""; position: absolute; inset: -5px; border: 1px solid #b99be877; border-radius: 50%; animation: orb-spin 7s linear infinite; }
            .voice-orb::after { inset: -9px -3px; border-color: #74d8eb66; transform: rotateX(68deg); animation-duration: 5s; animation-direction: reverse; }
            @keyframes orb-spin { to { transform: rotate(360deg); } }
            .chat-box { flex: 1; min-height: 170px; height: auto; border: 0; border-radius: 0; background: transparent; padding: 8px 4px 16px; color: #c4c3ce; }
            .message { line-height: 1.65; white-space: pre-wrap; overflow-wrap: anywhere; }
            .message + .message { margin-top: 16px; }
            .message.user { margin-left: auto; max-width: 86%; width: fit-content; padding: 10px 14px; border: 1px solid #35313f; border-radius: 15px 15px 4px 15px; background: #24212d; }
            .message.buddy { padding: 3px 4px; color: #e1deea; }
            .message.error { color: #ff9aab; }
            .command-form { flex: 0 0 auto; align-items: center; gap: 9px; margin-top: 8px; padding: 7px; border: 1px solid #393944; border-radius: 16px; background: #191a21; }
            .command-input { min-height: 44px; padding: 10px 8px; border: 0; border-radius: 10px; background: transparent; font-size: .94rem; letter-spacing: 0; }
            .command-input:focus { border: 0; box-shadow: none; }
            .command-input::placeholder { color: #888794; }
            .mic-button, .send-button { flex: 0 0 42px; width: 42px; min-width: 42px; height: 42px; padding: 0; display: grid; place-items: center; border-radius: 12px; cursor: pointer; }
            .mic-button { border: 1px solid #393944; background: #20212a; color: #ded9e9; font-size: 1.05rem; }
            .mic-button:hover, .mic-button.listening { background: #382944; border-color: #a77cda; color: #f5eaff; }
            .send-button { border: 1px solid #c7a6fa; background: #c7a6fa; color: #21182e; font-size: 1.2rem; }
            .send-button:hover:not(:disabled) { transform: translateY(-1px); box-shadow: 0 5px 16px #a57be633; }
            .agent-grid { grid-template-columns: 1fr; gap: 8px; margin-top: 14px; }
            .agent-card { min-height: 54px; border-color: #2b2c36; background: #191a21; }
            .small-icon { background: #8d68b522; border-color: #a780dc77; }
            .status-tag { color: #a2e8c0; letter-spacing: .04em; }
            #sectionView { grid-column: 1 / -1; grid-row: 2; min-width: 0; overflow: auto; padding: 24px; border: 1px solid #292a34; border-radius: 16px; background: #121319; }
            #sectionView[hidden] { display: none; }
            #sectionView h1 { margin: 0 0 8px; font-size: 1.35rem; }
            #sectionView p { color: #a4a2af; }
            .section-items { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 10px; margin-top: 22px; }
            .section-item { display: flex; align-items: center; justify-content: space-between; gap: 12px; min-height: 60px; padding: 13px 15px; border: 1px solid #2c2d37; border-radius: 12px; background: #191a21; color: #e3e1e9; overflow-wrap: anywhere; }
            .section-action { flex: 0 0 auto; border: 1px solid #5b4c6a; border-radius: 8px; padding: 7px 10px; background: #282230; color: #e6d5f8; cursor: pointer; }
            .section-search { display: flex; gap: 8px; max-width: 640px; margin-top: 20px; }
            .section-search input { flex: 1; min-width: 0; padding: 12px; border: 1px solid #393944; border-radius: 10px; background: #191a21; color: #fff; }
            .section-search button { border: 0; border-radius: 10px; padding: 0 18px; background: #c7a6fa; color: #21182e; font-weight: 700; cursor: pointer; }
            .setting-row { display: flex; align-items: center; justify-content: space-between; gap: 14px; max-width: 620px; padding: 16px 0; border-bottom: 1px solid #292a34; }
            .setting-row input { accent-color: #b894ec; width: 18px; height: 18px; }
            @media (max-width: 920px) {
              .app-shell { height: auto; min-height: 100vh; }
              .workspace { flex-direction: column; }
              .sidebar { width: 100%; padding: 8px 12px; flex-direction: row; border-right: 0; border-bottom: 1px solid #24252d; }
              .sidebar h2, .sidebar-footer { display: none; }
              .sidebar > div:first-child { width: 100%; }
              .nav-list { display: flex; flex-direction: row; overflow-x: auto; }
              .nav-item { flex: 0 0 auto; min-height: 38px; }
              .content { grid-template-columns: minmax(0, 1fr); grid-template-rows: auto auto auto; padding: 24px 16px; }
              .content::before { grid-column: 1; }
              #commandPanel, #agentsPanel, #sectionView { grid-column: 1; }
              #commandPanel { grid-row: 2; min-height: 55vh; }
              #agentsPanel { grid-row: 3; }
              #sectionView { grid-row: 2; min-height: 55vh; }
            }
            @media (max-width: 920px) {
              .sidebar-footer {
                position: fixed;
                left: 12px;
                bottom: 12px;
                z-index: 30;
                display: block;
                padding: 0;
                border: 0;
              }
              .sidebar-footer-status { display: none; }
              .sidebar-signature {
                display: block;
                padding: 8px 11px;
                border: 1px solid #37313f;
                border-radius: 10px;
                background: rgba(16, 17, 22, .94);
                box-shadow: 0 6px 24px #0005;
              }
            }
            @media (prefers-reduced-motion: reduce) {
              *, *::before, *::after { animation-duration: .01ms !important; animation-iteration-count: 1 !important; scroll-behavior: auto !important; }
            }
          </style>
        </head>
        <body>
          <div class="app-shell">
            <header class="topbar">
              <div class="brand-wrap">
                <div class="brand-icon">B</div>
                <div class="brand-name">BUDDY</div>
                <div class="brand-sub">Voice assistant</div>
              </div>

              <div class="topbar-right">
                <div class="ready-pill">Ready</div>
                <div class="clock" id="timeLabel">01:17:43 pm</div>
                <div class="tiny-icons">
                  <div class="tiny-box">⚙</div>
                  <div class="tiny-box">▣</div>
                  <div class="tiny-box">×</div>
                </div>
              </div>
            </header>

            <div class="workspace">
              <aside class="sidebar">
                <div>
                  <h2>BUDDY</h2>
                  <div class="nav-list">
                    <button class="nav-item active" type="button" data-section="home">⌂ <span>Home</span></button>
                    <button class="nav-item" type="button" data-section="ai">✦ <span>AI Core</span></button>
                    <button class="nav-item" type="button" data-section="activity">◷ <span>Activity</span></button>
                    <button class="nav-item" type="button" data-section="memory">▤ <span>Memory</span></button>
                    <button class="nav-item" type="button" data-section="files">▣ <span>Files</span></button>
                    <button class="nav-item" type="button" data-section="web">◎ <span>Web</span></button>
                    <button class="nav-item" type="button" data-section="settings">⚙ <span>Settings</span></button>
                  </div>
                </div>

                <div class="sidebar-footer">
                  <div class="sidebar-footer-status">
                    <span class="dot"></span>
                    <span>System online</span>
                  </div>
                  <span class="sidebar-signature">Bunny's personal AI agent</span>
                </div>
              </aside>

              <main class="content">
                <div class="main-panel">
                  <section class="hero-area">
                    <div class="hero" id="heroPanel">
                      <div class="orb-ring"></div>
                      <div class="orb-wrap">
                        <div class="orb"></div>
                      </div>

                      <div class="status-label">
                        <div class="ready" id="buddyGreeting">READY</div>
                        <div class="sub" id="voiceStatusText">Listening for the next command.</div>
                      </div>
                    </div>

                    <div class="hero-bottom">
                      <div class="voice-bar">
                        <div class="voice-line"></div>
                      </div>
                    </div>
                  </section>

                  <aside class="right-panel">
                    <div class="mini-box">
                      <h4>CPU</h4>
                      <div class="metric">18% <small>System load</small></div>
                    </div>
                    <div class="mini-box">
                      <h4>MEM</h4>
                      <div class="metric">42% <small>Memory relays</small></div>
                    </div>
                    <div class="mini-box">
                      <h4>Storage</h4>
                      <div class="metric">67% <small>Drive health</small></div>
                    </div>
                    <div class="mini-box live-card">
                      <h4>Recent activity</h4>
                      <ul class="live-list">
                        <li>[voice] Listening ready</li>
                        <li>[brain] Understanding request</li>
                        <li>[power] Executing action</li>
                        <li>[OK] Ready</li>
                      </ul>
                    </div>
                  </aside>
                </div>

                <div class="lower-grid">
                  <div class="panel" id="commandPanel">
                    <div class="panel-header">
                      <div class="voice-orb" aria-hidden="true">B</div>
                      <div class="assistant-heading"><div class="panel-title">Buddy</div><small id="voiceStatusText">Ready when you are</small></div>
                    </div>
                    <div class="chat-box" id="chatBox" aria-live="polite" aria-relevant="additions">
                    <div class="message buddy" style="margin: 0;">Hi, I’m Buddy. You can type or tap the microphone and talk to me.</div>
                    </div>
                    <form id="commandForm" class="command-form">
                    <button id="micBtn" class="mic-button" type="button" aria-label="Start voice input" title="Start voice input">🎙</button>
                    <input id="commandInput" class="command-input" type="text" autocomplete="off" aria-label="Message Buddy" placeholder="Message Buddy or use your voice..." />
                    <button id="sendBtn" class="send-button" type="submit" aria-label="Send message" title="Send">↑</button>
                    </form>
                  </div>

                  <div class="panel" id="agentsPanel">
                    <div class="panel-header">
                    <div class="panel-title">Active agents</div>
                    <div class="panel-link" id="agentStatus">1 online</div>
                    </div>
                    <div class="agent-grid">
                    <div class="agent-card"><div class="left"><div class="small-icon"></div><span>Buddy assistant</span></div><span class="status-tag">Online</span></div>
                    <div class="agent-card"><div class="left"><div class="small-icon"></div><span>Voice input</span></div><span class="status-tag" id="voiceAgentStatus">Ready</span></div>
                    <div class="agent-card"><div class="left"><div class="small-icon"></div><span>Conversation</span></div><span class="status-tag">Ready</span></div>
                    </div>
                  </div>

                  <div class="panel">
                    <div class="panel-header">
                      <div class="panel-title">Quick commands</div>
                    </div>
                    <div class="agent-grid">
                      <button class="agent-card" data-command="start now task" style="text-align:left; background: rgba(8, 21, 31, 0.7); color: var(--text); border:1px solid rgba(120,180,240,.12); width:100%;"><div class="left"><div class="small-icon"></div><span>Start New Task</span></div></button>
                      <button class="agent-card" data-command="open calendar" style="text-align:left; background: rgba(8, 21, 31, 0.7); color: var(--text); border:1px solid rgba(120,180,240,.12); width:100%;"><div class="left"><div class="small-icon"></div><span>Open Calendar</span></div></button>
                      <button class="agent-card" data-command="search latest news" style="text-align:left; background: rgba(8, 21, 31, 0.7); color: var(--text); border:1px solid rgba(120,180,240,.12); width:100%;"><div class="left"><div class="small-icon"></div><span>Run Workflow</span></div></button>
                      <button class="agent-card" data-command="what is my name" style="text-align:left; background: rgba(8, 21, 31, 0.7); color: var(--text); border:1px solid rgba(120,180,240,.12); width:100%;"><div class="left"><div class="small-icon"></div><span>Ask My Name</span></div></button>
                    </div>
                  </div>
                </div>

                <section id="sectionView" hidden aria-live="polite"></section>
                <div class="bottom-bar">
                  <div class="talk-box">
                    <span>Talk to JARVIS</span>
                    <div class="right">
                      <span style="color: var(--muted); font-size: 0.65rem; letter-spacing: 0.18em; text-transform: uppercase;">I am listening...</span>
                      <span class="icon-button">▶</span>
                    </div>
                  </div>
                </div>
              </main>
            </div>
          </div>

          <script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.3/dist/js/bootstrap.bundle.min.js"></script>
          <script>
            const timeLabel = document.getElementById('timeLabel');
            const buddyGreeting = document.getElementById('buddyGreeting');
            const voiceStatusText = document.getElementById('voiceStatusText');
            const commandForm = document.getElementById('commandForm');
            const commandInput = document.getElementById('commandInput');
            const sendBtn = document.getElementById('sendBtn');
            const chatBox = document.getElementById('chatBox');
            const micBtn = document.getElementById('micBtn');
            const sectionView = document.getElementById('sectionView');
            const mainPanel = document.querySelector('.main-panel');
            const lowerGrid = document.querySelector('.lower-grid');
            const SpeechRecognition = window.SpeechRecognition || window.webkitSpeechRecognition;
            let recognition = null;
            let finalTranscript = '';
            let selectedSection = 'home';

            function addMessage(text, kind) {
              const message = document.createElement('div');
              message.className = 'message ' + kind;
              message.textContent = text;
              chatBox.appendChild(message);
              chatBox.scrollTop = chatBox.scrollHeight;
              return message;
            }

            async function sendCommand(command) {
              const text = command.trim();
              if (!text || sendBtn.disabled) return;

              addMessage('You: ' + text, 'user');
              commandInput.value = '';
              sendBtn.disabled = true;
              sendBtn.textContent = '...';
              voiceStatusText.textContent = 'Working on your request...';
              const responseMessage = addMessage('Buddy is thinking...', 'buddy');

              try {
                const response = await fetch('/api/command', {
                  method: 'POST',
                  headers: { 'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8' },
                  body: new URLSearchParams({ text })
                });
                const data = await response.json();
                if (!response.ok || !data.ok) {
                  throw new Error(data.message || 'The command could not be completed.');
                }
                responseMessage.textContent = data.response || 'Done.';
                voiceStatusText.textContent = 'Request complete.';
                if (localStorage.getItem('buddyVoiceReplies') === 'true' && 'speechSynthesis' in window) {
                  window.speechSynthesis.cancel();
                  window.speechSynthesis.speak(new SpeechSynthesisUtterance(data.response || 'Done.'));
                }
              } catch (error) {
                responseMessage.className = 'message error';
                responseMessage.textContent = error.message || 'Could not reach Buddy.';
                voiceStatusText.textContent = 'Request failed. Please try again.';
              } finally {
                sendBtn.disabled = false;
                sendBtn.textContent = 'Send';
                commandInput.focus();
              }
            }

            commandForm.addEventListener('submit', (event) => {
              event.preventDefault();
              sendCommand(commandInput.value);
            });

            document.querySelectorAll('[data-command]').forEach((button) => {
              button.addEventListener('click', () => sendCommand(button.dataset.command));
            });

            function setActiveSection(section) {
              selectedSection = section;
              document.querySelectorAll('.nav-item[data-section]').forEach((item) => {
                const active = item.dataset.section === section;
                item.classList.toggle('active', active);
                item.setAttribute('aria-current', active ? 'page' : 'false');
              });
              const isHome = section === 'home';
              mainPanel.style.display = isHome ? '' : 'none';
              lowerGrid.style.display = isHome ? '' : 'none';
              sectionView.hidden = isHome;
              if (!isHome) renderSection(section);
            }

            function addSectionItem(container, title, actionLabel, action) {
              const item = document.createElement('div');
              item.className = 'section-item';
              const label = document.createElement('span');
              label.textContent = title;
              item.appendChild(label);
              if (actionLabel && action) {
                const button = document.createElement('button');
                button.className = 'section-action';
                button.type = 'button';
                button.textContent = actionLabel;
                button.addEventListener('click', action);
                item.appendChild(button);
              }
              container.appendChild(item);
            }

            async function renderSection(section) {
              sectionView.replaceChildren();
              const heading = document.createElement('h1');
              heading.textContent = ({ ai: 'AI Core', activity: 'Activity', memory: 'Memory', files: 'Files', web: 'Web', settings: 'Settings' })[section] || 'Buddy';
              sectionView.appendChild(heading);
              const description = document.createElement('p');
              description.textContent = section === 'files' ? 'Open a common folder with Buddy.' :
                section === 'web' ? 'Search the web through Buddy or review saved websites.' :
                section === 'settings' ? 'Choose how voice input and spoken replies work in this browser.' :
                section === 'activity' ? 'Your latest requests and results.' :
                section === 'memory' ? 'Information Buddy has saved for future conversations.' :
                'Live services available in this Buddy session.';
              sectionView.appendChild(description);

              if (section === 'settings') {
                const voiceRow = document.createElement('label');
                voiceRow.className = 'setting-row';
                voiceRow.textContent = 'Speak Buddy replies aloud';
                const voiceToggle = document.createElement('input');
                voiceToggle.type = 'checkbox';
                voiceToggle.checked = localStorage.getItem('buddyVoiceReplies') === 'true';
                voiceToggle.addEventListener('change', () => localStorage.setItem('buddyVoiceReplies', String(voiceToggle.checked)));
                voiceRow.appendChild(voiceToggle);
                sectionView.appendChild(voiceRow);
                const recognitionRow = document.createElement('div');
                recognitionRow.className = 'setting-row';
                recognitionRow.textContent = SpeechRecognition ? 'Voice recognition is supported in this browser.' : 'Voice recognition is not supported in this browser. Try a current version of Chrome or Edge.';
                sectionView.appendChild(recognitionRow);
                return;
              }

              if (section === 'web') {
                const searchForm = document.createElement('form');
                searchForm.className = 'section-search';
                const searchInput = document.createElement('input');
                searchInput.placeholder = 'What would you like to search?';
                searchInput.setAttribute('aria-label', 'Web search');
                const searchButton = document.createElement('button');
                searchButton.type = 'submit';
                searchButton.textContent = 'Search';
                searchForm.append(searchInput, searchButton);
                searchForm.addEventListener('submit', (event) => {
                  event.preventDefault();
                  const query = searchInput.value.trim();
                  if (query) sendCommand('search the web for ' + query);
                });
                sectionView.appendChild(searchForm);
              }

              const items = document.createElement('div');
              items.className = 'section-items';
              sectionView.appendChild(items);
              if (section === 'settings') return;
              try {
                const response = await fetch('/api/dashboard?section=' + encodeURIComponent(section));
                const data = await response.json();
                if (!response.ok || !data.ok) throw new Error(data.message || 'Could not load this section.');
                heading.textContent = data.title || heading.textContent;
                if (selectedSection !== section) return;
                if (!data.items || data.items.length === 0) {
                  const empty = document.createElement('p');
                  empty.textContent = section === 'activity' ? 'No activity has been recorded yet.' : 'Nothing saved here yet.';
                  items.appendChild(empty);
                } else {
                  data.items.forEach((value) => {
                    if (section === 'files') {
                      addSectionItem(items, value, 'Open', () => sendCommand('open ' + value));
                    } else {
                      addSectionItem(items, value);
                    }
                  });
                }
              } catch (error) {
                const message = document.createElement('p');
                message.className = 'message error';
                message.textContent = error.message || 'Could not load this section.';
                items.appendChild(message);
              }
            }

            document.querySelectorAll('.nav-item[data-section]').forEach((item) => {
              item.addEventListener('click', () => setActiveSection(item.dataset.section));
            });

            if (SpeechRecognition) {
              recognition = new SpeechRecognition();
              recognition.lang = navigator.language || 'en-US';
              recognition.interimResults = true;
              recognition.continuous = false;
              recognition.addEventListener('start', () => {
                finalTranscript = '';
                micBtn.classList.add('listening');
                document.getElementById('voiceAgentStatus').textContent = 'Listening';
                voiceStatusText.textContent = 'Listening... speak now';
              });
              recognition.addEventListener('result', (event) => {
                let interim = '';
                for (let i = event.resultIndex; i < event.results.length; i++) {
                  const transcript = event.results[i][0].transcript;
                  if (event.results[i].isFinal) finalTranscript += transcript;
                  else interim += transcript;
                }
                commandInput.value = finalTranscript || interim;
              });
              recognition.addEventListener('end', () => {
                micBtn.classList.remove('listening');
                document.getElementById('voiceAgentStatus').textContent = 'Ready';
                if (finalTranscript.trim()) {
                  commandInput.value = finalTranscript.trim();
                  sendCommand(finalTranscript.trim());
                } else {
                  voiceStatusText.textContent = 'Ready when you are';
                }
              });
              recognition.addEventListener('error', (event) => {
                micBtn.classList.remove('listening');
                document.getElementById('voiceAgentStatus').textContent = 'Ready';
                voiceStatusText.textContent = event.error === 'not-allowed'
                  ? 'Microphone permission was denied. Allow access in your browser settings.'
                  : 'Voice input failed. You can still type a message.';
              });
            } else {
              micBtn.title = 'Voice recognition is not supported in this browser';
              micBtn.setAttribute('aria-label', micBtn.title);
            }

            micBtn.addEventListener('click', () => {
              if (!recognition) {
                voiceStatusText.textContent = 'Voice recognition is not supported here. Try Chrome or Edge.';
                return;
              }
              if (micBtn.classList.contains('listening')) recognition.stop();
              else {
                try {
                  recognition.start();
                } catch (error) {
                  voiceStatusText.textContent = 'Could not start the microphone. Please try again.';
                }
              }
            });

            function updateClock() {
              const now = new Date();
              const time = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: true });
              timeLabel.textContent = time;
            }

            function updateStaticState() {
              if (buddyGreeting) buddyGreeting.textContent = 'READY';
              if (voiceStatusText) voiceStatusText.textContent = 'Ready when you are';
            }

            setInterval(updateClock, 1000);
            updateClock();
            updateStaticState();
          </script>
        </body>
        </html>
        """;
    return html.getBytes(StandardCharsets.UTF_8);
  }
}