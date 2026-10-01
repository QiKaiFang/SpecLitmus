<script setup>
import { computed, ref } from 'vue'
import { createApiClient } from './api/createApiClient'
import { ReviewApiError } from './api/apiClient'
import { reviewR1 } from './data/reviewR1'

const { client, fixtureMode, fixtureScenario } = createApiClient()
const MAX_REQUIREMENT_LENGTH = 2000
const requirementText = ref('')
const reviewedRequirement = ref('')
const questions = ref([])
const metadata = ref(null)
const errorMessage = ref('')
const errorRetryable = ref(false)
const isReviewing = ref(false)

const characterCount = computed(() => requirementText.value.length)
const isBlankRequirement = computed(() => !requirementText.value.trim())
const isOverCharacterLimit = computed(() => characterCount.value > MAX_REQUIREMENT_LENGTH)
const canSubmitReview = computed(() =>
  !isReviewing.value && !isBlankRequirement.value && !isOverCharacterLimit.value,
)
const backendFixtureMode = computed(() => metadata.value?.model === 'fixture')
const hasCurrentResult = computed(() =>
  reviewedRequirement.value !== '' && reviewedRequirement.value === requirementText.value,
)

function loadExample() {
  if (isReviewing.value) return
  requirementText.value = reviewR1
  clearError()
}

function clearError() {
  errorMessage.value = ''
  errorRetryable.value = false
}

async function submitReview() {
  if (!canSubmitReview.value) return

  const submittedText = requirementText.value
  isReviewing.value = true
  clearError()
  questions.value = []
  metadata.value = null
  reviewedRequirement.value = ''

  try {
    const result = await client.review(submittedText)
    questions.value = result.questions || []
    metadata.value = result.metadata || null
    reviewedRequirement.value = submittedText
  } catch (error) {
    errorMessage.value = error instanceof Error ? error.message : '审查失败，请稍后重试。'
    errorRetryable.value = !(error instanceof ReviewApiError) || error.retryable
  } finally {
    isReviewing.value = false
  }
}

function retryReview() {
  submitReview()
}

function typeLabel(type) {
  return {
    AMBIGUITY: '歧义',
    POSSIBLE_CONFLICT: '可能冲突',
    MISSING_BOUNDARY: '缺少边界',
  }[type] || type
}
</script>

