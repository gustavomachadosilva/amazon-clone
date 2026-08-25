import type { ElementType, HTMLAttributes, ReactNode } from 'react'

interface BlueprintProps extends HTMLAttributes<HTMLElement> {
  as?: ElementType
  children: ReactNode
}

export default function Blueprint({ as: Component = 'div', children, className = '', ...rest }: BlueprintProps) {
  return (
    <Component className={`blueprint ${className}`} {...rest}>
      <i className="corner tl" />
      <i className="corner tr" />
      <i className="corner bl" />
      <i className="corner br" />
      {children}
    </Component>
  )
}
