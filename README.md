# NovaCare Customer Service Chatbot

[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://openjdk.org/)
[![Spring Boot 3.3.4](https://img.shields.io/badge/Spring%20Boot-3.3.4-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Apache OpenNLP 2.5.3](https://img.shields.io/badge/Apache%20OpenNLP-2.5.3-orange.svg)](https://opennlp.apache.org/)
[![Evaluation Accuracy](https://img.shields.io/badge/Held--Out%20Accuracy-98.33%25-success.svg)]()
[![Tests](https://img.shields.io/badge/JUnit%205-31%20Passed-success.svg)]()

An enterprise-ready, autonomous Customer Service Chatbot built with **Java 17**, **Spring Boot 3.x**, and **Apache OpenNLP 2.5.3**. The system features hybrid deterministic rule dispatching, statistical Maximum Entropy intent categorization with bag-of-words and bigram n-grams, regex order ID extraction, session conversational memory, offline-first responsive web UI, and an interactive CLI.

---

## Architecture Diagram

```
+---------------------------------------------------------------------------------------+
|                                    Client Layer                                       |
|  +-----------------------------------+             +-------------------------------+  |
|  |     Browser UI (HTML5/CSS3/JS)    |             |       Interactive CLI         |  |
|  |   (Zero external CDN, Offline)    |             |     (Spring Profile: cli)     |  |
|  +-----------------+-----------------+             +---------------+---------------+  |
+--------------------|-----------------------------------------------|------------------+
                     | HTTP POST /api/chat                           |
                     v                                               | Direct Injection
+--------------------------------------------------------------------v------------------+
|                            Spring Boot REST Controller Layer                          |
|                       +------------------------------------------+                    |
|                       |  ChatController & GlobalExceptionHandler |                    |
|                       +--------------------+---------------------+                    |
+--------------------------------------------|------------------------------------------+
                                             |
                                             v
+---------------------------------------------------------------------------------------+
|                                  ChatBotService                                       |
|                                                                                       |
|   1. Session Context Retrieval (SessionContextStore: ConcurrentHashMap + TTL/Size)   |
|   2. Entity Extraction: OrderIdExtractor (#12345, ORD-9921, "order 54321")            |
|                                                                                       |
|                 +-------------------------------------------------+                   |
|                 |                   RuleEngine                    |                   |
|                 |     (Handoff: human / agent / representative)   |                   |
|                 +--------+-------------------------------+--------+                   |
|                          | Matched                       | Not matched                |
|                          v                               v                            |
|                 [contact_human_agent]         +-----------------------+               |
|                                               |   IntentClassifier    |               |
|                                               | (OpenNLP MaxEnt Doccat|               |
|                                               |  + TextPreprocessor)  |               |
|                                               +-----------+-----------+               |
|                                                           |                           |
|                        +----------------------------------+                           |
|                        | Confidence >= 0.45                                           |
|                        v                                  Confidence < 0.45           |
|            [Recognized Intent Response]                           v                   |
|            - Inject {order_id} if captured              [Fallback Response]           |
|            - Prompt if order ID missing                 + Suggested Topics            |
|            - Cache turn in SessionContext                                             |
+---------------------------------------------------------------------------------------+
                                             ^
                                             |
+---------------------------------------------------------------------------------------+
|                       NLP Training & Evaluation Subsystem                             |
|  intents.json + stopwords.txt -> TextPreprocessor -> Stratified 80/20 Train/Test Split|
|  -> ModelEvaluator (P/R/F1, Confusion Matrix CSV) -> Full Dataset Retrain & Save bin |
+---------------------------------------------------------------------------------------+
```

---

## Prerequisites

- **Java**: JDK 17 or higher (tested with Oracle OpenJDK 21)
- **Maven**: Maven Wrapper (`./mvnw` or `mvnw.cmd`) is included in the project root. No standalone Maven installation is required.

---

## Quick Start

### 1. Build and Run All Tests
```bash
./mvnw clean verify
```
*(On Windows PowerShell: `.\mvnw.cmd clean verify`)*

All 31 unit and integration tests will execute, including held-out model accuracy validation (asserting $\ge 90\%$, currently achieving **98.33%**).

### 2. Run the Web Application
```bash
./mvnw spring-boot:run
```
*(On Windows PowerShell: `.\mvnw.cmd spring-boot:run`)*

Access the responsive web interface in your browser:
```
http://localhost:8080/
```
*(Note: To run on a custom port, pass `SERVER_PORT=9090 ./mvnw spring-boot:run` or `-Dserver.port=9090`)*

### 3. Run the Interactive CLI
The chatbot provides a terminal interface driven by the `cli` Spring profile:
```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=cli
```
*(On Windows PowerShell: `.\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=cli"`)*

Example CLI session:
```text
==============================================================
        Customer Service Chatbot CLI (Session: cli-3a9b1c4e)
        Type your message and press ENTER.
        Type 'exit' or 'quit' to terminate.
==============================================================

You > where is my package #12345?
Chatbot [order_status | conf: 0.98] > Your order #12345 is currently in transit and scheduled to arrive on time.
Suggestions: When will it arrive? | Cancel this order | Talk to a live agent

You > exit
Chatbot > Thank you for using our customer support CLI. Goodbye!
```

---

## Training & Evaluation Workflow

1. **Dataset Loading**:
   - `intents.json` (15 intents, 20 varied patterns each, 3+ responses per intent, and fallback responses) is loaded from the classpath.
   - `stopwords.txt` is loaded, filtering noise words while strictly preserving negation tokens (`not`, `no`, `never`, `nor`, `cannot`, `n't`).

2. **Text Preprocessing Pipeline** (`TextPreprocessor`):
   - Lowercasing and whitespace normalization.
   - Contraction expansion (`don't` $\rightarrow$ `do not`, `can't` $\rightarrow$ `can not`).
   - Removal of URLs, emojis, and punctuation.
   - Stopword removal with negation retention.
   - Porter Stemming (`opennlp.tools.stemmer.PorterStemmer`).

3. **Stratified 80/20 Train/Test Partitioning** (`ModelEvaluator`):
   - Using a fixed seed (`chatbot.seed=42`), samples from each intent are partitioned into 80% training (16 samples per intent = 240 samples) and 20% held-out test (4 samples per intent = 60 samples).

4. **Model Training & Evaluation**:
   - Evaluates on the held-out test set using an OpenNLP Maximum Entropy classifier (`DocumentCategorizerME`) with combined Bag-of-Words and Bigram n-gram feature generators.
   - Generates accuracy, macro precision/recall/F1, per-intent breakdowns, and exports a confusion matrix CSV to `target/reports/confusion-matrix.csv`.

5. **Production Model Retraining**:
   - The classifier retrains on 100% of the dataset (300 samples) and serializes the binary model to `models/intent-model.bin` for runtime prediction.

### Evaluation Metrics Snapshot

```text
================ MODEL EVALUATION SUMMARY ================
Accuracy: 98.33% (59/60 correct)
Macro Precision: 0.9867
Macro Recall:    0.9833
Macro F1-Score:  0.9839
---------------- Per-Intent Breakdown --------------------
Intent                    | Precision  | Recall     | F1-Score  
----------------------------------------------------------
greeting                  | 1.0000     | 1.0000     | 1.0000    
goodbye                   | 1.0000     | 1.0000     | 1.0000    
thanks                    | 1.0000     | 1.0000     | 1.0000    
order_status              | 1.0000     | 1.0000     | 1.0000    
cancel_order              | 0.8000     | 1.0000     | 0.8889    
refund_request            | 1.0000     | 0.7500     | 0.8571    
return_policy             | 1.0000     | 1.0000     | 1.0000    
shipping_info             | 1.0000     | 1.0000     | 1.0000    
payment_issue             | 1.0000     | 1.0000     | 1.0000    
change_address            | 1.0000     | 1.0000     | 1.0000    
product_availability      | 1.0000     | 1.0000     | 1.0000    
contact_human_agent       | 1.0000     | 1.0000     | 1.0000    
business_hours            | 1.0000     | 1.0000     | 1.0000    
account_help              | 1.0000     | 1.0000     | 1.0000    
complaint                 | 1.0000     | 1.0000     | 1.0000    
==========================================================
```

---

## REST API Reference & cURL Examples

### 1. Send Chat Message
**Endpoint**: `POST /api/chat`
**Headers**: `Content-Type: application/json`

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "test-session-1", "message": "Where is my order #12345?"}'
```

**Response (200 OK)**:
```json
{
  "intent": "order_status",
  "confidence": 0.982,
  "response": "Your order #12345 is currently in transit and scheduled to arrive on time. You can check the tracking link sent to your email.",
  "suggestions": [
    "When will it arrive?",
    "Cancel this order",
    "Talk to a live agent"
  ]
}
```

### 2. Follow-Up Leveraging Session Memory
Reusing the same `sessionId`, the chatbot remembers order `#12345`:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "test-session-1", "message": "I changed my mind stop my order"}'
```

**Response (200 OK)**:
```json
{
  "intent": "cancel_order",
  "confidence": 0.991,
  "response": "We have submitted the cancellation request for order #12345. You will receive a confirmation email shortly.",
  "suggestions": [
    "When will I get my refund?",
    "What is your return policy?",
    "Speak to a representative"
  ]
}
```

### 3. Immediate Human Agent Escalation (Rule Engine)
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "test-session-2", "message": "I need to talk to a human agent please"}'
```

**Response (200 OK)**:
```json
{
  "intent": "contact_human_agent",
  "confidence": 1.0,
  "response": "I am connecting you with a customer support specialist now. Typical wait time is under 3 minutes.",
  "suggestions": [
    "What are your business hours?",
    "Track my order",
    "File a complaint"
  ]
}
```

### 4. Low-Confidence / Gibberish Handling (Fallback)
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "test-session-3", "message": "asdf qwerty zxcv blarg"}'
```

**Response (200 OK)**:
```json
{
  "intent": "fallback",
  "confidence": 0.067,
  "response": "I'm sorry, I didn't quite understand that. Could you please rephrase or choose from the suggested topics below?",
  "suggestions": [
    "Track my order",
    "Return an item",
    "Speak to a live agent"
  ]
}
```

### 5. Input Validation Error (Blank Message)
```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "test-session-4", "message": "   "}'
```

**Response (400 Bad Request)**:
```json
{
  "error": "Bad Request",
  "message": "message is required and cannot be blank"
}
```

### 6. Get Available Intent Tags
**Endpoint**: `GET /api/intents`

```bash
curl http://localhost:8080/api/intents
```

**Response (200 OK)**:
```json
[
  "greeting",
  "goodbye",
  "thanks",
  "order_status",
  "cancel_order",
  "refund_request",
  "return_policy",
  "shipping_info",
  "payment_issue",
  "change_address",
  "product_availability",
  "contact_human_agent",
  "business_hours",
  "account_help",
  "complaint"
]
```

### 7. Reset Conversational Session
**Endpoint**: `POST /api/reset`

```bash
curl -X POST http://localhost:8080/api/reset \
  -H "Content-Type: application/json" \
  -d '{"sessionId": "test-session-1"}'
```

**Response (200 OK)**:
```json
{
  "status": "success",
  "message": "Session test-session-1 cleared successfully"
}
```

---

## 3 Example Conversations

### Conversation 1: Order Inquiry and Contextual Follow-Up
```text
User: Where is my order #12345?
Bot:  Your order #12345 is currently in transit and scheduled to arrive on time. You can check the tracking link sent to your email.
      [Suggestions: When will it arrive? | Cancel this order | Talk to a live agent]

User: Cancel my order please.
Bot:  We have submitted the cancellation request for order #12345. You will receive a confirmation email shortly.
      [Suggestions: When will I get my refund? | What is your return policy? | Speak to a representative]

User: How do returns work?
Bot:  We offer a 30-day return policy on all unworn, unopened, and original condition items with tags attached.
```

### Conversation 2: Missing Order ID Prompt & Follow-Up
```text
User: Can you check my package delivery?
Bot:  Could you please provide your order ID (for example, #12345 or ORD-9921) so I can look that up for you?
      [Suggestions: My order is #12345 | Check ORD-4321 | Talk to a live agent]

User: ORD-8891
Bot:  Order ORD-8891 has been processed and is currently with our courier partner. Expect delivery in 2-3 business days.
```

### Conversation 3: Agent Escalation & Fallback Recovery
```text
User: asdf zxcv qwerty
Bot:  I'm sorry, I didn't quite understand that. Could you please rephrase or choose from the suggested topics below?
      [Suggestions: Track my order | Return an item | Speak to a live agent]

User: I need to talk to a human agent please
Bot:  I am connecting you with a customer support specialist now. Typical wait time is under 3 minutes.
```

---

## Adding a New Intent

To introduce a new intent (e.g., `store_locations`):

1. **Edit `src/main/resources/intents.json`**:
   Add the new intent entry inside `"intents"`:
   ```json
   {
     "tag": "store_locations",
     "patterns": [
       "where is your nearest store",
       "find store locations near me",
       "do you have physical retail stores",
       "store locator address",
       "find nearest branch"
     ],
     "responses": [
       "You can find your closest store using our online store locator at example.com/stores.",
       "We have over 50 retail locations nationwide! Check example.com/stores for addresses and hours."
     ]
   }
   ```
2. **Re-evaluate and build**:
   ```bash
   ./mvnw clean verify
   ```
   The application will automatically pick up the new intent, split it into train/test, verify the model passes evaluation criteria, and serialize the updated model.

---

## Design Decisions & Assumptions

1. **MaxEnt with Bag-of-Words & Bigrams**:
   Maximum Entropy classification provides fast, robust probabilistic outcomes on small-to-medium corpora. Unigrams capture core vocabulary, while bigrams capture local phrase structures (e.g. `order_status`, `human_agent`).
2. **Deterministic Pre-emption (Rule Engine)**:
   Explicit requests for human intervention (`human`, `representative`, `real person`) bypass statistical classification to ensure 100% precision for escalation.
3. **In-Memory Session Context**:
   Uses `ConcurrentHashMap` with thread-safe atomic compute updates, an access TTL (default 60 minutes), and size bounding (1000 max entries) to avoid memory leaks.
4. **Offline-First Frontend**:
   Built strictly with Vanilla HTML5, modern CSS3 (custom properties, glassmorphism, flexbox/grid), and ES6 JavaScript. No external CDNs, Google Fonts, or libraries are imported, ensuring 100% offline usability.
5. **No Static Mutable State**:
   All state is managed via Spring `@Component` and `@Service` singletons with constructor injection and concurrent data structures.

---

## Limitations & Future Improvements

1. **Embedding & Deep Learning Support**: Integrate **DJL (Deep Java Library)** with ONNX Runtime to support pre-trained Transformer embeddings (e.g. MiniLM, BERT) for zero-shot paraphrase understanding.
2. **Generative LLM Augmentation**: Implement Spring AI or LangChain4j integration to generate personalized, dynamic responses while maintaining deterministic intent routing.
3. **Database & ERP Integration**: Connect order extraction to real SQL/NoSQL databases or RESTful logistics APIs (FedEx, UPS, Shopify).
4. **Containerization**: Provide a multi-stage `Dockerfile` and `docker-compose.yml` with minimal JRE base image (e.g., Eclipse Temurin Alpine).
5. **Multilingual Support**: Add OpenNLP models or multilingual tokenizers for Tamil, Spanish, German, and French customer support.
