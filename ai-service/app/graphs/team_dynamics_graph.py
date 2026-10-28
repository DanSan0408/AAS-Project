import json
import re
from collections import defaultdict
from typing import TypedDict, List, Dict, Any, Optional

from langchain_core.messages import SystemMessage, HumanMessage
from langgraph.graph import StateGraph, END

from app.llm import get_gemini_llm
from app.schemas.team_dynamics_schemas import (
    TeamDynamicsRequest,
    TeamDynamicsResponse,
    MemberMetric,
    AnomalyFlag
)


class TeamDynamicsGraphState(TypedDict):
    request: Dict[str, Any]
    member_stats: Dict[int, Dict[str, Any]]
    group_peer_avg: float
    sentiment_notes: List[Dict[str, Any]]
    anomaly_flags: List[Dict[str, Any]]
    team_health_score: int
    collaboration_status: str
    executive_summary: str
    recommended_actions: List[str]
    final_response: Optional[Dict[str, Any]]


def compute_peer_metrics(state: TeamDynamicsGraphState) -> Dict[str, Any]:
    """Computes exact self vs peer rating distributions, inflation gaps, and group averages."""
    req = state["request"]
    members = req.get("members", [])
    ratings = req.get("peerRatings", [])

    member_map = {m["studentId"]: m["studentName"] for m in members}
    self_scores = defaultdict(list)
    peer_scores = defaultdict(list)
    scores_given = defaultdict(list)

    for r in ratings:
        evaluator_id = r.get("evaluatorId")
        evaluated_id = r.get("evaluatedId")
        earned = r.get("earned", 0.0)
        possible = r.get("possible", 100.0) or 100.0
        pct = (earned / possible) * 100.0 if possible > 0 else 0.0

        scores_given[evaluator_id].append(pct)
        if evaluator_id == evaluated_id:
            self_scores[evaluated_id].append(pct)
        else:
            peer_scores[evaluated_id].append(pct)

    all_peer_averages = []
    member_stats = {}

    for m in members:
        s_id = m["studentId"]
        s_name = m["studentName"]

        s_self = sum(self_scores[s_id]) / len(self_scores[s_id]) if self_scores[s_id] else None
        s_peer = sum(peer_scores[s_id]) / len(peer_scores[s_id]) if peer_scores[s_id] else 0.0
        if peer_scores[s_id]:
            all_peer_averages.append(s_peer)

        inflation = (s_self - s_peer) if s_self is not None else None

        member_stats[s_id] = {
            "studentId": s_id,
            "studentName": s_name,
            "selfScoreAvg": round(s_self, 1) if s_self is not None else None,
            "peerScoreAvg": round(s_peer, 1),
            "selfInflationGap": round(inflation, 1) if inflation is not None else None,
            "status": "CONTRIBUTING",
            "contributionSummary": ""
        }

    group_avg = sum(all_peer_averages) / len(all_peer_averages) if all_peer_averages else 0.0

    return {
        "member_stats": member_stats,
        "group_peer_avg": round(group_avg, 1)
    }


def analyze_comment_sentiments(state: TeamDynamicsGraphState) -> Dict[str, Any]:
    """Analyzes peer written commentary for tone, subtle friction, and mark consistency using Gemini."""
    req = state["request"]
    comments = req.get("peerComments", [])
    member_stats = state.get("member_stats", {})

    if not comments:
        return {"sentiment_notes": []}

    llm = get_gemini_llm(temperature=0.1)

    prompt = f"""You are an experienced academic supervisor reviewing qualitative peer feedback for a student group project.
Group: {req.get('groupName', 'Team')}
Assessment: {req.get('assessmentTitle', 'Capstone')}

Peer Comments:
{json.dumps(comments, indent=2)}

Task:
Write a human, natural assessment of how peers described each student's teamwork.
Avoid robotic AI phrasing, clinical jargon, or boilerplate text. Be direct, fair, and empathetic.

Guidelines:
- For each evaluated student, write 1 short, natural sentence capturing how teammates felt about their contribution, communication, and reliability (e.g., "Peers appreciated their prompt communication and ownership of the backend" or "Teammates felt they were hard to reach and fell behind on deliverables").
- Flag if there are genuine complaints regarding attendance, effort, or communication.

Respond in strict JSON:
{{
  "sentiments": [
    {{
      "evaluatedName": "Student Name",
      "sentiment": "POSITIVE / MIXED / NEGATIVE / COMPLAINT",
      "summary": "1 short, natural sentence describing peer feedback in plain English",
      "hasAttendanceOrEffortIssue": false
    }}
  ]
}}"""

    sentiment_notes = []
    try:
        response = llm.invoke([
            SystemMessage(content="You evaluate peer commentary in strict JSON. Output only valid JSON."),
            HumanMessage(content=prompt)
        ])
        match = re.search(r"\{.*\}", response.content, re.DOTALL)
        if match:
            data = json.loads(match.group(0))
            sentiment_notes = data.get("sentiments", [])
    except Exception:
        pass

    return {"sentiment_notes": sentiment_notes}


