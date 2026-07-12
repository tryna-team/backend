# TRYNA Backend

> 짧은 일정 속 맥락을 이해하고, 실제로 필요한 할 일을 제안하는 관계 기반
> 일정 관리 서비스

## 📌 About TRYNA

**TRYNA**는 사용자가 입력한 짧은 일정 속 맥락을 이해하고, 일정 전후에
실제로 필요한 할 일을 제안하는 일정 관리 서비스입니다.

같은 "회의"라는 일정이라도 전공 팀플 회의와 동아리 네트워킹 회의는
사용자가 준비해야 하는 일이 다릅니다.

TRYNA는 이러한 차이를 단순 키워드 매칭으로 처리하지 않습니다.\
일정과 행동 사이의 맥락 관계를 구조화하고, 관계 기반으로 후보를 탐색한
뒤 사용자의 기존 정보와 DB 정보를 바탕으로 실제로 필요한 할 일만
선별합니다.

사용자는 추천된 할 일을 그대로 사용할 수도 있고, 필요 없는 항목을
삭제하거나 새로운 항목을 직접 추가할 수도 있습니다.

## 📚 Tech Stacks

| Type | Tool |
| :---: | :---: |
| Language | ![Java](https://img.shields.io/badge/Java-007396?style=for-the-badge&logo=openjdk&logoColor=white) |
| Framework | ![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?style=for-the-badge&logo=springboot&logoColor=white) |
| ORM | ![Spring Data JPA](https://img.shields.io/badge/Spring_Data_JPA-6DB33F?style=for-the-badge&logo=spring&logoColor=white) |
| Security | ![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?style=for-the-badge&logo=springsecurity&logoColor=white) |
| Database | ![PostgreSQL](https://img.shields.io/badge/PostgreSQL-4169E1?style=for-the-badge&logo=postgresql&logoColor=white) |
| API Documentation | ![Swagger](https://img.shields.io/badge/Swagger-85EA2D?style=for-the-badge&logo=swagger&logoColor=black) |
| In-Memory Store | ![Valkey](https://img.shields.io/badge/Valkey-FF4438?style=for-the-badge&logo=valkey&logoColor=white) |
| Build Tool | ![Gradle](https://img.shields.io/badge/Gradle-02303A?style=for-the-badge&logo=gradle&logoColor=white) |
| Version Control | ![Git](https://img.shields.io/badge/Git-F05032?style=for-the-badge&logo=git&logoColor=white) ![GitHub](https://img.shields.io/badge/GitHub-181717?style=for-the-badge&logo=github&logoColor=white) |
| Collaboration | ![Notion](https://img.shields.io/badge/Notion-000000?style=for-the-badge&logo=notion&logoColor=white) |

## 👥 Team Members

| 담당 영역 | 담당자 | 담당 기능 |
| --- | --- | --- |
| 인증/사용자 | 사이먼/서동훈 | 회원·비회원 인증, 사용자 계정 관리 |
| 일정/캘린더 | 곤/김태현 | 일정 CRUD, 캘린더 조회, 자연어 일정 입력 |
| 분석/추천 | 레미/윤예림 | 일정 맥락 분석, 관계 기반 추천, LLM 후보 정제 |
| 준비/실행 항목 | 유복치/유다현 | 추천 항목 관리, 태스크 완료 처리, 일정·캘린더 연동 |
| 알림/외부연동/공통 | 라포/김태림 | 알림, 외부 캘린더 연동, 공통 응답 및 API 문서화 |

------------------------------------------------------------------------

## ✨ How It Works

``` text
사용자 일정 입력
       │
       ▼
Rule Based Parser + Kiwi
       │
       │ 일정 정보 구조화
       ▼
Neo4j
       │
       │ 관계 기반 할 일 후보 탐색
       ▼
Upstage LLM
       │
       │ 중복 통합 및 불필요한 후보 제거
       ▼
사용자 맞춤 할 일 제안
```

### 1. 일정 입력

사용자가 짧은 일정 제목을 입력합니다.

``` text
트라이나 전체 미팅
```

### 2. 일정 맥락 구조화

Rule Based Parser와 Kiwi를 사용하여 짧은 일정 속 정보를 구조화합니다.

``` text
트라이나 → 프로젝트 또는 그룹 맥락
미팅     → 일정 유형
전체     → 미팅의 범위
```

구조화된 데이터는 다음 단계의 추천 과정에서 활용됩니다.

``` json
{
  "title": "트라이나 전체 미팅",
  "eventType": "meeting",
  "contextTags": [
    "group_meeting",
    "people_meeting"
  ],
  "project": "tryna",
  "date": null,
  "time": null,
  "location": null
}
```

### 3. 관계 기반 후보 탐색

구조화된 일정 정보는 Neo4j 그래프 데이터베이스로 전달됩니다.

TRYNA는 일정과 행동 사이의 관계를 그래프로 관리하고, 현재 일정과 연결된
할 일 후보를 탐색합니다.

예를 들어 미팅 일정에서는 다음과 같은 후보가 탐색될 수 있습니다.

``` text
안건 정리
공유 자료 준비
회의록 준비
장소 확인
이동 시간 확인
교통편 확인
식당 후보 찾기
카페 후보 찾기
참석자 확인
```

### 4. 후보 정제

그래프에서 탐색한 모든 후보를 사용자에게 그대로 보여주지는 않습니다.

Upstage LLM이 다음 정보를 바탕으로 후보를 정제합니다.

-   사용자가 입력한 일정 정보
-   사용자가 기존에 입력한 정보
-   DB에 저장된 정보
-   Neo4j에서 탐색된 할 일 후보

이 과정에서 의미가 겹치는 항목을 묶고, 현재 일정에 불필요한 항목을
제거합니다.

### 5. 할 일 제안

최종적으로 사용자가 실제로 활용할 가능성이 높은 몇 개의 할 일만
제안합니다.

``` text
☑ 미팅 안건 정리하기
☐ 장소 및 이동시간 확인
☐ 주변 식당 후보 선정
```

추천 결과는 자동으로 강제 저장되지 않습니다.

사용자는 추천된 할 일을 자유롭게 수정할 수 있습니다.

-   추천 항목 사용
-   필요 없는 항목 삭제
-   새로운 할 일 직접 추가

TRYNA는 추천을 정답으로 제시하는 것이 아니라, 사용자가 부담 없이
받아들이고 수정할 수 있는 **가벼운 제안**으로 제공합니다.

------------------------------------------------------------------------

## 🔄 Recommendation Pipeline

| Step | Technology | Responsibility |
| --- | --- | --- |
| 1 | Rule Based Parser | 일정에서 규칙 기반 정보 추출 |
| 2 | Kiwi | 한국어 형태소 분석 및 일정 맥락 구조화 |
| 3 | Neo4j | 일정과 행동의 관계를 기반으로 후보 탐색 |
| 4 | Upstage LLM | 중복 후보 통합 및 불필요한 후보 제거 |
| 5 | TRYNA | 최종 할 일을 수정 가능한 형태로 제공 |

------------------------------------------------------------------------

## ✨ Main Features

### 📅 일정 관리

-   캘린더 메인 조회
-   일정 상세 조회
-   일정 생성
-   일정 수정
-   일정 삭제
-   사용자별 일정 관리

### 💬 자연어 일정 입력

-   짧은 자연어 일정 입력
-   일정 유형 및 맥락 분석
-   프로젝트 및 그룹 정보 추출
-   날짜, 시간, 장소 정보 구조화

### 🕸 관계 기반 할 일 추천

-   일정과 행동 사이의 관계 모델링
-   Neo4j 기반 관련 할 일 후보 탐색
-   일정 맥락에 따른 후보 확장
-   단순 키워드 매칭을 넘어선 관계 기반 추천

### 🤖 LLM 기반 후보 정제

-   사용자의 기존 정보 반영
-   DB 정보 기반 후보 정제
-   의미가 겹치는 추천 통합
-   불필요한 추천 제거
-   실제 활용 가능성이 높은 할 일 선별

### ✅ 사용자 피드백

-   추천된 할 일 사용
-   불필요한 추천 삭제
-   할 일 직접 추가
-   추천 결과 수정
-   사용자 변경 정보 기록

### 👤 회원 및 비회원 지원

-   회원 사용자 인증 및 식별
-   비회원 사용자 식별
-   인증 정보를 기반으로 한 일정 조회 및 저장
-   회원·비회원 공통 일정 API 사용

------------------------------------------------------------------------

## 🏗 Backend Architecture

``` text
Client
   │
   ▼
Controller
   │
   ▼
Service
   │
   ├── 일정 관리
   ├── 사용자 관리
   ├── 추천 결과 관리
   └── 피드백 관리
   │
   ├───────────────┐
   ▼               ▼
Relational DB     Recommendation Pipeline
                   │
                   ▼
            Rule Based Parser
                 + Kiwi
                   │
                   ▼
                 Neo4j
                   │
                   ▼
              Upstage LLM
                   │
                   ▼
               할 일 제안
```

------------------------------------------------------------------------

## 📂 Package Structure

프로젝트의 기본 패키지 구조는 `global`과 `domain`으로 구분합니다.

``` text
src/
└── main/
    ├── java/
    │   └── ...
    │       ├── global/
    │       │   ├── config/
    │       │   ├── exception/
    │       │   ├── response/
    │       │   ├── security/
    │       │   └── util/
    │       │
    │       └── domain/
    │           └── {domain}/
    │               ├── controller/
    │               │   └── docs/
    │               ├── service/
    │               ├── repository/
    │               ├── entity/
    │               ├── enums/
    │               └── dto/
    │
    └── resources/
        ├── application.yaml
        ├── application-local.yaml
        ├── application-prod.yaml
        └── application-test.yaml
```

각 계층은 다음 역할을 담당합니다.

  Layer            Responsibility
  ---------------- --------------------------------
  Controller       요청 및 응답 처리
  ControllerDocs   Swagger API 문서 작성
  Service          비즈니스 로직 및 트랜잭션 처리
  Repository       데이터 접근
  Entity           도메인 데이터 표현
  DTO              API 요청 및 응답 데이터 전달

------------------------------------------------------------------------

## 🔐 Authentication

TRYNA는 인증 정보를 기반으로 사용자를 식별합니다.

``` http
Authorization: Bearer {token}
```

회원과 비회원 모두 인증 정보를 통해 사용자를 식별하며, 일정 API에서는
인증된 사용자 정보를 기준으로 데이터를 조회하고 저장합니다.

------------------------------------------------------------------------

## 🔗 API Convention

API 경로는 다음 형식을 사용합니다.

``` text
/api/v1/{resources}
```

리소스명은 복수형 명사를 사용하며, 동작은 URL이 아닌 HTTP Method로
표현합니다.

  Method   Purpose
  -------- -----------
  GET      조회
  POST     생성
  PATCH    일부 수정
  DELETE   삭제

------------------------------------------------------------------------

## 📦 Common Response

### Success

``` json
{
  "success": true,
  "code": "COMMON_200",
  "message": "요청에 성공했습니다.",
  "data": {}
}
```

### Error

``` json
{
  "success": false,
  "code": "COMMON_400",
  "message": "잘못된 요청입니다.",
  "data": null
}
```

응답 데이터가 없는 경우 `data`는 `null`로 반환합니다.

------------------------------------------------------------------------

## 📖 API Documentation

API 문서는 Swagger와 OpenAPI를 기반으로 관리합니다.

실제 요청을 처리하는 Controller와 API 문서를 담당하는 ControllerDocs를
분리합니다.

``` text
domain/
└── {domain}/
    └── controller/
        ├── XxxController
        └── docs/
            └── XxxControllerDocs
```

API가 변경되면 Controller와 ControllerDocs를 함께 수정합니다.

------------------------------------------------------------------------

## ⚙️ Environment Configuration

환경별 설정 파일은 다음과 같이 관리합니다.

``` text
application.yaml        # 공통 설정
application-local.yaml  # 로컬 환경
application-prod.yaml   # 운영 환경
application-test.yaml   # 테스트 환경
```

민감 정보는 환경 변수 또는 별도의 로컬 설정 파일을 통해 관리하며 Git에
커밋하지 않습니다.

> ⚠️ `.env`, 인증 정보, API Key, 데이터베이스 비밀번호 등의 민감 정보는
> 저장소에 커밋하지 않습니다.

------------------------------------------------------------------------

## 🌿 Git Convention

### Branch Naming

``` text
type/nickname/issueNumber-task
```

``` text
feat/nickname/1-create-event
bug/nickname/2-calendar-response
docs/nickname/3-api-docs
```

### Commit Message

``` text
type: subject
```

``` text
feat: 일정 생성 API 추가
bug: 캘린더 조회 오류 수정
docs: README 작성
refactor: 일정 서비스 구조 개선
```

### Merge Flow

``` text
feat/*      ─┐
bug/*        │
docs/*       │
style/*      ├──> develop ──> main
refactor/*   │
chore/*     ─┘

hotfix/* ──> main
         └─> develop
```

`main` 브랜치에는 직접 작업하지 않으며, PR은 작성자 외 1명 이상의 확인
후 병합합니다.

------------------------------------------------------------------------
