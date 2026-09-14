import { forwardRef, useId, type ReactNode, type SelectHTMLAttributes } from 'react'

export interface SelectProps extends SelectHTMLAttributes<HTMLSelectElement> {
  label?: ReactNode
  error?: ReactNode
  helperText?: ReactNode
  containerClassName?: string
  children?: ReactNode
}

export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { label, error, helperText, containerClassName = '', className = '', id, children, ...props },
  ref
) {
  const generatedId = useId()
  const selectId = id ?? generatedId
  const selectElement = (
    <select
      ref={ref}
      id={selectId}
      className={`input ${className}`.trim()}
      aria-invalid={error ? 'true' : undefined}
      {...props}
    >
      {children}
    </select>
  )

  if (!label && !error && !helperText && !containerClassName) {
    return selectElement
  }

  return (
    <div className={`field ${containerClassName}`.trim()}>
      {label && <label htmlFor={selectId}>{label}</label>}
      {selectElement}
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

export default Select