def detect_anomalies_and_dynamics(state: TeamDynamicsGraphState) -> Dict[str, Any]:
    """Detects free-riders, collusion, self-inflation, and mark-sentiment discrepancies."""
    req = state["request"]
    member_stats = state.get("member_stats", {})
    group_avg = state.get("group_peer_avg", 0.0)
    sentiments = {s.get("evaluatedName"): s for s in state.get("sentiment_notes", [])}

    llm = get_gemini_llm(temperature=0.1)

    stats_list = list(member_stats.values())

    prompt = f"""You are an experienced academic advisor auditing student group collaboration and peer evaluation integrity.
Group Average Peer Mark: {group_avg}%
Member Statistics:
{json.dumps(stats_list, indent=2)}

Peer Feedback Insights:
{json.dumps(list(sentiments.values()), indent=2)}

Task:
1. Check for genuine collaboration issues:
   - FREE_RIDER: Student with peer marks noticeably below team average (>15% gap) or repeated complaints on lack of effort.
   - COLLUSION: Obvious reciprocal score pact where all teammates gave each other identical top marks without genuine critique.
   - SELF_INFLATION: Student rated themselves much higher (>20% gap) than teammates rated them.
   - SENTIMENT_MARK_MISMATCH: Generous scores given despite critical written complaints (or vice-versa).
2. Assign each student a status: 'CONTRIBUTING', 'SUSPECTED_FREE_RIDER', 'OVER_CONFIDENT', or 'UNDER_EVALUATED'.
   Write a 1-sentence plain-English summary of their contribution (e.g., "Active contributor whose teammates praised their reliable delivery" or "Rated lower by peers due to missed meetings and slow delivery").
3. Assign a Team Health Score (0-100) and Collaboration Status ('HEALTHY_COLLABORATION', 'SUSPECTED_COLLUSION', 'FREE_RIDER_DETECTED', or 'HIGH_FRICTION').
4. For any anomalies, describe the finding in plain, human English (1 concise sentence, avoiding clinical formulas or robot talk).

Respond in strict JSON:
{{
  "teamHealthScore": 85,
  "collaborationStatus": "HEALTHY_COLLABORATION",
  "anomalyFlags": [
    {{
      "type": "FREE_RIDER / COLLUSION / SELF_INFLATION / SENTIMENT_MARK_MISMATCH",
      "severity": "HIGH / MEDIUM / LOW",
      "targetStudent": "Student Name",
      "description": "1 clear, conversational sentence explaining what was spotted"
    }}
  ],
  "memberUpdates": [
    {{
      "studentId": 123,
      "status": "CONTRIBUTING",
      "contributionSummary": "1 short, plain sentence on their role and peer reception"
    }}
  ]
}}"""

    health_score = 85
    collab_status = "HEALTHY_COLLABORATION"
    anomalies = []

    try:
        response = llm.invoke([
            SystemMessage(content="You audit peer evaluation integrity. Output only strict JSON."),
            HumanMessage(content=prompt)
        ])
        match = re.search(r"\{.*\}", response.content, re.DOTALL)
        if match:
            data = json.loads(match.group(0))
            health_score = data.get("teamHealthScore", 85)
            collab_status = data.get("collaborationStatus", "HEALTHY_COLLABORATION")
            anomalies = data.get("anomalyFlags", [])
            for update in data.get("memberUpdates", []):
                s_id = update.get("studentId")
                if s_id in member_stats:
                    member_stats[s_id]["status"] = update.get("status", "CONTRIBUTING")
                    member_stats[s_id]["contributionSummary"] = update.get("contributionSummary", "")
    except Exception:
        pass

    # Fallback heuristic if LLM fails or is empty
    for s_id, m in member_stats.items():
        if not m["contributionSummary"]:
            m["contributionSummary"] = f"Average peer rating of {m['peerScoreAvg']}% with consistent team marks."
        if m.get("selfInflationGap") and m["selfInflationGap"] >= 20.0:
            anomalies.append({
                "type": "SELF_INFLATION",
                "severity": "MEDIUM",
                "targetStudent": m["studentName"],
                "description": f"{m['studentName']} rated their own work notably higher than teammates rated them ({m['selfInflationGap']}% higher)."
            })
        if group_avg > 0 and (group_avg - m["peerScoreAvg"]) >= 18.0:
            m["status"] = "SUSPECTED_FREE_RIDER"
            collab_status = "FREE_RIDER_DETECTED"
            health_score = min(health_score, 60)
            anomalies.append({
                "type": "FREE_RIDER",
                "severity": "HIGH",
                "targetStudent": m["studentName"],
                "description": f"{m['studentName']} received peer marks noticeably below the team average ({round(group_avg - m['peerScoreAvg'], 1)}% lower)."
            })

    return {
        "member_stats": member_stats,
        "team_health_score": health_score,
        "collaboration_status": collab_status,
        "anomaly_flags": anomalies
    }


