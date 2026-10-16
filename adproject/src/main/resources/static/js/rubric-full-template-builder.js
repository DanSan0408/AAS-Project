(function () {
    'use strict';

    document.addEventListener('DOMContentLoaded', () => {
        const fullTemplateForm = document.getElementById('fullTemplateForm');
        const structureInput = document.getElementById('structureData');
        const subRubricsContainer = document.getElementById('sub-rubrics-container');
        const directRatingsContainer = document.getElementById('direct-ratings-container');
        const rubricCommentsContainer = document.getElementById('rubric-comments-container');

        const addSubRubricBtn = document.getElementById('addSubRubricBtn');
        const addDirectRatingBtn = document.getElementById('addDirectRatingBtn');
        const addCommentBtn = document.getElementById('addCommentBtn'); // Wait, button id is addRubricCommentBtn in HTML
        const addRubricCommentBtn = document.getElementById('addRubricCommentBtn');

        let subRubricIndex = 0;
        let directRatingIndex = 0;
        let commentIndex = 0;

        const serializeForm = () => {
            const data = {
                templateName: document.getElementById('name') ? document.getElementById('name').value : '',
                templateDescription: document.getElementById('description') ? document.getElementById('description').value : '',
                assessmentType: document.getElementById('assessmentTypeSelect').value,
                marks: document.getElementById('rubricMarks').value,
                clo: document.getElementById('rubricClo').value,
                subRubrics: [],
                directRatings: [],
                comments: []
            };

            // Parse Sub-Rubrics
            document.querySelectorAll('.sub-rubric-card').forEach((card) => {
                const subRubric = {
                    name: card.querySelector('.sub-rubric-name').value,
                    description: card.querySelector('.sub-rubric-desc').value,
                    ratings: []
                };

                card.querySelectorAll('.rating-card').forEach((ratingCard) => {
                    subRubric.ratings.push({
                        name: ratingCard.querySelector('.rating-name').value,
                        marks: ratingCard.querySelector('.rating-marks').value,
                        description: ratingCard.querySelector('.rating-desc').value
                    });
                });
                data.subRubrics.push(subRubric);
            });

            // Parse Direct Ratings
            document.querySelectorAll('.direct-rating-card').forEach((card) => {
                data.directRatings.push({
                    name: card.querySelector('.rating-name').value,
                    marks: card.querySelector('.rating-marks').value,
                    description: card.querySelector('.rating-desc').value
                });
            });

            // Parse Comments
            document.querySelectorAll('.comment-card').forEach((card) => {
                data.comments.push({
                    label: card.querySelector('.comment-label').value,
                    minLength: card.querySelector('.comment-minlength').value,
                    isAnonymous: card.querySelector('.comment-anonymous').checked
                });
            });

            structureInput.value = JSON.stringify(data);
        };

        const createSubRubricHtml = (sub) => {
            const idx = subRubricIndex++;
            const card = document.createElement('div');
            card.className = 'sub-rubric-card card';
            card.style.marginBottom = '15px';
            
            let ratingsHtml = '';
            if (sub && sub.ratings) {
                sub.ratings.forEach(r => {
                    ratingsHtml += createRatingHtml(r, true);
                });
            }

            card.innerHTML = `
                <button type="button" class="remove-btn remove-sub-rubric-btn" title="Remove sub-rubric" aria-label="Remove sub-rubric"><span class="rf-icon rf-icon-close" aria-hidden="true"></span></button>
                <div class="form-group">
                    <label>Sub-Rubric Name</label>
                    <input type="text" class="sub-rubric-name" value="${sub ? escapeHtml(sub.name) : ''}">
                </div>
                <div class="form-group">
                    <label>Description</label>
                    <textarea class="sub-rubric-desc" rows="2">${sub ? escapeHtml(sub.description) : ''}</textarea>
                </div>
                <div class="ratings-section">
                    <h4>Ratings</h4>
                    <div class="sub-ratings-container">${ratingsHtml}</div>
                    <button type="button" class="btn btn-sm btn-secondary add-sub-rating-btn" style="margin-top: 10px; width: 100%;"><i class="fas fa-plus"></i> Add Rating</button>
                </div>
            `;
            return card;
        };

        const createRatingHtml = (r, isSubRating) => {
            const className = isSubRating ? 'rating-card card' : 'direct-rating-card rating-card card';
            const removeClass = isSubRating ? 'remove-rating-btn' : 'remove-direct-rating-btn';
            
            return `
                <div class="${className}" style="margin-bottom: 10px; padding: 10px; background-color: #f8fafc; border: 1px solid #e2e8f0; border-radius: 6px;">
                    <button type="button" class="remove-btn ${removeClass}" title="Remove rating" aria-label="Remove rating"><span class="rf-icon rf-icon-close" aria-hidden="true"></span></button>
                    <div class="rating-row form-grid">
                        <div class="form-group col-grow">
                            <label>Rating Name *</label>
                            <input type="text" class="rating-name" value="${r ? escapeHtml(r.name) : ''}" required>
                        </div>
                        <div class="form-group col-fixed">
                            <label>Marks *</label>
                            <input type="number" step="0.01" class="rating-marks" value="${r ? escapeHtml(r.marks) : ''}" required>
                        </div>
                    </div>
                    <div class="form-group">
                        <label>Description</label>
                        <textarea class="rating-desc" rows="1">${r ? escapeHtml(r.description) : ''}</textarea>
                    </div>
                </div>
            `;
        };

        const createCommentHtml = (c) => {
            const card = document.createElement('div');
            card.className = 'comment-card card';
            card.style.marginBottom = '15px';
            
            card.innerHTML = `
                <button type="button" class="remove-btn remove-comment-btn" title="Remove comment" aria-label="Remove comment"><span class="rf-icon rf-icon-close" aria-hidden="true"></span></button>
                <div class="form-group">
                    <label>Comment Question</label>
                    <textarea class="comment-label" rows="2">${c ? escapeHtml(c.label) : ''}</textarea>
                </div>
                <div class="rating-row form-grid">
                    <div class="form-group col-grow">
                        <label>Min Length</label>
                        <input type="number" class="comment-minlength" value="${c ? escapeHtml(c.minLength) : '20'}">
                    </div>
                    <div class="form-group col-grow" style="padding-top:25px;">
                        <label><input type="checkbox" class="comment-anonymous" ${c && c.isAnonymous ? 'checked' : ''}> Anonymous</label>
                    </div>
                </div>
            `;
            return card;
        };

        const escapeHtml = (unsafe) => {
            if (!unsafe) return '';
            return (unsafe + '').replace(/[&<"']/g, function (m) {
                switch (m) {
                    case '&': return '&amp;';
                    case '<': return '&lt;';
                    case '"': return '&quot;';
                    case "'": return '&#039;';
                    default: return m;
                }
            });
        };

        const loadExistingBlueprint = () => {
            const existingData = structureInput.value;
            if (existingData && existingData.trim().length > 0 && existingData !== 'null') {
                try {
                    const parsed = JSON.parse(existingData);
                    
                    if (parsed.assessmentType) document.getElementById('assessmentTypeSelect').value = parsed.assessmentType;
                    if (parsed.marks) document.getElementById('rubricMarks').value = parsed.marks;
                    if (parsed.clo) document.getElementById('rubricClo').value = parsed.clo;

                    if (parsed.subRubrics) {
                        parsed.subRubrics.forEach(sub => {
                            subRubricsContainer.appendChild(createSubRubricHtml(sub));
                        });
                    }
                    if (parsed.directRatings) {
                        parsed.directRatings.forEach(r => {
                            const wrapper = document.createElement('div');
                            wrapper.innerHTML = createRatingHtml(r, false);
                            directRatingsContainer.appendChild(wrapper.firstElementChild);
                        });
                    }
                    if (parsed.comments) {
                        parsed.comments.forEach(c => {
                            rubricCommentsContainer.appendChild(createCommentHtml(c));
                        });
                    }
                } catch (error) {
                    console.error('Could not parse JSON', error);
                }
            } else {
                // Default empty state
                // Admin will configure sub-rubrics manually
            }
        };

        loadExistingBlueprint();

        if (addSubRubricBtn) {
            addSubRubricBtn.addEventListener('click', () => {
                subRubricsContainer.appendChild(createSubRubricHtml());
            });
        }
        if (addDirectRatingBtn) {
            addDirectRatingBtn.addEventListener('click', () => {
                const wrapper = document.createElement('div');
                wrapper.innerHTML = createRatingHtml(null, false);
                directRatingsContainer.appendChild(wrapper.firstElementChild);
            });
        }
        if (addRubricCommentBtn) {
            addRubricCommentBtn.addEventListener('click', () => {
                rubricCommentsContainer.appendChild(createCommentHtml(null));
            });
        }

        document.body.addEventListener('click', function (e) {
            if (e.target.closest('.add-sub-rating-btn')) {
                const container = e.target.closest('.ratings-section').querySelector('.sub-ratings-container');
                const wrapper = document.createElement('div');
                wrapper.innerHTML = createRatingHtml(null, true);
                container.appendChild(wrapper.firstElementChild);
            } else if (e.target.closest('.remove-sub-rubric-btn')) {
                e.target.closest('.sub-rubric-card').remove();
            } else if (e.target.closest('.remove-rating-btn')) {
                e.target.closest('.rating-card').remove();
            } else if (e.target.closest('.remove-direct-rating-btn')) {
                e.target.closest('.direct-rating-card').remove();
            } else if (e.target.closest('.remove-comment-btn')) {
                e.target.closest('.comment-card').remove();
            }
        });

        fullTemplateForm.addEventListener('submit', serializeForm);
    });
})();
