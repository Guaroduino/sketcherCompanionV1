package com.sketcher.sketchercompanionv1.projection

object HtmlProjectionPage {
    val HTML = """
<!DOCTYPE html>
<html lang="es">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, user-scalable=no">
<title>Sketcher — Live View</title>
<style>
  * { margin: 0; padding: 0; box-sizing: border-box; }
  html, body {
    width: 100%; height: 100%;
    background: #000000;
    overflow: hidden;
    font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif;
  }
  #canvas-container {
    width: 100vw; height: 100vh;
    display: flex; align-items: center; justify-content: center;
    position: relative;
  }
  #canvas-img {
    max-width: 100%; max-height: 100%;
    width: 100%; height: 100%;
    object-fit: contain;
    display: block;
  }
  #reconnect-overlay {
    position: fixed; inset: 0;
    background: rgba(0,0,0,0.85);
    display: flex; align-items: center; justify-content: center;
    flex-direction: column; gap: 16px; z-index: 300;
    opacity: 0; pointer-events: none;
    transition: opacity 0.3s;
  }
  #reconnect-overlay.visible { opacity: 1; pointer-events: auto; }
  .reconnect-icon { font-size: 48px; animation: spin 1.5s linear infinite; color: #fff; }
  @keyframes spin { to { transform: rotate(360deg); } }
  .reconnect-text { color: rgba(255,255,255,0.7); font-size: 16px; }
  #error-log {
    position: fixed; bottom: 20px; left: 20px; right: 20px;
    background: rgba(239, 68, 68, 0.95); color: white;
    padding: 12px 16px; border-radius: 8px;
    font-family: monospace; font-size: 13px; z-index: 400;
    display: none; box-shadow: 0 4px 12px rgba(0,0,0,0.3);
    word-break: break-all;
  }
</style>
</head>
<body>
<div id="canvas-container">
  <img id="canvas-img" alt="Canvas" draggable="false">
</div>

<div id="reconnect-overlay" class="visible">
  <div class="reconnect-icon">🔄</div>
  <div class="reconnect-text">Conectando con el canvas...</div>
</div>

<div id="error-log"></div>

<script>
  const img = document.getElementById('canvas-img');
  const reconnectOverlay = document.getElementById('reconnect-overlay');
  const errorLog = document.getElementById('error-log');

  let ws = null;
  let currentUrl = null;
  let reconnectTimer = null;
  let logQueue = [];

  const originalLog = console.log;
  const originalError = console.error;
  const originalWarn = console.warn;

  function remoteLog(level, args) {
    const msg = Array.from(args).map(arg => {
      if (typeof arg === 'object') {
        try { return JSON.stringify(arg); } catch (e) { return arg.toString(); }
      }
      return String(arg);
    }).join(' ');
    
    originalLog("[Client]", msg);
    
    const payload = JSON.stringify({ type: 'log', level: level, message: msg });
    if (ws && ws.readyState === WebSocket.OPEN) {
      try { ws.send(payload); } catch (e) {}
    } else {
      logQueue.push(payload);
      if (logQueue.length > 100) logQueue.shift();
    }
  }

  console.log = function() { remoteLog('log', arguments); };
  console.error = function() { remoteLog('error', arguments); };
  console.warn = function() { remoteLog('warn', arguments); };

  window.onerror = function(message, source, lineno, colno, error) {
    var errorMsg = message + " at " + source + ":" + lineno + ":" + colno;
    console.error("Window Error:", errorMsg, error ? error.stack : "");
    showError("JS Error: " + errorMsg);
  };

  window.addEventListener('unhandledrejection', function(event) {
    console.error("Unhandled Promise Rejection:", event.reason);
  });

  function showError(msg) {
    errorLog.textContent = msg;
    errorLog.style.display = 'block';
  }

  function connect() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    console.log("Attempting WebSocket connection to " + protocol + '//' + host + '/ws');
    ws = new WebSocket(protocol + '//' + host + '/ws');
    ws.binaryType = 'arraybuffer';

    ws.onopen = () => {
      console.log("WebSocket connected. Sending hello...");
      ws.send(JSON.stringify({
        type: 'hello',
        width: window.innerWidth,
        height: window.innerHeight
      }));
      reconnectOverlay.classList.remove('visible');
      errorLog.style.display = 'none';
      if (reconnectTimer) { clearTimeout(reconnectTimer); reconnectTimer = null; }
      
      // Flush buffered logs
      while (logQueue.length > 0) {
        try { ws.send(logQueue.shift()); } catch (e) { break; }
      }
    };

    ws.onmessage = (e) => {
      try {
        if (typeof e.data === 'string') {
          console.log("Received text message: " + e.data);
          return;
        }

        const buf = e.data;
        if (buf.byteLength <= 1) {
          console.error("Received empty or truncated binary data: " + buf.byteLength + " bytes");
          showError("Error: WebSocket received empty or truncated binary data.");
          return;
        }

        const tag = new Uint8Array(buf, 0, 1)[0];

        const jpeg = new Uint8Array(buf.slice(1));
        const blob = new Blob([jpeg], { type: 'image/jpeg' });

        const newUrl = URL.createObjectURL(blob);
        img.src = newUrl;
        
        img.onload = () => {
          errorLog.style.display = 'none';
        };
        img.onerror = () => {
          console.error("Browser failed to decode image. Size: " + jpeg.byteLength + " bytes.");
          showError("Error: Browser failed to decode image.");
        };

        if (currentUrl) URL.revokeObjectURL(currentUrl);
        currentUrl = newUrl;
      } catch (err) {
        console.error("JS exception in onmessage: " + err.message + "\n" + err.stack);
        showError("JS Exception: " + err.message);
      }
    };

    ws.onclose = (event) => {
      console.warn("WebSocket closed. Code: " + event.code + ", Reason: " + event.reason);
      reconnectOverlay.classList.add('visible');
      if (!reconnectTimer) {
        reconnectTimer = setTimeout(connect, 2000);
      }
    };

    ws.onerror = (err) => {
      console.error("WebSocket error observed:", err);
      ws.close();
    };
  }

  window.addEventListener('resize', () => {
    if (ws && ws.readyState === WebSocket.OPEN) {
      ws.send(JSON.stringify({
        type: 'hello',
        width: window.innerWidth,
        height: window.innerHeight
      }));
    }
  });

  connect();
</script>
</body>
</html>
""".trimIndent()
}
