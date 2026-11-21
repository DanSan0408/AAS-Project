/*
 * "Is this person already registered?" hint for the Add Student / Add Assessor forms.
 *
 * While the admin types an email or a name, matching accounts that already exist on the system are listed under the
 * form. Choosing "Add to this course" brings that account into the course instead of creating a duplicate. Everything
 * shown is set with textContent, so nothing typed or stored can inject markup.
 *
 * The form opts in with: data-suggest-url, data-suggest-type, data-add-url, data-add-field, data-noun.
 * Edit forms (they carry an id) are skipped.
 */
(function () {
    'use strict';

    var MIN_LENGTH = 3;
    var DELAY_MS = 250;

    var form = document.querySelector('form[data-suggest-url]');
    var panel = document.getElementById('userSuggestions');
    if (!form || !panel || typeof fetch !== 'function') {
        return;
    }
    var idField = form.querySelector('input[name="id"]');
    if (idField && idField.value) {
        return; // editing an existing user: nothing to suggest
    }

    var url = form.getAttribute('data-suggest-url');
    var type = form.getAttribute('data-suggest-type');
    var addUrl = form.getAttribute('data-add-url');
    var addField = form.getAttribute('data-add-field');
    var noun = form.getAttribute('data-noun') || 'user';
    var csrf = form.querySelector('input[name="_csrf"]');
    var timer = null;
    var pending = null;
    var latest = 0;

    function clear() {
        panel.textContent = '';
        panel.hidden = true;
    }

    function el(tag, className, text) {
        var node = document.createElement(tag);
        if (className) {
            node.className = className;
        }
        if (text !== undefined) {
            node.textContent = text;
        }
        return node;
    }

    function addForm(person) {
        var f = el('form', 'suggest-add');
        f.method = 'post';
        f.action = addUrl;
        var id = document.createElement('input');
        id.type = 'hidden';
        id.name = addField;
        id.value = String(person.id);
        f.appendChild(id);
        if (csrf) {
            var token = document.createElement('input');
            token.type = 'hidden';
            token.name = csrf.name;
            token.value = csrf.value;
            f.appendChild(token);
        }
        var button = el('button', 'suggest-add-btn', 'Add to this course');
        button.type = 'submit';
        button.setAttribute('aria-label', 'Add ' + (person.username || person.email) + ' to this course');
        f.appendChild(button);
        return f;
    }

    function render(people) {
        panel.textContent = '';
        if (!people.length) {
            panel.hidden = true;
            return;
        }
        panel.appendChild(el('p', 'suggest-heading', 'Already registered? These ' + noun + ' accounts match what you typed.'));
        var list = el('ul', 'suggest-list');
        people.forEach(function (person) {
            var row = el('li', 'suggest-row');
            var who = el('div', 'suggest-who');
            who.appendChild(el('strong', 'suggest-name', person.username || '(no name)'));
            who.appendChild(el('span', 'suggest-email', person.email || ''));
            row.appendChild(who);
            row.appendChild(person.inThisCourse
                ? el('span', 'suggest-status', 'Already in this course')
                : addForm(person));
            list.appendChild(row);
        });
        panel.appendChild(list);
        panel.appendChild(el('p', 'suggest-note', 'Adding an existing account keeps one account for the person. No new password or welcome email is sent.'));
        panel.hidden = false;
    }

    function search(text) {
        var ticket = ++latest;
        if (pending && typeof pending.abort === 'function') {
            pending.abort();
        }
        pending = typeof AbortController === 'function' ? new AbortController() : null;
        var options = { credentials: 'same-origin', headers: { 'Accept': 'application/json' } };
        if (pending) {
            options.signal = pending.signal;
        }
        fetch(url + '?type=' + encodeURIComponent(type) + '&q=' + encodeURIComponent(text), options)
            .then(function (response) { return response.ok ? response.json() : []; })
            .then(function (people) {
                if (ticket === latest) {
                    render(Array.isArray(people) ? people : []);
                }
            })
            .catch(function () { /* the hint is optional; typing and submitting still work */ });
    }

    function onInput(event) {
        var text = event.target.value.trim();
        window.clearTimeout(timer);
        if (text.length < MIN_LENGTH) {
            latest++;
            clear();
            return;
        }
        timer = window.setTimeout(function () { search(text); }, DELAY_MS);
    }

    ['email', 'username'].forEach(function (id) {
        var input = document.getElementById(id);
        if (input) {
            input.addEventListener('input', onInput);
        }
    });
})();
