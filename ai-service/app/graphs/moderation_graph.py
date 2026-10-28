import json
import re
from collections import defaultdict
from typing import TypedDict, List, Dict, Any, Optional

from langchain_core.messages import SystemMessage, HumanMessage
from langgraph.graph import StateGraph, END

from app.llm import get_gemini_llm
from app.schemas.moderation_schemas import (
    ModerationBriefingRequest,
    ModerationBriefingResponse,
    CriterionDiscrepancy,
    TargetDiscrepancy
)


class ModerationGraphState(TypedDict):
    request: Dict[str, Any]
    cohort_gap_pct: float
    consensus_status: str
    top_divergent_criteria: List[Dict[str, Any]]
    priority_targets: List[Dict[str, Any]]
    executive_summary: str
    moderation_actions: List[str]
    final_response: Optional[Dict[str, Any]]


def compute_statistical_gaps(state: ModerationGraphState) -> Dict[str, Any]:
    """Computes exact pair gaps and target mark variances across lecturers."""
    req = state["request"]
    scores = req.get("scores", [])
    criteria = {c["id"]: c for c in req.get("criteria", [])}

    if not scores:
        return {
            "cohort_gap_pct": 0.0,
            "consensus_status": "HIGH_ALIGNMENT",
            "top_divergent_criteria": [],
            "priority_targets": []
        }

    # Group scores by (targetId, criterionId) -> list of {lecturerName, pct}
    target_crit_scores = defaultdict(list)
    # Group scores by targetId -> dict of lecturerName -> avg pct
    target_lecturer_totals = defaultdict(lambda: defaultdict(list))

    for s in scores:
        crit_id = s.get("criterionId")
        crit_info = criteria.get(crit_id, {})
        possible = crit_info.get("possible", 100.0) or 100.0
        earned = s.get("earned", 0.0)
        pct = (earned / possible) * 100.0 if possible > 0 else 0.0

        target_id = s.get("targetId")
        target_name = s.get("targetName")
        lecturer_name = s.get("lecturerName")

        target_crit_scores[(target_id, crit_id)].append({
            "lecturer": lecturer_name,
            "pct": pct
        })
        target_lecturer_totals[(target_id, target_name)][lecturer_name].append(pct)

    # Compute criterion-level pair gaps
    criterion_gaps = defaultdict(list)
    all_gaps = []

    for (target_id, crit_id), evaluations in target_crit_scores.items():
        if len(evaluations) < 2:
            continue
        for i in range(len(evaluations)):
            for j in range(i + 1, len(evaluations)):
                diff = abs(evaluations[i]["pct"] - evaluations[j]["pct"])
                criterion_gaps[crit_id].append(diff)
                all_gaps.append(diff)

    mean_cohort_gap = sum(all_gaps) / len(all_gaps) if all_gaps else 0.0

    # Rank criteria by mean pair gap
    ranked_criteria = []
    for crit_id, diffs in criterion_gaps.items():
        avg_diff = sum(diffs) / len(diffs)
        crit_info = criteria.get(crit_id, {})
        severity = "CRITICAL" if avg_diff >= 20.0 else "MODERATE" if avg_diff >= 10.0 else "LOW"
        ranked_criteria.append({
            "criterionId": crit_id,
            "criterionLabel": crit_info.get("label", crit_id),
            "rubricName": crit_info.get("rubricName", "General"),
            "meanPairGapPct": round(avg_diff, 1),
            "severity": severity,
            "analysis": ""
        })

    ranked_criteria.sort(key=lambda x: x["meanPairGapPct"], reverse=True)
    top_criteria = ranked_criteria[:3]

    # Rank target students/teams by inter-assessor spread
    ranked_targets = []
    for (target_id, target_name), lec_dict in target_lecturer_totals.items():
        if len(lec_dict) < 2:
            continue
        # Average pct per lecturer for this target
        lec_averages = {lec: round(sum(vals)/len(vals), 1) for lec, vals in lec_dict.items()}
        values = list(lec_averages.values())
        spread = max(values) - min(values)

        ranked_targets.append({
            "targetId": target_id,
            "targetName": target_name,
            "spreadPct": round(spread, 1),
            "lecturerScores": lec_averages,
            "summaryNote": f"Mark spread of {round(spread, 1)} percentage points across {len(lec_averages)} assessors."
        })

    ranked_targets.sort(key=lambda x: x["spreadPct"], reverse=True)
    top_targets = ranked_targets[:3]

    # Consensus level classification
    if mean_cohort_gap >= 15.0 or (top_targets and top_targets[0]["spreadPct"] >= 25.0):
        status = "ACTION_REQUIRED"
    elif mean_cohort_gap >= 8.0:
        status = "MODERATE_DISCREPANCY"
    else:
        status = "HIGH_ALIGNMENT"

    return {
        "cohort_gap_pct": round(mean_cohort_gap, 1),
        "consensus_status": status,
        "top_divergent_criteria": top_criteria,
        "priority_targets": top_targets
    }


