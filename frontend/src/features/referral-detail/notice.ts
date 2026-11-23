/** The result of the last command on this page, announced in the page's live region. */
export interface Notice {
  tone: 'success' | 'warning'
  text: string
}

export type NoticeHandler = (notice: Notice | null) => void
