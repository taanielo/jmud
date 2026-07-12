/*
 * jmud browser web client (issue #527).
 *
 * A dependency-free terminal over the WebSocket transport (issue #526). It renders server output
 * including ANSI SGR colors, keeps the input line below the scrollback, echoes input locally, keeps
 * up/down command history, masks password input, and surfaces connection state with one-click
 * reconnect. No auto-reconnect loop (so it never fights the server-side linkdead timeout, #343).
 */
(function () {
    'use strict';

    var ESC = '\x1b';
    var MAX_LINES = 5000;          // Scrollback cap.
    var MAX_HISTORY = 200;

    var urlInput = document.getElementById('url');
    var connectBtn = document.getElementById('connect');
    var disconnectBtn = document.getElementById('disconnect');
    var statusEl = document.getElementById('status');
    var terminal = document.getElementById('terminal');
    var output = document.getElementById('output');
    var form = document.getElementById('inputForm');
    var input = document.getElementById('input');

    var socket = null;
    var history = [];
    var historyIndex = -1;        // -1 means "editing a fresh line".
    var draft = '';
    var pinnedToBottom = true;

    // ── Default endpoint: same host as the page, /ws path; fall back to localhost for file:// ──
    urlInput.value = defaultWsUrl();

    function defaultWsUrl() {
        if (location.protocol === 'http:' || location.protocol === 'https:') {
            var scheme = location.protocol === 'https:' ? 'wss' : 'ws';
            return scheme + '://' + location.host + '/ws';
        }
        return 'ws://localhost:8080/ws';
    }

    // ── ANSI renderer ─────────────────────────────────────────────────────────────
    // Streaming SGR parser. Style persists across lines until reset, matching a real terminal.
    var style = { bold: false, fg: null, bg: null };
    var currentLine = newLine();

    function newLine() {
        var div = document.createElement('div');
        div.className = 'line';
        output.appendChild(div);
        return div;
    }

    function spanClass() {
        var cls = [];
        if (style.bold) { cls.push('b'); }
        if (style.fg !== null) { cls.push('fg' + style.fg); }
        if (style.bg !== null) { cls.push('bg' + style.bg); }
        return cls.join(' ');
    }

    function appendText(text) {
        if (text.length === 0) { return; }
        var span = document.createElement('span');
        var cls = spanClass();
        if (cls) { span.className = cls; }
        span.textContent = text;
        currentLine.appendChild(span);
    }

    function applySgr(params) {
        var codes = params.split(';');
        for (var i = 0; i < codes.length; i++) {
            var code = parseInt(codes[i] === '' ? '0' : codes[i], 10);
            if (isNaN(code) || code === 0) {
                style.bold = false; style.fg = null; style.bg = null;
            } else if (code === 1) {
                style.bold = true;
            } else if (code === 22) {
                style.bold = false;
            } else if ((code >= 30 && code <= 37) || (code >= 90 && code <= 97)) {
                style.fg = code;
            } else if (code === 39) {
                style.fg = null;
            } else if (code >= 40 && code <= 47) {
                style.bg = code;
            } else if (code === 49) {
                style.bg = null;
            }
            // Other SGR codes (italics, underline, 256/true-color) are ignored for simplicity.
        }
    }

    function render(text) {
        var i = 0;
        while (i < text.length) {
            var ch = text[i];
            if (ch === ESC && text[i + 1] === '[') {
                var j = i + 2;
                while (j < text.length && !isFinalByte(text[j])) { j++; }
                var final = text[j];
                var paramsPart = text.slice(i + 2, j);
                if (final === 'm') { applySgr(paramsPart); }
                // Non-SGR CSI sequences (cursor moves, clears) are dropped.
                i = j + 1;
            } else if (ch === '\r') {
                i++;
            } else if (ch === '\n') {
                currentLine = newLine();
                i++;
            } else {
                var k = i;
                while (k < text.length && text[k] !== ESC && text[k] !== '\n' && text[k] !== '\r') { k++; }
                appendText(text.slice(i, k));
                i = k;
            }
        }
        trimScrollback();
        maybeAutoScroll();
    }

    function isFinalByte(ch) {
        var c = ch.charCodeAt(0);
        return c >= 0x40 && c <= 0x7e;
    }

    function trimScrollback() {
        while (output.childElementCount > MAX_LINES) {
            output.removeChild(output.firstChild);
        }
    }

    function maybeAutoScroll() {
        if (pinnedToBottom) {
            terminal.scrollTop = terminal.scrollHeight;
        }
    }

    // ── Local echo helpers ──────────────────────────────────────────────────────────
    function echoLine(text) {
        render('\r\n');
        appendText(text);
        render('\r\n');
    }

    // Password masking heuristic (documented): if the last visible line ends with "password:"
    // (case-insensitive, ignoring trailing whitespace), the next input is a password field. The
    // login flow's prompt is "Enter password: ".
    function updateInputMode() {
        var last = currentLine.textContent || '';
        var masked = /password:\s*$/i.test(last);
        input.type = masked ? 'password' : 'text';
    }

    // ── Connection lifecycle ─────────────────────────────────────────────────────────
    function setStatus(state, label) {
        statusEl.className = 'status ' + state;
        statusEl.textContent = label;
    }

    function connect() {
        var url = urlInput.value.trim();
        if (!url) { return; }
        try {
            socket = new WebSocket(url);
        } catch (e) {
            render('\r\n[client] Invalid WebSocket URL: ' + e.message + '\r\n');
            return;
        }
        setStatus('connecting', 'connecting');
        connectBtn.hidden = true;
        disconnectBtn.hidden = false;
        urlInput.disabled = true;

        socket.onopen = function () {
            setStatus('connected', 'connected');
            input.disabled = false;
            input.focus();
        };
        socket.onmessage = function (event) {
            render(typeof event.data === 'string' ? event.data : '');
            updateInputMode();
        };
        socket.onclose = function () {
            onDisconnected();
        };
        socket.onerror = function () {
            // onclose always follows; surface a hint but do not auto-reconnect.
            render('\r\n[client] Connection error.\r\n');
        };
    }

    function onDisconnected() {
        setStatus('disconnected', 'disconnected');
        input.disabled = true;
        input.type = 'text';
        connectBtn.hidden = false;
        connectBtn.textContent = 'Reconnect';
        disconnectBtn.hidden = true;
        urlInput.disabled = false;
        socket = null;
        render('\r\n[client] Disconnected. Press Reconnect to resume.\r\n');
    }

    function disconnect() {
        if (socket) {
            socket.close();
        }
    }

    function send(line) {
        if (!socket || socket.readyState !== WebSocket.OPEN) { return; }
        socket.send(line);
    }

    // ── Input handling ───────────────────────────────────────────────────────────────
    form.addEventListener('submit', function (event) {
        event.preventDefault();
        var line = input.value;
        var masked = input.type === 'password';
        send(line);
        echoLine(masked ? '' : line);
        updateInputMode();
        if (!masked && line.trim() !== '') {
            history.push(line);
            if (history.length > MAX_HISTORY) { history.shift(); }
        }
        historyIndex = -1;
        draft = '';
        input.value = '';
    });

    input.addEventListener('keydown', function (event) {
        if (event.key === 'ArrowUp') {
            event.preventDefault();
            navigateHistory(-1);
        } else if (event.key === 'ArrowDown') {
            event.preventDefault();
            navigateHistory(1);
        }
    });

    function navigateHistory(direction) {
        if (history.length === 0) { return; }
        if (historyIndex === -1) {
            if (direction === 1) { return; }
            draft = input.value;
            historyIndex = history.length - 1;
        } else {
            historyIndex += direction;
        }
        if (historyIndex >= history.length) {
            historyIndex = -1;
            input.value = draft;
            return;
        }
        if (historyIndex < 0) { historyIndex = 0; }
        input.value = history[historyIndex];
        // Move caret to end.
        var value = input.value;
        input.value = '';
        input.value = value;
    }

    // Track whether the user has scrolled up (unpin) or is at the bottom (pin).
    terminal.addEventListener('scroll', function () {
        var distanceFromBottom = terminal.scrollHeight - terminal.scrollTop - terminal.clientHeight;
        pinnedToBottom = distanceFromBottom < 4;
    });

    // Clicking anywhere in the scrollback focuses the input for a keyboard-first feel.
    terminal.addEventListener('mouseup', function () {
        if (window.getSelection().toString() === '' && !input.disabled) {
            input.focus();
        }
    });

    connectBtn.addEventListener('click', connect);
    disconnectBtn.addEventListener('click', disconnect);
    urlInput.addEventListener('keydown', function (event) {
        if (event.key === 'Enter') { connect(); }
    });

    render('[client] jmud web terminal ready. Press Connect to join.\r\n');
})();
