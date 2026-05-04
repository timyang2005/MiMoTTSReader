let currentConfig = null;

function init() {
    loadConfig();
    loadVoices();
    updateServiceUI(Android.isServiceRunning());
}

function loadConfig() {
    try {
        const json = Android.getConfig();
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
        const voices = JSON.parse(Android.getVoices());
        const select = document.getElementById('voiceSelect');
        select.innerHTML = '';
        voices.forEach(function(v) {
            const opt = document.createElement('option');
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
        const config = collectConfig();
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
    const badge = document.getElementById('statusBadge');
    const statusText = document.getElementById('serviceStatus');
    const btnStart = document.getElementById('btnStart');
    const btnStop = document.getElementById('btnStop');
    const btnRestart = document.getElementById('btnRestart');
    const serverUrl = document.getElementById('serverUrl');

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
            const port = document.getElementById('serverPort').value || 9966;
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
        const rule = Android.getLegadoRule();
        document.getElementById('legadoRule').textContent = rule;
    } catch (e) {
        const port = document.getElementById('serverPort').value || 9966;
        document.getElementById('legadoRule').textContent =
            'http://localhost:' + port + '/tts,{"method":"POST","body":"tex={{java.encodeURI(java.encodeURI(speakText))}}&spd={{String((speakSpeed+5)/10+4)}}&_res_tag_=audio"}';
    }
}

function copyLegadoRule() {
    const rule = document.getElementById('legadoRule').textContent;
    if (navigator.clipboard) {
        navigator.clipboard.writeText(rule).then(function() {
            Android.showToast('已复制到剪贴板');
        });
    } else {
        const textarea = document.createElement('textarea');
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
    const text = document.getElementById('testText').value.trim();
    if (!text) {
        Android.showToast('请输入测试文本');
        return;
    }

    const config = collectConfig();
    if (!config.mimoApiKey) {
        Android.showToast('请先配置 API Key');
        return;
    }

    const btn = document.getElementById('btnTest');
    btn.disabled = true;
    btn.textContent = '合成中...';

    try {
        const port = config.serverPort || 9966;
        const formData = new FormData();
        formData.append('text', text);

        fetch('http://localhost:' + port + '/api/test', {
            method: 'POST',
            body: JSON.stringify({ text: text }),
            headers: { 'Content-Type': 'application/json' }
        })
        .then(function(response) {
            if (!response.ok) {
                return response.text().then(function(t) { throw new Error(t); });
            }
            return response.blob();
        })
        .then(function(blob) {
            const url = URL.createObjectURL(blob);
            const player = document.getElementById('audioPlayer');
            const container = document.getElementById('audioPlayerContainer');
            player.src = url;
            container.style.display = 'block';
            player.play();
            btn.disabled = false;
            btn.textContent = '测试合成';
            Android.showToast('合成成功');
        })
        .catch(function(err) {
            btn.disabled = false;
            btn.textContent = '测试合成';
            Android.showToast('合成失败: ' + err.message);
        });
    } catch (e) {
        btn.disabled = false;
        btn.textContent = '测试合成';
        Android.showToast('请求失败: ' + e.message);
    }
}

function toggleApiKeyVisibility() {
    const input = document.getElementById('apiKey');
    const icon = document.getElementById('eyeIcon');
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
        const logs = JSON.parse(logsJson);
        const container = document.getElementById('logContainer');
        if (logs.length === 0) {
            container.innerHTML = '<div class="log-empty">暂无日志</div>';
            return;
        }
        container.innerHTML = logs.map(function(log) {
            const isError = log.toLowerCase().includes('error') || log.toLowerCase().includes('fail');
            const isSuccess = log.toLowerCase().includes('success') || log.toLowerCase().includes('started') || log.toLowerCase().includes('ready');
            const cls = isError ? 'error' : (isSuccess ? 'success' : '');
            return '<div class="log-entry"><span class="log-msg ' + cls + '">' + escapeHtml(log) + '</span></div>';
        }).join('');
    } catch (e) {
    }
}

function clearLogs() {
    document.getElementById('logContainer').innerHTML = '<div class="log-empty">暂无日志</div>';
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

document.addEventListener('DOMContentLoaded', init);
