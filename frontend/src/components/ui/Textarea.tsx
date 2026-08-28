import { forwardRef, type ReactNode, type TextareaHTMLAttributes } from 'react'

export interface TextareaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label?: ReactNode
  error?: ReactNode
  helperText?: ReactNode
  containerClassName?: string
}

export const Textarea = forwardRef<HTMLTextAreaElement, TextareaProps>(function Textarea(
  { label, error, helperText, containerClassName = '', className = '', id, ...props },
  ref
) {
  const textareaElement = (
    <textarea
      ref={ref}
      id={id}
      className={`input ${className}`.trim()}
      aria-invalid={error ? 'true' : undefined}
      {...props}
    />
  )

  if (!label && !error && !helperText && !containerClassName) {
    return textareaElement
  }

  return (
    <div className={`field ${containerClassName}`.trim()}>
      {label && <label htmlFor={id}>{label}</label>}
      {textareaElement}
      {helperText && !error && (
        <span className="text-xs text-neutral-600 mt-1 block">{helperText}</span>
      )}
      {error && (
        <span className="text-xs text-accent-800 mt-1 block" role="alert">
          {error}
        </span>
      )}
    </div>
  )
})

export default Textarea

