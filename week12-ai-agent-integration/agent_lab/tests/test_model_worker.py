import unittest
from unittest.mock import patch
from ai_contracts import PolicyEvidence
from business import PolicySearch, load_policies
from model_worker import ModelWorker, build_worker
from offline import ScriptedModel, TopicEmbeddings, call


class ModelWorkerTests(unittest.TestCase):
    def setUp(self):
        self.environment = patch('os.environ', {})
        self.environment.start()
        self.addCleanup(self.environment.stop)

    def test_remaining_budget_covers_tool_loop_and_preserves_search_on_limit(self):
        model=ScriptedModel(responses=[call('search_policy', {'query':'취소 출고'}, 'search')])
        worker=ModelWorker(lambda *args:model,
            PolicyEvidence(PolicySearch(TopicEmbeddings(),load_policies()),load_policies()),mode='TEST')
        result=worker.execute({'operation':'draft','payload':{},'remaining_calls':1})
        self.assertEqual('limit_reached',result['status'])
        self.assertEqual(1,result['model_calls'])
        self.assertTrue(result['sources'])
        self.assertEqual('search',result['trace'][0]['stage'])

    def test_model_failure_counts_attempt_and_never_becomes_empty_success(self):
        worker=ModelWorker(lambda *args:ScriptedModel(responses=[TimeoutError()]),None,mode='TEST')
        result=worker.execute({'operation':'intake','payload':{},'remaining_calls':2})
        self.assertEqual('unavailable',result['status'])
        self.assertEqual(1,result['model_calls'])

    def test_intake_returns_schema_value_through_actual_langchain(self):
        result=build_worker().execute({'operation':'intake',
            'payload':{'request':'A-102 상태가 궁금해요.','previous':None},'remaining_calls':6})
        self.assertEqual('ok',result['status'])
        self.assertEqual(['A-102'],result['value']['order_ids'])
        self.assertEqual(1,result['model_calls'])

    def test_invalid_budget_is_rejected_before_model_creation(self):
        worker=ModelWorker(lambda *args:self.fail('모델을 만들면 안 됩니다.'),None,mode='TEST')
        for remaining in (-1,7,True):
            with self.assertRaises(ValueError):
                worker.execute({'operation':'intake','payload':{},'remaining_calls':remaining})


if __name__=='__main__':
    unittest.main()
