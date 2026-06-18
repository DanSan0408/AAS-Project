document.addEventListener('DOMContentLoaded', function() {
    // Pagination logic
    const STUDENTS_PER_PAGE = 4;
    let currentPage = 0;
    let totalPages = 1;
    let studentRows = []; 

    function initializePagination() {
        const tableBody = document.querySelector('#studentTable tbody');
        if (tableBody) {
            studentRows = Array.from(tableBody.querySelectorAll('.student-row'));
            if (studentRows.length > 0) {
                studentRows.forEach(row => row.setAttribute('data-filtered-match', 'true'));
                updateDisplay();
            }
        }
    }

    function updateSelectedCount() {
        const checkboxes = document.querySelectorAll('input[name="selectedStudentIds"]:checked');
        const countElement = document.getElementById('selectedCount');
        if (countElement) countElement.textContent = checkboxes.length;
    }

    function updateDisplay() {
        const currentlyVisibleRows = studentRows.filter(row => row.getAttribute('data-filtered-match') === 'true');
        totalPages = Math.ceil(currentlyVisibleRows.length / STUDENTS_PER_PAGE);

        if (currentPage >= totalPages) {
            currentPage = Math.max(0, totalPages - 1);
        }

        const startIndex = currentPage * STUDENTS_PER_PAGE;
        const endIndex = startIndex + STUDENTS_PER_PAGE;

        studentRows.forEach(row => row.style.display = 'none');
        currentlyVisibleRows.slice(startIndex, endIndex).forEach(row => row.style.display = '');

        const pageInfo = document.getElementById('pageInfo');
        if (pageInfo) {
            pageInfo.textContent = `Page ${totalPages > 0 ? currentPage + 1 : 0} of ${totalPages}`;
        }
        
        const prevBtn = document.getElementById('prevBtn');
        const nextBtn = document.getElementById('nextBtn');

        if (prevBtn) prevBtn.disabled = (currentPage === 0 || totalPages <= 1);
        if (nextBtn) nextBtn.disabled = (currentPage === totalPages - 1 || totalPages <= 1);
    }

    function filterStudents(query) {
        const filter = query.toUpperCase();
        studentRows.forEach(row => {
            const td = row.querySelector(".student-email");  
            let txtValue = td ? td.textContent || td.innerText : '';
            
            if (txtValue.toUpperCase().indexOf(filter) > -1) {
                row.setAttribute('data-filtered-match', 'true');
            } else {
                row.setAttribute('data-filtered-match', 'false');
            }
        });

        currentPage = 0;
        updateDisplay();
    }

    // Initialize UI components
    initializePagination();

    const studentSearch = document.getElementById('studentSearch');
    if (studentSearch) {
        studentSearch.addEventListener('keyup', function(e) {
            filterStudents(e.target.value);
        });
    }

    const prevBtn = document.getElementById('prevBtn');
    if (prevBtn) {
        prevBtn.addEventListener('click', () => {
            if (currentPage > 0) {
                currentPage--;
                updateDisplay();
            }
        });
    }

    const nextBtn = document.getElementById('nextBtn');
    if (nextBtn) {
        nextBtn.addEventListener('click', () => {
            if (currentPage < totalPages - 1) {
                currentPage++;
                updateDisplay();
            }
        });
    }

    document.addEventListener('change', function(e) {
        if (e.target && e.target.name === 'selectedStudentIds') {
            updateSelectedCount();
        }
    });

    // Lecturer search logic
    async function searchLecturers(searchInput) {
        const selectId = searchInput.getAttribute('data-target-select');
        const resultsDivId = searchInput.id.replace('Search', 'Results');
        const resultsDiv = document.getElementById(resultsDivId);
        const selectElement = document.getElementById(selectId);
        
        const query = searchInput.value.trim();
        if (query.length === 0) {
            if(resultsDiv) {
                resultsDiv.innerHTML = '';
                resultsDiv.style.display = 'none';
            }
            return;
        }
        
        try {
            const response = await fetch(`/admin/search-lecturers?query=${encodeURIComponent(query)}`);
            const lecturers = await response.json();
            
            if(resultsDiv) {
                resultsDiv.innerHTML = '';
                
                if (lecturers.length === 0) {
                    resultsDiv.innerHTML = '<div class="search-result-item">No lecturers found</div>';
                    resultsDiv.style.display = 'block';
                    return;
                }
                
                lecturers.forEach(lecturer => {
                    const resultItem = document.createElement('div');
                    resultItem.className = 'search-result-item';
                    resultItem.textContent = lecturer.username ? lecturer.username : lecturer.email;
                    resultItem.addEventListener('click', () => {
                        if (selectElement) selectElement.value = lecturer.id;
                        searchInput.value = '';
                        resultsDiv.innerHTML = '';
                        resultsDiv.style.display = 'none';
                    });
                    resultsDiv.appendChild(resultItem);
                });
                
                resultsDiv.style.display = 'block';
            }
        } catch (error) {
            console.error('Error searching lecturers:', error);
        }
    }

    document.querySelectorAll('.search-input').forEach(input => {
        input.addEventListener('keyup', () => searchLecturers(input));
    });
    
    document.addEventListener('click', function(event) {
        document.querySelectorAll('.search-container').forEach(container => {
            if (!container.contains(event.target)) {
                const resultsDiv = container.querySelector('.search-results');
                if (resultsDiv) {
                    resultsDiv.style.display = 'none';
                }
            }
        });
    });
});