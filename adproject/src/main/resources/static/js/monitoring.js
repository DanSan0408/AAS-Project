(function () {
    'use strict';
    var KEY = 'iams.monitoring.autorefresh';
    var toggle = document.getElementById('autoRefresh');
    if (!toggle) {
        return;
    }
    var timer = null;

    function start() {
        timer = window.setTimeout(function () { window.location.reload(); }, 30000);
    }

    toggle.checked = window.localStorage.getItem(KEY) === '1';
    if (toggle.checked) {
        start();
    }
    toggle.addEventListener('change', function () {
        window.localStorage.setItem(KEY, toggle.checked ? '1' : '0');
        if (timer) {
            window.clearTimeout(timer);
            timer = null;
        }
        if (toggle.checked) {
            start();
        }
    });
}());
