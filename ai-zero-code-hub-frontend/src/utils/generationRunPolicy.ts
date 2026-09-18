export type DeploymentRunState = 'in_progress' | 'succeeded'

/**
 * Deployment idempotency is scoped to a run, never to the app's historical
 * deploy fields. A failed deployment deliberately leaves no durable success
 * marker so the existing retry action can try again.
 */
export const canStartAutoDeployment = (
  runId: string,
  completedRunIds: ReadonlySet<string>,
  inFlightRunIds: ReadonlySet<string>,
  persistedState?: DeploymentRunState | null,
) => !completedRunIds.has(runId)
  && !inFlightRunIds.has(runId)
  && persistedState !== 'in_progress'
  && persistedState !== 'succeeded'

export const isBusinessGenerationError = (eventType: string) => eventType === 'generation_error'
