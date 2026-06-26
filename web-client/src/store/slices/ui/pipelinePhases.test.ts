import { describe, it, expect } from 'vitest'
import { computePhases } from './pipelinePhases'
import type { LegalActionInfo } from '@/types/messages'

/**
 * Minimal CastSpell LegalActionInfo factory — only the fields computePhases reads matter.
 */
function castAction(over: Record<string, unknown>): LegalActionInfo {
  return {
    actionType: 'CastSpellModal',
    description: 'Cast Test',
    action: { type: 'CastSpell', playerId: 'p1', cardId: 'c1' },
    ...over,
  } as unknown as LegalActionInfo
}

describe('computePhases — choose-N modal', () => {
  it('plain choose-N modal (Spree) runs only the modalModes phase', () => {
    const info = castAction({
      modalEnumeration: {
        chooseCount: 3,
        minChooseCount: 1,
        allowRepeat: true,
        modes: [],
      },
    })
    expect(computePhases(info)).toEqual([{ type: 'modalModes' }])
  })

  it('"choose both if you blight" modal (Pyrrhic Strike) also collects the blight target', () => {
    // The engine forces every mode on the blight variant but only unlocks the extra modes
    // once the submitted action carries blightTargets. The client must therefore run a
    // costPayment phase to pick the creature to blight — otherwise the action submits with
    // no blight and the engine rejects it ("Too many modes chosen").
    const info = castAction({
      modalEnumeration: {
        chooseCount: 2,
        minChooseCount: 2,
        allowRepeat: false,
        modes: [],
      },
      additionalCostInfo: {
        costType: 'Blight',
        description: 'creature to blight',
        validBlightTargets: ['fodder1'],
        blightAmount: 2,
      },
    })
    expect(computePhases(info)).toEqual([{ type: 'modalModes' }, { type: 'costPayment' }])
  })
})
