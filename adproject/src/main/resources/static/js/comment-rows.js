/**
 * Comment rows (fragments/comment_rows.html): "Read more" on a comment that is clamped, and the student page's filter by who
 * wrote the comment. Plain script, no dependencies; works without it (the full text is just clamped to four lines).
 */
(function () {
    'use strict';

    function measure(row) {
        var text = row.querySelector('.comment-row-text');
        var button = row.querySelector('.comment-row-more');
        if (!text || !button) {
            return;
        }
        if (row.classList.contains('is-expanded')) {
            button.hidden = false;
            return;
        }
        button.hidden = !(text.scrollHeight > text.clientHeight + 1);
    }

    function measureAll() {
        var rows = document.querySelectorAll('.comment-row');
        for (var i = 0; i < rows.length; i++) {
            measure(rows[i]);
        }
    }

    function toggle(button) {
        var row = button.closest('.comment-row');
        if (!row) {
            return;
        }
        var expanded = row.classList.toggle('is-expanded');
        button.setAttribute('aria-expanded', expanded ? 'true' : 'false');
        button.textContent = expanded ? 'Show less' : 'Read more';
    }

    function applyFilter(button) {
        var kinds = (button.getAttribute('data-kinds') || '').split(/\s+/).filter(Boolean);
        var buttons = document.querySelectorAll('.comment-filter-btn');
        for (var b = 0; b < buttons.length; b++) {
            buttons[b].setAttribute('aria-pressed', buttons[b] === button ? 'true' : 'false');
        }
        var rows = document.querySelectorAll('.comment-row');
        var shown = 0;
        for (var i = 0; i < rows.length; i++) {
            var visible = kinds.length === 0 || kinds.indexOf(rows[i].getAttribute('data-kind')) !== -1;
            rows[i].hidden = !visible;
            if (visible) {
                shown++;
            }
        }
        var blocks = document.querySelectorAll('.assessment-comments-block');
        for (var k = 0; k < blocks.length; k++) {
            var inBlock = blocks[k].querySelectorAll('.comment-row');
            var any = false;
            for (var j = 0; j < inBlock.length; j++) {
                if (!inBlock[j].hidden) {
                    any = true;
                }
            }
            blocks[k].hidden = !any;
        }
        var empty = document.querySelector('.comment-filter-empty');
        if (empty) {
            empty.hidden = shown !== 0;
        }
        measureAll();
    }

    document.addEventListener('click', function (event) {
        var target = event.target && event.target.closest ? event.target : null;
        if (!target) {
            return;
        }
        var more = target.closest('.comment-row-more');
        if (more) {
            toggle(more);
            return;
        }
        var filter = target.closest('.comment-filter-btn');
        if (filter) {
            applyFilter(filter);
        }
    });

    var pending = false;
    window.addEventListener('resize', function () {
        if (pending) {
            return;
        }
        pending = true;
        (window.requestAnimationFrame || window.setTimeout)(function () {
            pending = false;
            measureAll();
        });
    });

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', measureAll);
    } else {
        measureAll();
    }
    window.addEventListener('load', measureAll);
})();
