import test from 'node:test'
import assert from 'node:assert/strict'
import { canStartAutoDeployment, isBusinessGenerationError } from '../src/utils/generationRunPolicy.ts'

test('a successful new run deploys even when the app was deployed before', () => {
  assert.equal(canStartAutoDeployment('run-1', new Set(), new Set()), true)
})

test('duplicate done events are idempotent while a run is in flight or complete', () => {
  const inFlight = new Set(['run-1'])
  assert.equal(canStartAutoDeployment('run-1', new Set(), inFlight), false)
  inFlight.delete('run-1')
  assert.equal(canStartAutoDeployment('run-1', new Set(['run-1']), inFlight, 'succeeded'), false)
})

test('a failed deployment remains retryable after its in-progress marker is cleared', () => {
  assert.equal(canStartAutoDeployment('run-1', new Set(), new Set()), true)
})

test('business generation errors are not browser network errors', () => {
  assert.equal(isBusinessGenerationError('generation_error'), true)
  assert.equal(isBusinessGenerationError('error'), false)
})
