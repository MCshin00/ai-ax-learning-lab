# 1주차 Microtask — 티켓 제목 정규화

요구사항:

- `null`과 공백 문자로만 이뤄진 입력은 `IllegalArgumentException`을 던집니다.
- 앞뒤 공백을 제거합니다.
- 연속된 공백 문자를 일반 공백 한 칸으로 바꿉니다.
- 정규화한 제목이 Unicode code point 기준 80자를 넘으면 앞의 80개 code point만 남깁니다.
- 공개 테스트를 삭제하거나 약화하지 않습니다.
