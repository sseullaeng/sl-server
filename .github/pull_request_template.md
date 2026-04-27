## 변경 요약
<!-- 무엇을 왜 바꿨는지 1~3줄 -->

## 변경 유형
- [ ] feat (새 기능)
- [ ] fix (버그 수정)
- [ ] refactor (리팩터링)
- [ ] chore / docs / test / style

## 관련 이슈
<!-- close #이슈번호 -->

## 체크리스트
- [ ] 로컬 빌드 통과 (`./gradlew build`)
- [ ] 신규/변경 로직에 단위 테스트 추가
- [ ] 응답 포맷 `ApiResponse<T>` / 페이징 `PageResponse<T>` 준수
- [ ] 시크릿/민감정보 커밋 없음
- [ ] N+1 쿼리 점검 (필요 시 fetch join)

## 보안 민감 영역 변경 여부
- [ ] `global/security/**`
- [ ] `domain/auth/**`
- [ ] `domain/payment/**` / `domain/point/**`
- [ ] 결제 웹훅 / OAuth 콜백 / WebSocket 인증
- [ ] 동시성/잔액 처리 코드

> 위 중 하나라도 체크되면 본문에 `@codex review` 표시 후 머지 보류.

## 테스트 방법
<!-- 리뷰어가 검증할 수 있도록: API 호출 예시, 시나리오, 화면 등 -->
