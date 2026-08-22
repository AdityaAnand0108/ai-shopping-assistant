/**
 * An inline error.
 *
 * role="alert" so a screen reader announces it as soon as it appears — a failed
 * sign-in that is only visible is a failed sign-in that some users never learn
 * about.
 */
export function ErrorNote({ message }: { message: string | null }) {
  if (!message) return null
  return (
    <p className="error" role="alert">
      {message}
    </p>
  )
}