<template>
  <main class="page-shell">
    <header class="topbar">
      <a class="brand" href="/" aria-label="需求试纸首页">
        <span class="brand-mark" aria-hidden="true">试</span>
        <span>需求试纸</span>
      </a>
      <span v-if="fixtureMode || backendFixtureMode" class="fixture-badge">
        {{ fixtureMode ? `固定测试数据 · ${fixtureScenario}` : '后端固定测试模式' }}
      </span>
      <span v-else class="stage-label">S2 · 状态反馈</span>
    </header>

    <section class="intro">
      <p class="eyebrow">SPECIFICATION LITMUS</p>
      <h1>让需求先经得起追问</h1>
      <p>提交一段功能需求，找出值得进一步确认的场景和规则。</p>
    </section>

    <div class="workspace">
      <section class="panel input-panel" aria-labelledby="input-heading">
        <div class="panel-heading">
          <div>
            <p class="section-index">01 / INPUT</p>
            <h2 id="input-heading">输入需求</h2>
          </div>
          <button class="text-button" type="button" :disabled="isReviewing" @click="loadExample">
            载入示例
          </button>
        </div>

        <label class="visually-hidden" for="requirement-text">需求正文</label>
        <textarea
          id="requirement-text"
          v-model="requirementText"
          placeholder="粘贴一段功能需求，描述越具体越容易发现边界……"
          spellcheck="false"
          :disabled="isReviewing"
          :aria-invalid="isOverCharacterLimit"
          aria-describedby="requirement-limit"
          @input="clearError"
        ></textarea>

        <div class="input-footer">
          <span :class="{ 'character-count--invalid': isOverCharacterLimit }">
            {{ characterCount }} / {{ MAX_REQUIREMENT_LENGTH }} 字符
          </span>
          <button
            class="primary-button"
            type="button"
            :disabled="!canSubmitReview"
            @click="submitReview"
          >
            <span v-if="isReviewing" class="spinner" aria-hidden="true"></span>
            {{ isReviewing ? '正在审查…' : '审查需求' }}
          </button>
        </div>
        <p id="requirement-limit" class="input-hint" :class="{ 'input-hint--invalid': isOverCharacterLimit }">
          <template v-if="isBlankRequirement">请输入需求正文后再审查。</template>
          <template v-else-if="isOverCharacterLimit">
            已超过 {{ characterCount - MAX_REQUIREMENT_LENGTH }} 字符，请删减至 {{ MAX_REQUIREMENT_LENGTH }} 字符以内。
          </template>
          <template v-else>需求正文最多 {{ MAX_REQUIREMENT_LENGTH }} 字符。</template>
        </p>
        <div v-if="errorMessage" class="error-notice" role="alert">
          <p class="error-message">{{ errorMessage }}</p>
          <button v-if="errorRetryable" class="retry-button" type="button" :disabled="!canSubmitReview" @click="retryReview">
            重试
          </button>
        </div>
      </section>

      <section class="results" aria-labelledby="results-heading" aria-live="polite">
        <div class="results-heading">
          <div>
            <p class="section-index">02 / REVIEW</p>
            <h2 id="results-heading">反例问题</h2>
          </div>
          <span v-if="hasCurrentResult" class="result-count">{{ questions.length }} 项</span>
        </div>

        <div v-if="isReviewing" class="state-card loading-state">
          <span class="large-spinner" aria-hidden="true"></span>
          <p>正在阅读需求并整理需要确认的场景</p>
        </div>
        <div v-else-if="hasCurrentResult && questions.length === 0" class="state-card empty-state">
          <span class="empty-icon" aria-hidden="true">✓</span>
          <h3>本次未发现需要确认的问题</h3>
          <p>这不代表需求完全正确，仍建议结合实际业务复核。</p>
        </div>
        <div v-else-if="hasCurrentResult" class="question-list">
          <article v-for="(item, index) in questions" :key="item.questionId" class="question-card">
            <div class="card-topline">
              <span class="card-number">{{ String(index + 1).padStart(2, '0') }}</span>
              <span class="type-badge">{{ typeLabel(item.type) }}</span>
              <span class="paragraph-label">{{ item.paragraphId }}</span>
            </div>
            <blockquote>
              <span class="quote-label">建议核对原文</span>
              <span>{{ item.quote }}</span>
            </blockquote>
            <p class="scenario-label">反例场景</p>
            <p class="scenario-text">{{ item.scenario }}</p>
            <div class="question-prompt">
              <span class="question-mark" aria-hidden="true">?</span>
              <p>{{ item.question }}</p>
            </div>
            <div v-if="item.possibleInterpretations?.length" class="interpretations">
              <span>可能的解释</span>
              <ul>
                <li v-for="interpretation in item.possibleInterpretations" :key="interpretation">
                  {{ interpretation }}
                </li>
              </ul>
            </div>
          </article>
        </div>
        <div v-else class="state-card initial-state">
          <span class="initial-icon" aria-hidden="true">✳</span>
          <h3>审查结果会显示在这里</h3>
          <p>载入示例或粘贴需求，然后开始首轮审查。</p>
        </div>

        <p v-if="hasCurrentResult && metadata" class="metadata-line">
          模型 {{ metadata.model }} <span>·</span> Prompt {{ metadata.promptVersion }}
        </p>
      </section>
    </div>

    <footer class="page-footer">
      <span>模型输出是辅助审查建议，业务规则仍由你确认。</span>
      <span>引用已与原文逐字核对，问题语义仍需人工复核</span>
    </footer>
  </main>
</template>
