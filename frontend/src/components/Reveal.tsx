import { useEffect, useRef, useState } from 'react'
import type { ReactNode } from 'react'

/**
 * Fades a section in the first time it scrolls into view.
 *
 * Decoration, so every path fails open: content meant to be read must never sit
 * at zero opacity because something did not report back. Anything already on
 * screen reveals straight away, a scroll listener backs up the observer, and a
 * timer backs up both — because an observer callback, a scroll event and
 * `requestAnimationFrame` all depend on the browser producing frames, which a
 * background tab or a headless render does not. The transition itself is a CSS
 * one, so the global reduced-motion rule already switches it off.
 */
export function Reveal({
  children,
  delay = 0,
  as: Tag = 'div',
  className = '',
}: {
  children: ReactNode
  /** Stagger, in milliseconds, for items revealed as a group. */
  delay?: number
  as?: 'div' | 'section' | 'li' | 'article'
  className?: string
}) {
  const ref = useRef<HTMLElement>(null)
  const [shown, setShown] = useState(false)

  useEffect(() => {
    const node = ref.current
    if (!node) {
      setShown(true)
      return
    }

    let settled = false
    let observer: IntersectionObserver | undefined
    let frame = 0
    let timer = 0

    function cleanup() {
      observer?.disconnect()
      window.cancelAnimationFrame(frame)
      window.clearTimeout(timer)
      window.removeEventListener('scroll', onScroll)
    }

    function reveal() {
      if (settled) return
      settled = true
      setShown(true)
      cleanup()
    }

    function isInView() {
      const rect = node!.getBoundingClientRect()
      return rect.top < window.innerHeight * 0.9 && rect.bottom > 0
    }

    function onScroll() {
      if (isInView()) reveal()
    }

    // On screen already, or nothing to observe with: reveal on the next tick
    // rather than synchronously, so the hidden state is painted once and the
    // fade is actually seen.
    if (isInView() || typeof IntersectionObserver === 'undefined') {
      timer = window.setTimeout(reveal, 30)
      return cleanup
    }

    observer = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting)) reveal()
      },
      { rootMargin: '0px 0px -10% 0px' },
    )
    observer.observe(node)
    window.addEventListener('scroll', onScroll, { passive: true })

    // The failsafe: if no frame is ever produced, show the section anyway. A
    // browser that is painting cancels this on its very first frame, so the
    // scroll animation is untouched in the normal case.
    timer = window.setTimeout(reveal, 500)
    frame = window.requestAnimationFrame(() => window.clearTimeout(timer))

    return cleanup
  }, [])

  return (
    <Tag
      // The tag comes from a fixed set of elements, so the ref always matches.
      ref={ref as never}
      className={`reveal ${shown ? 'is-visible' : ''} ${className}`.trim()}
      style={{ transitionDelay: `${delay}ms` }}
    >
      {children}
    </Tag>
  )
}
