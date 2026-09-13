---
document_id: refund-policy-conflict-current
status: current
tenant_id: public
trust: trusted
version: conflict-fixture
policy_family: refund
conflicts_with: refund-policy
---
# 충돌 실험용 환불 정책

이 문서는 실행 설정에서 명시적으로 포함하는 충돌 평가 fixture다. 환불 접수 기한은
결제일로부터 7일이다. 현재 환불 정책 v2의 30일 규칙과 양립할 수 없으므로
두 문서가 함께 검색되면 자동 답변하지 말고 담당자 확인으로 전환해야 한다.
