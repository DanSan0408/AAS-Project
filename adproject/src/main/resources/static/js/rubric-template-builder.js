(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', () => {
        const blueprintForm = document.getElementById('blueprintForm');
        const structureInput = document.getElementById('structureData');
        const addSubRubricBtn = document.getElementById('addSubRubricBtn');
        const addDirectRatingBtn = document.getElementById('addDirectRatingBtn');
        const addCommentBtn = document.getElementById('addCommentBtn');

        if (!blueprintForm || !structureInput || !addSubRubricBtn || !addDirectRatingBtn || !addCommentBtn) {
            return;
        }

        let blueprint = {
            subRubrics: [],
            directRatingsCount: 0,
            commentsCount: 0
        };

        const serializeBlueprint = () => {
            structureInput.value = JSON.stringify(blueprint);
        };

        const ensureValidBlueprint = () => {
            if (!Array.isArray(blueprint.subRubrics)) {
                blueprint.subRubrics = [];
            }
            if (typeof blueprint.directRatingsCount !== 'number') {
                blueprint.directRatingsCount = 0;
            }
            if (typeof blueprint.commentsCount !== 'number') {
                blueprint.commentsCount = 0;
            }
        };

        const renderBlueprint = () => {
            ensureValidBlueprint();

            const subContainer = document.getElementById('blueprint-sub-rubrics');
            const directRatingsContainer = document.getElementById('blueprint-direct-ratings');
            const commentsContainer = document.getElementById('blueprint-comments');

            if (!subContainer || !directRatingsContainer || !commentsContainer) {
                return;
            }

            subContainer.innerHTML = '';
            blueprint.subRubrics.forEach((subRubric, index) => {
                let ratingsHtml = '';
                for (let ratingIndex = 0; ratingIndex < subRubric.ratingsCount; ratingIndex++) {
                    ratingsHtml += `<div class="rating-card"><span style="font-weight:600; font-size:0.9rem;">Rating Row ${ratingIndex + 1}</span></div>`;
                }

                subContainer.innerHTML += `
                    <div class="sub-rubric-card">
                        <button type="button" class="remove-btn remove-sub-rubric-btn" data-action="remove-sub-rubric" data-index="${index}">Remove Sub-Rubric</button>
                        <h4 style="margin-top:0; margin-bottom:15px;">Sub-Rubric Block ${index + 1}</h4>
                        <div class="ratings-section">
                            <div class="section-header" style="display: flex; justify-content: space-between; align-items: center; flex-wrap: wrap; gap: 10px; margin-bottom: 10px;">
                                <h5 style="margin:0; font-size:1rem;">Ratings (${subRubric.ratingsCount})</h5>
                                <div>
                                    <button type="button" class="btn btn-sm btn-danger" data-action="remove-rating-from-sub" data-index="${index}">-</button>
                                    <button type="button" class="btn btn-sm btn-secondary" data-action="add-rating-to-sub" data-index="${index}">+</button>
                                </div>
                            </div>
                            <div style="margin-bottom:10px;">${ratingsHtml}</div>
                        </div>
                    </div>
                `;
            });

            directRatingsContainer.innerHTML = '';
            for (let index = 0; index < blueprint.directRatingsCount; index++) {
                directRatingsContainer.innerHTML += `
                    <div class="rating-card">
                        <button type="button" class="remove-btn remove-direct-rating-btn" data-action="remove-direct-rating">Remove</button>
                        <p style="margin:0; font-weight:600;">Direct Rating Block ${index + 1}</p>
                    </div>
                `;
            }
            if (blueprint.directRatingsCount === 0) {
                directRatingsContainer.innerHTML = '<p class="info-text" style="margin-top:0;">No direct ratings added.</p>';
            }

            commentsContainer.innerHTML = '';
            for (let index = 0; index < blueprint.commentsCount; index++) {
                commentsContainer.innerHTML += `
                    <div class="comment-card">
                        <button type="button" class="remove-btn remove-rubric-comment-btn" data-action="remove-comment">Remove</button>
                        <p style="margin:0; font-weight:600;">Comment Question Block ${index + 1}</p>
                    </div>
                `;
            }
            if (blueprint.commentsCount === 0) {
                commentsContainer.innerHTML = '<p class="info-text" style="margin-top:0;">No rubric-specific comments added.</p>';
            }

            serializeBlueprint();
        };

        const addSubRubric = () => {
            blueprint.subRubrics.push({ ratingsCount: 1 });
            renderBlueprint();
        };

        const removeSubRubric = (index) => {
            blueprint.subRubrics.splice(index, 1);
            renderBlueprint();
        };

        const addRatingToSub = (index) => {
            if (!blueprint.subRubrics[index]) {
                return;
            }
            blueprint.subRubrics[index].ratingsCount += 1;
            renderBlueprint();
        };

        const removeRatingFromSub = (index) => {
            if (!blueprint.subRubrics[index] || blueprint.subRubrics[index].ratingsCount <= 0) {
                return;
            }
            blueprint.subRubrics[index].ratingsCount -= 1;
            renderBlueprint();
        };

        const addDirectRating = () => {
            blueprint.directRatingsCount += 1;
            renderBlueprint();
        };

        const removeDirectRating = () => {
            if (blueprint.directRatingsCount > 0) {
                blueprint.directRatingsCount -= 1;
                renderBlueprint();
            }
        };

        const addComment = () => {
            blueprint.commentsCount += 1;
            renderBlueprint();
        };

        const removeComment = () => {
            if (blueprint.commentsCount > 0) {
                blueprint.commentsCount -= 1;
                renderBlueprint();
            }
        };

        const loadExistingBlueprint = () => {
            const existingData = structureInput.value;
            if (existingData && existingData.trim().length > 0 && existingData !== 'null') {
                try {
                    const parsed = JSON.parse(existingData);
                    if (parsed.rubrics && parsed.rubrics.length > 0) {
                        const legacy = parsed.rubrics[0];
                        blueprint.subRubrics = Array.from({ length: legacy.subRubricsCount || 0 }).map(() => ({ ratingsCount: legacy.ratingsCount || 0 }));
                        blueprint.directRatingsCount = legacy.subRubricsCount === 0 ? (legacy.ratingsCount || 0) : 0;
                        blueprint.commentsCount = legacy.commentsCount || 0;
                    } else if (parsed.subRubrics || parsed.directRatingsCount !== undefined || parsed.commentsCount !== undefined) {
                        blueprint = parsed;
                    }
                } catch (error) {
                    console.error('Could not parse JSON', error);
                }
            } else {
                blueprint.subRubrics.push({ ratingsCount: 4 });
            }
        };

        loadExistingBlueprint();
        renderBlueprint();

        addSubRubricBtn.addEventListener('click', addSubRubric);
        addDirectRatingBtn.addEventListener('click', addDirectRating);
        addCommentBtn.addEventListener('click', addComment);
        blueprintForm.addEventListener('submit', serializeBlueprint);

        document.body.addEventListener('click', function (event) {
            const button = event.target.closest('button');
            if (!button) {
                return;
            }

            const action = button.dataset.action;
            const index = Number.parseInt(button.dataset.index, 10);

            if (action === 'remove-sub-rubric') {
                removeSubRubric(index);
            } else if (action === 'add-rating-to-sub') {
                addRatingToSub(index);
            } else if (action === 'remove-rating-from-sub') {
                removeRatingFromSub(index);
            } else if (action === 'remove-direct-rating') {
                removeDirectRating();
            } else if (action === 'remove-comment') {
                removeComment();
            }
        });
    });
})();