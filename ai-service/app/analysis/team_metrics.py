"""Teamwork findings worked out from the rating matrix. No model is involved: every flag and score is arithmetic, and each
flag carries the numbers behind it so a supervisor can judge it. The model only writes the wording afterwards."""
from collections import defaultdict
from typing import Any, Dict, List, Optional

FREE_RIDER_GAP = 15.0          # peers rated them this many points below the team average
FREE_RIDER_HIGH_GAP = 25.0
SELF_INFLATION_GAP = 20.0      # they rated themselves this many points above what peers gave them
UNIFORM_TOP_MIN = 90.0         # every peer rating at least this...
UNIFORM_TOP_RANGE = 5.0        # ...and all within this many points of each other
UNIFORM_MIN_RATINGS = 6
UNIFORM_MIN_RATERS = 3
MIN_RATED_MEMBERS = 3          # a team average needs at least this many members who received peer ratings


def _pct(rating: Dict[str, Any]) -> Optional[float]:
    possible = rating.get("possible")
    earned = rating.get("earned")
    if earned is None or possible is None or possible <= 0:
        return None
    return earned / possible * 100.0


def _mean(values: List[float]) -> Optional[float]:
    return sum(values) / len(values) if values else None


def analyse_team(members: List[Dict[str, Any]], ratings: List[Dict[str, Any]]) -> Dict[str, Any]:
    """Self and peer averages per member, the flags they trigger, a 0-100 health score and a status.

    Returns a dict with: member_stats {id: {...}}, group_peer_avg, flags [..], health_score (None when there is not enough
    peer data), status.
    """
    self_scores: Dict[int, List[float]] = defaultdict(list)
    peer_scores: Dict[int, List[float]] = defaultdict(list)
    peer_raters: Dict[int, set] = defaultdict(set)
    all_peer: List[float] = []
    all_raters: set = set()

    for r in ratings:
        pct = _pct(r)
        if pct is None:
            continue
        evaluator, evaluated = r.get("evaluatorId"), r.get("evaluatedId")
        if evaluator == evaluated:
            self_scores[evaluated].append(pct)
        else:
            peer_scores[evaluated].append(pct)
            peer_raters[evaluated].add(evaluator)
            all_peer.append(pct)
            all_raters.add(evaluator)

    stats: Dict[int, Dict[str, Any]] = {}
    for m in members:
        sid = m["studentId"]
        peer_avg = _mean(peer_scores[sid])
        self_avg = _mean(self_scores[sid])
        stats[sid] = {
            "studentId": sid,
            "studentName": m["studentName"],
            "selfScoreAvg": round(self_avg, 1) if self_avg is not None else None,
            "peerScoreAvg": round(peer_avg, 1) if peer_avg is not None else None,
            "peerRatingCount": len(peer_scores[sid]),
            "peerRaterCount": len(peer_raters[sid]),
            "selfInflationGap": round(self_avg - peer_avg, 1) if self_avg is not None and peer_avg is not None else None,
            "status": "CONTRIBUTING",
            "contributionSummary": "",
        }

    rated = [s for s in stats.values() if s["peerScoreAvg"] is not None]
    group_avg = _mean([s["peerScoreAvg"] for s in rated]) if len(rated) >= MIN_RATED_MEMBERS else None

    flags: List[Dict[str, Any]] = []
    for s in stats.values():
        peer = s["peerScoreAvg"]
        if peer is None:
            s["status"] = "UNDER_EVALUATED"
            s["contributionSummary"] = "No peer ratings have been received for this student yet."
            continue
        if group_avg is not None and s["peerRaterCount"] >= 2 and group_avg - peer >= FREE_RIDER_GAP:
            gap = round(group_avg - peer, 1)
            flags.append({
                "type": "FREE_RIDER",
                "severity": "HIGH" if gap >= FREE_RIDER_HIGH_GAP else "MEDIUM",
                "targetStudent": s["studentName"],
                "description": f"{s['studentName']} was rated well below the rest of the team by their teammates.",
                "evidence": f"Peers rated them {peer}% on average ({s['peerRatingCount']} ratings from {s['peerRaterCount']} teammates) "
                            f"against a team average of {round(group_avg, 1)}%, a gap of {gap} points.",
                "source": "CALCULATED",
            })
            s["status"] = "SUSPECTED_FREE_RIDER"
        gap_self = s["selfInflationGap"]
        if gap_self is not None and gap_self >= SELF_INFLATION_GAP:
            flags.append({
                "type": "SELF_INFLATION",
                "severity": "MEDIUM",
                "targetStudent": s["studentName"],
                "description": f"{s['studentName']} rated their own work much higher than their teammates did.",
                "evidence": f"Self rating {s['selfScoreAvg']}% against {peer}% from peers, a gap of {gap_self} points.",
                "source": "CALCULATED",
            })
            if s["status"] == "CONTRIBUTING":
                s["status"] = "OVER_CONFIDENT"
        if not s["contributionSummary"]:
            s["contributionSummary"] = (f"Peers rated them {peer}% on average "
                                        f"({s['peerRatingCount']} ratings from {s['peerRaterCount']} teammates).")

    uniform = (len(all_peer) >= UNIFORM_MIN_RATINGS and len(all_raters) >= UNIFORM_MIN_RATERS
               and min(all_peer) >= UNIFORM_TOP_MIN and max(all_peer) - min(all_peer) <= UNIFORM_TOP_RANGE)
    if uniform:
        flags.append({
            "type": "COLLUSION",
            "severity": "MEDIUM",
            "targetStudent": None,
            "description": "Every peer rating in this team is a near-identical top mark, with no differences between members.",
            "evidence": f"All {len(all_peer)} peer ratings from {len(all_raters)} students are between "
                        f"{round(min(all_peer), 1)}% and {round(max(all_peer), 1)}%. This can mean a mutual grading agreement, "
                        f"or simply a team that worked evenly: check against what you observed.",
            "source": "CALCULATED",
        })

    health: Optional[int] = None
    if len(rated) >= 2:
        free_riders = sum(1 for f in flags if f["type"] == "FREE_RIDER")
        inflations = sum(1 for f in flags if f["type"] == "SELF_INFLATION")
        health = 100 - min(50, 25 * free_riders) - min(20, 10 * inflations) - (15 if uniform else 0)
        health = max(0, min(100, health))

    if health is None:
        status = "INSUFFICIENT_DATA"
    elif any(f["type"] == "FREE_RIDER" for f in flags):
        status = "FREE_RIDER_DETECTED"
    elif uniform:
        status = "SUSPECTED_COLLUSION"
    elif health < 80:
        status = "HIGH_FRICTION"
    else:
        status = "HEALTHY_COLLABORATION"

    return {
        "member_stats": stats,
        "group_peer_avg": round(group_avg, 1) if group_avg is not None else None,
        "flags": flags,
        "health_score": health,
        "status": status,
        "peer_rating_count": len(all_peer),
    }


