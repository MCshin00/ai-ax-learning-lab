"""실제 연결은 학습자가 IDE에서 실행합니다. 테스트는 설정과 팩터리를 주입합니다."""


def live_dependencies(*, get_setting=None, chat_factory=None, embedding_factory=None):
    if get_setting is None:
        from os import getenv
        get_setting = getenv
    if get_setting("AI_AX_LIVE") != "1":
        raise ValueError("실제 연결은 IDE 실행 설정의 AI_AX_LIVE=1이 필요합니다.")
    names = ("OPENAI_API_KEY", "OPENAI_MODEL", "OPENAI_EMBEDDING_MODEL")
    values = {name: get_setting(name) for name in names}
    if not all(values.values()):
        raise ValueError("IDE 실행 설정에 API 키·대화 모델·임베딩 모델 이름을 지정하세요.")
    if chat_factory is None or embedding_factory is None:
        from langchain_openai import ChatOpenAI, OpenAIEmbeddings
        chat_factory = chat_factory or ChatOpenAI
        embedding_factory = embedding_factory or OpenAIEmbeddings
    common = {"api_key": values["OPENAI_API_KEY"], "max_retries": 0}
    return (
        chat_factory(model=values["OPENAI_MODEL"], timeout=20, **common),
        embedding_factory(model=values["OPENAI_EMBEDDING_MODEL"], request_timeout=20, **common),
    )
