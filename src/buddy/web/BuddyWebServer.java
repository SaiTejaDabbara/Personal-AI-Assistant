package buddy.web;

import buddy.commands.Commands;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
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
          <title>JARVIS Command Center</title>
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
              align-items: center;
              gap: 8px;
              font-size: 0.72rem;
              letter-spacing: 0.12em;
              color: var(--muted);
              text-transform: uppercase;
              padding-top: 12px;
              border-top: 1px solid rgba(120, 180, 240, 0.15);
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

            .agent-card .left {
              display: flex;
              align-items: center;
              gap: 8px;
            }

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

            @media (max-width: 1100px) {
              .main-panel {
                grid-template-columns: 1fr;
              }
              .right-panel {
                display: grid;
                grid-template-columns: repeat(2, minmax(0, 1fr));
              }
            }

            @media (max-width: 820px) {
              .workspace { display: block; }
              .sidebar { width: 100%; }
              .brand-name { font-size: 1.4rem; }
              .brand-sub { letter-spacing: 0.2em; }
              .lower-grid { grid-template-columns: 1fr; }
              .right-panel { grid-template-columns: 1fr; }
            }
          </style>
        </head>
        <body>
          <div class="app-shell">
            <header class="topbar">
              <div class="brand-wrap">
                <div class="brand-icon">B</div>
                <div class="brand-name">JARVIS</div>
                <div class="brand-sub">Command Center</div>
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
                  <h2>JARVIS</h2>
                  <div class="nav-list">
                    <div class="nav-item active">Home</div>
                    <div class="nav-item">AI Core</div>
                    <div class="nav-item">Activity</div>
                    <div class="nav-item">Memory</div>
                    <div class="nav-item">Files</div>
                    <div class="nav-item">Web</div>
                    <div class="nav-item">Settings</div>
                  </div>
                </div>

                <div class="sidebar-footer">
                  <span class="dot"></span>
                  <span>System online</span>
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
                  <div class="panel">
                    <div class="panel-header">
                      <div class="panel-title">Voice conversation</div>
                    </div>
                    <div class="chat-box" id="chatBox" style="height: 160px; border: 1px solid rgba(120, 180, 240, 0.15); background: rgba(255,255,255,0.03); border-radius: 8px; overflow: auto; padding: 14px; color: var(--muted);">
                      <div class="message buddy" style="margin: 0;">Buddy is listening and ready.</div>
                    </div>
                    <div style="display:flex; gap:8px; margin-top:10px;">
                      <input id="commandInput" style="flex:1; background: rgba(9, 18, 25, 0.9); border:1px solid rgba(120,180,240,.18); color: white; border-radius: 999px; padding: 10px 14px; outline:none;" placeholder="Tell Buddy what to do..." />
                      <button id="sendBtn" style="min-width: 84px; background: linear-gradient(180deg, #0f6075, #104465); border:1px solid rgba(137, 227, 255, 0.25); border-radius: 999px; color: white; font-weight:700;">Send</button>
                    </div>
                  </div>

                  <div class="panel">
                    <div class="panel-header">
                      <div class="panel-title">Active agents</div>
                      <div class="panel-link">View all</div>
                    </div>
                    <div class="agent-grid">
                      <div class="agent-card"><div class="left"><div class="small-icon"></div><span>Coding Agent</span></div><span class="status-tag">Active</span></div>
                      <div class="agent-card"><div class="left"><div class="small-icon"></div><span>Research Agent</span></div><span class="status-tag">Ready</span></div>
                      <div class="agent-card"><div class="left"><div class="small-icon"></div><span>Memory Agent</span></div><span class="status-tag">Standby</span></div>
                      <div class="agent-card"><div class="left"><div class="small-icon"></div><span>Task Agent</span></div><span class="status-tag">Standby</span></div>
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

            function updateClock() {
              const now = new Date();
              const time = now.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: true });
              timeLabel.textContent = time;
            }

            function updateStaticState() {
              buddyGreeting.textContent = 'READY';
              voiceStatusText.textContent = 'Listening for the next command.';
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