# Quiz Leaderboard System

**Bajaj Finserv Health — Java Qualifier | SRM Internship Assignment**

---

## Problem Statement

A quiz show backend where multiple participants earn scores across rounds. The validator API is polled 10 times, but the same event data may appear in multiple polls (simulating real-world duplicate delivery in distributed systems). The goal is to:

- Collect all events across 10 polls
- Deduplicate events correctly
- Aggregate scores per participant
- Submit a single correct leaderboard

---

## Approach & Logic

### The Core Challenge — Deduplication

In distributed systems, the same message can be delivered more than once. If duplicates are not handled, scores get inflated and the final total is wrong.

**Wrong approach (without deduplication):**
```
Poll 1 → Alice R1 +10   ✅
Poll 4 → Alice R1 +10   ✅ (counted again — WRONG)
Total Alice = 20  ❌
```

**Correct approach (with deduplication):**
```
Poll 1 → Alice R1 +10   ✅ (new, added)
Poll 4 → Alice R1 +10   ⏭️ (duplicate, skipped)
Total Alice = 10  ✅
```

### Deduplication Key

Each event is uniquely identified by the combination of:
```
key = roundId + "|" + participant
```

A `HashSet<String>` tracks all seen keys. Before processing any event, the key is checked against the set. If it already exists → skip. If not → add to set and aggregate the score.

### Score Aggregation

A `TreeMap<String, Integer>` maps each participant name to their running total. Java's `Map.merge()` with `Integer::sum` cleanly handles first-time inserts and subsequent additions.

### Leaderboard Sorting

After all 10 polls are complete, the score map is converted to a list and sorted by `totalScore` descending using a lambda comparator.

### Submission

The leaderboard is submitted exactly once via `POST /quiz/submit` after all polling and aggregation is done.

---

## Flow Diagram

```
Start
  │
  ▼
Poll API (poll=0 to poll=9)
  │   ├── 5 second delay between each poll
  │   └── Collect events from each response
  │
  ▼
For each event:
  │
  ├── key = roundId + "|" + participant
  │
  ├── Already in Set?
  │     ├── YES → Skip (duplicate)
  │     └── NO  → Add to Set, add score to Map
  │
  ▼
Sort Map by totalScore (descending)
  │
  ▼
POST leaderboard to /quiz/submit (once)
  │
  ▼
Print submission response
```

---

## Tech Stack

| Component | Choice | Reason |
|---|---|---|
| Language | Java 17 | Assignment requirement |
| HTTP Client | `java.net.http.HttpClient` | Built-in since Java 11, no extra dependency |
| JSON Parsing | Jackson (`jackson-databind`) | Industry standard, simple API |
| Build Tool | Maven | Standard Java project management |
| Deduplication | `HashSet<String>` | O(1) lookup, simple and efficient |
| Score Storage | `TreeMap<String, Integer>` | Auto-sorted keys, clean iteration |

---

## Project Structure

```
quiz-leaderboard/
├── pom.xml                          # Maven build config + Jackson dependency
├── README.md                        # This file
└── src/
    └── main/
        └── java/
            └── QuizLeaderboard.java # Main application (single file)
```

---

## API Reference

**Base URL:** `https://devapigw.vidalhealthtpa.com/srm-quiz-task`

### GET /quiz/messages

Fetches quiz events for a given poll index.

| Parameter | Type | Description |
|---|---|---|
| `regNo` | String | Your registration number |
| `poll` | Integer | Poll index (0 to 9) |

**Example Request:**
```
GET /quiz/messages?regNo=RA2311043010114&poll=0
```

**Example Response:**
```json
{
  "regNo": "RA2311043010114",
  "setId": "SET_1",
  "pollIndex": 0,
  "events": [
    { "roundId": "R1", "participant": "Alice", "score": 10 },
    { "roundId": "R1", "participant": "Bob", "score": 20 }
  ]
}
```

---

### POST /quiz/submit

Submits the final leaderboard.

**Request Body:**
```json
{
  "regNo": "RA2311043010114",
  "leaderboard": [
    { "participant": "Bob",     "totalScore": 295 },
    { "participant": "Alice",   "totalScore": 280 },
    { "participant": "Charlie", "totalScore": 260 }
  ]
}
```

**Example Response:**
```json
{
  "regNo": "RA2311043010114",
  "totalPollsMade": 11,
  "submittedTotal": 835,
  "attemptCount": 1
}
```

---

## How to Run

### Prerequisites

- Java 17 or higher
- Maven 3.8 or higher

Verify with:
```bash
java -version
mvn -version
```

### Steps

```bash
# 1. Clone the repository
git clone https://github.com/your-username/quiz-leaderboard.git
cd quiz-leaderboard

# 2. Build (downloads Jackson dependency automatically)
mvn clean package

# 3. Run
java -jar target/quiz-leaderboard-1.0-SNAPSHOT-jar-with-dependencies.jar
```

> ⚠️ The program takes approximately **50 seconds** to complete due to the mandatory 5-second delay between each of the 10 polls.

---

## Sample Console Output

```
Starting to poll the quiz API...

Poll 0: GET https://devapigw.vidalhealthtpa.com/srm-quiz-task/quiz/messages?regNo=RA2311043010114&poll=0
  Status: 200
  [NEW] Alice +75 (round: R1)
  [NEW] Bob +80 (round: R1)
  Waiting 5 seconds...

Poll 1: GET https://devapigw.vidalhealthtpa.com/srm-quiz-task/quiz/messages?regNo=RA2311043010114&poll=1
  Status: 200
  [DUPLICATE] Skipping: R1|Alice
  [NEW] Charlie +150 (round: R1)
  Waiting 5 seconds...

...

Poll 9: GET https://devapigw.vidalhealthtpa.com/srm-quiz-task/quiz/messages?regNo=RA2311043010114&poll=9
  Status: 200
  [DUPLICATE] Skipping: R2|Charlie

=== LEADERBOARD ===
Bob: 295
Alice: 280
Charlie: 260
Total Score (all participants): 835

Submitting leaderboard...
Payload: {"regNo":"RA2311043010114","leaderboard":[{"participant":"Bob","totalScore":295},{"participant":"Alice","totalScore":280},{"participant":"Charlie","totalScore":260}]}

=== SUBMISSION RESPONSE ===
Status: 201
Body: {"regNo":"RA2311043010114","totalPollsMade":11,"submittedTotal":835,"attemptCount":1}
```

---

## Key Implementation Details

### Why `roundId + participant` as the dedup key?

The same participant can appear in multiple rounds with different scores — those are valid and should be counted. Only the exact same `(roundId, participant)` pair appearing again is a duplicate.

```java
String key = roundId + "|" + participant;
if (seen.contains(key)) {
    // duplicate — skip
} else {
    seen.add(key);
    scoreMap.merge(participant, score, Integer::sum);
}
```

### Why `Map.merge()` instead of `put()`?

`merge()` handles both cases in one line — if the key doesn't exist yet, it inserts the value; if it does, it applies the combining function (`Integer::sum`).

```java
scoreMap.merge(participant, score, Integer::sum);
// Equivalent to:
// if (!scoreMap.containsKey(participant)) scoreMap.put(participant, score);
// else scoreMap.put(participant, scoreMap.get(participant) + score);
```

### Why `TreeMap` for score storage?

`TreeMap` keeps keys sorted alphabetically, which makes iteration predictable. Final sorting by score is done separately before submission.

---

## Author

**Registration No:** RA2311043010114  
**Institution:** SRM Institute of Science and Technology  
**Assignment:** Bajaj Finserv Health — Java Qualifier (April 2026)
