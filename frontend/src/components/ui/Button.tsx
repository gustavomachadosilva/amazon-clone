import { forwardRef, type ButtonHTMLAttributes, type ReactNode } from 'react'

export type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'icon'

export interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: ButtonVariant
  block?: boolean
  children?: ReactNode
}

export const Button = forwardRef<HTMLButtonElement, ButtonProps>(function Button(
  { variant = 'primary', block = false, className = '', children, ...props },
  ref
) {
  const variantClass = {
    primary: 'btn-primary',
    secondary: 'btn-secondary',
    ghost: 'btn-ghost',
    icon: 'btn-icon',
  }[variant]

  const blockClass = block ? 'btn-block' : ''
  const combinedClassName = ['btn', variantClass, blockClass, className].filter(Boolean).join(' ')

  return (
    <button ref={ref} className={combinedClassName} {...props}>
      {children}
    </button>
  )
})

export default Button

