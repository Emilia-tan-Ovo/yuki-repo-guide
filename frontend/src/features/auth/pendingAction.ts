export type PendingAction = () => Promise<void>

export class PendingActionSlot {
  private action: PendingAction | null = null

  remember(action: PendingAction): void {
    this.action = action
  }

  take(): PendingAction | null {
    const action = this.action
    this.action = null
    return action
  }

  clear(): void {
    this.action = null
  }
}
