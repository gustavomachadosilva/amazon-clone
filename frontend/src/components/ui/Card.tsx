import { type ElementType, type HTMLAttributes, type ReactNode } from 'react'
import Blueprint from './Blueprint'

export interface CardProps extends HTMLAttributes<HTMLElement> {
  as?: ElementType
  type?: 'button' | 'submit' | 'reset'
  blueprint?: boolean
  hoverLift?: boolean
  children?: ReactNode
}

export function Card({
  as: Component = 'div',
  blueprint = false,
  hoverLift = false,
  className = '',
  children,
  ...props
}: CardProps) {
  const classes = [
    'card',
    hoverLift ? 'prod' : '',
    className,
  ].filter(Boolean).join(' ')

  if (blueprint) {
    return (
      <Blueprint as={Component} className={classes} {...props}>
        {children}
      </Blueprint>
    )
  }

  return (
    <Component className={classes} {...props}>
      {children}
    </Component>
  )
}

export function CardTitle({
  as: Component = 'h3',
  className = '',
  children,
  ...props
}: HTMLAttributes<HTMLElement> & { as?: ElementType }) {
  return (
    <Component className={`card-title ${className}`.trim()} {...props}>
      {children}
    </Component>
  )
}

export function CardBody({ className = '', children, ...props }: HTMLAttributes<HTMLParagraphElement>) {
  return (
    <p className={`card-body ${className}`.trim()} {...props}>
      {children}
    </p>
  )
}

export function CardMeta({ className = '', children, ...props }: HTMLAttributes<HTMLDivElement>) {
  return (
    <div className={`card-meta ${className}`.trim()} {...props}>
      {children}
    </div>
  )
}

export default Card

