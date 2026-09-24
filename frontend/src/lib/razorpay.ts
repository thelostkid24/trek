import type { CheckoutSuccess, PaymentOrder } from '../api/payments.ts'

// Razorpay Standard Checkout (docs/TRD.md §7.4). Card details are entered only inside Razorpay's frame.

const SCRIPT_URL = 'https://checkout.razorpay.com/v1/checkout.js'
const BRAND_COLOR = '#143626' // --color-brand-900

type RazorpayInstance = {
  open: () => void
  on: (event: 'payment.failed', handler: (response: { error?: { description?: string } }) => void) => void
}

type RazorpayOptions = {
  key: string
  order_id: string
  amount: number
  currency: string
  name: string
  description: string
  timeout: number
  prefill: { name?: string; email?: string; contact?: string }
  theme: { color: string }
  config: { display: { hide: { method: string }[] } }
  handler: (response: CheckoutSuccess) => void
  modal: { ondismiss: () => void; confirm_close: boolean }
}

declare global {
  interface Window {
    Razorpay?: new (options: RazorpayOptions) => RazorpayInstance
  }
}

let loading: Promise<void> | null = null

/** Loads checkout.js once. */
export function loadCheckout(): Promise<void> {
  if (window.Razorpay) return Promise.resolve()
  loading ??= new Promise<void>((resolve, reject) => {
    const script = document.createElement('script')
    script.src = SCRIPT_URL
    script.async = true
    script.onload = () => resolve()
    script.onerror = () => {
      loading = null
      script.remove()
      reject(new Error("Couldn't load the payment window. Check your connection and try again."))
    }
    document.head.appendChild(script)
  })
  return loading
}

export type CheckoutResult =
  | { kind: 'success'; response: CheckoutSuccess }
  /** Closed without success. `lastError` is Razorpay's message if an attempt failed inside the window. */
  | { kind: 'dismissed'; lastError: string | null }

/** Opens Checkout for an order and resolves once it closes. */
export async function openCheckout(order: PaymentOrder): Promise<CheckoutResult> {
  await loadCheckout()
  const Razorpay = window.Razorpay
  if (!Razorpay) throw new Error("Couldn't load the payment window.")

  return new Promise<CheckoutResult>((resolve) => {
    let lastError: string | null = null
    const rzp = new Razorpay({
      key: order.key_id,
      order_id: order.razorpay_order_id,
      amount: order.amount_paise,
      currency: order.currency,
      name: 'The Empty Valley',
      description: order.description,
      timeout: order.checkout_timeout_seconds,
      prefill: {
        name: order.prefill.name ?? undefined,
        email: order.prefill.email ?? undefined,
        contact: order.prefill.contact ?? undefined,
      },
      theme: { color: BRAND_COLOR },
      // EMI and Pay Later are off in V1.
      config: { display: { hide: [{ method: 'emi' }, { method: 'cardless_emi' }, { method: 'paylater' }] } },
      handler: (response) => resolve({ kind: 'success', response }),
      modal: { ondismiss: () => resolve({ kind: 'dismissed', lastError }), confirm_close: true },
    })
    // Checkout stays open so the trekker can retry; remember why the attempt failed.
    rzp.on('payment.failed', (response) => {
      lastError = response.error?.description ?? 'The payment did not go through.'
    })
    rzp.open()
  })
}