def analyze_criteria_divergence(state: ModerationGraphState) -> Dict[str, Any]:
    """Enriches the top divergent criteria with pedagogical explanations using Gemini."""
    top_criteria = state.get("top_divergent_criteria", [])
    if not top_criteria:
        return {"top_divergent_criteria": []}

    llm = get_gemini_llm(temperature=0.1)

    prompt = f"""You are a veteran university academic moderation chair reviewing grading differences between assessors.
Assessment Criteria with Discrepancies:
{json.dumps(top_criteria, indent=2)}

Task:
For each criterion, write 1 clear, natural sentence explaining the practical reason why assessors likely saw this differently (e.g., whether one grader prioritized technical depth while another focused on presentation, or if rubric boundaries left room for interpretation).

Guidelines:
- Plain English, no academic jargon or formulaic phrasing.
- Specific, helpful, and concise (under 25 words per criterion).

Respond in strict JSON:
{{
  "analyses": [
    {{ "criterionId": "id", "analysis": "1 natural diagnostic sentence" }}
  ]
}}"""

    try:
        response = llm.invoke([
            SystemMessage(content="Output strict JSON only."),
            HumanMessage(content=prompt)
        ])
        match = re.search(r"\{.*\}", response.content, re.DOTALL)
        if match:
            data = json.loads(match.group(0))
            analyses_map = {item["criterionId"]: item["analysis"] for item in data.get("analyses", [])}
            for crit in top_criteria:
                if crit["criterionId"] in analyses_map:
                    crit["analysis"] = analyses_map[crit["criterionId"]]
    except Exception:
        pass

    # Fallback diagnostics if empty
    for crit in top_criteria:
        if not crit["analysis"]:
            crit["analysis"] = f"Assessors appear to have applied different standards on this criterion, likely differing on how strictly the highest rubric tier should be awarded."

    return {"top_divergent_criteria": top_criteria}


