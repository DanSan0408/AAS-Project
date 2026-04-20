(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', function () {
        const assignmentModeRadios = document.querySelectorAll('input[name="assignmentMode"]');
        const rubricSection = document.getElementById('rubricModeSection');
        const groupSection = document.getElementById('groupModeSection');

        if (!assignmentModeRadios.length || !rubricSection || !groupSection) {
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
        };

        assignmentModeRadios.forEach(radio => radio.addEventListener('change', toggleMode));
        toggleMode();

        document.body.addEventListener('click', function (event) {
            const addBtn = event.target.closest('[data-action="add-lecturer-field"]');
            if (addBtn) {
                const groupId = addBtn.dataset.groupId;
                const container = document.querySelector(`.lecturer-assignments-container[data-group-id="${groupId}"]`);
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
                newSelect.name = `group_${groupId}_lecturer_${newIndex}`;
                newSelect.value = 'none';

                const removeButton = document.createElement('button');
                removeButton.type = 'button';
                removeButton.className = 'btn-icon-remove';
                removeButton.dataset.action = 'remove-lecturer-field';
                removeButton.title = 'Remove Lecturer';
                removeButton.innerHTML = '<i class="fas fa-times"></i>';

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