def synthesize_team_report(state: TeamDynamicsGraphState) -> Dict[str, Any]:
    """Generates the executive summary narrative and recommended supervisor actions."""
    req = state["request"]
    group_name = req.get("groupName", "Team")
    health = state.get("team_health_score", 85)
    status = state.get("collaboration_status", "HEALTHY_COLLABORATION")
    anomalies = state.get("anomaly_flags", [])
    member_stats = list(state.get("member_stats", {}).values())

    llm = get_gemini_llm(temperature=0.2)

    prompt = f"""You are an experienced, empathetic university course lead writing a quick briefing note for an academic supervisor.
Group: {group_name}
Team Health: {health}/100 ({status})
Anomalies Spotted:
{json.dumps(anomalies, indent=2)}
Member Metrics:
{json.dumps(member_stats, indent=2)}

Task:
Write a humanlike, simplified summary and practical next steps for the supervisor.

STRICT WRITING GUIDELINES:
1. Executive Summary:
   - Exactly 2 to 3 natural, concise sentences (60-90 words max).
   - Write like a real person talking to a colleague: clear, empathetic, and constructive.
   - Sentence 1: The big picture of how the group worked together.
   - Sentence 2: The primary friction point or standout observation (if any). If the team was smooth, mention the strong peer cohesion.
   - Sentence 3: A reassuring, practical conclusion.
   - NEVER use AI clichés like "It is evident that", "Delving into the data", "Crucial to note", "In summary", or "Overall, the metrics demonstrate".
   - Do NOT just recite raw percentages or numbers—the supervisor already sees the numbers on screen. Explain what the pattern means in plain human terms.

2. Recommended Actions:
   - 2 to 3 short, actionable bullet points (1 sentence each).
   - Start with an active verb (e.g., "Check in with...", "Review...", "Calibrate...").
   - Practical and pragmatic, not bureaucratic.

Respond in strict JSON:
{{
  "executiveSummary": "2-3 natural, warm, human sentences...",
  "recommendedActions": [
    "Practical action 1",
    "Practical action 2"
  ]
}}"""

    if status in ("HEALTHY_COLLABORATION", "HEALTHY"):
        exec_summary = f"The team worked well together overall, sharing the workload fairly and communicating constructively. Peer feedback is consistent and positive across the board, reflecting a healthy, cooperative dynamic with no major concerns."
    elif status == "FREE_RIDER_DETECTED":
        exec_summary = f"The team mostly functioned well, but there is noticeable friction around individual contributions. One or more members received lower peer ratings and complaints regarding task completion, which warrants a brief check before locking in final scores."
    elif status == "SUSPECTED_COLLUSION":
        exec_summary = f"The peer evaluations show unusually uniform top marks across all team members with little qualitative distinction. This often points to a mutual grading pact rather than candid feedback, so relying on supervisor observations for individual marks is advisable."
    else:
        exec_summary = f"The team experienced some internal friction during this assessment cycle, reflected in mixed peer ratings and contrasting comments. Reviewing individual deliverables will help ensure each member is evaluated fairly."

    actions = [
        "Check in briefly with the team on workload division before locking in final scores.",
        "Review repository commits or meeting logs if individual contribution disputes arise."
    ]

    try:
        response = llm.invoke([
            SystemMessage(content="Output strict JSON only."),
            HumanMessage(content=prompt)
        ])
        match = re.search(r"\{.*\}", response.content, re.DOTALL)
        if match:
            data = json.loads(match.group(0))
            if data.get("executiveSummary"):
                exec_summary = data["executiveSummary"]
            if data.get("recommendedActions"):
                actions = data["recommendedActions"]
    except Exception:
        pass

    return {
        "executive_summary": exec_summary,
        "recommended_actions": actions
    }


