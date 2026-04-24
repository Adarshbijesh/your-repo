# Quiz Leaderboard System — SRM Internship Assignment

## Problem Summary

This application polls an external quiz API 10 times, deduplicates responses, aggregates scores per participant, and submits a correct leaderboard.

---

## How It Works

### Step 1: Poll API (10 times)
- Calls `GET /quiz/messages?regNo=<REG_NO>&poll=<0-9>`
- Waits **5 seconds** between each poll (mandatory)

### Step 2: Deduplicate
- Uses a `Set<String>` with key `roundId|participant`
- If the same `(roundId, participant)` pair appears again → **skip it**

### Step 3: Aggregate Scores
- Uses a `Map<String, Integer>` to accumulate scores per participant

### Step 4: Generate & Submit Leaderboard
- Sorts participants by `totalScore` descending
- Submits once via `POST /quiz/submit`

---

## Key Design Decisions

| Problem | Solution |
|---|---|
| Duplicate API data across polls | `Set<String>` dedup using `roundId + participant` composite key |
| Score aggregation | `Map.merge()` with `Integer::sum` |
| Sorted leaderboard | `List.sort()` by total score descending |
| Single submission | Submit only after all 10 polls are complete |

---

## Tech Stack

- **Java 17**
- **Maven** (build tool)
- **Jackson** (JSON parsing)
- **Java `HttpClient`** (built-in, no extra HTTP library needed)

---

## How to Run

### Prerequisites
- Java 17+
- Maven 3.8+

### Steps

```bash
# 1. Clone the repo
git clone https://github.com/your-username/quiz-leaderboard.git
cd quiz-leaderboard

# 2. Set your registration number
# Edit src/main/java/QuizLeaderboard.java
# Change: private static final String REG_NO = "YOUR_REG_NO_HERE";
# To:     private static final String REG_NO = "2024CS1234"; // your actual regNo

# 3. Build
mvn clean package

# 4. Run (takes ~50 seconds due to 5s delays between 10 polls)
java -jar target/quiz-leaderboard-1.0-SNAPSHOT-jar-with-dependencies.jar
```

---

## Sample Output

```
Starting to poll the quiz API...

Poll 0: GET https://devapigw.vidalhealthtpa.com/srm-quiz-task/quiz/messages?regNo=2024CS101&poll=0
  Status: 200
  [NEW] Alice +10 (round: R1)
  [NEW] Bob +20 (round: R1)
  Waiting 5 seconds...

Poll 1: GET ...
  [DUPLICATE] Skipping: R1|Alice
  [NEW] Alice +30 (round: R2)
  ...

=== LEADERBOARD ===
Bob: 120
Alice: 100
Total Score (all participants): 220

Submitting leaderboard...

=== SUBMISSION RESPONSE ===
Status: 200
Body: {"isCorrect":true,"isIdempotent":true,"submittedTotal":220,"expectedTotal":220,"message":"Correct!"}
```

---

## Project Structure

```
quiz-leaderboard/
├── pom.xml
├── README.md
└── src/
    └── main/
        └── java/
            └── QuizLeaderboard.java
```
