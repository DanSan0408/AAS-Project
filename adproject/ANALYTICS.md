# Data Analytics

Open **Data Analytics** from the admin sidebar, or **Open Data Analytics**
from Data Views. The page is available to admins and super-admins at
`/admin/analytics/lecturer-comparison` and uses the currently active course.

## Comparisons

- **Scores by lecturer:** the mean normalised score across each lecturer's evaluated
  targets, with the minimum and maximum target scores shown below each bar.
- **Agreement on shared criteria:** each lecturer pair's mean absolute difference
  in percentage points, using only the same target and criterion evaluated by both.
- **Team comparison:** lecturer scores within each team; group and individual
  evaluations are kept in separate groups.
- **Rubric comparison:** a lecturer-by-rubric heatmap with mean target percentages.
  A dash indicates no evaluation, rather than a zero score.
- **Score distribution:** box-and-whisker plots show median target scores, the
  middle 50% (first/third quartiles) and minimum/maximum values for each lecturer.
- **Rating alignment:** a selectable lecturer-pair scatter plot places each shared
  target/criterion on a 0–100% scale. The diagonal represents equal ratings;
  points are coloured by whether their difference is within 10 percentage points.
- **Evaluation breakdown:** the lecturer, team/student, evaluation type, rubric,
  criteria scored, available rating marks and normalised score behind the charts.

Filters cover assessment, team, group/individual evaluation, lecturer selection,
rubric, CLO, individual student and Final/Submitted status. The optional shared-
criteria filter retains only target/criterion combinations scored by at least
two selected lecturers. URLs retain filters for bookmarking or sharing with
another authorised admin. Changing assessments resets the scoped filters.

The average/range, pair-gap, box-plot and scatter charts have bounded display sizes.
Long lecturer lists scroll within their cards rather than expanding the dashboard.

## Additional comparison metrics

- **Agreement within 10 pp:** the percentage of matched lecturer-pair ratings
  whose absolute difference is at most 10 percentage points. Three lecturers on
  one criterion contribute three pairs. The fixed tolerance describes proximity,
  not grading quality.
- **Typical shared-criterion spread:** the median highest-minus-lowest rating
  percentage across target/criterion combinations with at least two selected
  lecturers. Each combination counts once, regardless of the number of lecturers.

Both metrics follow all filters and display a dash when no shared work exists.
Box-plot quartiles use linear interpolation at `(n − 1) × q`; single evaluations
produce coincident quartiles and whiskers. Scatter plots show at most 2,000 points
per selected pair for responsiveness and disclose this limit; all points still
contribute to metrics. Hover descriptions retain target and criterion context.

## Calculation rules

Each criterion's maximum is its highest configured rating mark. A legacy criterion
without rating definitions uses its stored criterion maximum. This is important
when the rubric's total marks are an assessment weight rather than the rating
scale: a rating of 4 on a 1–5 scale is 80%, even if the rubric carries 20 marks.
These comparisons describe awarded ratings before final-grade or factor adjustments.

A lecturer–target percentage is `100 × sum(awarded rating marks) / sum(available
rating marks)` across the criteria retained by the filters. Lecturer averages give
each target evaluation equal weight. Individual evaluations stay separate per
student. Group evaluations copied to team members count once per lecturer, team
and criterion, taking the latest stored row by submission time and then ID.

Only lecturer Final/Submitted scores are used. Peer/self scores and drafts are
excluded. Missing scores are never converted to zero. Invalid scores or obsolete
criteria are excluded with a visible row count. Team filters reflect current team
membership; this dashboard is not a historical membership or grade audit.

Different lecturers can assess different teams and rubric subsets. Use the shared-
criteria filter and the paired-gap chart when comparing their judgements on the
same work. The pair metric averages absolute criterion-percentage differences;
it does not infer lecturer quality or grading bias.

## Export and print

Team comparison displays four distinct teams per page; group and individual
sections stay together within each team. Evaluation breakdown displays 15 rows per
page. Both use Previous/Next controls with visible range/page indicators and reset
to page one when filters change.

CSV export contains every filtered breakdown row, including rows on other pages. Names are escaped and spreadsheet formula prefixes are neutralised.
Print includes all teams and breakdown rows, then restores the selected screen pages. Neither action changes marks.

## Implementation and checks

- `LecturerAnalyticsController` serves the page and an uncached JSON snapshot.
- `LecturerAnalyticsService` validates the assessment against the active course and
  returns immutable DTOs instead of entities or lecturer account details.
- `MarkRepository.findLecturerAnalyticsMarks` scopes both the assessment and evaluated
  students to that course. No schema migration or chart CDN is needed.
- Charts use locally generated SVG and accessible text/table representations.
- Run backend tests with Java 21: `mvn test`.
- Run calculation/export tests: `node --test src/test/js/lecturer-analytics.test.cjs`.

The Docker build/runtime now use Java 21 to match `pom.xml`. Deployment still uses
the existing app/database arrangement and persistent upload settings.