def format_output(state: TeamDynamicsGraphState) -> Dict[str, Any]:
    """Assembles the final TeamDynamicsResponse."""
    req = state["request"]
    member_stats = state.get("member_stats", {})
    members_dto = [MemberMetric(**m).model_dump() for m in member_stats.values()]
    anomalies_dto = [AnomalyFlag(**a).model_dump() for a in state.get("anomaly_flags", [])]

    resp = {
        "groupId": req.get("groupId", 0),
        "groupName": req.get("groupName", "Team"),
        "teamHealthScore": state.get("team_health_score", 85),
        "collaborationStatus": state.get("collaboration_status", "HEALTHY_COLLABORATION"),
        "executiveSummary": state.get("executive_summary", ""),
        "memberMetrics": members_dto,
        "anomalyFlags": anomalies_dto,
        "recommendedActions": state.get("recommended_actions", [])
    }
    return {"final_response": resp}


def build_team_dynamics_graph():
    """Builds the compiled LangGraph state graph for Team Dynamics & Peer Review Integrity."""
    builder = StateGraph(TeamDynamicsGraphState)

    builder.add_node("compute_peer_metrics", compute_peer_metrics)
    builder.add_node("analyze_comment_sentiments", analyze_comment_sentiments)
    builder.add_node("detect_anomalies_and_dynamics", detect_anomalies_and_dynamics)
    builder.add_node("synthesize_team_report", synthesize_team_report)
    builder.add_node("format_output", format_output)

    builder.set_entry_point("compute_peer_metrics")
    builder.add_edge("compute_peer_metrics", "analyze_comment_sentiments")
    builder.add_edge("analyze_comment_sentiments", "detect_anomalies_and_dynamics")
    builder.add_edge("detect_anomalies_and_dynamics", "synthesize_team_report")
    builder.add_edge("synthesize_team_report", "format_output")
    builder.add_edge("format_output", END)

    return builder.compile()


team_dynamics_graph = build_team_dynamics_graph()


def execute_team_dynamics_audit(request: TeamDynamicsRequest) -> TeamDynamicsResponse:
    """Executes the Team Dynamics & Peer Review Integrity LangGraph."""
    initial_state: TeamDynamicsGraphState = {
        "request": request.model_dump(),
        "member_stats": {},
        "group_peer_avg": 0.0,
        "sentiment_notes": [],
        "anomaly_flags": [],
        "team_health_score": 85,
        "collaboration_status": "HEALTHY_COLLABORATION",
        "executive_summary": "",
        "recommended_actions": [],
        "final_response": None
    }

    result = team_dynamics_graph.invoke(initial_state)
    resp = result["final_response"]
    return TeamDynamicsResponse(**resp)

