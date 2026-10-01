export const DRAFT_STORAGE_KEY = 'speclitmus.draft.v1'

function normalizeAnswerDrafts(value) {
  if (!Array.isArray(value)) return []
  return value
    .filter((item) => item && typeof item.text === 'string' && item.text.trim())
    .map((item) => ({
      questionId: typeof item.questionId === 'string' ? item.questionId : '',
      question: typeof item.question === 'string' ? item.question : '',
      text: item.text,
    }))
}

export function loadDraft(storage) {
  try {
    const raw = storage?.getItem(DRAFT_STORAGE_KEY)
    if (!raw) return { requirementText: '', answerDrafts: [] }
    const parsed = JSON.parse(raw)
    return {
      requirementText: typeof parsed?.requirementText === 'string' ? parsed.requirementText : '',
      answerDrafts: normalizeAnswerDrafts(parsed?.answerDrafts),
    }
  } catch {
    return { requirementText: '', answerDrafts: [] }
  }
}

export function saveDraft(storage, draft) {
  const normalized = {
    requirementText: typeof draft?.requirementText === 'string' ? draft.requirementText : '',
    answerDrafts: normalizeAnswerDrafts(draft?.answerDrafts),
  }
  if (!storage) return false
  try {
    storage.setItem(DRAFT_STORAGE_KEY, JSON.stringify(normalized))
    return true
  } catch {
    return false
  }
}

export function clearDraft(storage) {
  if (!storage) return false
  try {
    storage.removeItem(DRAFT_STORAGE_KEY)
    return true
  } catch {
    return false
  }
}
