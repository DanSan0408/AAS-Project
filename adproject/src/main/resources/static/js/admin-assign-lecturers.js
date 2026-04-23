(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        const assignmentModeRadios = document.querySelectorAll('input[name="assignmentMode"]');
        const rubricSection = document.getElementById('rubricModeSection');
        const groupSection = document.getElementById('groupModeSection');
        const studentSection = document.getElementById('studentModeSection');

        if (!assignmentModeRadios.length || !rubricSection || !groupSection || !studentSection) {
            return;
        }

        const setSectionState = (section, isVisible) => {
            section.style.display = isVisible ? 'block' : 'none';
            section.querySelectorAll('input, select, textarea, button').forEach(element => {
                element.disabled = !isVisible;
            });
        };

        const toggleMode = () => {
            const selectedMode = document.querySelector('input[name="assignmentMode"]:checked');
            const mode = selectedMode ? selectedMode.value : 'GROUP';

            setSectionState(rubricSection, mode === 'RUBRIC');
            setSectionState(groupSection, mode === 'GROUP');
            setSectionState(studentSection, mode === 'STUDENT');
        };

        assignmentModeRadios.forEach(radio => radio.addEventListener('change', toggleMode));
        toggleMode();

        document.body.addEventListener('click', function (event) {
            const addBtn = event.target.closest('[data-action="add-lecturer-field"]');
            if (addBtn) {
                const groupId = addBtn.dataset.groupId;
                const studentId = addBtn.dataset.studentId;
                const containerSelector = groupId
                    ? `.lecturer-assignments-container[data-group-id="${groupId}"]`
                    : `.lecturer-assignments-container[data-student-id="${studentId}"]`;
                const container = document.querySelector(containerSelector);
                if (!container) {
                    return;
                }

                const templateSelect = container.querySelector('select');
                if (!templateSelect) {
                    return;
                }

                const newIndex = container.querySelectorAll('.lecturer-row').length;
                const newField = document.createElement('div');
                newField.className = 'lecturer-row';
                const newSelect = templateSelect.cloneNode(true);
                newSelect.name = groupId
                    ? `group_${groupId}_lecturer_${newIndex}`
                    : `student_${studentId}_lecturer_${newIndex}`;
                newSelect.value = 'none';

                const removeButton = document.createElement('button');
                removeButton.type = 'button';
                removeButton.className = 'btn-icon-remove';
                removeButton.dataset.action = 'remove-lecturer-field';
                removeButton.title = 'Remove Lecturer';
                removeButton.setAttribute('aria-label', 'Remove Lecturer');
                removeButton.innerHTML = '<svg viewBox="0 0 24 24" width="16" height="16" aria-hidden="true" focusable="false"><path fill="currentColor" d="M9 3h6a1 1 0 0 1 1 1v1h4v2h-1v13a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V7H4V5h4V4a1 1 0 0 1 1-1Zm1 2v0h4V5h-4Zm-3 2v13h10V7Zm2 2h2v9H9Zm4 0h2v9h-2Z"/></svg>';

                newField.appendChild(newSelect);
                newField.appendChild(removeButton);
                container.appendChild(newField);
            }

            const removeBtn = event.target.closest('[data-action="remove-lecturer-field"]');
            if (removeBtn) {
                const field = removeBtn.closest('.lecturer-row');
                if (!field) {
                    return;
                }

                const container = field.parentElement;
                if (container.querySelectorAll('.lecturer-row').length > 1) {
                    field.remove();
                } else {
                    const select = field.querySelector('select');
                    if (select) {
                        select.value = 'none';
                    }
                }
            }
        });
    });
})();