document.addEventListener('DOMContentLoaded', function() {
    var hiddenInput = document.getElementById('selectedStudentIdsInput');
    if (!hiddenInput) return;
    
    var initialIdsString = hiddenInput.value;
    var currentStudentIds = [];
    
    if (initialIdsString && initialIdsString.trim() !== '') {
        currentStudentIds = initialIdsString.split(',').map(function(id) {
            return parseInt(id.trim());
        }).filter(function(id) {
            return !isNaN(id);
        });
    }
    
    var studentLookupMap = {};
    var addStudentSelect = document.getElementById('addStudentSelect');
    if (addStudentSelect) {
        Array.from(addStudentSelect.options).forEach(function(option) {
            if (option.value) {
                var id = parseInt(option.value);
                studentLookupMap[id] = { id: id, email: option.text };
            }
        });
    }
    
    var sizeSpan = document.querySelector('h2 span:last-of-type');
    var maxGroupSize = sizeSpan ? parseInt(sizeSpan.textContent) : 0;
    
    // DOM elements
    var studentList = document.getElementById('student-display-list');
    var addStudentButton = document.getElementById('addStudentButton');
    var sizeWarning = document.getElementById('sizeWarning');
    var currentGroupSizeElement = document.getElementById('currentGroupSize');

    function updateHiddenInput() {
        if (hiddenInput) {
            hiddenInput.value = currentStudentIds.join(',');
        }
    }

    function updateGroupSizeDisplay() {
        if (currentGroupSizeElement) {
            currentGroupSizeElement.textContent = currentStudentIds.length;
        }
        
        if (addStudentSelect && addStudentButton && sizeWarning) {
            if (currentStudentIds.length >= maxGroupSize) {
                addStudentSelect.disabled = true;
                addStudentButton.disabled = true;
                sizeWarning.style.display = 'inline';
            } else {
                addStudentSelect.disabled = false;
                addStudentButton.disabled = false;
                sizeWarning.style.display = 'none';
            }
        }
    }

    function renderStudents() {
        if (!studentList) return;
        studentList.innerHTML = '';

        if (currentStudentIds.length === 0) {
            var emptyMessage = document.createElement('li');
            emptyMessage.textContent = 'No students selected yet.';
            emptyMessage.style.fontStyle = 'italic';
            emptyMessage.style.color = '#95a5a6';
            studentList.appendChild(emptyMessage);
            updateHiddenInput();
            updateGroupSizeDisplay();
            return;
        }

        currentStudentIds.forEach(function(studentId) {
            var student = studentLookupMap[studentId];
            var name = student ? student.email : 'Student ID: ' + studentId + ' (Not Found)';
            
            var listItem = document.createElement('li');
            listItem.className = 'student-item';
            
            var nameSpan = document.createElement('span');
            nameSpan.textContent = name;

            var removeButton = document.createElement('button');
            removeButton.type = 'button';
            removeButton.className = 'remove-student-btn';
            removeButton.innerHTML = '<i class="fas fa-times"></i> Remove';
            removeButton.setAttribute('data-student-id', studentId);

            listItem.appendChild(nameSpan);
            listItem.appendChild(removeButton);
            studentList.appendChild(listItem);
        });
        
        if (addStudentSelect) {
            Array.from(addStudentSelect.options).forEach(function(option) {
                if (option.value) {
                    var id = parseInt(option.value);
                    option.style.display = currentStudentIds.includes(id) ? 'none' : '';
                }
            });
        }
        
        updateHiddenInput();
        updateGroupSizeDisplay();
    }

    renderStudents();

    // Event Delegation for Remove buttons
    if (studentList) {
        studentList.addEventListener('click', function(event) {
            const removeBtn = event.target.closest('.remove-student-btn');
            if (removeBtn) {
                const studentId = parseInt(removeBtn.getAttribute('data-student-id'));
                currentStudentIds = currentStudentIds.filter(id => id !== studentId);
                renderStudents();
            }
        });
    }

    if (addStudentButton && addStudentSelect) {
        addStudentButton.addEventListener('click', function() {
            if (currentStudentIds.length >= maxGroupSize) {
                return; 
            }
            
            var selectedId = addStudentSelect.value;
            if (selectedId) {
                var studentId = parseInt(selectedId);
                if (!currentStudentIds.includes(studentId)) {
                    currentStudentIds.push(studentId);
                    addStudentSelect.value = ""; 
                    renderStudents();
                }
            }
        });
    }
});