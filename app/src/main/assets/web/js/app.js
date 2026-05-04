let currentConfig = null;
let logRefreshTimer = null;

function init() {
    loadConfig();
    loadVoices();
    updateServiceUI(Android.isServiceRunning());
    startLogRefresh();
}

function startLogRefresh() {
    if (logRefreshTimer) clearInterval(logRefreshTimer);
    logRefreshTimer = setInterval(function() {
        try {
            var logsJson = Android.getLogs();
            if (logsJson) {
                onLogsUpdate(logsJson);
            }
        } catch(e) {}
    }, 2000);
}

function loadConfig() {
    try {
        var json = Android.getConfig();
        currentConfig = JSON.parse(json);
        fillForm(currentConfig);
        updateLegadoRule();
    } catch (e) {
        console.error('Failed to load config:', e);
    }
}

function fillForm(config) {
    document.getElementById('apiKey').value = config.mimoApiKey || '';
    document.getElementById('voiceSelect').value = config.voice || '冰糖';
    document.getElementById('modelSelect').value = config.model || 'mimo-v2.5-tts';
    document.getElementById('dialectSelect').value = config.dialect || '';
    document.getElementById('styleTag').value = config.styleTag || '';
    document.getElementById('userMessage').value = config.userMessage || '';
    document.getElementById('serverPort').value = config.serverPort || 9966;
    document.getElementById('autoStart').checked = config.autoStart || false;
}

function loadVoices() {
    try {
        var voices = JSON.parse(Android.getVoices());
        var select = document.getElementById('voiceSelect');
        select.innerHTML = '';
        voices.forEach(function(v) {
            var opt = document.createElement('option');
            opt.value = v.id;
            opt.textContent = v.name + ' (' + v.gender + ' - ' + v.language + ')';
            select.appendChild(opt);
        });
        if (currentConfig) {
            select.value = currentConfig.voice || '冰糖';
        }
    } catch (e) {
        console.error('Failed to load voices:', e);
    }
}

function collectConfig() {
    return {
        mimoApiKey: document.getElementById('apiKey').value.trim(),
        voice: document.getElementById('voiceSelect').value,
        model: document.getElementById('modelSelect').value,
        dialect: document.getElementById('dialectSelect').value,
        styleTag: document.getElementById('styleTag').value.trim(),
        userMessage: document.getElementById('userMessage').value.trim(),
        serverPort: parseInt(document.getElementById('serverPort').value) || 9966,
        autoStart: document.getElementById('autoStart').checked,
        speed: currentConfig ? currentConfig.speed : 5,
        volume: currentConfig ? currentConfig.volume : 5,
        audioFormat: currentConfig ? currentConfig.audioFormat : 'wav'
    };
}

function saveConfig() {
    try {
        var config = collectConfig();
        Android.saveConfig(JSON.stringify(config));
        currentConfig = config;
        updateLegadoRule();
        Android.showToast('配置已保存');
    } catch (e) {
        Android.showToast('保存失败: ' + e.message);
    }
}

function startService() {
    saveConfig();
    Android.startService();
    updateServiceUI(true);
}

function stopService() {
    Android.stopService();
    updateServiceUI(false);
}

function restartService() {
    saveConfig();
    Android.restartService();
}

function updateServiceUI(running) {
    var badge = document.getElementById('statusBadge');
    var statusText = document.getElementById('serviceStatus');
    var btnStart = document.getElementById('btnStart');
    var btnStop = document.getElementById('btnStop');
    var btnRestart = document.getElementById('btnRestart');
    var serverUrl = document.getElementById('serverUrl');

    if (running) {
        badge.className = 'status-badge running';
        badge.querySelector('.status-text').textContent = '运行中';
        statusText.textContent = '运行中';
        statusText.style.color = '#66bb6a';
        btnStart.disabled = true;
        btnStop.disabled = false;
        btnRestart.disabled = false;
        try {
            serverUrl.textContent = Android.getServerUrl();
        } catch (e) {
            var port = document.getElementById('serverPort').value || 9966;
            serverUrl.textContent = 'http://localhost:' + port;
        }
    } else {
        badge.className = 'status-badge';
        badge.querySelector('.status-text').textContent = '已停止';
        statusText.textContent = '未启动';
        statusText.style.color = '#ef5350';
        btnStart.disabled = false;
        btnStop.disabled = true;
        btnRestart.disabled = true;
        serverUrl.textContent = '-';
    }
}

