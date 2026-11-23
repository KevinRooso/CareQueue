/** The CareQueue mark: a triage tag with three queue lines. Same drawing as public/favicon.svg. */
export function Logo({ className }: { className?: string }) {
  return (
    <svg className={className} viewBox="0 0 32 32" aria-hidden="true" focusable="false">
      <path d="M9 2h14l5 5v21a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V7z" fill="#4e2a84" />
      <circle cx="16" cy="8" r="2.4" fill="#dbe2e8" />
      <rect x="9" y="14" width="14" height="3" rx="1.5" fill="#fff" />
      <rect x="9" y="19.5" width="10.5" height="3" rx="1.5" fill="#fff" />
      <rect x="9" y="25" width="7" height="3" rx="1.5" fill="#fff" />
    </svg>
  )
}
