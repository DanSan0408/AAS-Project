(function () {
    'use strict';

    function renumber(cards) {
        cards.forEach(function (card, index) {
            card.setAttribute('data-id', String(index + 1));
            var num = card.querySelector('.comment-num');
            if (num) {
                num.textContent = String(index + 1);
            }
        });
    }

    function createCommentItem(nextIndex) {
        var div = document.createElement('div');
        div.className = 'comment-card';
        div.setAttribute('data-id', String(nextIndex));

        div.innerHTML = [
            '<div class="comment-header">',
            '  <span class="badge">Q<span class="comment-num">' + nextIndex + '</span></span>',
            '  <button type="button" class="btn-icon-remove" data-action="remove-comment-item"><i class="fas fa-times"></i></button>',
            '</div>',
            '<div class="form-group">',
            '  <label>Question Prompt</label>',
            '  <textarea name="commentLabels" required placeholder="e.g. What were the strengths?"></textarea>',
            '</div>',
            '<div class="settings-row">',
            '  <div class="form-group setting-input">',
            '    <label>Min Characters</label>',
            '    <input type="number" name="commentMinLengths" value="20" min="10" max="500">',
            '  </div>',
            '  <div class="form-group setting-check">',
            '    <label class="toggle-label">',
            '      <input type="checkbox" name="commentAnonymous" value="true" checked>',
            '      <span class="toggle-text"><i class="fas fa-user-secret"></i> Anonymous</span>',
            '    </label>',
            '  </div>',
            '</div>'
        ].join('');

        return div;
    }

    document.addEventListener('DOMContentLoaded', function () {
        var commentsContainer = document.getElementById('commentsContainer');
        var emptyState = document.getElementById('emptyState');
        var addCommentBtn = document.getElementById('addCommentBtn');

        if (!commentsContainer || !emptyState || !addCommentBtn) {
            return;
        }

        function getCards() {
            return Array.prototype.slice.call(commentsContainer.querySelectorAll('.comment-card'));
        }

        function syncEmptyState() {
            emptyState.style.display = getCards().length === 0 ? 'block' : 'none';
        }

        addCommentBtn.addEventListener('click', function () {
            var nextIndex = getCards().length + 1;
            commentsContainer.appendChild(createCommentItem(nextIndex));
            renumber(getCards());
            syncEmptyState();
        });

        commentsContainer.addEventListener('click', function (event) {
            var removeBtn = event.target.closest('[data-action="remove-comment-item"]');
            if (!removeBtn) {
                return;
            }

            var card = removeBtn.closest('.comment-card');
            if (card) {
                card.remove();
                renumber(getCards());
                syncEmptyState();
            }
        });

        renumber(getCards());
        syncEmptyState();
    });
})();
