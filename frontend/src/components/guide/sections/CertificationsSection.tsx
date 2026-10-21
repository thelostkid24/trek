import { CredentialList } from '../../catalog/GuideProfileCard.tsx'
import type { GuideSectionProps } from './types.ts'

/** "Certifications and safety" (§7.21.3). For now the admin-set course table from §7.12. */
export function CertificationsSection({ guide }: GuideSectionProps) {
  return <CredentialList guide={guide} />
}
