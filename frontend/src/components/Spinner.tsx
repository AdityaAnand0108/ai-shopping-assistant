/** A loading indicator that also announces itself to a screen reader. */
export function Spinner({ label = 'Loading…' }: { label?: string }) {
  return (
    <p className="muted centered" role="status" aria-live="polite">
      {label}
    </p>
  )
}
