document.addEventListener('DOMContentLoaded', function () {
    const container = document.getElementById('sub-rubrics-container');
    const directRatingsContainer = document.getElementById('direct-ratings-container');
    const rubricCommentsContainer = document.getElementById('rubric-comments-container');
    const rubricCommentsEmpty = document.getElementById('rubric-comments-empty');
    const rubricMarksInput = document.getElementById('rubricMarks');
    const cloMarksInput = document.getElementById('cloMarks');
    const rubricForm = document.getElementById('rubricForm');

    if (!container || !directRatingsContainer || !rubricCommentsContainer || !rubricForm) {
        return;
    }

    let subRubricIndex = Number(rubricForm.dataset.subrubricCount || '0');
    let directRatingIndex = Number(rubricForm.dataset.directRatingCount || '0');
    let rubricCommentIndex = Number(rubricForm.dataset.commentCount || '0');
    const templateApiUrl = rubricForm.dataset.templateApiUrl || '/rubrics/templates/api/';

    const subRubricRatingIndices = {};
    document.querySelectorAll('.sub-rubric-card').forEach((card, index) => {
        subRubricRatingIndices[index] = card.querySelectorAll('.rating-card').length;
    });

    if (rubricMarksInput) {
        rubricMarksInput.addEventListener('input', function () {
            if (cloMarksInput) {
                cloMarksInput.value = this.value;
            }
        });
    }

    function updateSubRubricMarks(subIndex) {
        const cont = document.querySelector(`.sub-ratings-container[data-subindex="${subIndex}"]`);
        if (!cont) return;

        let maxMark = 0;
        cont.querySelectorAll('.sub-rating-marks').forEach(input => {
            const value = parseFloat(input.value) || 0;
            if (value > maxMark) {
                maxMark = value;
            }
        });

        const out = document.querySelector(`.sub-rubric-marks-auto[data-subindex="${subIndex}"]`);
        if (out) {
            out.value = maxMark.toFixed(2);
        }
    }

    function createSubRubricCard() {
        const card = document.createElement('div');
        card.className = 'sub-rubric-card';
        card.innerHTML = `
            <button type="button" class="remove-btn remove-sub-rubric-btn" title="Remove sub-rubric" aria-label="Remove sub-rubric"><span class="rf-icon rf-icon-close" aria-hidden="true"></span></button>
            <input type="hidden" name="subRubrics[${subRubricIndex}].id" value="" />
            <div class="form-group"><label>Sub-Rubric Name</label><input type="text" name="subRubrics[${subRubricIndex}].name" class="sub-rubric-name" maxlength="10000"></div>
            <div class="form-group"><label>Description</label><textarea rows="2" name="subRubrics[${subRubricIndex}].description"></textarea></div>
            <div class="form-group"><label>Marks (Auto-calculated)</label><input type="number" step="0.01" name="subRubrics[${subRubricIndex}].marks" readonly class="sub-rubric-marks-auto" data-subindex="${subRubricIndex}" value="0"></div>
            <div class="ratings-section"><h4>Ratings</h4><div class="sub-ratings-container" data-subindex="${subRubricIndex}"></div>
            <button type="button" class="btn btn-sm btn-secondary add-sub-rating-btn" data-subindex="${subRubricIndex}" style="margin-top: 10px; width: 100%;"><span class="rf-icon rf-icon-plus" aria-hidden="true"></span><span>Add Rating</span></button></div>
        `;
        return card;
    }

    const addSubBtn = document.getElementById('addSubRubricBtn');
    if (addSubBtn) {
        addSubBtn.addEventListener('click', function () {
            container.appendChild(createSubRubricCard());
            subRubricRatingIndices[subRubricIndex] = 0;
            subRubricIndex += 1;
        });
    }

    const addDirectBtn = document.getElementById('addDirectRatingBtn');
    if (addDirectBtn) {
        addDirectBtn.addEventListener('click', function () {
            const card = document.createElement('div');
            card.className = 'rating-card';
            card.innerHTML = `
                <button type="button" class="remove-btn remove-direct-rating-btn" title="Remove direct rating" aria-label="Remove direct rating"><span class="rf-icon rf-icon-close" aria-hidden="true"></span></button>
                <div class="rating-row form-grid">
                    <div class="form-group col-grow"><label>Rating Name *</label><input type="text" name="ratings[${directRatingIndex}].name" required maxlength="10000"></div>
                    <div class="form-group col-fixed"><label>Marks *</label><input type="number" step="0.01" name="ratings[${directRatingIndex}].marks" required class="direct-rating-marks"></div>
                </div>
                <div class="form-group"><label>Description</label><textarea rows="2" name="ratings[${directRatingIndex}].description"></textarea></div>
            `;
            directRatingsContainer.appendChild(card);
            directRatingIndex += 1;
        });
    }

    const addCommentBtn = document.getElementById('addRubricCommentBtn');
    if (addCommentBtn) {
        addCommentBtn.addEventListener('click', function () {
            if (rubricCommentsEmpty) {
                rubricCommentsEmpty.style.display = 'none';
            }

            const card = document.createElement('div');
            card.className = 'comment-card';
            card.innerHTML = `
                <button type="button" class="remove-btn remove-rubric-comment-btn" title="Remove comment" aria-label="Remove comment"><span class="rf-icon rf-icon-close" aria-hidden="true"></span></button>
                <div class="form-group"><label>Comment Question</label><textarea name="rubricCommentLabels[${rubricCommentIndex}]" rows="2"></textarea></div>
                <div class="rating-row form-grid">
                    <div class="form-group col-grow"><label>Min Length</label><input type="number" name="rubricCommentMinLengths[${rubricCommentIndex}]" value="20"></div>
                    <div class="form-group col-grow" style="padding-top:25px;"><label><input type="checkbox" name="rubricCommentAnonymousFlags[${rubricCommentIndex}]" value="true"> Anonymous</label></div>
                </div>
            `;
            rubricCommentsContainer.appendChild(card);
            rubricCommentIndex += 1;
        });
    }

    document.addEventListener('click', function (event) {
        const subRatingButton = event.target.closest('.add-sub-rating-btn');
        if (subRatingButton) {
            const subIndex = subRatingButton.getAttribute('data-subindex');
            const ratingsContainer = document.querySelector(`.sub-ratings-container[data-subindex="${subIndex}"]`);
            if (!ratingsContainer) return;

            const ratingIndex = subRubricRatingIndices[subIndex] || 0;
            const card = document.createElement('div');
            card.className = 'rating-card';
            card.innerHTML = `
                <button type="button" class="remove-btn remove-rating-btn" title="Remove rating" aria-label="Remove rating"><span class="rf-icon rf-icon-close" aria-hidden="true"></span></button>
                <div class="rating-row form-grid">
                    <div class="form-group col-grow"><label>Rating Name *</label><input type="text" name="subRubrics[${subIndex}].ratings[${ratingIndex}].name" required maxlength="10000"></div>
                    <div class="form-group col-fixed"><label>Marks</label><input type="number" step="0.01" name="subRubrics[${subIndex}].ratings[${ratingIndex}].marks" class="rating-marks sub-rating-marks" data-subindex="${subIndex}"></div>
                </div>
                <div class="form-group"><label>Description</label><textarea rows="1" name="subRubrics[${subIndex}].ratings[${ratingIndex}].description"></textarea></div>
            `;
            ratingsContainer.appendChild(card);
            subRubricRatingIndices[subIndex] = ratingIndex + 1;
            return;
        }

        if (event.target.closest('.remove-sub-rubric-btn')) {
            const card = event.target.closest('.sub-rubric-card');
            if (card) card.remove();
            return;
        }

        if (event.target.closest('.remove-rating-btn')) {
            const card = event.target.closest('.rating-card');
            const ratingsContainer = card ? card.closest('.sub-ratings-container') : null;
            if (card) card.remove();
            if (ratingsContainer) {
                updateSubRubricMarks(ratingsContainer.getAttribute('data-subindex'));
            }
            return;
        }

        if (event.target.closest('.remove-direct-rating-btn')) {
            const card = event.target.closest('.rating-card');
            if (card) card.remove();
            return;
        }

        if (event.target.closest('.remove-rubric-comment-btn')) {
            const card = event.target.closest('.comment-card');
            if (card) card.remove();
        }
    });

    document.addEventListener('input', function (event) {
        if (event.target.classList.contains('sub-rating-marks')) {
            updateSubRubricMarks(event.target.getAttribute('data-subindex'));
        }
    });

    const applyTemplateBtn = document.getElementById('applyTemplateBtn');
    if (applyTemplateBtn) {
        applyTemplateBtn.addEventListener('click', function () {
            const templateId = document.getElementById('templateSelect').value;
            if (!templateId) {
                alert('Please select a blueprint first.');
                return;
            }

            if (subRubricIndex > 0 || directRatingIndex > 0) {
                if (!confirm('Applying a blueprint will not clear existing rows, but will append new ones. Continue?')) {
                    return;
                }
            }

            fetch(templateApiUrl + templateId)
                .then(response => {
                    if (!response.ok) throw new Error('Network response was not ok');
                    return response.text();
                })
                .then(text => {
                    if (!text) return;

                    let data;
                    try {
                        data = JSON.parse(text);
                        if (typeof data === 'string') {
                            data = JSON.parse(data);
                        }
                    } catch (error) {
                        console.error('Error parsing blueprint JSON:', error);
                        alert('Blueprint data is corrupted or invalid format.');
                        return;
                    }

                    let blueprint = data;
                    if (data.rubrics && data.rubrics.length > 0) {
                        const first = data.rubrics[0];
                        blueprint = {
                            subRubrics: Array.from({ length: first.subRubricsCount || 0 }).map(() => ({ ratingsCount: first.ratingsCount || 0 })),
                            directRatingsCount: (first.subRubricsCount === 0) ? (first.ratingsCount || 0) : 0,
                            commentsCount: first.commentsCount || 0
                        };
                    }

                    if (blueprint.subRubrics) {
                        blueprint.subRubrics.forEach(subRubric => {
                            const currentSubIndex = subRubricIndex;
                            if (addSubBtn) addSubBtn.click();

                            setTimeout(() => {
                                const ratingButton = document.querySelector(`.add-sub-rating-btn[data-subindex="${currentSubIndex}"]`);
                                if (ratingButton) {
                                    for (let count = 0; count < (subRubric.ratingsCount || 0); count += 1) {
                                        ratingButton.click();
                                    }
                                }
                            }, 20);
                        });
                    }

                    for (let index = 0; index < (blueprint.directRatingsCount || 0); index += 1) {
                        if (addDirectBtn) addDirectBtn.click();
                    }

                    for (let index = 0; index < (blueprint.commentsCount || 0); index += 1) {
                        if (addCommentBtn) addCommentBtn.click();
                    }

                    setTimeout(() => {
                        alert('Template Applied successfully!');
                    }, 100);
                })
                .catch(error => {
                    console.error('Error fetching template:', error);
                    alert('Failed to load blueprint data.');
                });
        });
    }
});
