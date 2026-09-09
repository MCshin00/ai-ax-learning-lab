"""Working example. Amounts are integer KRW; no payment service is called."""


def refund_amount(paid: int, fee: int) -> int:
    if paid < 0 or fee < 0:
        raise ValueError("금액과 수수료는 0 이상이어야 합니다.")
    return max(0, paid - fee)


if __name__ == "__main__":
    print(refund_amount(10000, 2000))
