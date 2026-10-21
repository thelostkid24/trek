import type { GuideProfile } from '../../../api/catalog.ts'

/**
 * Every section of the public guide page (docs/TRD.md §7.21) takes the page's guide and whether the signed-in user
 * is that guide. Each section lives in its own file, so the parts of §7.21 can be built side by side.
 */
export type GuideSectionProps = { guide: GuideProfile; owner: boolean }
