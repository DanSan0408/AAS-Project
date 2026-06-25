document.addEventListener("DOMContentLoaded", function() {
    const courseCodeInput = document.getElementById("courseCode");
    if (!courseCodeInput) return;

    // Create feedback element if it doesn't exist
    let feedbackEl = document.getElementById("courseCodeFeedback");
    if (!feedbackEl) {
        feedbackEl = document.createElement("div");
        feedbackEl.id = "courseCodeFeedback";
        feedbackEl.className = "feedback-text";
        courseCodeInput.parentNode.appendChild(feedbackEl);
    }

    const idInput = document.getElementById("id");
    const excludeId = idInput ? idInput.value : "";

    let timeout = null;

    courseCodeInput.addEventListener("input", function() {
        clearTimeout(timeout);
        const code = courseCodeInput.value.trim();

        if (!code) {
            courseCodeInput.classList.remove("valid-code", "invalid-code");
            feedbackEl.textContent = "";
            feedbackEl.className = "feedback-text";
            return;
        }

        timeout = setTimeout(() => {
            let url = `/admin/courses/check-code?courseCode=${encodeURIComponent(code)}`;
            if (excludeId) {
                url += `&excludeId=${encodeURIComponent(excludeId)}`;
            }

            fetch(url)
                .then(response => response.json())
                .then(data => {
                    if (data.exists) {
                        courseCodeInput.classList.remove("valid-code");
                        courseCodeInput.classList.add("invalid-code");
                        feedbackEl.textContent = "The course exists, and the course ID has already been used. Please assign a new course ID.";
                        feedbackEl.className = "feedback-text invalid";
                    } else {
                        courseCodeInput.classList.remove("invalid-code");
                        courseCodeInput.classList.add("valid-code");
                        feedbackEl.textContent = "Course ID is available.";
                        feedbackEl.className = "feedback-text valid";
                    }
                })
                .catch(err => {
                    console.error("Error checking course code:", err);
                });
        }, 300); // 300ms debounce
    });

    // Run initial validation check if input has value (for edit page)
    if (courseCodeInput.value.trim()) {
        courseCodeInput.dispatchEvent(new Event("input"));
    }
});
