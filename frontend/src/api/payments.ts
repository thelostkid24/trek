import { apiFetch } from './client'

// Contract: docs/TRD.md §7.4 (with §7.6 changes). Call through `withAuth` from useAuth().

export type PaymentStatus = 'CREATED' | 'PAID' | 'FAILED' | 'EXPIRED'
export type PaymentMethod = 'UPI' | 'CARD' | 'NETBANKING' | 'WALLET'

export type Payment = {
  id: string
  booking_id: string
  status: PaymentStatus
  amount_paise: number
  amount_refunded_paise: number
  method: PaymentMethod | null
  method_detail: {
    card_network?: string
    card_last4?: string
    bank?: string
    wallet?: string
    vpa?: string
  } | null
  failure_reason: string | null
  paid_at: string | null
  created_at: string
}

export type PaymentOrder = {
  payment_id: string
  booking_id: string
  key_id: string
  razorpay_order_id: string
  amount_paise: number
  currency: 'INR'
  description: string
  checkout_timeout_seconds: number
  prefill: { name: string | null; email: string | null; contact: string | null }
}

export type CheckoutSuccess = {
  razorpay_order_id: string
  razorpay_payment_id: string
  razorpay_signature: string
}

export const createPaymentOrder = (token: string, bookingId: string) =>
  apiFetch<PaymentOrder>('/api/trekker/payments/orders', { method: 'POST', token, body: { booking_id: bookingId } })

export const verifyPayment = (token: string, paymentId: string, body: CheckoutSuccess) =>
  apiFetch<Payment>(`/api/trekker/payments/${paymentId}/verify`, { method: 'POST', token, body })

export const getPayment = (token: string, paymentId: string) =>
  apiFetch<Payment>(`/api/trekker/payments/${paymentId}`, { token })

export function describeMethod(payment: Payment): string | null {
  const d = payment.method_detail ?? {}
  switch (payment.method) {
    case 'CARD':
      return [d.card_network ?? 'Card', d.card_last4 && `•••• ${d.card_last4}`].filter(Boolean).join(' ')
    case 'UPI':
      return d.vpa ? `UPI · ${d.vpa}` : 'UPI'
    case 'NETBANKING':
      return d.bank ? `Netbanking · ${d.bank}` : 'Netbanking'
    case 'WALLET':
      return d.wallet ? `Wallet · ${d.wallet}` : 'Wallet'
    default:
      return null
  }
}