function onServiceStateChanged(running) {
    updateServiceUI(running);
}

function updateLegadoRule() {
    try {
        var ruleJson = Android.getLegadoRule();
        var rule = JSON.parse(ruleJson);
        document.getElementById('legadoRule').textContent = JSON.stringify(rule, null, 2);
    } catch (e) {
        document.getElementById('legadoRule').textContent = '获取规则失败';
    }
}

function copyLegadoRule() {
    var rule = document.getElementById('legadoRule').textContent;
    if (navigator.clipboard) {
        navigator.clipboard.writeText(rule).then(function() {
            Android.showToast('已复制到剪贴板');
        });
    } else {
        var textarea = document.createElement('textarea');
        textarea.value = rule;
        document.body.appendChild(textarea);
        textarea.select();
        document.execCommand('copy');
        document.body.removeChild(textarea);
        Android.showToast('已复制到剪贴板');
    }
}

function importToLegado() {
    Android.importToLegado();
}

function testTts() {
    var text = document.getElementById('testText').value.trim();
    if (!text) {
        Android.showToast('请输入测试文本');
        return;
    }

    var config = collectConfig();
    if (!config.mimoApiKey) {
        Android.showToast('请先配置 API Key');
        return;
    }

    var btn = document.getElementById('btnTest');
    btn.disabled = true;
    btn.textContent = '合成中...';

    Android.testTts(text);
}

function onTestResult(fileUrl) {
    var player = document.getElementById('audioPlayer');
    var container = document.getElementById('audioPlayerContainer');
    player.src = fileUrl;
    container.style.display = 'block';
    player.play();
    var btn = document.getElementById('btnTest');
    btn.disabled = false;
    btn.textContent = '测试合成';
    Android.showToast('合成成功');
}

function onTestError(errorMessage) {
    var btn = document.getElementById('btnTest');
    btn.disabled = false;
    btn.textContent = '测试合成';
    Android.showToast('合成失败: ' + errorMessage);
}

function toggleApiKeyVisibility() {
    var input = document.getElementById('apiKey');
    var icon = document.getElementById('eyeIcon');
    if (input.type === 'password') {
        input.type = 'text';
        icon.textContent = '🔒';
    } else {
        input.type = 'password';
        icon.textContent = '👁';
    }
}

function onVoiceChange() {
    updateLegadoRule();
}

function requestBatteryOptimization() {
    Android.requestBatteryOptimization();
}

function onLogsUpdate(logsJson) {
    try {
        var logs;
        if (typeof logsJson === 'string') {
            logs = JSON.parse(logsJson);
        } else {
            logs = logsJson;
        }
        var container = document.getElementById('logContainer');
        if (!logs || logs.length === 0) {
            container.innerHTML = '<div class="log-empty">暂无日志</div>';
            return;
        }
        container.innerHTML = logs.map(function(log) {
            var isError = log.toLowerCase().indexOf('error') >= 0 || log.toLowerCase().indexOf('fail') >= 0;
            var isSuccess = log.toLowerCase().indexOf('started') >= 0 || log.toLowerCase().indexOf('ready') >= 0 || log.toLowerCase().indexOf('success') >= 0;
            var cls = isError ? 'error' : (isSuccess ? 'success' : '');
            return '<div class="log-entry"><span class="log-msg ' + cls + '">' + escapeHtml(log) + '</span></div>';
        }).join('');
    } catch (e) {
    }
}

function clearLogs() {
    document.getElementById('logContainer').innerHTML = '<div class="log-empty">暂无日志</div>';
}

function escapeHtml(text) {
    var div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

document.addEventListener('DOMContentLoaded', init);
