/**
 * Phone OTP sign-in stays hidden until SMS can be delivered (TRAI DLT approval, docs/DEPLOY.md).
 * Build with `VITE_PHONE_OTP=true` to show it.
 */
export const PHONE_OTP_ENABLED = import.meta.env.VITE_PHONE_OTP === 'true'
