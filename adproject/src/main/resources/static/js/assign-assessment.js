document.addEventListener('DOMContentLoaded', function () {
    const scheduledGroup = document.getElementById('scheduledOpenDateGroup');
    const openScheduled = document.getElementById('openScheduled');
    const openInstant = document.getElementById('openInstant');

    if (!scheduledGroup || !openScheduled || !openInstant) {
        return;
    }

    function toggleDateInput() {
        if (openScheduled.checked) {
            scheduledGroup.classList.add('visible');
            return;
        }

        scheduledGroup.classList.remove('visible');
        const openDateInput = document.getElementById('openDate');
        if (openDateInput) {
            openDateInput.value = '';
        }
    }

    toggleDateInput();
    openScheduled.addEventListener('change', toggleDateInput);
    openInstant.addEventListener('change', toggleDateInput);
});