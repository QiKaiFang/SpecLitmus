import test from 'node:test'
import assert from 'node:assert/strict'
import { buildMarkdownReport, reportFilename } from '../src/reportExporter.js'

const question = {
  questionId: 'Q001',
  type: 'AMBIGUITY',
  paragraphId: 'P001',
  quote: '每天 | `自然日`',
  scenario: '第一行\n第二行',
  question: '按哪种口径？',
  possibleInterpretations: ['自然日', '滚动 `24` 小时'],
}

test('builds a complete report and safely preserves markdown-shaped text', () => {
  const report = buildMarkdownReport({
    generatedAt: '2026年10月1日 10:00:00',
    requirementText: '中文需求 | 示例\n```\n原文中的围栏',
    questions: [question],
    answers: { Q001: '按北京时间自然日。\n保留多行。' },
    reviewMetadata: { model: 'fixture|review', promptVersion: 'review-v2' },
    acceptanceResult: {
      cases: [{
        caseId: 'C001', title: '跨日 `领取`', given: '已领取', when: '跨日', then: '可以领取',
        basis: [
          { sourceType: 'ORIGINAL', sourceId: 'P001', quote: '每天 | `自然日`' },
          { sourceType: 'ANSWER', sourceId: 'Q001', quote: '按北京时间自然日。' },
        ],
      }],
      pendingQuestionIds: ['Q001'],
      warnings: ['规则 | 仍需复核'],
      metadata: { model: 'fixture', promptVersion: 'acceptance-v1' },
    },
  })

  for (const heading of ['## 原需求', '## 审查问题', '## 用户确认回答', '## 验收用例', '## 尚未确认的问题', '## 模型提示']) {
    assert.match(report, new RegExp(heading))
  }
  assert.match(report, /````\n中文需求 \| 示例/)
  assert.match(report, /fixture\\\|review/)
  assert.match(report, /按北京时间自然日。\n保留多行。/)
  assert.match(report, /用户回答 Q001/)
})

test('rejects export without a validated acceptance result', () => {
  assert.throws(() => buildMarkdownReport({ requirementText: '需求' }), /缺少当前有效/)
})

test('uses the local calendar date in the filename', () => {
  assert.equal(reportFilename(new Date(2026, 9, 1, 23, 59)), 'requirement-review-2026-10-01.md')
})