def synthesize_moderation_briefing(state: ModerationGraphState) -> Dict[str, Any]:
    """Generates the executive moderation narrative and board recommendations."""
    req = state["request"]
    title = req.get("assessmentTitle", "Assessment")
    cohort_gap = state.get("cohort_gap_pct", 0.0)
    status = state.get("consensus_status", "HIGH_ALIGNMENT")
    top_criteria = state.get("top_divergent_criteria", [])
    priority_targets = state.get("priority_targets", [])
    comments = req.get("comments", [])

    llm = get_gemini_llm(temperature=0.2)

    prompt = f"""You are an experienced academic moderation lead preparing a concise briefing for fellow faculty assessors.
Assessment: {title}
Cohort Mean Pair-Gap: {cohort_gap}%
Consensus Status: {status}
Key Divergent Criteria:
{json.dumps(top_criteria, indent=2)}
Submissions with Highest Assessor Spread:
{json.dumps(priority_targets, indent=2)}
Assessor Comments Sample:
{json.dumps(comments[:5], indent=2) if comments else "No comments submitted."}

Task:
Write a humanlike, simplified summary and practical next steps for the moderation discussion.

STRICT WRITING GUIDELINES:
1. Executive Summary:
   - Exactly 2 to 3 natural, concise sentences (60-90 words max).
   - Write like a real person talking to peer academics: respectful, constructive, and direct.
   - Sentence 1: The big picture of how aligned assessors are across the cohort.
   - Sentence 2: Where the main difference in perspective occurred (e.g. specific criteria or a few polarized submissions).
   - Sentence 3: A practical conclusion on how quickly this can be resolved.
   - NEVER use AI clichés like "It is evident that", "Delving into the dataset", "In conclusion", "As an AI", or "Overall, the analysis shows".
   - Do NOT just recite raw numbers or statistical formulas—the committee already sees the table. Explain what the spread means in practice.

2. Recommended Actions:
   - 2 to 3 short, actionable bullet points (1 sentence each).
   - Start with an active verb (e.g., "Hold a 5-minute sync...", "Sample-review the top 2 submissions...", "Standardize the interpretation of...").
   - Pragmatic and collaborative.

Respond in strict JSON:
{{
  "executiveSummary": "2-3 natural, human sentences...",
  "moderationActions": [
    "Practical action 1",
    "Practical action 2"
  ]
}}"""

    if status == "HIGH_ALIGNMENT":
        exec_summary = f"Assessors are closely aligned across this assessment, with grading standards remaining remarkably consistent. The small score variations are well within normal expectations, so final marks can proceed with high confidence."
    elif status == "MODERATE_DISCREPANCY":
        exec_summary = f"Assessors generally share the same view of overall student performance, though there are moderate differences on specific criteria. A quick calibration check on the divergent criteria will easily harmonize the marks before release."
    else:
        exec_summary = f"There are noticeable differences in how assessors marked several submissions, pointing to differing expectations on key rubric criteria. A short moderation review of the highlighted submissions will help bring everyone into consensus."

    actions = [
        "Hold a brief alignment sync to calibrate expectations on divergent criteria.",
        "Jointly review sample submissions showing wider score spreads before publishing grades."
    ]

    try:
        response = llm.invoke([
            SystemMessage(content="You are an academic moderation officer. Output only strict JSON."),
            HumanMessage(content=prompt)
        ])
        match = re.search(r"\{.*\}", response.content, re.DOTALL)
        if match:
            data = json.loads(match.group(0))
            if data.get("executiveSummary"):
                exec_summary = data["executiveSummary"]
            if data.get("moderationActions"):
                actions = data["moderationActions"]
    except Exception:
        pass

    return {
        "executive_summary": exec_summary,
        "moderation_actions": actions
    }


def format_briefing_output(state: ModerationGraphState) -> Dict[str, Any]:
    """Assembles the final ModerationBriefingResponse structure."""
    req = state["request"]
    resp = {
        "assessmentId": req.get("assessmentId", 0),
        "assessmentTitle": req.get("assessmentTitle", "Assessment"),
        "consensusStatus": state.get("consensus_status", "HIGH_ALIGNMENT"),
        "meanCohortGapPct": state.get("cohort_gap_pct", 0.0),
        "executiveSummary": state.get("executive_summary", ""),
        "topDivergentCriteria": [CriterionDiscrepancy(**c).model_dump() for c in state.get("top_divergent_criteria", [])],
        "priorityReviewTargets": [TargetDiscrepancy(**t).model_dump() for t in state.get("priority_targets", [])],
        "moderationActions": state.get("moderation_actions", [])
    }
    return {"final_response": resp}


def build_moderation_graph():
    """Builds the compiled LangGraph state graph for moderation briefings."""
    builder = StateGraph(ModerationGraphState)

    builder.add_node("compute_statistical_gaps", compute_statistical_gaps)
    builder.add_node("analyze_criteria_divergence", analyze_criteria_divergence)
    builder.add_node("synthesize_moderation_briefing", synthesize_moderation_briefing)
    builder.add_node("format_briefing_output", format_briefing_output)

    builder.set_entry_point("compute_statistical_gaps")
    builder.add_edge("compute_statistical_gaps", "analyze_criteria_divergence")
    builder.add_edge("analyze_criteria_divergence", "synthesize_moderation_briefing")
    builder.add_edge("synthesize_moderation_briefing", "format_briefing_output")
    builder.add_edge("format_briefing_output", END)

    return builder.compile()


moderation_graph = build_moderation_graph()


def execute_moderation_briefing(request: ModerationBriefingRequest) -> ModerationBriefingResponse:
    """Executes the moderation briefing LangGraph."""
    initial_state: ModerationGraphState = {
        "request": request.model_dump(),
        "cohort_gap_pct": 0.0,
        "consensus_status": "HIGH_ALIGNMENT",
        "top_divergent_criteria": [],
        "priority_targets": [],
        "executive_summary": "",
        "moderation_actions": [],
        "final_response": None
    }

    result = moderation_graph.invoke(initial_state)
    resp = result["final_response"]
    return ModerationBriefingResponse(**resp)

