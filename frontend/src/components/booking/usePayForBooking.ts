import { useMutation, useQueryClient } from '@tanstack/react-query'
import type { Booking } from '../../api/bookings.ts'
import { createPaymentOrder, getPayment, verifyPayment, type Payment } from '../../api/payments.ts'
import { useAuth } from '../../auth/useAuth.ts'
import { openCheckout } from '../../lib/razorpay.ts'

export type PayOutcome =
  | { kind: 'paid'; payment: Payment }
  /** Razorpay has it but hasn't captured yet; the webhook will finish it. */
  | { kind: 'processing' }
  | { kind: 'closed'; lastError: string | null }

const sleep = (ms: number) => new Promise((r) => setTimeout(r, ms))

/**
 * Order → Checkout → verify. When Checkout closes without success, the payment is polled briefly because a
 * webhook may already have completed it.
 */
export function usePayForBooking(booking: Booking) {
  const { withAuth } = useAuth()
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: async (): Promise<PayOutcome> => {
      const order = await withAuth((token) => createPaymentOrder(token, booking.id))
      const result = await openCheckout(order)
      if (result.kind === 'success') {
        const payment = await withAuth((token) => verifyPayment(token, order.payment_id, result.response))
        return payment.status === 'PAID' ? { kind: 'paid', payment } : { kind: 'processing' }
      }
      for (let attempt = 0; attempt < 3; attempt++) {
        const payment = await withAuth((token) => getPayment(token, order.payment_id))
        if (payment.status === 'PAID') return { kind: 'paid', payment }
        await sleep(1500)
      }
      return { kind: 'closed', lastError: result.lastError }
    },
    onSettled: () => {
      void queryClient.invalidateQueries({ queryKey: ['booking', booking.id] })
      void queryClient.invalidateQueries({ queryKey: ['bookings'] })
      void queryClient.invalidateQueries({ queryKey: ['public-departure', booking.departure.id] })
      void queryClient.invalidateQueries({ queryKey: ['public-departures'] })
    },
  })
}