def plain_summary(group_name: str, analysis: Dict[str, Any]) -> str:
    """A factual summary written from the numbers alone: what is used when no model wrote one."""
    count = analysis["peer_rating_count"]
    if analysis["status"] == "INSUFFICIENT_DATA":
        return (f"There are not enough peer ratings yet to judge how {group_name} worked together "
                f"({count} peer rating{'s' if count != 1 else ''} received).")
    flags = analysis["flags"]
    if not flags:
        return (f"Based on {count} peer ratings, no calculated warning signs were found for {group_name}: "
                f"ratings are consistent across members. Read the written comments before relying on this.")
    kinds = ", ".join(sorted({f["type"].replace("_", " ").lower() for f in flags}))
    return f"Based on {count} peer ratings, {len(flags)} warning sign{'s' if len(flags) != 1 else ''} ({kinds}) were found for {group_name}. See the evidence below."


def default_actions(analysis: Dict[str, Any]) -> List[str]:
    if analysis["status"] == "INSUFFICIENT_DATA":
        return ["Wait until more peer evaluations are submitted before drawing conclusions."]
    actions: List[str] = []
    if any(f["type"] == "FREE_RIDER" for f in analysis["flags"]):
        actions.append("Talk to the flagged student and the team about how the work was shared before confirming marks.")
    if any(f["type"] == "SELF_INFLATION" for f in analysis["flags"]):
        actions.append("Compare the student's self rating with what you observed of their contribution.")
    if any(f["type"] == "COLLUSION" for f in analysis["flags"]):
        actions.append("Check the team's evidence of who did what, since the peer ratings do not separate members.")
    return actions or ["No follow-up is suggested by the ratings alone."]
