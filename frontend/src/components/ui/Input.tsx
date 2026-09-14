import { forwardRef, useId, type InputHTMLAttributes, type ReactNode } from 'react'

export interface InputProps extends InputHTMLAttributes<HTMLInputElement> {
  label?: ReactNode
  error?: ReactNode
  helperText?: ReactNode
  containerClassName?: string
}

export const Input = forwardRef<HTMLInputElement, InputProps>(function Input(
  { label, error, helperText, containerClassName = '', className = '', id, ...props },
  ref
) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const inputElement = (
    <input
      ref={ref}
      id={inputId}
      className={`input ${className}`.trim()}
      aria-invalid={error ? 'true' : undefined}
      {...props}
    />
  )

  if (!label && !error && !helperText && !containerClassName) {
    return inputElement
  }

  return (
    <div className={`field ${containerClassName}`.trim()}>
      {label && <label htmlFor={inputId}>{label}</label>}
      {inputElement}
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

export default Input

