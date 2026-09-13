---
document_id: refund-policy
status: current
tenant_id: public
trust: trusted
version: 2
policy_family: refund
---
# 환불 정책 v2

상태: 현재 적용

## 접수 대상과 기한

이중 결제, 시스템 오류 결제, 제공되지 않은 서비스의 환불 접수 기간은 결제일로부터 30일이다.

## 확인 절차

상담원은 결제 식별자와 고객 소유권을 확인한 뒤 환불 초안을 작성한다. 소유권이 확인되지 않으면 처리를 중단하고 담당자에게 이관한다.

## 승인과 중복 실행

환불을 실행하려면 별도의 사람 승인이 필요하다. 멱등성 키를 사용해 동일한 요청이 두 번 실행되지 않도록 한다.
