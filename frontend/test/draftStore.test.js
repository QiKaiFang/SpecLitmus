import test from 'node:test'
import assert from 'node:assert/strict'
import { clearDraft, DRAFT_STORAGE_KEY, loadDraft, saveDraft } from '../src/draftStore.js'

function memoryStorage() {
  const values = new Map()
  return {
    getItem: (key) => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, value),
    removeItem: (key) => values.delete(key),
  }
}

test('saves and restores only requirement and answer draft fields', () => {
  const storage = memoryStorage()
  saveDraft(storage, {
    requirementText: '需求正文',
    answerDrafts: [{ questionId: 'Q001', question: '如何计算？', text: '按自然日。' }],
    apiKey: 'must-not-be-stored',
    internalError: 'must-not-be-stored',
  })

  assert.deepEqual(loadDraft(storage), {
    requirementText: '需求正文',
    answerDrafts: [{ questionId: 'Q001', question: '如何计算？', text: '按自然日。' }],
  })
  const raw = storage.getItem(DRAFT_STORAGE_KEY)
  assert.equal(raw.includes('apiKey'), false)
  assert.equal(raw.includes('internalError'), false)
})

test('ignores corrupt data and clears persisted drafts', () => {
  const storage = memoryStorage()
  storage.setItem(DRAFT_STORAGE_KEY, '{broken')
  assert.deepEqual(loadDraft(storage), { requirementText: '', answerDrafts: [] })

  saveDraft(storage, { requirementText: '需求', answerDrafts: [] })
  assert.equal(clearDraft(storage), true)
  assert.deepEqual(loadDraft(storage), { requirementText: '', answerDrafts: [] })
})

test('reports unavailable browser storage', () => {
  assert.equal(saveDraft(null, { requirementText: '需求', answerDrafts: [] }), false)
  assert.equal(clearDraft(null), false)
})
